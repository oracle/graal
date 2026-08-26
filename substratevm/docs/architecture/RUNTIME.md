# Native Image Runtime Architecture

This document describes the architecture of a generated Native Image after the build has produced an
executable, shared library, static executable, or image layer. The companion build-time guide is
[BUILDTIME.md](BUILDTIME.md).

The runtime architecture starts where image writing ends. The generated image contains compiled
application code, selected JDK/runtime support, runtime metadata, the image heap, startup entry
points, and the Native Image runtime substrate. Hosted builder implementation classes are not part of
the runtime unless explicitly modeled as runtime support.

## Scope

In scope:

- VM startup and shutdown
- Isolates and isolate threads
- Image heap, runtime allocations, and garbage collection
- Threads, safepoints, stacks, monitors, exceptions, and deoptimization support
- Runtime metadata for reflection, resources, serialization, method handles, JNI, JVMTI, JFR, and
  jcmd
- OS, CPU, C interface, and platform-specific runtime support

Out of scope except at runtime integration boundaries:

- The hosted builder pipeline, static analysis, compilation queue, and image writing
- Graal compiler internals
- Truffle language implementation details
- End-user operational documentation

## Main Runtime Components

Responsibilities:

- Provide the VM runtime embedded in generated images.
- Implement startup, isolates, threads, safepoints, stacks, monitors, memory, heap, GC,
  deoptimization, exception support, JNI/JVMTI, JFR, jcmd, resources, reflection metadata, method
  handles, and low-level C/OS integration.
- Expose platform and architecture-specific runtime/compiler lowering support where runtime code is
  analyzed, compiled, linked, or called by generated images.

Primary CE roots:

- `src/com.oracle.svm.core`
- `src/com.oracle.svm.core.posix`
- `src/com.oracle.svm.core.windows`
- `src/com.oracle.svm.core.genscavenge`
- `src/com.oracle.svm.core.graal.*`
- `src/com.oracle.svm.native.*`

Enterprise runtime extensions follow the same CE runtime and hosted extension boundaries without
being described here by implementation package or source-tree layout.

Architectural boundary:

- Runtime code may be included in generated images.
- Hosted builder code must stay out of generated images unless explicitly modeled as runtime support.

## Runtime Behavior To Derive

The first useful runtime slices are:

1. Startup path for executables, shared libraries, and libjvm mode.
2. Isolate creation, isolate-thread attachment, and teardown.
3. Image heap layout versus runtime heap allocation.
4. Garbage collector responsibilities.
5. Threading, safepoints, stack walking, monitors, and exception handling.
6. Runtime metadata access for reflection, resources, serialization, JNI, JVMTI, JFR, and jcmd.
7. Platform-specific runtime support for POSIX, Windows, and CPU-specific lowering.
