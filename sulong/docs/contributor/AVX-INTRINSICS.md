# Generic-IR AVX/AVX2/FMA intrinsic coverage

This document records which LLVM intrinsics Sulong must implement in order to execute AVX,
AVX2, and FMA code, and how that coverage is organized. The guiding principle is **completeness
with respect to what clang actually emits**: modern clang lowers almost every `<immintrin.h>`
operation — and every auto-vectorized loop — to *generic* LLVM IR rather than to `llvm.x86.*`
intrinsics, so the bulk of the work is making Sulong's generic vector paths correct at 256-bit
(and arbitrary) widths.

## What clang emits

Compiling AVX/AVX2/FMA code (`-mavx2 -mfma`, or `-march=haswell`) with clang 20 produces two
kinds of vector operations:

1. **Generic LLVM IR** — plain vector `fadd`/`fmul`/`and`/`shl`/`icmp`, `shufflevector`,
   `select`, and the target-independent intrinsics `llvm.fma.*`, `llvm.fmuladd.*`,
   `llvm.sqrt.*`, `llvm.fabs.*`, `llvm.minnum/maxnum/minimum/maximum.*`,
   `llvm.ceil/floor/trunc/rint/nearbyint/round/roundeven.*`, `llvm.copysign.*`,
   `llvm.ctpop/ctlz/cttz.*`, `llvm.{s,u}{add,sub}.sat.*`, `llvm.abs/smax/smin/umax/umin.*`,
   `llvm.fshl/fshr.*`, `llvm.is.fpclass.*`, and `llvm.vector.reduce.*`. This is the vast
   majority of the vector work and is entirely width-parametric in the IR: the same intrinsic
   name appears at `v4f32`, `v8f32`, `v2f64`, `v4f64`, etc., differing only in the `<N x T>`
   suffix.
2. **`llvm.x86.*` residue** — a small set of operations whose semantics have *no*
   generic-IR equivalent: the x86 min/max NaN/second-operand rule (`llvm.x86.*.min/max.*`),
   the SSE4.1/AVX rounding-mode-immediate forms (`llvm.x86.*.round.*`), and approximate
   reciprocal square root (`llvm.x86.*.rsqrt.*`). These require dedicated nodes and are
   handled separately (see "`llvm.x86.*` residue" below).

## Dispatch model: length-generic by construction

All generic intrinsics are routed through two width-parametric dispatch paths in
`BasicNodeFactory`, so that any vector length is handled by a single implementation with no
per-width special cases:

- **`getTypedLLVMBuiltin`** parses an intrinsic name with `TYPED_INTRINSIC_PATTERN`
  (`^llvm\.(?<op>[a-z0-9.]+)\.(?<type>(v(?<vlen>[0-9]+))?(?<ptype>[if][0-9]+))`), extracting
  the operation, element type, and optional vector length, then calls
  **`getBuiltinFactory(op, kind)`** to obtain a `TypedBuiltinFactory`. The factory's
  `vector`/`vector1`/`vector2`/`vector3` variants supply a scalar node factory *and* a vector
  node factory; the vector factory receives the parsed length. Scalar per-lane nodes are reused
  across widths by wrapping them in the generic `LLVMVectorUnaryNode` / `LLVMVectorArithmeticNode`
  adapters.
- **`matchVectorOp`** handles the horizontal reductions (`llvm.vector.reduce.*`) with
  `VECTOR_INTRINSIC_PATTERN`, again width-parametric.

Adding coverage therefore means adding a `getBuiltinFactory` case (and, where no vector node
existed, a width-generic vector node that mirrors the scalar semantics per lane) rather than
enumerating widths.

## Covered generic intrinsics

The following generic intrinsics execute at scalar and at every vector width. Entries marked
**(width)** gained their length-generic vector path in this effort; the rest were already
length-generic.

| Family | Intrinsics | Semantics / implementation |
|---|---|---|
| Fused multiply-add | `llvm.fma.*` | Fused single rounding via `Math.fma` per lane — bit-exact vs native. |
| Multiply-add | `llvm.fmuladd.*` | Decomposed mul+add (fusion is optional for `fmuladd`; see note). |
| Square root | `llvm.sqrt.*` | `Math.sqrt` per lane. |
| Absolute value | `llvm.fabs.*`, `llvm.abs.*` | Sign-bit clear / integer abs. |
| Rounding | `llvm.ceil/floor/rint.*`, **(width)** `llvm.trunc.*`, `llvm.nearbyint.*`, `llvm.roundeven.*`, `llvm.round.*` | See "Rounding contracts". |
| IEEE min/max | **(width)** `llvm.minimum.*` / `llvm.maximum.*` | IEEE-754-2019: propagate NaN, order −0<+0. `Math.min`/`Math.max` match exactly. |
| Numeric min/max | `llvm.maxnum.*`, **(width)** `llvm.minnum.*` | Return the non-NaN operand (see divergence note). |
| Copy sign | **(width)** `llvm.copysign.*` | `Math.copySign` per lane — bit-exact, incl. sign of zero and NaN magnitude. |
| Bit counting | **(width)** `llvm.ctpop.*` / `llvm.ctlz.*` / `llvm.cttz.*` | Per-lane `Integer/Long.bitCount` / `numberOfLeadingZeros` / `numberOfTrailingZeros`; the ctlz/cttz `is_zero_poison` flag is dropped (Sulong returns the full bit width for a zero lane). |
| Saturating integer | **(width)** `llvm.{s,u}add.sat.*` / `llvm.{s,u}sub.sat.*` | Clamp to the element type's range; one length-generic factory over `LLVMSimpleArithmeticPrimitive` covers all element types and widths. |
| Integer min/max | `llvm.smax/smin/umax/umin.*` | Already length-generic. |
| Funnel shift | `llvm.fshl/fshr.*` | Already length-generic. |
| FP classification | `llvm.is.fpclass.*` | Already length-generic (`vector2`). |
| Reductions | `llvm.vector.reduce.{add,mul,and,or,xor,fadd,fmax,smax,smin,umax,umin}.*`, **(new)** `.fmin`, `.fmul` | `matchVectorOp`; fmin mirrors fmax (NaN-skipping), fmul is the ordered product with a start operand. |

### Rounding contracts

The rounding intrinsics differ only in their tie-breaking and rounding-mode behavior, and it is
easy to get them subtly wrong:

- `llvm.trunc` — toward zero.
- `llvm.ceil` / `llvm.floor` — toward +∞ / −∞.
- `llvm.round` — round half **away from zero**, independent of the rounding mode.
  Implemented as `rint(x)` with an explicit half-away override, *not* `Math.round` (which is
  round-half-**up**, so `Math.round(-2.5) == -2` whereas `llvm.round(-2.5)` must be `-3`).
- `llvm.roundeven` — round half **to even**, independent of the mode: exactly `Math.rint`.
- `llvm.rint` — honor the current rounding mode (default: nearest-ties-to-even).
- `llvm.nearbyint` — `rint` without raising the inexact exception. Sulong does not track FP
  exception flags, so it is identical to `rint` and reuses its factory verbatim.

### Note on `llvm.fmuladd`

The `fmuladd` cases decompose into separate multiply and add nodes. This is spec-legal (fusion
is optional for `fmuladd`, unlike `fma`), but results can differ from native hardware (which
fuses) by up to 1 ulp per operation. `llvm.fma`, by contrast, is contractually fused and is
implemented with `Math.fma` so that it is bit-identical to native.

### Documented divergences

- **`minnum`/`maxnum` on signed zero and NaN-vs-NaN.** Sulong computes these with
  `Math.min`/`Math.max` on the non-NaN lanes, which orders −0 below +0; the x86
  `vminps`/`vmaxps`-based lowering instead returns the *second* operand for equal-magnitude
  zeros (and for NaN operands before the NaN-fixup). The results therefore agree on distinct
  finite lanes and on NaN-vs-number, but may differ in the sign of a ±0 result. Differential
  tests for these two intrinsics deliberately avoid ±0-only and NaN-vs-NaN lanes. (The IEEE
  `minimum`/`maximum` intrinsics have no such divergence and *are* tested on those edges.)
- **`llvm.x86.*.rsqrt.*`** is an *approximation*: hardware guarantees only
  |relative error| ≤ 1.5×2⁻¹². It is implemented as `(float)(1.0 / Math.sqrt(x))` and tested
  with epsilon asserts, never by exact-output comparison against native.

## `llvm.x86.*` residue

These have no generic-IR equivalent and are implemented as dedicated nodes:

| Intrinsic | Node |
|---|---|
| `llvm.x86.{sse,sse2,avx}.{min,max}.{ps,pd}[.256]` | `LLVMX86_VectorMathNode` (128- and 256-bit min/max with the x86 second-operand NaN rule). |
| `llvm.x86.{sse41,avx}.round.{ps,pd}[.256]` | round with 4-bit rounding-mode immediate (`_MM_FROUND_*`: nearest/floor/ceil/trunc). |
| `llvm.x86.{sse,avx}.rsqrt.ps[.256]` | approximate reciprocal square root (see divergence note). |

## Differential test methodology

Coverage is verified by self-checking C programs under
`sulong/tests/com.oracle.truffle.llvm.tests.sulongavx2fma.native/avx2fma/intrinsics/`, compiled
with `-mavx2 -mfma` at `-O1`/`-O2`/`-O3`. Each is run natively to produce a reference
(`ref.out`) and under Sulong; the exit code and stdout must match bit-for-bit.

To exercise a specific generic intrinsic at a chosen width **at every optimization level**, the
tests prefer clang's `__builtin_elementwise_*` / `__builtin_reduce_*` builtins on
`vector_size(32)` types (e.g. `__builtin_elementwise_roundeven`,
`__builtin_elementwise_add_sat`, `__builtin_reduce_min`). Unlike loop-vectorizer pragmas, these
emit the exact intrinsic at the exact width regardless of `-O` level. Inputs are `volatile` to
block constant folding, edge values (NaN, ±0, ±∞, saturation boundaries, exact halves) are
checked via `isnan`/`signbit`/exact comparison as appropriate, and the programs print nothing
and `return 0`. Builtins whose availability is version-dependent are guarded with
`__has_builtin`.

## Related runtime gaps

Executing AVX bitcode also requires several non-intrinsic pieces, implemented alongside:

- CPUID emulation (`LLVMAMD64CpuidNode`) must report AVX/AVX2/FMA/OSXSAVE/XSAVE and
  SSE3/SSSE3/SSE4.x feature bits so `__builtin_cpu_supports`-style dispatch selects the vector
  paths.
- `xgetbv` must be implemented in the inline-asm interpreter (`AsmFactory`); glibc probes it
  (the OSXSAVE gate) before trusting the CPUID AVX bit.
- The Sulong toolchain launcher passes `-mno-sse3 -mno-avx` (`ClangLikeBase`), so
  toolchain-built bitcode cannot contain these intrinsics regardless of runtime support; gated
  relaxation is part of this effort.
