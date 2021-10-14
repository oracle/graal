#
# Copyright (c) 2021, Oracle and/or its affiliates.
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
cmake_minimum_required(VERSION 3.10)
project(com.oracle.truffle.llvm.libraries.bitcode)

set(GRAALVM_LLVM_INCLUDE_DIR)

if(NOT GRAALVM_LLVM_INCLUDE_DIR)
   message(FATAL_ERROR "GRAALVM_LLVM_INCLUDE_DIR needs to be set")
endif()

set(LIBCXX_SRC)

if(NOT LIBCXX_SRC)
   message(FATAL_ERROR "LIBCXX_SRC needs to be set")
endif()

set(CMAKE_INSTALL_PREFIX ${CMAKE_BINARY_DIR})
add_compile_options(-fdebug-prefix-map=${CMAKE_SOURCE_DIR}=${CMAKE_PROJECT_NAME})
add_compile_options(-fdebug-prefix-map=${CMAKE_BINARY_DIR}=${CMAKE_PROJECT_NAME})

if (APPLE)
    set(CMAKE_INSTALL_RPATH "@loader_path")
    set(CMAKE_SHARED_LINKER_FLAGS "-Wl,-undefined,suppress -Wl,-flat_namespace ${CMAKE_SHARED_LINKER_FLAGS}")
else()
    set(CMAKE_INSTALL_RPATH "\$ORIGIN")
endif()

if(NOT MX_OS)
   message(FATAL_ERROR "MX_OS needs to be set")
endif()

if(NOT MX_ARCH)
   message(FATAL_ERROR "MX_ARCH needs to be set")
endif()

add_compile_definitions(OS_${MX_OS} ARCH_${MX_ARCH})

# using glob patterns is not recommended: https://cmake.org/cmake/help/latest/command/file.html#glob
add_library(sulong SHARED)

if(WIN32)
    if(NOT LLVM_LINK)
        message(FATAL_ERROR "LLVM_LINK needs to be set")
    endif()
    target_sources(sulong PRIVATE
      src/crt0.c
      src/exit.c
      src/sulong_dispose_context.c
    )
    # link to bitcode
    set(CMAKE_BC_CREATE_SHARED_LIBRARY "${LLVM_LINK} <OBJECTS> -o <TARGET>")
    set_target_properties(sulong PROPERTIES LINKER_LANGUAGE BC)
else()
    target_sources(sulong PRIVATE
      src/abort.c
      src/assert.c
      src/builtin.c
      src/clone.c
      src/complex.c
      src/crt0.c
      src/exec.c
      src/exit.c
      src/fork.c
      src/memcpy.c
      src/pthreads.c
      src/qsort.c
      src/setjmp.c
      src/signals.c
      src/stat.c
      src/string.c
      src/sulong_dispose_context.c
    )
endif()

target_include_directories(sulong PRIVATE ${GRAALVM_LLVM_INCLUDE_DIR})
if(NOT MX_OS STREQUAL "darwin")
    target_link_options(sulong PRIVATE -nostdlib)
endif()
install(TARGETS sulong DESTINATION bin)

if(NOT WIN32)
    add_library(sulong++ SHARED libsulongxx/exception_support.cpp)
    target_include_directories(sulong++ PRIVATE ${LIBCXX_SRC}/libcxx/src ${LIBCXX_SRC}/libcxxabi/src)
    target_compile_definitions(sulong++ PRIVATE LIBCXXABI_SILENT_TERMINATE)
    target_compile_options(sulong++ PRIVATE -std=c++11 -stdlib=libc++ -Wno-undefined-internal)
    target_link_options(sulong++ PRIVATE -stdlib=libc++ -Wno-undefined-internal)
    install(TARGETS sulong++ DESTINATION bin)
endif()
