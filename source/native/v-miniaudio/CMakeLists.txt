cmake_minimum_required(VERSION 3.16)

project(VMiniAudio
    VERSION 0.2.0
    DESCRIPTION "Lightweight audio framework written in C"
    LANGUAGES C
)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_C_EXTENSIONS OFF)

include(GNUInstallDirs)
include(CMakePackageConfigHelpers)

option(VMA_BUILD_SHARED "Build shared library" OFF)
option(VMA_BUILD_EXAMPLES "Build examples" OFF)
option(VMA_BUILD_TESTS "Build tests" OFF)
option(VMA_ENABLE_LOGGING "Enable logging" ON)
option(VMA_ENABLE_VORBIS "Decode Ogg Vorbis with stb_vorbis" ON)

set(VMA_SOURCES
    src/VAudio.c
    src/VAudioEngine.c
    src/VAudioPlayer.c
    src/VAudioSource.c
    src/VAudioBus.c
    src/VWaveform.c
)

set(VMA_HEADERS
    include/vminiaudio/VAudio.h
    include/vminiaudio/VAudioEngine.h
    include/vminiaudio/VAudioPlayer.h
    include/vminiaudio/VAudioTypes.h
    include/vminiaudio/VWaveform.h
)

if(VMA_BUILD_SHARED)
    add_library(VMiniAudio SHARED
        ${VMA_SOURCES}
        ${VMA_HEADERS}
    )
else()
    add_library(VMiniAudio STATIC
        ${VMA_SOURCES}
        ${VMA_HEADERS}
    )
endif()

add_library(VMiniAudio::VMiniAudio ALIAS VMiniAudio)

target_include_directories(VMiniAudio
    PUBLIC
        $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/include>
        $<INSTALL_INTERFACE:${CMAKE_INSTALL_INCLUDEDIR}>
)

target_compile_features(VMiniAudio PUBLIC c_std_11)

set_target_properties(VMiniAudio PROPERTIES
    OUTPUT_NAME "vminiaudio"
    VERSION ${PROJECT_VERSION}
    SOVERSION ${PROJECT_VERSION_MAJOR}
    POSITION_INDEPENDENT_CODE ON
)

if(NOT VMA_ENABLE_VORBIS)
    target_compile_definitions(VMiniAudio PRIVATE VMA_NO_VORBIS=1)
endif()

if(VMA_ENABLE_LOGGING)
    target_compile_definitions(VMiniAudio
        PRIVATE VMA_ENABLE_LOGGING=1
    )
endif()

if(WIN32)
    target_compile_definitions(VMiniAudio
        PRIVATE VMA_PLATFORM_WINDOWS=1
    )


elseif(ANDROID)
    target_compile_definitions(VMiniAudio
        PRIVATE VMA_PLATFORM_ANDROID=1
    )

    target_link_libraries(VMiniAudio PRIVATE
        android
        log
    )

elseif(APPLE)
    target_compile_definitions(VMiniAudio
        PRIVATE VMA_PLATFORM_APPLE=1
    )

    find_library(COREAUDIO_FRAMEWORK CoreAudio)
    find_library(AUDIOTOOLBOX_FRAMEWORK AudioToolbox)
    find_library(COREFOUNDATION_FRAMEWORK CoreFoundation)

    target_link_libraries(VMiniAudio PRIVATE
        ${COREAUDIO_FRAMEWORK}
        ${AUDIOTOOLBOX_FRAMEWORK}
        ${COREFOUNDATION_FRAMEWORK}
    )

elseif(UNIX)
    target_compile_definitions(VMiniAudio
        PRIVATE VMA_PLATFORM_LINUX=1
    )

    find_package(Threads REQUIRED)

    target_link_libraries(VMiniAudio
        PUBLIC Threads::Threads m
    )
endif()

if(VMA_BUILD_EXAMPLES)
    if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/examples/main.c")
        add_executable(vma_example examples/main.c)

        target_link_libraries(vma_example
            PRIVATE VMiniAudio::VMiniAudio
        )
    endif()
endif()

if(VMA_BUILD_TESTS)
    enable_testing()
    if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/tests/test_audio.c")
        add_executable(vma_tests tests/test_audio.c)

        target_link_libraries(vma_tests
            PRIVATE VMiniAudio::VMiniAudio
        )

        if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/../../../assets/sounds/scrollMenu.ogg")
            target_compile_definitions(vma_tests PRIVATE VMA_TEST_OGG="${CMAKE_CURRENT_SOURCE_DIR}/../../../assets/sounds/scrollMenu.ogg")
        endif()

        add_test(NAME VMiniAudioTests COMMAND vma_tests)
    endif()
endif()

install(TARGETS VMiniAudio
    EXPORT VMiniAudioTargets
    ARCHIVE DESTINATION ${CMAKE_INSTALL_LIBDIR}
    LIBRARY DESTINATION ${CMAKE_INSTALL_LIBDIR}
    RUNTIME DESTINATION ${CMAKE_INSTALL_BINDIR}
)

install(DIRECTORY include/vminiaudio
    DESTINATION ${CMAKE_INSTALL_INCLUDEDIR}
)

install(EXPORT VMiniAudioTargets
    FILE VMiniAudioTargets.cmake
    NAMESPACE VMiniAudio::
    DESTINATION ${CMAKE_INSTALL_LIBDIR}/cmake/VMiniAudio
)

configure_package_config_file(
    "${CMAKE_CURRENT_SOURCE_DIR}/cmake/VMiniAudioConfig.cmake.in"
    "${CMAKE_CURRENT_BINARY_DIR}/VMiniAudioConfig.cmake"
    INSTALL_DESTINATION
        "${CMAKE_INSTALL_LIBDIR}/cmake/VMiniAudio"
)

write_basic_package_version_file(
    "${CMAKE_CURRENT_BINARY_DIR}/VMiniAudioConfigVersion.cmake"
    VERSION ${PROJECT_VERSION}
    COMPATIBILITY SameMajorVersion
)

install(FILES
    "${CMAKE_CURRENT_BINARY_DIR}/VMiniAudioConfig.cmake"
    "${CMAKE_CURRENT_BINARY_DIR}/VMiniAudioConfigVersion.cmake"
    DESTINATION "${CMAKE_INSTALL_LIBDIR}/cmake/VMiniAudio"
)
