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

cmake_minimum_required(VERSION 3.15)
project(toolchain-launchers-test)

set(QUIETLY_REDIR)
set(CHECK_WRAPPER_SCRIPT | grep.exe "GraalVM wrapper script" ${QUIETLY_REDIR})

if (APPLE AND DEFINED SULONG_NATIVE_BUILD)
  set(BC_MAGIC 0b17c0de)
  set(BC_SECTION __bitcode\|__bundle)
  set(SHARED_FLAGS -dynamic -dylib -arch x86_64 -macosx_version_min 10.14.0 -lSystem)
else()
  set(BC_MAGIC dec04342)
  set(BC_SECTION \.llvmbc)
  set(SHARED_FLAGS --shared)
endif()

if (WIN32)
  # This is a hack on Windows to support the execution of absolute path
  # executables in ctest
  add_executable(runner runner.cpp)
  add_custom_command(TARGET runner POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E copy "${SULONG_LIB}/c++.dll" $<TARGET_FILE_DIR:runner>
    COMMAND_EXPAND_LISTS
  )
  set_property(TARGET runner PROPERTY MSVC_RUNTIME_LIBRARY "MultiThreaded")
  set(TEST_RUNNER runner)
else()
  set(TEST_RUNNER)
endif()

separate_arguments(SULONG_EXE)

enable_testing()

# Define toolchains

set(TC_BITCODE_EXE ${SULONG_C_COMPILER})
set(TC_BITCODE_FLAGS -c)

set(TCXX_BITCODE_EXE ${SULONG_CXX_COMPILER})
set(TCXX_BITCODE_FLAGS -c)

set(TC_CLINK_EXE ${SULONG_C_COMPILER})
set(TC_CXXLINK_EXE ${SULONG_CXX_COMPILER})

set(TC_LD_EXE ${SULONG_LINKER})
set(TC_LD_FLAGS ${SHARED_FLAGS})

set(TC_LDXX_EXE ${SULONG_LINKER})
set(TC_LDXX_FLAGS ${SHARED_FLAGS} -lc++)

if(WIN32)
  set(TC_LD_OUT_PREFIX /out:)
  set(TC_LD_OUT_FLAG)
  list(APPEND TC_LD_FLAGS -defaultlib:libcmt)

  set(TC_LDXX_OUT_PREFIX /out:)
  set(TC_LDXX_OUT_FLAG)
  list(APPEND TC_LDXX_FLAGS -defaultlib:libcmt)
endif()

set(TC_COMPLINK_EXE ${SULONG_C_COMPILER})

set(TC_COMPLINKXX_EXE ${SULONG_CXX_COMPILER})

# Helper functions

define_property(TARGET PROPERTY FLAGS BRIEF_DOCS "Compile flags" FULL_DOCS "Compile flags")

function(add_compile_target TARGET TC FLAGS INPUT OUTPUT)
  set(OUT_FLAG -o)
  if(DEFINED ${${TC}_OUT_FLAG})
    set(OUT_FLAG ${${TC}_OUT_FLAG})
  endif()
  add_custom_command(OUTPUT ${OUTPUT}
    COMMAND ${${TC}_EXE} $<TARGET_PROPERTY:${TARGET},FLAGS> ${${TC}_FLAGS} ${${FLAGS}} ${INPUT} ${OUT_FLAG} ${${TC}_OUT_PREFIX}${OUTPUT}
    DEPENDS ${INPUT})
  add_custom_target(${TARGET} ALL DEPENDS ${OUTPUT})
endfunction()

function(add_bc_magic_test PREFIX TEST DEST_EXT)
  add_test(NAME ${PREFIX}${TEST} COMMAND od -t x4 -N 4 obj/${TEST}${DEST_EXT})
  set_property(TEST ${PREFIX}${TEST} PROPERTY PASS_REGULAR_EXPRESSION ${BC_MAGIC})
endfunction()

function(add_bc_section_test PREFIX TEST DEST_EXT)
  add_test(NAME ${PREFIX}${TEST} COMMAND ${SULONG_OBJDUMP} -h ${TEST}${DEST_EXT})
  set_property(TEST ${PREFIX}${TEST} PROPERTY PASS_REGULAR_EXPRESSION ${BC_SECTION})
endfunction()

function(add_run_test PREFIX TEST DEST_EXT)
  add_test(NAME run-${PREFIX}${TEST} COMMAND ${SULONG_EXE} ${TEST}${DEST_EXT})
endfunction()

function(add_bc_magic_tests PREFIX TESTS TC FLAGS SRC_EXT DEST_EXT)
  foreach(TEST IN LISTS ${TESTS})
    add_compile_target(${PREFIX}${TEST} ${TC} ${FLAGS}
      ${CMAKE_CURRENT_SOURCE_DIR}/src/${TEST}${SRC_EXT} obj/${TEST}${DEST_EXT})
    add_bc_magic_test(${PREFIX} ${TEST} ${DEST_EXT})
  endforeach()
endfunction()

function(add_bc_section_tests PREFIX TESTS TC FLAGS SRC_PREFIX SRC_EXT DEST_EXT)
  foreach(TEST IN LISTS ${TESTS})
    add_compile_target(${PREFIX}${TEST} ${TC} ${FLAGS}
      ${SRC_PREFIX}${TEST}${SRC_EXT} ${TEST}${DEST_EXT})
    add_bc_section_test(${PREFIX} ${TEST} ${DEST_EXT})
    add_run_test(${PREFIX} ${TEST} ${DEST_EXT})
  endforeach()
endfunction()

# Magic tests

set(C_FILES "main" "polyglot_biginteger")
set(CXX_FILES "polyglot_biginteger_cxx")

set(BITCODE_FLAGS -c)
add_bc_magic_tests("magic-o-" C_FILES TC_BITCODE BITCODE_FLAGS ".c" ".o")
add_bc_magic_tests("magic-opp-" CXX_FILES TCXX_BITCODE BITCODE_FLAGS ".cpp" ".opp")
set_property(TARGET "magic-o-main" PROPERTY FLAGS -v)

# compilation with PIC (with -fLTO, i.e., result is bitcode file)
set(LO_FLAGS -fPIC)
if(WIN32)
  set(LO_FLAGS -flto)
endif()
add_bc_magic_tests("magic-lo-" C_FILES TC_BITCODE LO_FLAGS ".c" ".lo")
add_bc_magic_tests("magic-lo-" CXX_FILES TCXX_BITCODE LO_FLAGS ".cpp" ".lopp")


set(CLINK_FLAGS "-lgraalvm-llvm")
add_bc_section_tests("section-o-" C_FILES TC_CLINK CLINK_FLAGS obj/ ".o" ".out")
add_bc_section_tests("section-opp-" CXX_FILES TC_CXXLINK CLINK_FLAGS obj/ ".opp" ".out")
set(LD_FLAGS "-lgraalvm-llvm")
if (WIN32)
  set(LD_FLAGS "graalvm-llvm.lib")
endif()
set(LDXX_FLAGS ${LD_FLAGS} -lc++)
add_bc_section_tests("section-lo-" C_FILES TC_LD LD_FLAGS obj/ ".lo" ".ld-linked.out")
add_bc_section_tests("section-lopp-" CXX_FILES TC_LDXX LDXX_FLAGS obj/ ".lopp" ".ld-linked.out" )

set(COMPLINK_FLAGS -lgraalvm-llvm)
add_bc_section_tests("section-comp-and-link-" C_FILES TC_COMPLINK COMPLINK_FLAGS
  ${CMAKE_CURRENT_SOURCE_DIR}/src/ ".c" ".comp-and-link.out")
add_bc_section_tests("section-comp-and-link-pp-" CXX_FILES TC_COMPLINKXX COMPLINK_FLAGS
  ${CMAKE_CURRENT_SOURCE_DIR}/src/ ".cpp" ".comp-and-link.out")

# Command line output tests

set(GRAALVM_WRAPPER_SCRIPT "GraalVM wrapper script")

add_test(NAME print-help COMMAND ${TEST_RUNNER} ${SULONG_C_COMPILER} --help)
set_property(TEST print-help PROPERTY PASS_REGULAR_EXPRESSION ${GRAALVM_WRAPPER_SCRIPT})

add_test(NAME print-graalvm-help COMMAND ${TEST_RUNNER} ${SULONG_C_COMPILER} --graalvm-help)
set_property(TEST print-graalvm-help PROPERTY PASS_REGULAR_EXPRESSION ${GRAALVM_WRAPPER_SCRIPT})

add_test(NAME print-graalvm-cmd COMMAND ${TEST_RUNNER} ${SULONG_C_COMPILER} --graalvm-print-cmd bla.c)
set_property(TEST print-graalvm-cmd PROPERTY PASS_REGULAR_EXPRESSION "running:.*clang.*bla.c")

add_test(NAME fembed-bitcode COMMAND ${TEST_RUNNER} ${SULONG_C_COMPILER} -fembed-bitcode bla.c WILL_FAIL)
set_property(TEST fembed-bitcode PROPERTY PASS_REGULAR_EXPRESSION "Using \`-fembed-bitcode\` is not supported.")

add_test(NAME fuse-ld COMMAND ${TEST_RUNNER} ${SULONG_C_COMPILER} -fuse-ld=foo bla.c WILL_FAIL)
set_property(TEST fuse-ld PROPERTY PASS_REGULAR_EXPRESSION "Using \`-fuse-ld\` is not supported.")
