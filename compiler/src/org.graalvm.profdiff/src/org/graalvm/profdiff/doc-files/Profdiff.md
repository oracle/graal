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

There are several options accepted by all `profdiff` commands. None of them are mandatory, and they are all set to
default values which are suitable for quick identification of differences. Run `mx profdiff help` to view the
defaults. Note that binary options expect the literal `true` or `false`, e.g. `--long-bci=true`.

The options `--hot-min-limit`, `--hot-max-limit`, and `--hot-percentile` are parameters for the algorithm that selects
hot compilations. This is relevant only when proftool data is provided. `--hot-min-limit` and `--hot-max-limit` are hard
upper and lower bounds, respectively, on the number of hot compilations. `--hot-percentile` is the percentile of the
execution period taken up by hot compilations. Read the section about hot compilations for more information.

The option `--optimization-context-tree` enables the optimization-context tree. The optimization-context tree visualizes
optimizations in their inlining context and replaces the inlining and optimization tree. This is not enabled by default.
Read the separate section about the optimization-context tree to learn more.

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
include the canonicalizer, dead code elimination, and inlining phases. This is enabled by default.

If `--prune-identities` is enabled, only the differences with context between two
optimization/inlining/optimization-context trees are displayed, i.e., the identities are pruned. This is enabled by
default.

The option `--create-fragments` controls the creation of compilation fragments. This is enabled by default. Read the
section about compilation fragments to learn more.

## Hot compilation units

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
by default when `--long-bci` is not enabled.

However, in the presence of inlining, the bci is relative to the inlined method. When `--long-bci` is enabled,
the path from an inlined method to the root including bytecode indices of callsites is displayed.

For example, in the compilation of `String#equals`, we might have the following optimization:

```
Canonicalizer UnusedNodeRemoval at bci 9
```

We can run the program with `--long-bci=true` to see that the bci 9 is relative to the inlined `StringLatin1#equals`
method, which is invoked by the root method at bci 44.

```
Canonicalizer UnusedNodeRemoval at bci {java.lang.StringLatin1.equals(byte[], byte[]): 9,
                                        java.lang.String.equals(Object): 44}
```

Perhaps a better way to visualize the position of an optimization is to enable the optimization-context tree. Read the
section about the optimization-context tree to learn more.

## Abstract methods

Indirect calls, e.g. virtual method calls or a calls through an interface, appear in the log as calls to abstract
methods. An indirect call cannot be directly inlined. Therefore, profdiff marks calls known to be indirect
as `(abstract)`.

Consider a call to the abstract method `java.util.Iterator.next()`.

```
(abstract) java.util.Iterator.next() at bci 19
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

The compiler may devirtualize and inline the call if the there is only one receiver. As an example, the receiver of the
call to `java.lang.CharSequence.length()` was always a `String`. Thus, the compiler could inline it
as `java.lang.String.length()` and also inline the call to `java.lang.String.coder()` inside the method.

```
(abstract) java.lang.CharSequence.length() at bci 10
    |_ receiver-type profile
            100.00% java.lang.String -> java.lang.String.length()
    java.lang.String.coder() at bci 6
```

## Optimization-context tree

An optimization-context tree is an inlining tree extended with optimizations placed in their method context.
Optimization-context trees make it easier to attribute optimizations to inlining decisions. However, the structure of
the optimization tree is lost. The feature is enabled using the option `--optimization-context-tree`, and is compatible
with each command, e.g., `mx profdiff --optimization-context-tree=true report scrabble_log`.

As an instance, consider the inlining and optimization tree below:

```
A compilation unit of the method a()
    Inlining tree
        a() at bci -1
            b() at bci 1
                d() at bci 3
            c() at bci 2
    Optimization tree
        RootPhase
            SomeOptimizationPhase
                SomeOptimization OptimizationA at bci {a(): 3}
                SomeOptimization OptimizationB at bci {b(): 4, a(): 1}
                SomeOptimization OptimizationC at bci {c(): 5, a(): 2}
                SomeOptimization OptimizationD at bci {d(): 6, b(): 3, a(): 1}
```

By combining the trees, we obtain the following optimization-context tree:

```
A compilation unit of the method a()
    Optimization-context tree
        a() at bci -1
            SomeOptimization OptimizationA at bci 3
            b() at bci 1
                SomeOptimization OptimizationB at bci 4
                d() at bci 3
                    SomeOptimization OptimizationD at bci 6
            c() at bci 2
              SomeOptimization OptimizationC at bci 5
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

If `--diff-compilations` is enabled, all pairs of *hot* compilations are diffed (in our case, there is only one pair).
Otherwise, hot compilations are printed without any diffing.

### Optimization tree matching

The tool displays the diff of optimization trees for each pair of hot compilations. The diff of 2 optimization trees is
displayed in the form of a delta tree. Each node in the delta tree is an optimization phase/individual optimization
paired with an edit operation. Edit operations include insertion, deletion, and identity. The kind of the edit operation
is displayed as the prefix of the node.

- prefix `.` = this phase/optimization is present and unchanged in both compilations (identity)
- prefix `-` = this phase/optimization is present in the 1st compilation but absent in the 2nd compilation (deletion)
- prefix `+` = this phase/optimization is absent in the 1st compilation but present in the 2nd compilation (insertion)

Consider the following example:

```
Method java.util.stream.ReduceOps$ReduceOp.evaluateSequential(PipelineHelper, Spliterator)
    ...
    Compilation 9068 (19.74% of Graal execution, 11.64% of total) in experiment 1 vs compilation 12622 (16.24% of Graal
    execution, 9.74% of total) in experiment 2
        . RootPhase
            . Parsing
                . GraphBuilderPhase
                . DeadCodeEliminationPhase
            . HighTier
                . CanonicalizerPhase
                . InliningPhase
                  ...
```

We can see a few phase names with the prefix `.`, which means the phases present in both compilations. Further down, we
can observe something like this:

```
. CanonicalizerPhase
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

- prefix `.` = this inlining tree node is present and unchanged in both compilations (identity)
- prefix `-` = this inlining tree node is present in the 1st compilation but absent in the 2nd compilation (deletion)
- prefix `+` = this inlining tree node is absent in the 1st compilation but present in the 2nd compilation (insertion)
- prefix `*` = this inlining tree node is present int both compilations but the inlining decisions are different
  (relabelling)

Provided that there 2 kinds of inlining tree nodes with respect to the inlining decision (inlined and not inlined),
there are 2 * 4 cases to be interpreted.

For instance, consider a non-root method in a compilation like `java.lang.String.equals(Object) at bci 44`. After being
evaluated by the inlining algorithm, it would appear in the inlining tree either as
`java.lang.String.equals(Object) at bci 44` (after being inlined)
or `(not inlined) java.lang.String.equals(Object) at bci 44`. It is also possible that it would not be evaluated at all,
which is the case when its caller is not expanded by the inlining algorithm. In that situation, there is no node for
the method in the inlining tree.

Therefore, all possible combinations in the delta tree are:

- `. java.lang.String.equals(Object) at bci 44` = the method was evaluated and inlined in both compilations
- `. (not inlined) java.lang.String.equals(Object) at bci 44` = the method was evaluated and not inlined in both
  compilations
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

### Compilation fragments

A compilation fragment is a kind of compilation, which is defined as a part of a compilation unit. This enables the tool
to compare a part of a compilation unit with a different compilation unit (or with another part of a compilation unit).

Consider the following scenario. A method `a()` calls an important method `b()`. In experiment 1, the method `a()` is
compiled and `b()` is inlined. In experiment 2, only the method `b()` is compiled.

 ```
Method a() is hot only in experiment 1
    Compilation unit X
        a()
            ...
                ...
                b()
                    ...
                ...
Method b() is hot only in experiment 2
    Compilation unit Y
        b()
            ...
```

We would like to compare the hot compilation of `b()` with the hot compilation of `a()`, which encompasses `b()`. Thus,
the idea is to take only the `b()` part of the `a()` compilation. We denote the `b()` part of `a()` a compilation
fragment.

```
Method b()
    Compilation fragment X#1
        b()
          ...
    Compilation unit Y
        b()
            ...
```

The compilation fragment of `a()` can be compared with the compilation unit of `b()` directly.

#### Fragment implementation details

The following algorithm finds the parts of compilation units suitable for fragment creation.

```
def find_fragments():
    for each method M in the experiment:
        for each hot compilation unit CU of the method M:
            for each inlined method I in CU:
                if (1) the path to I in CU is unique
                   (2) and there is not any hot compilation unit of M in the other experiment
                           or there exists a hot compilation unit of M in the other experiment
                                           where I is not inlined:
                   create a fragment from CU rooted in I
```

The first condition (1) ensures that optimizations can be properly attributed to the fragments. Consider the example
below:

```
A compilation unit of the method a()
    Inlining tree
        a() at bci -1
            b() at bci 1
                c() at bci 3
                c() at bci 3
            c() at bci 2
    Optimization tree
        RootPhase
            SomePhase SomeOptimization at bci {c(): 5, b(): 3, a(): 1}
```

The method `b()` has 2 inlined callees `c() at bci 3`, which means they are not reachable via a unique path. This is a
side effect of code duplication. It is not clear to which callee `c() at bci 3` the optimization from the example
belongs.

The second condition (2) reduces the set of created fragments to only those that are likely useful.

A fragment is created from the parent compilation unit's inlining tree and optimization tree. A subtree of the inlining
tree becomes the inlining tree of the newly created fragment. The optimization tree of the fragment is created by
cloning all internal nodes of the compilation unit's optimization tree and cloning individual optimizations, whose
position is in the fragment's inlining subtree.

Proftool provides execution data on the granularity of compilation units. For that reason, the reported execution
fraction of a compilation fragment is inherited from its parent compilation unit.
