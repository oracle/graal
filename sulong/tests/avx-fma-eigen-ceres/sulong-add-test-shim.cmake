# sulong-add-test-shim.cmake — force every unit test to run under the Sulong `lli`
# emulator, regardless of which add_test() signature the sub-project uses.
#
# Injected into each dependency's configure via CMAKE_PROJECT_TOP_LEVEL_INCLUDES (so it
# runs inside the first project() call, before any test is registered).
#
# Why this is needed: CMake prepends CMAKE_CROSSCOMPILING_EMULATOR to a test's command
# ONLY when that command is a *bare executable target name*. Neither suite we run hits
# that case:
#   * Eigen registers tests with the *legacy* `add_test(<name> <target>)` form
#     (EigenTesting.cmake:112), for which CMake resolves the target to its built path but
#     does NOT prepend the emulator.
#   * Ceres 2.2 registers tests with `add_test(NAME <n> COMMAND $<TARGET_FILE:<tgt>> …)`
#     — a generator-expression path, NOT a bare target name — which *also* suppresses the
#     auto-prepend. (This was originally assumed to already run under lli; the CI run
#     proved otherwise: all 162 Ceres tests ran as native ELF, 0% passed, each dying at
#     load with `libc++.so.1: cannot open shared object file`.)
# Run as native ELF, these binaries have no libc++ .so — Sulong's graalvm-clang++ links
# libc++ as bitcode, so it only exists inside lli. (Verified for Eigen: the generated
# CTestTestfile.cmake contained `add_test(basicstuff_1 "basicstuff_1")` with no emulator;
# the same binary run as `lli basicstuff_1` passes.)
#
# This shim overrides add_test (the built-in stays callable as _add_test) and prepends the
# emulator ourselves for BOTH signatures. Because our rewritten COMMAND leads with the
# emulator path — which is not a target — CMake never prepends a second time, so there is
# no risk of a doubled emulator even for a command the built-in would otherwise have
# handled.
if(CMAKE_CROSSCOMPILING AND CMAKE_CROSSCOMPILING_EMULATOR AND NOT DEFINED _SULONG_ADD_TEST_SHIM)
    set(_SULONG_ADD_TEST_SHIM 1)
    message(STATUS "sulong-add-test-shim: routing add_test() (both signatures) through ${CMAKE_CROSSCOMPILING_EMULATOR}")

    function(add_test)
        if("${ARGV0}" STREQUAL "NAME")
            # New signature:
            #   add_test(NAME <name> COMMAND <cmd> [args...]
            #            [CONFIGURATIONS ...] [WORKING_DIRECTORY dir] [COMMAND_EXPAND_LISTS])
            cmake_parse_arguments(_ST "COMMAND_EXPAND_LISTS"
                                  "NAME;WORKING_DIRECTORY" "CONFIGURATIONS;COMMAND" ${ARGN})
            set(_cmd ${_ST_COMMAND})
            list(GET _cmd 0 _exe)           # command executable (bare target, path, or genex)
            list(REMOVE_AT _cmd 0)          # _cmd now holds only the trailing arguments
            if(TARGET "${_exe}")
                set(_exe "$<TARGET_FILE:${_exe}>")
            endif()
            set(_opt "")
            if(DEFINED _ST_WORKING_DIRECTORY)
                list(APPEND _opt WORKING_DIRECTORY "${_ST_WORKING_DIRECTORY}")
            endif()
            if(DEFINED _ST_CONFIGURATIONS)
                list(APPEND _opt CONFIGURATIONS ${_ST_CONFIGURATIONS})
            endif()
            if(_ST_COMMAND_EXPAND_LISTS)
                list(APPEND _opt COMMAND_EXPAND_LISTS)
            endif()
            _add_test(NAME "${_ST_NAME}"
                      COMMAND ${CMAKE_CROSSCOMPILING_EMULATOR} "${_exe}" ${_cmd}
                      ${_opt})
        else()
            # Legacy signature: add_test(<name> <command> [args...]).
            set(_name "${ARGV0}")
            set(_cmd  "${ARGV1}")
            set(_rest ${ARGN})
            list(LENGTH _rest _n)
            if(_n GREATER 2)
                list(REMOVE_AT _rest 0 1)   # keep only the trailing command arguments
            else()
                set(_rest "")
            endif()
            if(TARGET "${_cmd}")
                _add_test(NAME "${_name}"
                          COMMAND ${CMAKE_CROSSCOMPILING_EMULATOR} "$<TARGET_FILE:${_cmd}>" ${_rest})
            else()
                _add_test(NAME "${_name}"
                          COMMAND ${CMAKE_CROSSCOMPILING_EMULATOR} "${_cmd}" ${_rest})
            endif()
        endif()
    endfunction()
endif()
