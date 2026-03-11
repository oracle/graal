# AGENTS.md — Points-to core (`com.oracle.graal.pointsto`)

Scope:

- `src/com/oracle/graal/pointsto/**` in this project

This is the base guidance for points-to internals in open-source Graal.

Primary code anchors outside this package that drive points-to lifecycle:

- `substratevm/src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/NativeImageGenerator.java`
- `substratevm/src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/analysis/NativeImagePointsToAnalysis.java`
- `substratevm/src/com.oracle.graal.pointsto.standalone/src/com/oracle/graal/pointsto/standalone/PointsToAnalyzer.java`

## Mental model

Native Image points-to is a closed-world, whole-program static analysis.

Core loop:

1. Build universe (types/methods/fields/policy).
2. Seed roots.
3. Build method flow graphs lazily.
4. Propagate `TypeState` through `TypeFlow` until fixpoint.
5. Run fixed-point side tasks and post-analysis hooks.

Fixed-point mechanics are centered in `PointsToAnalysis.finish()` / `doTypeflow()`:

- `postFlow(...)` queues updates
- executor drains flow work
- `universe.runAtFixedPoint()` runs side tasks
- iterate until no new work remains

## Entry anchors

- `PointsToAnalysis`
- `AnalysisPolicy`
- `PointstoOptions`
- `AnalysisUniverse`, `AnalysisType`, `AnalysisMethod`, `AnalysisField`
- `TypeFlow`, `MethodTypeFlow`, `InvokeTypeFlow`

Additional frequently used classes:

- `BigBang`
- `ReachabilityAnalysis`
- `MethodFlowsGraph`, `MethodTypeFlowBuilder`
- `TypeState` (`SingleTypeState`, `MultiTypeState*`, `ConstantTypeState`)
- `AnalysisObject`
- `FieldTypeStore`, `ArrayElementsTypeStore`

Hosted wiring entry points:

- `NativeImageGenerator.createAnalysisUniverse(...)`
- `NativeImageGenerator.registerRootElements(...)`
- `NativeImagePointsToAnalysis`

Standalone entry points:

- `PointsToAnalyzer`
- `StandalonePointsToAnalysis`

Standalone-driver AGENTS map:

- `substratevm/src/com.oracle.graal.pointsto.standalone/AGENTS.md`

Outer lifecycle anchor:

- `AbstractAnalysisEngine.runAnalysis(...)` / `runPointsToAnalysis()` (macro-iteration loop around inner points-to
  fixpoint)

Execution flow (practical):

1. Universe/policy setup
2. BigBang initialization + root registration
3. Reachability expansion
4. On-demand method graph creation
5. Flow propagation
6. Fixpoint completion
7. Post-analysis hooks + metadata completion

## High-impact options

- `AllocationSiteSensitiveHeap`
- `RemoveSaturatedTypeFlows`
- `TypeFlowSaturationCutoff`
- `UsePredicates`
- `TrackPrimitiveValues`

Important interaction:

- Non-`insens` `AnalysisContextSensitivity` disables `UsePredicates` and `TrackPrimitiveValues`.

Additional high-impact options to watch:

- `MaxHeapContextDepth`
- `MaxObjectSetSize`
- `AliasArrayTypeFlows`
- `RelaxTypeFlowStateConstraints`

## Practical cautions

- Changes in `AnalysisPolicy` are global and can affect reachability + heap modeling.
- Saturation/predicate settings materially impact precision vs. build time.

## Hosted integration anchors (`com.oracle.svm.hosted`)

For hosted-side analysis wiring, use these integration entry points:

- `NativeImageGenerator.createAnalysisUniverse(...)`
- `NativeImageGenerator.registerRootElements(...)`
- `NativeImagePointsToAnalysis`

Key hosted hooks in `NativeImagePointsToAnalysis`:

- metadata init (`DynamicHubInitializer`)
- field handling (`CustomTypeFieldHandler`)
- call legality (`CallChecker`)
- shared-layer handling (field registration/rescan)

Engine-selection caveat:

- `PointstoOptions.UseExperimentalReachabilityAnalysis` can route hosted analysis through the experimental reachability
  path.

Hosted local validation (run from `substratevm`):

1. `mx unittest com.oracle.graal.pointsto.standalone.test`
2. `mx helloworld`

## Source map (paths relative to repository root)

- NativeImage points-to engine:
    - `substratevm/src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/analysis/NativeImagePointsToAnalysis.java`
- Universe/policy setup:
    - `substratevm/src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/NativeImageGenerator.java`
- Core package root:
    - `substratevm/src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/`
- Core engine:
    - `substratevm/src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/PointsToAnalysis.java`
- BigBang API:
    - `substratevm/src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/BigBang.java`
- Policy interface:
    - `substratevm/src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/AnalysisPolicy.java`
- Default policy:
    - `substratevm/src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/typestate/DefaultAnalysisPolicy.java`
- Context-sensitive policy:
    -
    `substratevm/src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/flow/context/bytecode/BytecodeSensitiveAnalysisPolicy.java`
- Standalone analyzer:
    -
    `substratevm/src/com.oracle.graal.pointsto.standalone/src/com/oracle/graal/pointsto/standalone/PointsToAnalyzer.java`

## Research references

- Comparing Rapid Type Analysis with Points-To Analysis in GraalVM Native Image (MPLR 2023)
    - https://doi.org/10.1145/3617651.3622980
- Scaling Type-Based Points-to Analysis with Saturation (PLDI 2024)
    - https://dl.acm.org/doi/10.1145/3656417
- SkipFlow: Improving the Precision of Points-to Analysis using Primitive Values and Predicate Edges (CGO 2025)
    - https://doi.org/10.1145/3696443.3708932
