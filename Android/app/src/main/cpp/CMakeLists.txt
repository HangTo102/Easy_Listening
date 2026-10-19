cmake_minimum_required(VERSION 3.22.1)
project(easylistening C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

# ES_CORE_DIR 由 Gradle 传入，指向仓库的 core/ 目录。
if(NOT DEFINED ES_CORE_DIR)
    set(ES_CORE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../../core")
endif()
message(STATUS "EasyListening core dir = ${ES_CORE_DIR}")

file(GLOB ES_CORE_SOURCES "${ES_CORE_DIR}/src/*/*.c")

add_library(easylistening SHARED
    es_jni.c
    ${ES_CORE_SOURCES}
)

target_include_directories(easylistening PRIVATE
    "${ES_CORE_DIR}/include"
    "${ES_CORE_DIR}/src"
)

find_library(log-lib log)
target_link_libraries(easylistening ${log-lib})
