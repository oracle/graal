---
layout: docs
toc_group: java
link_title: Graal JIT Compiler Operations Manual
permalink: /reference-manual/compiler/operations/
redirect_from: /reference-manual/java/operations/
---

# Graal JIT Compiler Operations Manual

## Measuring Performance

The first thing to confirm when measuring performance is that the Java Virtual Machine (JVM) is using the Graal JIT compiler.

GraalVM is configured to use the Graal JIT compiler as the top tier compiler by default.

To enable the Graal JIT compiler for use in the [Java HotSpot Virtual Machine](https://docs.oracle.com/en/java/javase/22/vm/java-virtual-machine-technology-overview.html), use the `-XX:+UseGraalJIT` option.
(The `-XX:+UseGraalJIT` option has to be used together with the `-XX:+UnlockExperimentalVMOptions` option that unlocks this experimental integration.)
The following example runs the Java application `com.example.myapp` with the Graal JIT compiler enabled:

```shell
java -XX:+UnlockExperimentalVMOptions -XX:+UseGraalJIT com.example.myapp
```

You can confirm that you are using the Graal JIT compiler by adding the `-Djdk.graal.ShowConfiguration=info` option to the command line.
It produces a line of output similar to the one below when the compiler is initialized:

```
Using "Graal Enterprise compiler with Truffle extensions" loaded from a PGO optimized Native Image shared library
```

> Note: The Graal compiler is only initialized on the first top-tier JIT compilation request so if your application is short-lived, you may not see this output.

Optimizing a JVM-based application is a science in itself.
Compilation may not even be a factor in the case of poor performance as the problem may lie in any other part of the JVM (I/O, garbage collection, threading, and so on), or in a poorly written application, or third-party library code.
For this reason, it is  worth employing the [JDK Mission Control](https://www.oracle.com/java/technologies/jdk-mission-control.html) tool chain to diagnose your application's behavior.

You can also compare performance against the native top-tier compiler in the JVM by adding `-XX:-UseJVMCICompiler` to the command line.

If you observe a significant performance regression when using the Graal JIT compiler, please open an issue on GitHub.
Attach a Java Flight Recorder log and instructions to reproduce the issue&mdash;this makes investigation easier and thus increases the chances of a fix.
Even better is if you can submit a [JMH](http://openjdk.java.net/projects/code-tools/jmh/) benchmark that represents the hottest parts of your application (as identified by a profiler).
This allows us to quickly pinpoint absent optimization opportunities or to provide suggestions on how to restructure your code to avoid or reduce performance bottlenecks.

## Troubleshooting the Graal JIT Compiler

If you spot a security vulnerability, please do **not** report it via GitHub Issues or the public mailing lists, but via the process outlined in the [Reporting Vulnerabilities guide](https://www.oracle.com/corporate/security-practices/assurance/vulnerability/reporting.html).

### Compilation Exceptions

One advantage of the compiler being written in Java is that a Java exception during compilation is not a fatal JVM error.
Instead, each compilation has an exception handler that takes action based on the `graal.CompilationFailureAction` property.

The default value is `Silent`. If you specify `Diagnose`, a failing compilation is retried with extra diagnostics.
In this case, just before the JVM exits, all diagnostic output captured during retried compilations is written to a ZIP file and its location is printed on the console, for example:
```
Graal diagnostic output saved in /Users/demo/graal-dumps/1499768882600/graal_diagnostics_64565.zip
```

You can then attach the ZIP file to an issue on [GitHub](https://github.com/oracle/graal/issues).

As well as `Silent` and `Diagnose`, the following values for `graal.CompilationFailureAction` are available:
* `Print`: prints a message and stack trace to the console but does not perform recompilation.
* `ExitVM`: same as `Diagnose` but the JVM process exits after recompilation.

### Code Generation Errors

The other type of error you might encounter with a compiler is the production of incorrect machine code.
This error can cause a JVM crash, resulting in a file that starts with _hs_err_pid_ in the current working directory of the JVM process.
In most cases, there is a section in the file that shows the stack at the time of the crash, including the type of code for each frame in the stack, as in the following example:

```
Stack: [0x00007000020b1000,0x00007000021b1000],  sp=0x00007000021af7a0,  free space=1017k
Native frames: (J=compiled Java code, j=interpreted, Vv=VM code, C=native code)
J 761 JVMCI jdk.graal.compiler.core.gen.NodeLIRBuilder.matchComplexExpressions(Ljava/util/List;)V (299 bytes) @ 0x0000000108a2fc01 [0x0000000108a2fac0+0x141] (null)
j  jdk.graal.compiler.core.gen.NodeLIRBuilder.doBlock(Ljdk.graal.compiler/nodes/cfg/Block;Ljdk.graal.compiler/nodes/StructuredGraph;Ljdk.graal.compiler/core/common/cfg/BlockMap;)V+211
j  jdk.graal.compiler.core.LIRGenerationPhase.emitBlock(Ljdk.graal.compiler/nodes/spi/NodeLIRBuilderTool;Ljdk.graal.compiler/lir/gen/LIRGenerationResult;Ljdk.graal.compiler/nodes/cfg/Block;Ljdk.graal.compiler/nodes/StructuredGraph;Ljdk.graal.compiler/core/common/cfg/BlockMap;)V+65
```

This example shows that the top frame was compiled (`J`) by the JVMCI compiler, which is the Graal JIT compiler.
The crash occurred at offset `0x141` in the machine code produced for:
```
jdk.graal.compiler.core.gen.NodeLIRBuilder.matchComplexExpressions(Ljava/util/List;)V
```

The next two frames in the stack were interpreted (`j`).
The location of the crash is also often indicated near the top of the file with something like this:
```s
# Problematic frame:
# J 761 JVMCI jdk.graal.compiler.core.gen.NodeLIRBuilder.matchComplexExpressions(Ljava/util/List;)V (299 bytes) @ 0x0000000108a2fc01 [0x0000000108a2fac0+0x141] (null)
```

In this example, there is probably an error in the code produced by the Graal JIT compiler for `NodeLIRBuilder.matchComplexExpressions`.

When filing an issue on [GitHub](https://github.com/oracle/graal/issues) for such a crash, you should first attempt to reproduce the crash with extra diagnostics enabled for the compilation of the problematic method.
In this example, you would add the following options to your command line:
```shell
-Djdk.graal.MethodFilter=NodeLIRBuilder.matchComplexExpressions, -Djdk.graal.Dump=:2
```

These options are described in more detail [here](https://github.com/oracle/graal/blob/master/compiler/docs/Debugging.md).
In brief, these options tell the Graal JIT compiler to capture snapshots of its state at verbosity level 2 while compiling any method named `matchComplexExpressions` in a class with a simple name of `NodeLIRBuilder`.
The complete format of the `MethodFilter` option is described in [MethodFilterHelp.txt](https://github.com/oracle/graal/blob/master/compiler/src/jdk.graal.compiler/src/jdk/graal/compiler/debug/doc-files/MethodFilterHelp.txt).

Quite often, the crash location does not exist directly in the problematic method mentioned in the crash log but comes from an inlined method.

In such a case, simply filtering for the problematic method might not capture an erroneous compilation causing a crash.

To improve the likelihood of capturing an erroneous compilation,  broaden the `MethodFilter` value.
To guide this, add the `-Djdk.graal.PrintCompilation=true` option when trying to reproduce the crash so you can see what was compiled just before the crash.

The following shows sample output from the console:
```
HotSpotCompilation-1218        Ljdk.graal.compiler/core/amd64/AMD64NodeLIRBuilder;                  peephole                                      (Ljdk.graal.compiler/nodes/ValueNode;)Z           |   87ms   428B   447B  1834kB
HotSpotCompilation-1212        Ljdk.graal.compiler/lir/LIRInstructionClass;                         forEachState                                  (Ljdk.graal.compiler/lir/LIRInstruction;Ljdk.graal.compiler/lir/InstructionValueProcedure;)V  |  359ms    92B   309B  6609kB
HotSpotCompilation-1221        Ljdk.graal.compiler/hotspot/amd64/AMD64HotSpotLIRGenerator;          getResult                                     ()Ljdk.graal.compiler/hotspot/HotSpotLIRGenerationResult;  |   54ms    18B   142B  1025kB
#
# A fatal error has been detected by the Java Runtime Environment:
#
#  SIGSEGV (0xb) at pc=0x000000010a6cafb1, pid=89745, tid=0x0000000000004b03
#
# JRE version: OpenJDK Runtime Environment (8.0_121-b13) (build 1.8.0_121-graalvm-olabs-b13)
# Java VM: OpenJDK 64-Bit GraalVM (25.71-b01-internal-jvmci-0.30 mixed mode bsd-amd64 compressed oops)
# Problematic frame:
# J 1221 JVMCI jdk.graal.compiler.hotspot.amd64.AMD64HotSpotLIRGenerator.getResult()Ljdk.graal.compiler/hotspot/HotSpotLIRGenerationResult; (18 bytes) @ 0x000000010a6cafb1 [0x000000010a6caf60+0x51] (null)
#
# Failed to write core dump. Core dumps have been disabled. To enable core dumping, try "ulimit -c unlimited" before starting Java again
```
Here, the crash happened in a different method than the first crash.
As such, we expand the filter argument to be `-Djdk.graal.MethodFilter=NodeLIRBuilder.matchComplexExpressions,AMD64HotSpotLIRGenerator.getResult` and run again.

When the JVM crashes in this way, it does not run the shutdown code that archives the Graal compiler diagnostic output or delete the directory in which it was written.
This must be done manually after the crash.

By default, the directory is _$PWD/graal-dumps/&lt;timestamp&gt;_ (for example, _./graal-dumps/1499938817387_).
However, you can specify the directory with the `-Djdk.graal.DumpPath=<path>` option.

A message, such as the following, is printed to the console when this directory is first used by the compiler:
```
Dumping debug output in /Users/demo/graal-dumps/1499768882600
```

This directory should contain content related to the method that crashed, such as:
```shell
ls -l /Users/demo/graal-dumps/1499768882600
-rw-r--r--  1 demo  staff    144384 Jul 13 11:46 HotSpotCompilation-1162[AMD64HotSpotLIRGenerator.getResult()].bgv
-rw-r--r--  1 demo  staff     96925 Jul 13 11:46 HotSpotCompilation-1162[AMD64HotSpotLIRGenerator.getResult()].cfg
-rw-r--r--  1 demo  staff  12600725 Jul 13 11:46 HotSpotCompilation-791[NodeLIRBuilder.matchComplexExpressions(List)].bgv
-rw-r--r--  1 demo  staff   1727409 Jul 13 11:46 HotSpotCompilation-791[NodeLIRBuilder.matchComplexExpressions(List)].cfg
```
You should attach a ZIP file of this directory to an issue on [GitHub](https://github.com/oracle/graal/issues).

### Related Documentation

- [Graal Compiler](compiler.md)
- [Graal JIT Compiler Configuration](Options.md)