# Truffle Changelog

This changelog summarizes major changes between Truffle versions relevant to languages implementors building upon the Truffle framework. The main focus is on APIs exported by Truffle.

## Version 20.3.0
* Added `RepeatingNode.initialLoopStatus` and `RepeatingNode.shouldContinue` to allow defining a custom loop continuation condition.


## Version 20.2.0
* Added new internal engine option `ShowInternalStackFrames` to show internal frames specific to the language implementation in stack traces.
* Added new identity APIs to `InteropLibrary`:
    * `hasIdentity(Object receiver)` to find out whether an object specifies identity
	* `isIdentical(Object receiver, Object other, InteropLibrary otherLib)` to compare the identity of two object
	* `isIdenticalOrUndefined(Object receiver, Object other)` export to specify the identity of an object.
	* `identityHashCode(Object receiver)` useful to implement maps that depend on identity.
* Added `TriState` utility class represents three states TRUE, FALSE and UNDEFINED.
* Added `InteropLibrary.getUncached()` and `InteropLibrary.getUncached(Object)` short-cut methods for convenience.
* Enabled by default the new inlining heuristic in which inlining budgets are based on Graal IR node counts and not Truffle Node counts.
* Added `ConditionProfile#create()` as an alias of `createBinaryProfile()` so it can be used like `@Cached ConditionProfile myProfile`. 
* Improved `AssumedValue` utility class: Code that reads the value but can not constant fold it does not need to deopt when the value changes.
* A `TruffleFile` for an empty path is no more resolved to the current working directory.
* Added [`SourceBuilder.canonicalizePath(boolean)`](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/source/Source.SourceBuilder.html) to control whether the `Source#getPath()` should be canonicalized.
* Deprecated and renamed `TruffleFile.getMimeType` to [TruffleFile.detectMimeType](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleFile.html#detectMimeType--). The new method no longer throws `IOException` but returns `null` instead.
* The languages are responsible for stopping and joining the stopped `Thread`s in the [TruffleLanguage.finalizeContext](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html#finalizeContext-C-).
* Added Truffle DSL `@Bind` annotation to common out expression for use in guards and specialization methods.
* Added the ability to disable adoption for DSL cached expressions with type node using `@Cached(value ="...", weak = true)`.
* Added an option not to adopt the parameter annotated by @Cached, using `@Cached(value ="...", adopt = false)`.
* Added `TruffleWeakReference` utility to be used on partial evaluated code paths instead of the default JDK `WeakReference`.
* Removed deprecated API in `com.oracle.truffle.api.source.Source`. The APIs were deprecated in 19.0.
* Added `CompilerDirectives.shouldNotReachHere()` as a short-cut for languages to indicate that a path should not be reachable neither in compiled nor interpreted code paths.
* All subclasses of `InteropException` do no longer provide a Java stack trace. They are intended to be thrown, immediately caught by the caller and not re-thrown. As a result they can now be allocated on compiled code paths and do no longer require a `@TruffleBoundary` or `transferToInterpreterAndInvalidate()` before use. Languages are encouraged to remove `@TruffleBoundary` annotations or leading `transferToInterpreterAndInvalidate()` method calls before interop exceptions are thrown. 
* All `InteropException` subclasses now offer a new `create` factory method to provide a cause. This cause should only be used if user provided guest application code caused the problem.
* The use of `InteropException.initCause` is now deprecated for performance reasons. Instead pass the cause when the `InteropException` is constructed. The method `initCause` will throw `UnsupportedOperationException` in future versions. Please validate all calls to `Throwable.initCause` for language or tool implementation code.
* Added [TruffleFile.isSameFile](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleFile.html#isSameFile-com.oracle.truffle.api.TruffleFile-java.nio.file.LinkOption...-) method to test if two `TruffleFile`s refer to the same physical file.
* Added new `EncapsulatingNodeReference` class to lookup read and write the current encapsulating node. Deprecated encapsulating node methods in `NodeUtil`.
* Added support for subclassing `DynamicObject` so that guest languages can directly base their object class hierarchy on it, add fields, and use `@ExportLibrary` on subclasses. Guest language object classes should implement `TruffleObject`.
* Added new [DynamicObjectLibrary](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/object/DynamicObjectLibrary.html) API for accessing and mutating properties and the shape of `DynamicObject` instances. This is the recommended API from now on. Other, low-level property access APIs will be deprecated and removed in a future release.

## Version 20.1.0
* Added `@GenerateLibrary(dynamicDispatchEnabled = false)` that allows to disable dynamic dispatch semantics for a library. The default is `true`.
* Added ability to load external default exports for libraries using a service provider. See `GenerateLibrary(defaultExportLookupEnabled = true)`.
* The use of `@NodeField` is now permitted in combination with `@GenerateUncached`, but it throws UnsupportedOperationException when it is used.
* It is now possible to specify a setter with `@NodeField`. The generated field then will be mutable.
* Removed deprecated interoperability APIs that were deprecated in 19.0.0. 
* Removed deprecated instrumentation APIs that were deprecated in 0.33
* The `PerformanceWarningsAreFatal` and `TracePerformanceWarnings` engine options take a comma separated list of performance warning types. Allowed warning types are `call` to enable virtual call warnings, `instanceof` to enable virtual instance of warnings and `store` to enables virtual store warnings. There are also `all` and `none` types to enable (disable) all performance warnings.
* Added [DebugValue#getRawValue()](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/DebugValue.html) for raw guest language object lookup from same language.
* Added [DebugStackFrame#getRawNode()](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/DebugStackFrame.html) for root node lookup from same language.
* Added [DebugException#getRawException()](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/DebugException.html) for raw guest language exception lookup from same language.
* Added [DebugStackFrame#getRawFrame()](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/DebugStackFrame.html) for underlying frame lookup from same language.
* Added `TruffleInstrument.Env.getPolyglotBindings()` that replaces now deprecated `TruffleInstrument.Env.getExportedSymbols()`.
* Added `@ExportLibrary(transitionLimit="3")` that allows the accepts condition of exported libraries to transition from true to false for a library created for a receiver instance. This is for example useful to export messages for array strategies. 
* Added `CompilationFailureAction` engine option which deprecates `CompilationExceptionsArePrinted `, `CompilationExceptionsAreThrown`, `CompilationExceptionsAreFatal` and `PerformanceWarningsAreFatal` options.
* Added `TreatPerformanceWarningsAsErrors` engine option which deprecates the `PerformanceWarningsAreFatal` option. To replace the `PerformanceWarningsAreFatal` option use the `TreatPerformanceWarningsAsErrors` with `CompilationFailureAction` set to `ExitVM`.
* Added `bailout` into performance warning kinds used by `TracePerformanceWarnings`, `PerformanceWarningsAreFatal` and `CompilationExceptionsAreFatal` options.
* Added [Option.deprecationMessage](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/Option.html#deprecationMessage--) to set the option deprecation reason.
* `engine.Mode` is now a supported option and no longer experimental.
* Added new meta-data APIs to `InteropLibrary`:
	* `has/getLanguage(Object receiver)` to access the original language of an object.
	* `has/getSourceLocation(Object receiver)` to access the source location of an object (e.g. of function or classes).
	* `toDisplayString(Object receiver, boolean allowsSideEffect)` to produce a human readable string.
	* `has/getMetaObject(Object receiver)` to access the meta-object of an object.
	* `isMetaObject(Object receiver)` to find out whether an object is a meta-object (e.g. Java class)
	* `getMetaQualifiedName(Object receiver)` to get the qualified name of the meta-object
	* `getMetaSimpleName(Object receiver)` to get the simple name of a the meta-object
	* `isMetaInstance(Object receiver, Object instance)` to check whether an object is an instance of a meta-object.
* Added `TruffleLanguage.getLanguageView` that allows to wrap values to add language specific information for primitive and foreign values.
* Added `TruffleLanguage.getScopedView` that allows to wrap values to add scoping and visibility to language values.
* Added `TruffleInstrument.Env.getScopedView` and `TruffleInstrument.Env.getLanguageView` to access language and scoped views from instruments.
* Added `TruffleInstrument.Env.getLanguageInfo` to convert language classes to `LanguageInfo`.
* Deprecated `TruffleLanguage.findMetaObject`, `TruffleLanguage.findSourceLocation`, `TruffleLanguage.toString` and `TruffleLanguage.isObjectOfLanguage`. Use the new interop APIs and language views as replacement.
* Added support for the value conversions of [DebugValue](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/DebugValue.html) that provide the same functionality as value conversions on [Value](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Value.html).
* Added [DebugValue#toDisplayString](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/DebugValue.html#toDisplayString--) to convert the value to a language-specific string representation.
* Deprecated `DebugValue#as`, other conversion methods should be used instead.
* Clarify [InteropLibrary](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/InteropLibrary.html) javadoc documentation of message exceptions. [UnsupportedMessageException](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/UnsupportedMessageException.html) is thrown when the operation is never supported for the given receiver type. In other cases [UnknownIdentifierException](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/UnknownIdentifierException.html) or [InvalidArrayIndexException](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/InvalidArrayIndexException.html) are thrown.
* Added [TruffleLanguage.Env.initializeLanguage](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.Env.html#initializeLanguage-com.oracle.truffle.api.nodes.LanguageInfo-) method to force language initialization.
* Values of `NAME` properties of [ReadVariableTag](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/StandardTags.ReadVariableTag.html#NAME) and [WriteVariableTag](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/StandardTags.WriteVariableTag.html#NAME) extended to allow an object or an array of objects with name and source location.
* Added support for asynchronous stack traces: [TruffleLanguage.Env.getAsynchronousStackDepth()](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.Env.html#getAsynchronousStackDepth--), [RootNode.findAsynchronousFrames()](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/RootNode.html#findAsynchronousFrames-com.oracle.truffle.api.frame.Frame-), [TruffleInstrument.Env.setAsynchronousStackDepth()](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/TruffleInstrument.Env.html#setAsynchronousStackDepth-int-), [TruffleStackTrace.getAsynchronousStackTrace()](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleStackTrace.html#getAsynchronousStackTrace-com.oracle.truffle.api.CallTarget-com.oracle.truffle.api.frame.Frame-), [DebuggerSession.setAsynchronousStackDepth()](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/DebuggerSession.html#setAsynchronousStackDepth-int-), [SuspendedEvent.getAsynchronousStacks()](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/SuspendedEvent.html#getAsynchronousStacks--), [DebugException.getDebugAsynchronousStacks()](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/DebugException.html#getDebugAsynchronousStacks--).

## Version 20.0.0
* Add [Layout#dispatch()](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/object/dsl/Layout.html#dispatch--) to be able to generate override of `ObjectType#dispatch()` method in the generated inner \*Type class.
* Deprecated engine options engine.InvalidationReprofileCount and engine.ReplaceReprofileCount. They no longer have any effect. There is no longer reprofiling after compilation. 
* Added [DebuggerSession.{suspend(), suspendAll,resume()}](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/DebuggerSession.html) to allow suspending and resuming threads.
* Add new loop explosion mode [LoopExplosionKind#FULL_UNROLL_UNTIL_RETURN](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/ExplodeLoop.LoopExplosionKind.html#FULL_UNROLL_UNTIL_RETURN), which can be used to duplicate loop exits during unrolling until function returns.
* The default [LoopExplosionKind](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/ExplodeLoop.LoopExplosionKind.html) for `@ExplodeLoop` changed from `FULL_UNROLL` to `FULL_UNROLL_UNTIL_RETURN`, which we believe is more intuitive. We recommend reviewing your usages of `@ExplodeLoop`, especially those with `return`, `break` and `try/catch` in the loop body as those might duplicate more code than before.
* The `TruffleCheckNeverPartOfCompilation` option when building a native image is now enabled by default, ensuring `neverPartOfCompilation()` is not reachable for runtime compilation. Use `CompilerDirectives.bailout()` if you want to test when a compilation fails, otherwise avoid `neverPartOfCompilation()` in code reachable for runtime compilation (e.g., by using `@TruffleBoundary`).
* The `DirectoryStream` created by a relative `TruffleFile` passes relative `TruffleFile`s into the `FileVisitor`, even when an explicit [current working directory was set](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.Env.html#setCurrentWorkingDirectory-com.oracle.truffle.api.TruffleFile-).
* Added `DebuggerTester.startExecute()` that allows to execute an arbitrary sequence of commands on the background thread.
* Time specification in `InteropLibrary` relaxed to allow a fixed timezone when no date is present.
* `TruffleLogger.getLogger` throws an `IllegalArgumentException` when given `id` is not a valid language or instrument id.
* [Node#getEncapsulatingSourceSection()](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/Node.html#getEncapsulatingSourceSection--) is no longer a fast-path method, because `getSourceSection()` is not fast-path.
* The algorithm used to generate a unique [URI](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/source/Source.html#getURI--) for a `Source` built without an `URI` was changed to SHA-256.
* Added [ExportLibrary.delegateTo](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/library/ExportLibrary.html#delegateTo--) attribute that allows to delegate all messages of a library to value of a final delegate field. This can be used in combination with `ReflectionLibrary` to improve the ability to build wrappers.
* `ReadVariableTag` and `WriteVariableTag` added to [StandardTags](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/StandardTags.html).

* Truffle TCK now checks that instrumentable nodes are not used in the context of a Library.
* Getter to check whether [TruffleContext](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleContext.html#isEntered--) is activated or not.
* All Truffle Graal runtime options (-Dgraal.) will be deprecated with 20.1. The Truffle runtime options are no longer specified as Graal options (-Dgraal.). The Graal options must be replaced by corresponding engine options specified using [polyglot API](https://www.graalvm.org/truffle/javadoc/org/graalvm/polyglot/Engine.Builder.html#option-java.lang.String-java.lang.String-). The `TRUFFLE_STRICT_OPTION_DEPRECATION` environment variable can be used to detect usages of deprecated Graal options. When the `TRUFFLE_STRICT_OPTION_DEPRECATION` is set to `true` and the deprecated Graal option is used the Truffle runtime throws an exception listing the used deprecated options and corresponding replacements.


## Version 19.3.0
* Added ability to obtain an [Internal Truffle File](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.Env.html#getInternalTruffleFile-java.lang.String-). The internal file is located in the language home directories and it's readable even when IO is not allowed by the Context.
* Deprecated `TruffleLanguage.Env.getTruffleFile` use [getInternalTruffleFile](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.Env.html#getInternalTruffleFile-java.lang.String-) for language standard library files located in language home or [getPublicTruffleFile](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.Env.html#getPublicTruffleFile-java.lang.String-) for user files.
* Added primitive specializations to `CompilerAsserts.partialEvaluationConstant()`.
* Added the new `execute` method to `LoopNode`, which allows loops to return values.
* Added support for temporary [files](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.Env.html#createTempFile-com.oracle.truffle.api.TruffleFile-java.lang.String-java.lang.String-java.nio.file.attribute.FileAttribute...-) and [directories](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.Env.html#createTempDirectory-com.oracle.truffle.api.TruffleFile-java.lang.String-java.nio.file.attribute.FileAttribute...-).
* Threads created by the embedder may now be collected by the GC before they can be [disposed](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html#disposeThread-C-java.lang.Thread-). If languages hold onto thread objects exposed via `initializeThread` they now need to do so with `WeakReference` to avoid leaking thread instances.
* Support boolean literals in DSL expressions used in [@Specialization](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/dsl/Specialization) and [@Cached](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/dsl/Cached) fields.
* Added standard [block node](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/BlockNode.html) for language implementations. Using the block node allows the optimizing runtime to split big blocks into multiple compilation units. This optimization may be enabled using `--engine.PartialBlockCompilation` (on by default) and configured using `--engine.PartialBlockCompilationSize` (default 3000).
* Added new experimental inlining heuristic in which inlining budgets are based on Graal IR node counts and not Truffle Node counts. Enable with `-Dgraal.TruffleLanguageAgnosticInlining=true`.
* Deprecated `DynamicObject#isEmpty()`, `DynamicObject#size()`; use `Shape#getPropertyCount()` instead.
* Deprecated `Shape#getPropertyList(Pred)`, `Shape#getKeyList(Pred)`, `Shape#hasTransitionWithKey(Object)`, `Shape.Allocator#locationForValue(Object, EnumSet)` without replacement.
* Added [Scope.Builder#rootInstance(Object)](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/Scope.Builder.html#rootInstance-java.lang.Object-), [Scope#getRootInstance()](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/Scope.html#getRootInstance--) and [DebugScope#getRootInstance()](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/DebugScope.html#getRootInstance--) to provide an instance of guest language representation of the root node (e.g. a guest language function).
* Debugger breakpoints can be restricted to a particular root instance via [Breakpoint.Builder#rootInstance(DebugValue)](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/Breakpoint.Builder.html#rootInstance-com.oracle.truffle.api.debug.DebugValue-) and found later on via [DebugValue#getRootInstanceBreakpoints()](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/DebugValue.html#getRootInstanceBreakpoints--).
* Deprecated `TruffleLanguage.getContextReference()` as this method is inefficient in many situations. The most efficient context lookup can be achieved knowing the current AST in which it is used by calling `Node.lookupContextReference(Class)`.
* Truffle languages and instruments no longer create `META-INF/truffle` files, but generate service implementations for [TruffleLanguage.Provider](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.Provider.html) and [TruffleInstrument.Provider](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/TruffleInstrument.Provider.html) automatically. Recompiling the TruffleLanguage using the Truffle annotation processor automatically migrates the language.
* The Truffle DSL processor jar no longer requires the Truffle API or Graal SDK as a dependency. 
* Added interop messages for guest language exception objects: [InteropLibrary#isException(Object)](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/InteropLibrary.html#isException-java.lang.Object-) and [InteropLibrary#throwException(Object)](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/InteropLibrary.html#throwException-java.lang.Object-).
* [TruffleLanguage.patchContext](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html#patchContext-C-com.oracle.truffle.api.TruffleLanguage.Env-) is invoked for all languages whose contexts were created during context pre-initialization. Originally the `patchContext`  was invoked only for languages with initialized contexts.

## Version 19.2.0
* Added sub-process output (error output) [redirection into OutputStream](https://www.graalvm.org/truffle/javadoc/org/graalvm/polyglot/io/ProcessHandler.Redirect.html#stream-java.io.OutputStream-).
* Added `RootNode.getQualifiedName()` for a better distinction when printing stack traces. Languages are encouraged to implement it, in case it differs from the root name.
* Added methods to identify date, time, timezone, instant and duration values in `InteropLibrary` and TCK `TypeDescriptor`.
* Added ability to read the default time zone from the language Environment with `Env.getTimeZone()`.
* Deprecated `Env.parse` and added replacement APIs `Env.parseInternal` and `Env.parsePublic`. The new API requires to differentiate between parse calls that were invoked by the guest language user and those which are part of the internal language semantics. The separation avoids accidentally exposing access to internal languages. 
* Deprecated `Env.getLanguages()` and added replacement APIs `Env.getInternalLanguages()` and `Env.getPublicLanguages()`. 
* Added [Source.newBuilder(Source)](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/source/Source.html#newBuilder-com.oracle.truffle.api.source.Source-) that inherits Source properties from an existing Source.
* Added [RootBodyTag](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/StandardTags.RootBodyTag.html).

## Version 19.1.0
* `@GenerateUncached` is now inherited by subclasses.
* `NodeFactory` now supports `getUncachedInstance` that returns the uncached singleton.  
* Introduced Truffle process sandboxing. Added a [TruffleLanguage.Env.newProcessBuilder](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.Env.html#newProcessBuilder-java.lang.String...-) method creating a new [TruffleProcessBuilder](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/io/TruffleProcessBuilder.html) to configure and start a new sub-process.
* Added support for reading environment variables, use [TruffleLanguage.Env.getEnvironment](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.Env.html#getEnvironment--) to obtain process environment variables.
* `NodeFactory` now supports `getUncachedInstance` that returns the uncached singleton. 
* `@GenerateUncached` can now be used in combination with `@NodeChild` if execute signatures for all arguments are present.
* Removed deprecated automatic registration of the language class as a service.
* The [LanguageProvider](https://www.graalvm.org/truffle/javadoc/org/graalvm/polyglot/tck/LanguageProvider.html#createIdentityFunctionSnippet-org.graalvm.polyglot.Context-) can override the default verfication of the TCK `IdentityFunctionTest`.
* Removed deprecated and misspelled method `TruffleStackTrace#getStacktrace`.
* Removed deprecated methods`TruffleStackTraceElement#getStackTrace` and `TruffleStackTraceElement#fillIn` (use methods of `TruffleStackTrace` instead).
* `SlowPathException#fillInStackTrace` is now `final`.
* Added an ability to read a [path separator](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.Env.html#getPathSeparator--) used to separate filenames in a path list.
* `@TruffleBoundary` methods that throw but are not annotated with `@TruffleBoundary(transferToInterpreterOnException=false)` will now transfer to the interpreter only once per `CallTarget` (compilation root).
* Added [TruffleFile.setAttribute](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleFile.html#setAttribute-com.oracle.truffle.api.TruffleFile.AttributeDescriptor-T-java.nio.file.LinkOption...-) to allow languages to set file attributes.

## Version 19.0.0
* Renamed version 1.0.0 to 19.0.0

## Version 1.0.0 RC15
* This version includes a major revision of the Truffle Interoperability APIs. Most existing APIs for Truffle Interoperability were deprecated. The compatiblity layer may cause significant performance reduction for interoperability calls. 
	* Please see the [Interop Migration Guide](https://github.com/oracle/graal/blob/master/truffle/docs/InteropMigration.md) for an overview and individual `@deprecated` javadoc tags for guidance.
	* Deprecated classes `ForeignAccess`, `Message`, `MessageResolution`, `Resolve` and `KeyInfo`. 
	* The following methods got deprecated:
		* `InteropException.raise`, with libraries there should be no need to convert checked exceptions to runtime exceptions.
		* `TruffleObject.getForeignAccess()`.
	* Introduced new classes: `InteropLibrary` and `InvalidArrayIndexException`.
	* Added `ObjectType.dispatch` to configure the dynamic dispatch and deprecated `ObjectType.getForeignAccessFactory`.
* Added Truffle Library API that allows language implementations to use polymorphic dispatch for receiver types with support for implementation specific caching/profiling with support for uncached dispatch. 
	* Please see the [Truffle Library Tutorial](https://github.com/oracle/graal/blob/master/truffle/docs/TruffleLibraries.md) for further details.
	* Introduced new package: `com.oracle.truffle.api.library`.
* Added `@GenerateUncached` to allow the generation of uncached Truffle DSL nodes accessible via the new static generated method`getUncached()`.
	* Set the default value for @Cached to `"create()"`. This allows `@Cached` to be used without attribute.
	* Added `@Cached(uncached="")` to specify the expression to use for the uncached node.
	* Added `@Cached(allowUncached=true)` to allow the cached expression to be reused as uncached expression. Only necessary if the cached expression is not trivial or there is no `getUncached()` static method in the node.
	* Added `@Cached#parameters` to allow to share the parameter specification for the cached and uncached version of a node.
	* Added `getUncached()` method to the following classes:
        - BranchProfile 
        - ByteValueProfile
        - ConditionProfile
        - DoubleValueProfile
        - FloatValueProfile
        - IntValueProfile 
        - LongValueProfile
        - LoopConditionProfile
        - PrimitiveValueProfile
        - ValueProfile
        - IndirectCallNode
* Truffle DSL can now properly handle checked exceptions in execute methods and specializations.
* Truffle DSL now guarantees to adopt nodes before they are executed in guards. Previously, nodes used in guards were only adopted for their second cached invocation.
* Added `@Cached.Shared` to allow sharing of cached values between specialization and exported Truffle Library methods.
* Added `Node.isAdoptable()` that allows `Node.getParent()` to always remain `null` even if the node is adopted by a parent. This allows to share nodes statically and avoid the memory leak for the parent reference.
* Added `NodeUtil.getCurrentEncapsulatingNode` to access the current encapsulating node in nodes that are not adoptable.
* Added the `Assumption.isValidAssumption` method that allows for simpler checking of assumptions in generated code. 
* Added Truffle DSL option `-Dtruffle.dsl.ignoreCompilerWarnings=true|false`, to ignore Truffle DSL compiler warnings. This is useful and recommended to be used for downstream testing.
* Added `@CachedContext` and `@CachedLanguage` for convenient language and context lookup in specializations or exported methods.
* Added `Node.lookupContextReference(Class)` and `Node.lookupLanguageReference(Class)` that allows for a more convenient lookup.
* Deprecated `RootNode.getLanguage(Class)`, the new language references should be used instead.
* Added `TruffleFile` aware file type detector
    - Added [TruffleFile.FileTypeDetector SPI](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleFile.FileTypeDetector.html) to detect a file MIME type and a file encoding. A language registering `FileTypeDetector` has to support all the MIME types recognized by the registered detector.
    - Added [TruffleFile.getMimeType method](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleFile.html#getMimeType--) to obtain a `TruffleFile` MIME type.
    - Added a possibility to set an [encoding in SourceBuilder](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/source/Source.SourceBuilder.html#encoding-java.nio.charset.Charset-)
    - The [Source builders](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/source/Source.html) are sandboxed for files and file URLs.
    - Removed usage of NIO `FileTypeDetector` for MIME type detection, language implementations have to migrate to `TruffleFile.FileTypeDetector`.
* TruffleFile's paths from image building time are translated in image execution time into new paths using Context's FileSystem. The absolute paths pointing to files in language homes in image generation time are resolved using image execution time language homes.
* Added [Env.isPolylgotAccessAllowed()](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.Env.html#isPolyglotAccessAllowed--) to check whether polyglot access (e.g. access to polyglot builtins) is allowed.
* The methods `Env.getPolyglotBindings()` and `Env.importSymbol` and `Env.exportSymbol` now throw a `SecurityException` if polyglot access not allowed.
* Added `DebugValue.isNull()` to check for null values, `DebugValue.execute()` to be able to execute values and `DebugValue.asString()` to get the String from String values.
* Added the [TruffleFile.getAttribute](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleFile.html#getAttribute-com.oracle.truffle.api.TruffleFile.AttributeDescriptor-java.nio.file.LinkOption...-) method to read a single file's attribute and [TruffleFile.getAttributes] (https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleFile.html#getAttributes-java.util.Collection-java.nio.file.LinkOption...-) method to read file's attributes as a bulk operation.

## Version 1.0.0 RC14
* Removed some deprecated elements:
    - EventBinding.getFilter
    - TruffleLanguage ParsingRequest.getFrame and ParsingRequest.getLocation
    - LoopCountReceiver
    - EventContext.parseInContext
    - NativeLibraryDescriptor.getBindings
    - Instrumenter.attachFactory and Instrumenter.attachListener
    - SuppressFBWarnings
    - TruffleBoundary.throwsControlFlowException
    - DebuggerTester.startEval
    - ExactMath.exact methods
    - TruffleInstrument.toString
    - TruffleInstrument.findMetaObject
    - TruffleInstrument.findSourceLocation
    - constructor of JSONStringBuilder
    - constructor of JSONHelper
    - constructor of CompilerDirectives
    - constructor of ExactMath
    - constructor of Truffle
    - constructor of NodeUtil
    - TruffleException.isTimeout
    - TruffleGraphBuilderPlugins.registerUnsafeLoadStorePlugins
    - TypedObject
    - Node.getLanguage
    - TVMCI.findLanguageClass
    - ExecutionContext and RootNode.getExecutionContext
    - FrameAccess.NONE
    - RootNode.setCalltarget
    - DirectCallNode.call and IndirectCallNode.call
    - FrameInstance.getFrame
    - Node.getAtomicLock
    - ExplodeLoop.merge
    - AcceptMessage
    - RootNode.reportLoopCount
    - GraalTruffleRuntime.getQueuedCallTargets
    - PrimitiveValueProfile.exactCompare
    - BranchProfile.isVisited
    - DebugStackFrame.iterator and DebugStackFrame.getValue
* The [@Option](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/Option.html) annotation can now specify the [stability](https://www.graalvm.org/truffle/javadoc/org/graalvm/options/OptionStability.html) of an option.
* Fixed the case of the method [`TruffleStackTrace.getStacktrace`](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleStackTrace.html#getStacktrace-java.lang.Throwable-) to `TruffleStackTrace.getStackTrace`.
* Added a getter for [name separator](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.Env.html#getFileNameSeparator--) used by `TruffleFile`'s paths.
* Added support for receiver object in a frame's Scope: [Scope.Builder receiver(String, Object)](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/Scope.Builder.html#receiver-java.lang.String-java.lang.Object-), [Scope.getReceiver()](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/Scope.html#getReceiver--), [Scope.getReceiverName()](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/Scope.html#getReceiverName--) and [DebugScope.getReceiver()](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/DebugScope.html#getReceiver--).
* Added [engine bound TruffleLogger for instruments](file:///Users/tom/Projects/graal/tzezula/graal/truffle/javadoc/com/oracle/truffle/api/instrumentation/TruffleInstrument.Env.html#getLogger-java.lang.String-). The engine bound logger can be used by threads executing without any context.

## Version 1.0.0 RC13
* Added [Debugger.getSessionCount()](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/Debugger.html#getSessionCount--) to return the number of active debugger sessions.
* The [TruffleFile.getName()](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleFile.html#getName--) returns `null` for root directory.
* `TruffleLanguage` can [register additional services](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.Env.html#registerService-java.lang.Object-). This change also deprecates the automatic registration of the language class as a service.
* Enabled the [experimental monomorphization heuristic](https://github.com/oracle/graal/blob/master/truffle/docs/splitting/) as default. Old heuristic still available as legacy, but will be removed soon.
* Added [TypeDescriptor.instantiable(instanceType, vararg, parameterTypes)](https://www.graalvm.org/truffle/javadoc/org/graalvm/polyglot/tck/TypeDescriptor.html#instantiable-org.graalvm.polyglot.tck.TypeDescriptor-boolean-org.graalvm.polyglot.tck.TypeDescriptor...-) into TCK to support instantiable types.
* The name of an [@Option](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/Option.html) can now start with a lowercase letter.
* Allowed navigation from host class to host symbol (companion object for static members) via the synthetic member `"static"`.
* Moved `getStackTrace` and `fillIn` from [TruffleStackTraceElement](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleStackTraceElement.html) to [TruffleStackTrace](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleStackTrace.html).




## Version 1.0.0 RC12
* Fixed: [Env.asHostException()](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.Env.html#asHostException-java.lang.Throwable-) should throw an `IllegalArgumentException` if the provided value is not a host exception.
* Changed host exceptions' [getExceptionObject()](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleException.html#getExceptionObject--) to return the original host exception object.

## Version 1.0.0 RC11
* `Source` can be created from a relative `TruffleFile`.
* `Source` can be created without content using `Source.CONTENT_NONE` constant.
* `SourceSection` can be created from line/column information by [Source.createSection(startLine,startColumn,endLine,endColumn)](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/source/Source.html#createSection-int-int-int-int-).
* Added [SourceSection.hasLines()](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/source/SourceSection.html#hasLines--), [SourceSection.hasColumns()](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/source/SourceSection.html#hasColumns--) and [SourceSection.hasCharIndex()](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/source/SourceSection.html#hasCharIndex--) to distinguish which positions are defined and which are not.
* `DebuggerSession` [accepts source-path](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/DebuggerSession.html#setSourcePath-java.lang.Iterable-) for source [resolution](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/DebuggerSession.html#resolveSource-com.oracle.truffle.api.source.Source-).
* Added Java interop support for string to primitive type conversion.

## Version 1.0.0 RC10
* Added support for setting current working directory for TruffleFiles, see [Env.setCurrentWorkingDirectory](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.Env.html#setCurrentWorkingDirectory-com.oracle.truffle.api.TruffleFile-)
* Removed deprecated `TruffleLanguage.Env.newSourceBuilder`.
* Added `TruffleLanguage.Env.isPreInitialization` method to determine whether the context is being pre-initialized.
* Added `ArrayUtils` API providing additional array and/or string operations that may be intrinsified by the compiler.
* Added a possibility to obtain a [relative URI](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleFile.html#toRelativeUri--) for a relative `TruffleFile`.
* Added `ForeignAccess.createAccess` method taking a [supplier of language check node](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/ForeignAccess.html#createAccess-com.oracle.truffle.api.interop.ForeignAccess.StandardFactory-java.util.function.Supplier-), deprecated the `ForeignAccess.create` method with languageCheck `RootNode` parameter.

## Version 1.0.0 RC9

* Added support for setting the `ThreadGroup` and `stackSize` on truffle thread creation in `TruffleLanguage.Env.createThread`.
* Added `Instrumenter.lookupExecutionEventNode()` to find an execution event node inserted at the node's location by an event binding.
* Added `SourceElement.ROOT` and `StepConfig.suspendAnchors()` to tune debugger stepping.
* Added `KeyInfo.READ_SIDE_EFFECTS` and `KeyInfo.WRITE_SIDE_EFFECTS` to inform about side-effects of READ/WRITE messages.
* Added `DebugValue.hasReadSideEffects()` and `DebugValue.hasWriteSideEffects()` to test for side-effects of reading or writing the value.

## Version 1.0.0 RC8

* Added `SuspendedEvent.setReturnValue` to change the return value of the currently executed source location.
* Deprecated `FrameSlot#getIndex` without replacement.
* Added `TruffleInstrument.Env.startServer()` to get a virtual message-based server provided via `MessageTransport` service.
* Added `TruffleFile.relativize`, `TruffleFile.startsWith`, `TruffleFile.endsWith`, `TruffleFile.createLink`,  `TruffleFile.createSymbolicLink`, `TruffleFile.getOwner`, `TruffleFile.getGroup`, `TruffleFile.newDirectoryStream`, `TruffleFile.visit`, `TruffleFile.copy` methods.

## Version 1.0.0 RC7

* Truffle was relicensed from GPLv2 with CPE to Universal Permissive License (UPL).
* Made all Truffle DSL annotations retention policy CLASS instead of RUNTIME. Reflecting DSL annotations at runtime is no longer possible. It is recommended to use `@Introspectable` instead.

* Removed deprecated FrameDescriptor#shallowCopy (deprecated since 1.0.0 RC3).
* Removed deprecated FrameSlot#getFrameDescriptor (deprecated since 1.0.0 RC3).

## Version 1.0.0 RC6

* Added support for byte based sources:
	* Byte based sources may be constructed using a `ByteSequence` or from a `TruffleFile` or `URL`. Whether sources are interpreted as character or byte based sources depends on the specified language.
	* `Source.hasBytes()` and `Source.hasCharacters()` may be used to find out whether a source is character or byte based.
	* Added `Source.getBytes()` to access the contents of byte based sources.
	* `TruffleLanguage.Registration.mimeType` is now deprecated in favor of `TruffleLanguage.Registration.byteMimeTypes` and `TruffleLanguage.Registration.characterMimeTypes`.
	* Added `TruffleLanguage.Registration.defaultMimeType` to define a default MIME type. This is mandatory if a language specifies more than one MIME type.
* `TruffleLanguage.Registration.id()` is now mandatory for all languages and reserved language ids will now be checked by the annotation processor.
* Deprecated Source builders and aligned them with polyglot source builders.
	* e.g. `Source.newBuilder("chars").name("name").language("language").build()` can be translated to `Source.newBuilder("language", "chars", "name").build()`
	* This is a preparation step for removing Truffle source APIs in favor of polyglot Source APIs in a future release.
* Deprecated `Source.getInputStream()`. Use `Source.getCharacters()` or `Source.getBytes()` instead.
* Deprecated `TruffleLanguage.Env.newSourceBuilder(String, TruffleFile)`. Use  `Source.newBuilder(String, TruffleFile)` instead.
* Added `Source.findLanguage` and `Source.findMimeType` to resolve languages and MIME types.
* The method `Source.getMimeType()` might now return `null`. Source builders now support `null` values for `mimeType(String)`.
* A `null` source name will no longer lead to an error but will be translated to `Unnamed`.
* Added `TruffleFile.normalize` to allow explicit normalization of `TruffleFile` paths. `TruffleFile` is no longer normalized by default.
* Added `Message#EXECUTE`, `Message#INVOKE`, `Message#NEW`.
* Deprecated `Message#createExecute(int)`, `Message#createInvoke(int)`, `Message#createNew(int)` as the arity argument is no longer needed. Jackpot rules available (run `mx jackpot --apply`).
* Removed APIs for deprecated packages: `com.oracle.truffle.api.vm`, `com.oracle.truffle.api.metadata`, `com.oracle.truffle.api.interop.java`
* Removed deprecated class `TruffleTCK`.
* Debugger API methods now throw [DebugException](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/DebugException.html) on language failures.
* Deprecated API methods that use `java.beans` package in [AllocationReporter](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/AllocationReporter.html) and [Debugger](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/Debugger.html). New add/remove listener methods were introduced as a replacement.
* [FrameDescriptor](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/frame/FrameDescriptor.html) no longer shares a lock with a RootNode.

## Version 1.0.0 RC5

* Added `TruffleLanguage.Env.isHostFunction`.
* Added Java interop support for converting executable values to legacy functional interfaces without a `@FunctionalInterface` annotation.
* Added `TruffleLogger.getLogger(String)` to obtain the root loger of a language or instrument.
* Introduced per language [context policy](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.ContextPolicy.html). Languages are encouraged to configure the most permissive policy that they can support.
* Added `TruffleLanguage.areOptionsCompatible` to allow customization of the context policy based on options.
* Changed default context policy from SHARED to EXCLUSIVE, i.e. there is one exclusive language instance per polyglot or inner context by default. This can be configured by the language
using the [context policy](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.ContextPolicy.html).
* TruffleInstrument.Env.lookup(LanguagInfo, Class) now requires to be entered in a context for the current thread.
* Removed deprecated FindContextNode (deprecated since 0.25).
* All languages now need to have a public zero argument constructor. Using a static singleton field is no longer supported.
* Renamed and changed the return value of the method for TruffleLanguage.initializeMultiContext to TruffleLanguage.initializeMultipleContexts. The original method remains but is now deprecated.
* Added [SourceSectionFilter#includes](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/SourceSectionFilter.html#includes-com.oracle.truffle.api.nodes.Node-)
* Deprecating `FrameSlot#getKind` and `FrameSlot#setKind` in favor of `FrameDescriptor#getFrameSlotKind` and `FrameDescriptor#setFrameSlotKind`.
* The `FrameDescriptor` is now thread-safe from the moment it is first passed to a RootNode constructor.
  * The list returned by [FrameDescriptor#getSlots](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/frame/FrameDescriptor.html#getSlots--) no longer reflects future changes in the FrameDescriptor. This is an incompatible change.
  * The set returned by [FrameDescriptor#getIdentifiers](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/frame/FrameDescriptor.html#getIdentifiers--) no longer reflects future changes in the FrameDescriptor. This is an incompatible change.
* Added [LanguageInfo#isInteractive](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/LanguageInfo.html#isInteractive--)
* Added [DebugStackFrame#getLanguage](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/DebugStackFrame.html#getLanguage--)

## Version 1.0.0 RC3

* Removed deprecated ResultVerifier.getDefaultResultVerfier.
* Deprecated `com.oracle.truffle.api.frame.FrameDescriptor.shallowCopy` and `com.oracle.truffle.api.frame.FrameSlot.getFrameDescriptor`
* Added [DebugValue#set](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/DebugValue.html#set-java.lang.Object-) to set primitive values to a debug value.
* Added support for [logging](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLogger.html) in Truffle languages and instruments.

## Version 1.0.0 RC2

* Added notification when [multiple language contexts](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html#initializeMultiContext--) were created for a language instance. Allows languages to invalidate assumptions only valid with a single context. Returning true also allows to enable caching of ASTs per language and not only per context.
* Added [asBoxedGuestValue](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.Env.html#asBoxedGuestValue-java.lang.Object-) method that allows to expose host members for primitive interop values.
* Added default value `"inherit"` to [TruffleLanguage.Registration#version](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.Registration.html#version--) which makes the language to inherit version from [Engine#getVersion](http://www.graalvm.org/truffle/javadoc/org/graalvm/polyglot/Engine.html#getVersion--).
* Changed default value of [TruffleInstrument.Registration#version](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleInstrument.Registration.html#version--) from `""` to `"inherit"` which makes the instrument to inherit version from [Engine#getVersion](http://www.graalvm.org/truffle/javadoc/org/graalvm/polyglot/Engine.html#getVersion--). An instrument previously not specifying any version will newly get version from Engine.
* Added new annotation @IncomingConverter and @OutgoingConverter to declare methods for [generated wrappers](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/GenerateWrapper.html) that allow to convert values when they are exposed to or introduced by the instrumentation framework.
* The documentation of [FrameDescriptor#getSize](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/frame/FrameDescriptor.html#getSize--) clarifies that it returns the size of an array which is needed for storing all the slots in it using their `FrameSlot#getIndex()` as a position in the array. (The number may be bigger than the number of slots, if some slots are removed.)
* Added an `InstrumentExceptionsAreThrown` engine option to propagate exceptions thrown by instruments.
* Added [Instrumenter.visitLoadedSourceSections](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/Instrumenter.html#visitLoadedSourceSections-com.oracle.truffle.api.instrumentation.SourceSectionFilter-com.oracle.truffle.api.instrumentation.LoadSourceSectionListener-) to be notified about loaded source sections that corresponds to a filter.
* Added [DebugValue#canExecute](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/DebugValue.html#canExecute--) to distinguish executable values and [DebugValue#getProperty](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/DebugValue.html#getProperty-java.lang.String-) to get a property value by its name.
* Removed deprecated `TruffleLanguage.Env.lookupSymbol` method.
* All Truffle source objects are now automatically weakly internalized when created using the source builder. The source builder will now return the same instance for every source where it was previously just equal.
* Added `Source.Builder.cached(boolean)` and `Source.isCached()` to configure caching behavior by source.
* Removed deprecated `Source.getCode()` and `SourceSection.getCode`.

## Version 1.0.0 RC1

* As announced in 0.27 all classes in package com.oracle.truffle.api.vm are now deprecated.
	* Deprecated all classes in com.oracle.truffle.api.vm. Replacements can be found in the org.graalvm.polyglot package.
	* Deprecated all classes in com.oracle.truffle.api.interop.java. Replacements for embedders can be found in org.graalvm.polyglot. Replacements for language implementations can be found in TruffleLanguage.Env. See deprecated documentation on the individual methods for details.
	* Deprecated TruffleTCK. Use the [new TCK](https://github.com/oracle/graal/blob/master/truffle/docs/TCK.md) instead.
	* Deprecated Debugger#find(PolyglotEngine)
	* Added Debugger#find(TruffleInstrument.Env) and Debugger#find(Engine)
* Added [FileSystem](http://www.graalvm.org/truffle/javadoc/org/graalvm/polyglot/io/FileSystem.html) SPI to allow embedder to virtualize TruffleLanguage Input/Output operations.
* Added [EventContext.lookupExecutionEventNodes](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/EventContext.html#lookupExecutionEventNodes-java.util.Collection-) to lookup all execution event nodes created by the bindings at the source location.
* Added `TruffleLanguage#getLanguageHome` to return the language directory in the GraalVM distribution or the location of the language Jar file.
* Added [TryBlockTag](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/StandardTags.TryBlockTag.html) as a new standard tag to mark program locations to be considered as try blocks, that are followed by a catch.
* Added [DebugException](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/DebugException.html), debugger methods that execute guest language code throws that exception and it's possible to [create exception breakpoints](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/Breakpoint.html#newExceptionBuilder-boolean-boolean-) that suspend when guest language exception occurs.
* Added [DebugStackTraceElement](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/DebugStackTraceElement.html) as a representation of exception stack trace.
* Added [Breakpoint.Kind](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/Breakpoint.Kind.html) to distinguish different breakpoint kinds.
* Added [ResultVerifier.getDefaultResultVerifier](http://www.graalvm.org/truffle/javadoc/org/graalvm/polyglot/tck/ResultVerifier.html#getDefaultResultVerifier--).
* Added [addToHostClassPath](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.Env.html#addToHostClassPath-com.oracle.truffle.api.TruffleFile-) method that can be used to allow guest language users to add to the host class path.
* Added new permission TruffleLanguage.Env#isNativeAccessAllowed to control access to the Truffle NFI.
* Changed default permissions in language launchers to full access. The embedding API still defaults to restricted access.
* Added [TruffleInstrument.onFinalize](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/TruffleInstrument.html#onFinalize-com.oracle.truffle.api.instrumentation.TruffleInstrument.Env-) that can be overridden to be notified about closing of Engine, while still having access to other instruments.
* Deprecated `TraceASTJSON` option and related APIs.

## Version 0.33

* This release contains major changes to the instrumentation framework.
	* Deprecated @[Instrumentable](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/Instrumentable.html) and replaced it with [InstrumentableNode](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/InstrumentableNode.html). Please see [InstrumentableNode](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/InstrumentableNode.html) on how to specify instrumentable nodes in 0.32.
	* Added @[GenerateWrapper](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/GenerateWrapper.html) for automatic wrapper generation.
	* Added a [standard expression tag](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/StandardTags.ExpressionTag.html), that allows languages to expose expressions for tools to use.
	* Added the ability to listen to [input values](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/ExecutionEventNode.html#onInputValue-com.oracle.truffle.api.frame.VirtualFrame-com.oracle.truffle.api.instrumentation.EventContext-int-java.lang.Object-) of instrumentable child nodes by specifying [input filters](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/Instrumenter.html#attachExecutionEventFactory-com.oracle.truffle.api.instrumentation.SourceSectionFilter-com.oracle.truffle.api.instrumentation.SourceSectionFilter-T-).
	* Added the the ability to [save](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/ExecutionEventNode.html#saveInputValue-com.oracle.truffle.api.frame.VirtualFrame-int-java.lang.Object-) and [load](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/ExecutionEventNode.html#getSavedInputValues-com.oracle.truffle.api.frame.VirtualFrame-) instrumentable child input values in ExecutionEventNode subclasses.
	* Renamed Instrumenter#attachListener/Factory to Instrumenter#attachExecutionEventListener/Factory. (jackpot rule available)
	* Automatic instrumentation [wrapper generation](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/GenerateWrpper.html) now delegates non execute abstract methods to the delegate node.
	* Added a [Tag](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/Tag.html) base class now required to be used by all tags.
	* Added [tag identifiers](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/Tag.Identifier.html) to allow the [lookup](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/Tag.html#findProvidedTag-com.oracle.truffle.api.nodes.LanguageInfo-java.lang.String-) of language specific tags in tools without compile time dependency to the languguage.
	* Added assertions to verify that instrumentable nodes that are annotated with a standard tag return a source section if their root node returns a source section.
	* Added assertions to verify that execution events always return interop values.
	* Added the ability for instrumentable nodes to a expose a [node object](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api//instrumentation/InstrumentableNode.html#getNodeObject--). This object is intended to contain language specific properties of the node.
* Added expression-stepping into debugger APIs. To support debugging of both statements and expressions, following changes were made:
	* Added [SourceElement](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/SourceElement.html) enum to provide a list of source syntax elements known to the debugger.
	* Added [StepConfig](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/StepConfig.html) class to represent a debugger step configuration.
	* Added [Debugger.startSession()](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/Debugger.html#startSession-com.oracle.truffle.api.debug.SuspendedCallback-com.oracle.truffle.api.debug.SourceElement...-) accepting a list of source elments to enable stepping on them.
	* Added [Breakpoint.Builder.sourceElements](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/Breakpoint.Builder.html#sourceElements-com.oracle.truffle.api.debug.SourceElement...-) to specify which source elements will the breakpoint adhere to.
	* Added [SuspendedEvent.getInputValues](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/SuspendedEvent.html#getInputValues--) to get possible input values of the current source element.
	* Removed deprecated methods on [SuspendedEvent](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/SuspendedEvent.html).
* Added column filters on [SourceSectionFilter.Builder](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/SourceSectionFilter.Builder.html) and [Breakpoint.Builder](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/Breakpoint.Builder.html).
* Added [Instrumenter.attachExecuteSourceListener](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/Instrumenter.html#attachExecuteSourceListener-com.oracle.truffle.api.instrumentation.SourceFilter-T-boolean-) to be able to [listen](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/ExecuteSourceListener.html) on [source execution events](http://www.graalvm.org/truffle/javadoc/javadoc/com/oracle/truffle/api/instrumentation/ExecuteSourceEvent.html).
* Added [InstrumentableNode.findNearestNodeAt](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/InstrumentableNode.html#findNearestNodeAt-int-java.util.Set-) to be able to find the nearest tagged node to the given source character index. This is used to auto-correct breakpoint locations.
* Added [Breakpoint.ResolveListener](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/Breakpoint.ResolveListener.html) to listen on breakpoint location resolution. Breakpoints are now resolved after the source is to be executed for the first time and breakpoint location is adjusted to match the nearest instrumentable node.
* Added new DSL annotation @[Executed](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/dsl/Executed.html) that allows to manually specify executed node fields.
* The Truffle Node traversal order was slightly changed to always respect field declaration order (super class before sub class).
* The [Assumption](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/Assumption.html) interface has an additional override for the `invalidate` method to provide a message for debugging purposes.
* Deprecated `KeyInfo.Builder`. Use bitwise constants in the KeyInfo class instead. Introduced new flag KeyInfo.INSERTABLE to indicate that a key can be inserted at a particular location, but it does not yet exist.
* Deprecated `TruffleLanguage#getLanguageGlobal`, implement [top scopes](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/TruffleInstrument.Env.html#findTopScopes-java.lang.String-) instead.
* Deprecated `TruffleLanguage#findExportedSymbol`, use the [polyglot bindings](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.Env.html#getPolyglotBindings--) TruffleLanguage.Env for exporting symbols into the polyglot scope explicitely. The polyglot scope no longer supports implicit exports, they should be exposed using [top scopes](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/TruffleInstrument.Env.html#findTopScopes-java.lang.String-) instead.
* Remove deprecated `TruffleInstrument#describeOptions` and TruffleLanguage#describeOptions
* Remove deprecated `TruffleLanguage.Env#lookupSymbol` without replacement.
* Remove deprecated `TruffleLanguage.Env#importSymbols`, use the polyglot bindings instead.
* Removed deprecated APIs and public debug classes in truffle.api.object and truffle.object packages, respectively.
* Removed internal truffle.object package from javadoc.
* Added the compiler directive [castExact](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/CompilerDirectives.html#castExact-java.lang.Object-java.lang.Class-).
* Added skipped exception types: `IndexOutOfBoundsException`, `BufferOverflowException`, and `BufferUnderflowException`.
* Introduced support for the experimental automated monomorphization feature:
    * The [Node.reportPolymorphicSpecialize](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/Node.html#reportPolymorphicSpecialize) method which notifies the runtime that a node has specialized to a more polymorphic state.
    * The [ReportPolymorphism](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/dsl/ReportPolymorphism.html) and [ReportPolymorphism.Exclude](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/dsl/ReportPolymorphism.Exclude.html) annotations which the DSL uses to generate (or not generate) calls to [Node.reportPolymorphicSpecialize](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/Node.html#reportPolymorphicSpecialize--).
* Added `TruffleException.getSourceLocation()` for syntax errors which don't have a `Node`.
* Changed member lookup on `Class` host objects (as obtained by e.g. `obj.getClass()`) to expose `Class` instance members, while `TruffleLanguage.Env.lookupHostSymbol(String)` returns a companion object providing the static members of the class and serving as a constructor.



## Version 0.32

* Added [SuspendAnchor](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/SuspendAnchor.html) enum class that describes where, within a guest language source section, the suspend position is and [Breakpoint.Builder.suspendAnchor()](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/Breakpoint.Builder.html#suspendAnchor-com.oracle.truffle.api.debug.SuspendAnchor-) to be able to break before or after the source section.
* Deprecated `SuspendedEvent.isHaltedBefore()`, [SuspendedEvent.getSuspendAnchor()](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/SuspendedEvent.html#getSuspendAnchor--) is to be used instead.
* Added new interop message [REMOVE](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/Message.html#REMOVE) with the appropriate foreign access methods [ForeignAccess.sendRemove](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/ForeignAccess.html#sendRemove-com.oracle.truffle.api.nodes.Node-com.oracle.truffle.api.interop.TruffleObject-java.lang.Object-) and [KeyInfo.isRemovable flag](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/KeyInfo.html#isRemovable-int-).
* Added [SourceFilter](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/SourceFilter.html) for source-only based filtering in instrumentation.
* Changed semantics of [UnexpectedResultException](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/UnexpectedResultException.html) when used in [Specialization#rewriteOn](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/dsl/Specialization.html#rewriteOn--) to indicate that a result is already available and no other specialization methods need to be invoked in Truffle DSL.

## Version 0.31

* Removed deprecated `com.oracle.truffle.api.source.LineLocation` class.
* Added `RootNode#isCaptureFramesForTrace()` to allow subclasses to configure capturing of frames in `TruffleException` instances and `TruffleStackTraceElement#getFrame()` to access the captured frames.
* [MaterializedFrame](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/frame/MaterializedFrame.html) changed to extend [VirtualFrame](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/frame/VirtualFrame.html), to be able to call methods taking `VirtualFrame` from behind Truffle boundary.
* Added [ExecutableNode](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/ExecutableNode.html), [TruffleLanguage.parse(InlineParsingRequest)](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html#parse-com.oracle.truffle.api.TruffleLanguage.InlineParsingRequest-) and [TruffleInstrument.Env.parseInline](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/TruffleInstrument.Env.html#parseInline-com.oracle.truffle.api.source.Source-com.oracle.truffle.api.nodes.Node-com.oracle.truffle.api.frame.MaterializedFrame-) to parse an inline code snippet at the provided location and produce an AST fragment that can be executed using frames valid at the provided location. `ParsingRequest.getLocation()` and `ParsingRequest.getFrame()` methods were deprecated in favor of `InlineParsingRequest`, `EventContext.parseInContext()` was deprecated in favor of `TruffleInstrument.Env.parseInline()`.
* [RootNode](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/RootNode.html) now extends [ExecutableNode](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/ExecutableNode.html).
* Removed deprecated methods `TruffleLanguage.parse(Source, Node, String...)` and `TruffleLanguage.evalInContext(Source, Node, MaterializedFrame)` and constructor `RootNode(Class, SourceSection, FrameDescriptor)`.
* Java Interop now wraps exceptions thrown by Java method invocations in host exceptions.
* Added [JavaInterop.isHostException](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/java/JavaInterop.html#isHostException-java.lang.Throwable-) and [JavaInterop.asHostException](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/java/JavaInterop.html#asHostException-java.lang.Throwable-) to identify and unwrap host exceptions, respectively.
* Added support for `TruffleLanguage` context pre-initialization in the native image. To support context pre-initialization a language has to implement the [patchContext](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage#patchContext-C-com.oracle.truffle.api.TruffleLanguage.Env-) method.
* The profiler infrastructure (`CPUSampler`, `CPUTracer` and `MemoryTracer`) moved to a new tools suite.
* Added [LanguageInfo.isInternal](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/LanguageInfo.html#isInternal--)
* Removed special Java interop support for `java.util.Map`.
* Added a mechanism to unwind execution nodes in instrumentation by [EventContext.createUnwind](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/EventContext.html#createUnwind-java.lang.Object-), [ExecutionEventListener.onUnwind](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/ExecutionEventListener.html#onUnwind-com.oracle.truffle.api.instrumentation.EventContext-com.oracle.truffle.api.frame.VirtualFrame-java.lang.Object-), [ExecutionEventNode.onUnwind](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/ExecutionEventNode.html#onUnwind-com.oracle.truffle.api.frame.VirtualFrame-java.lang.Object-) and [ProbeNode.onReturnExceptionalOrUnwind](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/ProbeNode.html#onReturnExceptionalOrUnwind-com.oracle.truffle.api.frame.VirtualFrame-java.lang.Throwable-boolean-). [ProbeNode.UNWIND_ACTION_REENTER](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/ProbeNode.html#UNWIND_ACTION_REENTER) constant added.
* Deprecated `ProbeNode.onReturnExceptional()` in favor of `ProbeNode.onReturnExceptionalOrUnwind()`.
* The wrapper node specification has changed, see [ProbeNode](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/ProbeNode.html). If the annotation processor is used (`@Instrumentable` annotation) then just a recompile is required. Manually written wrappers need to be updated.
* Added [SuspendedEvent.prepareUnwindFrame](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/SuspendedEvent.html#prepareUnwindFrame-com.oracle.truffle.api.debug.DebugStackFrame-) to unwind frame(s) during debugging.
* Added [DebuggerTester](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/DebuggerTester.html#DebuggerTester-org.graalvm.polyglot.Context.Builder-) constructor that takes `Context.Builder`.
* Removed deprecated [DebuggerTester](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/DebuggerTester.html) constructor that takes the legacy `PolyglotEngine.Builder`.
* Removed deprecated methods in `JavaInterop`: `isNull`, `isArray`, `isBoxed`, `unbox`, `getKeyInfo`.
* Disallowed `null` as `FrameSlot` identifier.
* Removed deprecated `FrameSlot` constructor and `FrameDescriptor.create` methods.
* Changed the behavior of exception handling (TruffleException) to capture stack frames lazily

## Version 0.30

* Truffle languages are being [finalized](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage##finalizeContext-C-) before disposal. This allows languages to run code with all languages still in a valid state. It is no longer allowed to access other languages during language disposal.
* Truffle languages can now declare dependent languages. This allows to take influence on the disposal order.
* All classes of the [com.oracle.truffle.api.metadata](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/metadata/package-summary.html) package were deprecated. As a replacement use [Scope](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/Scope.html), [TruffleLanguage.findLocalScopes](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html#findLocalScopes-C-com.oracle.truffle.api.nodes.Node-com.oracle.truffle.api.frame.Frame-) and [TruffleInstrument.Env.findLocalScopes](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/TruffleInstrument.Env.html#findLocalScopes-com.oracle.truffle.api.nodes.Node-com.oracle.truffle.api.frame.Frame-) instead.
* Added the ability to access [top scopes](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/TruffleInstrument.Env.html#findTopScopes-java.lang.String-) of languages and [exported symbols](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/TruffleInstrument.Env.html#getExportedSymbols--) of the polyglot scope using the instrumentation API.
* Added the ability to access [top scopes](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/DebuggerSession.html#getTopScope-java.lang.String-) and [exported symbols](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/DebuggerSession.html#getExportedSymbols--) using the debugger API.
* Added the [and](graal/truffle/javadoc/com/oracle/truffle/api/instrumentation/SourceSectionFilter.Builder.html#and-com.oracle.truffle.api.instrumentation.SourceSectionFilter-) method to the [SourceSectionFilter Builder](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/SourceSectionFilter.Builder.html) which allows composing filters.
* Added the new profiler infrastructure, including the [CPU sampler](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/tools/profiler/CPUSampler.html), [CPU tracer](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/tools/profiler/CPUTracer.html) and an experimental [Memory tracer](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/tools/profiler/MemoryTracer.html).
* Added a new [TCK SPI](https://github.com/graalvm/graal/blob/master/truffle/docs/TCK.md) based on the org.graalvm.polyglot API to test a language inter-operability. To test the language inter-operability implement the [LanguageProvider](http://www.graalvm.org/truffle/javadoc/org/graalvm/polyglot/tck/LanguageProvider.html).
* Removed all deprecated API in com.oracle.truffle.api.dsl.
* New interop messages [HAS_KEYS](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/Message.html#HAS_KEYS) and [IS_INSTANTIABLE](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/Message.html#IS_INSTANTIABLE) added, with the appropriate foreign access methods [ForeignAccess.sendHasKeys](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/ForeignAccess.html#sendHasKeys-com.oracle.truffle.api.nodes.Node-com.oracle.truffle.api.interop.TruffleObject-) and [ForeignAccess.sendIsInstantiable](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/ForeignAccess.html#sendIsInstantiable-com.oracle.truffle.api.nodes.Node-com.oracle.truffle.api.interop.TruffleObject-).
* New interop foreign access factory [ForeignAccess.StandardFactory](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/ForeignAccess.StandardFactory.html) replaces the version-specific factories, the deprecated ForeignAccess.Factory10 and ForeignAccess.Factory18 were removed, ForeignAccess.Factory26 was deprecated.
* [@MessageResolution](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/MessageResolution.html) automatically applies default value to boolean HAS/IS messages depending on presence of message handlers of corresponding messages.
* Added instrumentation API for listening on contexts and threads changes: [Instrumenter.attachContextsListener](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/Instrumenter.html#attachContextsListener-T-boolean-), [ContextsListener](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/ContextsListener.html), [Instrumenter.attachThreadsListener](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/Instrumenter.html#attachThreadsListener-T-boolean-) and [ThreadsListener](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/ThreadsListener.html).
* Added debugger representation of a context [DebugContext](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/DebugContext.html) and API for listening on contexts and threads changes: [DebuggerSession.setContextsListener](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/DebuggerSession.html#setContextsListener-com.oracle.truffle.api.debug.DebugContextsListener-boolean-), [DebugContextsListener](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/DebugContextsListener.html), [DebuggerSession.setThreadsListener](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/DebuggerSession.html#setThreadsListener-com.oracle.truffle.api.debug.DebugThreadsListener-boolean-) and [DebugThreadsListener](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/DebugThreadsListener.html).
* Added [TruffleContext.getParent](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleContext.html#getParent--) to provide the hierarchy of inner contexts.
* Added [TruffleLanguage.Env.getContext](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.Env.html#getContext--) for use by language implementations to obtain the environment's polyglot context.

## Version 0.29

* [SourceSectionFilter.Builder.includeInternal](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/SourceSectionFilter.Builder.html#includeInternal-boolean-) added to be able to exclude internal code from instrumentation.
* Debugger step filtering is extended with [include of internal code](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/SuspensionFilter.Builder.html#includeInternal-boolean-) and [source filter](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/SuspensionFilter.Builder.html#sourceIs-java.util.function.Predicate-). By default, debugger now does not step into internal code, unless a step filter that is set to include internal code is applied.
* [DebugScope.getSourceSection](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/DebugScope.html#getSourceSection--) added to provide source section of a scope.

## Version 0.28
4-Oct-2017

* Truffle languages may support [access](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html#isThreadAccessAllowed-java.lang.Thread-boolean-) to contexts from multiple threads at the same time. By default the language supports only single-threaded access.
* Languages now need to use the language environment to [create](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.Env.html#createThread-java.lang.Runnable-) new threads for a context. Creating Threads using the java.lang.Thread constructor is no longer allowed and will be blocked in the next release.
* Added `JavaInterop.isJavaObject(Object)` method overload.
* Deprecated helper methods in `JavaInterop`: `isNull`, `isArray`, `isBoxed`, `unbox`, `getKeyInfo`. [ForeignAccess](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/ForeignAccess.html) already provides equivalent methods: `sendIsNull`, `sendIsArray`, `sendIsBoxed`, `sendUnbox`, `sendKeyInfo`, respectively.
* Deprecated all String based API in Source and SourceSection and replaced it with CharSequence based APIs. Automated migration with Jackpot rules is available (run `mx jackpot --apply`).
* Added [Source.Builder.language](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/source/Source.Builder.html#language-java.lang.String-) and [Source.getLanguage](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/source/Source.html#getLanguage--) to be able to set/get source langauge in addition to MIME type.
* Added the [inCompilationRoot](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/CompilerDirectives.html#inCompilationRoot--) compiler directive.
* Deprecated TruffleBoundary#throwsControlFlowException and introduced TruffleBoundary#transferToInterpreterOnException.

## Version 0.27
16-Aug-2017

* The Truffle API now depends on the Graal SDK jar to also be on the classpath.
* Added an implementation of org.graalvm.polyglot API in Truffle.
* API classes in com.oracle.truffe.api.vm package will soon be deprecated. Use the org.graalvm.polyglot API instead.
* Added [SourceSectionFilter.Builder](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/SourceSectionFilter.Builderhtml).`rootNameIs(Predicate<String>)` to filter for source sections based on the name of the RootNode.
* Added [AllocationReporter](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/AllocationReporter.html) as a service for guest languages to report allocation of guest language values.
* Added [Instrumenter.attachAllocationListener](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/Instrumenter.html#attachAllocationListener-com.oracle.truffle.api.instrumentation.AllocationEventFilter-T-), [AllocationEventFilter](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/AllocationEventFilter.html), [AllocationListener](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/AllocationListener.html) and [AllocationEvent](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/AllocationEvent.html) for profilers to be able to track creation and size of guest language values.
* Added [RootNode.getCurrentContext](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/RootNode.html), [TruffleLanguage.getCurrentLanguage(Class)](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html), [TruffleLanguage.getCurrentContext(Class)](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html) to allow static lookups of the language and context.
* Added an id property to [TruffleLanguage.Registration](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.Registration#id) to specify a unique identifier for each language. If not specified getName().toLowerCase() will be used. The registration id will be mandatory in future releases.
* Added an internal property to [TruffleLanguage.Registration](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.Registration#internal) to specify whether a language is intended for internal use only. For example the Truffle Native Function Interface is a language that should be used from other languages only.
* Added an internal property to [TruffleInstrument.Registration](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/TruffleInstrument.Registration#internal) to specify whether a internal is intended for internal use by other instruments or languages only.
* Added the ability to describe options for languages and instruments using [TruffleLanguage.getOptionDescriptors()](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html) and [TruffleInstrument.getOptionDescriptors](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/TruffleInstrument.html). User provided options are available to the language using TruffleLanguage.Env.getOptions() and TruffleInstrument.Env.getOptions().
* Added JavaInterop.isJavaObject(TruffleObject) and JavaInterop.asJavaObject(TruffleObject) to check and convert back to host language object from a TruffleObject.
* Added [TruffleException](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleException.html) to allow languages to throw standardized error information.
* [Guest language stack traces](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleStackTraceElement.html) are now collected automatically for each exception thrown and passed through a CallTarget.
* Added RootNode.isInternal to indicate if a RootNode is considered internal and should not be shown to the guest language programmer.
* Added TruffleLanguage.lookupSymbol to be implemented by languages to support language agnostic lookups in the top-most scope.
* Added TruffleLanguage.Env.getApplicationArguments() to access application arguments specified by the user.
* Added [@Option](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/Option.html) annotation to allow simple declaration of options in TruffleLanguage or TruffleInstrument subclasses.
* Added [TruffleLanguage.RunWithPolyglotRule](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/tck/TruffleRunner.RunWithPolyglotRule.html) JUnit rule to allow running unit tests in the context of a polyglot engine.
* Added implementationName property to [TruffleLanguage.Registration](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.Registration#implementationName) to specify a human readable name of the language implementation name.
* Added TruffleLanguage.Env.lookupSymbol(String) to be used by other languages to support language lookups in their top-most scope.
* Added TruffleLanguage.Env.lookupHostSymbol(String) to be used by other languages to support language lookups from the host language.
* Added TruffleLanguage.Env.isHostLookupAllowed() to find out whether host lookup is generally allowed.
* Added Node#notifyInserted(Node) to notify the instrumentation framework about changes in the AST after the first execution.
* Added TruffleLanguage.Env.newContextBuilder() that allows guest languages to create inner language contexts/environments by returning TruffleContext instances.
* Added a concept of breakpoints shared accross sessions, associated with Debugger instance: [Debugger.install](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/Debugger.html#install-com.oracle.truffle.api.debug.Breakpoint-), [Debugger.getBreakpoints](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/Debugger.html#getBreakpoints--) and a possibility to listen on breakpoints changes: [Debugger.PROPERTY_BREAKPOINTS](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/Debugger.html#PROPERTY_BREAKPOINTS), [Debugger.addPropertyChangeListener](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/Debugger.html#addPropertyChangeListener-java.beans.PropertyChangeListener-) and [Debugger.removePropertyChangeListener](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/Debugger.html#removePropertyChangeListener-java.beans.PropertyChangeListener-). [Breakpoint.isModifiable](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/Breakpoint.html#isModifiable--) added to be able to distinguish the shared read-only copy of installed Breakpoints.
* [TruffleInstrument.Env.getLanguages()](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/TruffleInstrument.Env.html#getLanguages--) returns languages by their IDs instead of MIME types when the new polyglot API is used.
* Deprecated [ExactMath.addExact(int, int)](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/ExactMath.html#addExact-int-int-), [ExactMath.addExact(long, long)](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/ExactMath.html#addExact-long-long-), [ExactMath.subtractExact(int, int)](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/ExactMath.html#subtractExact-int-int-), [ExactMath.subtractExact(long, long)](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/ExactMath.html#subtractExact-long-long-), [ExactMath.multiplyExact(int, int)](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/ExactMath.html#multiplyExact-int-int-), [ExactMath.multiplyExact(long, long)](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/ExactMath.html#multiplyExact-long-long-). Users can replace these with java.lang.Math utilities of same method names.

## Version 0.26
18-May-2017

* Language can provide additional services and instruments can [look them up](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/TruffleInstrument.Env.html#lookup).
* Renamed `DebugValue.isWriteable` to [DebugValue.isWritable](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/DebugValue.html#isWritable--) to fix spelling.
* [Breakpoint.setCondition](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/Breakpoint.html#setCondition-java.lang.String-) does not throw the IOException any more.
* Added new message [Message.KEY_INFO](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/Message.html#KEY_INFO), and an argument to [Message.KEYS](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/Message.html#KEYS) specifying whether internal keys should be provided. The appropriate foreign access [ForeignAccess.sendKeyInfo](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/ForeignAccess.html#sendKeyInfo-com.oracle.truffle.api.nodes.Node-com.oracle.truffle.api.interop.TruffleObject-java.lang.Object-), [ForeignAccess.sendKeys](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/ForeignAccess.html#sendKeys-com.oracle.truffle.api.nodes.Node-com.oracle.truffle.api.interop.TruffleObject-boolean-) and a new factory [ForeignAccess.Factory26](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/ForeignAccess.Factory26.html).
* A new [KeyInfo](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/KeyInfo.html) utility class added to help with dealing with bit flags.
* Added new Java interop utility methods: [JavaInterop.getKeyInfo](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/java/JavaInterop.html#getKeyInfo-com.oracle.truffle.api.interop.TruffleObject-java.lang.Object-) and [JavaInterop.getMapView](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/java/JavaInterop.html#getMapView-java.util.Map-boolean-).
* Added [metadata](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/metadata/package-summary.html) package, intended for APIs related to guest language structure and consumed by tools.
* Added [ScopeProvider](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/metadata/ScopeProvider.html) to provide a hierarchy of scopes enclosing the given node. The scopes are expected to contain variables valid at the associated node.
* Added [Scope](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/metadata/Scope.html) for instruments to get a list of scopes enclosing the given node. The scopes contain variables valid at the provided node.
* Added [DebugScope](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/DebugScope.html), [DebugStackFrame.getScope](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/DebugStackFrame.html#getScope--) and [DebugValue.getScope](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/DebugValue.html#getScope--) to allow debuggers to retrieve the scope information and associated variables.
* Deprecated [DebugStackFrame.iterator](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/DebugStackFrame.html) and [DebugStackFrame.getValue](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/DebugStackFrame.html), [DebugStackFrame.getScope](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/DebugStackFrame.html#getScope--) is to be used instead.
* Added [Cached.dimensions()](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/dsl/Cached.html) to specify compilation finalness of cached arrays.
* [SuspendedEvent.prepareStepOut](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/SuspendedEvent.html#prepareStepOut-int-) has a `stepCount` argument for consistency with other prepare methods. The no-argument method is deprecated.
* Multiple calls to `SuspendedEvent.prepare*()` methods accumulate the requests to create a composed action. This allows creation of debugging meta-actions.
* [JavaInterop.toJavaClass](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/java/JavaInterop.html#toJavaClass) can find proper Java class for a wrapped object
* Added environment methods TruffleLanguage.Env.getLanguages(), TruffleLanguage.Env.getInstruments(), TruffleInstrument.Env.getLanguages(), TruffleInstrument.Env.getInstruments() that allows languages or instruments to inspect some basic information about other installed languages or instruments.
* Added lookup methods TruffleLanguage.Env.lookup(LanguageInfo, Class), TruffleLanguage.Env.lookup(InstrumentInfo, Class), TruffleInstrument.Env.lookup(LanguageInfo, Class) and TruffleInstrument.Env.lookup(InstrumentInfo, Class) that allows the exchange of services between instruments and languages.
* Added [EventContext.isLanguageContextInitialized](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/EventContext.html#isLanguageContextInitialized--) to be able to test language context initialization in instruments.
* Added [SuspensionFilter](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/SuspensionFilter.html) class, [DebuggerSession.setSteppingFilter](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/DebuggerSession.html#setSteppingFilter-com.oracle.truffle.api.debug.SuspensionFilter-) and [SuspendedEvent.isLanguageContextInitialized](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/SuspendedEvent.html#isLanguageContextInitialized--) to be able to ignore language context initialization during debugging.

## Version 0.25
3-Apr-2017

* Added [Instrumenter.attachOutConsumer](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/Instrumenter.html#attachOutConsumer-T-) and [Instrumenter.attachErrConsumer](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/Instrumenter.html#attachErrConsumer-T-) to receive output from executions run in the associated PolyglotEngine.
* [JavaInterop.asTruffleObject](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/java/JavaInterop.html#asTruffleObject-java.lang.Object-) lists methods as keys
* Deprecated `TypedObject` interface
* Added [PolyglotRuntime](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/vm/PolyglotRuntime.html) for global configuration and to allow engines share resources. The runtime of a PolyglotEngine can be configured using [PolyglotEngine](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/vm/PolyglotEngine.html)`.newBuilder().runtime(runtime).build()`.
* The `getInstruments()` method has been moved from the [PolyglotEngine](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/vm/PolyglotEngine.html) to [PolyglotRuntime](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/vm/PolyglotRuntime.html).
* [TruffleLanguage](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html) now requires a public default constructor instead of a singleton field named INSTANCE.
* [TruffleLanguage](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html) now requires a public no argument constructor instead of a singleton field named INSTANCE.
* The [TruffleLanguage](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html) instance can now be used to share code and assumptions between engine instances. See the TruffleLanguage javadoc for details.
* Added a new constructor to [RootNode](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/RootNode.html) with a [TruffleLanguage](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html) instance as argument. The current constructor was deprecated.  
* Added [RootNode.getLanguage(Class)](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/RootNode.html) to access the current language implementation instance.
* Added [RootNode.getLanguageInfo](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/RootNode.html) to access public information about the associated language.
* Added [TruffleLanguage.ContextReference](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html) class and [TruffleLanguage.getContextReference](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html).
* Added [Value.getMetaObject](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/vm/TruffleLanguage.html) and [Value.getSouceLocation](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/vm/TruffleLanguage.html)
* Deprecated [RootNode.getExecutionContext](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/RootNode.html)
* Deprecated [TruffleLanguage.createFindContextNode](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html) and [TruffleLanguage.findContext](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html).
* Deprecated [Node.getLanguage](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/Node.html).
* Deprecated [MessageResolution.language](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/Node.html) without replacement. (jackpot rule available)
* Deprecated [ExecutionContext](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/ExecutionContext.html), use RootNode#getCompilerOptions().
* Added [TruffleInstrument.Registration.services()](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/TruffleInstrument.Registration#services) to support declarative registration of services
* Deprecated internal class DSLOptions. Will be removed in the next release.
* Deprecated [Shape.getData()](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/object/Shape.html) and [ObjectType.createShapeData(Shape)](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/object/ObjectType.html) without replacement.
* Added [TruffleRunner](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/tck/TruffleRunner.html) JUnit runner for unit testing Truffle compilation.

## Version 0.24
1-Mar-2017
* Added possibility to activate/deactivate breakpoints via [DebuggerSession.setBreakpointsActive](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/DebuggerSession.html#setBreakpointsActive-boolean-) and get the active state via [DebuggerSession.isBreakpointsActive](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/DebuggerSession.html#isBreakpointsActive--).
* Deprecated the send methods in [ForeignAccess](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/ForeignAccess.html) and added a a new version that does not require a frame parameter. ([Jackpot](https://bitbucket.org/jlahoda/jackpot30/wiki/Home) rule for automatic migration available)
* Made [@NodeChild](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/dsl/NodeChild.html) and [@NodeField](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/dsl/NodeField.html) annotations repeatable
* Added Truffle Native Function Interface.
* Abstract deprecated methods in [NodeClass](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/NodeClass.html) have default implementation
* Added [RootNode.cloneUninitialized](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/RootNode.html) that allows an optimizing runtime to efficiently create uninitialized clones of root nodes on demand.

## Version 0.23
1-Feb-2017
* Incompatible: Removed most of deprecated APIs from the [com.oracle.truffle.api.source package](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/source/package-summary.html).
* Enabled the new flat generated code layout for Truffle DSL as default. To use it just recompile your guest language with latest Truffle annotation processor. The new layout uses a bitset to encode the states of specializations instead of using a node chain for efficiency. The number of specializations per operation is now limited to 127 (with no implicit casts used). All changes in the new layout are expected to be compatible with the old layout. The optimization strategy for implicit casts and fallback handlers changed and might produce different peak performance results.
* Deprecated the frame argument for [IndirectCallNode](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/IndirectCallNode.html) and [DirectCallNode](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/DirectCallNode.html). The frame argument is no longer required.
* Deprecated [FrameInstance](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/frame/FrameInstance.html).getFrame(FrameAccess, boolean). Usages need to be replaced by FrameInstance.getFrame(FrameAccess). The slowPath parameter was removed without replacement.
* Deprecated FrameAccess.NONE without replacement.
* [FrameInstance](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/frame/FrameInstance.html).getFrame now throws an AssertionError if a local variable of a frame was written in READ_ONLY frame access mode.

## Version 0.22
13-Jan-2017
* [TruffleLanguage.isVisible](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html#isVisible-C-java.lang.Object-) allows languages to control printing of values in interactive environments
* [PolyglotEngine](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/vm/PolyglotEngine.html)`.findGlobalSymbols` that returns `Iterable`
* [TruffleLanguage](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html)`.importSymbols` that returns `Iterable`
* [RootNode.setCallTarget](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/RootNode.html#setCallTarget-com.oracle.truffle.api.RootCallTarget-) is deprecated
* Generic parsing method [TruffleLanguage](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html).`parse(`[ParsingRequest](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.ParsingRequest.html) `)` replaces now deprecated multi-argument `parse` method.
* Added [TruffleLanguage.findMetaObject](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html#findMetaObject-C-java.lang.Object-) and [DebugValue.getMetaObject](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/DebugValue.html#getMetaObject--) to retrieve a meta-object of a value.
* Added [TruffleLanguage.findSourceLocation](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html#findSourceLocation-C-java.lang.Object-) and [DebugValue.getSourceLocation](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/DebugValue.html#getSourceLocation--) to retrieve a source section where a value is declared.
* Added [TruffleLanguage.Registration.interactive()](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.Registration.html#interactive--) and [PolyglotEngine.Language.isInteractive()](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/vm/PolyglotEngine.Language.html#isInteractive--) to inform about language interactive capability
* Deprecated the @[Specialization](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/dsl/Specialization.html) contains attribute and renamed it to replaces.
* Deprecated @[ShortCircuit](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/dsl/ShortCircuit.html) DSL annotation without replacement. It is recommended to implement short circuit nodes manually without using the DSL.
* Added Truffle DSL [introspection API](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/dsl/Introspection.html) that provides runtime information for specialization activation and cached data.

## Version 0.21
6-Dec-2016
* Added [Source.isInteractive()](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/source/Source.html#isInteractive--) to inform languages of a possibility to use polyglot engine streams during execution.
* Unavailable [SourceSection](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/source/SourceSection.html)s created by different calls to createUnavailableSection() are no longer equals(). This means builtins can share a single Source and call createUnavailableSection() for each builtin to be considered different in instrumentation.

## Version 0.20
23-Nov-2016
* Deprecated [Node.getAtomicLock()](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/Node.html#getAtomicLock--) and replaced it with Node.getLock() which returns a Lock.
* Switching the source and target levels to 1.8
* Significant improvements in Java/Truffle interop

## Version 0.19
27-Oct-2016
* New helper methods in [JavaInterop](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/java/JavaInterop.html): `isArray`, `isBoxed`, `isNull`, `isPrimitive`, `unbox`, `asTruffleValue`.
* Relaxed the restrictions for calling methods on [SuspendedEvent](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/SuspendedEvent.html) and [DebugStackFrame](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/DebugStackFrame.html) from other threads than the execution thread. Please see the javadoc of the individual methods for details.

## Version 0.18
1-Oct-2016
* Added [Instrumenter](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/Instrumenter.html).querySourceSections(SourceSectionFilter) to get a filtered list of loaded instances.
* Added [SourceSectionFilter](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/SourceSectionFilter.html).ANY, which always matches.
* Added [Message.KEYS](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/Message.html#KEYS) to let languages enumerate properties of its objects
* Deprecated [LineLocation](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/source/LineLocation.html), [SourceSection](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/source/SourceSection.html).getLineLocation(), [Source](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/source/Source.html).createLineLocation(int) without replacement.
* Deprecated [SourceSection](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/source/SourceSection.html).getShortDescription(); users can replace uses with their own formatting code.
* Deprecated [SourceSection](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/source/SourceSection.html).createUnavailable(String, String) and replaced it with.
* Added [Source](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/source/Source.html).createUnavailableSection(), [SourceSection](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/source/SourceSection.html).isAvailable() to find out whether a source section is available.
* [SourceSection](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/source/SourceSection.html).createSourceSection(int,int) now only throws IllegalArgumentExceptions if indices that are out of bounds with the source only when assertions (-ea) are enabled.
* Deprecated [Source](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/source/Source.html).createSection(int, int, int, int)

## Version 0.17
1-Sep-2016

#### Removals, Deprecations and Breaking Changes

* This release removes many deprecated APIs and is thus slightly incompatible
  * Remove deprecated instrumentation API package `com.oracle.truffle.api.instrument` and all its classes.
  * Remove deprecated API method [TruffleLanguage](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html)`.isInstrumentable(Node)`, `TruffleLanguage.getVisualizer()`, `TruffleLanguage.createWrapperNode()`, `TruffleLanguage.Env.instrumenter()`, `RootNode.applyInstrumentation()`
  * Remove deprecated API [Debugger](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/Debugger.html)`.setTagBreakpoint`
  * Remove deprecated API [RootNode](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/RootNode.html)`.applyInstrumentation`
  * Remove deprecated tagging API in [SourceSection](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/source/SourceSection.html) and [Source](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/source/Source.html).

* [PolyglotEngine](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/vm/PolyglotEngine.html)
`eval` method and few similar ones no longer declare `throws IOException`.
The I/O now only occurs when operating with [Source](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/source/Source.html).
The evaluation of already loaded sources doesn't need to perform any I/O operations and
thus it makes little sense to require callers to handle the `IOException`.
This change is binary compatible, yet it is source *incompatible* change.
You may need to [adjust your sources](https://github.com/graalvm/fastr/commit/09ab156925d24bd28837907cc2ad336679afc7a2)
to compile.
* Deprecate support for the "identifier" associated with each [SourceSection](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/source/SourceSection.html)
* Deprecated `PolyglotEngine.Builder.onEvent(EventConsumer)` and class `EventConsumer`, debugger events are now dispatched using the `DebuggerSession`.
* [@Fallback](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/dsl/Fallback.html) does not support type specialized arguments anymore.

#### Additions

* All debugging APIs are now thread-safe and can be used from other threads.
* Changed the debugging API to a session based model.
  * Added [Debugger](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/Debugger.html)`.find(TruffleLanguage.Env)` to lookup the debugger when inside a guest language implementation.
  * Added [Debugger](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/Debugger.html)`.startSession(SuspendedCallback)` to start a new debugging session using a SuspendedCallback as replacement for `ExecutionEvent.prepareStepInto()`.
  * Added class [DebuggerSession](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/DebuggerSession.html) which represents a debugger session where breakpoints can be installed and the execution can be suspended and resumed.
  * Added [Breakpoint](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/Breakpoint.html)`.newBuilder` methods to create a new breakpoint using the builder pattern based on Source, URI or SourceSections.
  * Added [Breakpoint](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/Breakpoint.html)`.isResolved()` to find out whether the source location of a breakpoint is loaded by the guest language.
  * Added [Breakpoint](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/Breakpoint.html)`.isDisposed()` to find out whether a breakpoint is disposed.
  * Added [SuspendedEvent](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/SuspendedEvent.html)`.getReturnValue()` to get return values of calls during debugging.
  * Added [SuspendedEvent](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/SuspendedEvent.html)`.getBreakpoints()` to return the breakpoints that hit for a suspended event.
  * Added [SuspendedEvent](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/SuspendedEvent.html)`.getStackFrames()` to return all guest language stack frames.
  * Added [SuspendedEvent](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/SuspendedEvent.html)`.getTopStackFrame()` to return the topmost stack frame.
  * Added [SuspendedEvent](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/SuspendedEvent.html)`.getSourceSection()` to return the current guest language execution location
  * Added [SuspendedEvent](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/SuspendedEvent.html)`.getSourceSections()` to return all guest language execution locations of the current method in the AST.
  * Added class [DebugStackFrame](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/DebugStackFrame.html) which represents a guest language stack frame. Allows to get values from the current stack frame, access stack values and evaluate inline expressions.
  * Added class [DebugValue](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/DebugValue.html) which represents a value on a stack frame or the result of an evaluated expression.
  * Added class [DebuggerTester](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/DebuggerTester.html) which represents a utility for testing guest language debugger support more easily.
  * Deprecated [Breakpoint](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/Breakpoint.html)`.getCondition()` and replaced it with [Breakpoint](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/Breakpoint.html)`.getConditionExpression()` to return a String instead of a Source object.
  * Deprecated [Breakpoint](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/Breakpoint.html)`.setCondition(String)` and replaced it with [Breakpoint](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/Breakpoint.html)`.setConditionExpression(String)` to avoid throwing IOException.
  * Deprecated class `ExecutionEvent` and replaced it with [Debugger](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/Debugger.html)`.startSession(SuspendedCallback)`
  * Deprecated [Debugger](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/Debugger.html) methods setLineBreakpoint, getBreakpoints, pause. Replacements are available in the DebuggerSession class
  * Deprecated [Breakpoint](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/Breakpoint.html)`.getState()` to be replaced with [Breakpoint](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/Breakpoint.html)isResolved(), [Breakpoint](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/Breakpoint.html)isDisposed() and [Breakpoint](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/Breakpoint.html)`.isEnabled()`.
  * Deprecated [SuspendedEvent](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/SuspendedEvent.html)`.getNode()` and [SuspendedEvent](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/SuspendedEvent.html).getFrame() without direct replacement.
  * Deprecated [SuspendedEvent](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/SuspendedEvent.html)`.getRecentWarnings()` and replaced it with [SuspendedEvent](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/SuspendedEvent.html).getBreakpointConditionException(Breakpoint)
  * Deprecated [SuspendedEvent](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/SuspendedEvent.html)`.eval` and replaced it with `DebugStackFrame.eval(String)`
  * Deprecated [SuspendedEvent](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/SuspendedEvent.html)`.getStack()` and replaced it with [SuspendedEvent](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/SuspendedEvent.html).getStackFrames()
  * Deprecated [SuspendedEvent](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/SuspendedEvent.html)`.toString(Object, FrameInstance)` and replaced it with `DebugValue.as(String.class)`.

* [TruffleLanguage.createContext](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html#createContext-com.oracle.truffle.api.TruffleLanguage.Env-)
supports [post initialization callback](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html#initializeContext-C-)
* Added [SourceSectionFilter.Builder](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/SourceSectionFilter.Builderhtml).`sourceIs(SourcePredicate)` to filter for source sections with a custom source predicate.
* Added [TruffleInstrument.Env](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/TruffleInstrument.Env.html).`isEngineRoot(RootNode)` to find out where the context of the current evaluation ends when looking up the guest language stack trace with `TruffleRuntime.iterateFrames()`.
* Added [TruffleInstrument.Env](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/TruffleInstrument.Env.html).`toString(Node, Object)` to allow string conversions for objects given a Node to identify the guest language.
* Added [EventContext](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/EventContext.html).`lookupExecutionEventNode(EventBinding)` to lookup other execution event nodes using the binding at a source location.
* Added [Node.getAtomicLock()](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/Node.html#getAtomicLock--) to allow atomic updates that avoid creating a closure.

## Version 0.16
* [Layout](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/object/dsl/Layout.html)
  now accepts an alternative way to construct an object with the `build` method instead of `create`.
* [TruffleTCK](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/tck/TruffleTCK.html) tests simple operation on foreign objects. For example, a simple WRITE accesss, a HAS_SIZE access, or an IS_NULL access. It also tests the message resolution of Truffle language objects, which enables using them in other languages.

## Version 0.15
1-Jul-2016
* [Source](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/source/Source.html) shall be
constructed via its `newBuilder` methods. The other ways to construct or modify
source objects are now deprecated.
* [RootNode.getName](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/RootNode.html#getName--)
to provide name of a method or function it represents.
* Instruments are now [loaded eagerly](https://github.com/graalvm/graal/commit/81018616abb0d4ae68e98b7fcd6fda7c8d0393a2) -
which has been reported as an observable behavioral change.
* The [Instrumenter](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/Instrumenter.html)
now allows one to observe when sources and source sections are being loaded via
[attaching a listener](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/Instrumenter.html#attachLoadSourceListener-com.oracle.truffle.api.instrumentation.SourceSectionFilter-T-boolean-).
* Control the way loops are exploded with a new [LoopExplosionKind](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/ExplodeLoop.LoopExplosionKind.html)
enum.
* [SuspendedEvent](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/SuspendedEvent.html#toString-java.lang.Object-com.oracle.truffle.api.frame.FrameInstance-)
provides a way to convert any value on stack to its string representation.
* [TruffleTCK](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/tck/TruffleTCK.html) checks
whether languages properly support being interrupted after a time out
* Language implementations are encouraged to mark their internal sources as
[internal](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/source/Source.html#isInternal--)

## Version 0.14
2-Jun-2016
* [Source](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/source/Source.html) has been
rewritten to be more immutable. Once (part of) content of a source is loaded, it cannot be
changed.
* Methods `fromNamedAppendableText`, `fromNamedText` and `setFileCaching` of
`Source` has been deprecated as useless or not well defined
* New method `Source`.[getURI()](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/source/Source.html#getURI--)
has been introduced and should be used as a persistent identification of `Source` rather than
existing `getName()` & co. methods. Debugger is using the `URI` to
[attach breakpoints](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/Debugger.html#setLineBreakpoint-int-java.net.URI-int-boolean-)
to not yet loaded sources
* Debugger introduces new [halt tag](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/DebuggerTags.AlwaysHalt.html) to
make it easier to simulate concepts like JavaScript's `debugger` statement
* Debugger can be paused via the Debugger.[pause](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/Debugger.html#pause--)
method
* [@CompilationFinal](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/CompilerDirectives.CompilationFinal.html)
annotation can now specify whether the finality applies to array elements as well
* [TruffleTCK](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/tck/TruffleTCK.html) has been
enhanced to test behavior of languages with respect to foreign array objects


## Version 0.13
22-Apr-2016
* `AcceptMessage` has been deprecated, replaced by
[MessageResolution](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/MessageResolution.html) &
[co](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/Resolve.html). annotations.
Now all message-oriented annotations need to be placed in a single source file.
That simplifies readability as well as improves incremental compilation in certain systems.
* Deprecated `Node.assignSourceSection` removed. This reduces the amount of memory
occupied by [Node](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/Node.html)
instance.
* `PolyglotEngine.Value.execute` is now as fast as direct `CallTarget.call`.
Using the [PolyglotEngine](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/vm/PolyglotEngine.html)
abstraction now comes with no overhead. Just [JPDA debuggers](http://wiki.apidesign.org/wiki/Truffle#Debugging_from_NetBeans)
need to
[turn debugging on](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/Debugger.html#find-com.oracle.truffle.api.vm.PolyglotEngine-)
explicitly.
* Sharing of efficient code/AST between multiple instances of
[PolyglotEngine](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/vm/PolyglotEngine.html)
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
