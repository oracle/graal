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
    requireVariable(CLANG)
    requireVariable(CLANGXX)
    setCompilerConfig(CMAKE_C_COMPILER ${CLANG})
    setCompilerConfig(CMAKE_CXX_COMPILER ${CLANGXX})
    # reset in order to create a log message
    setCompilerConfig(LLVM_OBJCOPY ${LLVM_OBJCOPY})
    if("Fortran" IN_LIST SULONG_ENABLED_LANGUAGES)
        requireVariable(DRAGONEGG)
        requireVariable(DRAGONEGG_FC)
        setCompilerConfig(DRAGONEGG_FC ${DRAGONEGG_FC})
        setCompilerConfig(DRAGONEGG ${DRAGONEGG})
        setCompilerConfig(CMAKE_Fortran_COMPILER ${DRAGONEGG_FC})
    endif()
endmacro()

macro(targetPostProcess SOURCE TARGET OUTPUT_DIR OUTPUT)
    get_source_file_property(TARGET_LANG ${SOURCE} LANGUAGE)
    if(${TARGET_LANG} STREQUAL "Fortran")
        set(TARGET_LL "${TARGET}.dragonegg.ll")
        # create a bitcode target
        add_library(${TARGET_LL} STATIC ${SOURCE})
        target_compile_options(${TARGET_LL} PRIVATE "-S" "-fplugin=${DRAGONEGG}" "-fplugin-arg-dragonegg-emit-ir")
        set_target_properties(${TARGET_LL} PROPERTIES Fortran_MODULE_DIRECTORY ${OUTPUT_DIR}/${OUTPUT}.dragonegg.ll.mod)
        # postprocess to add the bitcode to the target file via objcopy
        add_custom_command(
          TARGET ${TARGET} POST_BUILD
          COMMAND ${LLVM_OBJCOPY} "--add-section" ".llvmbc=$<TARGET_FILE:${TARGET_LL}>" "$<TARGET_FILE:${TARGET}>"
        )
    endif()
endmacro()

macro(setupOptions)
    # this must be called after compiler checks

    if(SULONG_BUILD_SHARED_OBJECT)
        set(OUTPUT "${SULONG_CURRENT_VARIANT}")
    else()
        set(OUTPUT "${SULONG_CURRENT_VARIANT}.bc")
    endif()

    string(APPEND CMAKE_C_FLAGS " ${EMBED_BC}")
    string(APPEND CMAKE_CXX_FLAGS " ${EMBED_BC}")

    # set optimization levels
    set(OPT_LEVELS "O0;O1;O2;O3")

    if(SULONG_CURRENT_OPT_LEVEL IN_LIST OPT_LEVELS)
        string(APPEND CMAKE_C_FLAGS " -${SULONG_CURRENT_OPT_LEVEL}")
        string(APPEND CMAKE_CXX_FLAGS " -${SULONG_CURRENT_OPT_LEVEL}")
    elseif(SULONG_CURRENT_OPT_LEVEL)
        # non-empty but not in the known list
        message(FATAL_ERROR "Unknonw opt-level: ${SULONG_CURRENT_OPT_LEVEL}")
    endif()

    if(SULONG_CURRENT_POST_OPT)
        message(WARNING "post-opt is only meaningful for compile-mode 'bitcode', current mode: ${SULONG_CURRENT_COMPILE_MODE}")
    endif()
endmacro()
