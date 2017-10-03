# Truffle Changelog

This changelog summarizes major changes between Truffle versions relevant to languages implementors building upon the Truffle framework. The main focus is on APIs exported by Truffle.

## Version 0.29

* [SourceSectionFilter.Builder.includeInternal](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/instrumentation/SourceSectionFilter.Builder.html#includeInternal-boolean-) added to be able to exclude internal code from instrumentation.
* Debugger step filtering is extended with [include of internal code](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/SuspensionFilter.Builder.html#includeInternal-boolean-) and [source filter](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/SuspensionFilter.Builder.html#sourceIs-java.util.function.Predicate-). By default, debugger now does not step into internal code, unless a step filter that is set to include internal code is applied.

## Version 0.28
4-Oct-2017

* Truffle languages may support [access](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html#isThreadAccessAllowed-java.lang.Thread-boolean-) to contexts from multiple threads at the same time. By default the language supports only single-threaded access. 
* Languages now need to use the language environment to [create](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.Env.html#createThread-java.lang.Runnable-) new threads for a context. Creating Threads using the java.lang.Thread constructor is no longer allowed and will be blocked in the next release.
* Added `JavaInterop.isJavaObject(Object)` method overload.
* Deprecated helper methods in `JavaInterop`: `isNull`, `isArray`, `isBoxed`, `unbox`, `getKeyInfo`. [ForeignAccess](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/interop/ForeignAccess.html) already provides equivalent methods: `sendIsNull`, `sendIsArray`, `sendIsBoxed`, `sendUnbox`, `sendKeyInfo`, respectively.
* Deprecated all String based API in Source and SourceSection and replaced it with CharSequence based APIs. Automated migration with Jackpot rules is available (run `mx jackpot --apply`).
* Added [Source.Builder.language](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/source/Source.Builder.html#language-java.lang.String-) and [Source.getLanguage](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/source/Source.html#getLanguage--) to be able to set/get source langauge in addition to MIME type.
* Added the [inCompilationRoot](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/CompilerDirectives.html#inCompilationRoot--) compiler directive.
* Deprecated TruffleBoundary#throwsControlFlowException and introduced TruffleBoundary#transferToInterpreterOnException.

## Version 0.27
16-Aug-2017

* The Truffle API now depends on the Graal SDK jar to also be on the classpath. 
* Added an implementation of org.graalvm.polyglot API in Truffle. 
* API classes in com.oracle.truffe.api.vm package will soon be deprecated. Use the org.graalvm.polyglot API instead.
* Added [SourceSectionFilter.Builder](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/instrumentation/SourceSectionFilter.Builderhtml).`rootNameIs(Predicate<String>)` to filter for source sections based on the name of the RootNode.
* Added [AllocationReporter](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/instrumentation/AllocationReporter.html) as a service for guest languages to report allocation of guest language values.
* Added [Instrumenter.attachAllocationListener](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/instrumentation/Instrumenter.html#attachAllocationListener-com.oracle.truffle.api.instrumentation.AllocationEventFilter-T-), [AllocationEventFilter](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/instrumentation/AllocationEventFilter.html), [AllocationListener](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/instrumentation/AllocationListener.html) and [AllocationEvent](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/instrumentation/AllocationEvent.html) for profilers to be able to track creation and size of guest language values.
* Added [RootNode.getCurrentContext](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/nodes/RootNode.html), [TruffleLanguage.getCurrentLanguage(Class)](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html), [TruffleLanguage.getCurrentContext(Class)](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html) to allow static lookups of the language and context.
* Added an id property to [TruffleLanguage.Registration](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.Registration#id) to specify a unique identifier for each language. If not specified getName().toLowerCase() will be used. The registration id will be mandatory in future releases.
* Added an internal property to [TruffleLanguage.Registration](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.Registration#internal) to specify whether a language is intended for internal use only. For example the Truffle Native Function Interface is a language that should be used from other languages only.
* Added an internal property to [TruffleInstrument.Registration](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/instrumentation/TruffleInstrument.Registration#internal) to specify whether a internal is intended for internal use by other instruments or languages only. 
* Added the ability to describe options for languages and instruments using [TruffleLanguage.getOptionDescriptors()](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html) and [TruffleInstrument.getOptionDescriptors](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/instrumentation/TruffleInstrument.html). User provided options are available to the language using TruffleLanguage.Env.getOptions() and TruffleInstrument.Env.getOptions().
* Added JavaInterop.isJavaObject(TruffleObject) and JavaInterop.asJavaObject(TruffleObject) to check and convert back to host language object from a TruffleObject.
* Added [TruffleException](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/TruffleException.html) to allow languages to throw standardized error information. 
* [Guest language stack traces](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/TruffleStackTraceElement.html) are now collected automatically for each exception thrown and passed through a CallTarget. 
* Added RootNode.isInternal to indicate if a RootNode is considered internal and should not be shown to the guest language programmer.
* Added TruffleLanguage.lookupSymbol to be implemented by languages to support language agnostic lookups in the top-most scope.
* Added TruffleLanguage.Env.getApplicationArguments() to access application arguments specified by the user.
* Added [@Option](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/Option.html) annotation to allow simple declaration of options in TruffleLanguage or TruffleInstrument subclasses.
* Added [TruffleLanguage.RunWithPolyglotRule](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/tck/TruffleRunner.RunWithPolyglotRule.html) JUnit rule to allow running unit tests in the context of a polyglot engine.
* Added implementationName property to [TruffleLanguage.Registration](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.Registration#implementationName) to specify a human readable name of the language implementation name.
* Added TruffleLanguage.Env.lookupSymbol(String) to be used by other languages to support language lookups in their top-most scope.
* Added TruffleLanguage.Env.lookupHostSymbol(String) to be used by other languages to support language lookups from the host language.
* Added TruffleLanguage.Env.isHostLookupAllowed() to find out whether host lookup is generally allowed.
* Added Node#notifyInserted(Node) to notify the instrumentation framework about changes in the AST after the first execution.
* Added TruffleLanguage.Env.newContextBuilder() that allows guest languages to create inner language contexts/environments by returning TruffleContext instances.
* Added a concept of breakpoints shared accross sessions, associated with Debugger instance: [Debugger.install](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/Debugger.html#install-com.oracle.truffle.api.debug.Breakpoint-), [Debugger.getBreakpoints](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/Debugger.html#getBreakpoints--) and a possibility to listen on breakpoints changes: [Debugger.PROPERTY_BREAKPOINTS](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/Debugger.html#PROPERTY_BREAKPOINTS), [Debugger.addPropertyChangeListener](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/Debugger.html#addPropertyChangeListener-java.beans.PropertyChangeListener-) and [Debugger.removePropertyChangeListener](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/Debugger.html#removePropertyChangeListener-java.beans.PropertyChangeListener-). [Breakpoint.isModifiable](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/Breakpoint.html#isModifiable--) added to be able to distinguish the shared read-only copy of installed Breakpoints.
* [TruffleInstrument.Env.getLanguages()](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/instrumentation/TruffleInstrument.Env.html#getLanguages--) returns languages by their IDs instead of MIME types when the new polyglot API is used.
* Deprecated [ExactMath.addExact(int, int)](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/ExactMath.html#addExact-int-int-), [ExactMath.addExact(long, long)](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/ExactMath.html#addExact-long-long-), [ExactMath.subtractExact(int, int)](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/ExactMath.html#subtractExact-int-int-), [ExactMath.subtractExact(long, long)](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/ExactMath.html#subtractExact-long-long-), [ExactMath.multiplyExact(int, int)](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/ExactMath.html#multiplyExact-int-int-), [ExactMath.multiplyExact(long, long)](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/ExactMath.html#multiplyExact-long-long-). Users can replace these with java.lang.Math utilities of same method names.

## Version 0.26
18-May-2017

* Language can provide additional services and instruments can [look them up](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/instrumentation/TruffleInstrument.Env.html#lookup).
* Renamed `DebugValue.isWriteable` to [DebugValue.isWritable](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/DebugValue.html#isWritable--) to fix spelling.
* [Breakpoint.setCondition](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/Breakpoint.html#setCondition-java.lang.String-) does not throw the IOException any more.
* Added new message [Message.KEY_INFO](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/interop/Message.html#KEY_INFO), and an argument to [Message.KEYS](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/interop/Message.html#KEYS) specifying whether internal keys should be provided. The appropriate foreign access [ForeignAccess.sendKeyInfo](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/interop/ForeignAccess.html#sendKeyInfo-com.oracle.truffle.api.nodes.Node-com.oracle.truffle.api.interop.TruffleObject-java.lang.Object-), [ForeignAccess.sendKeys](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/interop/ForeignAccess.html#sendKeys-com.oracle.truffle.api.nodes.Node-com.oracle.truffle.api.interop.TruffleObject-boolean-) and a new factory [ForeignAccess.Factory26](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/interop/ForeignAccess.Factory26.html).
* A new [KeyInfo](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/interop/KeyInfo.html) utility class added to help with dealing with bit flags.
* Added new Java interop utility methods: [JavaInterop.getKeyInfo](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/interop/java/JavaInterop.html#getKeyInfo-com.oracle.truffle.api.interop.TruffleObject-java.lang.Object-) and [JavaInterop.getMapView](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/interop/java/JavaInterop.html#getMapView-java.util.Map-boolean-).
* Added [metadata](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/metadata/package-summary.html) package, intended for APIs related to guest language structure and consumed by tools.
* Added [ScopeProvider](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/metadata/ScopeProvider.html) to provide a hierarchy of scopes enclosing the given node. The scopes are expected to contain variables valid at the associated node.
* Added [Scope](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/metadata/Scope.html) for instruments to get a list of scopes enclosing the given node. The scopes contain variables valid at the provided node.
* Added [DebugScope](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/DebugScope.html), [DebugStackFrame.getScope](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/DebugStackFrame.html#getScope--) and [DebugValue.getScope](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/DebugValue.html#getScope--) to allow debuggers to retrieve the scope information and associated variables.
* Deprecated [DebugStackFrame.iterator](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/DebugStackFrame.html) and [DebugStackFrame.getValue](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/DebugStackFrame.html), [DebugStackFrame.getScope](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/DebugStackFrame.html#getScope--) is to be used instead.
* Added [Cached.dimensions()](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/dsl/Cached.html) to specify compilation finalness of cached arrays.
* [SuspendedEvent.prepareStepOut](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/SuspendedEvent.html#prepareStepOut-int-) has a `stepCount` argument for consistency with other prepare methods. The no-argument method is deprecated.
* Multiple calls to `SuspendedEvent.prepare*()` methods accumulate the requests to create a composed action. This allows creation of debugging meta-actions.
* [JavaInterop.toJavaClass](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/interop/java/JavaInterop.html#toJavaClass) can find proper Java class for a wrapped object
* Added environment methods TruffleLanguage.Env.getLanguages(), TruffleLanguage.Env.getInstruments(), TruffleInstrument.Env.getLanguages(), TruffleInstrument.Env.getInstruments() that allows languages or instruments to inspect some basic information about other installed languages or instruments.
* Added lookup methods TruffleLanguage.Env.lookup(LanguageInfo, Class), TruffleLanguage.Env.lookup(InstrumentInfo, Class), TruffleInstrument.Env.lookup(LanguageInfo, Class) and TruffleInstrument.Env.lookup(InstrumentInfo, Class) that allows the exchange of services between instruments and languages.
* Added [EventContext.isLanguageContextInitialized](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/instrumentation/EventContext.html#isLanguageContextInitialized--) to be able to test language context initialization in instruments.
* Added [SuspensionFilter](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/SuspensionFilter.html) class, [DebuggerSession.setSteppingFilter](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/DebuggerSession.html#setSteppingFilter-com.oracle.truffle.api.debug.SuspensionFilter-) and [SuspendedEvent.isLanguageContextInitialized](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/SuspendedEvent.html#isLanguageContextInitialized--) to be able to ignore language context initialization during debugging.

## Version 0.25
3-Apr-2017

* Added [Instrumenter.attachOutConsumer](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/instrumentation/Instrumenter.html#attachOutConsumer-T-) and [Instrumenter.attachErrConsumer](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/instrumentation/Instrumenter.html#attachErrConsumer-T-) to receive output from executions run in the associated PolyglotEngine.
* [JavaInterop.asTruffleObject](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/interop/java/JavaInterop.html#asTruffleObject-java.lang.Object-) lists methods as keys
* Deprecated `TypedObject` interface
* Added [PolyglotRuntime](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/vm/PolyglotRuntime.html) for global configuration and to allow engines share resources. The runtime of a PolyglotEngine can be configured using [PolyglotEngine](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/vm/PolyglotEngine.html)`.newBuilder().runtime(runtime).build()`.
* The `getInstruments()` method has been moved from the [PolyglotEngine](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/vm/PolyglotEngine.html) to [PolyglotRuntime](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/vm/PolyglotRuntime.html).
* [TruffleLanguage](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html) now requires a public default constructor instead of a singleton field named INSTANCE.
* [TruffleLanguage](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html) now requires a public no argument constructor instead of a singleton field named INSTANCE.
* The [TruffleLanguage](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html) instance can now be used to share code and assumptions between engine instances. See the TruffleLanguage javadoc for details.
* Added a new constructor to [RootNode](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/nodes/RootNode.html) with a [TruffleLanguage](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html) instance as argument. The current constructor was deprecated.  
* Added [RootNode.getLanguage(Class)](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/nodes/RootNode.html) to access the current language implementation instance.
* Added [RootNode.getLanguageInfo](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/nodes/RootNode.html) to access public information about the associated language.
* Added [TruffleLanguage.ContextReference](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html) class and [TruffleLanguage.getContextReference](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html).
* Added [Value.getMetaObject](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/vm/TruffleLanguage.html) and [Value.getSouceLocation](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/vm/TruffleLanguage.html)
* Deprecated [RootNode.getExecutionContext](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/nodes/RootNode.html)
* Deprecated [TruffleLanguage.createFindContextNode](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html) and [TruffleLanguage.findContext](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html).
* Deprecated [Node.getLanguage](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/nodes/Node.html).
* Deprecated [MessageResolution.language](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/nodes/Node.html) without replacement. (jackpot rule available)
* Deprecated [ExecutionContext](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/ExecutionContext.html), use RootNode#getCompilerOptions().
* Added [TruffleInstrument.Registration.services()](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/instrumentation/TruffleInstrument.Registration#services) to support declarative registration of services
* Deprecated internal class DSLOptions. Will be removed in the next release.
* Deprecated [Shape.getData()](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/object/Shape.html) and [ObjectType.createShapeData(Shape)](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/object/ObjectType.html) without replacement.
* Added [TruffleRunner](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/tck/TruffleRunner.html) JUnit runner for unit testing Truffle compilation.

## Version 0.24
1-Mar-2017
* Added possibility to activate/deactivate breakpoints via [DebuggerSession.setBreakpointsActive](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/DebuggerSession.html#setBreakpointsActive-boolean-) and get the active state via [DebuggerSession.isBreakpointsActive](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/DebuggerSession.html#isBreakpointsActive--).
* Deprecated the send methods in [ForeignAccess](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/interop/ForeignAccess.html) and added a a new version that does not require a frame parameter. ([Jackpot](https://bitbucket.org/jlahoda/jackpot30/wiki/Home) rule for automatic migration available)
* Made [@NodeChild](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/dsl/NodeChild.html) and [@NodeField](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/dsl/NodeField.html) annotations repeatable
* Added Truffle Native Function Interface.
* Abstract deprecated methods in [NodeClass](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/nodes/NodeClass.html) have default implementation
* Added [RootNode.cloneUninitialized](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/nodes/RootNode.html) that allows an optimizing runtime to efficiently create uninitialized clones of root nodes on demand.

## Version 0.23
1-Feb-2017
* Incompatible: Removed most of deprecated APIs from the [com.oracle.truffle.api.source package](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/source/package-summary.html).
* Enabled the new flat generated code layout for Truffle DSL as default. To use it just recompile your guest language with latest Truffle annotation processor. The new layout uses a bitset to encode the states of specializations instead of using a node chain for efficiency. The number of specializations per operation is now limited to 127 (with no implicit casts used). All changes in the new layout are expected to be compatible with the old layout. The optimization strategy for implicit casts and fallback handlers changed and might produce different peak performance results.
* Deprecated the frame argument for [IndirectCallNode](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/nodes/IndirectCallNode.html) and [DirectCallNode](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/nodes/DirectCallNode.html). The frame argument is no longer required.
* Deprecated [FrameInstance](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/frame/FrameInstance.html).getFrame(FrameAccess, boolean). Usages need to be replaced by FrameInstance.getFrame(FrameAccess). The slowPath parameter was removed without replacement.
* Deprecated FrameAccess.NONE without replacement. 
* [FrameInstance](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/frame/FrameInstance.html).getFrame now throws an AssertionError if a local variable of a frame was written in READ_ONLY frame access mode.

## Version 0.22
13-Jan-2017
* [TruffleLanguage.isVisible](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html#isVisible-C-java.lang.Object-) allows languages to control printing of values in interactive environments
* [PolyglotEngine](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/vm/PolyglotEngine.html)`.findGlobalSymbols` that returns `Iterable`
* [TruffleLanguage](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html)`.importSymbols` that returns `Iterable`
* [RootNode.setCallTarget](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/nodes/RootNode.html#setCallTarget-com.oracle.truffle.api.RootCallTarget-) is deprecated
* Generic parsing method [TruffleLanguage](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html).`parse(`[ParsingRequest](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.ParsingRequest.html) `)` replaces now deprecated multi-argument `parse` method.
* Added [TruffleLanguage.findMetaObject](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html#findMetaObject-C-java.lang.Object-) and [DebugValue.getMetaObject](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/DebugValue.html#getMetaObject--) to retrieve a meta-object of a value.
* Added [TruffleLanguage.findSourceLocation](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html#findSourceLocation-C-java.lang.Object-) and [DebugValue.getSourceLocation](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/DebugValue.html#getSourceLocation--) to retrieve a source section where a value is declared.
* Added [TruffleLanguage.Registration.interactive()](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.Registration.html#interactive--) and [PolyglotEngine.Language.isInteractive()](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/vm/PolyglotEngine.Language.html#isInteractive--) to inform about language interactive capability
* Deprecated the @[Specialization](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/dsl/Specialization.html) contains attribute and renamed it to replaces.
* Deprecated @[ShortCircuit](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/dsl/ShortCircuit.html) DSL annotation without replacement. It is recommended to implement short circuit nodes manually without using the DSL.
* Added Truffle DSL [introspection API](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/dsl/Introspection.html) that provides runtime information for specialization activation and cached data.

## Version 0.21
6-Dec-2016
* Added [Source.isInteractive()](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/source/Source.html#isInteractive--) to inform languages of a possibility to use polyglot engine streams during execution.
* Unavailable [SourceSection](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/source/SourceSection.html)s created by different calls to createUnavailableSection() are no longer equals(). This means builtins can share a single Source and call createUnavailableSection() for each builtin to be considered different in instrumentation.

## Version 0.20
23-Nov-2016
* Deprecated [Node.getAtomicLock()](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/nodes/Node.html#getAtomicLock--) and replaced it with Node.getLock() which returns a Lock.
* Switching the source and target levels to 1.8
* Significant improvements in Java/Truffle interop

## Version 0.19
27-Oct-2016
* New helper methods in [JavaInterop](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/interop/java/JavaInterop.html): `isArray`, `isBoxed`, `isNull`, `isPrimitive`, `unbox`, `asTruffleValue`.
* Relaxed the restrictions for calling methods on [SuspendedEvent](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/SuspendedEvent.html) and [DebugStackFrame](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/DebugStackFrame.html) from other threads than the execution thread. Please see the javadoc of the individual methods for details.

## Version 0.18
1-Oct-2016
* Added [Instrumenter](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/instrumentation/Instrumenter.html).querySourceSections(SourceSectionFilter) to get a filtered list of loaded instances.
* Added [SourceSectionFilter](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/instrumentation/SourceSectionFilter.html).ANY, which always matches.
* Added [Message.KEYS](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/interop/Message.html#KEYS) to let languages enumerate properties of its objects
* Deprecated [LineLocation](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/source/LineLocation.html), [SourceSection](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/source/SourceSection.html).getLineLocation(), [Source](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/source/Source.html).createLineLocation(int) without replacement.
* Deprecated [SourceSection](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/source/SourceSection.html).getShortDescription(); users can replace uses with their own formatting code.
* Deprecated [SourceSection](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/source/SourceSection.html).createUnavailable(String, String) and replaced it with.
* Added [Source](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/source/Source.html).createUnavailableSection(), [SourceSection](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/source/SourceSection.html).isAvailable() to find out whether a source section is available.
* [SourceSection](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/source/SourceSection.html).createSourceSection(int,int) now only throws IllegalArgumentExceptions if indices that are out of bounds with the source only when assertions (-ea) are enabled.
* Deprecated [Source](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/source/Source.html).createSection(int, int, int, int) 

## Version 0.17
1-Sep-2016

#### Removals, Deprecations and Breaking Changes

* This release removes many deprecated APIs and is thus slightly incompatible
  * Remove deprecated instrumentation API package `com.oracle.truffle.api.instrument` and all its classes.
  * Remove deprecated API method [TruffleLanguage](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html)`.isInstrumentable(Node)`, `TruffleLanguage.getVisualizer()`, `TruffleLanguage.createWrapperNode()`, `TruffleLanguage.Env.instrumenter()`, `RootNode.applyInstrumentation()`
  * Remove deprecated API [Debugger](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/Debugger.html)`.setTagBreakpoint`
  * Remove deprecated API [RootNode](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/nodes/RootNode.html)`.applyInstrumentation`
  * Remove deprecated tagging API in [SourceSection](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/source/SourceSection.html) and [Source](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/source/Source.html).

* [PolyglotEngine](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/vm/PolyglotEngine.html)
`eval` method and few similar ones no longer declare `throws IOException`.
The I/O now only occurs when operating with [Source](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/source/Source.html).
The evaluation of already loaded sources doesn't need to perform any I/O operations and
thus it makes little sense to require callers to handle the `IOException`.
This change is binary compatible, yet it is source *incompatible* change.
You may need to [adjust your sources](https://github.com/graalvm/fastr/commit/09ab156925d24bd28837907cc2ad336679afc7a2)
to compile.
* Deprecate support for the "identifier" associated with each [SourceSection](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/source/SourceSection.html)
* Deprecated `PolyglotEngine.Builder.onEvent(EventConsumer)` and class `EventConsumer`, debugger events are now dispatched using the `DebuggerSession`.
* [@Fallback](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/dsl/Fallback.html) does not support type specialized arguments anymore. 

#### Additions

* All debugging APIs are now thread-safe and can be used from other threads.
* Changed the debugging API to a session based model. 
  * Added [Debugger](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/Debugger.html)`.find(TruffleLanguage.Env)` to lookup the debugger when inside a guest language implementation.
  * Added [Debugger](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/Debugger.html)`.startSession(SuspendedCallback)` to start a new debugging session using a SuspendedCallback as replacement for `ExecutionEvent.prepareStepInto()`.
  * Added class [DebuggerSession](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/DebuggerSession.html) which represents a debugger session where breakpoints can be installed and the execution can be suspended and resumed.
  * Added [Breakpoint](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/Breakpoint.html)`.newBuilder` methods to create a new breakpoint using the builder pattern based on Source, URI or SourceSections.
  * Added [Breakpoint](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/Breakpoint.html)`.isResolved()` to find out whether the source location of a breakpoint is loaded by the guest language.
  * Added [Breakpoint](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/Breakpoint.html)`.isDisposed()` to find out whether a breakpoint is disposed.
  * Added [SuspendedEvent](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/SuspendedEvent.html)`.getReturnValue()` to get return values of calls during debugging.
  * Added [SuspendedEvent](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/SuspendedEvent.html)`.getBreakpoints()` to return the breakpoints that hit for a suspended event.
  * Added [SuspendedEvent](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/SuspendedEvent.html)`.getStackFrames()` to return all guest language stack frames.
  * Added [SuspendedEvent](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/SuspendedEvent.html)`.getTopStackFrame()` to return the topmost stack frame.
  * Added [SuspendedEvent](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/SuspendedEvent.html)`.getSourceSection()` to return the current guest language execution location
  * Added [SuspendedEvent](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/SuspendedEvent.html)`.getSourceSections()` to return all guest language execution locations of the current method in the AST.
  * Added class [DebugStackFrame](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/DebugStackFrame.html) which represents a guest language stack frame. Allows to get values from the current stack frame, access stack values and evaluate inline expressions.
  * Added class [DebugValue](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/DebugValue.html) which represents a value on a stack frame or the result of an evaluated expression.
  * Added class [DebuggerTester](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/DebuggerTester.html) which represents a utility for testing guest language debugger support more easily.
  * Deprecated [Breakpoint](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/Breakpoint.html)`.getCondition()` and replaced it with [Breakpoint](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/Breakpoint.html)`.getConditionExpression()` to return a String instead of a Source object.
  * Deprecated [Breakpoint](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/Breakpoint.html)`.setCondition(String)` and replaced it with [Breakpoint](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/Breakpoint.html)`.setConditionExpression(String)` to avoid throwing IOException.
  * Deprecated class `ExecutionEvent` and replaced it with [Debugger](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/Debugger.html)`.startSession(SuspendedCallback)`
  * Deprecated [Debugger](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/Debugger.html) methods setLineBreakpoint, getBreakpoints, pause. Replacements are available in the DebuggerSession class
  * Deprecated [Breakpoint](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/Breakpoint.html)`.getState()` to be replaced with [Breakpoint](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/Breakpoint.html)isResolved(), [Breakpoint](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/Breakpoint.html)isDisposed() and [Breakpoint](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/Breakpoint.html)`.isEnabled()`.
  * Deprecated [SuspendedEvent](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/SuspendedEvent.html)`.getNode()` and [SuspendedEvent](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/SuspendedEvent.html).getFrame() without direct replacement.
  * Deprecated [SuspendedEvent](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/SuspendedEvent.html)`.getRecentWarnings()` and replaced it with [SuspendedEvent](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/SuspendedEvent.html).getBreakpointConditionException(Breakpoint)
  * Deprecated [SuspendedEvent](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/SuspendedEvent.html)`.eval` and replaced it with `DebugStackFrame.eval(String)`
  * Deprecated [SuspendedEvent](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/SuspendedEvent.html)`.getStack()` and replaced it with [SuspendedEvent](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/SuspendedEvent.html).getStackFrames()
  * Deprecated [SuspendedEvent](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/SuspendedEvent.html)`.toString(Object, FrameInstance)` and replaced it with `DebugValue.as(String.class)`.

* [TruffleLanguage.createContext](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html#createContext-com.oracle.truffle.api.TruffleLanguage.Env-)
supports [post initialization callback](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html#initializeContext-C-)
* Added [SourceSectionFilter.Builder](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/instrumentation/SourceSectionFilter.Builderhtml).`sourceIs(SourcePredicate)` to filter for source sections with a custom source predicate.
* Added [TruffleInstrument.Env](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/instrumentation/TruffleInstrument.Env.html).`isEngineRoot(RootNode)` to find out where the context of the current evaluation ends when looking up the guest language stack trace with `TruffleRuntime.iterateFrames()`.
* Added [TruffleInstrument.Env](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/instrumentation/TruffleInstrument.Env.html).`toString(Node, Object)` to allow string conversions for objects given a Node to identify the guest language.
* Added [EventContext](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/instrumentation/EventContext.html).`lookupExecutionEventNode(EventBinding)` to lookup other execution event nodes using the binding at a source location.
* Added [Node.getAtomicLock()](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/nodes/Node.html#getAtomicLock--) to allow atomic updates that avoid creating a closure.

## Version 0.16
* [Layout](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/object/dsl/Layout.html)
  now accepts an alternative way to construct an object with the `build` method instead of `create`.
* [TruffleTCK](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/tck/TruffleTCK.html) tests simple operation on foreign objects. For example, a simple WRITE accesss, a HAS_SIZE access, or an IS_NULL access. It also tests the message resolution of Truffle language objects, which enables using them in other languages.

## Version 0.15
1-Jul-2016
* [Source](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/source/Source.html) shall be
constructed via its `newBuilder` methods. The other ways to construct or modify
source objects are now deprecated.
* [RootNode.getName](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/nodes/RootNode.html#getName--)
to provide name of a method or function it represents.
* Instruments are now [loaded eagerly](https://github.com/graalvm/truffle/commit/81018616abb0d4ae68e98b7fcd6fda7c8d0393a2) -
which has been reported as an observable behavioral change.
* The [Instrumenter](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/instrumentation/Instrumenter.html)
now allows one to observe when sources and source sections are being loaded via
[attaching a listener](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/instrumentation/Instrumenter.html#attachLoadSourceListener-com.oracle.truffle.api.instrumentation.SourceSectionFilter-T-boolean-).
* Control the way loops are exploded with a new [LoopExplosionKind](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/nodes/ExplodeLoop.LoopExplosionKind.html)
enum.
* [SuspendedEvent](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/SuspendedEvent.html#toString-java.lang.Object-com.oracle.truffle.api.frame.FrameInstance-)
provides a way to convert any value on stack to its string representation.
* [TruffleTCK](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/tck/TruffleTCK.html) checks
whether languages properly support being interrupted after a time out
* Language implementations are encouraged to mark their internal sources as
[internal](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/source/Source.html#isInternal--)

## Version 0.14
2-Jun-2016
* [Source](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/source/Source.html) has been
rewritten to be more immutable. Once (part of) content of a source is loaded, it cannot be
changed.
* Methods `fromNamedAppendableText`, `fromNamedText` and `setFileCaching` of
`Source` has been deprecated as useless or not well defined
* New method `Source`.[getURI()](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/source/Source.html#getURI--)
has been introduced and should be used as a persistent identification of `Source` rather than
existing `getName()` & co. methods. Debugger is using the `URI` to
[attach breakpoints](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/Debugger.html#setLineBreakpoint-int-java.net.URI-int-boolean-)
to not yet loaded sources
* Debugger introduces new [halt tag](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/DebuggerTags.AlwaysHalt.html) to
make it easier to simulate concepts like JavaScript's `debugger` statement
* Debugger can be paused via the Debugger.[pause](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/Debugger.html#pause--)
method
* [@CompilationFinal](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/CompilerDirectives.CompilationFinal.html)
annotation can now specify whether the finality applies to array elements as well
* [TruffleTCK](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/tck/TruffleTCK.html) has been
enhanced to test behavior of languages with respect to foreign array objects


## Version 0.13
22-Apr-2016
* `AcceptMessage` has been deprecated, replaced by
[MessageResolution](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/interop/MessageResolution.html) &
[co](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/interop/Resolve.html). annotations.
Now all message-oriented annotations need to be placed in a single source file.
That simplifies readability as well as improves incremental compilation in certain systems.
* Deprecated `Node.assignSourceSection` removed. This reduces the amount of memory
occupied by [Node](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/nodes/Node.html)
instance.
* `PolyglotEngine.Value.execute` is now as fast as direct `CallTarget.call`.
Using the [PolyglotEngine](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/vm/PolyglotEngine.html)
abstraction now comes with no overhead. Just [JPDA debuggers](http://wiki.apidesign.org/wiki/Truffle#Debugging_from_NetBeans)
need to
[turn debugging on](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/debug/Debugger.html#find-com.oracle.truffle.api.vm.PolyglotEngine-)
explicitly.
* Sharing of efficient code/AST between multiple instances of
[PolyglotEngine](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/vm/PolyglotEngine.html)
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
