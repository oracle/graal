# Static Analysis

## Purpose

The static analysis step computes the closed-world model that the rest of a normal Native Image
build consumes. It starts after hosted setup has created the analysis universe, analysis engine,
feature handler, native-library model, graph-builder plugins, substitutions, and initial roots. It
ends before the hosted universe is created.

The step is responsible for:

- finding reachable types, methods, fields, constants, resources, and dynamic-access metadata;
- propagating points-to type states through parsed method graphs;
- scanning build-time objects that can become image-heap objects;
- repeatedly running feature callbacks until the analysis reaches a fixed point;
- reporting unsupported features, user limitation violations, and analysis diagnostics.

This document describes the main Native Image use case. Web Image and other alternative front ends
are outside the current scope.

## Inputs and Entry State

The static analysis phase receives state prepared by hosted setup:

- parsed hosted and runtime options from [`HostedOptionValues`](../../src/com.oracle.svm.shared/src/com/oracle/svm/shared/option/HostedOptionValues.java);
- the [`ImageClassLoader`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/ImageClassLoader.java) with application, builder, platform, and module information;
- registered features in [`FeatureHandler`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/FeatureHandler.java);
- a populated [`AnalysisUniverse`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/meta/AnalysisUniverse.java) containing JVMCI wrappers for types, methods, fields, signatures,
  substitutions, object replacers, feature substitutions, and image-layer support;
- an [`Inflation`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/analysis/Inflation.java)/[`BigBang`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/BigBang.java) analysis engine, normally [`NativeImagePointsToAnalysis`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/analysis/NativeImagePointsToAnalysis.java);
- analysis providers and graph-builder plugins configured for [`ParsingReason.PointsToAnalysis`](../../src/com.oracle.svm.core/src/com/oracle/svm/core/ParsingReason.java);
- initial roots, including system roots from `registerRootElements(...)`, Java/C entry points,
  feature roots, replacement/snippet graphs, native-library imports, and entry-point stubs;
- [`NativeLibraries`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/c/NativeLibraries.java), [`ClassInitializationSupport`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/classinitialization/ClassInitializationSupport.java), [`MissingRegistrationSupport`](../../src/com.oracle.svm.core/src/com/oracle/svm/core/MissingRegistrationSupport.java), service catalog
  state, and image-layer state where enabled;
- [`SVMImageHeapScanner`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/heap/SVMImageHeapScanner.java) and [`SVMImageHeapVerifier`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/heap/SVMImageHeapVerifier.java) for shadow-heap scanning and verification.

Reachability remains mutable, and the analysis lifecycle callbacks have not yet run.

## Results and Completion States

The step produces the analysis result consumed by hosted universe construction:

- an [`AnalysisUniverse`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/meta/AnalysisUniverse.java) whose [`AnalysisType`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/meta/AnalysisType.java), [`AnalysisMethod`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/meta/AnalysisMethod.java), and [`AnalysisField`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/meta/AnalysisField.java) objects are
  marked reachable, instantiated, invoked, accessed, unsafe accessed, or otherwise registered;
- method type-flow graphs and type-state summaries used by [`UniverseBuilder`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/meta/UniverseBuilder.java);
- shadow-heap state for build-time objects, static fields, array elements, embedded constants, and
  image-heap roots discovered during analysis;
- processed `@CLibrary` annotations in [`NativeLibraries`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/c/NativeLibraries.java);
- feature state after `beforeAnalysis`, repeated `duringAnalysis`, `afterAnalysis`, and
  `onAnalysisExit` callbacks;
- unsupported-feature diagnostics, reachability traces, and analysis reports;
- a decision to continue to hosted universe construction, return after analysis, or interrupt the
  build for [`ExitAfterAnalysis`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/NativeImageOptions.java).

On normal completion, reachability is fixed and the resulting metadata is ready for hosted-universe
construction.
`doRun(...)` keeps type-flow information until
[`UniverseBuilder.build(...)`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/meta/UniverseBuilder.java) transfers the required summaries, then calls `bb.cleanupAfterAnalysis()`.

## Main Classes

Core anchors:

- [`NativeImageGenerator`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/NativeImageGenerator.java)
  owns the static-analysis lifecycle through `setupNativeImage(...)`, `runPointsToAnalysis(...)`,
  `createAnalysisUniverse(...)`, `createBigBang(...)`, and `initializeBigBang(...)`.
- [`FeatureImpl`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/FeatureImpl.java) provides the public
  feature access objects for `beforeAnalysis`, `duringAnalysis`, `afterAnalysis`, and
  `onAnalysisExit`.

## Control Flow

The control flow for the normal Native Image build is:

1. `NativeImageGeneratorRunner.buildImage(...)` selects image kind, Java main support, and entry
   points, then calls [`NativeImageGenerator.run(...)`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/NativeImageGenerator.java).

2. [`NativeImageGenerator.run(...)`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/NativeImageGenerator.java) calls `doRun(...)` once for the generator. `doRun(...)` creates
   a [`DebugContext`](../../../compiler/src/jdk.graal.compiler/src/jdk/graal/compiler/debug/DebugContext.java), runs `setupNativeImage(...)`, then enters `runPointsToAnalysis(...)`.

3. `setupNativeImage(...)` prepares the analysis engine:
   - creates the target and installs core [`ImageSingletons`](../../../sdk/src/org.graalvm.nativeimage/src/org/graalvm/nativeimage/ImageSingletons.java);
   - registers features and runs `afterRegistration(...)`;
   - registers entry points and default configuration;
   - creates [`AnalysisUniverse`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/meta/AnalysisUniverse.java) through `createAnalysisUniverse(...)`;
   - creates [`SVMAnalysisMetaAccess`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/analysis/SVMAnalysisMetaAccess.java), analysis providers, and [`SVMHost`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/SVMHost.java);
   - creates the [`BigBang`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/BigBang.java) engine through `createBigBang(...)`;
   - creates [`SVMImageHeapScanner`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/heap/SVMImageHeapScanner.java) and [`SVMImageHeapVerifier`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/heap/SVMImageHeapVerifier.java);
   - registers already-created types as assignable and notifies reachable types;
   - verifies the C compiler when needed and creates [`NativeLibraries`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/c/NativeLibraries.java);
   - runs `duringSetup(...)`;
   - calls `initializeBigBang(...)`;
   - registers preserve-mode classes and entry-point stubs.

4. `createAnalysisUniverse(...)` selects the analysis policy and factory:
   - [`BytecodeSensitiveAnalysisPolicy`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/flow/context/bytecode/BytecodeSensitiveAnalysisPolicy.java) when allocation-site-sensitive heap analysis is enabled;
   - [`DefaultAnalysisPolicy`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/typestate/DefaultAnalysisPolicy.java) otherwise;
   - [`PointsToAnalysisFactory`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/meta/PointsToAnalysisFactory.java) for the normal engine;
   - [`ReachabilityAnalysisFactory`](../../src/com.oracle.graal.reachability/src/com/oracle/graal/reachability/ReachabilityAnalysisFactory.java) only when experimental reachability analysis is enabled.

5. `initializeBigBang(...)` installs analysis-specific roots and parsing support:
   - registers unsafe-accessed recomputed-value fields;
   - chains feature substitutions and native-method substitutions;
   - calls `registerRootElements(...)` for VM roots that must exist even without normal
     allocations;
   - registers graph-builder plugins for [`ParsingReason.PointsToAnalysis`](../../src/com.oracle.svm.core/src/com/oracle/svm/core/ParsingReason.java);
   - registers replacements and foreign calls;
   - analyzes snippet graphs through `performSnippetGraphAnalysis(...)`;
   - marks the analysis engine initialized.

6. `runPointsToAnalysis(...)` installs a [`ConcurrentAnalysisAccessImpl`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/FeatureImpl.java) before feature
   `beforeAnalysis(...)` callbacks so reachability callbacks triggered by pre-analysis work have a
   valid access object.

7. The pre-analysis block runs:
   - `beforeAnalysis(...)` on all features;
   - service-catalog map transformer setup and sealing;
   - well-known stable-field checks in [`SVMHost`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/SVMHost.java);
   - class-initialization configuration sealing;
   - image-layer singleton and external-value setup when building layers.

8. The fixed-point analysis block creates [`DuringAnalysisAccessImpl`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/FeatureImpl.java), marks analysis started, and
   calls `bb.runAnalysis(debug, analysisEndCondition)`.

9. [`AbstractAnalysisEngine.runAnalysis(...)`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/AbstractAnalysisEngine.java) is the outer fixed-point loop:
   - calls `finish()` on the concrete engine;
   - invokes the supplied end condition;
   - keeps looping if features request another iteration, if pending executor operations exist, or
     if heap verification modifies analysis state;
   - aborts if feature callbacks keep requesting iterations past the hard limit.

10. For the default engine, [`PointsToAnalysis.finish()`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/PointsToAnalysis.java) repeatedly:
    - starts the analysis executor and runs queued [`TypeFlow`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/flow/TypeFlow.java) and parsing work;
    - propagates type states through method, invoke, field, array, allocation, unsafe, and other
      flows;
    - runs `AnalysisUniverse.runAtFixedPoint()`;
    - repeats while new executor operations are posted.

    Method parsing creates type-flow graphs lazily. A [`MethodTypeFlow`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/flow/MethodTypeFlow.java) owns the graph for one
    [`PointsToAnalysisMethod`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/meta/PointsToAnalysisMethod.java). When an invoked method needs analysis,
    [`MethodTypeFlow.ensureFlowsGraphCreated(...)`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/flow/MethodTypeFlow.java) creates a [`MethodTypeFlowBuilder`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/flow/MethodTypeFlowBuilder.java), parses or
    reuses the method's analysis [`StructuredGraph`](../../../compiler/src/jdk.graal.compiler/src/jdk/graal/compiler/nodes/StructuredGraph.java), applies pre-analysis optimizations, registers
    directly used elements, and then builds the [`MethodFlowsGraph`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/flow/MethodFlowsGraph.java).

11. The `analysisEndCondition` inside `runPointsToAnalysis(...)` runs feature work after each
    engine iteration:
    - `SVMHost.notifyClassReachabilityListener(...)`;
    - `Feature.duringAnalysis(...)` for all features;
    - watchdog activity updates;
    - checks `DuringAnalysisAccess.requireAnalysisIteration()` and concurrent reachability
      requests.

12. If a feature makes new types, methods, or fields reachable but does not request another
    iteration, [`AbstractAnalysisEngine.runAnalysis(...)`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/AbstractAnalysisEngine.java) throws an analysis error. Manual rescans
    can post executor work without explicitly requesting an iteration; pending operations cause a
    new iteration.

13. When feature callbacks stop requesting work, heap verification runs during analysis. The
    verifier scans static roots and embedded constants. If the shadow heap scan observes new
    objects or fields and modifies analysis state, the outer loop continues.

14. After a fixed point:
    - `verifyAssignableTypes()` checks assignable type information unless disabled;
    - `nativeLibraries.processAnnotated()` adds libraries from `@CLibrary`;
    - [`BuildPhaseProviderImpl.markAnalysisFinished()`](../../src/com.oracle.svm.core/src/com/oracle/svm/core/BuildPhaseProviderImpl.java) records the phase transition;
    - `afterAnalysis(...)` runs on all features;
    - `checkUniverse()` validates naming conventions and invalid calls to native entry points;
    - unsupported features and user limitations are reported;
    - `bb.afterAnalysis()` runs engine-specific fixed-point validation.

15. The `finally` block always runs:
    - `onAnalysisExit(...)` on all features;
    - `AnalysisReporter.printAnalysisReports(...)`;
    - `ReachabilityTracePrinter.report(...)`.

16. The step returns to `doRun(...)`. If [`ReturnAfterAnalysis`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/NativeImageOptions.java) is enabled, the build stops
    normally after reports. If [`ExitAfterAnalysis`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/NativeImageOptions.java) is enabled, the build is interrupted. Otherwise
    `doRun(...)` enters hosted universe construction.

## Key Data Structures

A [`TypeFlow`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/flow/TypeFlow.java) graph is the points-to engine's data-flow model for values and memory locations. Each
[`TypeFlow`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/flow/TypeFlow.java) node has a [`TypeState`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/typestate/TypeState.java), usually representing the set of object types or allocation-site
objects that may flow through a program value. When primitive tracking is enabled, primitive flows
carry primitive type states as well. A flow normally represents either:

- a value in a method's parsed Graal [`StructuredGraph`](../../../compiler/src/jdk.graal.compiler/src/jdk/graal/compiler/nodes/StructuredGraph.java), such as a parameter, constant, allocation,
  invoke result, field load, array load, null check, type check, merge, or return;
- a memory or global analysis location, such as a static field flow, instance field flow, array
  elements flow, all-instantiated-subtypes flow, or all-synchronized flow.

The graph has three important edge kinds:

- use edges: value propagation edges. When one flow receives a larger [`TypeState`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/typestate/TypeState.java), it posts itself
  to the analysis executor and later propagates that state to its uses.
- observer edges: notification edges. They do not directly transfer a value state; they tell an
  observer that the observed flow changed. Invokes observe receiver flows so they can resolve and
  link callees. Instance field and array loads/stores observe receiver object flows so they can link
  to the field or elements flows of newly discovered receiver objects.
- predicate edges: reachability gating edges used when predicated analysis is enabled. A flow is
  enabled only when its predicate is enabled and becomes non-empty. This models path-sensitive
  reachability for branches, exception handlers, void returns, and other control-dependent flows.

Each flow moves monotonically through disabled, enabled, active, and saturated states. Disabled flows
can accumulate incoming state but do not propagate it. Enabled flows can run flow-specific setup.
Active flows have triggered outgoing predicate edges and propagate state to uses. Saturated flows no
longer keep precise individual type state after the configurable saturation cutoff is exceeded or an
input saturates. Saturation is a precision/performance tradeoff: the saturated flow notifies uses and
observers, unlinks itself lazily, and consumers that still need updates subscribe to broader flows
such as the declared type's all-instantiated flow.

A [`MethodFlowsGraph`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/flow/MethodFlowsGraph.java) is the per-method container. It stores:

- formal parameter flows and the formal return flow;
- flow nodes associated with encoded Graal graph nodes;
- invoke flows for call sites;
- miscellaneous entry flows such as constants, allocations, global proxies, merge flows, and field
  or array access helper flows.

Method graphs can be
[`STUB`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/flow/MethodFlowsGraph.java)
or
[`FULL`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/flow/MethodFlowsGraph.java).
A stub graph contains only parameter and return flows and can be used as a placeholder. A full graph
contains the method's internal flows. During analysis a stub can be upgraded to a full graph; the new
graph must be a superset of the earlier graph. Once a graph is sealed for consumers, later
replacement is rejected.

[`MethodTypeFlowBuilder`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/flow/MethodTypeFlowBuilder.java) constructs the method graph from a parsed and pre-optimized Graal
[`StructuredGraph`](../../../compiler/src/jdk.graal.compiler/src/jdk/graal/compiler/nodes/StructuredGraph.java). It keeps a temporary [`TypeFlowsOfNodes`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/flow/MethodTypeFlowBuilder.java) map from Graal nodes to
[`TypeFlowBuilder`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/flow/builder/TypeFlowBuilder.java) objects while a [`NodeIterator`](../../../compiler/src/jdk.graal.compiler/src/jdk/graal/compiler/graph/iterators/NodeIterator.java) walks fixed nodes. Representative mappings are:

- [`ParameterNode`](../../../compiler/src/jdk.graal.compiler/src/jdk/graal/compiler/nodes/ParameterNode.java) to [`FormalParamTypeFlow`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/flow/FormalParamTypeFlow.java) / [`FormalReceiverTypeFlow`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/flow/FormalReceiverTypeFlow.java);
- [`ReturnNode`](../../../compiler/src/jdk.graal.compiler/src/jdk/graal/compiler/nodes/ReturnNode.java) to [`FormalReturnTypeFlow`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/flow/FormalReturnTypeFlow.java);
- [`NewInstanceNode`](../../../compiler/src/jdk.graal.compiler/src/jdk/graal/compiler/nodes/java/NewInstanceNode.java), array allocation nodes, and virtual-object commits to [`NewInstanceTypeFlow`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/flow/NewInstanceTypeFlow.java)
  plus field or array store flows for initialized contents;
- invokes and macro-invokable nodes to [`InvokeTypeFlow`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/flow/InvokeTypeFlow.java), actual-parameter flows, and
  [`ActualReturnTypeFlow`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/flow/ActualReturnTypeFlow.java);
- field loads/stores to `Load*FieldTypeFlow`, `Store*FieldTypeFlow`, and [`FieldTypeFlow`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/flow/FieldTypeFlow.java);
- array loads/stores and array copies to indexed load/store/copy flows and array elements flows;
- unsafe memory operations to precise field/array flows when the offset is known, otherwise to
  unsafe load/store flows;
- control and value merges to [`MergeTypeFlow`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/flow/MergeTypeFlow.java), predicate merges, anchors, filters, and null/type
  check flows.

The builder first creates a graph of [`TypeFlowBuilder`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/flow/builder/TypeFlowBuilder.java) nodes rather than immediately retaining every
possible flow. [`TypeFlowGraphBuilder`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/flow/builder/TypeFlowGraphBuilder.java) then starts from registered data-flow sinks and walks backward
through builder dependencies. Sinks include actual parameters, invokes, returns that are used, field
stores, indexed loads, array copies, and other flows whose effects are needed even if no later value
uses them. This materialization step prunes flows that cannot affect analysis results, converts
builder dependencies into use and observer edges, attaches predicate edges, enables always-live
flows, and returns flows that need post-materialization initialization.

After materialization, [`MethodTypeFlow.initFlowsGraph(...)`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/flow/MethodTypeFlow.java) initializes delayed flows. This is when
some flows install links that require the complete graph to exist, for example static stores linking
to field flows, unsafe access flows, or invoke return links. During fixed-point processing,
`PointsToAnalysis.postFlow(...)` queues active flows on the analysis executor; [`TypeFlow.update(...)`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/flow/TypeFlow.java)
propagates the current state to use edges and calls observer update hooks.

Interprocedural analysis is expressed through invoke flows. Actual parameter flows in the caller are
linked to formal parameter flows in each resolved callee. Virtual and special invokes observe their
receiver flow, resolve callees as receiver types become possible, create or update callee method
graphs, and link callee return flows back to the caller's actual return flow. The analysis policy
controls whether method graphs are context-insensitive or cloned for context-sensitive variants.

The main structures are:

- [`AnalysisUniverse`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/meta/AnalysisUniverse.java): owns the analysis-time maps from original JVMCI elements to
  [`AnalysisType`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/meta/AnalysisType.java), [`AnalysisMethod`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/meta/AnalysisMethod.java), [`AnalysisField`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/meta/AnalysisField.java), signatures, constant pools, substitutions,
  embedded roots, feature substitution chains, heap scanner, heap verifier, image-layer loader or
  writer, and concurrent analysis access.
- [`BigBang`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/BigBang.java) / [`Inflation`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/analysis/Inflation.java): the mutable analysis engine interface used by features, heap scanning,
  graph parsing, and Native Image setup.
- [`NativeImagePointsToAnalysis`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/analysis/NativeImagePointsToAnalysis.java): Native Image's default points-to analysis engine. It adds SVM
  call checking, dynamic-hub metadata initialization, custom field-type handling, user limitation
  checks, and Native Image-specific method-flow builders.
- [`AnalysisPolicy`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/AnalysisPolicy.java): controls context sensitivity and type-state behavior. Hosted setup currently
  chooses [`DefaultAnalysisPolicy`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/typestate/DefaultAnalysisPolicy.java) or [`BytecodeSensitiveAnalysisPolicy`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/flow/context/bytecode/BytecodeSensitiveAnalysisPolicy.java).
- [`AnalysisType`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/meta/AnalysisType.java), [`AnalysisMethod`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/meta/AnalysisMethod.java), [`AnalysisField`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/meta/AnalysisField.java): analysis wrappers that store reachability,
  instantiated state, invocation state, field access state, callbacks, identifiers, and cleanup
  state.
- [`MethodFlowsGraph`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/flow/MethodFlowsGraph.java), [`MethodTypeFlow`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/flow/MethodTypeFlow.java), and [`TypeFlow`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/flow/TypeFlow.java): graph representation used to propagate
  object and primitive type states through parsed bytecode, snippets, method calls, fields, arrays,
  unsafe accesses, and selected control dependencies.
- [`TypeFlowBuilder`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/flow/builder/TypeFlowBuilder.java) and [`TypeFlowGraphBuilder`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/flow/builder/TypeFlowGraphBuilder.java): temporary graph-building layer that maps parsed
  Graal nodes to flow builders, records backward dependencies from analysis sinks, prunes unused
  flows, and materializes use, observer, and predicate edges.
- [`CompletionExecutor`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/util/CompletionExecutor.java): work queue used by the engine for method parsing, flow updates,
  reachability notifications, and heap scanning work.
- [`ImageHeapScanner`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/heap/ImageHeapScanner.java) / [`SVMImageHeapScanner`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/heap/SVMImageHeapScanner.java): converts hosted constants and objects into shadow
  image-heap constants, applies object replacers, patches fields and arrays, and notifies the
  analysis observer.
- [`HeapSnapshotVerifier`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/heap/HeapSnapshotVerifier.java) / [`SVMImageHeapVerifier`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/heap/SVMImageHeapVerifier.java): rescans from roots and embedded constants to
  verify and update the shadow heap during analysis.
- [`SubstrateUnsupportedFeatures`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/analysis/SubstrateUnsupportedFeatures.java): accumulates unsupported-feature messages with reachability
  context and reports them as build errors at defined phase boundaries.
- [`NativeLibraries`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/c/NativeLibraries.java): tracks native declarations, C constants, C functions, and libraries that
  analysis and image writing need.
- [`BuildPhaseProvider`](../../src/com.oracle.svm.shared/src/com/oracle/svm/shared/BuildPhaseProvider.java): exposes phase state such as setup finished, analysis started, and analysis
  finished to features and validation code.
