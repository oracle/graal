#
# Copyright (c) 2022, Oracle and/or its affiliates.
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

cmake_path(SET SULONG_TOOLCHAIN_FILE NORMALIZE "${CMAKE_TOOLCHAIN_FILE}")
cmake_path(GET SULONG_TOOLCHAIN_FILE PARENT_PATH SULONG_TOOLCHAIN_CMAKE_PATH)
cmake_path(GET SULONG_TOOLCHAIN_CMAKE_PATH PARENT_PATH SULONG_TOOLCHAIN_PATH)
cmake_path(GET SULONG_TOOLCHAIN_PATH PARENT_PATH GRAAL_LANGUAGES_PATH)
cmake_path(GET GRAAL_LANGUAGES_PATH PARENT_PATH GRAAL_HOME)

message("Using Sulong toolchain at ${SULONG_TOOLCHAIN_PATH}")

set(SULONG_TOOLCHAIN_BIN ${SULONG_TOOLCHAIN_PATH}/bin)

if(WIN32)
  set(SULONG_CMD_SUFFIX ".cmd")
  set(CMAKE_LINKER ${SULONG_TOOLCHAIN_BIN}/lld-link${SULONG_CMD_SUFFIX})
  set(CMAKE_RC_COMPILER ${GRAAL_HOME}/lib/llvm/llvm-rc.exe)
elseif(APPLE)
  set(SULONG_CMD_SUFFIX "")
  set(CMAKE_LINKER ${SULONG_TOOLCHAIN_BIN}/ld64.lld${SULONG_CMD_SUFFIX})
else()
  set(SULONG_CMD_SUFFIX "")
  set(CMAKE_LINKER ${SULONG_TOOLCHAIN_BIN}/ld.lld${SULONG_CMD_SUFFIX})
endif()

set(CMAKE_C_COMPILER ${SULONG_TOOLCHAIN_BIN}/clang${SULONG_CMD_SUFFIX})
set(CMAKE_CXX_COMPILER ${SULONG_TOOLCHAIN_BIN}/clang++${SULONG_CMD_SUFFIX})
set(CMAKE_AR ${SULONG_TOOLCHAIN_BIN}/llvm-ar${SULONG_CMD_SUFFIX})

set(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)
