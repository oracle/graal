# Truffle Options

You can list options from the command line with any language launcher:

```
$ my-language --help:expert
```

or for options only relevant for Truffle language implementers:

```
$ my-language --help:internal
```

In addition Graal compiler options can be listed with:

```
$ my-language --jvm --vm.XX:+JVMCIPrintProperties
```
See https://chriswhocodes.com/graalvm_ce_jdk8_options.html for a list of Graal compiler options.

## Default language launcher options

```
  --polyglot                                   Run with all other guest languages accessible.
  --native                                     Run using the native launcher with limited Java access (default).
  --jvm                                        Run on the Java Virtual Machine with Java access.
  --vm.[option]                                Pass options to the host VM. To see available options, use '--help:vm'.
  --log.file=<String>                          Redirect guest languages logging into a given file.
  --log.[logger].level=<String>                Set language log level to OFF, SEVERE, WARNING, INFO, CONFIG, FINE, FINER, FINEST or ALL.
  --help                                       Print this help message.
  --help:vm                                    Print options for the host VM.
  --version:graalvm                            Print GraalVM version information and exit.
  --show-version:graalvm                       Print GraalVM version information and continue execution.
  --help:languages                             Print options for all installed languages.
  --help:tools                                 Print options for all installed tools.
  --help:expert                                Print additional options for experts.
  --help:internal                              Print internal options for debugging language implementations and tools.
```

## Expert engine options

Advanced engine options for controlling the engine. Useful for users and language and tool implementers.
  
```
Expert engine options:
  --engine.BackgroundCompilation=<Boolean>     Enable asynchronous truffle compilation in background threads
  --engine.Compilation=<Boolean>               Enable or disable Truffle compilation.
  --engine.CompilationThreshold=<Integer>      Minimum number of invocations or loop iterations needed to compile a guest language root.
  --engine.CompilerIdleDelay=<Long>            Set the time in milliseconds an idle Truffle compiler thread will wait for new tasks before terminating. New compiler
                                               threads will be started once new compilation tasks are submitted. Select '0' to never terminate the Truffle compiler
                                               thread. The option is not supported by all Truffle runtimes. On the runtime which doesn't support it the option has no
                                               effect.
  --engine.CompilerThreads=<Integer>           Manually set the number of compiler threads
  --engine.EncodedGraphCacheCapacity=<Integer> Maximum number of entries in the encoded graph cache (< 0 unbounded, 0 disabled).
  --engine.EncodedGraphCachePurgeDelay=<Integer>
                                               Delay, in milliseconds, after which the encoded graph cache is dropped when the compile queue becomes idle.The option is
                                               only supported on the HotSpot (non-libgraal) Truffle runtime.On runtimes which doesn't support it the option has no
                                               effect.
  --engine.FirstTierCompilationThreshold=<Integer>
                                               Minimum number of invocations or loop iterations needed to compile a guest language root in low tier mode.
  --engine.FirstTierMinInvokeThreshold=<Integer>
                                               Minimum number of calls before a call target is compiled in the first tier.
  --engine.Inlining=<Boolean>                  Enable automatic inlining of guest language call targets.
  --engine.InliningExpansionBudget=<Integer>   The base expansion budget for language-agnostic inlining.
  --engine.InliningInliningBudget=<Integer>    The base inlining budget for language-agnostic inlining
  --engine.InliningNodeBudget=<Integer>        Maximum number of inlined non-trivial AST nodes per compilation unit.
  --engine.InliningPolicy=<String>             Explicitly pick a inlining policy by name. Highest priority chosen by default.
  --engine.InliningRecursionDepth=<Integer>    Maximum depth for recursive inlining.
  --engine.LanguageAgnosticInlining=<Boolean>  Use language-agnostic inlining (overrides the TruffleFunctionInlining setting, option is experimental).
  --engine.MinInvokeThreshold=<Integer>        Minimum number of calls before a call target is compiled
  --engine.Mode=<EngineMode>                   Configures the execution mode of the engine. Available modes are 'latency' and 'throughput'. The default value balances
                                               between the two.
  --engine.MultiTier                           Whether to use multiple Truffle compilation tiers by default.
  --engine.OSR=<Boolean>                       Enable automatic on-stack-replacement of loops.
  --engine.PartialBlockCompilation=<Boolean>   Enable partial compilation for BlockNode.
  --engine.PartialBlockCompilationSize=<Integer>
                                               Sets the target non-trivial Truffle node size for partial compilation of BlockNode nodes.
  --engine.Splitting=<Boolean>                 Enable automatic duplication of compilation profiles (splitting).
  --engine.TraceCompilation                    Print information for compilation results.
```

## Internal engine options:

Internal options for debugging language implementations and tools.

```
  --engine.ArgumentTypeSpeculation=<Boolean>   Speculate on arguments types at call sites
  --engine.CompilationFailureAction=<ExceptionAction>
                                               Specifies the action to take when Truffle compilation fails.%nThe accepted values are:%n    Silent - Print nothing to
                                               the console.%n     Print - Print the exception to the console.%n     Throw - Throw the exception to caller.%n  Diagnose
                                               - Retry compilation with extra diagnostics enabled.%n    ExitVM - Exit the VM process.
  --engine.CompilationStatisticDetails         Print additional more verbose Truffle compilation statistics at the end of a run.
  --engine.CompilationStatistics               Print Truffle compilation statistics at the end of a run.
  --engine.CompileImmediately                  Compile immediately to test Truffle compilation
  --engine.CompileOnly=<String>                Restrict compilation to ','-separated list of includes (or excludes prefixed with '~').
  --engine.ExcludeAssertions=<Boolean>         Exclude assertion code from Truffle compilations
  --engine.InlineAcrossTruffleBoundary         Enable inlining across Truffle boundary
  --engine.InstrumentBoundaries                Instrument Truffle boundaries and output profiling information to the standard output.
  --engine.InstrumentBoundariesPerInlineSite   Instrument Truffle boundaries by considering different inlining sites as different branches.
  --engine.InstrumentBranches                  Instrument branches and output profiling information to the standard output.
  --engine.InstrumentBranchesPerInlineSite     Instrument branches by considering different inlining sites as different branches.
  --engine.InstrumentExceptionsAreThrown       Propagates exceptions thrown by instruments.
  --engine.InstrumentFilter=<String>           Method filter for host methods in which to add instrumentation.
  --engine.InstrumentationTableSize=<Integer>  Maximum number of instrumentation counters available.
  --engine.IterativePartialEscape              Run the partial escape analysis iteratively in Truffle compilation.
  --engine.MaximumGraalNodeCount=<Integer>     Stop partial evaluation when the graph exceeded this many nodes.
  --engine.MaximumInlineNodeCount=<Integer>    Ignore further truffle inlining decisions when the graph exceeded this many nodes.
  --engine.NodeSourcePositions                 Enable node source positions in truffle partial evaluations.
  --engine.OSRCompilationThreshold=<Integer>   Number of loop iterations until on-stack-replacement compilation is triggered.
  --engine.PrintExpansionHistogram             Prints a histogram of all expanded Java methods.
  --engine.Profiling=<Boolean>                 Enable/disable builtin profiles in com.oracle.truffle.api.profiles.
  --engine.ReturnTypeSpeculation=<Boolean>     Speculate on return types at call sites
  --engine.ShowInternalStackFrames             Show internal frames specific to the language implementation in stack traces.
  --engine.SplittingAllowForcedSplits=<Boolean>
                                               Should forced splits be allowed.
  --engine.SplittingDumpDecisions              Dumps to IGV information on polymorphic events
  --engine.SplittingGrowthLimit=<Double>       Disable call target splitting if the number of nodes created by splitting exceeds this factor times node count
  --engine.SplittingMaxCalleeSize=<Integer>    Disable call target splitting if tree size exceeds this limit
  --engine.SplittingMaxNumberOfSplitNodes=<Integer>
                                               Disable call target splitting if number of nodes created by splitting exceeds this limit
  --engine.SplittingMaxPropagationDepth=<Integer>
                                               Propagate info about a polymorphic specialize through maximum this many call targets
  --engine.SplittingTraceEvents                Trace details of splitting events and decisions.
  --engine.TraceAssumptions                    Print stack trace on assumption invalidation
  --engine.TraceCompilationAST                 Print the entire AST after each compilation
  --engine.TraceCompilationCallTree            Print the inlined call tree for each compiled method
  --engine.TraceCompilationDetails             Print information for compilation queuing.
  --engine.TraceCompilationPolymorphism        Print all polymorphic and generic nodes after each compilation
  --engine.TraceInlining                       Print information for inlining decisions.
  --engine.TraceInliningDetails                Print detailed information for inlining (i.e. the entire explored call tree).
  --engine.TracePerformanceWarnings=<PerformanceWarningKind>
                                               Print potential performance problems
  --engine.TraceSplitting                      Print information for splitting decisions.
  --engine.TraceSplittingSummary               Used for debugging the splitting implementation. Prints splitting summary directly to stdout on shutdown
  --engine.TraceStackTraceLimit=<Integer>      Number of stack trace elements printed by TraceTruffleTransferToInterpreter and TraceTruffleAssumptions
  --engine.TraceTransferToInterpreter          Print stack trace on transfer to interpreter.
  --engine.TreatPerformanceWarningsAsErrors=<PerformanceWarningKind>
                                               Treat performance warnings as error. Handling of the error depends on the CompilationFailureAction option value
  --engine.UseConservativeContextReferences    Enables conservative context references. This allows invalid sharing between contexts. For testing purposes only.
```