# Comparing optimization trees

The `mx profdiff` tool can compare optimization decisions between 2 runs of one application. The profile of the
application is captured by [proftool](https://github.com/graalvm/mx/blob/master/README-proftool.md). The data is then
used to identify the hottest compilation units. `mx profdiff` pairs hot compilation units across the 2 runs and
displays the differences between their optimization trees.

## Prepare experiment data

First, we choose a benchmark or an application and run it with optimization log, node source positions and proftool.
Node source positions allow us to correlate individual optimizations with a position in the bytecode and therefore
compare the optimizations for equality.

```sh
mx benchmark renaissance:scrabble --tracker none -- --profiler proftool -Dgraal.OptimizationLog=Directory -Dgraal.OptimizationLogPath=$(pwd)/scrabble_log
```

We do not need to specify `-Dgraal.TrackNodeSourcePositions=true`, because it is inserted implicitly by
the `mx benchmark` infrastructure. The dump path is explicitly specified as an absolute path to avoid surprises. If the
subject to experiment is not a benchmark supported by `mx`, use `mx profrecord` as per
the [proftool documentation](https://github.com/graalvm/mx/blob/master/README-proftool.md).

The optimization log can be found in the `./scrabble_log` directory and the profile is in a directory
like `./proftool_scrabble_2022-07-05_140847`.

Before the proftool output can be further processed, it must be converted to a single JSON file.

```sh
mx profjson -E ./proftool_scrabble_2022-07-05_140847 -o scrabble_prof.json
```

Now, we could run the experiment again with a different compiler revision. It is however sufficient to run the same
experiment again and get a bit different result, which is caused by the inherent nondeterminism of a JIT compiler.

```sh
mx benchmark renaissance:scrabble --tracker none -- --profiler proftool -Dgraal.OptimizationLog=Directory -Dgraal.OptimizationLogPath=$(pwd)/scrabble_log2
```

Again, convert the profile to JSON:

```sh
mx profjson -E ./proftool_scrabble_2022-07-05_141855 -o scrabble_prof2.json
```

## Compare with profdiff

The `mx profdiff` takes the JSON profiles and optimization logs of 2 experiments and diffs the optimizations performed
in their hot methods. Run `mx profdiff` without arguments to display its usage.

```
The argument 'proftool_output_1' is required.
Usage: mx profdiff [options] proftool_output_1 optimization_log_1 proftool_output_2 optimization_log_2

Compares the optimization log of hot compilation units of two experiments.

Options:
  --hot-min-limit      the minimum number of compilation units to mark as hot
  --hot-max-limit      the maximum number of compilation units to mark as hot
  --hot-percentile     the percentile of the execution period that is spent executing hot compilation units
  --verbosity          the verbosity level of the diff, accepted values are [default, high, max]
```

Use the tool to diff our toy experiments:

```sh
mx profdiff --hot-max-limit 1 ./scrabble_prof.json ./scrabble_log ./scrabble_prof2.json ./scrabble_log2
```

At the beginning of the output, we get a summary of the input data:

```
Experiment 1 with execution ID 21168
    Collected optimization logs for 653 methods
    Collected proftool data for 3043 methods
    Graal-compiled methods account for 59.00% of execution
    1 hot methods account for 11.64% of execution

Experiment 2 with execution ID 23183
    Collected optimization logs for 696 methods
    Collected proftool data for 3069 methods
    Graal-compiled methods account for 59.96% of execution
    1 hot methods account for 9.74% of execution
```

### Hot compilations

Proftool collects samples for all executed methods, which is likely more than the number of graal-compiled methods.
The profile determines the fraction of the time spent executing graal-compiled methods. The tool marks some of the
graal-compiled methods with the highest timeshare as *hot*. This is a different term than "hot" in the context of
HotSpot. More precisely, the tool marks some of the method *compilations* as hot, whereas all of the methods we have
available (rather than their compilations) were considered hot in HotSpot's terminology.

The algorithm to mark hot methods works as follows:

- for each experiment
  - sort all graal-compiled methods (compilations) by their execution period (highest first)
  - mark the first 1 compilations as hot (the number can be adjusted by the `--hot-min-limit` parameter)
  - keep marking the compilations as hot while the total timeshare of hot compilations is less than
    90% (`--hot-percentile`) of total graal-compiled method execution and the number of hot compilations is less than
    10 (`--hot-max-limit`)

### Method matching

The result after hot method marking is a set of hot compilations for each experiment. Some of these may be compilations
of the same method. The tool can figure this out by grouping the compilations by their compilation method name.

From the tool's output:

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

We can see that the method compilation
name `java.util.stream.ReduceOps$ReduceOp.evaluateSequential(PipelineHelper, Spliterator)`corresponds to 3 compilations
in the 1st experiment and 2 compilations in the 2nd experiment. Some of these compilations were marked as hot.

In this case, we would like to show the diff between the 2 hot compilations, even though there are 3 * 2 possible pairs
of compilations. In the general case, the 1st hottest compilation from the 1st experiment is diffed with the 1st hottest
compilation from the 2nd experiment, the 2nd hottest is diffed with the 2nd hottest etc. When a hot compilation does not
have a pair in the other experiment, the whole optimization tree is simply dumped.

### Optimization tree diff

The tool displays the diff of optimization trees for each pair of hot compilations. The diff of the optimization tree is
reminiscent of the optimization tree presented in a previous section. However, each node in the tree (i.e. each
optimization phase or individual optimization) has an additional prefix with a meaning:

- prefix `-` = this node is present in the 1st compilation but absent in the 2nd compilation
- prefix `+` = this node is absent in the 1st compilation but present in the 2nd compilation
- prefix `*` = this node is present in both compilations but was changed (e.g. the name of a phase was changed)
- (no prefix) = this node is present and unchanged in both compilations

Continuing with the example:

```
Method java.util.stream.ReduceOps$ReduceOp.evaluateSequential(PipelineHelper, Spliterator)
    ...
    Compilation 9068 (19.74% of Graal execution, 11.64% of total) in experiment 1 vs compilation 12622 (16.24% of Graal execution, 9.74% of total) in experiment 2
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

Note that the reported byte code indices are relative to the method where the code comes from, which might not be the
root method in the presence of inlining. Run the diff again with `--verbosity high` to see the whole stacks of inlined
methods. Use `--verbosity max` to display optimization trees without diffing.
