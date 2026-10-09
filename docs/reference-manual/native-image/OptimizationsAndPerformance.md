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

### Profile-Guided Optimization for Improved Throughput

Consider using Profile-Guided Optimization (PGO) to optimize your application for improved throughput.
These optimizations allow the Graal compiler to leverage profiling information, similar to when it is running as a JIT compiler, when AOT-compiling your application.
For this, perform the following steps:

1. Build your application with `--pgo-instrument`.
2. Run your instrumented application with a representative workload to generate profiling information. Profiles collected from this run are stored by default in the _default.iprof_ file.
3. Rebuild your application with the `--pgo` option. You can pass a custom _.iprof_ file with `--pgo=<your>.iprof`, otherwise _default.iprof_ is used. This will rebuild your image and generate an optimized version of your application.

> Note: Not available in GraalVM Community Edition.

Find more information on this topic in [Basic Usage of Profile-Guided Optimization](PGO-Basic-Usage.md).

### ML-Powered Profile Inference for Enhanced Performance

Native Image supports machine learning-driven static profiling, as a built-in capability.
The **Graal Neural Network (GraalNN)** static profiler uses a model tailored to the selected optimization level: `gnn-O2` with `-O2` and `gnn-O3` with `-O3`.

> Note: Not available in GraalVM Community Edition.

Note that if the user provides a [PGO profile](#profile-guided-optimization-for-improved-throughput) using the `--pgo` option, additional ML inference is unnecessary and therefore disabled automatically.

Key Points:

* The GraalNN `gnn-O2` model is used with `-O2` by default.
* The GraalNN `gnn-O3` model is used with `-O3`.

### Optimizing for Specific Machines

Native Image provides a `-march` option that works similarly to the ones in `gcc` and `clang`: it enables users to control the set of instructions that the Graal compiler can use when compiling code to native.
By default, Native Image uses [`x86-64-v3` on x64](https://en.wikipedia.org/wiki/X86-64#Microarchitecture_levels){:target="_blank"} and [`armv8-a` on AArch64](https://en.wikipedia.org/wiki/ARM_architecture_family#Cores){:target="_blank"}.
Use `-march=list` to list all available machine types.
If the generated binary is built on the same or similar machine type that it is also deployed on, use `-march=native`.
This option instructs the compiler to use all instructions that it finds available on the machine the binary is generated on.
If the generated binary, on the other hand, is distributed to users with many different, and potentially very old machines, use `-march=compatibility`.
This reduces the set of instructions used by the compiler to a minimum and thus improves the compatibility of the generated binary.

### Position-Independent Code

Native Image generally builds executables as position-independent executables (PIE).
On most platforms, this is already the default for the system toolchain that Native Image uses.
On Linux systems, Native Image also requests PIE explicitly.
Shared library images (`-shared`) are always position-independent.
The operating system can use address space layout randomization (ASLR) on position-independent code to make its location in memory less predictable, which improves security.

Typically, position-independent code introduces many additional _relocations_ for pointers that must be adjusted at runtime based on where the code was loaded.
The dynamic linker needs to process these relocations, which increases startup time, memory usage, and file size.

Native Image substantially reduces this cost by using _relative code pointers_ by default.
Instead of storing absolute code addresses that need adjustment, relative code pointers store offsets from the _code base_, the start of the code area.
At runtime, Native Image keeps the code base address in a dedicated register and computes absolute code addresses by adding that base address to the stored offset.

The overhead of relative code pointers is typically negligible.
However, code with unusually many indirect calls or code that is especially sensitive to register pressure might see small slowdowns.
To turn off relative code pointers, use `-H:-RelativeCodePointers`.
To explicitly disable PIE on Linux systems (even if the system toolchain would otherwise produce PIE), use `-H:NativeLinkerOption=-no-pie`.

### Additional Features

Native Image provides additional features to further optimize a generated binary:
- Choosing an appropriate Garbage Collector and tailoring the garbage collection policy can reduce GC times. See [Memory Management](MemoryManagement.md).
- Using compressed references can lead to better memory efficiency. See [Object Header Size in Native Image](ObjectHeaderSize.md).
- Loading application configuration during the image build can speed up application startup. See [Class Initialization at Image Build Time](ClassInitialization.md).
- The build output may provide some other recommendations that help you get the best out of Native Image. See [Build Output: Recommendations](BuildOutput.md#recommendations).
