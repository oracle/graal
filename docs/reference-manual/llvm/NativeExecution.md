---
layout: docs
toc_group: llvm
link_title: Interaction of GraalVM with Native Code
permalink: /reference-manual/llvm/NativeExecution/
---
# Interaction of GraalVM with Native Code

The GraalVM LLVM runtime allows users to run code written in languages that traditionally compile directly to native code.
These languages typically do not require any managed runtime or VM to run.
Therefore, special care is needed to consider the interaction of this code with the [managed runtime of GraalVM](#), in particular if the code is using certain low-level features.

## Limited Access to Low-level System Calls

* Signal handling is performed based on the following assumptions: 
   - The managed runtime assumes that it has full control of handling all signals.
   - Installed signal handlers might behave differently than on native execution.
* Process control and threading is done based on the following aspects:
    - GraalVM assumes it has full control over threading.
    - Multi-threading is supported via the `pthreads` library (for example, `pthread_create`).
    - Directly using process related syscalls like `clone`, `fork`, `vfork`, etc. is not supported.
    - The `exec` function family is not supported.

## Memory Layout

The memory and stack layout of processes running on GraalVM is different than with direct native execution.
In particular, no assumptions are possible about the relative positions of globals, stack variables and so on.

Walking the stack is only possible using the GraalVM APIs.
There is a strict separation between code and data.
Self-modifying code will not work.
Reads, writes and pointer arithmetic on pointers to code are not supported.

## Interaction with System Libraries in Native Mode

In the native execution mode (the default mode), code running on the GraalVM LLVM runtime can do calls to real native libraries (for example, system libraries).
These calls behave similar to JNI calls in Java: they temporarily leave the managed execution environment.

Since the code executed in these libraries is not under the control of GraalVM, that code can essentially do anything.
In particular, no multicontext isolation applies, and GraalVM APIs like the virtual filesystem are bypassed.

Note that this applies in particular to most of the standard C library.

## Managed Execution Mode

The managed mode (enabled with the `--llvm.managed` option) is a special execution mode where the LLVM runtime runs purely in managed mode, similar to all other GraalVM supported languages.

> Note: The managed mode is only available in GraalVM Enterprise Edition.

In this mode, by design, it is not allowed to call native code and access native memory.
All memory is managed by the garbage collector, and all code that should be run needs to be compiled to bitcode.

Pointer arithmetic is only possible to the extent allowed by the C standard.
In particular, overflows are prevented, and it is not possible to access different allocations via out-of-bounds access.
All such invalid accesses result in runtime exceptions rather than in undefined behavior.

In managed mode, GraalVM simulates a virtual Linux/AMD64 operating system, with [musl libc](https://www.musl-libc.org/) and [libc++](https://libcxx.llvm.org/) as the C/C++ standard libraries.
All code needs to be compiled for that system, and can then be used to run on any architecture or operating system supported by GraalVM.
Syscalls are virtualized and routed through appropriate GraalVM APIs.

## Polyglot Interaction Between Native Code and Managed Languages

When using polyglot interoperability between LLVM languages (e.g., C/C++) and managed languages (e.g., JavaScript, Python, Ruby), some care must be taken with the manual memory management.
Note that this section only applies to the native mode of execution (the default).
In managed mode (enabled with the `--llvm.managed` option and only available in GraalVM Enterprise), the LLVM runtime itself behaves like a managed language, and the polyglot interaction is the same as between other managed languages.

* Garbage collection policies to be considered:
    - Pointers to objects of managed languages are managed by a garbage collector, therefore they do not need to be freed manually.
    - On the other hand, pointers to allocations from the LLVM code (e.g., `malloc`) are not under control of the garbage collector, so they need to be deallocated manually.
* Unmanaged heap policies to be considered:
    - Native memory (e.g., `malloc`, data sections, thread locals) is not under the control of a garbage collector.
    - Pointers to foreign objects controlled by the garbage collector can not be stored in native memory directly.
    - There are handle functions available to work around this limitation (see `graalvm/llvm/handles.h`).