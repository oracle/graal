# Compiler Changelog

This changelog summarizes newly introduced optimizations and other compiler related changes.

## Version 23.0.0
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
