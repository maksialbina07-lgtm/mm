# jniLibs — бинарники CPython под Android

Положить в <abi>/ после кросс-сборки (см. tools/prepare_python_env.sh):

  libpython3.11.so   — сам интерпретатор (shared build!)
  libssl.so  libcrypto.so  — OpenSSL для aiohttp/HTTPS
  libffi.so          — ctypes/cffi
  libsqlite3.so      — stdlib sqlite3

Загружаются автоматически при System.loadLibrary("pyembed"), т.к. pyembed
слинкован с ними (DT_NEEDED).
