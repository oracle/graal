# Optimizing Truffle Interpreters

This document is about tools for optimizing or debugging Truffle interpreters
for peak temporal performance.

## Strategy

1. Run with a profiler to sample the application and identify responsible compilation units. Use a sampling delay (`--cpusampler.Delay=MILLISECONDS`) to only profile after warmup.

2. Understand what is being compiled and look for deoptimizations. Methods that are listed to run mostly in the interpreter likely have a problem with deoptimization.

3. Simplify the code as much as possible where it still shows the performance problem.

4. Enable performance warnings and list boundary calls.

4. Dump the Graal graph of the responsible compilation unit and look at the phase `After TruffleTier`.
4. Look at the Graal graphs at the phases `After TruffleTier` and `After PartialEscape` and check it is what you'd expect.
   If there are nodes there you don't want to be there, think about how to guard against including them.
   If there are more complex nodes there than you want, think about how to add specialisations that generate simpler code.
   If there are nodes you think should be there in a benchmark that aren't, think about how to make values dynamic so they aren't optimized away.

5. Search for `Invoke` nodes in the Graal IR. `Invoke` nodes that are not representing guest language calls should be specialized away. This may not be always possible, e.g., if the method does I/O.

6. Search for control flow splits (red lines) and investigate whether they result from control flow caused by the guest application or are just artefacts from the language implementation. The latter should be avoided if possible.

7. Search for indirections in linear code (`Load` and `LoadIndexed`) and try to minimise the code. The less code that is on the hot-path the better.

## Profiling

See [`profiling.md`](Profiling.md).

## Truffle compiler options

See https://chriswhocodes.com/graal_options_graal_ce_19.html for a list of Truffle and Graal compiler options.
You can also list them from the command line with any language launcher:
```
$ my-language --jvm --vm.XX:+JVMCIPrintProperties
```

Also see https://github.com/oracle/graal/blob/master/compiler/docs/Truffle.md
for more details about instrumenting branches and boundaries.

#### Observing what is being compiled

`--vm.Dgraal.TraceTruffleCompilation=true` prints a line each time a method is compiled.

`--vm.Dgraal.TraceTruffleCompilationDetails=true` also prints a line when compilation is queued, starts or completes.

`--vm.Dgraal.TraceTruffleCompilationAST=true` prints the Truffle AST for each compilation.

`--vm.Dgraal.TraceTruffleCompilationCallTree=true` prints a guest-language call graph for each compilation.

`--vm.Dgraal.TraceTruffleInlining=true` prints guest-language inlining decisions for each compilation.

`--vm.Dgraal.TraceTruffleSplitting=true` prints guest-language splitting decisions for each compilation.

`--vm.Dgraal.TraceTruffleTransferToInterpreter=true` prints on transfer to interpreter.

`--vm.Dgraal.TraceTrufflePerformanceWarnings=true` prints code which may not be ideal for performance.

`--vm.Dgraal.TruffleCompilationStatistics=true` prints at the end of the process lots of information on what Truffle has compiled and how long it took and where.

`--vm.Dgraal.TruffleCompilationStatisticDetails=true` prints more information.

`--vm.Dgraal.PrintTruffleExpansionHistogram=true` prints at the end of each compilation a histogram of AST interpreter method calls.

`--vm.Dgraal.TruffleInstrumentBoundaries=true` prints at the end of the process information about runtime calls (`@TruffleBoundary`) made from compiled code. These cause objects to escape, are black-boxes to further optimization, and should generally be minimised.

`--vm.XX:+TraceDeoptimization` prints deoptimization events, whether code compiled by Truffle or conventional compilers.

`--vm.XX:+TraceDeoptimizationDetails` prints more information (only available for native images).

#### Controlling what is compiled

To make best use of the former options, limit what is compiled to the methods that you are interested in.

`--vm.Dgraal.TruffleCompileOnly=foo` restricts compilation to methods with `foo` in their name. Use this in combination with returning a value or taking parameters to avoid code being compiled away.

`--vm.Dgraal.TruffleCompileImmediately=true` compiles methods as soon as they are run.

`--vm.Dgraal.TruffleBackgroundCompilation=false` compiles synchronously, which can simplify things.

`--vm.Dgraal.TruffleFunctionInlining=false` disables inlining which can make code easier to understand.

`--vm.Dgraal.TruffleOSR=false` disables on-stack-replacement (compilation of the bodies of `while` loops for example) which can make code easier to understand.

`--vm.Dgraal.TruffleCompilation=false` turns off Truffle compilation all together.

## HotSpot options

See https://chriswhocodes.com/graal_options_graal_ce_19.html and https://chriswhocodes.com/hotspot_options_jdk8.html.

## IGV

IGV, the *Ideal Graph Visualizer*, is a tool to understand Truffle ASTs and
Graal graphs. It's available from
https://www.oracle.com/technetwork/graalvm/downloads/index.html.

Typical usage is to run with `--vm.Dgraal.Dump=Truffle:1`,
which will show you Truffle ASTs, guest-language call graphs, and the Graal
graphs as they leave the Truffle phase. Files are put into a `graal_dumps`
directory which you should then open in IGV.

Use `--vm.Dgraal.Dump=Truffle:2` to dump Graal graphs between each compiler phase.

## C1 Visualizer

The C1 Visualizer, is a tool to understand the LIR, register allocation, and
code generation stages of Graal. It's available from
http://lafo.ssw.uni-linz.ac.at/c1visualizer/.

Typical usage is `--vm.Dgraal.Dump=:3`. Files are put into a `graal_dumps`
directory which you should then open in the C1 Visualizer.

## Disassembler

`--vm.XX:+UnlockDiagnosticVMOptions --vm.XX:+PrintAssembly` prints assembly
code. You will need to install `hsdis` using `mx hsdis` in `graal/compiler`,
or manually into the current directory from
https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/hsdis/intel/.

Combine with `--vm.XX:TieredStopAtLevel=0` to disable compilation of runtime
routines so that it's easier to find your guest-language method.

Note that you can also look at assembly code in the C1 Visualizer.
