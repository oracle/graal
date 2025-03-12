#
# Copyright (c) 2022, 2025, Oracle and/or its affiliates.
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

macro(set_ifndef VAR VALUE)
  if(DEFINED ${VAR})
    message(VERBOSE "  - ${VAR} is already set to '${${VAR}}'")
  else()
    set(${VAR} ${VALUE})
  endif()
endmacro()

function(find_tc_file RESULT_VAR FILE)
  if(DEFINED SULONG_CMD_SUFFIX)
    foreach(EXT IN LISTS SULONG_CMD_SUFFIX)
      if(EXISTS "${FILE}${EXT}")
        set(${RESULT_VAR} ${FILE}${EXT} PARENT_SCOPE)
        return()
      endif()
    endforeach()
  elseif(EXISTS "${FILE}")
    set(${RESULT_VAR} ${FILE} PARENT_SCOPE)
    return()
  endif()
  message(FATAL_ERROR "Could not find ${FILE}")
endfunction()

macro(find_tc_ifndef VAR VALUE)
  if(DEFINED ${VAR})
    message(VERBOSE "  - ${VAR} is already set to '${${VAR}}'")
  else()
    find_tc_file(${VAR} ${VALUE})
  endif()
endmacro()

set_ifndef(SULONG_TOOLCHAIN_PATH ${CMAKE_CURRENT_LIST_DIR}/..)

message(VERBOSE "Using Sulong toolchain at ${SULONG_TOOLCHAIN_PATH}")

set_ifndef(SULONG_TOOLCHAIN_BIN ${SULONG_TOOLCHAIN_PATH}/bin)

if(WIN32)
  set_ifndef(SULONG_CMD_SUFFIX ".cmd" ".exe")
  set_ifndef(CMAKE_MSVC_RUNTIME_LIBRARY "MultiThreadedDLL")
  set_ifndef(CMAKE_RC_COMPILER ${GRAAL_HOME}/lib/llvm/llvm-rc.exe)

  # Set the build profile to sulong. This prevents CMake from adding additional
  # flags such as -O0 which preventing interop from working.
  set_ifndef(CMAKE_BUILD_TYPE "Sulong")

  # For cmake versions before 3.15 with old CMP0091 handling
  if(SULONG_CMAKE_PRE_315)
    set(CMAKE_C_FLAGS_SULONG_INIT "-D_DLL -D_MT -Xclang --dependent-lib=msvcrt")
    set(CMAKE_CXX_FLAGS_SULONG_INIT "-D_DLL -D_MT -Xclang --dependent-lib=msvcrt")
  endif()
endif()

find_tc_ifndef(CMAKE_C_COMPILER ${SULONG_TOOLCHAIN_BIN}/clang)
find_tc_ifndef(CMAKE_CXX_COMPILER ${SULONG_TOOLCHAIN_BIN}/clang++)
find_tc_ifndef(CMAKE_AR ${SULONG_TOOLCHAIN_BIN}/llvm-ar)
find_tc_ifndef(CMAKE_NM ${SULONG_TOOLCHAIN_BIN}/llvm-nm)

set_ifndef(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)

execute_process(
    COMMAND ${CMAKE_C_COMPILER} -print-resource-dir
    OUTPUT_VARIABLE clang_resource_dir
    OUTPUT_STRIP_TRAILING_WHITESPACE)

# convert llvm-dir/lib/clang/18 to just llvm-dir/
get_filename_component(clang_lib_dir ${clang_resource_dir} DIRECTORY)
get_filename_component(clang_lib_dir2 ${clang_lib_dir} DIRECTORY)
get_filename_component(llvm_dir ${clang_lib_dir2} DIRECTORY)

# add file-prefix-map, but not for the "LL" language
# the LL language is used by our test bulid scripts to refer to the llvm-link command, it doesn't understand this option
add_compile_options($<IF:$<COMPILE_LANGUAGE:LL>,,-ffile-prefix-map=${llvm_dir}=LLVM_TOOLCHAIN>)
