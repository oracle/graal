---
layout: docs
toc_group: llvm
link_title: LLVM Compatibility
permalink: /reference-manual/llvm/Compatibility/
---
# LLVM Compatibility

GraalVM supports LLVM bitcode versions 4.0 to 12.0.1.
It is recommended to use the LLVM toolchain shipped with GraalVM.

## Optimizations Flags

In contrast to the static compilation model of LLVM languages, in GraalVM the machine code is not directly produced from the LLVM bitcode.
There is an additional dynamic compilation step by the Graal compiler.

First, the LLVM frontend (e.g., `clang`) performs optimizations on the bitcode level, and then the Graal compiler does its own optimizations on top of that during dynamic compilation.
Some optimizations are better when done ahead-of-time on bitcode, while other optimizations are better left for the dynamic compilation of the Graal compiler, when profiling information is available.

The LLVM toolchain that is shipped with GraalVM automatically selects the recommended flags by default.

Generally, all optimization levels should work, but for a better result, it is recommended to compile the bitcode with the optimization level `-O1`.

For cross-language interoperability, the `-mem2reg` optimization is required.
There are two ways to get that: either compile with at least `-O1`, or use the `opt` tool to apply the `-mem2reg` optimization manually.
