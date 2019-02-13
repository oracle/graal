This documents describes options available in Graal that are commonly helpful.
It complements the detailed information in [Debugging](Debugging.md) which should be read first.

## Graal scenarios

Add `-Dgraal.Timers=CompilationTime` to measure the time spent in compilation.

Add `-XX:JVMCIThreads=1` to have a single JVMCI compiler thread. This simplifies
debugging in an IDE by ensuring a breakpoint in Graal code is hit by a single thread.

Use `-XX:+BootstrapJVMCI` to stress-test the compiler without having to specify an application.
To force *all* bootstrap compilations to go through Graal, add `-XX:-TieredCompilation` as well.

Use `-Dgraal.Dump= -Dgraal.MethodFilter=MyClass.someMethod` to see the compiler graphs in
IGV when compiling `MyClass.someMethod`. If you want the graphs sent immediately to IGV, ensure it is running (`mx igv`).
To have the graphs written to disk instead (e.g., to share), add `-Dgraal.PrintIdealGraphFile=true`.
To disable the dumping of LIR, register allocation and code generation info to `*.cfg` files readable by the C1Visualizer,
add `-Dgraal.PrintBackendCFG=false`.

## GraalTruffle scenarios

Add `-Dgraaldebug.timer.PartialEvaluationTime=true` to measure the time spent in partial evaluation.
