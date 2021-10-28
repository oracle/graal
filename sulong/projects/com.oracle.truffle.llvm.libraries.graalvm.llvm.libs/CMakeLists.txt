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
project(com.oracle.truffle.llvm.libraries.graalvm.llvm.libs LANGUAGES C)

set(GRAALVM_LLVM_INCLUDE_DIR)

if(NOT GRAALVM_LLVM_INCLUDE_DIR)
   message(FATAL_ERROR "GRAALVM_LLVM_INCLUDE_DIR needs to be set")
endif()

set(CMAKE_INSTALL_PREFIX ${CMAKE_BINARY_DIR})
add_compile_options(-fdebug-prefix-map=${CMAKE_SOURCE_DIR}=${CMAKE_PROJECT_NAME})
add_compile_options(-fdebug-prefix-map=${CMAKE_BINARY_DIR}=${CMAKE_PROJECT_NAME})

if (APPLE)
    set(CMAKE_INSTALL_RPATH "@loader_path")
else()
    set(CMAKE_INSTALL_RPATH "\$ORIGIN")
endif()

# using glob patterns is not recommended: https://cmake.org/cmake/help/latest/command/file.html#glob
add_library(graalvm-llvm SHARED
    src/common.c
    src/handles.c
    src/polyglot.c
    src/toolchain-api.c
    src/truffle-compat.c
)
target_include_directories(graalvm-llvm PRIVATE ${GRAALVM_LLVM_INCLUDE_DIR})
set_target_properties(graalvm-llvm PROPERTIES VERSION 1)
if (WIN32)
    if(NOT LLVM_LINK)
        message(FATAL_ERROR "LLVM_LINK needs to be set")
    endif()
    # no idea why we need to set this here but not for libsulong
    set_target_properties(graalvm-llvm PROPERTIES PREFIX "")
    # link to bitcode
    set(CMAKE_BC_CREATE_SHARED_LIBRARY "${LLVM_LINK} <OBJECTS> -o <TARGET>")
    set_target_properties(graalvm-llvm PROPERTIES LINKER_LANGUAGE BC)
endif()
install(TARGETS graalvm-llvm DESTINATION bin)
