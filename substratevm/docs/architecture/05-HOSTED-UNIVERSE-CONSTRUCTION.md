# Hosted Universe Construction

## Purpose

Hosted universe construction converts the fixed-point analysis result into the hosted metadata model
used by AOT compilation, image heap construction, and image writing. Static analysis works with
[`AnalysisType`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/meta/AnalysisType.java), [`AnalysisMethod`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/meta/AnalysisMethod.java), and [`AnalysisField`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/meta/AnalysisField.java); later phases work with [`HostedType`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/meta/HostedType.java),
[`HostedMethod`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/meta/HostedMethod.java), and [`HostedField`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/meta/HostedField.java).

This phase is responsible for:

- sealing the analysis universe;
- creating hosted wrappers for all reachable analysis types, fields, and method variants;
- computing type-check metadata, field layouts, vtables, dynamic hubs, and profiling information;
- building the runtime compiler configuration and AOT graph-builder plugins;
- finding hosted native entry points;
- releasing analysis type-flow state after summarized metadata has been transferred.

## Inputs and Entry State

The phase receives:

- fixed-point [`AnalysisUniverse`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/meta/AnalysisUniverse.java) from static analysis;
- [`Inflation`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/analysis/Inflation.java)/[`BigBang`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/BigBang.java) with analysis summaries and unsupported-feature state;
- [`ImageClassLoader`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/ImageClassLoader.java), feature state, native libraries, and image-layer state;
- analysis meta-access and original JVMCI metadata;
- parsed hosted options and class-initialization policy;
- analysis entry points and native entry-point markings.

The fixed-point analysis state remains available and has not yet been released by
`bb.cleanupAfterAnalysis()`.

## Results and Completion States

The phase produces:

- [`HostedUniverse`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/meta/HostedUniverse.java) containing hosted types, methods, fields, signatures, constant pools, vtables,
  dynamic hubs, and deterministic method/field orderings;
- [`HostedMetaAccess`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/meta/HostedMetaAccess.java) for hosted lookups;
- [`RuntimeConfiguration`](../../src/com.oracle.svm.core/src/com/oracle/svm/core/graal/meta/RuntimeConfiguration.java) with hosted providers, compiler backends, graph-builder plugins, foreign
  calls, snippets, and runtime configuration;
- a list of hosted native entry-point methods for image creation;
- completed `beforeUniverseBuilding(...)` feature callbacks;
- analysis cleanup after hosted universe construction, allowing type-flow graphs and type states to
  be garbage collected.

The analysis universe is sealed and converted into hosted metadata.
Later phases use hosted metadata and the AOT runtime configuration rather than mutable analysis
metadata.

## Main Classes

Core anchors:

- [`NativeImageGenerator`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/NativeImageGenerator.java) coordinates
  hosted universe construction in `doRun(...)`.
- [`HostedRuntimeConfigurationBuilder`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/code/HostedRuntimeConfigurationBuilder.java)
  creates runtime compiler configuration for AOT compilation.

## Control Flow

The normal hosted universe construction flow is:

1. After `runPointsToAnalysis(...)` returns without [`ReturnAfterAnalysis`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/NativeImageOptions.java), `doRun(...)` enters the
   universe reporter scope.

2. The generator creates [`HostedUniverse`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/meta/HostedUniverse.java) from [`BigBang`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/BigBang.java). For layered images, the hosted universe is
   registered with the shared-layer writer or extension-layer loader.

3. [`HostedMetaAccess`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/meta/HostedMetaAccess.java) is created over [`HostedUniverse`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/meta/HostedUniverse.java) and the analysis meta-access.

4. [`BeforeUniverseBuildingAccessImpl`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/FeatureImpl.java) is created and
   `Feature.beforeUniverseBuilding(...)` callbacks run.

5. [`UniverseBuilder.build(...)`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/meta/UniverseBuilder.java) seals [`AnalysisUniverse`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/meta/AnalysisUniverse.java) and creates hosted metadata:
   - every analysis type is converted with `makeType(...)`;
   - reached-constraint hierarchy checks run;
   - every analysis field is converted with `makeField(...)`;
   - every original method and method variant is converted with `makeMethod(...)`;
   - method variant maps are attached where a method has multiple variants.

6. The builder computes indirect-call targets. Closed type world uses the hosted method itself as
   target; open type world computes indirect call targets through [`OpenTypeWorldSupport`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/OpenTypeWorldSupport.java).

7. Hosted type metadata is completed:
   - dynamic hub layout is initialized;
   - `TypeCheckBuilder.buildTypeMetadata(...)` creates type-check metadata;
   - declared methods and monitor field information are collected;
   - profiling information is built in parallel;
   - instance and static fields are laid out;
   - method implementations and vtables are collected;
   - dynamic hubs are built;
   - hosted methods and fields are sorted deterministically.

8. [`BuildPhaseProviderImpl.markHostedUniverseBuilt()`](../../src/com.oracle.svm.core/src/com/oracle/svm/core/BuildPhaseProviderImpl.java) records the phase transition.

9. [`HostedRuntimeConfigurationBuilder.build()`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/code/HostedRuntimeConfigurationBuilder.java) creates [`RuntimeConfiguration`](../../src/com.oracle.svm.core/src/com/oracle/svm/core/graal/meta/RuntimeConfiguration.java) from hosted universe,
   hosted meta-access, providers, class-initialization support, and platform configuration.

10. `registerGraphBuilderPlugins(...)` is run again for [`ParsingReason.AOTCompilation`](../../src/com.oracle.svm.core/src/com/oracle/svm/core/ParsingReason.java), this time
    against hosted providers and runtime configuration instead of analysis providers.

11. The generator finds hosted native entry points by scanning analysis methods marked as native
    entry points and looking up their hosted counterparts.

12. Unsupported-feature state is reported, restricted-heap-access callees are recorded, and
    `bb.cleanupAfterAnalysis()` releases analysis type-flow state.

## Key Data Structures

- [`HostedUniverse`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/meta/HostedUniverse.java): hosted metadata graph keyed by analysis/JVMCI elements. It owns hosted type,
  method, field, and signature maps and exposes deterministic ordered method/field collections.
- [`HostedType`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/meta/HostedType.java), [`HostedMethod`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/meta/HostedMethod.java), and [`HostedField`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/meta/HostedField.java): post-analysis metadata wrappers used by
  compilation, heap layout, and image writing.
- [`HostedMetaAccess`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/meta/HostedMetaAccess.java): hosted lookup facade that maps analysis or JVMCI elements to hosted elements.
- [`UniverseBuilder`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/meta/UniverseBuilder.java): single-threaded converter from sealed analysis metadata to hosted metadata.
- [`TypeCheckBuilder`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/meta/TypeCheckBuilder.java): computes type-check metadata for the hosted type hierarchy.
- [`DynamicHub`](../../src/com.oracle.svm.core/src/com/oracle/svm/core/hub/DynamicHub.java): runtime type metadata object whose hosted layout is prepared during this phase.
- [`RuntimeConfiguration`](../../src/com.oracle.svm.core/src/com/oracle/svm/core/graal/meta/RuntimeConfiguration.java): backend/provider configuration for AOT compilation, graph parsing,
  snippets, foreign calls, and runtime metadata construction.
- [`HostedProviders`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/meta/HostedProviders.java): hosted compiler provider set used by AOT graph parsing and compilation.
- Static analysis summaries: summarized analysis information retained after type-flow state is
  cleaned up.
