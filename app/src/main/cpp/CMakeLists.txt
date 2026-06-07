cmake_minimum_required(VERSION 3.22.1)
project("linlator")

add_library(linlator SHARED
    nativebridge.cpp
)

target_compile_features(linlator PUBLIC cxx_std_17)
target_include_directories(linlator PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
find_library(log-lib log)
target_link_libraries(linlator ${log-lib} EGL GLESv3 android)
