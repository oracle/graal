
This pages covers the various mechanisms currently available for debugging Graal.

## IDE Support

All the parts of Graal written in Java can be debugged with a standard Java debugger.
While debugging with Eclipse is described here, it should not be too hard to adapt these instructions for another debugger.

The `mx eclipseinit` command not only creates Eclipse project configurations but also creates an Eclipse launch configuration (in `mx.compiler/eclipse-launches/compiler-attach-localhost-8000.launch`) that can be used to debug all Graal code running in the VM.
This launch configuration requires you to start the VM with the `-d` global option which puts the VM into a state waiting for a remote debugger to attach to port `8000`:

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

In addition to IDE debugging, Graal includes support for *printf* style debugging.
The simplest is to place temporary usages of `System.out` where ever you need them.

For more permanent logging statements, use the `Debug.log()` method that is part of debug scope mechanism.
A (nested) debug scope is entered via [`Debug.scope(...)`](../graal/org.graalvm.compiler.debug/src/org/graalvm/compiler/debug/Debug.java#L265). For example:

```java
InstalledCode code = null;
try (Scope s = Debug.scope("CodeInstall", method)) {
    code = ...
    Debug.log("installed code for %s", method);
} catch (Throwable e) {
    throw Debug.handle(e);
}
```

The `Debug.log` statement will send output to the console if `CodeInstall` is matched by the `graal.Log` system property. The matching logic for this option is described in [DebugFilter](../graal/org.graalvm.compiler.debug/src/org/graalvm/compiler/debug/DebugFilter.java#L31-L82). As mentioned in the javadoc, the same matching logic also applies to the `graal.Dump`, `graal.Time` and `-Dgraal.Count` system properties.

## JVMCI and Graal specific options

JVMCI and Graal options are specified by the `jvmci.*` and `graal.*` system properties respectively. These must be specified on the JVM command line. Modifications to these properties by application code are not seen by JVMCI and Graal. A listing of all such properties can be obtained with `-XX:+JVMCIPrintProperties`.

## Debug Metrics
Graal supports several types of *debug metrics* to record compiler related metrics.
Metrics are restricted to a numerical representation (counters and timers).  
Metrics are classified according to their scope which is either *global* or *method*.
Global metrics are collected per-VM execution whereas method metrics are collected for every compilation of a method.
A detailed description of both global metrics and method metrics can be found below.


### Global Metrics (Counters, Timers, MemUseTrackers)
*Global Metrics* are counters, timers and memory trackers.
A global metric always has a unique name across all compilations.
The VM will dump a summary of the global metrics on shutdown.

There are the following three global metrics types in Graal:
* DebugCounter: A simple named counter.
For example:
```java
// declaration: if the name of a DebugCounter is known at compile time it should be declared as a constant
private static final DebugCounter byteCodeSize = Debug.counter("ByteCodeSize");
// usage
long byteCodeSize= ... ;
byteCodeSize.add(byteCodeSize);
```
The VM dump on `stdout` will include the counter's name and its value:
```
<DebugValues>  
|-> Summary
    ...
    |-> ByteCodeSize=12345
    ...
</DebugValues>  
```
* DebugTimer: An auto-closable timer implementation that records the time spent in its `try` block.
For example:
```java
// declaration: if the name of DebugTimer is known at compile time it should be declared as a constant
private static final DebugTimer myOptimizationTime = Debug.timer("MyOptimizationTimer");
// usage
try(DebugCloseable a = myOptimizationTime.start()){
    // do your optimization
    doMyOptimization();
} // on close the time elapsed between start and close is accumulated to the timer's value
```
The VM dump on `stdout` will include the timer's name. Graal declares two different values for a DebugTimer:
a flat time and an accumulated time.
The accumulated time represents the real time spent in the scope associated with the try-with-resource statement.
The flat time is the amount of time spent within the declared scope minus the time spent in any nested timed scopes.
```
<DebugValues>  
|-> Summary
    ...
    |-> MyOptimizationTimer_Accm=100.0 ms
    |-> MyOptimizationTimer_Flat=10.0 ms
    ...
</DebugValues>  
```
* DebugMemUseTracker: An auto-closable memory tracker that tracks the memory usage in its `try` block. For example:
```java
// declaration: if the name of DebugMemUseTracker is known at compile time it should be declared as a constant
private static final DebugMemUseTracker myOptimizationMemoryUsage = Debug.timer("MyOptimizationMemory");
// usage
try(DebugCloseable a = myOptimizationMemoryUsage.start()){
    // do your optimization
    doMyOptimization();
} // on close the memory usage between start and close is accumulated to the MemUseTrackers's value
```
The VM dump on `stdout` will include the debug mem tracker's name. However, Graal declares two different instances for one DebugMemUseTracker following the same semantics as the DebugTimer.
```
<DebugValues>  
|-> Summary
    ...
    |-> MyOptimizationMemory_Accm=100000 bytes
    |-> MyOptimizationMemory_Flat=1000 bytes
    ...
</DebugValues>  
```

### Method Metrics
Method metrics are the second category of metrics in the Graal compiler.
Unlike the global metrics, method metrics allow to create and use *named counters* per compilation of a Java method.
Global metrics have the disadvantage that they are recorded on a per-VM invocation basis.
They can be filtered (as described in section *Method Filtering*) but filtering
does not satisfy the requirement of collecting metrics on a per-compilation basis.
To illustrate this with a simplified example, imagine the following compiler phase doing a novel optimization:
```java
public class MyCompilerPhase{
  private static final DebugCounter myOptCounter = Debug.counter("MyOptCounter");
  public void doOptimization(JavaMethod method){
    for(/*all IR nodes in the method*/){
      if(/*node is suitable for my optimization*/){
        // do your optimization
        myOptCounter.increment();
      }
    }
  }
}
```
Assuming you have a Java program `P1` where you know there is one method `M1`
very frequently called and thus gets marked for compilation and is compiled by Graal.
You want to know if your novel optimization is applied on `M1` and also how often,
thus you invoke the VM and enable debug counters with `-Dgraal.Count=MyCompilerPhase`
and to filter for the specific method you enable method filtering with `-Dgraal.MethodFilter=P1.M1`.
Assuming you get a counter value of `5` then you do not know if `M1` was compiled
once and the optimization triggered `5` times or if `M1` was compiled 5 times and
the optimization always triggered just once (depending on your optimization and
the conditions for it to apply any other combination is possible).

Method metrics solve this problem by introducing a container for metrics per compilation of a method.
A `MethodMetrics` object is a `List<CompilationData<CompilationId,Map<CounterName,Value>>>`
that collects all its declared metrics for every compilation of a method.
Every time a method gets compiled, assuming a method metrics object for this
method is defined, a new entry in this compilation list is created.
This ensures that all collected metrics preserve the context of the compilation
they belonged to (including the *order* of the compilations).
To come back to the example from before, if we rewrite our compiler phase to use
method metrics we end up with:
```java
public class MyCompilerPhase{
  public void doOptimization(JavaMethod method){
    DebugMethodMetrics methodMetric = Debug.methodMetrics(method);
    // asssuming this phase is only executed once each compilation of a method
    methodMetric.incrementMetric("Compilations");
    for(/*all IR nodes in the method*/){
      if(/*node is suitable for my optimization*/){
        // do your optimization
        methodMetric.incrementMetric("MyOptCounter");
      }
    }
  }
}
```
And we will then see that e.g. we have 5 compilations where in each compilation
the counter named `Compilations` has a value of `1`  and the value of `MyOptCounter` is `1`.


Method metrics are enabled with `-Dgraal.MethodMeter=` and follow the same debugging scope matching rules as e.g. `-Dgraal.Time=`.
There are two different ways to dump method metrics (that can both be enabled):
* ASCII Command Line Dump: The option `-Dgraal.MethodMeterPrintAscii=true` dumps method metrics after the global metrics in a human readable ASCII format.
* File Dump: The option `-Dgraal.MethodMeterFile=filename` will create a file on VM shutdown named `filename.csv` that contains the method compilation metrics in a long data format. The format is:
```
{methodname,compilationListIndex,counterName,counterValue\n}
```
This file can easily be post processed for more elaborate analysis scenarios.

#### Global Metric Interception
Graal uses global metrics in a lot of places, e.g. to measure time and memory of compiler phases (or compilations).
Assuming you are interested in those global metrics on  a per-method-compilation basis there is the option
`-Dgraal.GlobalMetricsInterceptedByMethodMetrics` to enable an interception of the global metrics for method metrics.
If a global metric (`DebugTimer`, `DebugCounter` or `DebugMemUseTracker`)
is enabled in the same scope as method metrics, this option will enable Graal
to use the global metric to update the method metrics for the current compilation
(using the name of the global metric).
The format to specify global metric interception is: `(Timers|Counters|MemUseTrackers)(,Timers|,Counters|,MemUseTrackers)*`.
This option however comes with a small but constant overhead for the lookup of the context method in the global metric.

Assuming you are interested in the phase times during the compilation of a certain method consider the following example that will intercept global timers during the compilation of the method `Long.bitCount`:
```
mx dacapo
  -XX:+UseJVMCICompiler
  -Dgraal.MethodMeter=FrontEnd
  -Dgraal.Time=FrontEnd
  -Dgraal.GlobalMetricsInterceptedByMethodMetrics=Timers
  -Dgraal.MethodMeterPrintAscii=true
  -Dgraal.MethodFilter=Long.bitCount fop -n 10
 // Output
 ==========================================================================
HotSpotMethod<Long.bitCount(long)>
__________________________________________________________________________
FrontEnd_Accm                                       =                    9
PhaseTime_CanonicalizerPhase_Accm                   =                    1
PhaseTime_CanonicalizerPhase_Flat                   =                    1
PhaseTime_HighTier_Accm                             =                    2
PhaseTime_IncrementalCanonicalizerPhase_Accm        =                    1
PhaseTime_LowTier_Accm                              =                    1
PhaseTime_LoweringPhase_Accm                        =                    1
PhaseTime_MidTier_Accm                              =                    3
PhaseTime_PhaseSuite_Accm                           =                    2
PhaseTime_PhaseSuite_Flat                           =                    1
==========================================================================
```

However when using the interception there are several non-trivial things to remember:
* Time Reporting: Time is collected in nanoseconds with the most accurate timer on the given platform, however a certain phase might be so fast that rounding to milliseconds will generate a 0 value for the reported time. Zero values are ignored during dumping.
* Compilation Policy: Timing is heavily influenced by several factors including:
    * TieredCompilation: If Graal is run in tiered mode and compiled by C1 the first compilations will be slower due to the fact that Graal still runs in the interpreter.
    * Type Profile: Certain debugging options of Graal might result in scenarios where a certain non-Graal method is hot and enqueued for Graal compilation as Graal itself heavily calls it e.g. specifying `-Dgraal.PrintAfterCompilation=true` will format method names with `String.format` which can result in a compilation request to Graal to compile `String.format` although the application itself never calls `String.format`.


## Method filtering

Specifying one of the debug scope options (i.e., `-Dgraal.Log`, `-Dgraal.Dump`, `-Dgraal.Count`, or `-Dgraal.Time`) can generate a lot of output. Typically, you are only interesting in compiler output related to one or a couple of methods. Use the `-Dgraal.MethodFilter` option to specify a method filter. The matching logic for this option is described in [MethodFilter](../graal/org.graalvm.compiler.debug/src/org/graalvm/compiler/debug/MethodFilter.java#L33-L92).

## Dumping

In addition to logging, Graal provides support for generating (or dumping) more detailed visualizations of certain compiler data structures. Currently, there is support for dumping:

* HIR graphs (i.e., instances of [Graph](../graal/org.graalvm.compiler.graph/src/org/graalvm/compiler/graph/Graph.java)) to the [Ideal Graph Visualizer](http://ssw.jku.at/General/Staff/TW/igv.html) (IGV), and
* LIR  register allocation and generated code to the [C1Visualizer](https://java.net/projects/c1visualizer/)

Dumping is enabled via the `-Dgraal.Dump` option. The dump handler for generating C1Visualizer output will also generate output for HIR graphs if the `-Dgraal.PrintCFG=true` option is specified (in addition to the `-Dgraal.Dump` option).

By default, `-Dgraal.Dump` output is sent to the IGV over a network socket (localhost:4445). If the IGV is not running, you will see connection error messages like:
```
"Could not connect to the IGV on 127.0.0.1:4445 : java.net.ConnectException: Connection refused"
```
These can be safely ignored.

C1Visualizer output is written to `*.cfg` files. These can be open via **File -> Open Compiled Methods...** in C1Visualizer.

The IGV can be launched with the command `mx igv`. The C1Visualizer can also be launched via mx with the command `mx c1visualizer`.

## Tracing VM activity related to compilation

Various other VM options are of interest to see activity related to compilation:

- `-XX:+PrintCompilation` or `-Dgraal.PrintCompilation=true` for notification and brief info about each compilation
- `-XX:+TraceDeoptimization` can be useful to see if excessive compilation is occurring

## Examples
#### Inspecting the compilation of a single method

To see the compiler data structures used while compiling `Node.updateUsages`, use the following command:
```
mx vm -XX:+UseJVMCICompiler -XX:+BootstrapJVMCI -XX:-TieredCompilation -Dgraal.Dump= -Dgraal.MethodFilter=Node.updateUsages -version
Bootstrapping JVMCI....Connected to the IGV on 127.0.0.1:4445
CFGPrinter: Output to file /Users/dsimon/graal/graal/compiler/compilations-1456505279711_1.cfg
CFGPrinter: Dumping method HotSpotMethod<Node.updateUsages(Node, Node)> to /Users/dsimon/graal/graal/compiler/compilations-1456505279711_1.cfg
............................... in 18636 ms (compiled 3589 methods)
java version "1.8.0_65"
Java(TM) SE Runtime Environment (build 1.8.0_65-b17)
Java HotSpot(TM) 64-Bit JVMCI VM (build 25.66-b00-internal-jvmci-0.9-dev, mixed mode)
```
The `*.cfg` files mentioned in the lines starting with `CFGPrinter:` can be opened in the [C1Visualizer](https://java.net/projects/c1visualizer/) (which can be launched with `mx c1v`).

As you become familiar with the scope names used in Graal, you can refine the `-Dgraal.Dump` option to limit the amount of dump output generated. For example, the `"CodeGen"` and  `"CodeInstall"` scopes are active during code generation and installation respectively. To see the machine code (in the C1Visualizer) produced during these scopes:
```
mx vm -Dgraal.Dump=CodeGen,CodeInstall -Dgraal.MethodFilter=NodeClass.get -version
```
You will notice that no output is sent to the IGV by this command.

Alternatively, you can see the machine code using [HotSpot's PrintAssembly support](https://wiki.openjdk.java.net/display/HotSpot/PrintAssembly):
```
mx hsdis
mx vm -XX:+UseJVMCICompiler -XX:+BootstrapJVMCI -XX:-TieredCompilation -XX:CompileCommand='print,*Node.updateUsages' -version
```
The first step above installs the [hsdis](https://kenai.com/projects/base-hsdis) disassembler and only needs to be performed once.
