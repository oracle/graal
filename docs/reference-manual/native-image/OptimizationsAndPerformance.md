---
layout: docs
toc_group: optimizations-and-performance
link_title: Optimizations and Performance
permalink: /reference-manual/native-image/optimizations-and-performance/
---

# Optimizations and Performance

Native Image provides different mechanisms that enable users to optimize a generated binary in terms of performance, file size, build time, debuggability, and other metrics.

### Optimization Levels

Similar to `gcc` and `clang`, users can control the optimization level using the `-O` option.
By default, `-O2` is used which aims for a good tradeoff between performance, file size, and build time.
The following table provides an overview of the different optimization levels and explains when they are useful:

| Level | Optimizations | Use Cases |
|:---:|:---:|---|
| `-Ob` | Reduced | Quick build mode: Speeds up builds during development by avoiding time-consuming optimizations. This can also reduce file size sometimes. |
| `-Os` | Reduced | Optimize for size: `-Os` enables all `-O2` optimizations except those that can increase code or image size significantly. Typically creates the smallest possible images at the cost of reduced performance. |
| `-O0` | None | Typically used together with `-g` to improve the debugging experience. |
| `-O1` | Basic | Trades performance for reduced file size and build time. Oracle GraalVM's `-O1` is somewhat comparable to `-O2` in GraalVM Community Edition. |
| `-O2` | Advanced | **Default:** Aims for good performance at a reasonable file size. |
| `-O3` | All | Aims for the best performance at the cost of longer build times. Used automatically by Oracle GraalVM for [PGO builds](guides/optimize-native-executable-with-pgo.md) (`--pgo` option). `-O3` and `-O2` are identical in GraalVM Community Edition. |

### Optimizing for Specific Machines

Native Image provides a `-march` option that works similarly to the ones in `gcc` and `clang`: it enables users to control the set of instructions that the Graal compiler can use when compiling code to native.
By default, Native Image uses [`x86-64-v3` on x64](https://en.wikipedia.org/wiki/X86-64#Microarchitecture_levels){:target="_blank"} and [`armv8-a` on AArch64](https://en.wikipedia.org/wiki/ARM_architecture_family#Cores){:target="_blank"}.
Use `-march=list` to list all available machine types.
If the generated binary is built on the same or similar machine type that it is also deployed on, use `-march=native`.
This option instructs the compiler to use all instructions that it finds available on the machine the binary is generated on.
If the generated binary, on the other hand, is distributed to users with many different, and potentially very old machines, use `-march=compatibility`.
This reduces the set of instructions used by the compiler to a minimum and thus improves the compatibility of the generated binary.

### Additional Features

Native Image provides additional features to further optimize a generated binary:

 - Profile-Guided Optimization (PGO) can provide additional performance gain and higher throughput for most native images. See [Profile-Guided Optimization](PGO.md).
 - Choosing an appropriate Garbage Collector and tailoring the garbage collection policy can reduce GC times. See [Memory Management](MemoryManagement.md).
 - Loading application configuration during the image build can speed up application startup. See [Class Initialization at Image Build Time](ClassInitialization.md).
