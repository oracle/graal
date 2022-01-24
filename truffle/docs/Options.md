---
layout: docs
toc_group: truffle
link_title: Options
permalink: /graalvm-as-a-platform/language-implementation-framework/Options/
---
# Truffle Options

You can list options from the command line with any language launcher:

```shell
language-launcher --help:expert
```

Or, for options only relevant for Truffle language implementers:

```shell
language-launcher --help:internal
```

In addition, the Graal Compiler options can be listed with:

```shell
language-launcher --jvm --vm.XX:+JVMCIPrintProperties
```
See [graalvm_ce_jdk8_options](https://chriswhocodes.com/graalvm_ce_jdk8_options.html) for a list of Graal Compiler options.

## Default Language Launcher Options

- `--polyglot` : Run with all other guest languages accessible.
- `--native` : Run using the native launcher with limited Java access (default).
- `--jvm` : Run on the Java Virtual Machine with Java access.
- `--vm.[option]` : Pass options to the host VM. To see available options, use '--help:vm'.
- `--log.file=<String>` : Redirect guest languages logging into a given file.
- `--log.[logger].level=<String>` : Set language log level to OFF, SEVERE, WARNING, INFO, CONFIG, FINE, FINER, FINEST or ALL.
- `--help` : Print this help message.
- `--help:vm` : Print options for the host VM.
- `--version:graalvm` : Print GraalVM version information and exit.
- `--show-version:graalvm` : Print GraalVM version information and continue execution.
- `--help:languages` : Print options for all installed languages.
- `--help:tools` : Print options for all installed tools.
- `--help:expert` : Print additional options for experts.
- `--help:internal` : Print internal options for debugging language implementations and tools.

## Expert Engine Options

These are advanced options for controlling the engine.
They are useful to users and language and tool implementers.

<!-- BEGIN: expert-engine-options -->
- `--engine.PreinitializeContexts=` : Preinitialize language contexts for given languages.
- `--engine.RelaxStaticObjectSafetyChecks` : On property accesses, the Static Object Model does not perform shape checks and uses unsafe casts
- `--engine.TraceStackTraceInterval=[1, inf)` : Prints the stack trace for all threads for a time interval. By default 0, which disables the output.
- `--engine.DebugCacheCompileUseLastTier=true|false` : If true uses the last tier instead of the first tier compiler. By default the last tier compiler is used (default: true).
- `--engine.BackgroundCompilation=true|false` : Enable asynchronous truffle compilation in background threads (default: true)
- `--engine.Compilation=true|false` : Enable or disable Truffle compilation.
- `--engine.CompilerIdleDelay=<ms>` : Set the time in milliseconds an idle Truffle compiler thread will wait for new tasks before terminating. New compiler threads will be started once new compilation tasks are submitted. Select '0' to never terminate the Truffle compiler thread. The option is not supported by all Truffle runtimes. On the runtime which doesn't support it the option has no effect. default: 10000
- `--engine.CompilerThreads=[1, inf)` : Manually set the number of compiler threads. By default, the number of compiler threads is scaled with the number of available cores on the CPU.
- `--engine.EncodedGraphCacheCapacity=[-1, inf)` : Maximum number of entries in the encoded graph cache (< 0 unbounded, 0 disabled) (default: 0).
- `--engine.EncodedGraphCachePurgeDelay=<ms>` : Delay, in milliseconds, after which the encoded graph cache is dropped when the compile queue becomes idle.The option is only supported on the HotSpot (non-libgraal) Truffle runtime.On runtimes which doesn't support it the option has no effect (default: 10000).
- `--engine.FirstTierBackedgeCounts=true|false` : Whether to emit look-back-edge counters in the first-tier compilations. (default: true)
- `--engine.FirstTierCompilationThreshold=[1, inf)` : Minimum number of invocations or loop iterations needed to compile a guest language root in first tier under normal compilation load (default: 400).
- `--engine.FirstTierMinInvokeThreshold=[1, inf)` : Minimum number of calls before a call target is compiled in the first tier (default: 1)
- `--engine.FirstTierUseEconomy=true|false` : Whether to use the economy configuration in the first-tier compilations. (default: true)
- `--engine.ForceFrameLivenessAnalysis` : Forces the frame clearing mechanism to be executed, even if Frame.clear() is not used.
- `--engine.Inlining=true|false` : Enable automatic inlining of guest language call targets (default: true).
- `--engine.InliningExpansionBudget=[1, inf)` : The base expansion budget for language-agnostic inlining (default: 12000).
- `--engine.InliningInliningBudget=[1, inf)` : The base inlining budget for language-agnostic inlining (default: 12000)
- `--engine.InliningRecursionDepth=[1, inf)` : Maximum depth for recursive inlining (default: 2).
- `--engine.InvalidationReprofileCount=` : Delay compilation after an invalidation to allow for reprofiling. Deprecated: no longer has any effect.
- `--engine.LastTierCompilationThreshold=[1, inf)` : Minimum number of invocations or loop iterations needed to compile a guest language root in last tier under normal compilation load (default: 10000).
- `--engine.MinInvokeThreshold=[1, inf)` : Minimum number of calls before a call target is compiled (default: 3).
- `--engine.Mode=latency|throughput` : Configures the execution mode of the engine. Available modes are 'latency' and 'throughput'. The default value balances between the two.
- `--engine.MultiTier=true|false` : Whether to use multiple Truffle compilation tiers by default. (default: true)
- `--engine.OSR=true|false` : Enable automatic on-stack-replacement of loops (default: true).
- `--engine.PartialBlockCompilation=true|false` : Enable partial compilation for BlockNode (default: true).
- `--engine.PartialBlockCompilationSize=[1, inf)` : Sets the target non-trivial Truffle node size for partial compilation of BlockNode nodes (default: 3000).
- `--engine.PartialBlockMaximumSize=[1, inf)` : Sets the maximum non-trivial Truffle node size for partial compilation of BlockNode nodes (default: 10000).
- `--engine.ReplaceReprofileCount=` : Delay compilation after a node replacement. Deprecated: no longer has any effect.
- `--engine.SingleTierCompilationThreshold=[1, inf)` : Minimum number of invocations or loop iterations needed to compile a guest language root when not using multi tier (default: 1000).
- `--engine.Splitting=true|false` : Enable automatic duplication of compilation profiles (splitting) (default: true).
- `--engine.TraceCompilation` : Print information for compilation results.
- `--engine.HostCallStackHeadRoom=[1, inf)<B>|<KB>|<MB>|<GB>` : Stack space headroom for calls to the host.
- `--engine.IsolateLibrary=<path>` : Path to the isolate library.
- `--engine.IsolateOption.<key>=<value>` : Isolate VM options.
<!-- END: expert-engine-options -->

## Internal Engine Options

These are internal options for debugging language implementations and tools.

<!-- BEGIN: internal-engine-options -->
- `--engine.DisableCodeSharing` : Option to force disable code sharing for this engine, even if the context was created with an explicit engine. This option is intended for testing purposes only.
- `--engine.ForceCodeSharing` : Option to force enable code sharing for this engine, even if the context was created with a bound engine. This option is intended for testing purposes only.
- `--engine.InstrumentExceptionsAreThrown=true|false` : Propagates exceptions thrown by instruments. (default: true)
- `--engine.SafepointALot` : Repeadly submits thread local actions and collects statistics about safepoint intervals in the process. Prints event and interval statistics when the context is closed for each thread. This option significantly slows down execution and is therefore intended for testing purposes only.
- `--engine.ShowInternalStackFrames` : Show internal frames specific to the language implementation in stack traces.
- `--engine.SpecializationStatistics` : Enables specialization statistics for nodes generated with Truffle DSL and prints the result on exit. In order for this flag to be functional -Atruffle.dsl.GenerateSpecializationStatistics=true needs to be set at build time. Enabling this flag and the compiler option has major implications on the performance and footprint of the interpreter. Do not use in production environments.
- `--engine.StaticObjectStorageStrategy=default|array-based|field-based` : Set the storage strategy used by the Static Object Model. Accepted values are: ['default', 'array-based', 'field-based']
- `--engine.TraceCodeSharing` : Enables printing of code sharing related information to the logger. This option is intended to support debugging language implementations.
- `--engine.TraceThreadLocalActions` : Traces thread local events and when they are processed on the individual threads.Prints messages with the [engine] [tl] prefix. 
- `--engine.TriggerUncaughtExceptionHandlerForCancel` : Propagates cancel execution exception into UncaughtExceptionHandler. For testing purposes only.
- `--engine.UseConservativeContextReferences` : Enables conservative context references. This allows invalid sharing between contexts. For testing purposes only.
- `--engine.UsePreInitializedContext=true|false` : Use pre-initialized context when it's available (default: true).
- `--engine.DebugCacheCompile=none|compiled|hot|aot|executed` : Policy to use to to force compilation for executed call targets before persisting the engine. Possible values are:  
  - 'none':     No compilations will be persisted and existing compilations will be invalidated.  
  - 'compiled': No compilations will be forced but finished compilations will be persisted.  
  - 'hot':      (default) All started compilations will be completed and then persisted.   
  - 'aot':      All started and AOT compilable roots will be forced to compile and persisted.  
  - 'executed': All executed and all AOT compilable roots will be forced to compile.
- `--engine.DebugCacheLoad` : Prepares the engine to take the stored engine from the static field instead of reading it from disk.
- `--engine.DebugCachePreinitializeContext=true|false` : Preinitialize a new context with all languages that support it and that were used during the run (default: true).
- `--engine.DebugCacheStore` : Prepares the engine for caching and stores it a static field instead of writing it to disk.
- `--engine.DebugTraceCache` : Enables tracing for the engine cache debug feature.
- `--engine.ArgumentTypeSpeculation=true|false` : Speculate on arguments types at call sites (default: true)
- `--engine.CompilationExceptionsAreFatal` : Treat compilation exceptions as fatal exceptions that will exit the application
- `--engine.CompilationExceptionsArePrinted` : Prints the exception stack trace for compilation exceptions
- `--engine.CompilationExceptionsAreThrown` : Treat compilation exceptions as thrown runtime exceptions
- `--engine.CompilationFailureAction=Silent|Print|Throw|Diagnose|ExitVM` : Specifies the action to take when Truffle compilation fails.  
The accepted values are:  
    Silent - Print nothing to the console.  
     Print - Print the exception to the console.  
     Throw - Throw the exception to caller.  
  Diagnose - Retry compilation with extra diagnostics enabled.  
    ExitVM - Exit the VM process.
- `--engine.CompilationStatisticDetails` : Print additional more verbose Truffle compilation statistics at the end of a run.
- `--engine.CompilationStatistics` : Print Truffle compilation statistics at the end of a run.
- `--engine.CompileAOTOnCreate` : Compiles created call targets immediately with last tier. Disables background compilation if enabled.
- `--engine.CompileImmediately` : Compile immediately to test Truffle compilation
- `--engine.CompileOnly=<name>,<name>,...` : Restrict compilation to ','-separated list of includes (or excludes prefixed with '~'). No restriction by default.
- `--engine.DynamicCompilationThresholds=true|false` : Reduce or increase the compilation threshold depending on the size of the compilation queue (default: true).
- `--engine.DynamicCompilationThresholdsMaxNormalLoad=[1, inf)` : The desired maximum compilation queue load. When the load rises above this value, the compilation thresholds are increased. The load is scaled by the number of compiler threads.  (default: 10)
- `--engine.DynamicCompilationThresholdsMinNormalLoad=[1, inf)` : The desired minimum compilation queue load. When the load falls bellow this value, the compilation thresholds are decreased. The load is scaled by the number of compiler threads (default: 10).
- `--engine.DynamicCompilationThresholdsMinScale=[0.0, inf)` : The minimal scale the compilation thresholds can be reduced to (default: 0.1).
- `--engine.ExcludeAssertions=true|false` : Exclude assertion code from Truffle compilations (default: true)
- `--engine.FirstTierInliningPolicy=<policy>` : Explicitly pick a first tier inlining policy by name (None, TrivialOnly). If empty (default) the lowest priority policy (TrivialOnly) is chosen.
- `--engine.InlineAcrossTruffleBoundary` : Enable inlining across Truffle boundary
- `--engine.InlineOnly=<name>,<name>,...` : Restrict inlined methods to ','-separated list of includes (or excludes prefixed with '~'). No restriction by default.
- `--engine.InliningPolicy=<policy>` : Explicitly pick a inlining policy by name. If empty (default) the highest priority chosen by default.
- `--engine.InstrumentBoundaries` : Instrument Truffle boundaries and output profiling information to the standard output.
- `--engine.InstrumentBoundariesPerInlineSite` : Instrument Truffle boundaries by considering different inlining sites as different branches.
- `--engine.InstrumentBranches` : Instrument branches and output profiling information to the standard output.
- `--engine.InstrumentBranchesPerInlineSite` : Instrument branches by considering different inlining sites as different branches.
- `--engine.InstrumentFilter=<method>,<method>,...` : Method filter for host methods in which to add instrumentation.
- `--engine.InstrumentationTableSize=[1, inf)` : Maximum number of instrumentation counters available (default: 10000).
- `--engine.IterativePartialEscape` : Run the partial escape analysis iteratively in Truffle compilation.
- `--engine.MaximumGraalNodeCount=[1, inf)` : Stop partial evaluation when the graph exceeded this many nodes (default: 40000).
- `--engine.MaximumInlineNodeCount=[1, inf)` : Ignore further truffle inlining decisions when the graph exceeded this many nodes (default: 150000).
- `--engine.MethodExpansionStatistics=true|false|peTier|truffleTier|lowTier|<tier>,<tier>,...` : Print statistics on expanded Java methods during partial evaluation at the end of a run.Accepted values are:  
    true - Collect data for the default tier 'truffleTier'.  
    false - No data will be collected.  
Or one or multiple tiers separated by comma (e.g. truffleTier,lowTier):  
    peTier - After partial evaluation without additional phases applied.  
    truffleTier - After partial evaluation with additional phases applied.  
    lowTier - After low tier phases were applied.
- `--engine.NodeExpansionStatistics=true|false|peTier|truffleTier|lowTier|<tier>,<tier>,...` : Print statistics on expanded Truffle nodes during partial evaluation at the end of a run.Accepted values are:  
    true - Collect data for the default tier 'truffleTier'.  
    false - No data will be collected.  
Or one or multiple tiers separated by comma (e.g. truffleTier,lowTier):  
    peTier - After partial evaluation without additional phases applied.  
    truffleTier - After partial evaluation with additional phases applied.  
    lowTier - After low tier phases were applied.
- `--engine.NodeSourcePositions` : Enable node source positions in truffle partial evaluations.
- `--engine.OSRCompilationThreshold=[1, inf)` : Number of loop iterations until on-stack-replacement compilation is triggered (default 100352).
- `--engine.PerformanceWarningsAreFatal=` : Treat performance warnings as fatal occurrences that will exit the applications
- `--engine.PrintExpansionHistogram` : Prints a histogram of all expanded Java methods.
- `--engine.PriorityQueue=true|false` : Use the priority of compilation jobs in the compilation queue (default: true).
- `--engine.Profiling=true|false` : Enable/disable builtin profiles in com.oracle.truffle.api.profiles. (default: true)
- `--engine.ReturnTypeSpeculation=true|false` : Speculate on return types at call sites (default: true)
- `--engine.SplittingAllowForcedSplits=true|false` : Should forced splits be allowed (default: true)
- `--engine.SplittingDumpDecisions` : Dumps to IGV information on polymorphic events
- `--engine.SplittingGrowthLimit=[0.0, inf)` : Disable call target splitting if the number of nodes created by splitting exceeds this factor times node count (default: 1.5).
- `--engine.SplittingMaxCalleeSize=[1, inf)` : Disable call target splitting if tree size exceeds this limit (default: 100)
- `--engine.SplittingMaxPropagationDepth=[1, inf)` : Propagate info about a polymorphic specialize through maximum this many call targets (default: 5)
- `--engine.SplittingTraceEvents` : Trace details of splitting events and decisions.
- `--engine.TraceAssumptions` : Print stack trace on assumption invalidation
- `--engine.TraceCompilationAST` : Print the entire AST after each compilation
- `--engine.TraceCompilationDetails` : Print information for compilation queuing.
- `--engine.TraceCompilationPolymorphism` : Print all polymorphic and generic nodes after each compilation
- `--engine.TraceDeoptimizeFrame` : Print stack trace when deoptimizing a frame from the stack with `FrameInstance#getFrame(READ_WRITE|MATERIALIZE)`.
- `--engine.TraceInlining` : Print information for inlining decisions.
- `--engine.TraceInliningDetails` : Print detailed information for inlining (i.e. the entire explored call tree).
- `--engine.TraceMethodExpansion=true|false|peTier|truffleTier|lowTier|<tier>,<tier>,...` : Print a tree of all expanded Java methods with statistics after each compilation. Accepted values are:  
    true - Collect data for the default tier 'truffleTier'.  
    false - No data will be collected.  
Or one or multiple tiers separated by comma (e.g. truffleTier,lowTier):  
    peTier - After partial evaluation without additional phases applied.  
    truffleTier - After partial evaluation with additional phases applied.  
    lowTier - After low tier phases were applied.
- `--engine.TraceNodeExpansion=true|false|peTier|truffleTier|lowTier|<tier>,<tier>,...` : Print a tree of all expanded Truffle nodes with statistics after each compilation. Accepted values are:  
    true - Collect data for the default tier 'truffleTier'.  
    false - No data will be collected.  
Or one or multiple tiers separated by comma (e.g. truffleTier,lowTier):  
    peTier - After partial evaluation without additional phases applied.  
    truffleTier - After partial evaluation with additional phases applied.  
    lowTier - After low tier phases were applied.
- `--engine.TracePerformanceWarnings=none|all|<perfWarning>,<perfWarning>,...` : Print potential performance problems, Performance warnings are: call, instanceof, store, frame_merge, trivial.
- `--engine.TraceSplitting` : Print information for splitting decisions.
- `--engine.TraceSplittingSummary` : Used for debugging the splitting implementation. Prints splitting summary directly to stdout on shutdown
- `--engine.TraceStackTraceLimit=[1, inf)` : Number of stack trace elements printed by TraceTruffleTransferToInterpreter, TraceTruffleAssumptions and TraceDeoptimizeFrame (default: 20).
- `--engine.TraceTransferToInterpreter` : Print stack trace on transfer to interpreter.
- `--engine.TraversingCompilationQueue=true|false` : Use a traversing compilation queue. (default: true)
- `--engine.TraversingQueueFirstTierBonus=[0.0, inf)` : Controls how much of a priority should be given to first tier compilations (default 15.0).
- `--engine.TraversingQueueFirstTierPriority` : Traversing queue gives first tier compilations priority.
- `--engine.TraversingQueueWeightingBothTiers=true|false` : Traversing queue uses rate as priority for both tier. (default: true)
- `--engine.TreatPerformanceWarningsAsErrors=none|all|<perfWarning>,<perfWarning>,...` : Treat performance warnings as error. Handling of the error depends on the CompilationFailureAction option value. Performance warnings are: call, instanceof, store, frame_merge, trivial.
<!-- END: internal-engine-options -->
