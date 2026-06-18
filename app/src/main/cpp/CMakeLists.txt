cmake_minimum_required(VERSION 3.4.1)

project("native-keys")

add_library(
        native-keys
        SHARED
        native-lib.cpp)

find_library(
        log-lib
        log)

target_link_libraries(
        native-keys
        ${log-lib})
