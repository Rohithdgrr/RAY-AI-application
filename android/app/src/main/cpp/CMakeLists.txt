cmake_minimum_required(VERSION 3.22.1)

project("offlinellm")

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)

# Add llama.cpp as subdirectory
# Disable unnecessary components for mobile build
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "llama: build examples" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "llama: build tests" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "llama: build server" FORCE)
set(LLAMA_BUILD_COMMON ON CACHE BOOL "llama: build common" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "llama: use libcurl" FORCE)
set(LLAMA_HTTPLIB OFF CACHE BOOL "llama: use httplib" FORCE)

# Optimization: Only build for the architecture we need
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(LLAMA_AVX OFF CACHE BOOL "llama: use AVX" FORCE)
    set(LLAMA_AVX2 OFF CACHE BOOL "llama: use AVX2" FORCE)
    set(LLAMA_FMA OFF CACHE BOOL "llama: use FMA" FORCE)
endif()

add_subdirectory(llama.cpp)

add_library(
        llama-jni
        SHARED
        native-lib.cpp
)

find_library(
        log-lib
        log
)

target_include_directories(
        llama-jni
        PRIVATE
        llama.cpp/include
        llama.cpp/common
        llama.cpp/src
)

target_link_libraries(
        llama-jni
        llama
        common
        ${log-lib}
)
