# Truffle Changelog

This changelog summarizes major changes between Truffle versions relevant to languages implementors building upon the Truffle framework. The main focus is on APIs exported by Truffle.

<<<<<<< HEAD
## Version 0.22
1-Feb-2017
* [RootNode.setCallTarget](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/nodes/RootNode.html#setCallTarget-com.oracle.truffle.api.RootCallTarget-) is deprecated
* Generic parsing method [TruffleLanguage](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/TruffleLanguage.html).`parse(`[ParsingRequest](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/TruffleLanguage.ParsingRequest.html) `)` replaces now deprecated multi-argument `parse` method.
* Added [TruffleLanguage.findMetaObject](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/TruffleLanguage.html#findMetaObject-C-java.lang.Object-) and [DebugValue.getMetaObject](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/debug/DebugValue.html#getMetaObject--) to retrieve a meta-object of a value.
* Added [TruffleLanguage.findSourceLocation](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/TruffleLanguage.html#findSourceLocation-C-java.lang.Object-) and [DebugValue.getSourceLocation](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/debug/DebugValue.html#getSourceLocation--) to retrieve a source section where a value is declared.
* Added [TruffleLanguage.Registration.interactive()](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/TruffleLanguage.Registration.html#interactive--) and [PolyglotEngine.Language.isInteractive()](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/vm/PolyglotEngine.Language.html#isInteractive--) to inform about language interactive capability. When false (the default), the language is supposed to ignore interactive property of sources. When true, the language should print results of execution of interactive sources.
* Deprecated the @[Specialization](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/dsl/Specialization.html) contains attribute and renamed it to replaces.
* Deprecated @[ShortCircuit](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/dsl/ShortCircuit.html) DSL annotation without replacement. It is recommended to implement short circuit nodes manually without using the DSL.
* Added Truffle DSL [reflection API](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/dsl/Reflection.html) that provides reflection information for specialization activation and cached data.

## Version 0.21
6-Dec-2016
* Added [Source.isInteractive()](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/source/Source.html#isInteractive--) to inform languages of a possibility to use polyglot engine streams during execution.
* Unavailable [SourceSection](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/source/SourceSection.html)s created by different calls to createUnavailableSection() are no longer equals(). This means builtins can share a single Source and call createUnavailableSection() for each builtin to be considered different in instrumentation.

## Version 0.20
23-Nov-2016
* Deprecated [Node.getAtomicLock()](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/nodes/Node.html#getAtomicLock--) and replaced it with Node.getLock() which returns a Lock.
* Switching the source and target levels to 1.8
* Significant improvements in Java/Truffle interop

## Version 0.19
27-Oct-2016
* New helper methods in [JavaInterop](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/interop/java/JavaInterop.html): `isArray`, `isBoxed`, `isNull`, `isPrimitive`, `unbox`, `asTruffleValue`.

## Version 0.18
1-Oct-2016
* Added [Instrumenter](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/instrumentation/Instrumenter.html).querySourceSections(SourceSectionFilter) to get a filtered list of loaded instances.
* Added [SourceSectionFilter](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/instrumentation/SourceSectionFilter.html).ANY, which always matches.
* Added [Message.KEYS](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/interop/Message.html#KEYS) to let languages enumerate properties of its objects
* Deprecated [LineLocation](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/source/LineLocation.html), [SourceSection](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/source/SourceSection.html).getLineLocation(), [Source](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/source/Source.html).createLineLocation(int) without replacement.
* Deprecated [SourceSection](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/source/SourceSection.html).getShortDescription(); users can replace uses with their own formatting code.
* Deprecated [SourceSection](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/source/SourceSection.html).createUnavailable(String, String) and replaced it with.
* Added [Source](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/source/Source.html).createUnavailableSection(), [SourceSection](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/source/SourceSection.html).isAvailable() to find out whether a source section is available.
* [SourceSection](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/source/SourceSection.html).createSourceSection(int,int) now only throws IllegalArgumentExceptions if indices that are out of bounds with the source only when assertions (-ea) are enabled.
* Deprecated [Source](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/source/Source.html).createSection(int, int, int, int) 
* Relaxed the restrictions for calling methods on [SuspendedEvent](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/debug/SuspendedEvent.html) and [DebugStackFrame](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/debug/DebugStackFrame.html) from other threads than the execution thread. Please see the javadoc of the individual methods for details.


## Version 0.17
1-Sep-2016

#### Removals, Deprecations and Breaking Changes

* This release removes many deprecated APIs and is thus slightly incompatible
  * Remove deprecated instrumentation API package `com.oracle.truffle.api.instrument` and all its classes.
  * Remove deprecated API method [TruffleLanguage](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/TruffleLanguage.html)`.isInstrumentable(Node)`, `TruffleLanguage.getVisualizer()`, `TruffleLanguage.createWrapperNode()`, `TruffleLanguage.Env.instrumenter()`, `RootNode.applyInstrumentation()`
  * Remove deprecated API [Debugger](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/debug/Debugger.html)`.setTagBreakpoint`
  * Remove deprecated API [RootNode](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/nodes/RootNode.html)`.applyInstrumentation`
  * Remove deprecated tagging API in [SourceSection](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/source/SourceSection.html) and [Source](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/source/Source.html).

* [PolyglotEngine](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/vm/PolyglotEngine.html)
`eval` method and few similar ones no longer declare `throws IOException`.
The I/O now only occurs when operating with [Source](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/source/Source.html).
The evaluation of already loaded sources doesn't need to perform any I/O operations and
thus it makes little sense to require callers to handle the `IOException`.
This change is binary compatible, yet it is source *incompatible* change.
You may need to [adjust your sources](https://github.com/graalvm/fastr/commit/09ab156925d24bd28837907cc2ad336679afc7a2)
to compile.
* Deprecate support for the "identifier" associated with each [SourceSection](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/source/SourceSection.html)
* Deprecated `PolyglotEngine.Builder.onEvent(EventConsumer)` and class `EventConsumer`, debugger events are now dispatched using the `DebuggerSession`.
* [@Fallback](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/dsl/Fallback.html) does not support type specialized arguments anymore. 

#### Additions

* All debugging APIs are now thread-safe and can be used from other threads.
* Changed the debugging API to a session based model. 
  * Added [Debugger](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/debug/Debugger.html)`.find(TruffleLanguage.Env)` to lookup the debugger when inside a guest language implementation.
  * Added [Debugger](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/debug/Debugger.html)`.startSession(SuspendedCallback)` to start a new debugging session using a SuspendedCallback as replacement for `ExecutionEvent.prepareStepInto()`.
  * Added class [DebuggerSession](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/debug/DebuggerSession.html) which represents a debugger session where breakpoints can be installed and the execution can be suspended and resumed.
  * Added [Breakpoint](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/debug/Breakpoint.html)`.newBuilder` methods to create a new breakpoint using the builder pattern based on Source, URI or SourceSections.
  * Added [Breakpoint](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/debug/Breakpoint.html)`.isResolved()` to find out whether the source location of a breakpoint is loaded by the guest language.
  * Added [Breakpoint](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/debug/Breakpoint.html)`.isDisposed()` to find out whether a breakpoint is disposed.
  * Added [SuspendedEvent](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/debug/SuspendedEvent.html)`.getReturnValue()` to get return values of calls during debugging.
  * Added [SuspendedEvent](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/debug/SuspendedEvent.html)`.getBreakpoints()` to return the breakpoints that hit for a suspended event.
  * Added [SuspendedEvent](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/debug/SuspendedEvent.html)`.getStackFrames()` to return all guest language stack frames.
  * Added [SuspendedEvent](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/debug/SuspendedEvent.html)`.getTopStackFrame()` to return the topmost stack frame.
  * Added [SuspendedEvent](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/debug/SuspendedEvent.html)`.getSourceSection()` to return the current guest language execution location
  * Added [SuspendedEvent](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/debug/SuspendedEvent.html)`.getSourceSections()` to return all guest language execution locations of the current method in the AST.
  * Added class [DebugStackFrame](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/debug/DebugStackFrame.html) which represents a guest language stack frame. Allows to get values from the current stack frame, access stack values and evaluate inline expressions.
  * Added class [DebugValue](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/debug/DebugValue.html) which represents a value on a stack frame or the result of an evaluated expression.
  * Added class [DebuggerTester](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/debug/DebuggerTester.html) which represents a utility for testing guest language debugger support more easily.
  * Deprecated [Breakpoint](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/debug/Breakpoint.html)`.getCondition()` and replaced it with [Breakpoint](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/debug/Breakpoint.html)`.getConditionExpression()` to return a String instead of a Source object.
  * Deprecated [Breakpoint](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/debug/Breakpoint.html)`.setCondition(String)` and replaced it with [Breakpoint](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/debug/Breakpoint.html)`.setConditionExpression(String)` to avoid throwing IOException.
  * Deprecated class `ExecutionEvent` and replaced it with [Debugger](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/debug/Debugger.html)`.startSession(SuspendedCallback)`
  * Deprecated [Debugger](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/debug/Debugger.html) methods setLineBreakpoint, getBreakpoints, pause. Replacements are available in the DebuggerSession class
  * Deprecated [Breakpoint](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/debug/Breakpoint.html)`.getState()` to be replaced with [Breakpoint](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/debug/Breakpoint.html)isResolved(), [Breakpoint](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/debug/Breakpoint.html)isDisposed() and [Breakpoint](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/debug/Breakpoint.html)`.isEnabled()`.
  * Deprecated [SuspendedEvent](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/debug/SuspendedEvent.html)`.getNode()` and [SuspendedEvent](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/debug/SuspendedEvent.html).getFrame() without direct replacement.
  * Deprecated [SuspendedEvent](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/debug/SuspendedEvent.html)`.getRecentWarnings()` and replaced it with [SuspendedEvent](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/debug/SuspendedEvent.html).getBreakpointConditionException(Breakpoint)
  * Deprecated [SuspendedEvent](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/debug/SuspendedEvent.html)`.eval` and replaced it with `DebugStackFrame.eval(String)`
  * Deprecated [SuspendedEvent](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/debug/SuspendedEvent.html)`.getStack()` and replaced it with [SuspendedEvent](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/debug/SuspendedEvent.html).getStackFrames()
  * Deprecated [SuspendedEvent](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/debug/SuspendedEvent.html)`.toString(Object, FrameInstance)` and replaced it with `DebugValue.as(String.class)`.

* [TruffleLanguage.createContext](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/TruffleLanguage.html#createContext-com.oracle.truffle.api.TruffleLanguage.Env-)
supports [post initialization callback](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/TruffleLanguage.html#initializeContext-C-)
* Added [SourceSectionFilter.Builder](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/instrumentation/SourceSectionFilter.Builderhtml).`sourceIs(SourcePredicate)` to filter for source sections with a custom source predicate.
* Added [TruffleInstrument.Env](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/instrumentation/TruffleInstrument.Env.html).`isEngineRoot(RootNode)` to find out where the context of the current evaluation ends when looking up the guest language stack trace with `TruffleRuntime.iterateFrames()`.
* Added [TruffleInstrument.Env](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/instrumentation/TruffleInstrument.Env.html).`toString(Node, Object)` to allow string conversions for objects given a Node to identify the guest language.
* Added [EventContext](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/instrumentation/EventContext.html).`lookupExecutionEventNode(EventBinding)` to lookup other execution event nodes using the binding at a source location.
* Added [Node.getAtomicLock()](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/nodes/Node.html#getAtomicLock--) to allow atomic updates that avoid creating a closure.

## Version 0.16
* [Layout](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/object/dsl/Layout.html)
  now accepts an alternative way to construct an object with the `build` method instead of `create`.
* [TruffleTCK](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/tck/TruffleTCK.html) tests simple operation on foreign objects. For example, a simple WRITE accesss, a HAS_SIZE access, or an IS_NULL access. It also tests the message resolution of Truffle language objects, which enables using them in other languages.

## Version 0.15
1-Jul-2016
* [Source](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/source/Source.html) shall be
constructed via its `newBuilder` methods. The other ways to construct or modify
source objects are now deprecated.
* [RootNode.getName](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/nodes/RootNode.html#getName--)
to provide name of a method or function it represents.
* Instruments are now [loaded eagerly](https://github.com/graalvm/truffle/commit/81018616abb0d4ae68e98b7fcd6fda7c8d0393a2) -
which has been reported as an observable behavioral change.
* The [Instrumenter](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/instrumentation/Instrumenter.html)
now allows one to observe when sources and source sections are being loaded via
[attaching a listener](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/instrumentation/Instrumenter.html#attachLoadSourceListener-com.oracle.truffle.api.instrumentation.SourceSectionFilter-T-boolean-).
* Control the way loops are exploded with a new [LoopExplosionKind](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/nodes/ExplodeLoop.LoopExplosionKind.html)
enum.
* [SuspendedEvent](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/debug/SuspendedEvent.html#toString-java.lang.Object-com.oracle.truffle.api.frame.FrameInstance-)
provides a way to convert any value on stack to its string representation.
* [TruffleTCK](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/tck/TruffleTCK.html) checks
whether languages properly support being interrupted after a time out
* Language implementations are encouraged to mark their internal sources as
[internal](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/com/oracle/truffle/api/source/Source.html#isInternal--)

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
* `TruffleRuntime#iterateFrames` now starts at the current frame.

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
