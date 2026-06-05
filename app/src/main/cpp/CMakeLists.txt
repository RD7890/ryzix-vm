cmake_minimum_required(VERSION 3.22.1)
project("ryzixvm")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# JNI bridge — QEMU is loaded at runtime via dlopen from jniLibs
add_library(
    ryzixvm
    SHARED
    qemu_jni.cpp
    vnc_client.cpp
)

find_library(log-lib log)
find_library(android-lib android)
find_library(dl-lib dl)

target_link_libraries(
    ryzixvm
    ${log-lib}
    ${android-lib}
    ${dl-lib}
)
