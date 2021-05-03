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

include_guard(GLOBAL)
include(SulongCommon)

macro(setupCompiler)
    requireVariable(DRAGONEGG_GCC)
    requireVariable(DRAGONEGG_LLVMAS)
    setCompilerConfig(CMAKE_C_COMPILER ${DRAGONEGG_GCC})
    # reset in order to create a log message
    setCompilerConfig(DRAGONEGG_LLVMAS ${DRAGONEGG_LLVMAS})
    # https://gitlab.kitware.com/cmake/community/-/wikis/doc/cmake/Build-Rules
    set(CMAKE_LL_LINK_EXECUTABLE "${DRAGONEGG_LLVMAS} <OBJECTS> -o <TARGET>")
    noFortranSupport()
    noSharedObjectSupport()
endmacro()

macro(setupOptions)
    # this must be called after compiler checks

    # check compiler flags before messing around with the compiler options
    requireVariable(DRAGONEGG)
    set(DRAGONEGG_FLAGS "-w -S --std=gnu99 -fplugin=${DRAGONEGG} -fplugin-arg-dragonegg-emit-ir")
    include(CheckCCompilerFlag)
    # the following voids running the linker
    set(CMAKE_TRY_COMPILE_TARGET_TYPE STATIC_LIBRARY)
    check_c_compiler_flag("${DRAGONEGG_FLAGS}" GCC_HAS_DRAGONEGG_SUPPORT)
    if(NOT GCC_HAS_DRAGONEGG_SUPPORT)
        message(FATAL_ERROR "No dragon-egg support")
    endif()

    string(APPEND CMAKE_C_FLAGS " ${DRAGONEGG_FLAGS}")

    set(OUTPUT "${SULONG_CURRENT_VARIANT}.bc")

    # set optimization levels
    set(OPT_LEVELS "O0;O1;O2;O3")

    if(SULONG_CURRENT_OPT_LEVEL IN_LIST OPT_LEVELS)
        string(APPEND CMAKE_C_FLAGS " -${SULONG_CURRENT_OPT_LEVEL}")
    elseif(SULONG_CURRENT_OPT_LEVEL)
        # non-empty but not in the known list
        message(FATAL_ERROR "Unknonw opt-level: ${SULONG_CURRENT_OPT_LEVEL}")
    endif()

    if(SULONG_CURRENT_POST_OPT)
        message(WARNING "post-opt is only meaningful for compile-mode 'bitcode', current mode: ${SULONG_CURRENT_COMPILE_MODE}")
    endif()
endmacro()

macro(targetPostProcess SOURCE TARGET OUTPUT_DIR OUTPUT)
    # link with LL
    set_target_properties(${TARGET} PROPERTIES LINKER_LANGUAGE LL)
endmacro()
