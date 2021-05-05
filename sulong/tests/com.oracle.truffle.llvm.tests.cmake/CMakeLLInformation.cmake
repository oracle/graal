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

set(CMAKE_LL_OUTPUT_EXTENSION .o)

# https://github.com/Kitware/CMake/blob/master/Modules/CMakeAddNewLanguage.txt

# compile an LL file into an object file
if(NOT CMAKE_LL_COMPILE_OBJECT)
  set(CMAKE_LL_COMPILE_OBJECT
    "<CMAKE_LL_COMPILER> <FLAGS> -o <OBJECT> <SOURCE>")
endif()

# create an LL shared library (just llvm-link)
if(NOT CMAKE_LL_CREATE_SHARED_LIBRARY)
  set(CMAKE_LL_CREATE_SHARED_LIBRARY
    "<CMAKE_LL_COMPILER> ${LLVM_LINK_OPTIONS} <OBJECTS> -o <TARGET>")
endif()

# create an LL shared module just copy the shared library rule
if(NOT CMAKE_LL_CREATE_SHARED_MODULE)
  set(CMAKE_LL_CREATE_SHARED_MODULE ${CMAKE_LL_CREATE_SHARED_LIBRARY})
endif()

# create an LL executable just copy the shared library rule
if(NOT CMAKE_LL_LINK_EXECUTABLE)
  set(CMAKE_LL_LINK_EXECUTABLE ${CMAKE_LL_CREATE_SHARED_LIBRARY})
endif()
