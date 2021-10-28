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
    setCompilerConfig(CMAKE_C_COMPILER ${CLANG})
    requireVariable(CLANGXX)
    setCompilerConfig(CMAKE_CXX_COMPILER ${CLANGXX})
    # reset in order to create a log message
    if("Fortran" IN_LIST SULONG_ENABLED_LANGUAGES)
        requireVariable(FC)
        setCompilerConfig(FC ${FC})
        setCompilerConfig(CMAKE_Fortran_COMPILER ${FC})
    endif()
    noSharedObjectSupport()
endmacro()

macro(targetPostProcess SOURCE TARGET OUTPUT_DIR OUTPUT)
    get_source_file_property(TARGET_LANG ${SOURCE} LANGUAGE)
    if(${TARGET_LANG} STREQUAL "LL")
        # support for adding dependencies
        target_compile_options(${TARGET} PRIVATE "$<$<COMPILE_LANGUAGE:C>:-emit-llvm>")
        set_target_properties(${TARGET} PROPERTIES LINKER_LANGUAGE LL)
        # llvm-link should produce ll files
        target_link_options(${TARGET} PRIVATE "-S")
        separate_arguments(LL_LINKER_OPTIONS NATIVE_COMMAND "${CMAKE_C_LINK_FLAGS} ${CMAKE_EXE_LINKER_FLAGS}")
        add_custom_command(
          TARGET ${TARGET} POST_BUILD
          # <TARGET> is actually an ll file - create a copy with the .ll extension so that clang will switch into the right mode
          COMMAND cp "$<TARGET_FILE:${TARGET}>" "$<TARGET_FILE:${TARGET}>.ll"
          COMMAND ${CMAKE_C_COMPILER} ${LL_LINKER_OPTIONS} "$<TARGET_FILE:${TARGET}>.ll" "-o" "$<TARGET_FILE:${TARGET}>"
          VERBATIM
        )
    endif()
endmacro()

macro(setupOptions)
    set(OUTPUT "ref.out")
    string(APPEND CMAKE_C_FLAGS " ${EMBED_BC}")
    string(APPEND CMAKE_CXX_FLAGS " ${EMBED_BC}")
    # reset in order to create a log message
    setCompilerConfig(LLVM_CONFIG ${LLVM_CONFIG})
    execute_process(COMMAND ${LLVM_CONFIG} --libdir OUTPUT_VARIABLE _llvm_config_libdir OUTPUT_STRIP_TRAILING_WHITESPACE)
    setCompilerConfig(CMAKE_INSTALL_RPATH "${_llvm_config_libdir}")
endmacro()
