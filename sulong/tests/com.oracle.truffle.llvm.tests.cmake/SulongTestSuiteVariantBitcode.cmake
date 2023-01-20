#
# Copyright (c) 2021, 2022, Oracle and/or its affiliates.
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
    requireVariable(CLANG)
    requireVariable(CLANGXX)
    setCompilerConfig(CMAKE_C_COMPILER ${CLANG})
    setCompilerConfig(CMAKE_CXX_COMPILER ${CLANGXX})
    noFortranSupport()
    noSharedObjectSupport()

    requireVariable(LLVM_LINK)
    # reset in order to create a log message
    setCompilerConfig(LLVM_LINK ${LLVM_LINK})
    # https://gitlab.kitware.com/cmake/community/-/wikis/doc/cmake/Build-Rules
    set(CMAKE_BC_LINK_EXECUTABLE "${LLVM_LINK} <OBJECTS> -o <TARGET>")
endmacro()

macro(setupOptions)
    # this must be called after compiler checks

    if("C" IN_LIST languages)
        # check compiler flags before messing around with the compiler options
        set(CLANG_DISABLE_O0_OPTNONE "-Xclang -disable-O0-optnone")
        include(CheckCCompilerFlag)
        check_c_compiler_flag("${CLANG_DISABLE_O0_OPTNONE}" HAVE_CLANG_DISABLE_O0_OPTNONE)
    endif()

    set(OUTPUT "${SULONG_CURRENT_VARIANT}")
    set(SUFFIX ".bc")
    string(APPEND CMAKE_C_FLAGS " -emit-llvm")
    string(APPEND CMAKE_CXX_FLAGS " -emit-llvm")

    # set optimization levels
    set(OPT_LEVELS "O0;O1;O2;O3;Os")
    set(MISC_OPTS "-functionattrs;-instcombine;-always-inline;-jump-threading;-simplifycfg;-mem2reg")
    set(MEM2REG "-mem2reg")

    if(SULONG_CURRENT_OPT_LEVEL IN_LIST OPT_LEVELS)
        string(APPEND CMAKE_C_FLAGS " -${SULONG_CURRENT_OPT_LEVEL}")
        string(APPEND CMAKE_CXX_FLAGS " -${SULONG_CURRENT_OPT_LEVEL}")
        # disable O0 optnone
        if(${SULONG_CURRENT_OPT_LEVEL} STREQUAL "O0" AND HAVE_CLANG_DISABLE_O0_OPTNONE)
            string(APPEND CMAKE_C_FLAGS " ${CLANG_DISABLE_O0_OPTNONE}")
            string(APPEND CMAKE_CXX_FLAGS " ${CLANG_DISABLE_O0_OPTNONE}")
        endif()
    elseif(SULONG_CURRENT_OPT_LEVEL)
        # non-empty but not in the known list
        message(FATAL_ERROR "Unknown opt-level: ${SULONG_CURRENT_OPT_LEVEL}")
    endif()

    # set post-opt
    if(SULONG_CURRENT_POST_OPT)
        if(SULONG_CURRENT_POST_OPT STREQUAL "MISC_OPTS")
            set(TARGET_OPT_FLAGS ${MISC_OPTS})
        elseif(SULONG_CURRENT_POST_OPT STREQUAL "MEM2REG")
            set(TARGET_OPT_FLAGS ${MEM2REG})
        else()
            message(FATAL_ERROR "Unknown opt sub-variant ${SULONG_CURRENT_POST_OPT}")
        endif()

        requireVariable(LLVM_OPT)
        # reset in order to create a log message
        setCompilerConfig(LLVM_OPT ${LLVM_OPT})

        macro(targetPostOpt)
            # postprocess
            add_custom_command(
              TARGET ${TARGET} POST_BUILD
              COMMAND ${LLVM_OPT} "$<TARGET_FILE:${TARGET}>" ${TARGET_OPT_FLAGS} -o "$<TARGET_FILE:${TARGET}>"
              )
        endmacro()
    endif()
endmacro()

macro(targetPostProcess SOURCE TARGET OUTPUT_DIR OUTPUT)
    # link with BC
    set_target_properties(${TARGET} PROPERTIES LINKER_LANGUAGE BC)
    # potential post-opt
    if(COMMAND targetPostOpt)
        targetPostOpt()
    endif()
endmacro()
