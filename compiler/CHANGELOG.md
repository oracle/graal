# Compiler Changelog

This changelog summarizes newly introduced optimizations that may be relevant to other teams.

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
