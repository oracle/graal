# Optimization log

The class `OptimizationLog` presents a unified interface that logs graph transformations performed in optimization
phases.

Each optimization should be reported just after the transformation using the `OptimizationLog` instance bound to the
transformed `StructeredGraph` (i.e. `StructuredGraph#getOptimizationLog`). Use the
method `report(Class<?> optimizationClass, String eventName, Node node)`, which accepts the following arguments:

- the class that performed the transformation, preferably the optimization phase like `CanonicalizerPhase`,
- a string in `PascalCase` that describes the transformation well in the context of the class, e.g. `CfgSimplification`,
- the most relevant node in the transformation, i.e., a node that was just replaced/deleted/modified or
  a `LoopBeginNode` in the context of loop transformations like unrolling.

The node is used to obtain the position of the transformation. The position is characterized by the byte code index (
bci). However, in the presence of inlining, we need to collect the bci of each method in the inlined stack of methods.

The `report` method handles the following use cases:

| Concern                         | Option                    | Output                                                                       |
|---------------------------------|---------------------------|------------------------------------------------------------------------------|
| `log` using a `DebugContext`    | `-Dgraal.Log`             | log `Performed {optimizationName} {eventName} at bci {bci} {properties}`     |
| `dump` using a `DebugContext`   | `-Dgraal.Dump`            | dump with caption `{optimizationName} {eventName} for node {nodeName}`       |
| `CounterKey` increment          | `-Dgraal.Count`           | increment the counter `{optimizationName}_{eventName}`                       |
| structured optimization logging | `-Dgraal.OptimizationLog` | optimization tree dumped to the standard output, a JSON file or an IGV graph |

The method logs and dumps at `DETAILED_LEVEL` by default. There is a variant of the method which allows the log level to
be specified as the first argument.

It suffices to insert a line like the one below (from `DeadCodeEliminationPhase`) to solve all of the above concerns.
The `report` method creates an *optimization entry*.

```java
graph.getOptimizationLog().report(DeadCodeEliminationPhase.class, "NodeRemoved", node);
```

## Compiler options

Structured optimization logging is enabled by the `-Dgraal.OptimizationLog` option. It is recommended to enable the
option jointly with node source position tracking (`-Dgraal.TrackNodeSourcePosition`) so that the bytecode position of
nodes can be logged. Otherwise, a warning is emitted.

Similarly, the options `-H:OptimizationLog` and `-H:OptimizationLogPath` can be used with `native-image`.

The value of the option `-Dgraal.OptimizationLog` specifies where the structured optimization log is printed.
The accepted values are:

- `Directory` - format the structured optimization as JSON and print it to files a directory. The directory
  is specified by the option `-Dgraal.OptimizationLogPath`. If `OptimizationLogPath` is not set, the target directory is
  `DumpPath/optimization_log` (specified by `-Dgraal.DumpPath`). Directories are created if they do not exist.
- `Stdout` - print the structured optimization log to the standard output.
- `Dump` - dump optimization trees for IdealGraphVisualizer according to the `-Dgraal.PrintGraph` option.

It is possible to specify multiple comma-separated values (e.g., `-Dgraal.OptimizationLog=Stdout,Dump`).

It is best to inspect the generated files using `mx profdiff`. Read `Profdiff.md` for more information.

## Properties

It is possible to provide additional key/value properties that are logged to the structured optimization log and in the
regular log. Consider the example from `LoopTransformations#peel`.

```java
loop.loopBegin().graph().getOptimizationLog()
        .withProperty("peelings", loop.loopBegin().peelings())
        .report(LoopTransformations.class, "LoopPeeling", loop.loopBegin());
```

The `withProperty` and `withLazyProperty` methods return an optimization entry that holds the provided named properties.
The returned optimization entry can be further extended with more properties, and its `report` method should be called
afterwards. The value of the property can be any `String`-convertible object. Property keys should be in `camelCase`.

If the computation of the value is costly, use the `withLazyProperty` method, which accepts a `Supplier<Object>`
instead. If logging is enabled, the supplier is evaluated immediately. Otherwise, it is never evaluated.

```java
graph.getOptimizationLog().withLazyProperty("replacedNodeClass", nodeClass::shortName)
        .withLazyProperty("canonicalNodeClass", () -> (finalCanonical == null) ? null : finalCanonical.getNodeClass().shortName())
        .report(DebugContext.VERY_DETAILED_LEVEL, CanonicalizerPhase.class, "CanonicalReplacement", node);
```

## Optimization tree

The context of the optimizations is also collected when `-Dgraal.OptimizationLog` is enabled. This is achieved by
setting the graph's `OptimizationLog` as the `CompilationListener`. We establish parent-child relationships between
optimization phases and optimization entries. The result is a tree of optimizations.

- We create an artificial `optimizationTree`, which is the root.
- When a phase is entered (`CompilationListener#enterPhase`), the new phase is a child of the phase that entered this
  phase.
- When an optimization is logged via the `report` method, it is attributed to its parent phase.

The ASCII art below is a snippet of an optimization tree.

```
                                  RootPhase
                    _____________/    |    \_____________
                   /                  |                  \
                LowTier            MidTier            HighTier
           ______/  \___              |                   |
          /             \     CanonicalizerPhase         ...
  LoopPeelingPhase      ...     ___|     |__________
          |                    /                    \
     LoopPeeling       CfgSimplification      CanonicalReplacement
      at bci 122           at bci 13                at bci 25
                                         {replacedNodeClass: ValuePhi,
                                          canonicalNodeClass: Constant}
```

In reality, however, the trees are significantly larger than in this example. Read `Profdiff.md` to learn how to inspect
a real tree. The sections below explain the format of a serialized optimization tree, and how to view the tree in IGV.

## Inlining tree

`-Dgraal.OptimizationLog` also collects inlining trees. The inlining tree represents the call tree of methods considered
for inlining in a compilation. The root of the tree is the root-compiled method. Each node of the tree corresponds to
one method, which may have been inlined or not. We store the result of the decision (i.e., inlined or not) and also the
reason for this decision. There may be several negative decisions until a method is finally inlined. The children of a
node are the methods invoked in the method which were considered for inlining. The bci of the callsite is also stored
for each method in the tree.

## Example: optimization log of a benchmark

Run a benchmark with the flag `-Dgraal.OptimizationLog=Directory` to produce an output and save it to the directory
specified by the `-Dgraal.OptimizationLogPath` option. Run it jointly with `-Dgraal.TrackNodeSourcePosition=true`, so
that optimizations can be linked with a source position.

```sh
mx benchmark renaissance:scrabble -- -Dgraal.TrackNodeSourcePosition=true -Dgraal.OptimizationLog=Directory \
  -Dgraal.OptimizationLogPath=$(pwd)/optimization_log
```

An equivalent set of commands for `native-image` is:

```sh
cd ../vm
mx --env ni-ce build
mx --env ni-ce benchmark renaissance-native-image:scrabble -- --jvm=native-image --jvm-config=default-ce \
  -Dnative-image.benchmark.extra-image-build-argument=-H:OptimizationLog=Directory \
  -Dnative-image.benchmark.extra-image-build-argument=-H:OptimizationLogPath=$(pwd)/optimization_log
```

Now, we can use `mx profdiff` to explore the compilation units in a human-friendly format.

```sh
mx profdiff report optimization_log
```

Read `Profdiff.md` to learn how to use `profdiff` to view frequently-executed methods or compare two experiments.

### Structure of the generated files

In the `optimization_log` directory, we can find many files with numeric names (named after compilation thread IDs).
Each file contains several compilation units. Each line is a JSON-encoded compilation unit. The structure of one
compilation unit, after formatting, is the following:

```json
{
  "methodName": "java.lang.String.hashCode()",
  "compilationId": "17697",
  "inliningTree": {
    "methodName": "java.lang.String.hashCode()",
    "callsiteBci": -1,
    "inlined": true,
    "reason": null,
    "invokes": [
      {
        "methodName": "java.lang.String.isLatin1()",
        "callsiteBci": 17,
        "inlined": true,
        "reason": [
          "bytecode parser did not replace invoke",
          "trivial (relevance=1.000000, probability=0.618846, bonus=1.000000, nodes=9)"
        ],
        "invokes": null
      },
      {
        "methodName": "java.lang.StringLatin1.hashCode(byte[])",
        "callsiteBci": 27,
        "inlined": true,
        "reason": [
          "bytecode parser did not replace invoke",
          "relevance-based (relevance=1.000000, probability=0.618846, bonus=1.000000, nodes=27 <= 300.000000)"
        ],
        "invokes": null
      }
    ]
  },
  "optimizationTree": {
    "phaseName": "RootPhase",
    "optimizations": [
      ...
    ]
  }
}
```

The `methodName` is the name of the root method in the compilation unit. `compilationId` is a unique identifier of the
compilation unit.

`inliningTree` contains the root of the inlining tree, i.e, the name of the root method matches `methodName`.
`invokes` are the invoked methods which were considered for inlining. The final result of the inlining decisions is
reflected by the `inlined` property. Its value equals `true` if the method was inlined, otherwise it is `false`. The
reasons for the decisions, in their original order, are listed in the `reason` property. Finally, `callsiteBci` is the
byte code index of the invoke node in the callsite.

`optimizationTree` contains the root of the optimization tree. Each node in the optimization tree is either:

- a phase node, which contains a `phaseName` derived from the class name and a list of children (phases and optimization
  entries),
- or an optimization entry node, containing `optimizationName`, `eventName` (from the arguments of `report`), `position`
  (from the `Node` passed to `report`) and additional properties.

Consider the following example with an `IncrementalCanonicalizerPhase` that performed a `CanonicalReplacement` and
a `CfgSimplification`:

```json
{
  "phaseName": "IncrementalCanonicalizerPhase",
  "optimizations": [
    {
      "optimizationName": "Canonicalizer",
      "eventName": "CanonicalReplacement",
      "replacedNodeClass": "==",
      "canonicalNodeClass": "LogicNegation",
      "position": {
        "java.lang.String.hashCode()": 20
      }
    },
    {
      "optimizationName": "Canonicalizer",
      "eventName": "CfgSimplification",
      "position": {
        "java.lang.String.hashCode()": 20
      }
    }
  ]
}
```

We can see that both the replacement and the simplification occurred at bci 20 in the root `java.lang.String.hashCode()`
method. In the presence of inlining, the positions are more complex. Consider the example below.

```json
{
  "phaseName": "DeadCodeEliminationPhase",
  "optimizations": [
    {
      "optimizationName": "DeadCodeElimination",
      "eventName": "NodeRemoval",
      "position": {
        "java.lang.StringLatin1.hashCode(byte[])": 10,
        "java.lang.String.hashCode()": 27
      }
    }
  ]
}
```

We can see that the `NodeRemoval` occurred at bci 10 in the inlined `java.lang.StringLatin1.hashCode(byte[])` method,
which callsite is at bci 27 in the root method. Note that the order of keys is important in this case.

## IGV output

Optimization trees can be printed to IdealGraphVisualizer. First, start an IGV instance. After that, run a benchmark
with the flag `-Dgraal.OptimizationLog=Dump`. Run it jointly with `-Dgraal.TrackNodeSourcePosition=true`, so
that optimizations can be linked with a source position.

```sh
mx benchmark renaissance:scrabble -- -Dgraal.TrackNodeSourcePosition=true -Dgraal.OptimizationLog=Dump \
  -Dgraal.PrintGraph=Network
```

Optimization trees for each compilation should now be available in IGV.

## Overhead of optimization logging

Enabling `-Dgraal.OptimizationLog` as well as `-Dgraal.TrackNodeSourcePosition` comes with an overhead. It may slow the
compilation down in terms of CPU time and the logs may generate hundreds of MB of many small files. Depending on the
workload, node source positions can decrease the compile speed (measured in bytes/sec) by up to 15%. Optimization log
with node source positions can decrease the speed by up to 25%.
