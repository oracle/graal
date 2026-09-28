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

set(CMAKE_SYSTEM_NAME Linux)

# Specify the compilers to use
set(CMAKE_C_COMPILER "${GRAALOS_TOOLCHAIN_ROOT}/bin/clang" CACHE FILEPATH "GraalVM for GraalOS C compiler with SWCFI")
set(CMAKE_CXX_COMPILER "${GRAALOS_TOOLCHAIN_ROOT}/bin/clang++" CACHE FILEPATH "GraalVM for GraalOS C++ compiler with SWCFI")
set(CMAKE_ASM_COMPILER "${GRAALOS_TOOLCHAIN_ROOT}/bin/clang" CACHE FILEPATH "GraalVM for GraalOS assembler with SWCFI")

# Set additional toolchain-specific variables
set(CMAKE_AR "${GRAALOS_TOOLCHAIN_ROOT}/bin/llvm-ar" CACHE FILEPATH "GraalVM for GraalOS archiver")
set(CMAKE_RANLIB "${GRAALOS_TOOLCHAIN_ROOT}/bin/llvm-ranlib" CACHE FILEPATH "GraalVM for GraalOS ranlib")
set(CMAKE_LINKER "${GRAALOS_TOOLCHAIN_ROOT}/bin/ld" CACHE FILEPATH "GraalVM for GraalOS linker")
set(CMAKE_NM "${GRAALOS_TOOLCHAIN_ROOT}/bin/llvm-nm" CACHE FILEPATH "GraalVM for GraalOS nm")
set(CMAKE_READELF "${GRAALOS_TOOLCHAIN_ROOT}/bin/llvm-readelf" CACHE FILEPATH "GraalVM for GraalOS readelf")

# Build a small source file to identify the compiler:
set(CMAKE_C_COMPILER_ID_RUN TRUE)
set(CMAKE_CXX_COMPILER_ID_RUN TRUE)

set(CMAKE_CROSSCOMPILING_EMULATOR "${GRAALOS_SYSROOT}/lib/ld-musl-x86_64.so.1")
