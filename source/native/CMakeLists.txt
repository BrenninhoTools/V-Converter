cmake_minimum_required(VERSION 3.18)

project(VConverterNative LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

set(VMA_ENABLE_LOGGING OFF CACHE BOOL "" FORCE)
set(VMA_BUILD_SHARED OFF CACHE BOOL "" FORCE)

add_subdirectory(v-miniaudio)
add_subdirectory(v-touch)

add_library(vconverter_audio SHARED bridge/vconverter_audio_jni.c)

target_link_libraries(vconverter_audio PRIVATE VMiniAudio::VMiniAudio)

add_library(vconverter_touch SHARED bridge/vconverter_touch_jni.c)

target_link_libraries(vconverter_touch PRIVATE VTouch::VTouch)

set(VCA_TARGETS vconverter_audio vconverter_touch)

if(NOT ANDROID)
    set(VCA_JAVA_HOME "$ENV{JAVA_HOME}" CACHE PATH "JDK used for the JNI headers")

    if(VCA_JAVA_HOME AND EXISTS "${VCA_JAVA_HOME}/include/jni.h")
        if(WIN32)
            set(VCA_JNI_PLATFORM win32)
        elseif(APPLE)
            set(VCA_JNI_PLATFORM darwin)
        else()
            set(VCA_JNI_PLATFORM linux)
        endif()
        foreach(target IN LISTS VCA_TARGETS)
            target_include_directories(${target} PRIVATE
                "${VCA_JAVA_HOME}/include"
                "${VCA_JAVA_HOME}/include/${VCA_JNI_PLATFORM}"
            )
        endforeach()
    else()
        find_package(JNI REQUIRED)
        foreach(target IN LISTS VCA_TARGETS)
            target_include_directories(${target} PRIVATE ${JNI_INCLUDE_DIRS})
        endforeach()
    endif()

    if(MSVC)
        foreach(target IN LISTS VCA_TARGETS)
            target_compile_options(${target} PRIVATE /O2)
        endforeach()
        set_property(TARGET vconverter_audio vconverter_touch VMiniAudio VTouch PROPERTY MSVC_RUNTIME_LIBRARY "MultiThreaded")
    else()
        foreach(target IN LISTS VCA_TARGETS)
            target_compile_options(${target} PRIVATE -O2)
        endforeach()
        if(NOT APPLE)
            target_link_options(vconverter_touch PRIVATE -static-libstdc++ -static-libgcc)
        endif()
    endif()

    if(VCA_OUTPUT_DIR)
        set_target_properties(${VCA_TARGETS} PROPERTIES
            LIBRARY_OUTPUT_DIRECTORY "$<1:${VCA_OUTPUT_DIR}>"
            RUNTIME_OUTPUT_DIRECTORY "$<1:${VCA_OUTPUT_DIR}>"
            ARCHIVE_OUTPUT_DIRECTORY "$<1:${VCA_OUTPUT_DIR}/lib>"
        )
    endif()
else()
    target_link_libraries(vconverter_audio PRIVATE log)
endif()
