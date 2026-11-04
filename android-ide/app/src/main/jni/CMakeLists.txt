cmake_minimum_required(VERSION 3.22.1)
project(pydroid_embedded CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Path to the prebuilt CPython (headers + libpython3.x.so), unpacked per ABI.
# Expected layout (see ../README.md):
#   jni/prebuilt/${ANDROID_ABI}/include/python3.11/Python.h
#   jni/prebuilt/${ANDROID_ABI}/lib/libpython3.11.so
set(PREBUILT_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/prebuilt/${ANDROID_ABI})

add_library(pyembed SHARED pyembed.cpp)

target_include_directories(pyembed PRIVATE
    ${PREBUILT_ROOT}/include/python3.11)

# Импортируем libpython из jniLibs через prebuilt-библиотеку,
# чтобы линковка не требовала статического libc.a Android.
add_library(python3.11 SHARED IMPORTED)
set_target_properties(python3.11 PROPERTIES
    IMPORTED_LOCATION ${PREBUILT_ROOT}/lib/libpython3.11.so)

target_link_libraries(pyembed
    python3.11
    android
    log
    OpenSLES)
