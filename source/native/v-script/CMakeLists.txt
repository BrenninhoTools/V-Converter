cmake_minimum_required(VERSION 3.16)

project(VScript
    VERSION 1.0.0
    DESCRIPTION "Converts Psych Engine Lua scripts into V-Slice HScript classes and generates V-Slice scripts, written in C++"
    LANGUAGES CXX
)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

option(VSCRIPT_BUILD_TESTS "Build tests" OFF)

add_library(VScript STATIC
    src/Json.cpp
    src/LuaParser.cpp
    src/Converter.cpp
    src/Builtins.cpp
    src/Generator.cpp
    src/VScript.cpp
    include/vscript/VScript.h
)
add_library(VScript::VScript ALIAS VScript)

target_include_directories(VScript PUBLIC $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/include> PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/src)
set_target_properties(VScript PROPERTIES OUTPUT_NAME "vscript" POSITION_INDEPENDENT_CODE ON)

if(MSVC)
    target_compile_options(VScript PRIVATE /O2 /utf-8 /EHsc)
else()
    target_compile_options(VScript PRIVATE -O2 -Wall -Wextra)
endif()

if(VSCRIPT_BUILD_TESTS)
    enable_testing()
    add_executable(vscript_tests tests/test_script.cpp)
    target_link_libraries(vscript_tests PRIVATE VScript::VScript)
    target_include_directories(vscript_tests PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/src)
    add_test(NAME VScriptTests COMMAND vscript_tests)
endif()
