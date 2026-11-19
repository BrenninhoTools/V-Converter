cmake_minimum_required(VERSION 3.16)

project(VTouch
    VERSION 1.0.0
    DESCRIPTION "Touch input engine for V-Converter written in C++"
    LANGUAGES CXX
)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

option(VTOUCH_BUILD_TESTS "Build tests" OFF)

add_library(VTouch STATIC src/VTouchEngine.cpp include/vtouch/VTouch.h)
add_library(VTouch::VTouch ALIAS VTouch)

target_include_directories(VTouch PUBLIC $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/include>)
set_target_properties(VTouch PROPERTIES OUTPUT_NAME "vtouch" POSITION_INDEPENDENT_CODE ON)

if(MSVC)
    target_compile_options(VTouch PRIVATE /O2 /GR- /EHs-c-)
else()
    target_compile_options(VTouch PRIVATE -O2 -fno-exceptions -fno-rtti -Wall -Wextra)
endif()

if(VTOUCH_BUILD_TESTS)
    enable_testing()
    add_executable(vtouch_tests tests/test_touch.cpp)
    target_link_libraries(vtouch_tests PRIVATE VTouch::VTouch)
    add_test(NAME VTouchTests COMMAND vtouch_tests)
endif()
