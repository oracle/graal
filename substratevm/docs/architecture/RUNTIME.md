# AR-003-image-runtime: Native Image Runtime Architecture

This document describes the architecture of a generated Native Image after the build has produced an
executable, shared library, static executable, or image layer. The companion build-time architecture is
[§AR-002-image-builder](BUILDTIME.md#ar-002-image-builder-native-image-build-time-architecture), and the place of both systems is [§AR-001-native-image-topology](TOPOLOGY.md#ar-001-native-image-topology-native-image-topology).

The runtime architecture starts where image writing ends. The generated image contains compiled
application code, selected JDK/runtime support, runtime metadata, the image heap, startup entry
points, and the Native Image runtime substrate. Hosted builder implementation classes are not part of
the runtime unless explicitly modeled as runtime support.

## system-overview: System Overview

<!-- grund:fmt off -->
<pre>
 artifact from the builder §AR-002: code, image heap, metadata, singletons   §AR-001.design.2.2
                                       │
                                       ▼
 ┌──────────────────────────────── generated image ────────────────────────────────┐
 │ startup and shutdown · isolates and isolate threads                             │
 │ image heap · runtime allocation · garbage collection                            │
 │ threads · safepoints · stacks · monitors · exceptions · deoptimization          │
 │ runtime metadata: reflection, resources, serialization, JNI, JVMTI, JFR, jcmd   │
 │ OS, CPU, and C interface support                                                │
 └─────────────────────────────────────────────────────────────────────────────────┘

 where: one of two systems §AR-001.design.1.3 · run-time side of §AR-004
</pre>
<!-- grund:fmt on -->

## terminology: Terminology

- **Isolate**: an independent runtime heap and its threads inside one process.
- **Isolate thread**: a thread attached to an isolate.
- **Image heap**: the objects the builder laid out in the artifact, available at startup.
- **Runtime metadata**: the reflection, resource, serialization, JNI, JVMTI, JFR, and jcmd data the
  builder retained.

## design: Design

### design.1: Scope

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

### design.2: Main Runtime Components

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

The boundary with the builder is [§AR-001-native-image-topology.design.2.2](TOPOLOGY.md#design22-buildrun-boundary).

### design.3: Runtime Behavior To Derive

The first useful runtime slices are:

1. Startup path for executables, shared libraries, and libjvm mode.
2. Isolate creation, isolate-thread attachment, and teardown.
3. Image heap layout versus runtime heap allocation.
4. Garbage collector responsibilities.
5. Threading, safepoints, stack walking, monitors, and exception handling.
6. Runtime metadata access for reflection, resources, serialization, JNI, JVMTI, JFR, and jcmd.
7. Platform-specific runtime support for POSIX, Windows, and CPU-specific lowering.

## requirements: Requirements

### requirements.1: Hosted Code Stays Out

Hosted builder code must stay out of the generated image unless it is explicitly modeled as runtime
support, as [§AR-001-native-image-topology.requirements.1](TOPOLOGY.md#requirements1-hosted-code-stays-out-of-the-generated-image) states.
