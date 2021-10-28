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

# Variants
##########
#
# Each variant is defined in its own file configuration file ("SulongTestSuiteVariant<VARIANT>.cmake")
# The file must define the following macros:
# - `setupCompiler()`: This is called before `enableLanguage`. It should be used perform compiler setup like
#   setting `CMAKE_C_COMPILER` or check whether required tools are available.
# - `setupOptions()`: This is called after `enableLanguage`. It is supposed to set flags that might conflict with
#   `enableLanguage` (e.g. because the linked executable is not an exectable but a bitcode file).
#
# In addition, a variant configuration might define the following:
# - `targetPostProcess(SOURCE TARGET OUTPUT_DIR OUTPUT)`: this can be used to post-process the build or to set source
#   or target properties which cannot be set globally.
#
# LL Language support
#####################
#
# Support for compiling .ll files is implemented in the `CMake*LL*.cmake` files. The main implementation lives in
# `CMakeLLInformation.cmake`.


include_guard(GLOBAL)
include(SulongCommon)

set(SULONG_MODULE_PATH "" CACHE STRING "Path for looking up cmake modules")

# handle default C/CXX flags
set(CMAKE_C_FLAGS "${SULONG_C_FLAGS} ${CMAKE_C_FLAGS}")
set(CMAKE_CXX_FLAGS "${SULONG_CXX_FLAGS} ${CMAKE_CXX_FLAGS}")

# ensure that LDFLAGS environement variable is respected
if(DEFINED CMAKE_EXE_LINKER_FLAGS AND DEFINED ENV{LDFLAGS})
    # If CMAKE_EXE_LINKER_FLAGS are set in the suite.py, the LDFLAGS environment variable is no longer used automatically.
    # Thus, we add it manually.
    string(APPEND CMAKE_EXE_LINKER_FLAGS " $ENV{LDFLAGS}")
endif()

# ensure that CPPFLAGS environement variable is respected
if(DEFINED ENV{CPPFLAGS})
    string(APPEND CMAKE_C_FLAGS " $ENV{CPPFLAGS}")
    string(APPEND CMAKE_CXX_FLAGS " $ENV{CPPFLAGS}")
endif()

# variables required to be set via SulongCMakeTestSuite
requireVariable(SULONG_CURRENT_VARIANT)
requireVariable(SULONG_TESTS)
requireVariable(SULONG_BUILD_SHARED_OBJECT)
requireVariable(SULONG_ENABLED_LANGUAGES)

# parse the current variant
macro(parseVariant)
    # format: "<compile-mode>-<opt-level>-<post-opt>
    string(REPLACE "-" ";" SULONG_CURRENT_VARIANT_LIST "${SULONG_CURRENT_VARIANT}")
    # compile mode
    list(POP_FRONT SULONG_CURRENT_VARIANT_LIST SULONG_CURRENT_COMPILE_MODE)
    # clang opt-level
    list(POP_FRONT SULONG_CURRENT_VARIANT_LIST SULONG_CURRENT_OPT_LEVEL)
    # clang opt-level
    list(POP_FRONT SULONG_CURRENT_VARIANT_LIST SULONG_CURRENT_POST_OPT)
endmacro()

parseVariant()

################################
# Load the variant configuration
################################
# configuration file "SulongTestSuiteVariant<VARIANT>.cmake"
if(SULONG_CURRENT_COMPILE_MODE STREQUAL "ref.out")
    set(VARIANT_CONFIG_FILE SulongTestSuiteVariantRefOut)
    include(${VARIANT_CONFIG_FILE})
else()
    capitalize(${SULONG_CURRENT_COMPILE_MODE} SULONG_CURRENT_COMPILE_MODE_FILE)
    set(VARIANT_CONFIG_FILE "SulongTestSuiteVariant${SULONG_CURRENT_COMPILE_MODE_FILE}")
    include(${VARIANT_CONFIG_FILE})
endif()

if(NOT COMMAND setupCompiler)
    message(FATAL_ERROR "${VARIANT_CONFIG_FILE} does not define setupCompiler()")
endif()

if(NOT COMMAND setupOptions)
    message(FATAL_ERROR "${VARIANT_CONFIG_FILE} does not define setupOptions()")
endif()

################################################
# call setup from the variant configuration file
################################################
setupCompiler()

# enable languages (after setup!)
foreach(lang ${SULONG_ENABLED_LANGUAGES})
    enable_language(${lang})
endforeach()

###########################################
# further setup after languages are enabled
###########################################
setupOptions()

if(NOT OUTPUT)
    message(FATAL_ERROR "${VARIANT_CONFIG_FILE} does not define the OUTPUT variable")
endif()

# deal with non-standard file extensions
set(EXTRA_C_SOURCE_FILE_EXTENSIONS cint gcc)
# ensure the right compiler mode is selected
# see https://cmake.org/cmake/help/latest/policy/CMP0119.html#policy:CMP0119
set(EXTRA_C_COMPILE_OPTIONS -xc)

set(EXTRA_Fortran_SOURCE_FILE_EXTENSIONS f03)

# set language for non-default file extensions
function(setSourceLanguage lang source target)
    if(EXTRA_${lang}_SOURCE_FILE_EXTENSIONS)
        string(JOIN "|." ext_re ${EXTRA_${lang}_SOURCE_FILE_EXTENSIONS})
        string(REGEX MATCH "(${ext_re})$" match ${source})
        if (match)
            set_source_files_properties(${source} PROPERTIES LANGUAGE ${lang})
            # we might need extra compile options because the compiler might not be able
            # to detect the language from the non-default file extension
            if(EXTRA_${lang}_COMPILE_OPTIONS)
                target_compile_options(${target} PRIVATE ${EXTRA_${lang}_COMPILE_OPTIONS})
            endif()
        endif()
    endif()
endfunction()

# deal with external test sources
if(NOT DEFINED SULONG_TEST_SOURCE_DIR)
    set(SULONG_TEST_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}")
endif()

################################################
# This creates the target for every test source.
################################################
foreach(TEST ${SULONG_TESTS})
    # create unique target name
    get_target(TARGET ${TEST})
    # set the source variable
    set(SOURCE "${SULONG_TEST_SOURCE_DIR}/${TEST}")

    # add executable/library
    if(SULONG_BUILD_SHARED_OBJECT)
        add_library(${TARGET} SHARED ${SOURCE})
        # do not use "lib" prefix and fix .so suffix
        set_target_properties(${TARGET} PROPERTIES PREFIX "")
        set_target_properties(${TARGET} PROPERTIES SUFFIX ".so")
    else()
        add_executable(${TARGET} ${SOURCE})
    endif()

    # set source properties for non-default file extensions
    foreach(lang ${SULONG_ENABLED_LANGUAGES})
        setSourceLanguage(${lang} ${SOURCE} ${TARGET})
    endforeach()

    set(OUTPUT_DIR "${TEST}.dir")

    # post-process
    if(COMMAND targetPostProcess)
        targetPostProcess(${SOURCE} ${TARGET} ${OUTPUT_DIR} ${OUTPUT})
    endif()

    # set the output name and directories
    set_target_properties(${TARGET} PROPERTIES OUTPUT_NAME ${OUTPUT})
    set_target_properties(${TARGET} PROPERTIES Fortran_MODULE_DIRECTORY ${OUTPUT_DIR}/${OUTPUT}.mod)
    set_target_properties(${TARGET} PROPERTIES RUNTIME_OUTPUT_DIRECTORY ${OUTPUT_DIR})
    set_target_properties(${TARGET} PROPERTIES LIBRARY_OUTPUT_DIRECTORY ${OUTPUT_DIR})
    # set the install destination
    # (Note: OUTPUT_DIR is just a subfolder. The directories set above and the install directory set below do not collide.)
    install(TARGETS ${TARGET} DESTINATION ${OUTPUT_DIR})
endforeach()
