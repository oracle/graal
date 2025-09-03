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
project(com.oracle.truffle.llvm.libraries.native LANGUAGES C)

set(TRUFFLE_NFI_NATIVE_INCLUDE)

if(NOT TRUFFLE_NFI_NATIVE_INCLUDE)
   message(FATAL_ERROR "TRUFFLE_NFI_NATIVE_INCLUDE needs to be set")
endif()

set(CMAKE_INSTALL_PREFIX ${CMAKE_BINARY_DIR})
add_compile_options(-ffile-prefix-map=${CMAKE_SOURCE_DIR}=${CMAKE_PROJECT_NAME})
add_compile_options(-ffile-prefix-map=${CMAKE_BINARY_DIR}=${CMAKE_PROJECT_NAME})
add_compile_options(-ffile-prefix-map=${TRUFFLE_NFI_NATIVE_INCLUDE}=TRUFFLE_NFI_NATIVE/include)

add_library(sulong-native SHARED
    src/misc.c
    src/longdouble.c
    src/globals.c
)
if(NOT WIN32)
    target_sources(sulong-native PRIVATE
        src/posix.c
        src/stat.c
    )
endif()

target_include_directories(sulong-native PRIVATE ${TRUFFLE_NFI_NATIVE_INCLUDE})
install(TARGETS sulong-native DESTINATION bin)
