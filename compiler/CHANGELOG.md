# Compiler Changelog

This changelog summarizes newly introduced optimizations that may be relevant to other teams.

## Version 21.1.0
* (GR-28523) Optimize Box nodes: Add support to optimize box operations on unbox/box operations by re-using boxed
representations if the value of the boxed primitive is outside of the cache range of the Int/Long/Short/Char caches.
Box node optimization is enabled per default. Disable it with `-Dgraal.ReuseOufOutOfCacheBoxes=false`.
