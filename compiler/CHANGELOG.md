# Compiler Changelog

This changelog summarizes newly introduced optimizations that may be relevant to other teams.

## Version 21.1.0
* (GR-28523) Optimize Box nodes: Optimizes box operations by re-using boxed representations 
if the value of the boxed primitive is outside of the cache range of the Int/Long/Short/Char caches.
Box node optimization is enabled per default. Disable it with `-Dgraal.ReuseOutOfCacheBoxedValues=false`.
* (GR-29373) Eliminate unneeded STORE_LOAD barriers on sequential volatile writes on x86.
This improves ConcurrentHashMap performance.
* (GR-29337) Volatile loads were losing type information about the underlying field, resulting in unneeded casts.
This improves ConcurrentHashMap performance.
