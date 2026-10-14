#!/usr/bin/env bash
#
# Build and run the Eigen + Ceres 2.2 unit tests under Sulong, in both linear-algebra
# backends. Driven by the superbuild in this directory; see CMakeLists.txt for the
# design.
#
# Assumes `mx` is on PATH and the sulong suite is built (`mx --primary-suite-path
# sulong build` has run and produced the assembled GraalVM home containing bin/lli).
#
# Rather than a SULONG_JVM_STANDALONE (whose build needs native-image, which the CI JDK
# lacks), we run against the *built graal tree* with the very tools the Sulong gate uses:
#   - the SULONG_BOOTSTRAP_TOOLCHAIN wrapper compilers (graalvm-clang / graalvm-clang++),
#     resolved via `lli --print-toolchain-path`; and
#   - `lli` from the mx-assembled GraalVM home (`mx graalvm-home`/bin/lli).
# Both are JVM-based and need no native-image.
#
# Environment (all optional except JAVA_HOME):
#   MX             mx command (default: `mx` on PATH)
#   JAVA_HOME      JVMCI JDK that built graal (required)
#   SULONG_DIR     path to the sulong suite (default: <repo>/sulong)
#   BUILD_PARALLEL / CTEST_PARALLEL   overrides for compile / test parallelism
#   SKIP_MKL       set to any non-empty value to skip the MKL-backed variant
#
# Blocking: `set -euo pipefail` + each variant's `cmake --build` runs the ctest suites
# as part of the build (ExternalProject test steps), so any test failure aborts nonzero.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TOOLCHAIN="$HERE/toolchain-sulong.cmake"

MX="${MX:-mx}"
SULONG_DIR="${SULONG_DIR:-$(cd "$HERE/../.." && pwd)}"
[ -d "$SULONG_DIR/mx.sulong" ] || {
    echo "ERROR: SULONG_DIR ($SULONG_DIR) does not look like the sulong suite" >&2
    exit 1
}
: "${JAVA_HOME:?JAVA_HOME must be set to the JVMCI JDK used for the graal build}"
export JAVA_HOME="${JAVA_HOME%/}"
export PATH="$JAVA_HOME/bin:$PATH"
echo "== JAVA_HOME=$JAVA_HOME =="
echo "== SULONG_DIR=$SULONG_DIR =="

# --- resolve lli --------------------------------------------------------------
# Use the direct lli from the mx-assembled GraalVM home (no per-test mx startup).
GVM_HOME="$("$MX" --primary-suite-path "$SULONG_DIR" graalvm-home)"
[ -x "$GVM_HOME/bin/lli" ] || {
    echo "ERROR: no lli at $GVM_HOME/bin/lli — did 'mx build' succeed?" >&2
    exit 1
}
LLI_WRAPPER="$HERE/.sulong-lli"
cat > "$LLI_WRAPPER" <<EOF
#!/usr/bin/env bash
export JAVA_HOME="$JAVA_HOME"
exec "$GVM_HOME/bin/lli" "\$@"
EOF
chmod +x "$LLI_WRAPPER"
export SULONG_LLI="$LLI_WRAPPER"
echo "  lli = $GVM_HOME/bin/lli"
echo "== lli smoke test =="
"$SULONG_LLI" --version 2>&1 | sed 's/^/  /' || true

# --- resolve the Sulong toolchain (clang/clang++ that emit bitcode) -----------
# Primary: the documented public API `lli --print-toolchain-path` (its dir holds
# clang / clang++). Fallback: SULONG_BOOTSTRAP_TOOLCHAIN — but `mx paths` returns the
# packaged *.tar*, not a dir, so extract it and locate the wrapper compilers.
echo "== resolving toolchain =="
TOOLCHAIN_ROOT=""; SULONG_CC=""; SULONG_CXX=""
tp="$("$SULONG_LLI" --print-toolchain-path 2>/dev/null | tail -n1 || true)"
if [ -n "$tp" ] && [ -x "$tp/clang++" ]; then
    TOOLCHAIN_ROOT="$tp"; SULONG_CC="$tp/clang"; SULONG_CXX="$tp/clang++"
else
    bt="$("$MX" --primary-suite-path "$SULONG_DIR" paths SULONG_BOOTSTRAP_TOOLCHAIN 2>/dev/null || true)"
    root="$bt"
    if [ -f "$bt" ]; then
        root="$HERE/.bootstrap-toolchain"
        rm -rf "$root"; mkdir -p "$root"
        tar -xf "$bt" -C "$root"
    fi
    if [ -n "$root" ]; then
        SULONG_CXX="$(find "$root" -type f \( -name 'graalvm-*clang++' -o -name 'clang++' \) 2>/dev/null | head -1 || true)"
        SULONG_CC="$(find "$root" -type f \( -name 'graalvm-*clang' -o -name 'clang' \) 2>/dev/null | head -1 || true)"
        [ -n "$SULONG_CXX" ] && TOOLCHAIN_ROOT="$(dirname "$SULONG_CXX")"
    fi
fi
export SULONG_CC SULONG_CXX
for f in "$SULONG_CC" "$SULONG_CXX"; do
    [ -n "$f" ] && [ -x "$f" ] || {
        echo "ERROR: could not resolve toolchain compiler (got CC='$SULONG_CC' CXX='$SULONG_CXX', root='$TOOLCHAIN_ROOT')" >&2
        [ -n "$TOOLCHAIN_ROOT" ] && ls -1 "$TOOLCHAIN_ROOT" >&2 || true
        exit 1
    }
done
echo "  CC = $SULONG_CC"
echo "  CXX= $SULONG_CXX"

# --- ccache -----------------------------------------------------------------
# Best-effort speedup. When run under GitHub Actions the workflow wires the ccache
# directory into actions/cache (via hendrikmuhs/ccache-action), so hits carry across
# runs. Locally ccache falls back to ~/.ccache.
#
# The compilers are the graalvm-clang++ *wrapper* scripts; hash the real clang they
# invoke (its content is stable) so cache keys track the actual toolchain.
export CCACHE_COMPILERCHECK="${CCACHE_COMPILERCHECK:-content}"
if command -v ccache >/dev/null; then
    ccache --zero-stats >/dev/null 2>&1 || true
    echo "== ccache config =="
    ccache --show-config 2>/dev/null | grep -E 'cache_dir|max_size|compiler_check' | sed 's/^/  /' || true
fi

GEN=(-G "Unix Makefiles")
command -v ninja >/dev/null && GEN=(-G Ninja)

# --- build & test parallelism -----------------------------------------------
# Run compiles and tests at maximum concurrency (all cores; override via BUILD_PARALLEL /
# CTEST_PARALLEL). Each graalvm-clang++ compile is a full clang+LLVM process and each test
# case is an lli JVM; we deliberately do not throttle for memory here.
NPROC="$(nproc 2>/dev/null || echo 8)"
# Build: exported as CMAKE_BUILD_PARALLEL_LEVEL so it is honored by every `cmake --build`
# below (outer superbuild, each ExternalProject build step, and the Eigen per-target
# builds) regardless of generator (Ninja self-parallelizes; a Makefiles fallback would
# otherwise be serial).
export BUILD_PARALLEL="${BUILD_PARALLEL:-$NPROC}"
export CMAKE_BUILD_PARALLEL_LEVEL="$BUILD_PARALLEL"
# Tests: forwarded to CTEST_PARALLEL (ctest --parallel) at configure time below.
export CTEST_PARALLEL="${CTEST_PARALLEL:-$NPROC}"
echo "== parallelism: build -j$BUILD_PARALLEL, ctest --parallel $CTEST_PARALLEL (cores=$NPROC) =="

run_variant() {
    # $1 = variant (bitcode|mkl); remaining args = extra -D options
    local variant="$1"; shift
    local build="$HERE/build-$variant"
    echo "======================================================================"
    echo "== VARIANT=$variant  (build dir: $build)"
    echo "======================================================================"
    rm -rf "$build"
    cmake "${GEN[@]}" -S "$HERE" -B "$build" \
        -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" \
        -DCTEST_PARALLEL="$CTEST_PARALLEL" \
        -DVARIANT="$variant" "$@"
    # The build drives configure/compile of each dependency AND runs its ctest suite
    # (ExternalProject TEST_BEFORE_INSTALL). Any red test fails here.
    cmake --build "$build" --parallel "$BUILD_PARALLEL"
}

# --- variant B: pure bitcode (Eigen dense LA, no external BLAS) --------------
# Also the pass that runs the Eigen SIMD test subset (packetmath, vectorization_logic).
export SULONG_LLI_EXTRA=""
run_variant bitcode -DENABLE_EIGEN=ON -DENABLE_CERES=ON

# --- variant A: MKL-backed (Ceres LAPACK=ON -> libmkl_rt.so via NFI) ---------
# Skip cleanly if SKIP_MKL is set or MKL was not installed on the runner.
if [ -n "${SKIP_MKL:-}" ]; then
    echo "== SKIP_MKL set — skipping MKL-backed variant =="
else
    MKL_LIB_DIR=""
    for d in /opt/intel/oneapi/mkl/latest/lib/intel64 /opt/intel/oneapi/mkl/latest/lib; do
        if [ -e "$d/libmkl_rt.so" ]; then MKL_LIB_DIR="$d"; break; fi
    done
    if [ -z "$MKL_LIB_DIR" ]; then
        hit="$(find /opt/intel -name libmkl_rt.so 2>/dev/null | head -1 || true)"
        [ -n "$hit" ] && MKL_LIB_DIR="$(dirname "$hit")"
    fi

    if [ -n "$MKL_LIB_DIR" ]; then
        echo "== MKL found: $MKL_LIB_DIR/libmkl_rt.so =="
        # Native MKL is bound from bitcode at run time by lli. Sequential threading layer
        # avoids pulling libiomp5 into the interpreter; LP64 matches Eigen/Ceres int width.
        export MKL_THREADING_LAYER=SEQUENTIAL
        export MKL_INTERFACE_LAYER=LP64
        export LD_LIBRARY_PATH="$MKL_LIB_DIR${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
        export SULONG_LLI_EXTRA="--llvm.libraryPath=$MKL_LIB_DIR --llvm.libraries=libmkl_rt.so"
        # Eigen tests already covered in the bitcode pass; here only re-run Ceres on MKL.
        run_variant mkl -DENABLE_EIGEN=OFF -DENABLE_CERES=ON -DMKL_LIB_DIR="$MKL_LIB_DIR"
    else
        echo "== MKL not present — skipping MKL-backed variant =="
    fi
fi

command -v ccache >/dev/null && ccache --show-stats 2>/dev/null | sed 's/^/  /' || true
echo "== eigen-ceres: all variants passed =="
