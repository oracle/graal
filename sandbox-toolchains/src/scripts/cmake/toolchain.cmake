#
# Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or
# data (collectively the "Software"), free of charge and under any and all
# copyright rights in the Software, and any and all patent rights owned or
# freely licensable by each licensor hereunder covering either (i) the
# unmodified Software as contributed to or provided by such licensor, or (ii)
# the Larger Works (as defined below), to deal in both
#
# (a) the Software, and
#
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
# one is included with the Software each a "Larger Work" to which the Software
# is contributed by such licensors),
#
# without restriction, including without limitation the rights to copy, create
# derivative works of, display, perform, and distribute the Software and make,
# use, sell, offer for sale, import, export, have made, and have sold the
# Software and the Larger Work(s), and to sublicense the foregoing rights on
# either these or other terms.
#
# This license is subject to the following condition:
#
# The above copyright notice and either this complete permission notice or at a
# minimum a reference to the UPL must be included in all copies or substantial
# portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.
#

# Resolve symlinks first so the shared toolchain file can be referenced either
# via toolchains/musl-*/lib/cmake or directly from toolchains/sysroot/.../lib/cmake.
get_filename_component(_toolchainFileRealpath "${CMAKE_CURRENT_LIST_FILE}" REALPATH)
get_filename_component(_ownDir "${_toolchainFileRealpath}" DIRECTORY)
set(_modulesDir "${_ownDir}/Modules")

include("${_ownDir}/GraalOSToolchainVariant.cmake")

# Target-specific settings
set(CMAKE_SYSTEM_NAME Generic)
set(CMAKE_SYSTEM_PROCESSOR "${GRAALOS_SYSTEM_PROCESSOR}")

get_filename_component(GRAALOS_SYSROOT "${_ownDir}/../.." REALPATH)
get_filename_component(_toolchainsDir "${GRAALOS_SYSROOT}/../.." REALPATH)
get_filename_component(GRAALOS_TOOLCHAIN_ROOT "${_toolchainsDir}/${GRAALOS_TOOLCHAIN_NAME}" REALPATH)

get_filename_component(_sysrootName "${GRAALOS_SYSROOT}" NAME)
if(NOT _sysrootName STREQUAL "${GRAALOS_TARGET_TRIPLE}")
    message(FATAL_ERROR
        "GraalOS toolchain variant mismatch: expected sysroot ${GRAALOS_TARGET_TRIPLE}, "
        "got ${_sysrootName} from ${CMAKE_CURRENT_LIST_FILE}")
endif()

if(NOT EXISTS "${GRAALOS_TOOLCHAIN_ROOT}/bin/clang")
    message(FATAL_ERROR "Resolved GraalOS toolchain root does not contain bin/clang: ${GRAALOS_TOOLCHAIN_ROOT}")
endif()

# Define system root and library paths
set(CMAKE_SYSROOT "${GRAALOS_SYSROOT}")
set(CMAKE_FIND_ROOT_PATH "${GRAALOS_SYSROOT}")
set(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER) # Don't search system paths for programs
set(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY ONLY)  # Search only toolchain paths for libraries
set(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE ONLY)  # Search only toolchain paths for includes

# Configure build environment variables
set(ENV{PATH} "${GRAALOS_TOOLCHAIN_ROOT}/bin:$ENV{PATH}")

# Define paths for find commands
set(CMAKE_FIND_ROOT_PATH "${CMAKE_SYSROOT}")
set(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)
set(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY ONLY)
set(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE ONLY)

# Update the module search path so that cmake can find the platform config file
list(APPEND CMAKE_MODULE_PATH "${_modulesDir}")

############################################################################################
# include the default modules for building GraalOS apps:
include(GraalOSToolchainPaths)
