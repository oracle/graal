# AR-002-image-builder: Native Image Build-Time Architecture

This document describes the build-time architecture of the GraalVM Native Image project from the
current `substratevm` suite.

It explains how the `native-image` driver starts the hosted builder, how the builder derives a
closed-world model, and how it produces a native executable, shared library, static executable, or
image layer. Runtime behavior belongs in [§AR-003-image-runtime](RUNTIME.md#ar-003-image-runtime-native-image-runtime-architecture); the place of both systems is
[§AR-001-native-image-topology](TOPOLOGY.md#ar-001-native-image-topology-native-image-topology).

## system-overview: System Overview

<!-- grund:fmt off -->
<pre>
 driver §PH-001
 │ builder JVM, argument file   §AR-001.design.2.1
 ▼
 ┌─ builder JVM ──────────────────────────────────────────────┐
 │ startup §PH-002 → hosted setup §PH-003                     │
 │ → static analysis §PH-004 → universe §PH-005               │
 │ → compilation §PH-006 → heap and model §PH-007             │
 │ → writing and linking §PH-008                              │
 │                                                            │
 │ extension mechanisms §AR-002.design.4:                     │
 │   features · substitutions · singletons · options          │
 │ analysis contract §AR-005                                  │
 │ components spanning both systems, e.g. §AR-004             │
 └────────────────────┬───────────────────────────────────────┘
                      ▼ executable · shared library · static executable · layer
                        §AR-001.design.2.2

 where: one of two systems §AR-001.design.1.2 · the runtime it feeds §AR-003
</pre>
<!-- grund:fmt on -->

## terminology: Terminology

- **Analysis universe**: the analysis-time model of types, methods, fields, signatures, and constants.
- **Points-to analysis**: type-flow based reachability and type-state propagation.
- **Hosted universe**: the post-analysis metadata used for compilation and image writing.
- **Feature**: a component that participates in the build through lifecycle callbacks.
- **Substitution**: a replaced, deleted, aliased, or synthesized JDK or runtime element.
- **Image singleton**: a hosted or runtime service registered in
  [`ImageSingletons`](../../../sdk/src/org.graalvm.nativeimage/src/org/graalvm/nativeimage/ImageSingletons.java).

## design: Design

### design.1: Scope

In scope:

- CE suite: `graal/substratevm`
- The `native-image` driver and hosted image builder
- Static analysis, hosted universe construction, compilation, image heap layout, image writing, and
  build-time configuration of runtime substrate support
- Native Image-specific extension mechanisms such as features, substitutions, options, and
  [`ImageSingletons`](../../../sdk/src/org.graalvm.nativeimage/src/org/graalvm/nativeimage/ImageSingletons.java)

Out of scope for this document except at integration boundaries:

- Runtime behavior of the generated image; see [§AR-003-image-runtime](RUNTIME.md#ar-003-image-runtime-native-image-runtime-architecture)
- The Graal compiler suite internals
- Truffle language implementations
- GraalVM product packaging outside the Native Image suites
- Native Image manuals under `graal/docs/reference-manual/native-image`

### design.2: Source Facts

The CE suite descriptor defines 63 projects and 39 [mx distributions](https://github.com/graalvm/mx/blob/master/README.md#java-distributions)

The central CE distributions are:

- `SVM`: image builder components
- `POINTSTO`: static analysis for ahead-of-time reachability
- `NATIVE_IMAGE_BASE`: shared base for image building and pointsto
- `SVM_DRIVER`: the `native-image` building tool
- `SVM_CONFIGURE`, `SVM_AGENT`, `SVM_DIAGNOSTICS_AGENT`: metadata configuration and agent tooling

The largest CE source modules by Java/C/C++/header file count are currently:

- `com.oracle.svm.core`: runtime substrate: VM services, image runtime, threads, heap, GC
  interfaces, JDK substitutions, JNI/JVMTI, JFR, reflection, resources, metadata, and low-level
  support.
- `com.oracle.svm.hosted`: hosted image builder: build lifecycle, features, analysis setup,
  substitutions, class initialization, image heap/model, compilation orchestration, and image
  writing.
- `com.oracle.graal.pointsto`: static analysis engine and analysis universe.
- `com.oracle.objectfile`: object file, debug, and binary emission support.
- `com.oracle.svm.core.posix` / `com.oracle.svm.core.windows`: OS-specific runtime support.
- `com.oracle.svm.core.genscavenge`: serial and epsilon garbage collector implementation.
- `com.oracle.svm.configure`: metadata/configuration parsing and model support.
- `com.oracle.svm.graal`: Native Image integration with the Graal compiler.
- `com.oracle.svm.driver`: external `native-image` command implementation.

### design.3: Build-Time Functional Architecture

Native Image is best understood as a two-process/product architecture:

1. The `native-image` driver prepares the build command, resolves classpath/module-path inputs,
   processes `native-image.properties`, configures the builder JVM, and launches the image builder.
2. The hosted image builder runs on a JVM, loads application and builder classes, executes the image
   build lifecycle, and writes a native executable, shared library, static executable, or image layer.
3. The generated image contains a closed-world runtime substrate and compiled application code. It
   must not contain hosted builder implementation classes.

The current hosted build pipeline is anchored by
[`com.oracle.svm.driver.NativeImage`](../../src/com.oracle.svm.driver/src/com/oracle/svm/driver/NativeImage.java),
[`com.oracle.svm.hosted.NativeImageGeneratorRunner`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/NativeImageGeneratorRunner.java),
and
[`com.oracle.svm.hosted.NativeImageGenerator`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/NativeImageGenerator.java).

The main build flow spans the external driver and the hosted builder JVM: step 1 runs in the
driver; steps 2–8 run in the builder after the driver launches
[`NativeImageGeneratorRunner`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/NativeImageGeneratorRunner.java).
The driver subsequently waits for and interprets the builder exit status, and can launch a
rebuild-after-analysis invocation when requested by the builder.

The main build flow is:

1. Driver setup
   - Described by [§PH-001-driver-setup](phases/PH-001-driver-setup.md#ph-001-driver-setup-driver-setup).
   - Parse user options and embedded `native-image.properties`.
   - Derive builder classpath/module-path and image classpath/module-path.
   - Configure the JVM running the image builder.
   - Launch the builder JVM.

2. Builder startup
   - Described by [§PH-002-builder-startup](phases/PH-002-builder-startup.md#ph-002-builder-startup-builder-startup).
   - Install the Native Image class loader.
   - Parse hosted/runtime options.
   - Load build-time classes and register automatically discovered builder services.
   - Determine image kind and Java/C entry points.

3. Hosted setup
   - Described by [§PH-003-hosted-setup](phases/PH-003-hosted-setup.md#ph-003-hosted-setup-hosted-setup).
   - Install platform, option, singleton, class loading, and substitution support.
   - Register features and their dependencies.
   - Configure analysis policies, class initialization, native libraries, and layered-image support.

4. Static analysis
   - Described by [§PH-004-static-analysis](phases/PH-004-static-analysis.md#ph-004-static-analysis-static-analysis).
   - Build and iterate the analysis universe.
   - Track reachable types, methods, fields, constants, resources, and metadata.
   - Notify feature lifecycle callbacks during analysis.
   - Report unsupported features and static analysis diagnostics.

5. Hosted universe construction
   - Described by [§PH-005-hosted-universe-construction](phases/PH-005-hosted-universe-construction.md#ph-005-hosted-universe-construction-hosted-universe-construction).
   - Transform analysis metadata into hosted metadata.
   - Build hosted types, methods, fields, signatures, and constant pools.
   - Prepare compiler/runtime configuration and graph builder plugins.

6. Compilation
   - Described by [§PH-006-compilation](phases/PH-006-compilation.md#ph-006-compilation-compilation).
   - Create the compile queue.
   - Compile reachable hosted methods with the configured Graal backends.
   - Build code cache layout, constants, and runtime metadata.

7. Image heap and image model
   - Described by [§PH-007-image-heap-and-image-model](phases/PH-007-image-heap-and-image-model.md#ph-007-image-heap-and-image-model-image-heap-and-image-model).
   - Build the native image heap model from reachable constants and roots.
   - Verify and seal the shadow heap.
   - Lay out heap objects.
   - Create the abstract image for the selected image kind.

8. Image writing and linking
   - Described by [§PH-008-image-writing-and-linking](phases/PH-008-image-writing-and-linking.md#ph-008-image-writing-and-linking-image-writing-and-linking).
   - Emit image/object-file state and debug information.
   - Link the final executable or library.
   - Run final feature callbacks and optional layer archiving.

### design.4: Extension Mechanisms

Important architectural extension mechanisms:

- Feature lifecycle callbacks: allow components to participate in setup, analysis, compilation, heap
  layout, image creation, and image writing.
- [`ImageSingletons`](../../../sdk/src/org.graalvm.nativeimage/src/org/graalvm/nativeimage/ImageSingletons.java):
  global registry for hosted and runtime services, with layered-image-specific singleton handling.
- Substitutions: replace, delete, alias, or synthesize JDK/runtime elements for Native Image.
- Options: hosted, runtime, expert, diagnostic, and product-specific configuration.
- Class initialization policies: decide build-time versus run-time initialization.
- Graph builder plugins, snippets, phases, and lowerings: compiler integration hooks.
- Platform and architecture abstractions: split common runtime behavior from OS/CPU-specific code.
- Suite distributions and Java modules: define packaging, dependency, export, and service boundaries.

### design.5: Component Architecture

#### design.5.1: Driver And Builder Front Door

Primary roots and classes:

- `src/com.oracle.svm.driver`
- `src/com.oracle.svm.driver.launcher`
- [`com.oracle.svm.driver.NativeImage`](../../src/com.oracle.svm.driver/src/com/oracle/svm/driver/NativeImage.java)
- [`com.oracle.svm.hosted.NativeImageGeneratorRunner`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/NativeImageGeneratorRunner.java)
- [`com.oracle.svm.hosted.NativeImageGenerator`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/NativeImageGenerator.java)

Responsibilities:

- Convert command-line and metadata configuration into builder inputs.
- Separate driver options, hosted options, runtime options, and Java arguments.
- Start and supervise the hosted build.
- Own the top-level build lifecycle and progress reporting.

#### design.5.2: Hosted Build System

Primary root:

- `src/com.oracle.svm.hosted`

Responsibilities:

- Build-time class loading.
- Feature lifecycle management.
- Substitution processing.
- Class initialization policy.
- Static analysis setup and integration.
- Hosted universe construction.
- Image heap creation and verification.
- Compile queue setup and image writing.

Important subareas:

- `analysis`: Native Image analysis engines and flow integration.
- `classinitialization`: build-time versus run-time initialization policy.
- `dynamicaccess`: reflection, JNI, proxy, serialization, resource, and other dynamic metadata.
- `image`: image heap, code cache, object layout, image model, writing.
- `imagelayer` and `snapshot`: layered image persistence and loading.
- `meta`: hosted metadata wrappers for analysis metadata.
- `substitute`: substitutions and deleted/annotated element modeling.

#### design.5.3: Static Analysis

Primary roots:

- `src/com.oracle.graal.pointsto`
- `src/com.oracle.graal.reachability`
- `src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/analysis`

Responsibilities:

- Model the closed or partially open type world.
- Discover reachable methods, fields, types, and constants.
- Propagate type states through method and field flows.
- Feed analysis results into the hosted universe.
- Produce analysis diagnostics and reports.

Key concepts:

- Analysis universe: analysis-time model of types, methods, fields, signatures, and constants.
- Points-to analysis: type-flow based reachability and type-state propagation.
- Hosted universe: post-analysis metadata used for compilation and image writing.

#### design.5.4: Runtime Boundary

The build embeds selected runtime substrate code, metadata, and image-heap state into the generated
image. This page describes that interaction from the builder side. The runtime architecture itself is
described by [§AR-003-image-runtime](RUNTIME.md#ar-003-image-runtime-native-image-runtime-architecture).

The boundary itself is [§AR-001-native-image-topology.design.2.2](TOPOLOGY.md#design22-buildrun-boundary), and what may cross it is
[§AR-001-native-image-topology.requirements.1](TOPOLOGY.md#requirements1-hosted-code-stays-out-of-the-generated-image).

#### design.5.5: Compiler Integration

Primary roots:

- `src/com.oracle.svm.graal`
- `src/com.oracle.svm.core.graal`
- `src/com.oracle.svm.core.graal.amd64`
- `src/com.oracle.svm.core.graal.aarch64`
- `src/com.oracle.svm.core.graal.riscv64`
- `src/com.oracle.svm.core.graal.llvm`

Responsibilities:

- Configure Graal for Native Image.
- Provide Native Image-specific nodes, snippets, phases, lowerings, backend integration, calling
  conventions, frame states, code info, and platform-specific code generation support.

#### design.5.6: Binary And Native Interop Support

Primary roots:

- `src/com.oracle.objectfile`
- `src/com.oracle.svm.native.*`
- `src/com.oracle.svm.libjvm`
- `src/com.oracle.svm.libffi`
- `src/com.oracle.svm.core.c`
- `src/com.oracle.svm.hosted.c`

Responsibilities:

- Describe and emit native object files.
- Generate C headers and helper libraries.
- Support C entry points, C calls, JNI invocation, libjvm mode, libffi, and platform native glue.

#### design.5.7: Configuration And Metadata

Primary roots:

- `src/com.oracle.svm.configure`
- `src/com.oracle.svm.agent`
- `src/com.oracle.svm.diagnosticsagent`
- `src/com.oracle.svm.hosted.dynamicaccess`
- `src/com.oracle.svm.core.configure`

Responsibilities:

- Parse and represent reachability metadata.
- Collect dynamic-access metadata through agents.
- Register metadata for reflection, JNI, proxies, serialization, resources, predefined classes, and
  foreign access.
- Encode metadata into the image runtime.

#### design.5.8: Language And Tooling Integrations

Primary roots:

- `src/com.oracle.svm.truffle`
- `src/com.oracle.svm.polyglot`
- `src/com.oracle.svm.truffle.nfi.*`
- `src/com.oracle.svm.jdwp.*`
- `src/com.oracle.svm.interpreter`
- `src/com.oracle.svm.guest*`

Responsibilities:

- Integrate Truffle/polyglot runtimes.
- Support native function interface variants.
- Provide debugging support.
- Support interpreter and guest-runtime experiments.

## requirements: Requirements

### requirements.1: Phases Hand Off Completion States

Each build phase starts from the completion state of the phase before it: a phase's
inputs and entry state are the results and completion states of its predecessor.

### requirements.2: Only Runtime Support Crosses Into the Image

The builder must embed hosted code in the artifact only when it is explicitly modeled as runtime
support. This realizes [§AR-001-native-image-topology.requirements.1](TOPOLOGY.md#requirements1-hosted-code-stays-out-of-the-generated-image).
