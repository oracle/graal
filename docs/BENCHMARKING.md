# Benchmarking

Sulong strives to provide the best possible peak performance for
long-running server processes. We thus do not measure the startup, or
warm-up cost.

## Harness

To measure the peak performance, you have to write a harness or use an
existing one (e.g. [ReBench](https://github.com/smarr/ReBench)). The harness
has to run warm-up iterations of the benchmark until all functions have
been compiled (you can check with `-Dgraal.TraceTruffleCompilation=true`,
see below). Then, you can start to record the iterations that are used to
compute the the benchmark score.

You can have a look at
[Bringing Low-Level Languages to the JVM: Efficient Execution of LLVM IR on Truffle](http://conf.researchr.org/event/vmil2016/vmil2016-bringing-low-level-languages-to-the-jvm-efficient-execution-of-llvm-ir-on-truffle)
to see how we configured our benchmark harness for C and Fortran programs.

## Commands

To execute a C benchmark `test.c` use the following command:

```
mx suoptbench test.c -Dsulong.ExecutionCount=1
```

The command first compiles the C file to LLVM IR. Then, it uses LLVM's
`opt` tool to apply selected static optimizations on the LLVM IR file.
Afterwards, it uses Sulong to execute the bitcode file. To get more detailed
information about the performed steps, you can use the `mx -v` flag
when executing the command.

If you do not want `opt` to apply optimizations, you can also use
`mx subench` instead of `mx suoptbench` which just compiles and executes
the benchmark.

## Options

* `-Dgraal.TraceTruffleCompilation=true`: prints when Graal compiles a
  Truffle function
* `-Dgraal.TruffleBackgroundCompilation=false`: turns off background
  compilation to prevent that the benchmark exits before it is compiled,
  which can happen for short-running benchmarks
* `-Dgraal.TruffleTimeThreshold=1000000`: specifies up until which time
  Graal is used to compile a function. If the value is too low, then
  some parts of the program might get never compiled.
* `-Dsulong.ExecutionCount=<number>`: execute the programm the specified
  number of times, which is useful when a benchmark should be executed
  without a test harness

There are also other Sulong (see `mx su-options`) and Graal options
relevant for benchmarking. Sulong's mx benchmark commands mentioned above
already set these options.

## Graal compiler

Truffle uses Graal to compile Truffle ASTs to machine code. To get the best
performance and a stable version, use the latest Graal compiler that is available
on [OTN](http://www.oracle.com/technetwork/oracle-labs/program-languages/downloads/index.html)
by downloading it and setting `JAVA_HOME` to its path in `mx.sulong/env`.

## Common pitfalls

### The benchmark does not get compiled

A common reason that a benchmark is not compiled is that it is not executed
often enough. If `-Dgraal.TraceTruffleCompilation=true` does not print
compiled functions, then try to run more iterations of the benchmark. The
benchmark has to be called as a function, since Sulong does not yet support
On-Stack Replacement (OSR).

### Profiling and Invalidation

Sulong performs profiling and applies dynamic optimizations, such as speculating
that loaded memory values are constant. When benchmarking, you have to use
the same input for warm-up and the measured benchmark runs. If you use
different input data, then the benchmark is compiled under speculations
given by the warm-up data, which results in invalidations in the measuring
runs, i.e., the compiled code is again discarded and execution continues
in the interpreter, until the benchmark has been executed often enough to
get recompiled.

## Internal Benchmark Suite

If Sulong's internal benchmark suite is available, you can run the benchmark
suite in different configurations:

### Sulong

`mx benchmark csuite:* -- --native-vm sulong --jvm server --jvm-config graal-core`

### e.g. Clang O3

`mx -v benchmark csuite:* -- --native-vm clang --native-vm-config O3`