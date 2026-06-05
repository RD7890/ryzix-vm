cmake_minimum_required(VERSION 3.22.1)
project("ryzixvm")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Path where GitHub Actions places prebuilt QEMU static library
set(QEMU_PREBUILT_DIR "${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}")
set(QEMU_INCLUDE_DIR "${CMAKE_SOURCE_DIR}/qemu_includes")

# JNI bridge source
add_library(
    ryzixvm
    SHARED
    qemu_jni.cpp
    vnc_client.cpp
)

# Link against Android system libraries
find_library(log-lib log)
find_library(android-lib android)

# Check if prebuilt QEMU library exists (placed by GitHub Actions)
if(EXISTS "${QEMU_PREBUILT_DIR}/libqemu.a")
    message(STATUS "Found prebuilt QEMU at ${QEMU_PREBUILT_DIR}/libqemu.a")
    add_library(qemu STATIC IMPORTED)
    set_target_properties(qemu PROPERTIES
        IMPORTED_LOCATION "${QEMU_PREBUILT_DIR}/libqemu.a"
    )
    target_include_directories(ryzixvm PRIVATE ${QEMU_INCLUDE_DIR})
    target_compile_definitions(ryzixvm PRIVATE QEMU_AVAILABLE=1)
    target_link_libraries(
        ryzixvm
        qemu
        ${log-lib}
        ${android-lib}
        z
        dl
    )
else()
    message(STATUS "QEMU prebuilt not found — building stub mode")
    target_compile_definitions(ryzixvm PRIVATE QEMU_AVAILABLE=0)
    target_link_libraries(
        ryzixvm
        ${log-lib}
        ${android-lib}
    )
endif()
