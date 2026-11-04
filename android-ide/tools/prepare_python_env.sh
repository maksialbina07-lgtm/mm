#!/usr/bin/env bash
# prepare_python_env.sh — собирает python-среду для PyDroid IDE.
#
# Что происходит:
#   1. CPython, собранный под Android NDK (бинарники), раскладывается в
#      app/src/main/jniLibs/<abi>/  (libpython3.11.so, libssl, libcrypto,
#      libffi, libsqlite3) и в app/src/main/jni/prebuilt/<abi>/ (заголовки).
#   2. pip устанавливает aiogram (+aiohttp, pydantic и все зависимости) в
#      app/src/main/assets/python/site-packages чистым x86_64-linux Python —
#      это pure-python пакеты, их .so-расширения (multidict, yarl, …) тоже
#      нужно пересобрать под Android (см. шаг 3).
#   3. Опционально: кросс-компиляция нативных расширений (multidict, yarl)
#      под Android через Chaquopy pip или NDK toolchain.
#
# Требования на машине сборки: NDK r26+, Python 3.11, git.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JNI="$ROOT/app/src/main/jni"
JNILIBS="$ROOT/app/src/main/jniLibs"
ASSETS="$ROOT/app/src/main/assets/python"
PY_VER="3.11"
ABIS=(arm64-v8a armeabi-v7a x86_64)

echo "[1/3] Раскладка бинарников CPython по jniLibs..."
for abi in "${ABIS[@]}"; do
    SRC="${CPYTHON_PREBUILT_ROOT:-$HOME/cpython-android}/$abi"
    [ -d "$SRC" ] || { echo "!! Нет prebuilt CPython для $abi в $SRC — см. README.md"; continue; }
    mkdir -p "$JNILIBS/$abi" "$JNI/prebuilt/$abi/include"
    cp -v "$SRC"/lib/*.so "$JNILIBS/$abi/"
    cp -rT "$SRC"/include "python${PY_VER}" "$JNI/prebuilt/$abi/include/python${PY_VER}" 2>/dev/null \
      || cp -r "$SRC"/include/* "$JNI/prebuilt/$abi/include/"
done

echo "[2/3] Установка site-packages (aiogram и зависимости) в assets..."
mkdir -p "$ASSETS/site-packages"
# Чистая установка без кэша; target-платформа не важна — пакеты pure-python.
python3 -m pip install --target "$ASSETS/site-packages" \
    aiogram==3.* aiohttp pydantic certifi \
    --disable-pip-version-check --no-compile

# stdlib кладём рядом: CPython на устройстве ищет её в home/lib/python3.11
mkdir -p "$ASSETS/lib/python${PY_VER}"
STDLIB="$(python3 -c 'import sysconfig; print(sysconfig.get_paths()["stdlib"])')"
rsync -a --exclude='site-packages' --exclude='__pycache__' --exclude='test*' \
    "$STDLIB/" "$ASSETS/lib/python${PY_VER}/" \
    --include='*.py' --include='*/' --exclude='*' --prune-empty-dirs || true

echo "[3/3] Готово."
echo "  jniLibs : $JNILIBS"
echo "  headers : $JNI/prebuilt"
echo "  assets  : $ASSETS"
du -sh "$JNILIBS" "$ASSETS" 2>/dev/null || true
