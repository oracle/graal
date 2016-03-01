
This pages covers the various mechanisms currently available for debugging Graal.

## IDE Support

All the parts of Graal written in Java can be debugged with a standard Java debugger. While debugging with Eclipse is described here, it should not be too hard to adapt these instructions for another debugger.

The `mx eclipseinit` command not only creates Eclipse project configurations but also creates an Eclipse launch configuration (in `mx.graal-core/eclipse-launches/graal-core-attach-localhost-8000.launch`) that can be used to debug all Graal code running in the VM. This launch configuration requires you to start the VM with the `-d` global option which puts the VM into a state waiting for a remote debugger to attach to port `8000`:
```
mx -d vm -version
Listening for transport dt_socket at address: 8000
```
Note that the `-d` option applies to any command that runs the VM, such as the `mx unittest` command:
```
mx -d vm unittest
Listening for transport dt_socket at address: 8000
```
Once you see the message above, you then run the Eclipse launch configuration:

1. From the main menu bar, select **Run > Debug Configurations...** to open the Debug Configurations dialogue.
2. In the tree of configurations, select the **Remote Java Application > graal-core-attach-localhost-8000** entry.
3. Click the **Debug** button to start debugging.

At this point you can set breakpoints and perform all other normal Java debugging actions.

## Logging

In addition to IDE debugging, Graal includes support for *printf* style debugging. The simplest is to place temporary usages of `System.out` whereever you need them.

For more permanent logging statements, use the `Debug.log()` method that is part of debug scope mechanism. A (nested) debug scope is entered via [`Debug.scope(...)`](https://github.com/graalvm/graal-core/blob/6daee4240193a87f43f6b8586f2749e4bae9816e/graal/com.oracle.graal.debug/src/com/oracle/graal/debug/Debug.java#L265). For example:

```java
InstalledCode code = null;
try (Scope s = Debug.scope("CodeInstall", method)) {
    code = ...
    Debug.log("installed code for %s", method);
} catch (Throwable e) {
    throw Debug.handle(e);
}
```

The `Debug.log` statement will send output to the console if `CodeInstall` is matched by the `-G:Log` GraalVM option. The matching logic for this option is described in [DebugFilter](https://github.com/graalvm/graal-core/blob/6daee4240193a87f43f6b8586f2749e4bae9816e/graal/com.oracle.graal.debug/src/com/oracle/graal/debug/DebugFilter.java#L31-L82). As mentioned in the javadoc, the same matching logic also applies to the `-G:Dump`, `-G:Time` and `-G:Meter` options.

## Graal specific VM options

Note that a listing of all Graal specific VM options (i.e., those with a "-G:" prefix) can be listed along with a brief description of each option by specifying the `-G:+PrintFlags` option. Also note that the "-G:" prefix is a [short-cut](https://github.com/graalvm/graal-core/blob/3e5b6a39007ef68a720d62170e16577a240f3616/mx.graal-core/mx_graal_8.py#L338-L352) for use of system properties.

## Method filtering

Specifying one of the debug scope options (i.e., `-G:Log`, `-G:Dump`, `-G:Meter`, or `-G:Time`) can generate a lot of output. Typically, you are only interesting in compiler output related to one or a couple of methods. Use the `-G:MethodFilter` option to specifying a method filter. The matching logic for this option is described in [MethodFilter](https://github.com/graalvm/graal-core/blob/6daee4240193a87f43f6b8586f2749e4bae9816e/graal/com.oracle.graal.debug/src/com/oracle/graal/debug/MethodFilter.java#L33-L92).

## Dumping

In addition to logging, Graal provides support for generating (or dumping) more detailed visualizations of certain compiler data structures. Currently, there is support for dumping:

* HIR graphs (i.e., instances of [Graph](https://github.com/graalvm/graal-core/blob/6daee4240193a87f43f6b8586f2749e4bae9816e/graal/com.oracle.graal.graph/src/com/oracle/graal/graph/Graph.java)) to the [Ideal Graph Visualizer](http://ssw.jku.at/General/Staff/TW/igv.html) (IGV), and
* LIR  register allocation and generated code to the [C1Visualizer](https://java.net/projects/c1visualizer/)

Dumping is enabled via the `-G:Dump` option. The dump handler for generating C1Visualizer output will also generate output for HIR graphs if the `-G:+PrintCFG` option is specified (in addition to the `-G:Dump` option).

By default, `-G:Dump` output is sent to the IGV over a network socket (localhost:4445). If the IGV is not running, you will see connection error messages like:
```
"Could not connect to the IGV on 127.0.0.1:4445 : java.net.ConnectException: Connection refused"
```
These can be safely ignored.

C1Visualizer output is written to `*.cfg` files. These can be open via **File -> Open Compiled Methods...** in C1Visualizer.

The IGV can be launched with the command `mx igv`. The C1Visualizer can also be launched via mx with the command `mx c1visualizer`.

## Tracing VM activity related to compilation

Various other VM options are of interest to see activity related to compilation:

- `-XX:+PrintCompilation` or `-G:+PrintCompilation` for notification and brief info about each compilation
- `-XX:+TraceDeoptimization` can be useful to see if excessive compilation is occurring

## Examples
#### Inspecting the compilation of a single method

To see the compiler data structures used while compiling `Node.updateUsages`, use the following command:
```
mx vm -XX:+BootstrapJVMCI -XX:-TieredCompilation -G:Dump= -G:MethodFilter=Node.updateUsages -version
Bootstrapping JVMCI....Connected to the IGV on 127.0.0.1:4445
CFGPrinter: Output to file /Users/dsimon/graal/graal-core/compilations-1456505279711_1.cfg
CFGPrinter: Dumping method HotSpotMethod<Node.updateUsages(Node, Node)> to /Users/dsimon/graal/graal-core/compilations-1456505279711_1.cfg
............................... in 18636 ms (compiled 3589 methods)
java version "1.8.0_65"
Java(TM) SE Runtime Environment (build 1.8.0_65-b17)
Java HotSpot(TM) 64-Bit JVMCI VM (build 25.66-b00-internal-jvmci-0.9-dev, mixed mode)
```
The `*.cfg` files mentioned in the lines starting with `CFGPrinter:` can be opened in the [C1Visualizer](https://java.net/projects/c1visualizer/) (which can be launched with `mx c1v`).

As you become familiar with the scope names used in Graal, you can refine the `-G:Dump` option to limit the amount of dump output generated. For example, the `"CodeGen"` and  `"CodeInstall"` scopes are active during code generation and installation respectively. To see the machine code (in the C1Visualizer) produced during these scopes:
```
mx vm -G:Dump=CodeGen,CodeInstall -G:MethodFilter=NodeClass.get -version
```
You'll notice that no output is sent to the IGV by this command.

Alternatively, you can see the machine code using [HotSpot's PrintAssembly support](https://wiki.openjdk.java.net/display/HotSpot/PrintAssembly):
```
mx hsdis
mx vm -XX:+BootstrapJVMCI -XX:-TieredCompilation -XX:CompileCommand='print,*Node.updateUsages' -version
```
The first step above installs the [hsdis](https://kenai.com/projects/base-hsdis) disassembler and only needs to be performed once.
