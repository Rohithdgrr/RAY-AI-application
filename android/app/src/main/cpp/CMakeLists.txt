cmake_minimum_required(VERSION 3.22.1)

project("offlinellm")

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)

# Note: In a production app, llama.cpp would be added as a subdirectory
# add_subdirectory(llama.cpp)

add_library(
        llama-jni
        SHARED
        native-lib.cpp
)

find_library(
        log-lib
        log
)

target_link_libraries(
        llama-jni
        # llama (link against llama.cpp library)
        ${log-lib}
)
