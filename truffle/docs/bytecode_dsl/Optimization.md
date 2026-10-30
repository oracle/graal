# Optimization

Bytecode interpreters commonly employ a variety of optimizations to achieve better performance.
This section discusses how to employ these optimizations in Bytecode DSL interpreters.

## Quickening

[Quickening](https://dl.acm.org/doi/10.1145/1869631.1869633) is a technique that rewrites an instruction with a specialized version that requires less work.
The Bytecode DSL supports quickened instructions that handle a subset of the specializations defined by an operation.
At the moment, these operation quickenings can only be specified manually using [`@ForceQuickening`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/ForceQuickening.java).
In the future, [tracing](#tracing) will be used to automatically infer useful quickenings.

Consider the `Add` operation below as an example.
```java
@Operation
static final class Add {
    @Specialization
    @ForceQuickening("numeric")
    public static long addInts(int lhs, int rhs) {
        return lhs + rhs;
    }
    @Specialization
    @ForceQuickening("numeric")
    public static long addLongs(long lhs, long rhs) {
        return lhs + rhs;
    }
    @Specialization
    @TruffleBoundary
    public static String addStrings(String lhs, String rhs) {
        return lhs + rhs;
    }
}
```
As usual, the operation generates a base instruction that handles all three specializations.
However, the `@ForceQuickening` annotation directs the DSL to generate a quickened instruction that supports only `addInts` and `addLongs`.
The quickened instruction may perform fewer type checks and specialization state checks, have a smaller handler method, and may avoid primitive boxing (discussed [below](#boxing-elimination)).

During execution, if the operation specializes to `addInts` or `addLongs`, it can optimistically rewrite the instruction to the quickened instruction, and then the interpreter will execute the quickened instruction instead.
If later the operation re-specializes and activates a specialization not handled by the quickening, it can rewrite back to the generic instruction.

## Boxing elimination

A major source of overhead in interpreted code (for both Truffle AST and bytecode interpreters) is boxing.
By default, values are passed between operations as objects, which forces primitive values to be boxed up.
Often, the boxed value is subsequently unboxed when it gets consumed.

Boxing elimination (BE) avoids these unnecessary boxing steps.
The interpreter can speculatively rewrite bytecode instructions to specialized instructions that pass primitive values whenever possible.
Boxing elimination can also improve compiled performance, because Graal is not always able to remove box-unbox sequences during compilation.

To enable BE, specify a set of `boxingEliminationTypes` on the [`@GenerateBytecode`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/GenerateBytecode.java) annotation. For example, the following configuration

```java
@GenerateBytecode(
    ...
    boxingEliminationTypes = {int.class, long.class}
)
```

will generate an interpreter that attempts to avoid boxing for `int` and `long` values.

### How it works

*This section describes implementation details that are subject to change.*

Boxing elimination is implemented using quickening.
When BE is enabled, the code generator creates quickenings for instructions that may consume or produce values of the `boxingEliminationTypes`.
For example, a `load.constant$int` will push a primitive `int` value instead of an `Object`.

Simply quickening an instruction when it supports an unboxed representation is insufficient: if an instruction can arbitrarily change its output representation, the instruction that consumes its result must handle arbitrary representations, which would not optimize well.
Instead, BE relies on coordination between the _producing_ and _consuming_ instructions.
When a consuming instruction specializes itself, if it has the option to accept an unboxed input, it checks whether the producing instruction can also be quickened to produce an unboxed output.
Only when both instructions can agree on the representation will BE quickening take place.
To support this coordination, consuming instructions are encoded with indices that track BE-able producing instructions.

Since quickenings are speculative, they are carefully written to handle unexpected behaviour.
For example, if a quickening assumes its input will be `int`, it must still handle the case where it encounters a different type, usually by re-quickening itself to a more general implementation.
Importantly, an instruction should not transition indefinitely between different quickenings to avoid deoptimization cycles, so BE quickening will typically fall back on a generic `Object` representation.

#### Local boxing elimination

Because locals support multiple stores and loads, they do not share the same consumer-producer relationship described above.
Instead, a local's type is tracked using a _tag_ profile, which is updated whenever a different type is stored into it.
The cached tag should eventually stabilize, and all store/load instructions should converge on quickenings that use that representation.

For example, suppose we have a fresh unprofiled local.
If `store.local` encounters an integer value, it will update the local tag to `int` and quicken itself to store `int`.
Each subsequent store checks the tag before writing; if the value is incompatible with the tag (say, a `float`), it generalizes the tag to `Object` (giving up on BE) and quickens itself to store `Object.`
Each `load.local` instruction uses the tag to rewrite itself to a quickening that speculatively reads the given representation.

Usually only local stores affect the tag profile for a local.
However, if a [default local value or illegal local exception](UserGuide.md#uninitialized-and-cleared-locals) is declared, loading an uninitialized or cleared local can implicitly generalize the tag to `Object`.
This disables BE for the local, but it is necessary to prevent speculative load instructions from causing deoptimization cycles.
Note that a clear itself does not force an `Object` tag; it is only a subsequent load that can generalize the tag to `Object`.


## Superinstructions

**Note: Superinstructions are not yet supported**.

[Superinstructions](https://dl.acm.org/doi/abs/10.1145/1059579.1059583) combine common sequences of instructions together into single instructions.
Using superinstructions can reduce the overhead of instruction dispatch, and it can enable the host compiler to perform optimizations across the instructions (e.g., eliding a stack push for a value that is subsequently popped).

In the future, [tracing](#tracing) will be able to automatically infer useful superinstructions.


## Tracing

**Note: Tracing is not yet supported**.

Determining which instructions are worth optimizing (via quickening or superinstructions) typically requires manual profiling and benchmarking.
In the future, the Bytecode DSL will automatically infer optimization opportunities by tracing the execution of a representative corpus of code.

<!--

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


-->
