# Compilation

## Purpose

The compilation phase compiles hosted methods selected by the hosted universe into machine code and
builds the code-cache model that image creation will write into the final object file. It starts
after hosted metadata and runtime compiler configuration are available and ends with a laid-out
[`NativeImageCodeCache`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/image/NativeImageCodeCache.java) containing method code, constants, and runtime metadata.

This phase is responsible for:

- running `beforeCompilation(...)` feature callbacks;
- creating and finishing the [`CompileQueue`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/code/CompileQueue.java);
- compiling reachable hosted methods with the configured Graal backends;
- laying out code-cache constants and compiled methods;
- building runtime metadata for code lookup, deoptimization, stack walking, exception handling, and
  related runtime services;
- running `afterCompilation(...)` feature callbacks;
- verifying the shadow heap again after compilation has produced embedded constants.

## Inputs and Entry State

The phase receives:

- [`HostedUniverse`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/meta/HostedUniverse.java), [`HostedMetaAccess`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/meta/HostedMetaAccess.java), and hosted entry points;
- [`RuntimeConfiguration`](../../src/com.oracle.svm.core/src/com/oracle/svm/core/graal/meta/RuntimeConfiguration.java) and hosted providers/backends;
- [`AnalysisUniverse`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/meta/AnalysisUniverse.java) summaries retained after analysis cleanup;
- [`NativeImageHeap`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/image/NativeImageHeap.java) model object, initially empty but connected to hosted metadata;
- feature state, native libraries, debug context, and target platform;
- parsed hosted options such as deoptimization testing, backend selection, and compilation
  diagnostics.

The image heap has not yet been laid out, and no final code-cache layout or runtime code metadata
has been produced.

## Results and Completion States

The phase produces:

- [`CompileQueue`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/code/CompileQueue.java) with completed compilation tasks and [`CompilationResult`](../../../compiler/src/jdk.graal.compiler/src/jdk/graal/compiler/code/CompilationResult.java) objects;
- [`NativeImageCodeCache`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/image/NativeImageCodeCache.java) containing ordered compilations, code-cache constants, method layout,
  code-area metadata, and runtime metadata;
- heap additions for code-cache constants that later participate in image heap layout;
- cleared hosted method graph state after compilation to reduce memory usage;
- completed `beforeCompilation(...)` and `afterCompilation(...)` feature callbacks;
- a verified shadow heap after compilation.

Later phases may lay out heap objects and write object-file sections, but must not add methods to the
compile queue.

## Main Classes

Core anchors:

- [`NativeImageGenerator`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/NativeImageGenerator.java) coordinates
  compilation in `doRun(...)`.
- [`NativeImageCodeCacheFactory`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/image/NativeImageCodeCacheFactory.java)
  creates the target-specific code-cache implementation.

## Control Flow

The normal compilation flow is:

1. [`NativeImageGenerator.doRun(...)`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/NativeImageGenerator.java) creates [`NativeImageHeap`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/image/NativeImageHeap.java) from [`AnalysisUniverse`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/meta/AnalysisUniverse.java),
   [`HostedUniverse`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/meta/HostedUniverse.java), hosted meta-access, hosted constant reflection, and [`ImageHeapLayouter`](../../src/com.oracle.svm.core/src/com/oracle/svm/core/image/ImageHeapLayouter.java).

2. For shared-layer builds, the heap is registered with the image-layer writer.

3. [`BeforeCompilationAccessImpl`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/FeatureImpl.java) is created and
   `Feature.beforeCompilation(...)` callbacks run. Features can inspect hosted metadata, heap state,
   runtime configuration, and native libraries.

4. [`BuildPhaseProviderImpl.markReadyForCompilation()`](../../src/com.oracle.svm.core/src/com/oracle/svm/core/BuildPhaseProviderImpl.java) records that hosted setup and hosted universe
   construction have completed.

5. `HostedConfiguration.instance().createCompileQueue(...)` creates the [`CompileQueue`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/code/CompileQueue.java) from the
   debug context, feature handler, hosted universe, runtime configuration, and deoptimization test
   mode.

6. If runtime compilation callbacks are installed, they observe compile queue creation through
   `RuntimeCompilationCallbacks.onCompileQueueCreation(...)`.

7. `compileQueue.finish(debug)` parses and compiles queued hosted methods. The queue owns
   [`CompileTask`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/code/CompileQueue.java) objects and records [`CompilationResult`](../../../compiler/src/jdk.graal.compiler/src/jdk/graal/compiler/code/CompilationResult.java) objects keyed by hosted method.

8. [`BuildPhaseProviderImpl.markCompileQueueFinished()`](../../src/com.oracle.svm.core/src/com/oracle/svm/core/BuildPhaseProviderImpl.java) records that method compilation is complete.

9. Hosted method graph state is cleared with `HostedMethod.clear()` so image writing has more
   memory available.

10. `NativeImageCodeCacheFactory.get().newCodeCache(...)` creates the code-cache model from the
    compile queue, heap, target platform, and temporary build directory.

11. The code cache is laid out:
    - `layoutConstants()` orders and lays out code-cache constants;
    - `layoutMethods(...)` orders compiled methods and assigns code offsets;
    - `buildRuntimeMetadata(...)` builds runtime code metadata from compilation results and embedded
      constants.

12. [`AfterCompilationAccessImpl`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/FeatureImpl.java) is created and
    `Feature.afterCompilation(...)` callbacks run with compilation results, code cache, heap, and
    runtime configuration.

13. [`BuildPhaseProviderImpl.markCompilationFinished()`](../../src/com.oracle.svm.core/src/com/oracle/svm/core/BuildPhaseProviderImpl.java) records the phase transition.

14. The analysis heap verifier runs again after compilation. This catches embedded constants
    produced by compilation and snippet lowering before image heap layout starts.

## Key Data Structures

- [`CompileQueue`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/code/CompileQueue.java): work queue for hosted method compilation, compile tasks, compilation policy, and
  completed compilation results.
- [`CompileQueue.CompileTask`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/code/CompileQueue.java): per-method compilation unit.
- [`CompilationResult`](../../../compiler/src/jdk.graal.compiler/src/jdk/graal/compiler/code/CompilationResult.java): Graal compilation output for one hosted method, including target code,
  infopoints, data patches, exception handlers, and metadata.
- [`NativeImageCodeCache`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/image/NativeImageCodeCache.java): image-level model of compiled methods, constants, method layout, symbols,
  embedded constants, and runtime code metadata.
- [`LIRNativeImageCodeCache`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/image/LIRNativeImageCodeCache.java): LIR-backed code-cache implementation that writes code bytes and
  handles code/data references for the normal backend.
- [`RuntimeConfiguration`](../../src/com.oracle.svm.core/src/com/oracle/svm/core/graal/meta/RuntimeConfiguration.java): providers and backends used to compile normal methods.
- [`HostedMethod`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/meta/HostedMethod.java): hosted method metadata that links analysis summaries, compiled code, and runtime
  metadata.
- [`ImageHeapScanner`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/heap/ImageHeapScanner.java) / [`HeapSnapshotVerifier`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/heap/HeapSnapshotVerifier.java): reused after compilation to account for newly
  embedded constants.
