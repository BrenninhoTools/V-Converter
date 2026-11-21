cmake_minimum_required(VERSION 3.16)

project(VArchive
    VERSION 1.0.0
    DESCRIPTION "Reads and writes ZIP mod archives without extracting them, copying compressed entries as they are"
    LANGUAGES C CXX
)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

option(VARCHIVE_BUILD_TESTS "Build tests" OFF)

add_library(VArchive STATIC
    src/VArchive.cpp
    third_party/miniz/miniz.c
    include/varchive/VArchive.h
)
add_library(VArchive::VArchive ALIAS VArchive)

target_include_directories(VArchive PUBLIC $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/include> PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/third_party/miniz)
target_compile_definitions(VArchive PRIVATE MINIZ_NO_ZLIB_COMPATIBLE_NAMES _LARGEFILE64_SOURCE)
set_target_properties(VArchive PROPERTIES OUTPUT_NAME "varchive" POSITION_INDEPENDENT_CODE ON C_EXTENSIONS ON)

find_package(Threads)
if(Threads_FOUND)
    target_link_libraries(VArchive PUBLIC Threads::Threads)
endif()

if(MSVC)
    target_compile_options(VArchive PRIVATE /O2 /utf-8 /EHsc)
else()
    target_compile_options(VArchive PRIVATE -O2)
    set_source_files_properties(src/VArchive.cpp PROPERTIES COMPILE_OPTIONS "-Wall;-Wextra")
endif()

if(VARCHIVE_BUILD_TESTS)
    enable_testing()
    add_executable(varchive_tests tests/test_archive.cpp)
    target_link_libraries(varchive_tests PRIVATE VArchive::VArchive)
    add_test(NAME VArchiveTests COMMAND varchive_tests)
endif()
