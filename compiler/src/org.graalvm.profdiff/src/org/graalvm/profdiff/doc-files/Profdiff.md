# Introduction to profdiff

The `mx profdiff` tool can display and compare optimization and inlining decisions performed in a compilation of a Java
application. The output can be augmented with an application profile captured
by [proftool](https://github.com/graalvm/mx/blob/master/README-proftool.md). Proftool data enables the tool to identify
the hottest compilation units.

The tool can compare optimization decisions between 2 runs of one application. It finds differences between the hottest
compilations by utilizing tree diffing algorithms. A compilation unit consists of an inlining tree and optimization
tree. Read `OptimizationLog.md` first to understand the meaning of the trees. It is possible to compare 2 JIT
compilation or a JIT compilation with an AOT compilation.

The tool reads the data produced by the optimization log. The optimization log can is enabled by
the `-Dgraal.OptimizationLog` flag (`-H:OptimizationLog` for `native-image`). Read `OptimizationLog.md` to learn more.

## Usage

There are 3 general use cases:

- display the optimization log of one experiment, optionally providing proftool data,
- compare 2 JIT experiments including proftool data,
- compare a JIT experiment (including proftool data) with an AOT experiment.

These use cases are facilitated by the commands `mx profdiff report`, `mx profdiff jit-vs-jit`,
and `mx profdiff jit-vs-aot` respectively. Run `mx profdiff help` to show the general help or `mx profdiff help COMMAND`
to show help for a command.

## Example: benchmark without profile

Run a benchmark with the optimization log and node source positions enabled. Node source positions allow the
optimization log to correlate individual optimizations with a position in the bytecode.

```sh
mx benchmark renaissance:scrabble -- -Dgraal.OptimizationLog=Directory -Dgraal.OptimizationLogPath=$(pwd)/scrabble_log
```

It is not necessary to specify `-Dgraal.TrackNodeSourcePositions=true`, because it is inserted implicitly by
the `mx benchmark` infrastructure. The dump path is explicitly specified as an absolute path to avoid any surprises.

Use the tool to display the logs in the `scrabble_log` directory:

```sh
mx profdiff report scrabble_log
```

The output always starts with a short explanation of the concepts and formats. Compilation units are grouped by root
method names and the inlining and optimization trees are printed for each compilation.

## Example: benchmark with profile

To focus only on the most important compilation units, i.e. the most frequently executed compilations, it is possible to
make use of a profile collected by proftool.

Note that the directory with the optimization logs must be cleared before the experiment (`rm -rf scrabble_log`),
otherwise the logs will get merged together.

```sh
mx benchmark renaissance:scrabble --tracker none -- --profiler proftool -Dgraal.OptimizationLog=Directory \
  -Dgraal.OptimizationLogPath=$(pwd)/scrabble_log
```

If the application subject to experiment is not a benchmark supported by `mx benchmark`, use `mx profrecord` as per
the [proftool documentation](https://github.com/graalvm/mx/blob/master/README-proftool.md).

Proftool creates a directory named like `proftool_scrabble_2022-10-14_143325`. The generated proftool data must be
now converted to JSON.

```sh
mx profjson -E proftool_scrabble_2022-10-14_143325 -o scrabble_prof.json
```

Finally, use profdiff to view the results:

```sh
mx profdiff report scrabble_log scrabble_prof.json
```

## Example: compare 2 JIT benchmarks

Run a benchmark with the optimization log, node source positions and proftool. After that, convert the proftool data to
JSON.

```sh
mx benchmark renaissance:scrabble --tracker none -- --profiler proftool -Dgraal.OptimizationLog=Directory \
  -Dgraal.OptimizationLogPath=$(pwd)/scrabble_log
mx profjson -E proftool_scrabble_2022-07-05_140847 -o scrabble_prof.json
```

Now, we could run the experiment again with a different compiler revision. It is however sufficient to run the same
experiment again and get a slightly different result, which is caused by the inherent nondeterminism of a JIT compiler.

```sh
mx benchmark renaissance:scrabble --tracker none -- --profiler proftool -Dgraal.OptimizationLog=Directory \
  -Dgraal.OptimizationLogPath=$(pwd)/scrabble_log2
mx profjson -E proftool_scrabble_2022-07-05_141855 -o scrabble_prof2.json
```

Use the tool to diff the experiments:

```sh
mx profdiff jit-vs-jit scrabble_prof.json scrabble_log scrabble_prof2.json scrabble_log2
```

## Example: compare JIT and AOT

Start with a profiled JIT benchmark and convert the proftool data:

```sh
mx benchmark renaissance:scrabble --tracker none -- --profiler proftool -Dgraal.OptimizationLog=Directory \
  -Dgraal.OptimizationLogPath=$(pwd)/jit_scrabble_log
mx profjson -E proftool_scrabble_2022-07-05_140847 -o scrabble_prof.json
```

Run the AOT version of the benchmark from the `vm` directory:

```sh
cd ../vm
mx --env ni-ce build
mx --env ni-ce benchmark renaissance-native-image:scrabble -- --jvm=native-image --jvm-config=default-ce \
  -Dnative-image.benchmark.extra-image-build-argument=-H:OptimizationLog=Directory \
  -Dnative-image.benchmark.extra-image-build-argument=-H:OptimizationLogPath=$(pwd)/aot_scrabble_log
```

Finally, compare the logs. The profiles from the JIT run determine hot methods in AOT.

```sh
mx profdiff jit-vs-aot ../compiler/jit_scrabble_log ../compiler/scrabble_prof.json aot_scrabble_log
```

# Profdiff documentation

## Options

There a few options accepted by all `profdiff` commands. The `--verbosity` option controls the amount of output that is
displayed for each compilation unit. Possible values are `low`, `default`, `high`, `max`. The `low` verbosity level
displays only the important differences between compilation units by applying preprocessing steps. In contrast, `max`
dumps all available data about a compilation without any preprocessing or diffing.

The options `--hot-min-limit`, `--hot-max-limit`, and `--hot-percentile` are parameters for the algorithm that selects
hot compilations. This is relevant only when proftool data is provided. `--hot-min-limit` and `--hot-max-limit` are hard
upper and lower bounds, respectively, on the number of hot compilations. `--hot-percentile` is the percentile of the
execution period taken up by hot compilations. Read the section about hot compilations for more information.

## Hot compilations

Proftool collects samples the number of cycles spent executing each compilation unit. The tool marks some of
the compilation units with the highest timeshare as *hot*. This is a different term than "hot" in the context of
HotSpot. More precisely, the tool marks some *compilations units* as hot, whereas all the available methods (rather than
their compilations) are considered hot in HotSpot's terminology.

The algorithm to mark hot methods works as follows:

- for each experiment separately
  - sort all graal-compiled compilation units by their execution period (highest first)
  - mark the first compilation as hot (the number can be adjusted by the `--hot-min-limit` parameter)
  - keep marking the compilations as hot while the total timeshare of hot compilations is less than
    90% (`--hot-percentile`) of total graal-compiled method execution and the number of hot compilations is less than
    10 (`--hot-max-limit`)

## Node source positions

Optimizations are associated with a node source position via a node that took part in the transformation. A node source
position contains a bytecode index (bci) in the method. This bci is the position of an optimization that is displayed
at verbosity level `default` or lower.

However, in the presence of inlining, the bci is relative to the inlined method. For verbosity levels `high` and `max`,
we also display the path from an inlined method to the root including bytecode indices of callsites.

For example, in the compilation of `String#equals`, we might have the following optimization:

```
Canonicalizer UnusedNodeRemoval at bci 9
```

At a higher verbosity level, we can see that the bci 9 is relative to the inlined `StringLatin1#equals` method, which is
invoked by the root method at bci 44.

```
Canonicalizer UnusedNodeRemoval at bci {java.lang.StringLatin1.equals(byte[], byte[]): 9,
                                        java.lang.String.equals(Object): 44}
```

## Preprocessing and postprocessing

Depending on the `--verbosity` level, preprocessing steps are taken to simplify the inlining/optimization trees and/or
their matching. This reduces the amount of produced output by leaving out unimportant information.

- The children of each node in the inlining tree are always sorted lexicographically by (callsite bci, method name).
  This makes the tree easier to navigate and also reduces excessive differences found by the tree matching algorithm,
  which does not understand the change of order as an operation.
- The children of selected optimization phases in the inlining trees are sorted. The goal is to establish a fixed order
  of optimization phases across compilation units to reduce the number of superfluous differences found by the
  algorithm. This is only applied at `--verbosity low`.
- Selected optimization phases are removed from the optimization tree at `--verbosity low`. These include the
  canonicalizer, dead code elimination, and inlining phases.

Finally, there is one postprocessing step performed on the delta tree after optimization/inlining tree matching. The
delta tree is the result of tree matching. Each node represents either a deletion, insertion, or identity.

- Only the differences between the trees are shown with context at `--verbosity low`. Equivalently, all identity nodes
  which do not have any deletion or insertion as a descendant are deleted. As a result of this, a matching of equivalent
  trees is an empty tree at `--verbosity low`.

## Comparing experiments

This section describes algorithms and output formats that are relevant for comparing experiments.

### Method matching

The result after hot method marking is a set of hot compilations for each experiment. Some of these may be compilations
of the same root method. The tool can figure this out by grouping the compilations by their method names.

Consider an example of the tool's output:

```
Method java.util.stream.ReduceOps$ReduceOp.evaluateSequential(PipelineHelper, Spliterator)
    In experiment 1
        3 compilations (1 of which are hot)
        Compilations
            9068 (19.74% of Graal execution, 11.64% of total) *hot*
            7878 (0.00% of Graal execution, 0.00% of total)
            9003 (0.00% of Graal execution, 0.00% of total)
    In experiment 2
        2 compilations (1 of which are hot)
        Compilations
            12622 (16.24% of Graal execution, 9.74% of total) *hot*
            9215 (0.06% of Graal execution, 0.04% of total)
```

We can see that the root method `java.util.stream.ReduceOps$ReduceOp.evaluateSequential(PipelineHelper, Spliterator)`
has 3 compilations in the 1st experiment and 2 compilations in the 2nd experiment. Some of these compilations were
marked as hot.

In this case, we would like to show the diff between the 2 hot compilations, even though there are 3 * 2 possible pairs
of compilations. In the general case, the 1st hottest compilation from the 1st experiment is diffed with the 1st hottest
compilation from the 2nd experiment, the 2nd hottest is diffed with the 2nd hottest and so on. When a hot compilation
does not have a pair in the other experiment, the whole optimization tree is simply dumped.

### Optimization tree matching

The tool displays the diff of optimization trees for each pair of hot compilations. The diff of 2 optimization trees is
displayed in the form of a delta tree. Each node in the delta tree is an optimization phase/individual optimization
paired with an edit operation. Edit operations include insertion, deletion, and identity. The kind of the edit operation
is displayed as the prefix of the node.

- prefix `-` = this phase/optimization is present in the 1st compilation but absent in the 2nd compilation (deletion)
- prefix `+` = this phase/optimization is absent in the 1st compilation but present in the 2nd compilation (insertion)
- (no prefix) = this phase/optimization is present and unchanged in both compilations (identity)

Consider the following example:

```
Method java.util.stream.ReduceOps$ReduceOp.evaluateSequential(PipelineHelper, Spliterator)
    ...
    Compilation 9068 (19.74% of Graal execution, 11.64% of total) in experiment 1 vs compilation 12622 (16.24% of Graal
    execution, 9.74% of total) in experiment 2
          RootPhase
              Parsing
                  GraphBuilderPhase
                  DeadCodeEliminationPhase
              HighTier
                  CanonicalizerPhase
                  InliningPhase
                  ...
```

We can see a few phase names without any prefix, which means the phases present in both compilations. Further down, we
can observe something like this:

```
CanonicalizerPhase
    - Canonicalizer CanonicalReplacement at bci 6 {replacedNodeClass: ValuePhi, canonicalNodeClass: Constant}
    - Canonicalizer CanonicalReplacement at bci 9 {replacedNodeClass: ValuePhi, canonicalNodeClass: Constant}
    + Canonicalizer CanonicalReplacement at bci 6 {replacedNodeClass: Pi, canonicalNodeClass: Pi}
```

The interpretation here is that the `CanonicalizerPhase` was present in both compilations. However, it performed 2
canonical replacements in the 1st compilation and one different replacement in the 2nd compilation.

### Inlining tree matching

The matching of inlining trees is similar to the matching of optimization trees, except with an extra relabelling
operation. Negative inlining decisions also make the interpretation a bit more complicated.

The result of 2 matched inlining trees is again a delta tree. Each node in the delta tree is an inlining tree node
paired with an edit operation. Edit operations include insertion, deletion, relabelling, and identity. The kind of the
edit operation is indicated by the prefix of the node.

- prefix `-` = this inlining tree node is present in the 1st compilation but absent in the 2nd compilation (deletion)
- prefix `+` = this inlining tree node is absent in the 1st compilation but present in the 2nd compilation (insertion)
- prefix `*` = this inlining tree node is present int both compilations but the inlining decisions are different
  (relabelling)
- (no prefix) = this inlining tree node is present and unchanged in both compilations (identity)

Provided that there 2 kinds of inlining tree nodes with respect to the inlining decision (inlined and not inlined),
there are 2 * 4 cases to be interpreted.

For instance, consider a non-root method in a compilation like `java.lang.String.equals(Object) at bci 44`. After being
evaluated by the inlining algorithm, it would appear in the inlining tree either as
`java.lang.String.equals(Object) at bci 44` (after being inlined)
or `(not inlined) java.lang.String.equals(Object) at bci 44`. It is also possible that it would not be evaluated at all,
which is the case when its caller is not expanded by the inlining algorithm. In that situation, there is no node for
the method in the inlining tree.

Therefore, all possible combinations in the delta tree are:

- `- java.lang.String.equals(Object) at bci 44` = the method was inlined in the 1st compilation but not evaluated in the
  2nd compilation
- `- (not inlined) java.lang.String.equals(Object) at bci 44` = the method was evaluated but not inlined in the 1st
  compilation and not evaluated in the 2nd compilation
- `+ java.lang.String.equals(Object) at bci 44` = the method was not evaluated in the 1st compilation but inlined in the
  2nd compilation
- `+ (not inlined) java.lang.String.equals(Object) at bci 44` = the method was not evaluated in the 1st compilation
  but evaluated and not inlined in the 2nd compilation
- `* (inlined -> not inlined) java.lang.String.equals(Object) at bci 44` = the method was evaluated in both
  compilations, inlined the 1st compilation but not inlined in the 2nd compilation
- `* (not inlined -> inlined) java.lang.String.equals(Object) at bci 44` = the method was evaluated in both
  compilations, not inlined the 1st compilation but inlined in the 2nd compilation
- `java.lang.String.equals(Object) at bci 44` = the method was evaluated and inlined in both compilations
- `(not inlined) java.lang.String.equals(Object) at bci 44` = the method was evaluated and not inlined in both
  compilations
