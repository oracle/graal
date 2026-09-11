# ccache.cmake — transparently wrap the compiler in ccache when it is available.
#
# It works with the Sulong wrapper compilers (graalvm-clang / graalvm-clang++) exactly
# as with a plain clang: ccache sees the launcher and caches its output. Set
# CCACHE_COMPILERCHECK=content in the environment (run-tests.sh does) so ccache hashes
# the *real* clang behind the wrapper rather than the wrapper script's mtime.

find_program(CCACHE_EXECUTABLE NAMES ccache)

if(CCACHE_EXECUTABLE)
    message(STATUS "ccache found: ${CCACHE_EXECUTABLE} — enabling compiler launcher")
    set(CMAKE_C_COMPILER_LAUNCHER   "${CCACHE_EXECUTABLE}" CACHE STRING "" FORCE)
    set(CMAKE_CXX_COMPILER_LAUNCHER "${CCACHE_EXECUTABLE}" CACHE STRING "" FORCE)
else()
    message(STATUS "ccache not found — building without a compiler cache")
endif()
