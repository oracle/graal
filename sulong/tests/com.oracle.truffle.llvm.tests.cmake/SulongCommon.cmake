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

# fail if the variables is not defined
function(requireVariable varname)
    if(NOT DEFINED ${varname})
        message(FATAL_ERROR "variable ${varname} is required")
    endif()
endfunction()

# set variable from environement variable if the latter exists
function(setFromEnv varname envname)
    if(DEFINED ENV{${envname}})
        set(${varname} $ENV{${envname}} PARENT_SCOPE)
    endif()
endfunction()

# set compiler from environment variable if the latter exists
# If the environment variable consists of the command + flags,
# the command will be sotred in ${compiler_var} and the rest in
# ${flag_var}.
function(setCompilerFromEnv compiler_var flag_var envname)
    if(DEFINED ENV{${envname}})
        separate_arguments(_env_flags UNIX_COMMAND $ENV{${envname}})
        list(POP_FRONT _env_flags _compiler)
        set(${compiler_var} ${_compiler} PARENT_SCOPE)
        string(APPEND ${flag_var} " ${_env_flags}")
        set(${flag_var} "${${flag_var}}" PARENT_SCOPE)
    endif()
endfunction()

# set a variable to a default value if it is not defined
function(setDefault varname value)
    if(NOT DEFINED ${varname})
        set(${varname} ${value} PARENT_SCOPE)
    endif()
endfunction()

# set a variable and produce a log message (even in non-verbose mode)
function(setCompilerConfig varname value)
   message(NOTICE "  ${varname}: ${value}")
   set(${varname} ${value} PARENT_SCOPE)
endfunction()

if(CMAKE_HOST_SYSTEM_NAME STREQUAL "Darwin")
    # NOTE: the darwin linker refuses bundle bitcode if any of the dependencies do not have a bundle section.
    #   However, it does include the bundle if linked with -flto, although the warning still says otherwise.
    set(EMBED_BC "-flto -Wl,-bitcode_bundle -Wno-everything")
else()
    set(EMBED_BC "-fembed-bitcode")
endif()

# helper for asserting that this variant does not support Fortran
function(noFortranSupport)
    if("Fortran" IN_LIST SULONG_ENABLED_LANGUAGES)
        message(FATAL_ERROR "Fortran is not supported by compile mode ${SULONG_CURRENT_COMPILE_MODE}")
    endif()
endfunction()

# helper for asserting that this variant does not support building shared objects
function(noSharedObjectSupport)
    if(SULONG_BUILD_SHARED_OBJECT)
        message(FATAL_ERROR "Building shared objects is not supported by compile mode ${SULONG_CURRENT_COMPILE_MODE}")
    endif()
endfunction()

# capitalize a string
function(capitalize string output_variable)
    string(SUBSTRING ${string} 0 1 _start)
    string(TOUPPER ${_start} _start_upper)
    string(SUBSTRING ${string} 1 -1 _end)
    set(${output_variable} "${_start_upper}${_end}" PARENT_SCOPE)
endfunction()

function(get_target VARNAME TEST)
    if(NOT DEFINED OUTPUT)
        message(FATAL_ERROR "Variable OUTPUT must be defined before calling get_target() (usually OUTPUT is set via the variant configuration).")
    endif()
    set(OUTPUT_DIR "${TEST}.dir")
    string(REPLACE "/" "_" TARGET "${OUTPUT_DIR}/${OUTPUT}")
    set(${VARNAME} ${TARGET} PARENT_SCOPE)
endfunction()
