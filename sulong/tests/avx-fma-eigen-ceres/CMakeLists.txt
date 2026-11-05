# Eigen + Ceres 2.2 unit tests under Sulong — superbuild.
#
# A thin ExternalProject superbuild (project LANGUAGES NONE — nothing is compiled at
# *this* level; each dependency is configured with the Sulong toolchain file and
# built/tested in its own tree). Sources are git-cloned from their public upstream
# hosts: Eigen 3.4.0 from gitlab.com/libeigen/eigen (Eigen's canonical home), and
# ceres-solver 2.2.0 + gflags v2.2.2 from GitHub. gflags is built from source too
# (not apt) so it compiles to bitcode and its symbols run under lli alongside the
# Ceres test binaries.
#
# Each dependency registers its tests with add_test(NAME ... COMMAND <target>), so the
# toolchain's CMAKE_CROSSCOMPILING_EMULATOR (= lli [+ MKL args]) is prepended and every
# test executes on the Sulong LLVM runtime. Tests run as an ExternalProject test step
# that is part of the main build, so a nonzero ctest exit fails the build — which is
# what makes the CI job blocking.
#
# Two linear-algebra backends, selected by VARIANT:
#   bitcode  Ceres dense LA via Eigen (LAPACK=OFF) — pure bitcode, no native BLAS.
#   mkl      Ceres LAPACK=ON with BLAS/LAPACK resolved to Intel MKL's libmkl_rt.so,
#            which lli binds from bitcode via Sulong's native interface at run time.
# Eigen's own tests (incl. packetmath / vectorization_logic — the direct AVX2/FMA SIMD
# coverage) always run as pure bitcode and are independent of VARIANT.

cmake_minimum_required(VERSION 3.20)
project(eigen_ceres_sulong LANGUAGES NONE)

include(ExternalProject)
include(${CMAKE_CURRENT_LIST_DIR}/cmake/ccache.cmake)

# --- knobs ------------------------------------------------------------------
# Direct upstream URLs (no proxy). Override e.g. -DEIGEN_REPO_URL=... to point at
# an internal mirror.
set(EIGEN_REPO_URL  "https://gitlab.com/libeigen/eigen.git"
    CACHE STRING "Eigen git repo URL")
set(CERES_REPO_URL  "https://github.com/ceres-solver/ceres-solver.git"
    CACHE STRING "Ceres git repo URL")
set(GFLAGS_REPO_URL "https://github.com/gflags/gflags.git"
    CACHE STRING "gflags git repo URL")

set(VARIANT "bitcode" CACHE STRING "Linear-algebra backend: bitcode | mkl")
set(MKL_LIB_DIR "" CACHE STRING "Directory containing libmkl_rt.so (VARIANT=mkl)")
option(ENABLE_EIGEN "Build & run the Eigen test subset" ON)
option(ENABLE_CERES "Build & run the Ceres test suite" ON)

set(EIGEN_TAG  "3.4.0")
set(CERES_TAG  "2.2.0")
set(GFLAGS_TAG "v2.2.2")

# Curated Eigen test subset: SIMD-heavy kernels (packetmath / vectorization_logic
# directly exercise the AVX2/FMA packet paths this fork adds) plus the core dense
# decompositions. Kept small with EIGEN_TEST_MAX_SIZE to bound interpreter runtime.
set(EIGEN_TESTS packetmath vectorization_logic basicstuff redux
                cholesky product_small geo_quaternion jacobisvd)
set(EIGEN_TEST_MAX_SIZE 16 CACHE STRING "Cap Eigen test matrix sizes")

if(NOT DEFINED CMAKE_TOOLCHAIN_FILE OR "${CMAKE_TOOLCHAIN_FILE}" STREQUAL "")
    message(FATAL_ERROR "Configure with -DCMAKE_TOOLCHAIN_FILE=<abs path>/toolchain-sulong.cmake")
endif()
get_filename_component(_toolchain "${CMAKE_TOOLCHAIN_FILE}" ABSOLUTE)

# add_test shim, injected into every sub-project's first project() call. It forces the
# legacy add_test(<name> <target>) form (used by Eigen) to run under the lli emulator,
# which CMake otherwise only does for the NAME/COMMAND form (used by Ceres). See
# sulong-add-test-shim.cmake for the full rationale.
get_filename_component(_add_test_shim "${CMAKE_CURRENT_LIST_DIR}/sulong-add-test-shim.cmake" ABSOLUTE)

# Arguments forwarded to every sub-project: the Sulong toolchain, Release, static
# libs, the add_test shim, and (when present) the ccache launcher so the two variant
# passes share a cache.
set(FWD_ARGS
    -DCMAKE_TOOLCHAIN_FILE=${_toolchain}
    -DCMAKE_PROJECT_TOP_LEVEL_INCLUDES=${_add_test_shim}
    -DCMAKE_BUILD_TYPE=Release
    -DBUILD_SHARED_LIBS=OFF)
if(CCACHE_EXECUTABLE)
    list(APPEND FWD_ARGS
        -DCMAKE_C_COMPILER_LAUNCHER=${CCACHE_EXECUTABLE}
        -DCMAKE_CXX_COMPILER_LAUNCHER=${CCACHE_EXECUTABLE})
endif()

set(EIGEN_SRC     "${CMAKE_BINARY_DIR}/eigen-src")
set(EIGEN_PREFIX  "${CMAKE_BINARY_DIR}/eigen-install")
set(GFLAGS_PREFIX "${CMAKE_BINARY_DIR}/gflags-install")

# ctest invocation shared by the dependency test steps. Runs the cases at maximum
# concurrency: --parallel ${CTEST_PARALLEL}, defaulting to the core count (each case is an
# lli JVM). Left a cache var so run-tests.sh can pass the run host's core count explicitly
# and it can still be overridden. --output-on-failure so a red test prints its own
# diagnostics into the job log.
include(ProcessorCount)
ProcessorCount(_NPROC)
if(_NPROC EQUAL 0)
    set(_NPROC 8)
endif()
set(CTEST_PARALLEL ${_NPROC} CACHE STRING "Concurrent ctest cases per suite (default: all cores)")
set(CTEST_RUN ${CMAKE_CTEST_COMMAND}
    --parallel ${CTEST_PARALLEL}
    --output-on-failure --timeout 1200)

# JUnit XML per suite/variant, so CI can ingest the per-case results (pass/fail/timing)
# instead of only scraping the job log. ctest's native --output-junit (CMake >= 3.21)
# writes the file at the end of the run even when a case fails, so a red suite still
# uploads its report. Absolute path under the superbuild dir; run-tests.sh runs each
# VARIANT in its own build-<variant> tree, so the files never collide across variants.
set(REPORT_DIR "${CMAKE_BINARY_DIR}/test-reports")
file(MAKE_DIRECTORY "${REPORT_DIR}")

# --- gflags (source -> bitcode; provides the flag lib the Ceres tests link) --------
ExternalProject_Add(gflags
    GIT_REPOSITORY ${GFLAGS_REPO_URL}
    GIT_TAG        ${GFLAGS_TAG}
    GIT_SHALLOW    ON
    CMAKE_ARGS     ${FWD_ARGS}
                   -DCMAKE_INSTALL_PREFIX=${GFLAGS_PREFIX}
                   -DBUILD_STATIC_LIBS=ON
                   -DBUILD_gflags_LIB=ON
                   -DBUILD_TESTING=OFF
                   -DGFLAGS_NAMESPACE=google
    INSTALL_DIR    ${GFLAGS_PREFIX})

# --- Eigen (header-only: configure + install so it provides Eigen3Config.cmake) -----
# Ceres 2.2 resolves Eigen with `find_package(Eigen3 3.3 REQUIRED)` in *config* mode —
# the legacy -DEIGEN_INCLUDE_DIR was dropped in Ceres 2.x. So install Eigen to a prefix
# (headers + Eigen3Config.cmake under <prefix>/share/eigen3/cmake) and point Ceres at it
# with -DEigen3_DIR. Nothing is compiled (Eigen is header-only), so this is quick.
# The source tree stays at ${EIGEN_SRC} for the eigen_tests pass, which builds Eigen's
# own test/ subdir out-of-source from the same checkout.
ExternalProject_Add(eigen_src
    GIT_REPOSITORY   ${EIGEN_REPO_URL}
    GIT_TAG          ${EIGEN_TAG}
    GIT_SHALLOW      ON
    SOURCE_DIR       ${EIGEN_SRC}
    CMAKE_ARGS       ${FWD_ARGS}
                     -DCMAKE_INSTALL_PREFIX=${EIGEN_PREFIX}
                     -DBUILD_TESTING=OFF
                     -DEIGEN_BUILD_DOC=OFF)

# --- Eigen test subset (pure bitcode, both variants) --------------------------------
if(ENABLE_EIGEN)
    # Build only the curated targets, then ctest just those (each split test is named
    # <target>_<n>, so anchor the regex on the target names).
    set(_eigen_build_cmds "")
    set(_eigen_regex "^(")
    set(_first TRUE)
    foreach(t ${EIGEN_TESTS})
        list(APPEND _eigen_build_cmds
            COMMAND ${CMAKE_COMMAND} --build <BINARY_DIR> --target ${t})
        if(_first)
            set(_eigen_regex "${_eigen_regex}${t}")
            set(_first FALSE)
        else()
            set(_eigen_regex "${_eigen_regex}|${t}")
        endif()
    endforeach()
    set(_eigen_regex "${_eigen_regex})")

    # Excluded from the lli run (orthogonal to the AVX2/FMA float/double intrinsics under test):
    #   *_ok / *_ko  - Eigen's compile-check meta-tests (EigenTesting.cmake). Their add_test
    #                  COMMAND is `cmake --build ... --target <t>_ok`, i.e. they invoke the
    #                  build tool, not a bitcode program. Under CMAKE_CROSSCOMPILING_EMULATOR
    #                  ctest prefixes every command with lli, so lli tries to interpret
    #                  /usr/bin/cmake as bitcode and fails ("cannot dynamically load PIE").
    #                  They must not run under the emulator.
    #   product_small_9  - bool-matrix product; clang packs the 2x2 bool result as <N x i4>.
    #   geo_quaternion_2 - Quaterniond (32-byte) bulk op lowered to <1 x i256>.
    #                  Both hit a pre-existing upstream Sulong limitation: vector literals /
    #                  zeroinitializers whose element type is a VariableBitWidthType (iN with
    #                  N != 1/8/16/32/64) are unsupported (BasicNodeFactory throws
    #                  "iN not yet supported"). This is unrelated to the 256-bit float/double
    #                  vector intrinsics this job validates.
    set(_eigen_exclude "(_ok$|_ko$|^product_small_9$|^geo_quaternion_2$)")

    ExternalProject_Add(eigen_tests
        DEPENDS           eigen_src
        DOWNLOAD_COMMAND  ""
        SOURCE_DIR        ${EIGEN_SRC}
        BINARY_DIR        ${CMAKE_BINARY_DIR}/eigen-tests
        CMAKE_ARGS        ${FWD_ARGS}
                          -DBUILD_TESTING=ON
                          -DEIGEN_TEST_CXX11=ON
                          -DEIGEN_TEST_MAX_SIZE=${EIGEN_TEST_MAX_SIZE}
        # Eigen tests are EXCLUDE_FROM_ALL; build the curated targets explicitly.
        BUILD_COMMAND     ${CMAKE_COMMAND} -E echo "Building Eigen test subset: ${EIGEN_TESTS}"
                          ${_eigen_build_cmds}
        INSTALL_COMMAND   ""
        TEST_BEFORE_INSTALL ON
        TEST_COMMAND      ${CTEST_RUN} -R "${_eigen_regex}" -E "${_eigen_exclude}"
                          --output-junit ${REPORT_DIR}/eigen-${VARIANT}.xml)
endif()

# --- Ceres 2.2 (dense LA: Eigen for bitcode, MKL for mkl) ---------------------------
if(ENABLE_CERES)
    set(CERES_LA_ARGS -DLAPACK=OFF)
    if(VARIANT STREQUAL "mkl")
        if("${MKL_LIB_DIR}" STREQUAL "")
            message(FATAL_ERROR "VARIANT=mkl requires -DMKL_LIB_DIR=<dir with libmkl_rt.so>")
        endif()
        set(_mkl "${MKL_LIB_DIR}/libmkl_rt.so")
        # A single libmkl_rt.so exports both the BLAS (dgemm_ …) and LAPACK (dgetrf_ …)
        # symbol families. lli binds them from bitcode at run time (run-tests.sh passes
        # --llvm.library=libmkl_rt.so + --llvm.libraryPath), and the build links against
        # it so the ELF resolves.
        set(CERES_LA_ARGS -DLAPACK=ON
                          -DBLAS_LIBRARIES=${_mkl}
                          -DLAPACK_LIBRARIES=${_mkl})
    endif()

    ExternalProject_Add(ceres
        DEPENDS        eigen_src gflags
        GIT_REPOSITORY ${CERES_REPO_URL}
        GIT_TAG        ${CERES_TAG}
        GIT_SHALLOW    ON
        # CMAKE_PREFIX_PATH carries both dependency prefixes as a list; LIST_SEPARATOR lets
        # us pass it through ExternalProject without the ';' being eaten. find_package then
        # discovers Eigen3Config.cmake / gflags-config.cmake wherever each install put them
        # (Eigen3_DIR is an extra hint for the usual <prefix>/share/eigen3/cmake layout).
        # NB: LIST_SEPARATOR must NOT be '|' — ExternalProject rewrites *every* occurrence of
        # the separator (in all step commands, including TEST_COMMAND) to ';', which would
        # silently turn the ctest -E alternation regex below into a literal-';' regex that
        # matches nothing (so no test would be excluded). '@' never appears in these paths
        # or in the exclusion regex, so it is a safe separator.
        LIST_SEPARATOR @
        CMAKE_ARGS     ${FWD_ARGS}
                       -DEigen3_DIR=${EIGEN_PREFIX}/share/eigen3/cmake
                       -DCMAKE_PREFIX_PATH=${GFLAGS_PREFIX}@${EIGEN_PREFIX}
                       -Dgflags_DIR=${GFLAGS_PREFIX}/lib/cmake/gflags
                       -DBUILD_TESTING=ON
                       -DBUILD_EXAMPLES=OFF
                       -DBUILD_BENCHMARKS=OFF
                       -DMINIGLOG=ON
                       -DGFLAGS=ON
                       -DSUITESPARSE=OFF
                       -DEIGENSPARSE=ON
                       -DUSE_CUDA=OFF
                       -DPROVIDE_UNINSTALL_TARGET=OFF
                       ${CERES_LA_ARGS}
        INSTALL_COMMAND ""
        TEST_BEFORE_INSTALL ON
        # Ceres' test binaries build with the library (not EXCLUDE_FROM_ALL). We run the whole
        # suite on the interpreter except three orthogonal categories excluded via ctest -E.
        # All AVX2/FMA/numeric correctness gaps that this suite surfaced are FIXED in the Sulong
        # fork (llvm.ldexp scalb, correctly-rounded llvm.log2, llvm.experimental.constrained.fma,
        # integer llvm.masked.{load,store}.v<N>i32) — so jet_test, cubic_interpolation_test,
        # fixed_array_test, dynamic_sparsity_test and sparse_cholesky_test stay IN the run.
        # Excluded:
        #   * cuda            — no GPU device under lli.
        #   * ^ba_ , small_blas_test , rotation_test — heavy iterative solves / large BLAS that
        #     time out purely because the CI JVM has no optimizing Truffle runtime (interpreter
        #     only, no JIT), not because of any miscompile. Sanctioned "named core subset when
        #     interpreter runtime is excessive."
        #   * corrector/graph/ordered_groups/parameter_block/problem/triplet_sparse_matrix/
        #     covariance/manifold_test — these fail only on gtest EXPECT_DEATH subtests, which
        #     use fork(); fork() in the multithreaded JVM-hosted Sulong process is fundamentally
        #     unsupported ("Death tests use fork(), detected N threads"), not an AVX2/FMA issue.
        TEST_COMMAND   ${CTEST_RUN} -E "(cuda|^ba_|^small_blas_test$|^rotation_test$|^corrector_test$|^graph_test$|^ordered_groups_test$|^parameter_block_test$|^problem_test$|^triplet_sparse_matrix_test$|^covariance_test$|^manifold_test$)"
                       --output-junit ${REPORT_DIR}/ceres-${VARIANT}.xml)
endif()
