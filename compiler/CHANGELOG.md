# Compiler Changelog

This changelog summarizes newly introduced optimizations and other compiler related changes.

## GraalVM for JDK 26 (Internal Version 26.0.0)
* (GR-69280): Allow use of the `graal.` prefix for Graal compiler options without issuing a warning.
* (GR-58163): Added support for recording and replaying JIT compilations. The `-Djdk.graal.RecordForReplay=*` option
  serializes all compilations matching the pattern to JSON files, which contain the results of JVMCI calls. The
  recorded compilations can be replayed with the `mx replaycomp` command. Truffle compilations are currently not
  supported. See `docs/ReplayCompilation.md` for details.

## GraalVM for JDK 25 (Internal Version 25.0.0)
* (GR-60088): This PR adds the `org.graalvm.nativeimage.libgraal` SDK module. With this module, all logic for building
  libgraal has been moved into the compiler suite in a new `jdk.graal.compiler.libgraal` module
  which has no dependency on Native Image internals. This
  is required for Galahad CE where libgraal must be buildable from the Graal compiler sources in the OpenJDK
  while using Native Image as an external tool.
* (GR-59869) Implemented initial optimization of Java Vector API (JEP 338) operations.
  Load, store, basic arithmetic, reduce, compare, and blend operations are transformed to efficient machine instructions where possible.
  Coverage of more operations is planned for the future.
  This optimization is experimental.
  It is enabled by default and can be disabled by setting the `OptimizeVectorAPI` option to `false`.
  Vector API operations are supported both on JIT and when building native images.
  Native image builds must use the `--add-modules jdk.incubator.vector` and `-H:+VectorAPISupport` options to enable optimization.

## GraalVM for JDK 24 (Internal Version 24.2.0)
* (GR-57209): The default number of JVMCI threads is now the same as the number of C2 threads (`-XX:JVMCINativeLibraryThreadFraction=0.66`).
  This benefits the program warmup but could increase the maximum RSS.
  Setting `-XX:JVMCINativeLibraryThreadFraction` to a smaller value will result in smaller maximum RSS but potentially longer warmup. (See [JDK-8337493](https://bugs.openjdk.org/browse/JDK-8337493)).
* (GR-54476): Issue a deprecation warning on first use of a legacy `graal.` prefix (see GR-49960 below).
  The warning is planned to be replaced by an error in GraalVM for JDK 25.

## GraalVM for JDK 23 (Internal Version 24.1.0)
* (GR-50352): Added `-Djdk.graal.PrintPropertiesAll` to make `-XX:+JVMCIPrintProperties` show all Graal options.
* (GR-25968): New optimization for reducing code size on AMD64, by emitting smaller jump instructions if the displacement fits in one byte.
  Enabled for Native Image O1-O3 per default; disabled elsewhere. Use `-Djdk.graal.OptimizeLongJumps=true` to enable.
* (GR-45919): Added support for [Generational ZGC (JEP 439)](https://openjdk.org/jeps/439).

## GraalVM for JDK 22 (Internal Version 24.0.0)
* (GR-49876): Added `-Dgraal.PrintIntrinsics=true` to log the intrinsics used by Graal in the current runtime.
* (GR-49960): The Graal options now use the `jdk.graal.` prefix (e.g. `-Djdk.graal.PrintCompilation=true`).
  The legacy `graal.` prefix is deprecated but still supported (e.g. `-Dgraal.PrintCompilation=true`).
* (GR-49610): The Graal module has been renamed from `jdk.internal.vm.compiler` to `jdk.graal.compiler`.
  Likewise, the compiler packages moved into the `jdk.graal.compiler` namespace.
  These renamings were done in preparation for [Project Galahad](https://openjdk.org/projects/galahad/).
* (GR-20827): Extend endbranch support: Add endbranch CFI landing pad markers to exception targets.
  Ensure that LIR insertion buffers do not move existing endbranches on basic blocks.
  Extend the `AMD64MacroAssembler` with a `PostCallAction` that is performed after a `call` is emitted.

## GraalVM for JDK 21 (Internal Version 23.1.0)
* (GR-43228): Enforce backward-edge control-flow integrity (CFI) on aarch64 based on the `UseBranchProtection` JVM flag.

## GraalVM for JDK 17 and GraalVM for JDK 20 (Internal Version 23.0.0)
* (GR-42212): Remove support for all JDKs earlier than JDK 17.
* (GR-42044): Improved output of `-Dgraal.ShowConfiguration=info`. For example:
    `Using "Graal Community compiler" loaded from a Native Image shared library`
  instead of:
    `Using compiler configuration 'community' provided by org.graalvm.compiler.hotspot.CommunityCompilerConfigurationFactory loaded from JVMCI native library`.
* (GR-42145): The periodic dumping of benchmark counters enabled by `-Dgraal.TimedDynamicCounters` is now limited to jargraal.
* (GR-31578): Novel Optimization Log: Unified interface to log and dump (e.g. via JSON) optimization decisions.
Optimization phases should use the `OptimizationLog` to log transformations. Read more in `OptimizationLog.md` and read
`Profdiff.md` to learn how to compare performed optimizations in hot compilations of 2 experiments.
* The `-Dgraal.PrintCompilation=true` output now includes stub compilations. For example:
`StubCompilation-57   <stub>    exceptionHandler  (Object,Word)void  |  166us     0B bytecodes    88B codesize   137kB allocated`
* (GR-27475) Add support for the ZGC collector on HotSpot.

## Version 22.3.0
* (GR-19840): An image produced by GraalVM's jlink now includes and uses libgraal by default and its `java -version` output includes GraalVM branding.
* (GR-32382): Added a dedicated Native Image GC policy for libgraal that will adjust the eden space aggressively to
minimize RSS memory usage.
* (GR-38950): Removed deprecated JMX `HotSpotGraalRuntime` management bean from both `libgraal` and `jargraal`.

## Version 22.2.0
* (GR-23737): New global value numbering optimization for fixed nodes early in the compilation pipeline.
Early global value numbering and loop invariant code motion is enabled per default.
Disable early global value numbering with `-Dgraal.EarlyGVN=false`.
Disable early loop invariant code motion with  `-Dgraal.EarlyLICM=false`.
* (GR-16452): Compute the code emission basic block order after backend control flow optimizations.
* (GR-35033): Enable floating and global value numbering of division nodes early on in the compilation pipeline if
  it is known they will not trap.
* (GR-38405): Compute all unswitchable invariant then pick the most frequent one.  
* (GR-38857): Deprecated and disabled the JMX `HotSpotGraalRuntime` management bean. Re-enable the `HotSpotGraalRuntime`
  management bean with `-Dgraal.LibGraalManagementDelay=0`.

## Version 22.1.0
* (GR-36751): Removed the `DuplicateIrreducibleLoops` option. To disable irreducible loop handling, set
  `-Dgraal.MaxDuplicationFactor` to a value less than or equal to 1. For AOT compilations, the effort
  spent to handle irreducible loops is boosted to let Native Image support more programs with irreducible loops.

## Version 22.0.0
* (GR-22707) (GR-30838): New, inner loops first, reverse post order and loop frequency calculations for the compiler.

## Version 21.2.0
* (GR-29770) Loop safepoint elimination: Not only consider 32bit loops for safepoint removal but also 64bit ones
that iterate in 32bit ranges.
* (GR-29341) AVX-512 support: Fix EVEX encoding and feature checks for existing instructions and add AVX-512
alternatives.
* (GR-31162) Do not de-duplicate ValueAnchorNode. As part of this change, there is a new marker interface
NodeWithIdentity to mark nodes that have identity.
* (GR-8974) Speculative guard movement: An optimization that tries to move a loop invariant guard
  (e.g., an array bounds check) inside a loop to outside of the loop. Disable with `-Dgraal.SpeculativeGuardMovement=false`.
  Included in this change is enhanced output for `-Dgraal.ShowConfiguration=verbose` in terms of
  showing the compilation phases of a compiler configuration.
* (GR-31031) Add intrinsic for Reference.refersTo and PhantomReference.refersTo.
* (GR-31059) Permit unusually structured locking in Kotlin coroutines.

## Version 21.1.0
* (GR-29126) Unify box optimizations in the compiler. Remove `-Dgraal.ReuseOutOfCacheBoxedValues=false`.
* (GR-28523) Optimize Box nodes: Optimizes box operations by re-using boxed representations
if the value of the boxed primitive is outside of the cache range of the Int/Long/Short/Char caches.
Box node optimization is enabled per default. Disable it with `-Dgraal.ReuseOutOfCacheBoxedValues=false`.
* (GR-29373) Eliminate unneeded STORE_LOAD barriers on sequential volatile writes on x86.
This improves ConcurrentHashMap performance.
* (GR-29337) Volatile loads were losing type information about the underlying field, resulting in unneeded casts.
This improves ConcurrentHashMap performance.
* (GR-28956) Use more informative `ProfileData` objects instead of raw branch probabilities and loop frequencies.
