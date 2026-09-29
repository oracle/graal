# AVX/AVX2/FMA intrinsic coverage for Eigen/Ceres workloads

This document records the discovery methodology and the resulting backlog for making Sulong
execute the SIMD code that the Eigen (and, through it, Ceres Solver) C++ libraries emit when
compiled with modern x86 vector flags. It is the authoritative worklist for the intrinsic
implementation effort; re-run the procedure below to regenerate it.

## Methodology

Five small workloads, each instantiated for `float` and `double`, mirroring the dense-solver
hot paths Ceres delegates to Eigen:

| Workload | Eigen entry points |
|---|---|
| `matmul` | dense `MatrixX?` product (GEBP kernel) |
| `llt` | `.llt().solve()` (Cholesky; Ceres `DenseNormalCholeskySolver`) |
| `qr` | `.householderQr().solve()` (Ceres `DenseQRSolver`) |
| `colpivqr` | `.colPivHouseholderQr().solve()` (rank-revealing QR) |
| `arrayops` | coefficient-wise `min/max/abs/round/floor/ceil/select/exp/log/sqrt/rsqrt`, normalization (loss functions, bounds, Jet element ops) |

Compiled against **Eigen 3.4.0** with **clang 20.1.8** (`-std=c++17 -DNDEBUG
-DEIGEN_DONT_PARALLELIZE -S -emit-llvm`) under four flag configurations:
`-O3 -mavx2 -mfma` (primary), `-O2 -mavx2 -mfma`, `-O3 -march=haswell`, and
`-O3 -msse4.2` (SSE-tier pass, no AVX). Distinct `@llvm.*` names were extracted from the
emitted IR and unioned across workloads and configurations.

Pending cross-check (needs a Linux environment): repeat with
`--target=x86_64-unknown-linux-gnu` and with Sulong's own bundled clang, since intrinsic
lowering can drift between LLVM versions.

## Key result

Modern clang lowers almost every `<immintrin.h>` operation Eigen uses to **generic LLVM IR**
(plain vector `fadd`/`fmul`, `shufflevector`, `select`, `llvm.fma.*`, `llvm.fmuladd.*`,
`llvm.sqrt.*`, `llvm.fabs.*`, `llvm.vector.reduce.*`). The `llvm.x86.*` residue is limited to
operations whose semantics have no generic-IR equivalent (x86 min/max NaN behavior, SSE4.1
rounding-mode immediates, approximate reciprocal square root).

## Backlog: intrinsics that crash Sulong today, by frequency

Static occurrence counts summed across all workloads and configurations.

### Generic intrinsics (highest priority — hottest paths)

| Intrinsic | Occurrences | Gap |
|---|---|---|
| `llvm.fma.v4f32` / `llvm.fma.v2f64` / `llvm.fma.f32` / `llvm.fma.f64` | 183 / 169 / 18 / 18 | **`llvm.fma.*` is entirely unimplemented** (no case anywhere in `BasicNodeFactory`). Must be implemented with `Math.fma` per lane — the LLVM `llvm.fma` contract requires fused single rounding; a decomposed mul+add silently diverges from native. |
| `llvm.fmuladd.v8f32` | 5 | Missing from the otherwise-broad `fmuladd` case table in `BasicNodeFactory` (which covers v2/v4/v16 f32 and v2/v3/v4/v8 f64 but skips v8f32). |

### `llvm.x86.*` residue

| Intrinsic | Occurrences | Gap |
|---|---|---|
| `llvm.x86.avx.max.ps.256` / `max.pd.256` | 21 / 21 | 256-bit forms of the already-implemented SSE max nodes — widen `LLVMX86_VectorMathNode` guards to length 8/4. |
| `llvm.x86.avx.round.ps.256` / `round.pd.256` | 12 / 12 | New node: SSE4.1/AVX round with 4-bit rounding-mode immediate (Eigen emits mode 8+0/1/2 for round/floor/ceil under `_MM_FROUND_*`). |
| `llvm.x86.avx.min.ps.256` / `min.pd.256` | 6 / 6 | 256-bit min — widen existing SSE min nodes. |
| `llvm.x86.avx.rsqrt.ps.256` / `llvm.x86.sse.rsqrt.ps` | 6 / 2 | New node: approximate reciprocal sqrt. Hardware guarantees only |rel err| ≤ 1.5×2⁻¹²; implement as `(float) (1.0 / Math.sqrt(x))` and write tests with epsilon asserts, **not** exact-output comparison against native. |
| `llvm.x86.sse41.round.ps` / `round.pd` | 4 / 4 | 128-bit forms of the round node above (emitted by the `-msse4.2` tier). |
| `llvm.x86.sse.max.ps`, `sse.min.ps`, `sse2.max.pd`, `sse2.min.pd` | 7 / 2 / 7 / 2 | Already implemented — no action. |

### Confirmed already covered (no action)

`llvm.sqrt.v2f64/v4f64`, `llvm.fabs.v4f64/v8f32` and the scalar forms (length-generic
`TypedBuiltinFactory.vector1` paths), `llvm.is.fpclass.v4f32/v8f32` (`vector2`),
`llvm.round/ceil/floor.f32/f64` (scalar), `llvm.vector.reduce.add.v4i64` (regex-generic
`matchVectorOp`), `llvm.smax/smin/umin.i64`, `llvm.memcpy/memset`, `llvm.lifetime.*`,
`llvm.stacksave/stackrestore`, `llvm.prefetch.p0`, `llvm.va_start/va_end`,
`llvm.experimental.noalias.scope.decl` (dropped in `DebugInfoFunctionProcessor`).

### Audit note on `llvm.fmuladd`

Existing `fmuladd` cases decompose into separate mul + add nodes. This is spec-legal (fusion
is optional for `fmuladd`, unlike `fma`), but it means results can differ from native
hardware (which fuses) by up to 1 ulp per operation. Acceptable for now; revisit if Ceres
convergence comparisons against native show drift.

## Not observed in these workloads

No `llvm.x86.avx2.gather.*`, no `llvm.x86.fma.*` (all FMA lowers to `llvm.fma`/
`llvm.fmuladd`), no pshufb/pblendvb/hadd/movmsk/dpps/ptest, no masked loads/stores.
Eigen 3.4 dense kernels use contiguous loads and generic shuffles throughout. These families
therefore stay out of scope until a real workload shows them; `LLVMX86_MissingBuiltin`'s
runtime error message identifies any straggler immediately.

## Related runtime gaps (fixed alongside, see git history)

- CPUID emulation (`LLVMAMD64CpuidNode`) must report AVX/AVX2/FMA/OSXSAVE/XSAVE and SSE3/
  SSSE3/SSE4.x feature bits so `__builtin_cpu_supports`-style dispatch selects vector paths.
- `xgetbv` must be implemented in the inline-asm interpreter (`AsmFactory`); glibc probes it
  (OSXSAVE gate) before trusting the CPUID AVX bit, and it currently hits the unsupported-
  instruction fallback.
- The Sulong toolchain launcher passes `-mno-sse3 -mno-avx` (`ClangLikeBase`), so
  toolchain-built bitcode cannot contain these intrinsics regardless of runtime support;
  gated relaxation is part of this effort.
