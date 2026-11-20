cmake_minimum_required(VERSION 3.16)

project(VLauncher
    VERSION 1.0.0
    DESCRIPTION "Finds, fills and opens a Friday Night Funkin' install for V-Converter, written in C++"
    LANGUAGES CXX
)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

option(VLAUNCHER_BUILD_TESTS "Build tests" OFF)

add_library(VLauncher STATIC src/VLauncher.cpp include/vlauncher/VLauncher.h)
add_library(VLauncher::VLauncher ALIAS VLauncher)

target_include_directories(VLauncher PUBLIC $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/include>)
set_target_properties(VLauncher PROPERTIES OUTPUT_NAME "vlauncher" POSITION_INDEPENDENT_CODE ON)

if(MSVC)
    target_compile_options(VLauncher PRIVATE /O2 /utf-8)
else()
    target_compile_options(VLauncher PRIVATE -O2 -Wall -Wextra)
endif()

if(VLAUNCHER_BUILD_TESTS)
    enable_testing()
    add_executable(vlauncher_tests tests/test_launcher.cpp)
    target_link_libraries(vlauncher_tests PRIVATE VLauncher::VLauncher)
    add_test(NAME VLauncherTests COMMAND vlauncher_tests)
endif()
