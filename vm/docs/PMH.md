# Polyglot Microbench Harness

The *Polyglot Microbench Harness* (PMH) is a simple benchmark description language for writing
microbenchmarks for languages on the Truffle framework.

## Getting Started

The Polyglot Microbench Harness is not shipped with GraalVM by default.

To be able to benchmark a language with the PMH, a GraalVM with the following components needs to be built:
* Polyglot Microbench Harness
* Polybench Launcher
* all languages that should be benchmarked
* optionally Native Image, to benchmark in native mode

For example, this command builds a GraalVM with PMH, Native Image, JS, Ruby, Python, Wasm, Espresso and Sulong:
```
mx --env polybench-ce build
```

To run a microbenchmark, run the following on the built GraalVM:
```
$GRAALVM_HOME/bin/polybench --path=path/to/benchmark.pmh
```

## The PMH Language

A `.pmh` file is a simple description of a polyglot microbenchmark.

The language is kept very simple on purpose. This is not a general purpose programming language, just a
simple language to describe workloads for benchmarking. The only things the language can do is parse
expressions in other languages, and send polyglot interop messages.

### Example

```
# Lines starting with '#' are comments.

# Parse a JS function to benchmark.
func = EVAL "js"
    (function(x) {
        return x + 1;
    })

# Prepare a JS number as argument.
arg = EVAL "js"
    42

# Prepare both func and arg during benchmark setup.
SETUP func arg

# Call the function in the benchmark loop.
run = EXECUTE func arg

# Call the function a million times per iteration, and report the average time in nanoseconds.
MICROBENCH run
    repeat: 1000000
    unit: ns
```

### General Structure

A PMH benchmark consists of two sections, the SETUP section and the MICROBENCH sections.

Both sections consist of a list of declarations, on per line, and end with a "SETUP" or
"MICROBENCH" directive, respectively.

When running the benchmark, first the SETUP section is executed, setting up the benchmark.
This code is only run once, before the first iteration. This is typically used to parse the code
that should be benchmarked, or prepare values to be used as arguments when the computation of
these arguments should be excluded from measurement.

Then the harness will run multiple *iterations* of the code described in the MICROBENCH section,
measuring the benchmark metric for each iteration. For microbenchmarks, typically each measured
iteration consists of a loop that executes the workload multiple times. This way it is possible
to benchmark smaller operations, down to the nanosecond range.

### Declarations

A *declaration* is a single line of the following form:
```
identifier = STATEMENT arg1 arg2 ... argn
```

Declarations can be used later in the file, in other expressions or in the SETUP or MICROBENCH directive.
There are no backwards references allowed. Each declaration can only be used once.

Declarations essentially build an *expression tree* that can be evaluated in the benchmark.

Possible statements are either `EVAL` to evaluate code in another language, or an interop message.
See below for more details.

### SETUP directive

The `SETUP` directive is a single line of the form:
```
SETUP decl1 decl2 ... decln
```

`decl1` to `decln` are identifiers referring to earlier declarations. This is the list of declarations
that should be prepared before the first benchmark iteration.

Declarations after the `SETUP` directive can not refer to earlier declarations, but they can use
any of the prepared values. These values will not be recomputed in each benchmark iteration, and they
can be used more than once. Despite being the same in all iterations, they will *not* look constant
to the compiler.

### MICROBENCH directive

The `MICROBENCH` directive is a single line describing the workload, optionally followed by a list
of options:
```
MICROBENCH workload
    repeat: number
    batchSize: number
    unit: unit
    iterations: number
    warmupIterations: number
```

`workload` is an identifier referring to an earlier declaration. This is the operation that should
be benchmarked in the measurement iterations. The harness makes sure that the result of `workload` must
be computed and at least put in a register for each repetition, regardless of compiler optimizations.

All options have a default value and can thus be omitted:

* `repeat`: Repeat the workload `number` times within a single iteration.
            Defaults to 1.
* `batchSize`: Sometimes it is more convenient to write a loop inside the workload, instead of (or in
               addition to) using `repeat`. In that case, use `batchSize` to report how many operations
               are in a single iteration.
               Defaults to the same value as `repeat`.
* `unit`: Select which time unit to report the result in.
          Possible values are `s`, `ms`, `us` and `ns`.
          Defaults to `ms`.
* `iterations`: Configure how many measurement iterations to run.
                Can be overridden with the `-i` argument to `polybench`.
                Defaults to 30.
* `warmupIterations`: Configure how many iterations to run for warmup before the measurement iterations.
                      Can be overridden with the `-w` argument to `polybench`.
                      Defaults to 20.

### EVAL statement

The `EVAL` statement parses and evaluates source code of another language:

```
identifier = EVAL "languageId"
    indented block...
       ... of source code
    of the other language
```

The `"languageId"` is a literal string and specifies which language to use. The indented block is not
interpreted, it is passed verbatim to the other language. The indentation is determined by the first line.
The code block includes all lines that are indented at least as far as the first line, or more. The
indentation is stripped from the code before passing it to the other language. Beware empty lines! They
must be indented, too, if they should be included.

### Interop message statements

All other statements correspond directly to messages in the `InteropLibrary`. The first argument is always
a declaration specifying the receiver of the message. The other arguments depend on the message.

Currently the following interop messages are supported:

* `EXECUTE receiver arg1 arg2 ... argn`
    Send the `execute` message, with the given arguments. All arguments refer to previous declarations.

* `READ_MEMBER receiver "name"`
    Send the `readMember` message to the receiver. The argument is a literal string.

* `INVOKE_MEMBER receiver "method" arg1 arg2 ... argn`
    Send the `invokeMember` message to the receiver. The "method" argument is a literal string. All other
    arguments refer to previous declarations.
