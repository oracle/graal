# Introduction to profdiff

`mx profdiff` can display and compare optimization and inlining decisions performed in a compilation of a Java
application. The output can be augmented with an application profile captured
by [proftool](https://github.com/graalvm/mx/blob/master/README-proftool.md). Proftool data enables the tool to identify
the hottest compilation units.

The tool can compare optimization decisions between two runs of one application. It finds differences between the
hottest compilations by utilizing tree-diffing algorithms. A compilation unit consists of an inlining tree and
optimization tree. Read `OptimizationLog.md` first to understand the trees. It is possible to compare two JIT
compilations, a JIT compilation with an AOT compilation, or two AOT compilations.

The tool reads data from the optimization log, which is enabled using the flag `-Djdk.graal.OptimizationLog`
(`-H:OptimizationLog` for Native Image). Read `OptimizationLog.md` to learn more.

## Usage

There is a separate command for each use cae:

- `mx profdiff report`: display the optimization log of a single JIT or AOT experiment (optionally providing a profile),
- `mx profdiff jit-vs-jit`: compare two profiled JIT experiments,
- `mx profdiff jit-vs-aot`: compare a profiled JIT experiment with an (optionally profiled) AOT experiment,
- `mx profdiff aot-vs-aot`: compare two profiled AOT experiments,
- `mx profdiff aot-vs-aot-jit-profile`: compare two AOT experiments using an external profile from a JIT experiment.

Run `mx profdiff help` to show the general help or `mx profdiff help COMMAND` to show help for a command.

## Example: benchmark without a profile

Run a benchmark with the optimization log and node source positions enabled. Node source positions allow the
optimization log to correlate individual optimizations with a position in the bytecode.

```sh
mx benchmark renaissance:scrabble -- -Djdk.graal.TrackNodeSourcePosition=true -Djdk.graal.OptimizationLog=Directory \
  -Djdk.graal.OptimizationLogPath=$PWD/scrabble_log
```

The dump path is explicitly specified as an absolute path to avoid any surprises. Use the tool to display the logs in
the `scrabble_log` directory:

```sh
mx profdiff report scrabble_log
```

The output always starts with a short explanation of the concepts and formats. Compilation units are grouped by root
method names and the inlining and optimization trees are printed for each compilation.

## Example: benchmark with a profile

To focus only on the most important compilation units, i.e. the most frequently executed compilations, it is possible to
collect a profile using proftool.

Note that the directory with the optimization logs must be cleared before the experiment (`rm -rf scrabble_log`),
otherwise the logs get merged together.

```sh
rm -rf proftool_scrabble_*
mx benchmark renaissance:scrabble --tracker none -- --profiler proftool -Djdk.graal.OptimizationLog=Directory \
  -Djdk.graal.OptimizationLogPath=$PWD/scrabble_log
```

If the application subject to experiment is not a benchmark supported by `mx benchmark`, use `mx profrecord` as per
the [proftool documentation](https://github.com/graalvm/mx/blob/master/README-proftool.md).

Convert the proftool data to a JSON file:

```sh
mx profjson -E proftool_scrabble_* -o scrabble_prof.json
```

Finally, use profdiff to view the results:

```sh
mx profdiff report scrabble_log scrabble_prof.json
```

## Example: compare two JIT benchmarks

Run a benchmark with the optimization log, node source positions, and proftool. After that, convert the proftool data to
a JSON file.

```sh
rm -rf proftool_scrabble_*
mx benchmark renaissance:scrabble --tracker none -- --profiler proftool -Djdk.graal.OptimizationLog=Directory \
  -Djdk.graal.OptimizationLogPath=$PWD/scrabble_log
mx profjson -E proftool_scrabble_* -o scrabble_prof.json
```

Now, we could rerun the experiment with a different compiler revision. It is, however, sufficient to run the same
experiment again and get a slightly different result, which is caused by the inherent nondeterminism of a JIT compiler.

```sh
rm -rf proftool_scrabble_*
mx benchmark renaissance:scrabble --tracker none -- --profiler proftool -Djdk.graal.OptimizationLog=Directory \
  -Djdk.graal.OptimizationLogPath=$PWD/scrabble_log_2
mx profjson -E proftool_scrabble_* -o scrabble_prof_2.json
```

Use the tool to diff the experiments:

```sh
mx profdiff jit-vs-jit scrabble_log scrabble_prof.json scrabble_log_2 scrabble_prof_2.json
```

## Example: compare JIT and AOT

Start with a profiled JIT benchmark and convert the proftool data:

```sh
rm -rf proftool_scrabble_*
mx benchmark renaissance:scrabble --tracker none -- --profiler proftool -Djdk.graal.OptimizationLog=Directory \
  -Djdk.graal.OptimizationLogPath=$PWD/jit_scrabble_log
mx profjson -E proftool_scrabble_* -o jit_scrabble_prof.json
```

Run the AOT version of the benchmark:

```sh
mx -p ../vm --env ni-ce benchmark renaissance-native-image:scrabble -- --jvm=native-image --jvm-config=default-ce \
  -Dnative-image.benchmark.extra-image-build-argument=-H:+TrackNodeSourcePosition \
  -Dnative-image.benchmark.extra-image-build-argument=-H:OptimizationLog=Directory \
  -Dnative-image.benchmark.extra-image-build-argument=-H:OptimizationLogPath=$PWD/aot_scrabble_log
```

Finally, compare the experiments. The profiles from the JIT run determine hot methods in AOT.

```sh
mx profdiff jit-vs-aot jit_scrabble_log jit_scrabble_prof.json aot_scrabble_log
```

### Adding an AOT profile

It is possible to build the image with debug symbols and run it with proftool:

```sh
rm -rf proftool_scrabble_* aot_scrabble_log
mx -p ../vm --env ni-ce benchmark renaissance-native-image:scrabble --tracker none -- \
  --profiler proftool --jvm=native-image --jvm-config=default-ce \
  -Dnative-image.benchmark.extra-image-build-argument=-H:+TrackNodeSourcePosition \
  -Dnative-image.benchmark.extra-image-build-argument=-H:OptimizationLog=Directory \
  -Dnative-image.benchmark.extra-image-build-argument=-H:OptimizationLogPath=$PWD/aot_scrabble_log
```

Now, convert the profile and pass it to profdiff:

```sh
mx profjson -E proftool_scrabble_* -o aot_scrabble_prof.json
mx profdiff jit-vs-aot jit_scrabble_log jit_scrabble_prof.json aot_scrabble_log aot_scrabble_prof.json
```

# Example: compare two AOT experiments

It is possible to execute and compare two AOT experiments with profiles:

```sh
for i in 1 2; do
    rm -rf proftool_scrabble_*
    mx -p ../vm --env ni-ce benchmark renaissance-native-image:scrabble --tracker none -- \
        --profiler proftool --jvm=native-image --jvm-config=default-ce \
        -Dnative-image.benchmark.extra-image-build-argument=-H:+TrackNodeSourcePosition \
        -Dnative-image.benchmark.extra-image-build-argument=-H:OptimizationLog=Directory \
        -Dnative-image.benchmark.extra-image-build-argument=-H:OptimizationLogPath=$PWD/scrabble_log_$i
    mx profjson -E proftool_scrabble_* -o scrabble_prof_$i.json
done
mx profdiff aot-vs-aot scrabble_log_1 scrabble_prof_1.json scrabble_log_2 scrabble_prof_2.json
```

# Profdiff documentation

## Options

There are several options accepted by all `profdiff` commands. None of them are mandatory, and they are all set to
default values which are suitable for quick identification of differences. Run `mx profdiff help` to view the
defaults. Note that binary options expect the literal `true` or `false`, e.g. `--long-bci=true`.

The options `--hot-min-limit`, `--hot-max-limit`, and `--hot-percentile` are parameters for the algorithm that selects
hot compilations. This is relevant only when proftool data is provided. `--hot-min-limit` and `--hot-max-limit` are hard
upper and lower bounds, respectively, on the number of hot compilations. `--hot-percentile` is the percentile of the
execution period taken up by hot compilations. Read the section about hot compilations for more information.

The option `--optimization-context-tree` enables the optimization-context tree. The optimization-context tree visualizes
optimizations in their inlining context. An optimization-context tree is constructed by combining an inlining tree and
optimization tree. If this option is enabled, optimization-context trees replace inlining and optimization trees in the
output. This is not enabled by default. Read the separate section about the optimization-context tree to learn more.

If `--diff-compilations` is enabled, all pairs of hot compilations of the same method in two experiments are diffed. If
it is not enabled, all compilations are printed without any diffing. `--diff-compilations` is enabled by default. Read
the section about method matching to learn more.

If `--long-bci` is enabled, the full position of each optimization is displayed. This is not applied by default. Read
the section about node source positions to learn more.

Depending on the value of `--sort-inlining-tree`, the children of each node in the inlining tree are sorted
lexicographically by (callsite bci, method name). This makes the tree easier to navigate and also reduces excessive
differences found by the tree matching algorithm, which does not understand the change of order as an operation. Enabled
by default.

If `--sort-unordered-phases` is enabled, the children of selected optimization phases in the optimization trees are
sorted. The goal is to establish a fixed order of optimization phases across compilation units to reduce the number of
superfluous differences found by the tree diffing algorithm. This is enabled by default.

Selected optimization phases are removed from the optimization tree when `--remove-detailed-phases` is enabled. These
include the canonicalizer and dead code elimination. This is enabled by default.

If `--prune-identities` is enabled, only the differences with context between two
optimization/inlining/optimization-context trees are displayed, i.e., the identities are pruned. This is enabled by
default.

The option `--create-fragments` controls the creation of compilation fragments. This is enabled by default. Read the
section about compilation fragments to learn more.

If `--inliner-reasoning` is enabled, reasons for all inlining decisions are printed. Disabled by default.

## Hot compilation units

Proftool samples the number of cycles spent executing each native method. Profdiff reports two numbers for each
compilation unit, e.g., `20.00% of Graal execution, 10.00% of total`. This means that proftool sampled 10% of all cycles
in this compilation unit, and relative to only Graal-compiled compilation units, 20% of cycles were sampled in this
compilation unit. For compilation fragments, profdiff reports the statistics of the compilation unit from which the
fragment originates.

Profdiff marks some of the compilation units with the highest timeshare as *hot*. Note that this is a different term
than "hot" in the context of HotSpot. The algorithm to mark hot compilation units works as follows:

- for each experiment separately
  - sort all Graal-compiled compilation units by their sampled execution period (highest first)
  - mark the first compilation as hot (the number can be adjusted by the `--hot-min-limit` parameter)
  - keep marking the compilations as hot while the total timeshare of hot compilation units is less than
    90% (`--hot-percentile`) of total Graal-compiled method execution and the number of hot compilations is less than
    10 (`--hot-max-limit`)

## Node source positions

Optimizations are associated with node source positions via nodes that took part in the transformation. Profdiff
displays the positions either as bytecode indexes relative to the root method (short form) or as lists of method names
and bcis (long form). Short-form positions are the default.

An example of an optimization with a short-form position is:

```
Canonicalizer UnusedNodeRemoval at bci 44
```

We can run profdiff with `--long-bci=true` to print long-form positions.

```
Canonicalizer UnusedNodeRemoval at bci {java.lang.StringLatin1.equals(byte[], byte[]): 9,
                                        java.lang.String.equals(Object): 44}
```

The output above says that the optimization was performed in method `java.lang.StringLatin1.equals(byte[], byte[])` at
bci 9, which was inlined in the root method `java.lang.String.equals(Object)` at bci 44. Note that the short-form
output reported the bci as 44, because it is relative to the root method (`java.lang.String.equals(Object)` in this
case).

The above text describes how positions are formatted in the optimization tree. Perhaps a better way to visualize the
position of an optimization is to enable the optimization-context tree. The optimization-context tree shows optimization
decisions in their inlining context. The bcis of optimizations (like `UnusedNodeRemoval`) are displayed relative to the
parent method. Consider the example below. It states that `UnusedNodeRemoval` was performed at bci 9 in method
`java.lang.StringLatin1.equals(byte[], byte[])`, which was inlined at bci 44 in method
`java.lang.String.equals(Object)`.

```
(root) java.lang.String.equals(Object)
    (inlined) java.lang.StringLatin1.equals(byte[], byte[]) at bci 44
        Canonicalizer UnusedNodeRemoval at bci 9
```

Read the section about the optimization-context tree to learn more.

## Indirect calls

Indirect calls, e.g. virtual method calls or a calls through an interface, cannot be directly inlined. Therefore,
profdiff marks these calls as `(indirect)`.

Consider a call to the interface method `java.util.Iterator.next()`.

```
(indirect) java.util.Iterator.next() at bci 19
    |_ receiver-type profile
            90.13% java.util.HashMap$KeyIterator -> java.util.HashMap$KeyIterator.next()
             9.51% java.util.Arrays$ArrayItr -> java.util.Arrays$ArrayItr.next()
             0.24% java.util.Collections$1 -> java.util.Collections$1.next()
             0.08% java.util.ImmutableCollections$Set12$1 -> java.util.ImmutableCollections$Set12$1.next()
             0.03% java.util.HashMap$ValueIterator -> java.util.HashMap$ValueIterator.next()
             0.01% java.util.LinkedList$ListItr -> java.util.LinkedList$ListItr.next()
```

Profdiff also shows the receiver-type profile for indirect calls if it is available. The profile contains several
entries on separate lines, each in the format `probability% typeName -> concreteMethodName`. The type name is the exact
type of the receiver (a type implementing `java.util.Iterator` in the example). `concreteMethodName` is the concrete
method called for the given receiver type (an implementation of `java.util.Iterator.next()` in our case). `probability`
is the fraction of calls having this exact receiver type.

The compiler may devirtualize an indirect call. The log always shows the last known state. For example, if there is an
indirect call with just one possible receiver, the compiler can link the call to the concrete receiver and the call
appears in the log as a direct call.

## Optimization-context tree

An optimization-context tree is an inlining tree extended with optimizations placed in their method context.
Optimization-context trees make it easier to attribute optimizations to inlining decisions. However, the structure of
the optimization tree is lost. The feature is enabled using the option `--optimization-context-tree`, and is compatible
with each command, e.g., `mx profdiff --optimization-context-tree=true report scrabble_log`.

As an instance, consider the inlining and optimization trees below:

```
A compilation unit of the method a()
    Inlining tree
        (root) a()
            (inlined) b() at bci 1
                (inlined) d() at bci 3
            (inlined) c() at bci 2
    Optimization tree
        RootPhase
            SomeOptimizationPhase
                OptimizationA at bci {a(): 3}
                OptimizationB at bci {b(): 4, a(): 1}
                OptimizationC at bci {c(): 5, a(): 2}
                OptimizationD at bci {d(): 6, b(): 3, a(): 1}
```

By combining the trees, we obtain the following optimization-context tree:

```
A compilation unit of the method a()
    Optimization-context tree
        (root) a()
            OptimizationA at bci 3
            (inlined) b() at bci 1
                OptimizationB at bci 4
                (inlined) d() at bci 3
                    OptimizationD at bci 6
            (inlined) c() at bci 2
                OptimizationC at bci 5
```

However, code duplication makes the attribution of optimizations to the inlining tree ambiguous. For example, suppose
that `b() at bci 1` is called in a loop, which gets peeled. The inlining tree then looks as follows:

```
A compilation unit of the method a() with duplication
    Inlining tree
        (root) a()
            (inlined) b() at bci 1
                (inlined) d() at bci 3
            (inlined) b() at bci 1
                (inlined) d() at bci 3
            (inlined) c() at bci 2
```

It is not clear to which `b() at bci 1` the optimization `OptimizationB at bci {b(): 4, a(): 1}` belongs.
If `OptimizationB` were performed before the duplication, it would affect both calls to `b()`. For these reasons,

- profdiff inserts warnings about the ambiguity to all calls whose *inlining path from root* is not unique in the
  inlining tree,
- the optimizations are always attributed to each matching inlining-tree node.

The optimization optimization-context tree for the above example would look like this:

```
A compilation unit of the method a() with duplication
    Optimization-context tree
        (root) a()
            OptimizationA at bci 3
            (inlined) b() at bci 1
                Warning: Optimizations cannot be unambiguously attributed (duplicate path)
                OptimizationB at bci 4
                (inlined) d() at bci 3
                    Warning: Optimizations cannot be unambiguously attributed (duplicate path)
                    OptimizationD at bci 6
            (inlined) b() at bci 1
                Warning: Optimizations cannot be unambiguously attributed (duplicate path)
                OptimizationB at bci 4
                (inlined) d() at bci 3
                    Warning: Optimizations cannot be unambiguously attributed (duplicate path)
                    OptimizationD at bci 6
            (inlined) c() at bci 2
                OptimizationC at bci 5
```

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
             9068 consumed 19.74% of Graal execution, 11.64% of total *hot*
             7878 consumed  0.00% of Graal execution,  0.00% of total
             9003 consumed  0.00% of Graal execution,  0.00% of total
    In experiment 2
        2 compilations (1 of which are hot)
        Compilations
            12622 consumed 16.24% of Graal execution,  9.74% of total *hot*
             9215 consumed  0.06% of Graal execution,  0.04% of total
```

We can see that the root method `java.util.stream.ReduceOps$ReduceOp.evaluateSequential(PipelineHelper, Spliterator)`
has 3 compilations in the 1st experiment and 2 compilations in the 2nd experiment. Some of these compilations were
marked as hot.

If `--diff-compilations` is enabled, all pairs of *hot* compilations are diffed (in our case, there is only one pair).
Otherwise, hot compilations are printed without any diffing.

Note that a compilation fragment is a kind of compilation. However, fragments are diffed only with other compilation
units, i.e., pairs of 2 compilation fragments are skipped.

### Optimization-tree matching

The tool computes a matching between optimization trees for each pair of hot compilations. The matching is displayed in
the form of a delta tree. Each node in the delta tree is an optimization phase/individual optimization paired with an
edit operation. Edit operations include insertion, deletion, and identity. The kind of edit operation is displayed as
the prefix of the node.

- prefix `.` = this phase/optimization is present and unchanged in both compilations (identity)
- prefix `-` = this phase/optimization is present in the 1st compilation but absent in the 2nd compilation (deletion)
- prefix `+` = this phase/optimization is absent in the 1st compilation but present in the 2nd compilation (insertion)

Consider the following example:

```
Method java.util.stream.ReduceOps$ReduceOp.evaluateSequential(PipelineHelper, Spliterator)
    ...
    Compilation unit  9068 consumed 19.74% of Graal execution, 11.64% of total in experiment 1 vs
    Compilation unit 12622 consumed 16.24% of Graal execution,  9.74% of total in experiment 2
        . RootPhase
            . Parsing
                . GraphBuilderPhase
                . DeadCodeEliminationPhase
            . HighTier
                . CanonicalizerPhase
                . InliningPhase
                  ...
```

We can see a few phase names with the prefix `.`, which means that the phases are present in both compilations. Further
down, we can observe a difference between applied optimizations:

```
. CanonicalizerPhase
    - Canonicalizer CanonicalReplacement at bci 6 {replacedNodeClass: ValuePhi, canonicalNodeClass: Constant}
    - Canonicalizer CanonicalReplacement at bci 9 {replacedNodeClass: ValuePhi, canonicalNodeClass: Constant}
    + Canonicalizer CanonicalReplacement at bci 6 {replacedNodeClass: Pi, canonicalNodeClass: Pi}
```

The interpretation here is that the `CanonicalizerPhase` is present in both compilations. However, it performed 2
canonical replacements in the 1st compilation and one different replacement in the 2nd compilation.

### Inlining-tree matching

The matching between inlining trees is similar to the matching between optimization trees, except with an extra
relabelling operation. Different callsite kinds also make the interpretation a bit more complicated. As a reminder, each
inlining-tree node has one of the following callsite kinds:

- `root` - the compiled root method
- `inlined` - an inlined method
- `direct` - a direct method invocation, which was not inlined and not deleted
- `indirect` - an indirect method invocation, which was not inlined and not deleted
- `deleted` - a deleted method invocation
- `devirtualized` - an indirect method invocation that was devirtualized to at least one direct call and then deleted

The result of 2 matched inlining trees is again a delta tree. Each node in the delta tree is an inlining tree node
paired with an edit operation. Edit operations include insertion, deletion, relabelling, and identity. The kind of
edit operation is indicated by the prefix of the node.

- prefix `.` = this inlining tree node is present and unchanged in both compilations (identity)
- prefix `-` = this inlining tree node is present in the 1st compilation but absent in the 2nd compilation (deletion)
- prefix `+` = this inlining tree node is absent in the 1st compilation but present in the 2nd compilation (insertion)
- prefix `*` = this inlining tree node is present in both compilations but the *callsite kind* is different
  (relabelling)

For instance, consider a non-root method in a compilation like `(direct) java.lang.String.equals(Object) at bci 44`.
This means that there is an invocation of the `equals` method in the target machine code, which is a direct invocation (
there is no dynamic dispatch). If the method was inlined by the inliner, it would appear
as `(inlined) java.lang.String.equals(Object) at bci 44`. If an optimization phase completely removed the call to
the `equals` method, it would appear as `(deleted) java.lang.String.equals(Object) at bci 44`

It is also possible that although a callsite is present in a compilation of some root method, it may not be present in a
different compilation of the same method. Note that this is different from the callsite being deleted. In our example,
the callsite `java.lang.String.equals(Object)` would disappear from the inlining tree if its caller was not inlined. If
a call is present in one of the compilations but absent in the other, the matching algorithm reports a difference with
the prefix `-` or `+`. If a call is present in both compilations, the prefix is `.` if the callsite kind is the same
or `*` otherwise.

Some examples of operations in the inlining tree are below.

- `. (inlined) someMethod()` - the callsite is inlined in both compilations
- `. (direct) someMethod()` - there is a direct invocation in both compilations
- `. (deleted) someMethod()` - the callsite is deleted in both compilations
- `* (inlined -> direct) someMethod()` - the callsite is inlined in the 1st compilation but there is a direct invocation
  in the 2nd compilation
- `* (direct -> deleted) someMethod()` - there is a direct invocation in the 1st compilation but the callsite is deleted
  in the 2nd compilation
- `- (inlined) someMethod()` - the callsite is inlined in the 1st compilation but absent in the 2nd compilation
- `+ (direct) someMethod()` - the callsite is absent in the 1st compilation but there is a direct invocation in the 2nd
  compilation
- `+ (deleted) someMethod()` - the callsite is absent in the 1st compilation and it is deleted in the 2nd compilation

### Compilation fragments

A compilation fragment is created from a compilation unit's inlining tree and optimization tree. A subtree of the
inlining tree becomes the inlining tree of the newly created fragment. The optimization tree of the fragment is created
from a subgraph of the compilation unit's optimization tree. This enables the tool to compare a fragment of a
compilation unit with a different compilation unit.

Consider the following scenario. Method `a()` calls an important method `b()`. In experiment 1, method `a()` is compiled
and `b()` is inlined. In experiment 2, only method `b()` is compiled.

 ```
Method a() is hot only in experiment 1
    Compilation unit X
        Inlining tree
            (root) a()
                ...
                    ...
                    (inlined) b()
                        ...
                ...
Method b() is hot only in experiment 2
    Compilation unit Y
        Inlining tree
            (root) b()
                ...
```

We would like to compare the hot compilation of `b()` with the hot compilation of `a()`, which encompasses `b()`. Thus,
the idea is to take only the `b()` part of the `a()` compilation. The `b()` part of `a()` is a compilation
fragment.

```
Method b()
    Compilation fragment X#1 in experiment 1
        Inlining tree
            (root) b()
                ...
    Compilation unit Y in experiment 2
        Inlining tree
            (root) b()
                ...
```

The compilation fragment of `a()` can be compared with the compilation unit of `b()` directly.

To illustrate how the optimization tree of a fragment is constructed, consider the following compilation unit of `a()`.

```
Compilation unit X
    Inlining tree
        (root) a()
            (inlined) b() at bci 1
                (inlined) d() at bci 3
            (inlined) c() at bci 2
    Optimization tree
         RootPhase
             Tier1
                 Optimization1 at null
                 Optimization2 at {a(): 5}
                 Optimization3 at {b(): 2, a(): 1}
             Tier2
                 Optimization4 at {d(): 1, b(): 3, a(): 1}
                 Optimization5 at {c(): 4, a(): 2}
                 Optimization6 at {b(): 4, a(): 1}
             Tier3
```

The fragment rooted in `b()` is visualized below. The optimization tree of a fragment always contains all optimization
phases from its original compilation unit. Individual optimizations are kept only if their positions are in the subtree
of `b()`. Notice that the positions of the optimizations in the fragment are corrected, so that they are relative
to `b()`.

```
Compilation fragment X#1
    Inlining tree
         (root) b()
             (inlined) d() at bci 3
    Optimization tree
         RootPhase
             Tier1
                 Optimization3 at {b(): 2}
             Tier2
                 Optimization4 at {d(): 1, b(): 3}
                 Optimization6 at {b(): 4}
             Tier3
```

Proftool provides execution data on the granularity of compilation units. For that reason, the reported execution
fraction of a compilation fragment is inherited from its parent compilation unit.

A fragment is defined by a parent compilation unit and a non-root node from the compilation unit's inlining tree.
The inlining-tree node becomes the root node of the fragment. The tool creates compilation fragments for all hot
inlinees in all hot compilation units. We have described how hot compilation units are determined in a previous section.
An inlinee is considered hot iff there exists a hot compilation unit of the inlined method in any of the experiments.
