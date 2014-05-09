# GraalVM Changelog

## `tip`
### Graal
* ...

### Truffle
* ...

## Version 0.3
9-May-2014, [Repository Revision](http://hg.openjdk.java.net/graal/graal/rev/graal-0.3)

### Graal
* Explicit support for oop compression/uncompression in high level graph.
* LIRGenerator refactoring.
* Explicit types for inputs (InputType enum).
* Added graal.version system property to Graal enabled VM builds.
* Transitioned to JDK 8 as minimum JDK level for Graal.
* Added support for stack introspection.
* New MatchRule facility to convert multiple HIR nodes into specialized LIR

### Truffle
* The method `CallTarget#call` takes now a variable number of Object arguments.
* Support for collecting stack traces and for accessing the current frame in slow paths (see `TruffleRuntime#getStackTrace`).
* Renamed `CallNode` to `DirectCallNode`.
* Renamed `TruffleRuntime#createCallNode` to `TruffleRuntime#createDirectCallNode`.
* Added `IndirectCallNode` for calls with a changing `CallTarget`.
* Added `TruffleRuntime#createIndirectCallNode` to create an `IndirectCallNode`.
* `DirectCallNode#inline` was renamed to `DirectCallNode#forceInlining()`.
* Removed deprecated `Node#adoptChild`.

## Version 0.2
25-Mar-2014, [Repository Revision](http://hg.openjdk.java.net/graal/graal/rev/graal-0.2)

### Graal
* Use HotSpot stubs for certain array copy operations.
* New methods for querying memory usage of individual objects and object graphs in Graal API (`MetaAccessProvider#getMemorySize`, `MetaUtil#getMemorySizeRecursive`).
* Added tiered configuration (C1 + Graal).
* Initial security model for Graal [GRAAL-22](https://bugs.openjdk.java.net/browse/GRAAL-22).
* New (tested) invariant that equality comparisons for `JavaType`/`JavaMethod`/`JavaField` values use `.equals()` instead of `==`.
* Made graph caching compilation-local.
* Added AllocSpy tool for analyzing allocation in Graal using the [Java Allocation Instrumenter](https://code.google.com/p/java-allocation-instrumenter/).
* Initial support for memory arithmetic operations on x86.
* Expanded Debug logging/dumping API to avoid allocation when this Debug facilities are not enabled.

### Truffle
* New API `TruffleRuntime#createCallNode` to create call nodes and to give the runtime system control over its implementation.
* New API `RootNode#getCachedCallNodes` to get a weak set of `CallNode`s that have registered to call the `RootNode`.
* New API to split the AST of a call-site context sensitively. `CallNode#split`, `CallNode#isSplittable`, `CallNode#getSplitCallTarget`, `CallNode#getCurrentCallTarget`, `RootNode#isSplittable`, `RootNode#split`.
* New API to inline a call-site into the call-graph. `CallNode#isInlinable`, `CallNode#inline`, `CallNode#isInlined`.
* New API for the runtime environment to register `CallTarget`s as caller to the `RootNode`. `CallNode#registerCallTarget`.
* Improved API for counting nodes in Truffle ASTs. `NodeUtil#countNodes` can be used with a `NodeFilter`.
* New API to declare the cost of a Node for use in runtime environment specific heuristics. See `NodeCost`, `Node#getCost` and `NodeInfo#cost`.
* Removed old API for `NodeInfo#Kind` and `NodeInfo#kind`. As a replacement the new `NodeCost` API can be used.
* Changed `Node#replace` reason parameter type to `CharSequence` (to enable lazy string building)
* Deprecated `Node#adoptChild` and `Node#adoptChildren`, no longer needed in node constructor
* New `Node#insert` method for inserting new nodes into the tree (formerly `adoptChild`)
* New `Node#adoptChildren` helper method that adopts all (direct and indirect) children of a node
* New API `Node#atomic` for atomic tree operations
* Made `Node#replace` thread-safe


## Version 0.1
5-Feb-2014, [Repository Revision](http://hg.openjdk.java.net/graal/graal/rev/graal-0.1)

### Graal

* Initial version of a dynamic Java compiler written in Java.
* Support for multiple co-existing GPU backends ([GRAAL-1](https://bugs.openjdk.java.net/browse/GRAAL-1)).
* Fixed a compiler bug when running RuneScape ([GRAAL-7](https://bugs.openjdk.java.net/browse/GRAAL-7)).
* Bug fixes ([GRAAL-4](https://bugs.openjdk.java.net/browse/GRAAL-4), [GRAAL-5](https://bugs.openjdk.java.net/browse/GRAAL-5)).

### Truffle

* Initial version of a multi-language framework on top of Graal.
* Update of the [Truffle Inlining API](http://mail.openjdk.java.net/pipermail/graal-dev/2014-January/001516.html).

