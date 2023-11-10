---
layout: docs
toc_group: java
link_title: Compiler Configuration on JVM
permalink: /reference-manual/java/options/
---

# Compiler Configuration on JVM

The options for configuring the Graal compiler on the JVM are in 3 categories.

### General Options

These are general options for setting/getting configuration details.

* `-XX:-UseJVMCICompiler`: This disables use of the Graal compiler as the top tier JIT.
This is useful when wanting to compare performance of the Graal compiler against the native JIT compilers.
* `-Djdk.graal.CompilerConfiguration=<name>`: Selects the Graal compiler configuration to use. If omitted, the compiler
configuration with the highest auto-selection priority is used. To see the set
of available configurations, supply the value help to this option.

    The current configurations and their semantics are:
    * `enterprise`: To produce highly optimized code with a possible trade-off to compilation time. <a href="https://www.oracle.com/downloads/graalvm-downloads.html" class="enterprise">[Oracle GraalVM]</a>
    * `community`: To produce reasonably optimized code with a faster compilation time.
    * `economy`: To compile as fast as possible with less optimal throughput of the generated code.

* `-Djdk.graal.ShowConfiguration=none`: Prints information about the Graal compiler configuration selected.
    This option only produces output when the compiler is initialized. By default, the Graal compiler is
    initialized on the first top-tier compilation. For this reason, the way to use this option
    is as follows: `java -XX:+EagerJVMCI -Djdk.graal.ShowConfiguration=info -version`.

    The accepted values for this option are:
    * `none`: To show no information.
    * `info`: To print one line of output describing the compiler configuration in use
       and whether it is loaded from a Native Image ("libgraal") or from class files ("jargraal").
    * `verbose`: To print detailed compiler configuration information.

* `-Djdk.graal.MitigateSpeculativeExecutionAttacks=None`: Selects a strategy to mitigate speculative
    execution attacks (e.g., SPECTRE).

    Accepted values are:
    * `None`: No mitigations are used in JIT compiled code.
    * `AllTargets`: All branches are protected against speculative attacks. This has a large
      performance impact.
    * `GuardTargets`: Only branches that preserve Java memory safety are protected. This has
      reduced performance impact.
    * `NonDeoptGuardTargets`: Same as GuardTargets except that branches which deoptimize are
      not protected since they can not be executed repeatedly.

### Performance Tuning Options

* `-Djdk.graal.UsePriorityInlining=true`: This can be used to disable use of the advanced inlining
algorithm that favours throughput over compilation speed. <a href="https://www.oracle.com/downloads/graalvm-downloads.html" class="enterprise">[Oracle GraalVM]</a>
* `-Djdk.graal.Vectorization=true`: This can be used to disable the auto vectorization optimization.
<a href="https://www.oracle.com/downloads/graalvm-downloads.html" class="enterprise">[Oracle GraalVM]</a>
* `-Djdk.graal.OptDuplication=true`: This can be used to disable the [path duplication optimization](http://ssw.jku.at/General/Staff/Leopoldseder/DBDS_CGO18_Preprint.pdf). <a href="https://www.oracle.com/downloads/graalvm-downloads.html" class="enterprise">[Oracle GraalVM]</a>
* `-Djdk.graal.TuneInlinerExploration=0`: This can be used to try tune for better peak performance or faster warmup.
It automatically adjusts values governing the effort spent during inlining. The value of the option is
a float clamped between `-1` and `1` inclusive. Anything below
`0` reduces inlining effort and anything above `0` increases
inlining effort. In general, peak performance is improved with more inlining effort
while less inlining effort improves warmup (albeit to a lower peak). Note that this
option is only a heuristic and the optimal value can differ from application to application. <a href="https://www.oracle.com/downloads/graalvm-downloads.html" class="enterprise">[Oracle GraalVM]</a>
* `-Djdk.graal.TraceInlining=false`: Enables tracing of inlining decisions. This can be used
    for advanced tuning where it may be possible to change the source code of the program.
    The output format is shown below:

    ```shell
compilation of 'Signature of the compilation root method':
  at 'Sig of the root method' ['Bytecode index']: <'Phase'> 'Child method signature': 'Decision made about this callsite'
    at 'Signature of the child method' ['Bytecode index']:
       |--<'Phase 1'> 'Grandchild method signature': 'First decision made about this callsite'
       \--<'Phase 2'> 'Grandchild method signature': 'Second decision made about this callsite'
    at 'Signature of the child method' ['Bytecode index']: <'Phase'> 'Another grandchild method signature': 'The only decision made about this callsite.'
    ```

    For example:
    ```shell
compilation of java.lang.Character.toUpperCaseEx(int):
  at java.lang.Character.toUpperCaseEx(Character.java:7138) [bci: 22]:
     ├──<GraphBuilderPhase> java.lang.CharacterData.of(int): no, bytecode parser did not replace invoke
     └──<PriorityInliningPhase> java.lang.CharacterData.of(int): yes, worth inlining according to the cost-benefit analysis.
  at java.lang.Character.toUpperCaseEx(Character.java:7138) [bci: 26]:
     ├──<GraphBuilderPhase> java.lang.CharacterDataLatin1.toUpperCaseEx(int): no, bytecode parser did not replace invoke
     └──<PriorityInliningPhase> java.lang.CharacterDataLatin1.toUpperCaseEx(int): yes, worth inlining according to the cost-benefit analysis.
    at java.lang.CharacterDataLatin1.toUpperCaseEx(CharacterDataLatin1.java:223) [bci: 4]:
       ├──<GraphBuilderPhase> java.lang.CharacterDataLatin1.getProperties(int): no, bytecode parser did not replace invoke
       └──<PriorityInliningPhase> java.lang.CharacterDataLatin1.getProperties(int): yes, worth inlining according to the cost-benefit analysis.
     ```

### Diagnostic Options

* `-Djdk.graal.CompilationFailureAction=Silent`: Specifies the action to take when compilation fails by
    throwing an exception.

    The accepted values are:
    * `Silent`: Print nothing to the console.
    * `Print`: Print a stack trace to the console.
    * `Diagnose`: Retry the compilation with extra diagnostics enabled. On VM exit, the collected
       diagnostics are saved to a zip file that can be submitted along with a bug report. A message
       is printed to the console describing where the diagnostics file is saved:
        ```shell
Graal diagnostic output saved in /Users/graal/graal_dumps/1549459528316/graal_diagnostics_22774.zip
        ```
    * `ExitVM`: Same as `Diagnose` except that the VM process exits after retrying.

    For all values except for `ExitVM`, the VM continues executing.
* `-Djdk.graal.CompilationBailoutAsFailure=false`: The compiler may not complete compilation of a method due
 to some property or code shape in the method (e.g., exotic uses of the jsr and ret bytecodes). In this
 case the compilation _bails out_. If you want to be informed of such bailouts, this option makes GraalVM
 treat bailouts as failures and thus be subject to the action specified by the
 `-Djdk.graal.CompilationFailureAction` option.
* `-Djdk.graal.PrintCompilation=false`: Prints an informational line to the console for each completed compilation.
  For example:
  ```shell
  HotSpotCompilation-11  Ljava/lang/Object;                            wait          ()V       |  591ms    12B    92B  4371kB
  HotSpotCompilation-175 Ljava/lang/String;                            lastIndexOf   (II)I     |  590ms   126B   309B  4076kB
  HotSpotCompilation-184 Ljava/util/concurrent/ConcurrentHashMap;      setTabAt      ([Ljava/util/concurrent/ConcurrentHashMap$Node;ILjava/util/concurrent/ConcurrentHashMap$Node;)V  |  591ms    38B    67B  3411kB
  HotSpotCompilation-136 Lsun/nio/cs/UTF_8$Encoder;                    encode        ([CII[B)I |  591ms   740B   418B  4921
  ```

## Setting Compiler Options with Language Launchers

The Graal compiler properties above are usable with some other GraalVM launchers such as `node`, `js` and `lli`. 
The prefix for specifying the properties is slightly different.
For example:
```shell
java -XX:+EagerJVMCI -Djdk.graal.ShowConfiguration=info -version
```

Becomes:
```shell
js --vm.Djdk.graal.ShowConfiguration=info -version
```

> Note the `-D` prefix is replaced by `--vm.D`.

### Related Documentation

- [Graal Compiler](compiler.md)
- [JVM Operations Manual](Operations.md)