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

# This is the main CMake configuration for building SulongCMakeTestSuite projects.
# Note that only a single variant (SULONG_CURRENT_VARIANT) is built at a time and each variant are built individually.

# Usage
#######
#
# For the `suite.py` side of things, refer to the documentation of the `SulongCMakeTestSuite` class.
#
# Every test project needs a `CMakeLists.txt`. The project is mainly configured in project definition of `suite.py`,
# so the following is usually sufficient:
# ```
#   cmake_minimum_required(VERSION 3.10)
#   project(${SULONG_PROJECT_NAME} LANGUAGES NONE)
#
#   include(SulongTestSuite)
# ```
# (The variable ${SULONG_PROJECT_NAME} is set automatically.)
# However, the `CMakeLists.txt` can be used for further specializing a built. For an examplesee `add_test_dependency`
# below.

include_guard(GLOBAL)
include(SulongCommon)

# CMake configuration variables
###############################
# The following variables can be used to customize the build in the suite.py.

# Flags
# -----

# Many standard cmake variables are respected.
# See https://cmake.org/cmake/help/latest/manual/cmake-variables.7.html for more information.
# Useful standard cmake variables:
#   CMAKE_C_FLAGS - Flags used by the C compiler for compiling AND linking
#   CMAKE_CXX_FLAGS - Flags used by the C++ compiler for compiling AND linking
#   CMAKE_C_LINK_FLAGS - Flags used by the C compiler linking
#   CMAKE_CXX_LINK_FLAGS - Flags used by the C++ compiler linking
#   CMAKE_SHARED_LINKER_FLAGS - Flags used by the linker when linking shared objects
#   CMAKE_EXE_LINKER_FLAGS - Flags used by the linker when linking executables

# Default flags for C/C++
set(SULONG_C_FLAGS "-g" CACHE STRING "Default C flags  (use CMAKE_C_FLAGS for setting additional flags)")
set(SULONG_CXX_FLAGS "-std=c++11 -stdlib=libc++ -g" CACHE STRING "Default C++ flags (use CMAKE_CXX_FLAGS for setting additional flags)")

# Tools
# -----
# The following tools are used by one or multiple variants.
# Their default value is set via SulongCMakeTestSuite, but can be overridden in suite.py.
set(FC "" CACHE STRING "Fortran Compiler")
set(CLANG "" CACHE STRING "Clang used for compiling C sources (native and bitcode)")
set(CLANGXX "" CACHE STRING "Clang++ used for compiling C++ sources (native and bitcode)")
set(LLVM_LINK "" CACHE STRING "LLVM bitcode linker")
set(LLVM_AS "" CACHE STRING "LLVM bitcode assembler")
set(LLVM_OPT "" CACHE STRING "LLVM opt tool")
set(LLVM_OBJCOPY "" CACHE STRING "llvm-objcopy (for native object files like elf)")
set(LLVM_CONFIG "" CACHE STRING "llvm-config utility for setting up library paths")
set(DRAGONEGG "" CACHE STRING "DRAGONEGG plugin path")
set(DRAGONEGG_GCC "" CACHE STRING "DRAGONEGG enabled gcc")
set(DRAGONEGG_FC "" CACHE STRING "DRAGONEGG enabled Fortran compiler")
set(DRAGONEGG_LLVM_LINK "" CACHE STRING "llvm-link compatible with the DRAGONEGG LLVM version")
set(DRAGONEGG_LLVMAS "" CACHE STRING "llvm-as compatible with the DRAGONEGG LLVM version")
# not set by default
set(TOOLCHAIN_CLANG "" CACHE STRING "Toolchain Wrapper for clang used by the 'bitcode' variant")
set(TOOLCHAIN_CLANGXX "" CACHE STRING "Toolchain Wrapper for clang++ used by the 'bitcode' variant")

# Environment Variables
# ---------------------
# variables that can be overridden via the environment (select different LLVM versions in the CI config)
setFromEnv(LLVM_AS CLANG_LLVM_AS)
setFromEnv(LLVM_LINK CLANG_LLVM_LINK)
setFromEnv(LLVM_DIS CLANG_LLVM_DIS)
setFromEnv(LLVM_OPT CLANG_LLVM_OPT)
setFromEnv(LLVM_CONFIG CLANG_LLVM_CONFIG)
setFromEnv(LLVM_OBJCOPY CLANG_LLVM_OBJCOPY)
# set up a compiler from an environment variable
# Example: export CLANG_CC="clang-8 -v" will result in CLANG=clang-8 CMAKE_C_FLAGS+=" -v"
setCompilerFromEnv(CLANG CMAKE_C_FLAGS CLANG_CC)
setCompilerFromEnv(CLANGXX CMAKE_CXX_FLAGS CLANG_CXX)


# Helper Functions
##################

# Add a dependency to a test.
#
# Parameters:
#   - TEST: the test source file, e.g., mytests/bitcode-test.ll
#   - DEPENDENCY: the dependency that should be linked in, e.g., mytests/common.c
#
# Given on the current variant, the test and the dependency might be linked via different tools,
# e.g., `llvm-link` for the bitcode variant or the toolchain linker for the toolchain variant.
# Not all combinations of test and dependency types will work. Known to work combinations are
# *.ll tests linked with *.c files. For non-standard file extentions, the languange of the
# dependency might need to be set manually:
#   set_source_files_properties(mytests/common.cmain PROPERTIES LANGUAGE C)
#
function(add_test_dependency TEST DEPENDENCY)
    get_target(TARGET ${TEST})
    target_sources(${TARGET} PRIVATE ${DEPENDENCY})
endfunction()

# include the actual implementation
include(SulongTestSuiteImpl)