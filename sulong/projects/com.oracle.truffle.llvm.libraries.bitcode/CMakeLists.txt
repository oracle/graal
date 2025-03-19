#
# Copyright (c) 2021, 2025, Oracle and/or its affiliates.
#
# All rights reserved.
#
# Redistribution and use in source and binary forms, with or without modification, are
# permitted provided that the following conditions are met:
#
# 1. Redistributions of source code must retain the above copyright notice, this list of
# conditions and the following disclaimer.
#
# 2. Redistributions in binary form must reproduce the above copyright notice, this list of
# conditions and the following disclaimer in the documentation and/or other materials provided
# with the distribution.
#
# 3. Neither the name of the copyright holder nor the names of its contributors may be used to
# endorse or promote products derived from this software without specific prior written
# permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
# OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
# MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
# COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
# EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
# GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
# AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
# NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
# OF THE POSSIBILITY OF SUCH DAMAGE.
#
cmake_minimum_required(VERSION 3.15)
project(com.oracle.truffle.llvm.libraries.bitcode)

function(require_var var)
  if (NOT ${var})
    message(FATAL_ERROR "${var} needs to be set")
  endif()
endfunction()

function(check_var var)
  set(${var} PARENT_SCOPE)
  require_var(${var})
endfunction()

# set variable from environement variable if the latter exists
function(setFromEnv varname envname)
  if(DEFINED ENV{${envname}})
    set(${varname} $ENV{${envname}} PARENT_SCOPE)
  endif()
endfunction()

check_var(GRAALVM_LLVM_INCLUDE_DIR)
check_var(GRAALVM_LLVM_LIBS_INCLUDE_DIR)
check_var(LIBCXX_SRC)

set(CMAKE_INSTALL_PREFIX ${CMAKE_BINARY_DIR})
add_compile_options(-ffile-prefix-map=${CMAKE_SOURCE_DIR}=${CMAKE_PROJECT_NAME})
add_compile_options(-ffile-prefix-map=${CMAKE_BINARY_DIR}=${CMAKE_PROJECT_NAME})
add_compile_options(-ffile-prefix-map=${GRAALVM_LLVM_INCLUDE_DIR}=com.oracle.truffle.llvm.libraries.graalvm.llvm/include)
add_compile_options(-ffile-prefix-map=${GRAALVM_LLVM_LIBS_INCLUDE_DIR}=com.oracle.truffle.llvm.libraries.graalvm.llvm.libs/include)
add_compile_options(-ffile-prefix-map=${LIBCXX_SRC}=llvm-project)

if (APPLE)
    set(CMAKE_INSTALL_RPATH "@loader_path")
    set(CMAKE_SHARED_LINKER_FLAGS "-Wl,-undefined,suppress -Wl,-flat_namespace ${CMAKE_SHARED_LINKER_FLAGS}")
else()
    set(CMAKE_INSTALL_RPATH "\$ORIGIN")
endif()

require_var(MX_OS)
require_var(MX_ARCH)

add_compile_definitions(OS_${MX_OS} ARCH_${MX_ARCH})

# using glob patterns is not recommended: https://cmake.org/cmake/help/latest/command/file.html#glob
add_library(sulong SHARED)

target_sources(sulong PRIVATE
  src/abort.c
  src/crt0.c
  src/exit.c
  src/pthreads.c
  src/string.c
  src/sulong_dispose_context.c
)

if(WIN32)
    check_var(LIBCXX_ISYSTEM)
    check_var(GRAALVM_LLVM_LIB_DIR)
    check_var(GRAALVM_PTHREAD_INCLUDE_DIR)

    target_link_directories(sulong PRIVATE ${GRAALVM_LLVM_LIB_DIR})
    target_link_libraries(sulong graalvm-llvm)
    target_include_directories(sulong PRIVATE ${GRAALVM_PTHREAD_INCLUDE_DIR})
    target_sources(sulong PRIVATE
      sulong.def
      src/windows/assert.c
    )
else()
    target_sources(sulong PRIVATE
      src/assert.c
      src/builtin.c
      src/clone.c
      src/complex.c
      src/exec.c
      src/fork.c
      src/memcpy.c
      src/qsort.c
      src/setjmp.c
      src/signals.c
      src/stat.c
    )
endif()

include(CheckIncludeFile)
CHECK_INCLUDE_FILE(threads.h SUPPORTS_C11_THREADS)
if(SUPPORTS_C11_THREADS AND NOT WIN32)
  target_sources(sulong PRIVATE src/c11threads.c)
else()
  message(NOTICE "Compiling without C11 threads because threads.h is not available.")
endif()

target_include_directories(sulong PRIVATE ${GRAALVM_LLVM_INCLUDE_DIR} ${GRAALVM_LLVM_LIBS_INCLUDE_DIR})
if(NOT MX_OS STREQUAL "darwin")
    target_link_options(sulong PRIVATE -nostdlib)
endif()
install(TARGETS sulong DESTINATION bin)


# sulong++ library

add_library(sulong++ SHARED)
target_include_directories(sulong++ PRIVATE ${LIBCXX_SRC}/libcxx/src ${LIBCXX_SRC}/libcxxabi/src ${LIBCXX_SRC}/libcxxabi/include)
target_include_directories(sulong++ PRIVATE ${GRAALVM_LLVM_INCLUDE_DIR} ${GRAALVM_LLVM_LIBS_INCLUDE_DIR})
target_compile_definitions(sulong++ PRIVATE LIBCXXABI_SILENT_TERMINATE)
target_compile_options(sulong++ PRIVATE -std=c++14 -stdlib=libc++ -Wno-undefined-internal)
target_link_options(sulong++ PRIVATE -stdlib=libc++ -Wno-undefined-internal)
install(TARGETS sulong++ DESTINATION bin)

if(NOT WIN32)
  target_sources(sulong++ PRIVATE libsulongxx/exception_support.cpp)
else()
  setFromEnv(VCTOOLS_INSTALL_DIR VCToolsInstallDir)
  set(VCRUNTIME_SRC ${VCTOOLS_INSTALL_DIR}/crt/src/vcruntime)

  target_include_directories(sulong++ PRIVATE ${VCRUNTIME_SRC})
  target_link_directories(sulong++ PRIVATE ${GRAALVM_LLVM_LIB_DIR})
  target_link_libraries(sulong++ graalvm-llvm)
  target_compile_options(sulong++ PRIVATE -stdlib++-isystem ${LIBCXX_ISYSTEM})
  target_link_options(sulong++ PRIVATE -nostdlib)

  target_sources(sulong++ PRIVATE libsulongxx/windows/sulong++.def)
  target_sources(sulong++ PRIVATE libsulongxx/windows/exception_helpers.cpp libsulongxx/windows/type_info.cpp)
  if(MSVC_VERSION GREATER_EQUAL 1939)
    target_compile_definitions(sulong++ PRIVATE USE_THREAD_WAIT_V2)
  endif()
  target_sources(sulong++ PRIVATE libsulongxx/windows/cpp_threads.cpp)

  target_compile_definitions(sulong++ PRIVATE _VCRT_BUILD)
  # source of _Init_thread_* functions
  target_sources(sulong++ PUBLIC ${VCRUNTIME_SRC}/thread_safe_statics.cpp)
  set_source_files_properties(${VCRUNTIME_SRC}/thread_safe_statics.cpp PROPERTIES COMPILE_FLAGS "-O0") # prevent inlining of init thread functions
  # source of operator new functions
  target_sources(sulong++ PUBLIC
    ${VCRUNTIME_SRC}/delete_array.cpp
    ${VCRUNTIME_SRC}/delete_array_nothrow.cpp
    ${VCRUNTIME_SRC}/delete_array_size.cpp
    ${VCRUNTIME_SRC}/delete_scalar.cpp
    ${VCRUNTIME_SRC}/delete_scalar_nothrow.cpp
    ${VCRUNTIME_SRC}/delete_scalar_size.cpp
    ${VCRUNTIME_SRC}/new_array.cpp
    ${VCRUNTIME_SRC}/new_array_nothrow.cpp
    ${VCRUNTIME_SRC}/new_scalar.cpp
    ${VCRUNTIME_SRC}/new_scalar_nothrow.cpp
  )

  set_source_files_properties(libsulongxx/windows/type_info.cpp PROPERTIES COMPILE_FLAGS -fno-rtti)
endif()
