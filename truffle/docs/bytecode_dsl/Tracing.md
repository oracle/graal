# Optimization

Bytecode interpreters commonly employ [quickening](https://dl.acm.org/doi/10.1145/1869631.1869633) and [superinstructions](https://dl.acm.org/doi/abs/10.1145/1059579.1059583) to achieve better interpreted performance. This section discusses how to employ these optimizations in Bytecode DSL interpreters.

## Quickening

**TODO**: talk about how quickening works, how it connects with BE, @ForceQuickening, and tracing

## Superinstructions

**Note: Superinstructions are not yet supported**.

## Tracing

**Note: Tracing is not yet supported**.

Determining which instructions are worth optimizing (via quickening or superinstructions) typically requires manual profiling and benchmarking. 
Bytecode DSL can automatically infer optimization opportunities using *tracing*.

First, the DSL allows you to generate a *tracing interpreter* to collect data about the executed bytecode (e.g., common instruction sequences).
Then, executed on a representative corpus of programs, the interpreter collects tracing data and infers a set of optimization decisions (e.g., "create a superinstruction with instructions X, Y, and Z").
Finally, the interpreter can be rebuilt with these decisions, and the optimized instructions will be automatically included in the generated interpreter.

The following sections describe the tracing process in more detail.

### Step 1: Build the tracing interpreter

Tracing is built around the concept of a *decisions file*.
The decisions file encodes a set of optimization decisions (e.g., quickenings or superinstructions).

To prepare your Bytecode DSL interpreter for tracing, first specify a path for the decisions file using the `decisionsFile = "..."` attribute of the top-level `@GenerateBytecode` annotation.
The path is relative to the current file.
It is recommended to store decisions in a file named `"decisions.json"`.
It is also recommended to check the decisions file in to version control and to update it whenever significant changes to the interpreter specification are made.

**TODO**: does the file need to exist already?


<!-- After it finishes executing a corpus program, the tracing interpreter persists the collected data (encoded as optimization decisions) to disk in the decisions file.
When it traces subsequent corpus programs, the interpreter combines the tracing data; the resulting decisions file comprises tracing metrics from the entire corpus. -->

Then we can recompile the Bytecode DSL interpreter for tracing. This will create a modified version of the interpreter that traces bytecode execution at run time.
To do this, recompile the project with the `truffle.dsl.BytecodeEnableTracing=true` annotation processor flag. This can be done in `mx` using:

```sh
mx build -f -A-Atruffle.dsl.BytecodeEnableTracing=true
```

### Step 2: Collect tracing data

When the tracing interpreter is run on one or more programs (the tracing *corpus*), it collects tracing data that is used to infer optimization decisions.
Though tracing is automated, selecting the corpus should be an intentional process:

* The corpus should be representative of actual code written in the guest language. Ideally, the corpus should not be a suite of micro-benchmarks, but should instead be composed of real-world applications.
* Bytecode DSL will try to optimize for specific patterns found in the corpus. For this reason, if guest language code is typically written in multiple different styles/paradigms, they should all be represented in the corpus.
* In general, Bytecode DSL uses heuristics to make *the corpus* run as best as it can. It infers optimization decisions that may not generalize to other guest programs. You should use external benchmarks (that do not belong to the corpus) to validate the efficacy of the optimization decisions.

**TODO: can we avoid the state file?**

To run the corpus with tracing enabled, you must first create a *state file*, which is used to persist tracing data across executions.
Here, we will store it in `/tmp`:

```
touch /tmp/state.json
```

Then, run the tracing interpreter on each program in the corpus, specifying the state path via the as `engine.BytecodeTracingState` Polyglot option.
Each program in the corpus should be run serially (locking the state file is used to prevent concurrent runs).
Each program may use internally multithreading, but any non-determinism is discouraged, as it may make the optimization decisions non-deterministic as well.

If you want to see a summary of optimization decisions, you can also set the `engine.BytecodeDumpDecisions` Polyglot option to `true`. This will print the resulting decisions to the Polyglot log.

After each program in the corpus is executed, the decisions file specified with `decisionsFile` is automatically updated with the current set of optimization decisions.

### Step 3: Apply optimization decisions

To apply the optimization decisions, simply recompile the interpreter without the tracing enabled. For example, with `mx`, just run:

```sh
mx build -f
```

This will regenerate the interpreter without the tracing calls. Bytecode DSL will take the decisions (stored in the decisions file) into account when generating the bytecode interpreter.

### (Optional) Manually overriding the decisions

In addition to the decisions automatically inferred with tracing, you may wish to manually to specify additional optimization decisions to Bytecode DSL.
The `@GenerateBytecode` annotation has a second attribute, `decisionOverrideFiles`, whereby you can specify additional `json` files with these manually-encoded decisions. The format for the decisions is described below.

#### Decisions file format

**TODO**