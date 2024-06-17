# Optimization log

The class `OptimizationLog` presents a unified interface that logs graph transformations performed in optimization
phases.

Each optimization should be reported just after the transformation using the `OptimizationLog` instance bound to the
transformed `StructeredGraph` (i.e. `StructuredGraph#getOptimizationLog`). Use the
method `report(Class<?> optimizationClass, String eventName, Node node)`, which accepts the following arguments:

- the class that performed the transformation, preferably the optimization phase like `CanonicalizerPhase.class`,
- a string in `PascalCase` that describes the transformation well in the context of the class, e.g. `CfgSimplification`,
- the most relevant node in the transformation, i.e., a node that was just replaced/deleted/modified or
  the `LoopBeginNode` in the context of loop transformations like unrolling.

The node is used to obtain the position of the transformation. The position is characterized by the byte code index (
bci). However, in the presence of inlining, we need to collect the bci of each method in the inlined stack of methods.

The `report` method handles the following use cases:

| Concern                          | Option                        | Output                                                                   |
|----------------------------------|-------------------------------|--------------------------------------------------------------------------|
| `log` using a `DebugContext`     | `-Djdk.graal.Log`             | log `Performed {optimizationName} {eventName} at bci {bci} {properties}` |
| `dump` using a `DebugContext`    | `-Djdk.graal.Dump`            | dump with caption `{optimizationName} {eventName} for node {nodeName}`   |
| increment a `CounterKey`         | `-Djdk.graal.Count`           | increment the counter `Optimization_{optimizationName}_{eventName}`      |
| structured optimization logging  | `-Djdk.graal.OptimizationLog` | optimization info dumped to stdout, a JSON file or an IGV graph          |

The method logs and dumps at `DETAILED_LEVEL` by default. There is a variant of the method which allows the log level to
be specified as the first argument.

It suffices to insert a line like the one below (from `DeadCodeEliminationPhase`) to handle all the above concerns.

```java
graph.getOptimizationLog().report(DeadCodeEliminationPhase.class, "NodeRemoved", node);
```

## Command-line options

Structured optimization logging is enabled by the `-Djdk.graal.OptimizationLog` option. It is recommended to enable the
option jointly with node source position tracking (`-Djdk.graal.TrackNodeSourcePosition`) so that the bytecode position of
nodes can be logged. Otherwise, a warning is emitted.

Similarly, the equivalent options `-H:OptimizationLog` and `-H:OptimizationLogPath` can be used with Native Image.

The value of the option `-Djdk.graal.OptimizationLog` specifies where the structured optimization log is printed.
The accepted values are:

- `Directory` - format the structured optimization as JSON and print it to files in a directory. The directory
  is specified by the option `-Djdk.graal.OptimizationLogPath`. If `OptimizationLogPath` is not set, the target directory is
  `DumpPath/optimization_log` (specified by `-Djdk.graal.DumpPath`). Directories are created if they do not exist.
- `Stdout` - print the structured optimization log to the standard output.
- `Dump` - dump optimization trees for IdealGraphVisualizer according to the `-Djdk.graal.PrintGraph` option.

Multiple targets can be specified together by separating them with a comma, e.g., `-Djdk.graal.OptimizationLog=Stdout,Dump`.
The generated files are human-readable but verbose. Therefore, it is best to inspect them with `mx profdiff`. Read
`Profdiff.md` for more information.

## Additional key/value properties

It is possible to provide additional key/value properties to the `report` method. The provided properties are included
both in the optimization log and in the regular log messages (`-Djdk.graal.Log`). Consider the example
from `LoopTransformations#peel`.

```java
loop.loopBegin().graph().getOptimizationLog()
        .withProperty("peelings", loop.loopBegin().peelings())
        .report(LoopTransformations.class, "LoopPeeling", loop.loopBegin());
```

The `withProperty` and `withLazyProperty` methods return an optimization entry that holds the provided named properties.
The returned optimization entry can be further extended with more properties, and its `report` method should be called
afterward. The value of the property can be any `String`-convertible object. Property keys should be in `camelCase`.

If the computation of the value is costly, use the `withLazyProperty` method, which accepts a `Supplier<Object>`
instead. If logging is enabled, the supplier is evaluated immediately. Otherwise, it is never evaluated.

```java
graph.getOptimizationLog().withLazyProperty("replacedNodeClass", nodeClass::shortName)
        .withLazyProperty("canonicalNodeClass", () -> (finalCanonical == null) ? null : finalCanonical.getNodeClass().shortName())
        .report(DebugContext.VERY_DETAILED_LEVEL, CanonicalizerPhase.class, "CanonicalReplacement", node);
```

## Optimization tree

The context of optimizations is also collected when `-Djdk.graal.OptimizationLog` is enabled. This is achieved by
notifying the graph's `OptimizationLog` whenever an optimization phase is entered or exited. We establish parent-child
relationships between the optimization phases and the optimizations. The result is an optimization tree.

We create an artificial `RootPhase`, which is the root of the tree and initially is the *current phase*. When a phase is
entered, the new phase is a child of the current phase and after that the current phase is set to the newly-entered
phase. When an optimization is logged via the `report` method, it is attributed to the current phase. When a phase is
exited, the current phase is updated to the parent of the just exited phase.

The diagram below is a snippet of an optimization tree.

```
                                  RootPhase
                    _____________/    |    \_____________
                   /                  |                  \
               HighTier            MidTier             LowTier
           ______/  \___              |                   |
          /             \     CanonicalizerPhase         ...
  LoopPeelingPhase      ...     ___|     |__________
          |                    /                    \
     LoopPeeling       CfgSimplification      CanonicalReplacement
      at bci 122           at bci 13                at bci 25
                                         {replacedNodeClass: ValuePhi,
                                          canonicalNodeClass: Constant}
```

When a method is inlined, it may have already gone through some optimization phases. For that reason, the optimization
tree of the inlinee is copied to the optimization tree of the caller. Therefore, the children of an optimization phase
that performs inlining may be whole optimization subtrees rooted in a `RootPhase` like in the example below.

```
                                  RootPhase
                    _____________/    |    \_____________
                   /                  |                  \
               HighTier            MidTier             LowTier
                   |                  |                   |
             InliningPhase           ...                 ...
             ___/    \___
            /            \
        RootPhase     RootPhase
            |             |
           ...           ...
```

In reality, however, the trees are significantly larger than in this example. Read `Profdiff.md` to learn how to inspect
a real tree. The sections below explain the format of a serialized optimization tree, and how to view an optimization
tree in IGV.

## Inlining tree

`-Djdk.graal.OptimizationLog` also collects inlining trees. The inlining tree of a compilation is a call tree with inlining
decisions. The root of the tree is the root-compiled method. Each node of the tree corresponds to one method, which may
have been inlined to the caller or not. We store the result of the decision (i.e., inlined or not) and also the reason
for this decision. There may be several negative decisions until a method is finally inlined. The children of a node are
the methods invoked in the method represented by the node. The bci of the callsite is also stored for each method in the
tree.

As an example, consider the following set of methods:

```
void a() { b(); c(); }
void b() { }
void c() { d(); e(); }
void d() { }
void e() { }
```

Inlining everything in a compilation of the method `a()` yields the inlining tree below. The prefixes in parentheses
mark the *callsite kind* of an inlining-tree node. Callsite kinds will be explained later.

```
            (root) a()
         ______/  \____
       /                \
(inlined) b()       (inlined) c()
  at bci 0            at bci 3
                  ______/  \_____
                /                 \
         (inlined) d()        (inlined) e()
            at bci 0            at bci 3
```

In the preorder notation used by profdiff, the above inlining tree can be formatted as follows.

```
(root) a()
    (inlined) b() at bci 0
    (inlined) c() at bci 3
        (inlined) d() at bci 0
         (inlined) e() at bci 3
```

Each node except the root represents an invoke node in Graal IR. An invoke node may be deleted as a result of
optimization. The call target of a callsite may be indirect. An indirect callsite cannot be directly inlined, but it may
be devirtualized by the compiler. Devirtualized invokes are represented as the children of an indirect callsite in the
inlining tree.

Using all the properties described above, we classify each inlining-tree node into one of the following callsite kinds:

- `root` - the compiled root method
- `inlined` - an inlined method
- `direct` - a direct method invocation, which was not inlined and not deleted
- `indirect` - an indirect method invocation, which was not inlined and not deleted
- `deleted` - a deleted method invocation
- `devirtualized` - an indirect method invocation that was devirtualized to at least one direct call and then deleted

When an invoke node is added to a Graal graph, it represents either a `direct` or an `indirect` callsite. A `direct`
callsite may be inlined. When an invoke is inlined, it is deleted and the body of the call target is inserted in its
place. The callsite is then marked `inlined`. Alternatively, a `direct` or an `indirect` callsite may be deleted, for
example as a result of dead-code elimination. In that case, we mark the callsite `deleted`. Note that the classification
of a callsite changes during compilation. The inlining tree captures the final state at the end of a compilation.

An `indirect` callsite cannot be directly inlined. If there is only one recorded receiver type for an invoke, the
compiler might (speculatively) relink the invoke to the recorded receiver, effectively making the call direct and
inlinable. Such a call would be classified as `inlined` in the inlining tree. As another option, the compiler might
insert a type switch for the receiver type. Each branch of the switch leads to a direct inlinable call and possibly to a
virtual call or deoptimization as a fallback. In that case, the invokes created for the branches of the type switch are
the children of the original invoke in the inlining tree. The original invoke is either marked `indirect` if it is alive
or `devirtualized` if it is deleted.

As an example of devirtualization, consider the snippet from profdiff below. The call to the `read()` method is
indirect. The recorded receiver types are `ByteArrayInputStream` and `BufferedInputStream`. Both receiver types
implement the `read()` method. We can see that the compiler inlined both implementations of `read()`. Read more about
profdiff and indirect calls in `Profdiff.md`.

```
(devirtualized) java.io.InputStream.read() at bci 4
    |_ receiver-type profile
            97.30% java.io.ByteArrayInputStream -> java.io.ByteArrayInputStream.read()
             2.70% java.io.BufferedInputStream -> java.io.BufferedInputStream.read()
    (inlined) java.io.ByteArrayInputStream.read() at bci 4
    (inlined) java.io.BufferedInputStream.read() at bci 4
        (inlined) jdk.internal.misc.InternalLock.lock() at bci 11
            (inlined) java.util.concurrent.locks.ReentrantLock.lock() at bci 4
                (direct) java.util.concurrent.locks.ReentrantLock$Sync.lock() at bci 4
        (direct) java.io.BufferedInputStream.implRead() at bci 15
        (direct) jdk.internal.misc.InternalLock.unlock() at bci 23
```

## Example: optimization log of a benchmark

Run a benchmark with the flag `-Djdk.graal.OptimizationLog=Directory` to produce an output and save it to the directory
specified by the `-Djdk.graal.OptimizationLogPath` option. Run it jointly with `-Djdk.graal.TrackNodeSourcePosition=true`, so
that optimizations can be linked with a source position.

```sh
mx benchmark renaissance:scrabble -- -Djdk.graal.TrackNodeSourcePosition=true -Djdk.graal.OptimizationLog=Directory \
  -Djdk.graal.OptimizationLogPath=$PWD/optimization_log
```

An equivalent set of commands for Native Image is:

```sh
cd ../vm
mx --env ni-ce build
mx --env ni-ce benchmark renaissance-native-image:scrabble -- --jvm=native-image --jvm-config=default-ce \
  -Dnative-image.benchmark.extra-image-build-argument=-H:+TrackNodeSourcePosition \
  -Dnative-image.benchmark.extra-image-build-argument=-H:OptimizationLog=Directory \
  -Dnative-image.benchmark.extra-image-build-argument=-H:OptimizationLogPath=$PWD/optimization_log
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
    "indirect": false,
    "alive": false,
    "invokes": [
      {
        "methodName": "java.lang.String.isLatin1()",
        "callsiteBci": 17,
        "inlined": true,
        "reason": [
          "bytecode parser did not replace invoke",
          "trivial (relevance=1.000000, probability=0.618846, bonus=1.000000, nodes=9)"
        ],
        "indirect": false,
        "alive": false,
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
        "indirect": false,
        "alive": false,
        "invokes": null
      }
    ]
  },
  "optimizationTree": {
    "phaseName": "RootPhase",
    "optimizations": [
      "..."
    ]
  }
}
```

The `methodName` is the name of the root method in the compilation unit. `compilationId` is a unique identifier of the
compilation unit.

`inliningTree` contains the root of the inlining tree, i.e, the name of the root method matches `methodName`.
`invokes` are the invoked methods which were considered for inlining. The final result of the inlining decisions is
reflected by the `inlined` property. Its value equals `true` if the method was inlined, otherwise it is `false`. The
reasons for the decisions, in their original order, are listed in the `reason` property. The property `alive` is `false`
iff the associated invoke node was deleted during the compilation (e.g. as a result of dead code elimination).
Finally, `callsiteBci` is the byte code index of the invoke node in the callsite.

The `indirect` property is `true` iff the call is known to be indirect, i.e., it is an invoke through an
interface or a virtual method call. Indirect calls contain
a [receiver-type profile](https://wiki.openjdk.org/display/HotSpot/TypeProfile) if it is available. Consider the
indirect call to `Iterator.next()` below.

```json
{
  "methodName": "java.util.Iterator.next()",
  "callsiteBci": 19,
  "inlined": false,
  "reason": [
    "bytecode parser did not replace invoke",
    "call is indirect."
  ],
  "indirect": true,
  "alive": false,
  "receiverTypeProfile": {
    "mature": true,
    "profiledTypes": [
      {
        "typeName": "java.util.HashMap$KeyIterator",
        "probability": 0.90,
        "concreteMethodName": "java.util.HashMap$KeyIterator.next()"
      },
      {
        "typeName": "java.util.Arrays$ArrayItr",
        "probability": 0.09,
        "concreteMethodName": "java.util.Arrays$ArrayItr.next()"
      },
      {
        "typeName": "java.util.Collections$1",
        "probability": 0.01,
        "concreteMethodName": "java.util.Collections$1.next()"
      }
    ]
  }
}
```

`mature` is `true` iff the receiver-type profile is mature. `profiledTypes` is an array, which contains an entry
for each observed receiver type of the call. The exact type of the receiver is in the `typeName` property, `probability`
is the fraction of calls when the receiver's type is `typeName`, and `concreteMethodName` is the concrete method invoked
for this receiver type.

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

We can see that a `NodeRemoval` occurred at bci 10 in the inlined `java.lang.StringLatin1.hashCode(byte[])` method,
whose callsite is at bci 27 in the root method. Note that the order of keys is important in this case.

## IGV output

Optimization trees can be printed to Ideal Graph Visualizer. First, start an IGV instance. After that, run a benchmark
with the flag `-Djdk.graal.OptimizationLog=Dump`. Run it jointly with `-Djdk.graal.TrackNodeSourcePosition=true`, so that
optimizations can be linked with a source position.

```sh
mx benchmark renaissance:scrabble -- -Djdk.graal.TrackNodeSourcePosition=true -Djdk.graal.OptimizationLog=Dump \
  -Djdk.graal.PrintGraph=Network
```

Optimization trees for each compilation should now be available in IGV.

## Overhead of optimization logging

Enabling `-Djdk.graal.OptimizationLog` as well as `-Djdk.graal.TrackNodeSourcePosition` comes with an overhead. It may slow the
compilation down in terms of CPU time and the logs may generate hundreds of MB of many small files. Depending on the
workload, node source positions can decrease the compile speed (measured in bytes/sec) by up to 15%. Optimization log
with node source positions can decrease the speed by up to 25%.
