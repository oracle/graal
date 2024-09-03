---
layout: docs
toc_group: java
link_title: Graal JIT Compiler Configuration
permalink: /reference-manual/java/options/
---

# Graal JIT Compiler Configuration

The options to configure the Graal JIT compiler are in three categories: general, performance tuning, and diagnostic.

>The Graal JIT compiler is mostly configured by system properties whose names begin with the `jdk.graal` prefix, set via `-Djdk.graal...` on the command line.
The list of available properties can be printed using the `-XX:+JVMCIPrintProperties` option.

### General Options

These are general options for setting/getting configuration details.

* `-XX:-UseJVMCICompiler`: Disables use of the Graal compiler as the top-tier JIT compiler.
This is useful when you want to compare the performance of the Graal JIT compiler against a native JIT compiler.
* `-Djdk.graal.CompilerConfiguration=<name>`: Selects the Graal JIT compiler configuration to use.
If omitted, the compiler configuration with the highest auto-selection priority is selected.
To see the available configurations, supply the value `help` to this option.

    The names of the compiler configurations and their semantics are:
    * `enterprise`: Produces highly optimized code with a possible trade-off to compilation time (only available in Oracle GraalVM).
    * `community`: Produces reasonably optimized code with a faster compilation time.
    * `economy`: Compiles as fast as possible with less optimal throughput of the generated code.

* `-Djdk.graal.ShowConfiguration=<level>`: Prints information about the Graal JIT compiler configuration selected.
    This option only produces output when the compiler is initialized. By default, the Graal JIT compiler is
    initialized on the first top-tier compilation. For this reason, the way to use this option
    is as follows: `java -XX:+EagerJVMCI -Djdk.graal.ShowConfiguration=info -version`.

    Accepted arguments are:
    * `none`: Shows no information.
    * `info`: Prints one line of output describing the compiler configuration in use and from where it is loaded.
    * `verbose`: Prints detailed compiler configuration information.

* `-Djdk.graal.SpectrePHTBarriers=<strategy>`: Selects a strategy to mitigate speculative bounds check bypass (also known as Spectre-PHT or Spectre V1).

    Accepted arguments are:
    * `None`: Uses no mitigations in JIT-compiled code. (Default.)
    * `AllTargets`: Uses speculative execution barrier instructions to stop speculative execution on all branch targets.
    This option is equivalent to setting `SpeculativeExecutionBarriers` to `true`.
    (This has a large performance impact.)
    * `GuardTargets`: Instruments branch targets relevant to Java memory safety with barrier instructions.
    Protects only those branches that preserve Java memory safety.
    (This option has a lower performance impact than `AllTargets`.)
    * `NonDeoptGuardTargets`: Same as `GuardTargets`, except that branches which are deoptimized, are not protected because they cannot be executed repeatedly and are, thus, less likely to be successfully exploited in an attack.

    Note that all modes except `None` also instrument branch target blocks containing `UNSAFE` memory accesses with barrier instructions.

### Performance Tuning Options

* `-Djdk.graal.Vectorization={ true | false }`: To disable the auto vectorization optimization (only available in Oracle GraalVM). (Default: `true`.)
* `-Djdk.graal.OptDuplication={ true | false }`: To disable the [path duplication optimization](http://ssw.jku.at/General/Staff/Leopoldseder/DBDS_CGO18_Preprint.pdf){:target="_blank"} (only available in Oracle GraalVM). (Default: `true`.)
* `-Djdk.graal.TuneInlinerExploration=<value>`: To tune for better peak performance or faster warmup.
It automatically adjusts values governing the effort spent during inlining. The value of the option is a float clamped between `-1` and `1` inclusive. Anything below `0` reduces inlining effort and anything above `0` increases inlining effort. In general, peak performance is improved with more inlining effort while less inlining effort improves warmup (albeit to a lower peak). Note that this option is only a heuristic and the optimal value can differ from application to application (only available in Oracle GraalVM).

### Diagnostic Options

* `-Djdk.graal.CompilationFailureAction=<action>`: Specifies the action to take when compilation fails by throwing an exception.

    Accepted actions:
    * `Silent`: Print nothing to the console. (Default.)
    * `Print`: Print a stack trace to the console.
    * `Diagnose`: Retry the compilation with extra diagnostics enabled. On JVM exit, the collected
       diagnostics are saved to a ZIP file that can be submitted along with a bug report. A message
       is printed to the console describing where the diagnostics file is saved:
        ```
      Graal diagnostic output saved in /Users/graal/graal_dumps/1549459528316/graal_diagnostics_22774.zip
        ```
    * `ExitVM`: Same as `Diagnose` except that the JVM process exits after retrying.

    For all values except for `ExitVM`, the JVM continues.
* `-Djdk.graal.CompilationBailoutAsFailure={ true | false }`: The compiler may not complete compilation of a method due
 to some property or code shape in the method (for example, exotic uses of the `jsr` and `ret` bytecodes). In this
 case the compilation _bails out_. If you want to be informed of such bailouts, this option makes the Graal JIT compiler
 treat bailouts as failures and thus be subject to the action specified by the
 `-Djdk.graal.CompilationFailureAction` option. (Default: `false`.)

## Setting Compiler Options with Language Launchers

The Graal JIT compiler properties above are usable with some other GraalVM launchers such as `node` and `js`. 
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
- [Graal JIT Compiler Operations Manual](Operations.md)