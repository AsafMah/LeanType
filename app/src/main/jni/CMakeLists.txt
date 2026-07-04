# SPDX-License-Identifier: Apache-2.0
#
# Standalone HOST build of the native latinime unit tests (issue #78).
#
# This is NOT used by the app build (gradle uses ndkBuild via Android.mk). It exists so the
# existing C++ gtest suite under tests/ can be compiled and run on a plain Linux host (CI) WITHOUT
# an AOSP platform checkout — the legacy run-tests.sh / HostUnitTests.mk require the AOSP build
# system (mmm/lunch/BUILD_HOST_NATIVE_TEST) and cannot run in gradle/NDK CI.
#
# Build & run:
#   cmake -S app/src/main/jni -B build-host-tests -DCMAKE_BUILD_TYPE=Release
#   cmake --build build-host-tests -j
#   ctest --test-dir build-host-tests --output-on-failure
#
# Host build => __ANDROID__ is undefined, so defines.h uses its host fallbacks (no android/log.h).

cmake_minimum_required(VERSION 3.14)
project(latinime_host_tests CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

include(FetchContent)
FetchContent_Declare(
    googletest
    URL https://github.com/google/googletest/archive/refs/tags/v1.14.0.zip
)
FetchContent_MakeAvailable(googletest)

set(JNI_DIR ${CMAKE_CURRENT_SOURCE_DIR})
set(SRC_DIR ${JNI_DIR}/src)
set(TEST_DIR ${JNI_DIR}/tests)

# Some core engine headers (e.g. suggest/core/dictionary/dictionary.h) include <jni.h> for type
# signatures (JNIEnv / jintArray / ... — header-only, the host tests never call the JNI bridge, so
# no libjvm is linked). CI provides a JDK via actions/setup-java -> find_package(JNI). For a local
# host build without a JDK, pass -DLATINIME_JNI_INCLUDE=<dir> (e.g. the Android NDK's jni.h dir).
if(LATINIME_JNI_INCLUDE)
    set(JNI_INCLUDE_DIRS ${LATINIME_JNI_INCLUDE})
else()
    find_package(JNI REQUIRED)
endif()

# Engine core: every src/*.cpp EXCEPT the JNI bridge (com_android_*.cpp / jni_common.cpp need
# jni.h and are not part of the host-testable core — see NativeFileList.mk LATIN_IME_CORE_SRC_FILES).
file(GLOB_RECURSE ENGINE_SRC CONFIGURE_DEPENDS ${SRC_DIR}/*.cpp)
list(FILTER ENGINE_SRC EXCLUDE REGEX "(com_android_inputmethod_|jni_common\\.cpp$)")

# gtest suite (LATIN_IME_CORE_TEST_FILES).
file(GLOB_RECURSE TEST_SRC CONFIGURE_DEPENDS ${TEST_DIR}/*.cpp)

add_executable(latinime_host_unittests ${ENGINE_SRC} ${TEST_SRC})
target_include_directories(latinime_host_unittests PRIVATE ${SRC_DIR} ${TEST_DIR} ${JNI_INCLUDE_DIRS})
target_compile_options(latinime_host_unittests PRIVATE
    -include ${CMAKE_CURRENT_SOURCE_DIR}/host_test_compat.h
    -Wno-unused-parameter -Wno-unused-function)
# Expose the on-disk fixture directory to the replay tests so they can load
# JSON fixtures by path (TraceFixtureParserTest.LoadsHelloQwertyFromFile).
target_compile_definitions(latinime_host_unittests PRIVATE
    FIXTURE_DIR="${CMAKE_CURRENT_SOURCE_DIR}/tests/replay/fixtures")
target_link_libraries(latinime_host_unittests PRIVATE gtest gtest_main)

enable_testing()
include(GoogleTest)
gtest_discover_tests(latinime_host_unittests DISCOVERY_TIMEOUT 60)
