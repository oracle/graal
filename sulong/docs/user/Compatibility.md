# LLVM Compatibility

GraalVM supports LLVM bitcode versions 3.8 to 9.0. It is recommended to use LLVM
shipped with GraalVM.

## Optimizations Flags

In contrast to the static compilation model of LLVM languages, in GraalVM the
machine code is not directly produced from the LLVM bitcode, but there is an
additional dynamic compilation step by the GraalVM compiler.

First, the LLVM frontend (e.g., `clang`) does optimizations on
the bitcode level, and then the GraalVM compiler does its own optimizations on top of that
during dynamic compilation. Some optimizations are better when done
ahead-of-time on the bitcode, while other optimizations are better left for the
dynamic compilation of the GraalVM compiler, when profiling information is available.

The LLVM toolchain that is shipped with GraalVM automatically selects the
recommended flags by default.

Generally, all optimization levels should work, but, for a better result, it is
recommended to compile the bitcode with optimization level `-O1`.

Cross-language interoperability will only work when the bitcode is compiled
with debug information enabled (`-g`), and the `-mem2reg` optimization is
performed on the bitcode (compiled with at least `-O1`, or explicitly using the
`opt` tool).
