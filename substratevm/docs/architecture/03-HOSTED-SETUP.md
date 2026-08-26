# Hosted Setup

## Purpose

Hosted setup initializes the Native Image build environment inside [`NativeImageGenerator`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/NativeImageGenerator.java). It creates
the services and analysis model that static analysis consumes: platform singletons, feature state,
substitution processors, class-initialization policy, dynamic-access support, native-library support,
analysis providers, the analysis universe, the analysis engine, graph-builder plugins, snippet
graphs, initial roots, and image-layer state.

This phase ends when [`BigBang`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/BigBang.java) is initialized and the analysis roots, entry-point stubs, heap scanner,
heap verifier, and native-library model are ready for static analysis.

## Inputs and Entry State

Hosted setup receives:

- parsed hosted options from [`HostedOptionValues`](../../src/com.oracle.svm.shared/src/com/oracle/svm/shared/option/HostedOptionValues.java);
- [`ImageClassLoader`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/ImageClassLoader.java) with loaded classes, modules, platform, hosted option parser, and guest type
  discovery;
- image kind, image name, Java main support, and native entry-point map from builder startup;
- original JVMCI providers and meta-access from [`GuestAccess`](../../src/com.oracle.svm.util/src/com/oracle/svm/util/GuestAccess.java);
- substitution processors supplied by the harness or caller;
- registered option values for class initialization, missing-registration behavior, layered images,
  compiler configuration, native libraries, preserve mode, and reporting.

Core hosted state and feature lifecycle callbacks through `afterRegistration(...)` and
`duringSetup(...)` have not yet been created or run.

## Results and Completion States

The phase produces:

- core [`ImageSingletons`](../../../sdk/src/org.graalvm.nativeimage/src/org/graalvm/nativeimage/ImageSingletons.java) for platform, target, class loading, reporting, link-at-build-time support,
  class initialization, missing registration, dynamic access, and native libraries;
- registered and initialized [`Feature`](../../../sdk/src/org.graalvm.nativeimage/src/org/graalvm/nativeimage/hosted/Feature.java) instances with `afterRegistration(...)` and
  `duringSetup(...)` completed;
- default configuration, entry points, and entry-point stubs;
- [`AnalysisUniverse`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/meta/AnalysisUniverse.java) and [`SVMAnalysisMetaAccess`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/analysis/SVMAnalysisMetaAccess.java);
- [`HostedProviders`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/meta/HostedProviders.java) for analysis-time graph parsing and constant reflection;
- [`Inflation`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/analysis/Inflation.java)/[`BigBang`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/BigBang.java), normally [`NativeImagePointsToAnalysis`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/analysis/NativeImagePointsToAnalysis.java);
- [`SVMImageHeapScanner`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/heap/SVMImageHeapScanner.java) and [`SVMImageHeapVerifier`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/heap/SVMImageHeapVerifier.java);
- [`NativeLibraries`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/c/NativeLibraries.java) and C annotation processor support;
- graph-builder plugins, replacements, foreign calls, snippet graph analysis, and initial root
  elements registered for [`ParsingReason.PointsToAnalysis`](../../src/com.oracle.svm.core/src/com/oracle/svm/core/ParsingReason.java);
- image-layer loader/writer state when building layers.

Features are dependency-ordered and advanced through `afterRegistration(...)` and `duringSetup(...)`.
Static analysis can start using the established hosted services, roots, substitutions, and metadata.

## Main Classes

Core anchors:

- [`NativeImageGenerator`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/NativeImageGenerator.java) owns
  `setupNativeImage(...)`, `createAnalysisUniverse(...)`, `createBigBang(...)`, and
  `initializeBigBang(...)`.
- [`NativeImagePointsToAnalysis`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/analysis/NativeImagePointsToAnalysis.java)
  is the default analysis engine created by `createBigBang(...)`.

## Control Flow

The normal hosted setup flow is:

1. [`NativeImageGenerator.doRun(...)`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/NativeImageGenerator.java) creates a [`DebugContext`](../../../compiler/src/jdk.graal.compiler/src/jdk/graal/compiler/debug/DebugContext.java) and calls
   `setupNativeImage(...)`.

2. `setupNativeImage(...)` installs the default exception handler, creates [`SubstrateTarget`](../../src/com.oracle.svm.core/src/com/oracle/svm/core/SubstrateTarget.java), and
   registers early [`ImageSingletons`](../../../sdk/src/org.graalvm.nativeimage/src/org/graalvm/nativeimage/ImageSingletons.java) for [`Platform`](../../../sdk/src/org.graalvm.nativeimage/src/org/graalvm/nativeimage/Platform.java), target description, reporting support, class
   loading, link-at-build-time support, and observable image-heap maps.

3. Class-initialization and missing-registration support are configured from options. Build
   statistics, economy compiler configuration, and layered-image heap map support are installed when
   enabled.

4. [`BuildPhaseProviderImpl.init()`](../../src/com.oracle.svm.core/src/com/oracle/svm/core/BuildPhaseProviderImpl.java) starts phase tracking before feature code can query it.

5. `AutomaticallyRegisteredImageSingletonHandler.registerImageSingletons(...)` registers singleton
   services discovered from the image class loader.

6. `FeatureHandler.registerFeatures(...)` loads automatically registered features, resolves
   most-specific feature implementations, then registers user-enabled feature classes from
   `--features`.

7. After feature registration, the generator initializes core modules, installs
   [`APIDeprecationSupport`](../../../sdk/src/org.graalvm.nativeimage/src/org/graalvm/nativeimage/impl/APIDeprecationSupport.java), invokes `Feature.afterRegistration(...)`, seals dynamic-access
   registration, sets default libc/configuration, and registers Java/C entry points.

8. Layered-image setup creates [`SVMImageLayerWriter`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/imagelayer/SVMImageLayerWriter.java) for shared layers or [`SVMImageLayerLoader`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/imagelayer/SVMImageLayerLoader.java) for
   extension layers and connects that state to later analysis structures.

9. The generator creates the substitution chain:
   - annotation substitutions;
   - C function substitutions;
   - C enum call-wrapper substitutions;
   - proxy renaming substitutions;
   - caller-provided substitutions.

10. `createAnalysisUniverse(...)` creates [`SVMHost`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/SVMHost.java), chooses [`DefaultAnalysisPolicy`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/typestate/DefaultAnalysisPolicy.java) or
    [`BytecodeSensitiveAnalysisPolicy`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/flow/context/bytecode/BytecodeSensitiveAnalysisPolicy.java), selects either the normal points-to analysis factory or the
    experimental reachability factory, and returns the [`AnalysisUniverse`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/meta/AnalysisUniverse.java).

11. The generator creates [`SVMAnalysisMetaAccess`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/analysis/SVMAnalysisMetaAccess.java), [`SVMHostedValueProvider`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/ameta/SVMHostedValueProvider.java), platform configuration,
    [`HostedProviders`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/meta/HostedProviders.java), and simulated class-initializer support.

12. `createBigBang(...)` creates the analysis engine. The normal path is
    [`NativeImagePointsToAnalysis`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/analysis/NativeImagePointsToAnalysis.java); the experimental path creates
    [`NativeImageReachabilityAnalysisEngine`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/analysis/NativeImageReachabilityAnalysisEngine.java).

13. The image heap scanner and heap verifier are created and installed into [`AnalysisUniverse`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/meta/AnalysisUniverse.java).
    Existing analysis types are registered as assignable and reachable types are reported to the
    analysis engine.

14. The C compiler is verified when needed, [`NativeLibraries`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/c/NativeLibraries.java) is created, C annotation imports are
    processed, and native-library support singletons such as [`SizeOfSupport`](../../../sdk/src/org.graalvm.nativeimage/src/org/graalvm/nativeimage/impl/SizeOfSupport.java), [`OffsetOf.Support`](../../src/com.oracle.svm.core/src/com/oracle/svm/core/c/struct/OffsetOf.java),
    and [`CConstantValueSupport`](../../../sdk/src/org.graalvm.nativeimage/src/org/graalvm/nativeimage/impl/CConstantValueSupport.java) are installed.

15. `Feature.duringSetup(...)` callbacks run, then `initializeBigBang(...)` registers unsafe
    recomputed fields, feature substitutions, root elements, graph-builder plugins, replacements,
    foreign calls, and snippet graphs.

16. Shared-layer and preserve-mode roots are registered, entry-point stubs are added, and initialize
    progress is reported.

## Key Data Structures

- [`FeatureHandler`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/FeatureHandler.java): ordered feature registry with automatic feature loading, user feature loading,
  required-feature recursion, and guarded lifecycle callback invocation.
- [`ImageSingletons`](../../../sdk/src/org.graalvm.nativeimage/src/org/graalvm/nativeimage/ImageSingletons.java): build-wide service registry used for platform, target, class loading,
  metadata, native libraries, heap layout, reporting, image layers, and runtime support services.
- [`ClassInitializationSupport`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/classinitialization/ClassInitializationSupport.java): build-time versus run-time class initialization policy and
  associated option processing.
- [`MissingRegistrationSupport`](../../src/com.oracle.svm.core/src/com/oracle/svm/core/MissingRegistrationSupport.java): controls missing-registration diagnostics and filtering.
- [`AnnotationSubstitutionProcessor`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/substitute/AnnotationSubstitutionProcessor.java) and chained [`SubstitutionProcessor`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/infrastructure/SubstitutionProcessor.java): replace, delete, alias, or
  synthesize Java elements for analysis and compilation.
- [`AnalysisUniverse`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/meta/AnalysisUniverse.java): maps original JVMCI elements to analysis types, methods, fields, signatures,
  substitutions, heap scanner/verifier, image-layer loader/writer, and feature substitutions.
- [`SVMHost`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/SVMHost.java): Native Image host VM policy for analysis, class reachability, dynamic hubs,
  substitutions, class initialization, and parsing hooks.
- [`HostedProviders`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/meta/HostedProviders.java): provider set used while parsing graphs for analysis.
- [`Inflation`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/analysis/Inflation.java) / [`BigBang`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/BigBang.java): analysis engine interface used by features, heap scanning, graph parsing,
  and root registration.
- [`SVMImageHeapScanner`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/heap/SVMImageHeapScanner.java) and [`SVMImageHeapVerifier`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/heap/SVMImageHeapVerifier.java): build the shadow heap model and verify it from
  roots and embedded constants.
- [`NativeLibraries`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/c/NativeLibraries.java): C interface metadata and native-library model used by analysis and image
  writing.
