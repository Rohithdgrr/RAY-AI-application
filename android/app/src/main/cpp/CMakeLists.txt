cmake_minimum_required(VERSION 3.22.1)

project("offlinellm")

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)

# Add llama.cpp as subdirectory
add_subdirectory(llama.cpp)

add_library(
        llama-jni
        SHARED
        native-lib.cpp
)

# Include llama.cpp headers
target_include_directories(llama-jni PRIVATE 
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/include
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp
)

find_library(
        log-lib
        log
)

target_link_libraries(
        llama-jni
        llama
        ${log-lib}
)
