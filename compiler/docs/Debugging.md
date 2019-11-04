
This pages covers the various mechanisms currently available for debugging the GraalVM compiler.

## IDE Support

All the parts of the GraalVM compiler (henceforth just "compiler") written in Java can be debugged with a standard Java debugger.
While debugging with Eclipse is described here, it should not be too hard to adapt these instructions for another debugger.

The `mx eclipseinit` command not only creates Eclipse project configurations but also creates an
Eclipse launch configuration (in
`mx.compiler/eclipse-launches/compiler-attach-localhost-8000.launch`) that can be used to debug all
compiler code running in the VM.  This launch configuration requires you to start the VM with the `-d`
global option which puts the VM into a state waiting for a remote debugger to attach to port `8000`:

```
mx -d vm -XX:+UseJVMCICompiler -version
Listening for transport dt_socket at address: 8000
```

Note that the `-d` option applies to any command that runs the VM, such as the `mx unittest` command:

```
mx -d vm unittest
Listening for transport dt_socket at address: 8000
```

Once you see the message above, you then run the Eclipse launch configuration:

1. From the main menu bar, select **Run > Debug Configurations...** to open the Debug Configurations dialogue.
2. In the tree of configurations, select the **Remote Java Application > compiler-attach-localhost-8000** entry.
3. Click the **Debug** button to start debugging.

At this point you can set breakpoints and perform all other normal Java debugging actions.

## Logging

In addition to IDE debugging, there is support for *printf* style debugging.
The simplest is to place temporary usages of `System.out` where ever you need them.

For more permanent logging statements, use the `log(...)` methods in
[`DebugContext`](../src/org.graalvm.compiler.debug/src/org/graalvm/compiler/debug/DebugContext.java).
A (nestable) debug scope is entered via one of the `scope(...)` methods in that class. For example:

```java
DebugContext debug = ...;
InstalledCode code = null;
try (Scope s = debug.scope("CodeInstall", method)) {
    code = ...
    debug.log("installed code for %s", method);
} catch (Throwable e) {
    throw debug.handle(e);
}
```

The `debug.log` statement will send output to the console if `CodeInstall` is matched by the
`-Dgraal.Log` option. The matching logic for this option is implemented in
[DebugFilter](../src/org.graalvm.compiler.debug/src/org/graalvm/compiler/debug/DebugFilter.java#L31-L82)
and documented in the
[Dump help message](../src/org.graalvm.compiler.debug/src/org/graalvm/compiler/debug/doc-files/DumpHelp.txt). As
mentioned in the javadoc, the same matching logic also applies to the `-Dgraal.Dump`,
`-Dgraal.Time`, `-Dgraal.Count` and `-Dgraal.TrackMemUse` options.

Since `DebugContext` objects are thread local, they need to be passed around as parameters.  For
convenience, they may be available from objects such as a `Graph` or a `Node` but care needs to be
taken when such objects can be exposed to multiple threads.  There are assertions in `DebugContext`
guarding against use in a thread different from the one in which it was instantiated.


## JVMCI and compiler specific options

JVMCI and GraalVM compiler options are specified by the `jvmci.*` and `graal.*` system properties
respectively. These must be specified on the JVM command line. Modifications to these properties by
application code are not seen by JVMCI and the compiler. A listing of all supported properties can be
obtained with `-XX:+JVMCIPrintProperties`.

## Metrics

The compiler supports metrics in the form of counters, timers and memory trackers.
Each metric has a unique name. Metrics are collected per-compilation.
At shutdown, they are aggregated across all compilations and reported to the console.
This ouput can be redirected to a file via the `-Dgraal.AggregatedMetricsFile` option.

To list the per-compilation metrics, use the `-Dgraal.MetricsFile` option. If not specified,
per-compilation metrics are not reported.

Metrics are represented in the code via fields or variables of type `CounterKey`, `TimerKey` or `MemUseTrackerKey`.
As implied by the `Key` suffix, these objects do not store the value of a metric.
The value is stored in a `DebugContext` object. How to use each of these metrics is described below.

* A `CounterKey` counts some quantity.
For example:
```java
// declaration
private static final CounterKey ByteCodesCompiled = DebugContext.counter("ByteCodesCompiled");
// usage
DebugContext debug = ...;
long compiled = ... ;
ByteCodesCompiled.add(debug, compiled);
```

* A `TimerKey` records the time spent in a scope. For example:
```java
// declaration
private static final TimerKey CompilationTime = DebugContext.timer("CompilationTime");
// usage
DebgContext debug = ...;
try (DebugCloseable scope = CompilationTime.start(debug)) {
    ...
}
// time spent in the above scope will be added to the `CompilationTime` value in `debug`
```
When reporting a timer, two values are reported.
The accumulated time represents the real time spent in the scope.
The flat time is the amount of time spent within the scope minus the time spent in any nested timer scopes.
For example:
```
CompilationTime_Accm=100.0 ms
CompilationTime_Flat=10.0 ms
```

* A `MemUseTrackerKey` tracks the memory allocation in a scope. For example:
```java
// declaration
private static final MemUseTrackerKey CompilationMemory = DebugContext.memUseTracker("CompilationMemory");
// usage
DebgContext debug = ...;
try (DebugCloseable a = CompilationMemory.start(debug)) {
    ...
}
 // the bytes allocated in the above scope will be added to the `CompilationMemory` value in `debug`
```
Like `TimerKey`, an accumulated and flat value is reported for memory usage tracker.
For example:
```
CompilationMemory_Accm=100000 bytes
CompilationMemory_Flat=1000 bytes
```

For both the `-Dgraal.AggregatedMetricsFile` and the `-Dgraal.MetricsFile` option, the output format
is determined by the file name suffix. A `.csv` suffix produces a semi-colon separated format that
is amenable to automatic data processing.

The columns in the `-Dgraal.MetricsFile` CSV output are:
* **compilable**: A textual label such as the fully qualified name of the method being compiled.
For Truffle compilations, this will be a name describing the guest language function/method being compiled.
* **compilable_identity**: The identity hash code of the **compilable**. This is useful when the
label may not uniquely identify the compilation input. For example, when a Java method is in a class
loaded multiple times by different class loaders or is redefined via JVMTI, its label may not change
but its identity will.
* **compilation_nr**: The number of times metrics have been dumped for a compilation of
  **compilable_identity**. This is guaranteed to be asymptotically increasing since dumping of
  per-compilation metrics is serialized on a global lock.
* **compilation_id**: An identifier for the compilation that is highly likely (but not guaranteed)
  to be unique. Note that it may not be purely numeric.
* **metric_name**: The name of the metric being reported.
* **metric_value**: The metric value being reported.
* **metric_unit**: The unit of measurement for the value being reported. This will be the empty string for a counter.

Example `-Dgraal.MetricsFile` output:
```
java.lang.String.hashCode()int;1272077530;0;HotSpotCompilation-95;PhaseNodes_PhaseSuite;1
java.lang.String.hashCode()int;1272077530;0;HotSpotCompilation-95;PhaseNodes_GraphBuilderPhase;1
java.lang.String.hashCode()int;1272077530;0;HotSpotCompilation-95;PhaseCount_GraphBuilderPhase;1
java.lang.String.hashCode()int;1272077530;0;HotSpotCompilation-95;PhaseMemUse_GraphBuilderPhase_Accm;896536
java.lang.String.hashCode()int;1272077530;0;HotSpotCompilation-95;PhaseMemUse_GraphBuilderPhase_Flat;896536
java.lang.String.hashCode()int;1272077530;0;HotSpotCompilation-95;PhaseTime_GraphBuilderPhase_Accm;31095000
java.lang.String.hashCode()int;1272077530;0;HotSpotCompilation-95;PhaseTime_GraphBuilderPhase_Flat;27724000
```

The columns in the `-Dgraal.AggregatedMetricsFile` CSV output are:
* **metric_name**: The name of the metric being reported.
* **metric_value**: The metric value being reported.
* **metric_unit**: The unit of measurement for the value being reported. This will be the empty string for a counter.

Example `-Dgraal.AggregatedMetricsFile` output:
```
AllocationsRemoved;6446;
BackEnd_Accm;7931303;us
BackEnd_Flat;91919;us
DuplicateGraph_Accm;1935356;us
DuplicateGraph_Flat;1935356;us
LIRPhaseMemUse_AllocationStage_Accm;499763160;bytes
LIRPhaseMemUse_AllocationStage_Flat;17354112;bytes
```

Note that `-Dgraal.MetricsFile` produces per-compilation output, not per-method output.
If a method is compiled multiple times, post-processing of the CSV output will be required
to obtain per-method metric values.

## Method filtering

Specifying one of the debug scope options (i.e., `-Dgraal.Log`, `-Dgraal.Dump`, `-Dgraal.Count`,
`-Dgraal.Time`, or `-Dgraal.TrackMemUse`) can generate a lot of output. Typically, you are only
interesting in compiler output related to a single or few methods. In this case, use the
`-Dgraal.MethodFilter` option to specify a method filter. The matching logic for this option is
described in
[MethodFilter](../src/org.graalvm.compiler.debug/src/org/graalvm/compiler/debug/MethodFilter.java#L33-L92)
and documented in the
[MethodFilter help message](../src/org.graalvm.compiler.debug/src/org/graalvm/compiler/debug/doc-files/MethodFilterHelp.txt).

## Metric filtering

Alternatively, you may only want to see certain metrics. The `-Dgraal.Timers`, `-Dgraal.Counters`
and `-Dgraal.MemUseTrackers` exist for this purpose.  These options take a comma separated list of
metrics names. Only the named metrics will be activated and reported. To see the available metric
names, specify `-Dgraal.ListMetrics=true`. At VM shutdown this option lists all the metrics that
were encountered during the VM execution. It does not list metrics registered on non-executed paths
since metric registration is lazy.  For example, to see all the metrics available in a boot strap:
``` mx vm -XX:+UseJVMCICompiler -XX:+BootstrapJVMCI -Dgraal.ListMetrics=true -version ```

## Dumping

In addition to logging, there is support for generating (or dumping) more detailed
visualizations of certain compiler data structures. Currently, there is support for dumping:

* HIR graphs (i.e., instances of
  [Graph](../src/org.graalvm.compiler.graph/src/org/graalvm/compiler/graph/Graph.java)) to the
  [Ideal Graph Visualizer](https://www.graalvm.org/docs/reference-manual/tools/#ideal-graph-visualizer) (IGV), and
* LIR register allocation and generated code to the
  [C1Visualizer](https://java.net/projects/c1visualizer/)

Dumping is enabled via the `-Dgraal.Dump` option. The dump handler for generating C1Visualizer
output will also generate output for HIR graphs if the `-Dgraal.PrintCFG=true` option is specified
(in addition to the `-Dgraal.Dump` option).

By default, `-Dgraal.Dump` output is written to the local file system in a directory determined
by the `-Dgraal.DumpPath` option (default is `$CWD/graal_dumps`). You can send dumps directly to
the IGV over a network socket with the `-Dgraal.PrintGraph=Network` option. The `-Dgraal.PrintGraphHost`
and `-Dgraal.PrintGraphPort` options determine where the dumps are sent. By default, they are
sent to 127.0.0.1:4445 and IGV listens on port 4445 by default.

C1Visualizer output is written to `*.cfg` files. These can be opened via **File -> Open Compiled Methods...** in C1Visualizer.

The IGV can be launched with `mx igv` and the C1Visualizer can be launched via `mx c1visualizer`.

## Tracing VM activity related to compilation

Various other VM options are of interest to see activity related to compilation:

- `-XX:+PrintCompilation` or `-Dgraal.PrintCompilation=true` for notification and brief info about each compilation
- `-XX:+TraceDeoptimization` can be useful to see if excessive compilation is occurring

## Examples
#### Inspecting the compilation of a single method

To see the compiler data structures used while compiling `Node.updateUsages`, use the following command:
```
> mx vm -XX:+UseJVMCICompiler -XX:+BootstrapJVMCI -XX:-TieredCompilation -Dgraal.Dump= -Dgraal.MethodFilter=Node.updateUsages -version
Bootstrapping JVMCI....Dumping debug output in /Users/dsimon/graal/graal/compiler/dumps/1497910458736
................................................ in 38177 ms (compiled 5206 methods)
java version "1.8.0_212"
Java(TM) SE Runtime Environment (build 1.8.0_212-b31)
Java HotSpot(TM) 64-Bit Server VM (build 25.212-b31-jvmci-20-b01, mixed mode)
> find dumps/1497910458736 -type f
dumps/1497910458736/HotSpotCompilation-539[org.graalvm.compiler.graph.Node.updateUsages(Node, Node)].bgv
dumps/1497910458736/HotSpotCompilation-539[org.graalvm.compiler.graph.Node.updateUsages(Node, Node)].cfg
```

As you become familiar with the scope names used in the compiler, you can refine the `-Dgraal.Dump` option
to limit the amount of dump output generated. For example, the `"CodeGen"` and `"CodeInstall"`
scopes are active during code generation and installation respectively. To see the machine code (in
the C1Visualizer) produced during these scopes:
```
 mx vm -Dgraal.Dump=CodeGen,CodeInstall -Dgraal.MethodFilter=NodeClass.get -version
```
You will notice that no IGV output is generated by this command.

Alternatively, you can see the machine code using [HotSpot's PrintAssembly support](https://wiki.openjdk.java.net/display/HotSpot/PrintAssembly):
```
mx hsdis
mx vm -XX:+UseJVMCICompiler -XX:+BootstrapJVMCI -XX:-TieredCompilation -XX:CompileCommand='print,*Node.updateUsages' -version
```
The first step above installs the [hsdis](https://kenai.com/projects/base-hsdis) disassembler and only needs to be performed once.
