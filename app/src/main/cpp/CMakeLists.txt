cmake_minimum_required(VERSION 3.22.1)
project(jarvis_native)

set(CMAKE_CXX_STANDARD 17)

set(WHISPER_DIR ${CMAKE_SOURCE_DIR}/whisper.cpp)

# Extract whisper.cpp version from its CMakeLists.txt
file(READ "${WHISPER_DIR}/CMakeLists.txt" WHISPER_CMAKE_CONTENT)
string(REGEX MATCH "project\\(\"whisper\\.cpp\" VERSION ([0-9]+\\.[0-9]+\\.[0-9]+)\\)" VERSION_MATCH "${WHISPER_CMAKE_CONTENT}")
if(CMAKE_MATCH_1)
    set(WHISPER_VERSION ${CMAKE_MATCH_1})
else()
    set(WHISPER_VERSION "unknown")
endif()
message(STATUS "Whisper version: ${WHISPER_VERSION}")

set(SOURCE_FILES
    ${WHISPER_DIR}/src/whisper.cpp
    ${CMAKE_SOURCE_DIR}/whisper_jni.cpp
)

find_library(LOG_LIB log)

include(FetchContent)

function(build_whisper_jni target_name)
    add_library(${target_name} SHARED ${SOURCE_FILES})

    target_compile_definitions(${target_name} PUBLIC GGML_USE_CPU)
    target_compile_definitions(${target_name} PRIVATE WHISPER_VERSION="${WHISPER_VERSION}")

    if (${target_name} STREQUAL "whisper_jni_v8fp16")
        target_compile_options(${target_name} PRIVATE -march=armv8.2-a+fp16)
        set(GGML_COMPILE_OPTIONS -march=armv8.2-a+fp16)
    endif()

    if (NOT ${CMAKE_BUILD_TYPE} STREQUAL "Debug")
        target_compile_options(${target_name} PRIVATE -O3)
        target_compile_options(${target_name} PRIVATE -fvisibility=hidden -fvisibility-inlines-hidden)
        target_compile_options(${target_name} PRIVATE -ffunction-sections -fdata-sections)
        target_link_options(${target_name} PRIVATE -Wl,--gc-sections)
        target_link_options(${target_name} PRIVATE -flto)
    endif()

    FetchContent_Declare(ggml SOURCE_DIR ${WHISPER_DIR}/ggml)
    FetchContent_MakeAvailable(ggml)

    if (DEFINED GGML_COMPILE_OPTIONS)
        target_compile_options(ggml PRIVATE ${GGML_COMPILE_OPTIONS})
    endif()

    target_include_directories(${target_name} PRIVATE
        ${WHISPER_DIR}/include
        ${WHISPER_DIR}/src
        ${WHISPER_DIR}/ggml/include
        ${WHISPER_DIR}/ggml/src
        ${WHISPER_DIR}/ggml/src/ggml-cpu
    )

    target_link_libraries(${target_name} ${LOG_LIB} android ggml)
endfunction()

# Build optimized variant for arm64-v8a (real device), default for x86_64 (emulator)
if (${ANDROID_ABI} STREQUAL "arm64-v8a")
    build_whisper_jni("whisper_jni_v8fp16")
endif()

build_whisper_jni("whisper_jni")
