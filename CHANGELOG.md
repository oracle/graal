# Truffle Changelog

This changelog summarizes major changes between Truffle versions relevant to languages implementors building upon the Truffle framework. The main focus is on APIs exported by Truffle.

## Version 0.14
2-Jun-2016
* [Source](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/source/Source.html) has been
rewritten to be more immutable. Once (part of) content of a source is loaded, it cannot be
changed.
* Methods `fromNamedAppendableText`, `fromNamedText` and `setFileCaching` of
`Source` has been deprecated as useless or not well defined
* New method `Source`.[getURI()](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/source/Source.html#getURI--)
has been introduced and should be used as a persistent identification of `Source` rather than
existing `getName()` & co. methods. Debugger is using the `URI` to
[attach breakpoints](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/debug/Debugger.html#setLineBreakpoint-int-java.net.URI-int-boolean-)
to not yet loaded sources
* Debugger introduces new [halt tag](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/debug/DebuggerTags.AlwaysHalt.html) to
make it easier to simulate concepts like JavaScript's `debugger` statement
* Debugger can be paused via the Debugger.[pause](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/debug/Debugger.html#pause--)
method
* [@CompilationFinal](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/CompilerDirectives.CompilationFinal.html)
annotation can now specify whether the finality applies to array elements as well
* [TruffleTCK](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/tck/TruffleTCK.html) has been
enhanced to test behavior of languages with respect to foreign array objects


## Version 0.13
22-Apr-2016
* `AcceptMessage` has been deprecated, replaced by
[MessageResolution](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/interop/MessageResolution.html) &
[co](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/interop/Resolve.html). annotations.
Now all message-oriented annotations need to be placed in a single source file.
That simplifies readability as well as improves incremental compilation in certain systems.
* Deprecated `Node.assignSourceSection` removed. This reduces the amount of memory
occupied by [Node](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/nodes/Node.html)
instance.
* `PolyglotEngine.Value.execute` is now as fast as direct `CallTarget.call`.
Using the [PolyglotEngine](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/vm/PolyglotEngine.html)
abstraction now comes with no overhead. Just [JPDA debuggers](http://wiki.apidesign.org/wiki/Truffle#Debugging_from_NetBeans)
need to
[turn debugging on](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/debug/Debugger.html#find-com.oracle.truffle.api.vm.PolyglotEngine-)
explicitly.
* Sharing of efficient code/AST between multiple instances of
[PolyglotEngine](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/vm/PolyglotEngine.html)
is possible. Using more than one `PolyglotEngine` resulted in code de-opt previously.
That isn't the case anymore. Future version of the API will provide explicit control
over the set of engines that share the code.
* Simple language JAR no longer contains test classes. There is a separate simple language tests distribution.

## Version 0.12
* The Instrumentation Framework has been revised and has new APIs that are integrated into the PolyglotEngine.
* Instrumentation support required of language implementations is specified as abstract methods on TruffleLanguage.
* Clients access instrumentation services via an instance of Instrumenter, provided by the Polyglot framework.

## Version 0.11
28-Jan-2016
* Improved interop API
* PolyglotEngine.Builder.getConfig
* TruffleLanguage.Env.isMimeTypeSupported

## Version 0.10
18-Dec-2015
* Profile API classes moved into its own com.oracle.truffle.api.profiles package

## Version 0.9
21-Oct-2015
* Debugger API

## Version 0.8
17-Jul-2015, [Repository Revision](http://lafo.ssw.uni-linz.ac.at/hg/truffle/shortlog/graal-0.8)
* The Truffle repository no longer contains Graal
* PolyglotEngine is an entry point for creating, building and running multi language Truffle systems
* Implement TruffleLanguage and use @Registration to register your language into the Truffle polyglot system
* Include Truffle TCK (test compatibility kit) into your test cases to verify your language implementation is compliant enough
* Interoperability API polished
* Cleanup of Source related API

## Version 0.7
29-Apr-2015, [Repository Revision](http://hg.openjdk.java.net/graal/graal/shortlog/graal-0.7)
* New, faster partial evaluation (no more TruffleCache).
* If a method is annotated with @ExplodeLoop and contains a loop that can not be exploded, partial evaluation will fail.
* Truffle background compilation is now multi-threaded.
* Experimental merge=true flag for @ExplodeLoop allows building bytecode-based interpreters (see BytecodeInterpreterPartialEvaluationTest).
* Added Node#deepCopy as primary method to copy ASTs.
* Disable inlining across Truffle boundary by default. New option TruffleInlineAcrossTruffleBoundary default false.
* Node.replace(Node) now guards against non-assignable replacement, and Node.isReplacementSafe(Node) checks in advance.
* Instrumentation:  AST "probing" is now safe and implemented by Node.probe(); language implementors need only implement Node.isInstrumentable() and Node.createWrapperNode().
* Instrumentation:  A new framework defines a category of  simple "instrumentation tools" that can be created, configured, and installed, after which they autonomously collect execution data of some kind.
* Instrumentation:  A new example "instrumentation tool" is a language-agnostic collector of code coverage information (CoverageTracker); there are two other examples.
* Removed unsafe compiler directives; use `sun.misc.Unsafe` instead.
* Removed `Node#onAdopt()`.
* Implemented a new generated code layout that reduces the code size.
* Changed all methods enclosed in a @TypeSystem must now be static.
* Changed all methods enclosed in generated type system classes are now static.
* Deprecated the type system constant used in the generated type system classes.
* Changed NodeFactory implementations are no longer generated by default. Use {Node}Gen#create instead of {Node}Factory#create to create new instances of nodes.
* Added @GenerateNodeFactory to generate NodeFactory implementations for this node and its subclasses.
* Deprecated @NodeAssumptions for removal in the next release.
* Deprecated experimental @Implies for removal in the next release.
* Added new package c.o.t.api.dsl.examples to the c.o.t.api.dsl project containing documented and debug-able Truffle-DSL use cases.
* Changed "typed execute methods" are no longer required for use as specialization return type or parameter. It is now sufficient to declare them in the @TypeSystem.
* Added @Cached annotation to express specialization local state.
* Added Specialization#limit to declare a limit expression for the maximum number of specialization instantiations.
* Changed syntax and semantics of Specialization#assumptions and Specialization#guards. They now use a Java like expression syntax.
* Changed guard expressions that do not bind any dynamic parameter are invoked just once per specialization instantiation. They are now asserted to be true on the fast path.
* Renamed @ImportGuards to @ImportStatic.
* Changed declaring a @TypeSystemReference for a node that contains specializations is not mandatory anymore.
* Changed types used in specializations are not restricted on types declared in the type system anymore.
* Changed nodes that declare all execute methods with the same number of evaluated arguments as specialization arguments do not require @NodeChild annotations anymore.
* Changed types used in checks and casts are not mandatory to be declared in the type system.

## Version 0.6
19-Dec-2014, [Repository Revision](http://hg.openjdk.java.net/graal/graal/shortlog/graal-0.6)
* Instrumentation: add Instrumentable API for language implementors, with most details automated (see package `com.oracle.truffle.api.instrument`).
* The BranchProfile constructor is now private. Use BranchProfile#create() instead.
* Renamed @CompilerDirectives.SlowPath to @CompilerDirectives.TruffleBoundary
* Renamed RootNode#isSplittable to RootNode#isCloningAllowed
* Removed RootNode#split. Cloning ASTs for splitting is now an implementation detail of the Truffle runtime implementation.
* Renamed DirectCallNode#isSplittable to DirectCallNode#isCallTargetCloningAllowed
* Renamed DirectCallNode#split to DirectCallNode#cloneCallTarget
* Renamed DirectCallNode#isSplit to DirectCallNode#isCallTargetCloned
* Added PrimitiveValueProfile.
* Added -G:TruffleTimeThreshold=5000 option to defer compilation for call targets
* Added RootNode#getExecutionContext to identify nodes with languages
* Removed `FrameTypeConversion` interface and changed the corresponding `FrameDescriptor` constructor to have a default value parameter instead.
* Removed `CompilerDirectives.unsafeFrameCast` (equivalent to a `(MaterializedFrame)` cast).
* Added `TruffleRuntime#getCapability` API method.
* Added `NodeInterface` and allowed child field to be declared with interfaces that extend it.
* Added `CompilerOptions` and allowed it to be set for `ExecutionContext` and `RootNode`.
* Added experimental object API (see new project `com.oracle.truffle.api.object`).

## Version 0.5
23-Sep-2014, [Repository Revision](http://hg.openjdk.java.net/graal/graal/shortlog/graal-0.5)
* Added `TruffleRuntime#getCallTargets()` to get all call targets that were created and are still referenced.
* Added `NeverValidAssumption` to complement `AlwaysValidAssumption`.
* Fixed a bug in `AssumedValue` that may not invalidate correctly.
* New option, `-G:+/-TruffleCompilationExceptionsAreThrown`, that will throw an `OptimizationFailedException` for compiler errors.

## Version 0.4
19-Aug-2014, [Repository Revision](http://hg.openjdk.java.net/graal/graal/shortlog/graal-0.4)
### Truffle
* Change API for stack walking to a visitor: `TruffleRuntime#iterateFrames` replaces `TruffleRuntime#getStackTrace`
* New flag `-G:+TraceTruffleCompilationCallTree` to print the tree of inlined calls before compilation.
* `truffle.jar`: strip out build-time only dependency into a seperated JAR file (`truffle-dsl-processor.jar`)
* New flag `-G:+TraceTruffleCompilationAST` to print the AST before compilation.
* New experimental `TypedObject` interface added.
* Added `isVisited` method for `BranchProfile`.
* Added new `ConditionProfile`, `BinaryConditionProfile` and `CountingConditionProfile` utility classes to profile if conditions.

## Version 0.3
9-May-2014, [Repository Revision](http://hg.openjdk.java.net/graal/graal/shortlog/graal-0.3)
* The method `CallTarget#call` takes now a variable number of Object arguments.
* Support for collecting stack traces and for accessing the current frame in slow paths (see `TruffleRuntime#getStackTrace`).
* Renamed `CallNode` to `DirectCallNode`.
* Renamed `TruffleRuntime#createCallNode` to `TruffleRuntime#createDirectCallNode`.
* Added `IndirectCallNode` for calls with a changing `CallTarget`.
* Added `TruffleRuntime#createIndirectCallNode` to create an `IndirectCallNode`.
* `DirectCallNode#inline` was renamed to `DirectCallNode#forceInlining()`.
* Removed deprecated `Node#adoptChild`.

## Version 0.2
25-Mar-2014, [Repository Revision](http://hg.openjdk.java.net/graal/graal/shortlog/graal-0.2)
* New API `TruffleRuntime#createCallNode` to create call nodes and to give the runtime system control over its implementation.
* New API `RootNode#getCachedCallNodes` to get a weak set of `CallNode`s that have registered to call the `RootNode`.
* New API to split the AST of a call-site context sensitively. `CallNode#split`, `CallNode#isSplittable`, `CallNode#getSplitCallTarget`, `CallNode#getCurrentCallTarget`, `RootNode#isSplittable`, `RootNode#split`.
* New API to inline a call-site into the call-graph. `CallNode#isInlinable`, `CallNode#inline`, `CallNode#isInlined`.
* New API for the runtime environment to register `CallTarget`s as caller to the `RootNode`. `CallNode#registerCallTarget`.
* Improved API for counting nodes in Truffle ASTs. `NodeUtil#countNodes` can be used with a `NodeFilter`.
* New API to declare the cost of a Node for use in runtime environment specific heuristics. See `NodeCost`, `Node#getCost` and `NodeInfo#cost`.
* Changed `Node#replace` reason parameter type to `CharSequence` (to enable lazy string building)
* New `Node#insert` method for inserting new nodes into the tree (formerly `adoptChild`)
* New `Node#adoptChildren` helper method that adopts all (direct and indirect) children of a node
* New API `Node#atomic` for atomic tree operations
* Made `Node#replace` thread-safe


## Version 0.1
5-Feb-2014, [Repository Revision](http://hg.openjdk.java.net/graal/graal/shortlog/graal-0.1)
* Initial version of a multi-language framework on top of Graal.
