# Optimization log

The class `OptimizationLog` presents a unified interface that logs graph transformations performed in optimization
phases.

Each optimization should be reported just after the transformation using the `OptimizationLog` instance bound to the
transformed `StructeredGraph` (i.e. `StructuredGraph#getOptimizationLog`). Use the
method `report(Class<?> optimizationClass, String eventName, Node node)`, which accepts the following arguments:
- the class that performed the transformation, preferably the optimization phase like `CanonicalizerPhase`
- a string in `PascalCase` that describes the transformation well in the context of the class, e.g. `CfgSimplification`
- the most relevant node in the transformation, i.e., a node that was just replaced/deleted/modified or
  a `LoopBeginNode` in the context of loop transformations like unrolling

The node is used to obtain the position of the transformation. The position is characterized by the byte code index (
bci). However, in the presence of inlining, we need to collect the bci of each method in the inlined stack of methods.

The `report` method handles the following use cases:

| Concern                              | Option                    | Output                                                                           |
|--------------------------------------|---------------------------|----------------------------------------------------------------------------------|
| `log` using a `DebugContext`         | `-Dgraal.Log`             | log the message `Performed {optimizationName} {eventName} at bci {bci}`          |
| `dump` using a `DebugContext`        | `-Dgraal.Dump`            | dump the graph with the caption `After {optimizationName} {eventName}`           |
| `CounterKey` increment               | `-Dgraal.Count`           | increment the counter `{optimizationName}_{eventName}`                           |
| structured optimization logging      | `-Dgraal.OptimizationLog` | tree of optimizations dumped to the standard output, a JSON file or an IGV graph |

The method logs and dumps at `DETAILED_LEVEL` by default. There is a variant of the method which allows the log level to
be specified as the first argument.

It suffices to insert a line like the one below (from `DeadCodeEliminationPhase`) to solve all of the above concerns.
The `report` method creates an *optimization entry*.

```java
graph.getOptimizationLog().report(DeadCodeEliminationPhase.class,"NodeRemoved",node);
```

It is recommended to enable the structured optimization jointly with node source position
tracking (`-Dgraal.TrackNodeSourcePosition`) so that the bytecode position of nodes can be logged. Otherwise, a warning
is emitted.

The value of the option `-Dgraal.OptimizationLog` specifies where the structured optimization log is printed.
The accepted values are:

- `Directory` - print the structured optimization log to JSON files (`<compile-id>.json`) in a directory. The directory
  is specified by the option `-Dgraal.OptimizationLogPath`. If `OptimizationLogPath` is not set,
  the target directory is `DumpPath/optimization_log` (specified by `-Dgraal.DumpPath`). Directories are created if they
  do not exist.
- `Stdout` - print the structured optimization log to the standard output.
- `Dump` - dump optimization trees for IdealGraphVisualizer according to the `-Dgraal.PrintGraph` option.

It is possible to specify multiple comma-separated values (e.g., `-Dgraal.OptimizationLog=Stdout,Dump`).

## Properties

It is possible to provide additional key/value properties that are logged to the structured **optimization log only**.
Consider the example from `LoopTransformations#peel`.

```java
loop.loopBegin().graph().getOptimizationLog()
                .report(LoopTransformations.class, "LoopPeeling", loop.loopBegin())
                .setProperty("peelings", loop.loopBegin().peelings());
```

The `report` method returns an optimization entry that can be used to extend the optimization entry with any `String`
-convertible object identified by a `String` key. The property keys should be in `camelCase`. If the optimization
log (`-Dgraal.OptimizationLog`) is disabled, the method is a nop.

If the computation of the value is costly, use the `setLazyProperty` method, which accepts a `Supplier<Object>` instead
of a direct value.

```java
graph.getOptimizationLog().report(CanonicalizerPhase.class, "CanonicalReplacement", node)
                                    .setLazyProperty("replacedNodeClass", nodeClass::shortName)
                                    .setLazyProperty("canonicalNodeClass", () -> {
                                        if (finalCanonical == null) {
                                            return null;
                                        }
                                        return finalCanonical.getNodeClass().shortName();
                                    });
```

If `-Dgraal.OptimizationLog` is enabled, the supplier is never evaluated. Otherwise, it is evaluated immediately.

## Optimization tree

The context of the optimizations is also collected when `-Dgraal.OptimizationLog` is enabled. This is achieved by
setting the graph's `OptimizationLog` as the `CompilationListener`. We establish parent-child relationships between
optimization phases and optimization entries. The result is a tree of optimizations.

- we create an artificial `RootPhase`, which is the root
- when a phase is entered (`CompilationListener#enterPhase`), the new phase is a child of the phase that entered this phase
- when an optimization is logged via the `report` method, it is attributed to its parent phase

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

In reality, however, the trees are significantly larger than in this example. Read the sections below to learn how to
format the tree as JSON or view it in IGV.

## JSON output

Run a benchmark with the flag `-Dgraal.OptimizationLog=Directory` to produce an output and save it to the directory
specified by the `-Dgraal.OptimizationLogPath` option. It is a good idea to run it jointly
with `-Dgraal.TrackNodeSourcePosition=true`.

```sh
mx benchmark renaissance:scrabble -- -Dgraal.TrackNodeSourcePosition=true -Dgraal.OptimizationLog=Directory -Dgraal.OptimizationLogPath=$(pwd)/optimization_log
```

In the `optimization_log` directory, we can find many files named `<compilation-id>.json`. Each of them corresponds
to one compilation. The structure is the following:

```json
{
  "compilationMethodName": "java.lang.String.hashCode()",
  "compilationId": "17697",
  "rootPhase": {
    "phaseName": "RootPhase",
    "optimizations": [
      ...
    ]
  }
}
```

The `compilationMethodName` can be used to match several compilations of one compilation unit. `rootPhase` contains the
root of the optimization tree. Each node in the optimization tree is either:

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

First, start an IdealGraphVisualizer instance. After that, run a benchmark with the flag `-Dgraal.OptimizationLog=Dump`.
It is a good idea to run it jointly with `-Dgraal.TrackNodeSourcePosition=true`.

```sh
mx benchmark renaissance:scrabble -- -Dgraal.TrackNodeSourcePosition=true -Dgraal.OptimizationLog=Dump -Dgraal.PrintGraph=Network
```

Optimization trees for each compilation should now be available in IGV.

## Overhead of optimization logging

Enabling `-Dgraal.OptimizationLog` as well as `-Dgraal.TrackNodeSourcePosition` comes with an overhead. It may slow the
compilation down in terms of CPU time and the logs may generate hundreds of MB of many small files. Depending on the
workload, node source positions can decrease the compile speed (measured in bytes/sec) by up to 15%. Optimization log
with node source positions can decrease the speed by up to 25%.
