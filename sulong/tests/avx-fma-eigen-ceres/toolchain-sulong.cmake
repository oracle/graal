# toolchain-sulong.cmake — compile C/C++ with the Sulong LLVM toolchain and run the
# resulting programs (incl. the ctest suites) under Sulong's `lli` interpreter.
#
# Sulong's toolchain wrappers (graalvm-clang / graalvm-clang++) emit native ELF objects
# carrying embedded LLVM bitcode; `lli` executes that bitcode on the GraalVM LLVM
# runtime. We build normally but *run* every test through lli by declaring a
# cross-compile whose "emulator" is lli: CMake prepends CMAKE_CROSSCOMPILING_EMULATOR
# to every add_test(NAME ... COMMAND tgt) invocation, which is how Eigen (ei_add_test)
# and Ceres (CERES_TEST) register their unit tests.
#
# The compilers and lli come from the built graal tree (SULONG_BOOTSTRAP_TOOLCHAIN's
# JVM-based wrappers and <graalvm-home>/bin/lli) — the same tools the Sulong gate uses
# to build and run its C/C++ tests. No SULONG_JVM_STANDALONE is required (building one
# needs native-image, which the CI JDK does not have). run-tests.sh resolves the paths
# via mx and passes them in the environment:
#   SULONG_CC / SULONG_CXX  the graalvm-clang / graalvm-clang++ wrappers
#   SULONG_LLI              an lli launcher (script) that runs a bitcode executable
#   SULONG_LLI_EXTRA        extra, space-separated lli args (the MKL-backed variant
#                           passes --llvm.libraryPath=<dir> --llvm.libraries=libmkl_rt.so)

foreach(_v SULONG_CC SULONG_CXX SULONG_LLI)
    if(NOT DEFINED ENV{${_v}})
        message(FATAL_ERROR "toolchain-sulong.cmake: ${_v} is not set in the environment")
    endif()
endforeach()

# Cross-compiling so CMake never tries to *execute* a freshly built binary directly
# (it is run through lli instead); this also switches on the EMULATOR hook.
#
# CMAKE_CROSSCOMPILING must be forced TRUE: on a Linux host, setting CMAKE_SYSTEM_NAME to
# "Linux" does NOT make CMake consider this a cross-build — it leaves CROSSCOMPILING FALSE
# and, critically, *clears* CMAKE_CROSSCOMPILING_EMULATOR. ctest would then run the test
# binaries as native ELF (which fail with `libc++.so.1: cannot open shared object file`,
# since the graalvm-clang++ wrappers link libc++ as bitcode, not a native .so). Forcing it
# TRUE preserves the emulator so every add_test runs `lli <binary>` and executes the
# embedded bitcode on Sulong (which provides libc++ itself).
set(CMAKE_SYSTEM_NAME Linux)
set(CMAKE_SYSTEM_PROCESSOR x86_64)
set(CMAKE_CROSSCOMPILING TRUE)

set(CMAKE_C_COMPILER   "$ENV{SULONG_CC}")
set(CMAKE_CXX_COMPILER "$ENV{SULONG_CXX}")

# Target AVX2 + FMA. This is the whole point of the exercise: the fork adds the 256-bit
# AVX2/FMA packet kernels and the matching inline-asm mnemonics (vminps/vmaxps/vfmadd231ps
# …) to Sulong's amd64 asm parser, so the tests must be built for that ISA to exercise
# them. Without it, graalvm-clang defaults to SSE2 and Eigen routes pmin/pmax through the
# legacy `minps`/`maxps` asm forms (Eigen's GCC<6.3 workaround, tripped because the wrapper
# reports __GNUC__=4.2) — which the fork's parser does not implement, so packetmath aborts
# with `ASM error in "minps …"`. Building with -mavx2 -mfma selects Eigen's `vminps`/`vmaxps`
# asm path instead, exactly the mnemonics the fork supports. Set via *_INIT so these seed
# the flags without clobbering per-target additions.
set(CMAKE_C_FLAGS_INIT   "-mavx2 -mfma")
set(CMAKE_CXX_FLAGS_INIT "-mavx2 -mfma")

# The wrappers produce a normal executable target; don't let CMake's compiler probe
# try to run it. A static-library probe is enough to validate the toolchain.
set(CMAKE_TRY_COMPILE_TARGET_TYPE STATIC_LIBRARY)

# Search roots: prefer the toolchain's own sysroot, but still see host headers/libs
# (e.g. MKL under /opt) for the MKL-backed variant.
set(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM BOTH)
set(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY BOTH)
set(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE BOTH)

# --- the emulator: lli [+ variant-specific args] ---------------------------
set(CMAKE_CROSSCOMPILING_EMULATOR "$ENV{SULONG_LLI}")
if(DEFINED ENV{SULONG_LLI_EXTRA} AND NOT "$ENV{SULONG_LLI_EXTRA}" STREQUAL "")
    # Turn the space-separated arg string into a CMake list so each token becomes a
    # separate argv entry appended to the emulator command (before the test binary).
    separate_arguments(_lli_extra NATIVE_COMMAND "$ENV{SULONG_LLI_EXTRA}")
    list(APPEND CMAKE_CROSSCOMPILING_EMULATOR ${_lli_extra})
endif()
