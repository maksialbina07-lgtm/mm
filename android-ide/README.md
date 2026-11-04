# PyDroid IDE — Android IDE для Python-ботов (Kotlin + JNI + Embedded CPython)

Архитектура ровно та, что в ТЗ:

```
Android-приложение (Kotlin / Java)      app/src/main/java/com/pydroid/ide/*
Интерфейс и управление Python           activity_main.xml: серый фон, текст, кнопка «Запустить код»
JNI                                     app/src/main/jni/pyembed.cpp
Embedded CPython                        бинарники в app/src/main/jniLibs/<abi>/
Нативная Python-среда из jniLibs        libpython3.11.so, libssl, libcrypto, libffi, libsqlite3
Python site-packages                    app/src/main/assets/python/site-packages
aiogram, aiohttp, pydantic и зависимости pip install --target (см. tools/prepare_python_env.sh)
Telegram Bot API                        aiohttp long-polling из user-кода
Асинхронные сетевые запросы             asyncio event loop внутри dp.run(Bot(...))
```

## Почему 24/7 работает

1. **Foreground-сервис `BotService`** (`foregroundServiceType="specialUse"`,
   `START_STICKY`) — процесс с активным уведомлением Android не убивает при
   нехватке памяти; при случайной остановке система пересоздаёт сервис и
   перезапускает сохранённый код бота.
2. **BootReceiver** — автозапуск бота после перезагрузки устройства.
3. Event loop aiogram крутится на отдельном POSIX/JVM-потоке внутри
   встроенного CPython; GIL освобождается между вызовами JNI.

Ограничение платформы, которое нельзя обойти без root: aggressive battery
optimization (Doze) у отдельных OEM может замедлять сеть в фоне. Пользователю
нужно добавить приложение в исключения оптимизации батареи.

## Сборка

### Шаг 1. Бинарники CPython → jniLibs

Нужен CPython, собранный Android NDK (r26+). Проще всего взять готовый
распределённый build (Chaquopy, python-for-android, или свой кросс-билд):

```bash
# ожидаемая раскладка входа:
$HOME/cpython-android/<abi>/{lib/*.so, include/python3.11/**}

./tools/prepare_python_env.sh
# результат:
#   app/src/main/jniLibs/<abi>/libpython3.11.so (+libssl/libcrypto/libffi/libsqlite3)
#   app/src/main/jni/prebuilt/<abi>/include/python3.11/Python.h
```

Минимальный ручной кросс-билд CPython под arm64:

```bash
git clone -b 3.11 https://github.com/android/python-for-android.git  # или Chaquopy native
# либо: ./configure --host=aarch64-linux-android CC=$NDK/.../clang \
#       --with-build-python=$(which python3.11) && make -j && make install
```

### Шаг 2. Site-packages (aiogram предустановлен)

Скрипт выше выполняет:

```bash
python3 -m pip install --target app/src/main/assets/python/site-packages \
    aiogram==3.* aiohttp pydantic certifi
```

⚠️ Нативные колёса пакетов (aiohttp `_http_parser`, `multidict`, `yarl`)
должны быть собраны под Android — используйте wheels из Chaquopy репозитория
или соберите их NDK-toolchain'ем. Без этого aiohttp откатится на pure-python
парсер и всё равно будет работать.

### Шаг 3. Сборка APK

```bash
./gradlew assembleDebug
```

## Использование

Открыть приложение → вставить токен в код по умолчанию → «Запустить код».
Бот поднимется в foreground-сервисе и останется рабочим после закрытия UI.

## Структура проекта

```
android-ide/
├── build.gradle.kts / settings.gradle.kts
├── tools/prepare_python_env.sh          # подготовка бинарников и пакетов
└── app/
    ├── build.gradle.kts                 # NDK/CMake, abiFilters, viewBinding
    └── src/main/
        ├── AndroidManifest.xml          # INTERNET, FOREGROUND_SERVICE_SPECIAL_USE, BOOT
        ├── java/com/pydroid/ide/
        │   ├── MainActivity.kt          # UI: серый фон, текст, кнопка
        │   ├── BotService.kt            # foreground 24/7, START_STICKY
        │   ├── BotRuntime.kt            # запуск Python-потока
        │   ├── PythonEngine.kt          # loadLibrary + копирование assets→filesDir
        │   ├── PyDroidApp.kt            # Application, глобальный Context
        │   └── BootReceiver.kt          # автозапуск после reboot
        ├── jni/
        │   ├── CMakeLists.txt           # линкует pyembed.cpp с libpython3.11.so
        │   └── pyembed.cpp              # JNI: nativeInit/nativeRun (CPython C-API)
        ├── jniLibs/<abi>/               # ← бинарники CPython (шаг 1)
        ├── assets/python/
        │   ├── lib/python3.11/          # stdlib
        │   └── site-packages/           # aiogram, aiohttp, pydantic…
        └── res/layout/activity_main.xml # интерфейс по ТЗ
```
