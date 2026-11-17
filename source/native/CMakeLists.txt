cmake_minimum_required(VERSION 3.18)

project(VConverterAudio LANGUAGES C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

set(VMA_ENABLE_LOGGING OFF CACHE BOOL "" FORCE)
set(VMA_BUILD_SHARED OFF CACHE BOOL "" FORCE)

add_subdirectory(v-miniaudio)

add_library(vconverter_audio SHARED bridge/vconverter_audio_jni.c)

target_link_libraries(vconverter_audio PRIVATE VMiniAudio::VMiniAudio)

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
        target_include_directories(vconverter_audio PRIVATE
            "${VCA_JAVA_HOME}/include"
            "${VCA_JAVA_HOME}/include/${VCA_JNI_PLATFORM}"
        )
    else()
        find_package(JNI REQUIRED)
        target_include_directories(vconverter_audio PRIVATE ${JNI_INCLUDE_DIRS})
    endif()

    if(MSVC)
        target_compile_options(vconverter_audio PRIVATE /O2)
        set_property(TARGET vconverter_audio VMiniAudio PROPERTY MSVC_RUNTIME_LIBRARY "MultiThreaded")
    else()
        target_compile_options(vconverter_audio PRIVATE -O2)
    endif()

    if(VCA_OUTPUT_DIR)
        set_target_properties(vconverter_audio PROPERTIES
            LIBRARY_OUTPUT_DIRECTORY "$<1:${VCA_OUTPUT_DIR}>"
            RUNTIME_OUTPUT_DIRECTORY "$<1:${VCA_OUTPUT_DIR}>"
            ARCHIVE_OUTPUT_DIRECTORY "$<1:${VCA_OUTPUT_DIR}/lib>"
        )
    endif()
else()
    target_link_libraries(vconverter_audio PRIVATE log)
endif()
