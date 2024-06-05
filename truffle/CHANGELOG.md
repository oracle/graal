# Truffle Changelog

This changelog summarizes major changes between Truffle versions relevant to languages implementors building upon the Truffle framework. The main focus is on APIs exported by Truffle.

## Version 24.1.0
* GR-51253 Extend allowed DynamicObject shape flags from 8 to 16 bits.
* GR-42882 Changed behavior: Java host interop no longer exposes bridge methods.
* GR-51385 Added an instrumentation filter to include available source sections only: `SourceSectionFilter.Builder#sourceSectionAvailableOnly(boolean)`
* GR-51385 Added a debugger filter to suspend in available source sections only: `SuspensionFilter.Builder#sourceSectionAvailableOnly(boolean)`.
* GR-52443 Removed many deprecated `DynamicObject` APIs, deprecated since 22.2 or earlier (`Shape` methods: `addProperty`, `defineProperty`, `removeProperty`, `replaceProperty`, `newInstance`, `createFactory`, `getObjectType`, `changeType`, `getLayout`, `getMutex`, `getParent`, `allocator`, and related interfaces; `Property.set*`, `*Location` methods: `getInternal`, `setInternal`, `set*`, `getType`, and `ObjectLocation`).
* GR-51136 Uninitialized static slots of a `Frame` can now be read, and returns the default value for the access kind. 
* GR-38322 Added `--engine.TraceMissingSafepointPollInterval=N` to show Java stacktraces when there are [missing `TruffleSafepoint.poll()` calls](https://github.com/oracle/graal/blob/master/truffle/docs/Safepoints.md#find-missing-safepoint-polls).
* GR-52644 Deprecated `TruffleLanguage.Registration.needsAllEncodings`, no longer needs to be declared. It is sufficient for a language module to require `org.graalvm.shadowed.jcodings` to enable all string encodings.
* GR-51172 Add `CompilerDirectives.ensureAllocatedHere` to mark an allocation as non-movable. This allows language developers to mark special allocations as non-optimizable to allow better control for allocations potentially throwing OutOfMemoryErrors.
* GR-28103 Deprecated `com.oracle.truffle.api.utilities.JSONHelper` as it is untested, and in its current state does not contain any special logic for dumping AST. JSON printing of AST nodes should be delegated to an external library if necessary. This class will be removed in a future version.
* GR-54085 Added [`MathUtils`](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/utilities/MathUtils.html) API providing additional mathematical functions useful for language implementations, namely: `asinh`, `acosh`, and `atanh`.
* GR-49484 Added `TruffleStackFrameElement.getBytecodeIndex()` to access bytecode indices of a stack frame. Bytecode based languages should consider implementing `RootNode#findBytecodeIndex(Node, Frame)` to resolve the bytecode index.
* GR-49484 Deprecated `RootNode.isCaptureFramesForTrace()`. Implementers should use `RootNode.isCaptureFramesForTrace(Node)` instead.
* GR-28866 Added `TruffleLanguage.Env.getScopePublic(LanguageInfo)` and `TruffleLanguage.Env.getScopeInternal(LanguageInfo)` to allow languages direct access to other language scopes to implement new polyglot builtins.
* GR-28866 Deprecated `TruffleLanguage.Env.isPolyglotEvalAllowed()`. Replace usages with `TruffleLanguage.Env.isPolyglotEvalAllowed(LanguageInfo)`. Please see javadoc for the updated usage.
* GR-52843 Deprecated `Node.getCost()` and the associated `NodeCost` class without replacement. Truffle DSL no longer generates implementations of this method automatically and will therefore always return `NodeCost.MONOMORPHIC` by default. This is intended to reduce the binary footprint.
* GR-52843 Added the `UnadoptableNode` interface. This is interface should be preferred to overriding `Node.isAdoptable()` if the result is statically known.

## Version 24.0.0

* GR-45863 Yield and resume events added to the instrumentation:
	* `ExecutionEventListener.onYield()` and `ExecutionEventNode.onYield()` is invoked on a yield of the current thread
	* `ExecutionEventListener.onResume()` and `ExecutionEventNode.onResume()` is invoked on a resume of the execution on the current thread after a yield
	* `ProbeNode.onYield()` and `ProbeNode.onResume()`
	* `GenerateWrapper` has new `yieldExceptions()` and `resumeMethodPrefix()` parameters to automatically call the new `onYield()`/`onResume()` methods from wrapper nodes.
	* `RootNode.isSameFrame()` and `TruffleInstrument.Env.isSameFrame()` added to test if two frames are the same, to match the yielded and resumed execution.
* GR-45863 Adopted onYield() and onResume() instrumentation events in the debugger stepping logic.
* [GR-21361] Remove support for legacy `<language-id>.home` system property. Only `org.graalvm.language.<language-id>.home` will be used.
* GR-41302 Added the `--engine.AssertProbes` option, which asserts that enter and return are always called in pairs on ProbeNode, verifies correct behavior of wrapper nodes. Java asserts need to be turned on for this option to have an effect.
* GR-48816 Added new interpreted performance warning to Truffle DSL.
* GR-44706 Relaxed `InteropLibrary` invariant assertions for side-effecting members (i.e. `hasMemberReadSideEffects` or `hasMemberWriteSideEffects`) for `readMember`, `invokeMember`, `writeMember`, and `removeMember`, allowing them to succeed even if `isMemberReadable`, `isMemberInvocable`, `isMemberWritable`, and `isMemberRemovable`, respectively, returned `false` for that member. This avoids spurious assertion failures for accessor and proxy members.
* GR-49386 Added `InteropLibrary#readBuffer(long, byte[], int, int)` to enable bulk reads of buffers into byte arrays.
* [GR-50262] Added the system property `-Dtruffle.UseFallbackRuntime=true`. This property is preferred over the usage of `-Dtruffle.TruffleRuntime=com.oracle.truffle.api.impl.DefaultTruffleRuntime`.


## Version 23.1.0

* GR-45123 Added `GenerateInline#inlineByDefault` to force usage of inlined node variant even when the node has also a cached variant (`@GenerateCached(true)`).
* GR-45036 Improved IGV IR dumping. Dump folders for Truffle now include the compilation tier to differentiate compilations better. Inlined IR graphs are now additionally dumped in separate folders if dump level is >= 2.
* GR-45036 Improved IGV AST dumping. The Truffle AST is now dumped as part of the IR dump folder. The dumped AST tree now shows all inlined ASTS in a single tree. Individual functions can be grouped using the "Cluster nodes" function in IGV (top status bar). Root nodes now display their name e.g. `SLFunctionBody (root add)`. Every AST node now has a property `graalIRNode` that allows to find the corresponding Graal IR constant if there is one. 
* GR-45284 Added Graal debug options `TruffleTrustedNonNullCast` and `TruffleTrustedTypeCast` that allow disabling trusted non-null and type casts in Truffle, respectively. Note that disabling trusted type casts effectively disables non-null casts, too.
* GR-44211 Added `TruffleLanguage.Env#newTruffleThreadBuilder(Runnable)` to create a builder for threads that have access to the appropriate `TruffleContext`. All existing `TruffleLanguage.Env#createThread` methods have been deprecated. On top of what the deprecated methods provided, the builder now allows to specify `beforeEnter` and `afterLeave` callbacks for the created threads.
* GR-44211 Added `TruffleContext#leaveAndEnter(Node, Interrupter, InterruptibleFunction, Object)` to be able to interrupt the function run when the context is not entered. The exisiting API `TruffleContext#leaveAndEnter(Node, Supplier)` is deprecated.
* GR-44211 Removed the deprecated method `TruffleSafepoint#setBlocked(Node, Interrupter, Interruptible, Object, Runnable, Runnable)`.
* GR-44211 Added `TruffleSafepoint#setBlocked(Node, Interrupter, Interruptible, Object, Runnable, Consumer)`. It replaces the method `TruffleSafepoint#setBlockedWithException(Node, Interrupter, Interruptible, Object, Runnable, Consumer)` that is now deprecated.
* GR-44211 Added `TruffleSafepoint#setBlockedFunction(Node, Interrupter, InterruptibleFunction, Object, Runnable, Consumer)` to be able to return an object from the interruptible functional method.
* GR-44211 Added `TruffleSafepoint#setBlockedThreadInterruptibleFunction(Node, InterruptibleFunction, Object)` as a short-cut method to allow setting the blocked status for methods that throw `InterruptedException` and support interrupting using `Thread#interrupt()`.
* GR-44829 TruffleStrings: added specialized TruffleStringBuilder types for better performance on UTF encodings.
* GR-46146 Added `TruffleLanguage#ContextLocalProvider` and `TruffleInstrument#ContextLocalProvider`, and deprecated `TruffleLanguage.createContextLocal`, `TruffleLanguage.createContextThreadLocal`, `TruffleInstrument.createContextLocal` and `TruffleInstrument.createContextThreadLocal`. Starting with JDK 21, the deprecated methods trigger the new this-escape warning. The replacement API avoids the warning.
* GR-44217 In the past, on a GraalVM JDK, languages or instruments could be provided using `-Dtruffle.class.path.append`, but are now loaded from the application module path. The truffle class path is deprecated and should no longer be used, but remains functional. Languages are not picked up from the application class path, so the language first needs to be [migrated](https://github.com/oracle/graal/blob/master/truffle/docs/ModuleMigration.md). Truffle languages or instruments installed as a GraalVM component in the GraalVM JDK are still loaded in an unnamed module. However, GraalVM components will be deprecated, so languages and instruments should be migrated to the module path.
* GR-46181 `truffle-tck.jar` is not included in GraalVM artifacts anymore. It is still available via Maven.
* GR-46181 `truffle-dsl-processor.jar` is not included in GraalVM artifacts anymore. It is still available via Maven.
* GR-44222 Deprecated several experimental engine options and moved them to use the `compiler` prefix instead of the `engine` prefix. You can search for these options with this regexp: `git grep -P '\bengine\.(EncodedGraphCache|ExcludeAssertions|FirstTierInliningPolicy|FirstTierUseEconomy|InlineAcrossTruffleBoundary|InlineOnly|Inlining|InliningExpansionBudget|InliningInliningBudget|InliningPolicy|InliningRecursionDepth|InliningUseSize|InstrumentBoundaries|InstrumentBoundariesPerInlineSite|InstrumentBranches|InstrumentBranchesPerInlineSite|InstrumentFilter|InstrumentationTableSize|IterativePartialEscape|MaximumGraalGraphSize|MethodExpansionStatistics|NodeExpansionStatistics|NodeSourcePositions|ParsePEGraphsWithAssumptions|TraceInlining|TraceInliningDetails|TraceMethodExpansion|TraceNodeExpansion|TracePerformanceWarnings|TraceStackTraceLimit|TreatPerformanceWarningsAsErrors)\b'`.
* GR-44222 The following deprecated debugging options were removed in this release:
	* `engine.InvalidationReprofileCount`: The option no longer has any effect. Remove the usage to migrate.
	* `engine.ReplaceReprofileCount`: The option no longer has any effect. Remove the usage to migrate.
	* `engine.PerformanceWarningsAreFatal`: Use `engine.CompilationFailureAction=ExitVM` and `compiler.TreatPerformanceWarningsAsErrors=<PerformanceWarningKinds>` instead.
	* `engine.PrintExpansionHistogram`: Superseded by `engine.TraceMethodExpansion`.
	* `engine.ForceFrameLivenessAnalysis`: The option no longer has any effect. Remove the usage to migrate.
	* `engine.CompilationExceptionsArePrinted`: Use `engine.CompilationFailureAction=Print` instead.
	* `engine.CompilationExceptionsAreThrown`: Use `engine.CompilationFailureAction=Throw` instead.
	* `engine.CompilationExceptionsAreFatal`: Use `engine.CompilationFailureAction=ExitVM` instead.
* GR-44420 Added `TruffleLanguage.finalizeThread(Object, Thread)` to allow languages run finalization hooks for initialized threads before the context is disposed.
* GR-45923 Added `EventBinding.tryAttach()` to try to attach a binding, if not disposed or attached already.
* GR-20628 Added atomic byte-array operations to `ByteArraySupport` and subclasses.
* GR-39571 Added `TranscodingErrorHandler` to `TruffleString.SwitchEncodingNode`. 
* GR-46345 Added a support for the lazy unpacking of language and instrument resources necessary for execution. This support replaces the concept of language homes for Maven language and tool deployment. For a language or instrument that requires additional files to execute, it needs to follow these steps:
  * Bundle the necessary files into a jar distribution.
  * Implement the `InternalResource` interface for handling the resource file unpacking.
  * Call the `Env#getInternalResource` when the language or instrument needs the bundled resource files. This method ensures that the requested `InternalResource` is unpacked and provides a directory containing the unpacked files. Since unpacking internal resources can be an expensive operation, the implementation ensures that internal resources are cached.
* GR-44464 Added `TruffleString.ToValidStringNode` for encoding-level string sanitization.

## Version 23.0.0

* GR-38526 Added `TruffleLanguage.Env#isSocketIOAllowed()`. The method returns true if access to network sockets is allowed.
* GR-41634 Added `TruffleLanguage.Env#isFileIOAllowed()`. The method returns true if access to files is allowed.
* Deprecated `TruffleLanguage.Env#isIOAllowed()`. To migrate, use `TruffleLanguage.Env#isFileIOAllowed()`.
* GR-41408 Added `Version.format(String)`, to support formatting the version using a custom format string, e.g. for use in URLs.
* GR-41408 Added `${graalvm-version}` and `${graalvm-website-version}` substitutions for the `website` property of language and instrument registrations.
* GR-41034 Added `TruffleInstrument.Env.getTruffleFile(TruffleContext, ...)` methods to allow reading a truffle file from a specific context without being entered. Deprecated `TruffleInstrument.Env.getTruffleFile(...)` methods that do not take the `TruffleContext`.
* GR-42271 Native image build verifies that the context pre-initialization does not introduce absolute TruffleFiles into the image heap. The check can be disabled using the `-H:-TruffleCheckPreinitializedFiles` native image option.
* GR-41369 The icu4j language initializes charsets while building the native image. Languages depending on the icu4j language can no longer use `--initialize-at-run-time=com.ibm.icu`.
* GR-40274 TruffleStrings: added AsNativeNode and GetStringCompactionLevelNode.
* GR-39189 Added attach methods on the `Instrumenter` class, that take `NearestSectionFilter` as a parameter. The new `NearestSectionFilter` class can be used to instrument or detect nearest locations to a given source line and column. For example, this can be used to implement breakpoints, where the exact line or column is not always precise and the location needs to be updated when new code is loaded.
* GR-39189 Added `InstrumentableNode.findNearestNodeAt(int line, int column, ...)` to find the nearest node to the given source line and column. This is an alternative to the existing method that takes character offset.
* GR-42674 It has been documented that methods `TruffleLanguage.Env#getPublicTruffleFile`, `TruffleLanguage.Env#getInternalTruffleFile`, `TruffleLanguage.Env#getTruffleFileInternal` and `TruffleInstrument.Env#getPublicTruffleFile` can throw `IllegalArgumentException` when the path string cannot be converted to a `Path` or uri preconditions required by the `FileSystem` do not hold.
* GR-31342 Implemented several new features for Truffle DSL and improved its performance:
	* Added an `@GenerateInline` annotation that allows Truffle nodes to be object-inlined automatically. Object-inlined Truffle nodes become singletons and therefore reduce memory footprint. Please see the [tutorial](https://github.com/oracle/graal/blob/master/truffle/docs/DSLNodeObjectInlining.md) for further details.
	* Added an `@GenerateCached` annotation that allows users to control the generation of cached nodes. Use `@GenerateCached(false)` to disable cached node generation when all usages of nodes are object-inlined to save code footprint.
	* Updated Truffle DSL nodes no longer require the node lock during specialization, resulting in improved first execution performance. CAS-style inline cache updates are now used to avoid deadlocks when calling CallTarget.call(...) in guards. Inline caches continue to guarantee no duplicate values and are not affected by race conditions. Language implementations should be aware that the reduced contention may reveal other thread-safety issues in the language.
	* Improved Truffle DSL node memory footprint by merging generated fields for state and exclude bit sets and improving specialization data class generation to consider activation probability. Specializations should be ordered by activation probability for optimal results.
	* Improved memory footprint by automatically inlining cached parameter values of enum types into the state bitset
	* Added `@Cached(neverDefault=true|false)` option to indicate whether the cache initializer will ever return a `null` or primitive default value. Truffle DSL now emits a warning if it is beneficial to set this property. Alternatively, the new `@NeverDefault` annotation may be used on the bound method or variable. The generated code layout can benefit from this information and reduce memory overhead. If never default is set to `true`, then the DSL will now use the default value instead internally as a marker for uninitialized values.
	* `@Shared` cached values may now use primitive values. Also, `@Shared` can now be used for caches contained in specializations with multiple instances. This means that the shared cache will be used across all instances of a specialization.
	* Truffle DSL now generates many more Javadoc comments in the generated code that try to explain the decisions of the code generator. 
	* Added inlined variants for all Truffle profiles in `com.oracle.truffle.api.profiles`. The DSL now emits recommendation warnings when inlined profiles should be used instead of the allocated ones.
	* Truffle DSL now emits many more warnings for recommendations. For example, it emits warnings for inlining opportunities, cached sharing or when a cache initializer should be designated as `@NeverDefault`. To ease migration work, we added several new ways to suppress the warnings temporarily for a Java package. For a list of possible warnings and further usage instructions, see the new [warnings page](https://github.com/oracle/graal/blob/master/truffle/docs/DSLWarnings.md) in the docs.
	* The DSL now produces warnings for specializations with multiple instances but an unspecified limit. The new warning can be resolved by specifying the desired limit (previously, default `"3"` was assumed)
	* Added the capability to unroll specializations with multiple instances. Unrolling in combination with node object inlining may further reduce the memory footprint of a Truffle node. In particular, if all cached states can be encoded into the state bit set of the generated node. See `@Specialization(...unroll=2)` for further details

* GR-31342 Deprecated `ConditionProfile.createBinaryProfile()` and `ConditionProfile.createCountingProfile()`. Use `ConditionProfile.create()` and `CountingConditionProfile.create()` instead.
* GR-31342 Added `ValueProfile.create()` that automatically creates an exact class value profile. This allows its usage in `@Cached` without specifying a cached initializer.
* GR-31342 The node `insert` method is now public instead of protected. This avoids the need to create cumbersome accessor methods when needed in corner-cases.
* GR-43599 Specifying the sharing group in `@Shared` is now optional for cached values. If not specified, the parameter name will be used as sharing group. For example, `@Shared @Cached MyNode sharedNode` will get the sharing group `sharedNode` assigned. It is recommended to use the explicit sharing group still if it improves readability or if the parameter name cannot be changed.
* GR-43492 `LanguageReference#get()` is now always supported inside of `InstrumentableNode#materializeInstrumentableNodes()`.
* GR-43944 Added `HostCompilerDirectives.inInterpreterFastPath()` which allows to mark branches that should only be executed in the interpreter, but also optimized like fast-path code in the host compiler.
* GR-25539 Added `InteropLibrary#fitsInBigInteger()` and `InteropLibrary#asBigInteger()` to access interop values that fit into `java.math.BigInteger` without loss of precision. A warning is produced for objects that export the `isNumber` interop message and don't export the new big integer messages.
* GR-25539 Added `DebugValue#fitsInBigInteger()` and `DebugValue#asBigInteger()`.
* GR-25539 Added `GenerateLibrary.Abstract#ifExportedAsWarning()` to specify a library message to be abstract only if another message is exported. A warning is produced that prompts the user to export the message.
* GR-43903 Usages of `@Specialization(assumptions=...)` that reach a `@Fallback` specialization now produce a suppressable warning. In most situations, such specializations should be migrated to use a regular guard instead. For example, instead of using `@Specialization(assumptions = "assumption")` you might need to be using `@Specialization(guards = "assumption.isValid()")`.
* GR-43903 Added `@Idempotent` and `@NonIdempotent` DSL annotations useful for DSL guard optimizations. Guards that only bind idempotent methods and no dynamic values can always be assumed `true` after they were `true` once on the slow-path. The generated code leverages this information and asserts instead of executes the guard on the fast-path. The DSL now emits warnings with for all guards where specifying the annotations may be beneficial. Note that all guards that do not bind dynamic values are assumed idempotent by default for compatibility reasons.
* GR-43663 Added RootNode#computeSize as a way for languages to specify an approximate size of a RootNode when number of AST nodes cannot be used (e.g. for bytecode interpreters).
* GR-42539 (change of behavior) Unclosed polyglot engines are no longer closed automatically on VM shutdown. They just die with the VM. As a result, `TruffleInstrument#onDispose` is not called for active instruments on unclosed engines in the event of VM shutdown. In case an instrument is supposed to do some specific action before its disposal, e.g. print some kind of summary, it should be done in `TruffleInstrument#onFinalize`.
* GR-42961 Added `TruffleString.ByteIndexOfCodePointSetNode`, which allows fast searching for a given set of codepoints.
* GR-42961 Added `TruffleString.GetCodeRangeImpreciseNode`, which allows querying the currently known code range without triggering a string scan.
* GR-42961 `TruffleString.FromJavaStringNode` no longer eagerly scans strings for their code range. To still get eager scanning of constant strings, use `fromConstant(String)`.
* GR-30473 Added support for sandbox policies. By default, languages and instruments support just the `TRUSTED` sandbox policy.
  * If a language wants to target a more restrictive sandbox policy, it must:
    1. Specify the most strict sandbox policy it satisfies using `TruffleLanguage.Registration#sandbox()`.
    2. For each option, the language must specify the most restrictive sandbox policy in which the option can be used via `Option#sandbox()`. By default, options have a `TRUSTED` sandbox policy.
    3.  If a language needs additional validation, it can use `TruffleLanguage.Env#getSandboxPolicy()` to obtain the current context sandbox policy.
  * If an instrument wants to target a more restrictive sandbox policy, it must:
    1. Specify the most strict sandbox policy it satisfies using `TruffleInstrument.Registration#sandbox()`.
    2. For each option, the instrument must specify the most restrictive sandbox policy in which the option can be used via `Option#sandbox()`. By default, options have a `TRUSTED` sandbox policy.
    3.  If an instrument needs additional validation, it can use `TruffleInstrument.Env#getSandboxPolicy()` to obtain the engine's sandbox policy.
  * Added `TruffleOptionDescriptors` extending `OptionDescriptors` by the ability to provide the option's `SandboxPolicy`.
* GR-43818 Library messages annotated with `@Deprecated` now emit a warning when they are exported. It is now possible to overload a message method by adding, removing a parameter or making the parameter type more generic. Also added `Message.isDeprecated()` to find out whether a message was deprecated at runtime.
* GR-44053 (change of behavior) The default implementation of `InteropLibrary.getExceptionStackTrace()` will now include host stack trace elements if [public host access is allowed](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/HostAccess.Builder.html#allowPublicAccess-boolean-).
* GR-44053 (change of behavior) Truffle stack trace information is now attached to host and internal exceptions via suppressed exceptions. The cause of an exception is never modified anymore.
* GR-44053 (change of behavior) A `StackOverflowError` or `OutOfMemoryError` crossing a Truffle call boundary will not be injected guest stack trace information anymore.
* GR-44723 `Truffle.getRuntime().getName()` and consequently `Engine.getImplementationName()` have been adjusted to return "Oracle GraalVM" instead of "GraalVM EE".
* GR-44211 Added `TruffleLanguage.Env#newTruffleThreadBuilder(Runnable)` to create a builder for threads that have access to the appropriate `TruffleContext`. All existing `TruffleLanguage.Env#createThread` methods have been deprecated. On top of what the deprecated methods provide, the builder allows specifying `beforeEnter` and `afterLeave` callbacks for the created threads. 
* GR-44211 Added `TruffleLanguage.Env#newTruffleThreadBuilder(Runnable)` to create a builder for threads that have access to the appropriate `TruffleContext`. All existing `TruffleLanguage.Env#createThread` methods have been deprecated. On top of what the deprecated methods provided, the builder now allows to specify `beforeEnter` and `afterLeave` callbacks for the created threads. 

## Version 22.3.0

* GR-40069 Added additional methods to the static frame API.
  * Added `copyStatic` to copy a static slot in cases where the underlying type is not known.
  * Added `clearStatic` to clear a static slot in cases where the underlying type is not known.
  * Added `swap...Static` to swap slots of known (primitive or object) or unknown underlying type.
* GR-40103 Potentially breaking: Static frame access is now validated when assertions are enabled. Reading a slot with a different type than written to leads to an `AssertionError`.
* GR-40163 Deprecated `TruffleLanguage.Env.newContextBuilder()` and replaced it with a new method `TruffleLanguage.Env.newInnerContextBuilder(String...)`. The new method does no longer inherit all privileges from the parent context and does no longer initialize the creator context by default. The new method also allows to set the permitted languages for the inner context similarly as in the polyglot embedding API. 
* GR-40163 Changed behavior: Inner contexts do no longer inherit application arguments from the outer context. It is now possible to set application arguments explicitly for inner contexts using `TruffleContext.Builder.arguments(String, String[])`.
* GR-40163 Changed behavior: Inner contexts do no longer use system exit on exit even if the polyglot embedder specified it with `Context.Builder.useSystemExit(boolean)` for the outer context.
* GR-40163 Added new capabilities to `TruffleContext`:
	* Added `TruffleContext.Builder.out(OutputStream)`, `TruffleContext.Builder.err(OutputStream)` and `TruffleContext.Builder.in(InputStream)` to customize streams for inner contexts. 
	* GR-35358 Added `TruffleContext.Builder.forceSharing(Boolean)` to force or deny code sharing for inner contexts.
	* GR-36927 Added `TruffleContext.Builder.option(String, String)` to override language options for inner contexts. This is currently only supported if all access privileges have been granted by the embedder.
	* Added `TruffleContext.Builder.inheritAllAccess(boolean)` which allows to enable access privilege inheritance from the outer context. By default this flag is `false`. 
	* Access privileges can now be individually granted or denied. Note that an inner context still cannot use any of the privileges that have not been granted to the outer context. For example, if the outer context has no access to IO then the inner context won't have access to IO even if the privilege is set for the inner context. The following new methods were added to configure privileges for inner contexts:
		* `TruffleContext.Builder.allowCreateThreads(boolean)` 
		* `TruffleContext.Builder.allowNativeAccess(boolean)` 
		* `TruffleContext.Builder.allowIO(boolean)` 
		* `TruffleContext.Builder.allowHostClassLoading(boolean)` 
		* `TruffleContext.Builder.allowHostLookup(boolean)` 
		* `TruffleContext.Builder.allowCreateProcess(boolean)` 
		* `TruffleContext.Builder.allowPolyglotAccess(boolean)` 
		* `TruffleContext.Builder.allowInheritEnvironmentAccess(boolean)` 
		* `TruffleContext.Builder.allowInnerContextOptions(boolean)` 
* GR-40163 Added `TruffleContext.initializePublic(Node, String)` and `TruffleContext.initializeInternal(Node, String)` to initialize a public or internal language of an inner context.
* GR-39354 TruffleStrings: added ErrorHandling parameter to CreateForwardIteratorNode and CreateBackwardIteratorNode.
* GR-40062 `String.indexOf` methods are no longer considered PE safe and using them will now fail the native-image block list check. Use `TruffleString` instead or put them behind a `@TruffleBoundary`.
* GR-39354 TruffleStrings: added ErrorHandling parameter to ByteLengthOfCodePointNode, CodePointAtIndexNode and CodePointAtByteIndexNode.
* GR-39219 Removed several deprecated APIs:
    * Removed deprecated `FrameSlot` API. The API was deprecated in 22.0.
    * Removed deprecated `CompilerOptions` API. The API was deprecated in 22.1.
    * Removed deprecated `TruffleRuntime.createCallTarget` and `RootNode.setCallTarget` API. The API was deprecated in 22.0.
    * Removed deprecated `TruffleContext.enter` and `TruffleContext.leave` API. The API was deprecated in 20.3.
    * Removed deprecated `MemoryFence` API. The API was deprecated in 22.1.
    * Removed deprecated `TruffleRuntime.getCallerFrame` and `TruffleRuntime.getCurrentFrame` API. The API was deprecated in 22.1.
    * Removed deprecated `@CachedContext` and `@CachedLibrary` API. The API was deprecated in 21.3.
    * Removed deprecated `get()` method from `LanguageContext` and `ContextReference` API. The API was deprecated in 21.3.
    * Removed deprecated equality `ValueProfile` . The API was deprecated in 21.2.
    * Removed deprecated `UnionAssumption`, `AlwaysValidAssumption` and `NeverValidAssumption`. The API was deprecated in 22.1.
* GR-35797 The [SnippetRun#getException()](https://www.graalvm.org/truffle/javadoc/org/graalvm/polyglot/tck/ResultVerifier.SnippetRun.html#getException--) now provides an `IllegalArgumentException` thrown during the snippet execution. The `IllegalArgumentException` is converted to a `PolyglotException` before it is returned.
* Added the `@HostCompilerDirectives.InliningCutoff` annotation that allows to manually tune inlining decisions for host inlining.
* Tuned host inlining heuristic for reduced code size. A new host inlining tuning guide is available in the [docs](https://github.com/oracle/graal/blob/master/truffle/docs/HostCompilation.md#host-inlining)
* GR-35007 (EE-only) The Truffle sandboxing CPU time limits (`sandbox.MaxCPUTime`) are also supported on the native image.
* GR-24927 The Truffle annotation processor now emits an error for methods annotated by a `@TruffleBoundary` annotation and a `Frame` parameter. Previously, the processor only reported an error for `VirtualFrame` parameters. To resolve this, either change the parameter to a `MaterializedFrame` , remove the parameter or remove the `@TruffleBoundary`.
* GR-24927 Truffle DSL now automatically materalizes frames when a `Frame` parameter is used in an uncached instance of a `@Specialization`. For example, if it rewrites itself to an uncached version due to the usage of a `@CachedLibrary`.
* GR-35007 (EE-only) The Truffle sandboxing CPU time limits (`sandbox.MaxCPUTime`) are also supported on the native image.
* GR-28705 Added `TruffleInstrument.Env#createSystemThread` to create a new thread designed to process instrument tasks in the background.
* GR-28705 Added `TruffleLanguage.Env#createSystemThread` to create a new thread designed to process language internal tasks in the background.
* GR-31304 `Debugger.disableStepping()` and `Debugger.restoreStepping()` added to disable/restore stepping on a dedicated thread on a specific code path.
* GR-39415 Experimental options are on by default in the TCK test Context. Added `LanguageProviders.additionalOptions()` which allows TCK providers to set their language's options in the test context (Attempts to set other languages' options will result in an `IllegalArgumentException` while creation the context).
* GR-38163 Introduced [RootNode.getParentFrameDescriptor](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/RootNode.html#getParentFrameDescriptor) to support identifying lexical scope parents of hot methods and compiling them earlier.

## Version 22.2.0

* GR-33829 Added support on libgraal for caching encoded graphs across Truffle compilations to speedup partial evaluation. The cache is enabled by default and can be enabled/disabled with the `--engine.EncodedGraphCache` option.
* GR-38925 Added `InteropLibrary.hasMetaParents(Object)` and `InteropLibrary.getMetaParents(Object)` that allow lookup of the hierarchy of parents for meta objects (e.g. super class or implemented interface of Java classes).
* GR-36557 Deprecated `--engine.MaximumGraalNodeCount` and introduced `--engine.MaximumGraalGraphSize` to control the maximum graal graph size during partial evaluation.
* GR-37493 Added `@DenyReplace` to deny replacement of final node types. 
* GR-37493 Potentially breaking: Disabled replace of all Truffle DSL generated uncached nodes. If you call `Node.replace()` on an uncached version of a generated node or library it will now fail with an `IllegalArgumentException`. As a rule of thumb, uncached versions of nodes should not ever be stored in `@Child` fields. Instead, they should always be used as singletons.
* GR-37493 Removed long time deprecated API `NodeFieldAccessor` without replacement. Added a some utility methods in `NodeUtil` as a replacement for this API: `NodeUtil.collectFieldNames(Class)`, `NodeUtil.collectNodeChildren(Node)` and `NodeUtil.collectNodeProperties(Node)`.
* GR-37100 Deprecated `BytecodeOSRNode.copyIntoOSRFrame(VirtualFrame, VirtualFrame, int)`, in favor of `BytecodeOSRNode.copyIntoOSRFrame(VirtualFrame, VirtualFrame, int, Object)`.
* GR-36944 Added new static APIs to `com.oracle.truffle.api.frame.Frame`:
    * Added new `Static` option to `com.oracle.truffle.api.frame.FrameSlotKind` for index-based slots. Frame slots using this kind cannot be changed to another kind later on. Static frame slots can only hold either a primitive or an `Object` value, never both at the same time.
    * Added new `get.../set...` methods postfixed by `Static` for exclusively accessing static frame slots.
    * Added new `copy.../clear...` methods postfixed by `Static` for exclusively copying and clearing static frame slots.
    * Static frame slots are intended for situations where the type of a variable in a frame slots is known ahead of time and does not need any type checks (e.g. in statically typed languages).
* GR-36557 Introduced `--engine.InliningUseSize` which changes the code size approximation during inlining to an approximation of the size of the graph rather than just the node count. This option is false by default.
* GR-37310 Add `BytecodeOSRNode.storeParentFrameInArguments` and `BytecodeOSRNode.restoreParentFrameFromArguments` to give languages more control over how frame arguments in bytecode OSR compilations are created.
* GR-35280 Implemented new domain specific inlining phase for Truffle interpreter host compilation. 
    * In native image hosts the new optimizations is applied to all methods annotated with `@BytecodeInterpreterSwitch` and methods which were detected to be used for runtime compilation. 
    * On HotSpot hosts the new optimizations will be applied to methods annotated with `@BytecodeInterpreterSwitch` only.
    * The annotation `@BytecodeInterpreterSwitchBoundary` was deprecated. Boundaries for the compilation are now inferred from directives like `CompilerDirective.transferToInterpreter()` and `@TruffleBoundary` automatically.
    * See the [HostOptimization.md](https://github.com/oracle/graal/blob/master/truffle/docs/HostCompilation.md) for further details.
* GR-38387 Deterministic and declaration order of `InteropLibrary.getMembers()` is now required.
* GR-38110 Added option to use `long` values as offsets for accessing memory through `ByteArraySupport`.
* GR-39029 Fixed issue in `InteropLibrary` that required `asDate` to be implemented whenever `isTime` is exported; correct dependency is on `isDate`.
* GR-38945 Truffle IGV dumping with log level 5 (e.g. `-Dgraal.Dump=Truffle:5`) now dumps the graph after each method that was fully partially evaluated. This enables debugging of problems only visible during partial evaluation.
* GR-34894 Removed deprecated `DynamicObject` APIs:
    * `Shape`: `getKeyList(Pred)`, `getPropertyList(Pred)`, `Pred`, `getObjectType`, `getId`, `isRelated`, `createSeparateShape`, `append`, `reservePrimitiveExtensionArray`, `hasTransitionWithKey`
    * `DynamicObject`: all deprecated constructors and methods (`get`, `set`, `contains`, `define`, `delete`, `size`, `isEmpty`, `setShapeAndGrow`, `setShapeAndResize`, `updateShape`, `copy`)
    * `ShapeListener`
    * `TypedLocation`
    * `Layout.newInstance`, `Layout.createShape`
    * `Property`: `copyWithRelocatable`, `copyWithFlags`, `isSame`, `relocate`, `set`, `setInternal`, `setGeneric`.
    * `IncompatibleLocationException`, `FinalLocationException` constructors
* GR-34894 Deprecated legacy and low-level `DynamicObject` APIs:
    * `Shape`: `Allocator`, `allocator`, `createFactory`, `newInstance`, `defineProperty`, `addProperty`, `changeType`, `getMutex`
    * `ObjectLocation`, `BooleanLocation`, `DoubleLocation`, `IntLocation`, `LongLocation`
    * `Location`: `canSet`, `set`, `setInternal`, `get`, `getInternal`, `incompatibleLocation`, `finalLocation`
    * `Property`: `create`, `get`, `set`, `setSafe`, `setGeneric`.
    * `ObjectType`
    * `DynamicObjectFactory`, `LocationModifier`, `LocationFactory`, `LayoutFactory`
    * `IncompatibleLocationException`, `FinalLocationException`
* GR-34894 Introduced `Location.isPrimitive()`, `Location.getConstantValue()`, and `Shape.makePropertyGetter(Object)`.
* GR-39058 The Static Object Model offers preliminary support for field-based storage also on Native Image. 

## Version 22.1.0

* GR-35924 Context preinitialization in combination with auxiliary engine caching now preinitializes a context for each sharing layer with the common configuration of previously created contexts.
* Added [TruffleStrings](https://github.com/oracle/graal/blob/master/truffle/docs/TruffleStrings.md), a flexible string implementation for all Truffle languages.
* Added a `@GeneratePackagePrivate` annotation to change the visibility of generated nodes to package-private even if the template node is public.
* Changed the default [`Object` target type mapping](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Value.html#as-java.lang.Class-) for values that have both array elements and members from `Map` to `List`.
* GR-36425 Truffle DSL assumption expressions are now always executed instead of cached if their return value can be proven a partial evaluation constant. For example, if an assumption is read from a field in the declared node or a `@Cached` parameter then the expression can be always executed instead of being cached. This improves memory footprint of the generated code as no fields for the assumption cache needs to be generated. Language implementations are encouraged to check all usages of `@Specialization(assumption=...)` and verify whether they can be expressed as a PE constant. Ensure that all fields accessed in the expression are either final or annotated with `@CompilationFinal` and do not bind any dynamic parameter. Note that the DSL cannot infer PE constantness from method bodies and is therefore limited to field accesses. The resolution works through an arbitrary number of field accesses, e.g. through other Node classes, if they are visible in the expression (e.g. `field1.field2.field3`). 
* Added [TruffleLanguage.Env#createHostAdapter](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.Env.html#createHostAdapter-java.lang.Object:A-) accepting host symbols and host classes as the types to extend, replacing and deprecating the `java.lang.Class`-based version.
* GR-10128 Added the `website` property to the [TruffleLanguage.Registration](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.Registration.html#website--) and [TruffleInstrument.Registration](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/TruffleInstrument.Registration.html#website--) allowing language and instrument developers to specify a URL for a web site with further information about their language/tool.
* GR-10128 Added the `usageSyntax` property to the [Option](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/Option.html#usageSyntax--) allowing developers to specify the syntax their option expects. See the javadoc for more information.
* Added `TruffleSafepoint.setAllowActions` to disable thread local actions temporarily for trusted internal guest code. Currently only allowed during the finalization of a context in `TruffleLanguage.finalizeContext(Object)`.
* Added `FrameDescriptor.getInfo()` and `FrameDescriptor.Builder.info()` to associate a user-defined object with a frame descriptor.
* GR-33851 Dropped Java 8 support.
* Deprecated `MemoryFence`. Please use `VarHandle` directly.
* Deprecated `TruffleSafepoint.setBlocked`, in favor of `TruffleSafepoint.setBlockedWithException`, which allows interception and handling of safepoint-thrown exceptions.
* GR-36525 Deprecated `CompilerOptions`. They had no effect for several releases already. Deprecated for removal.
* GR-22281 Deprecated `TruffleRuntime.getCurrentFrame()` and `TruffleRuntime.getCallerFrame()`. They were encouraging unsafe use of the `FrameInstance` class. Note that a `FrameInstance` instance must not be escaped outside the scope of the `FrameInstanceVisitor.visitFrame` method. Language implementers are encouraged to validate all usages of `TruffleRuntime.iterateFrames(...)`. We plan to enforce this rule in future versions of Truffle.
* GR-22281 Added `TruffleRuntime.iterateFrames(FrameInstanceVisitor visitor, int skipFrames)` that allows to efficiently skip a number of frames before the visitor is invoked. This was added to allow efficient migration of usages from the deprecated `TruffleRuntime.getCallerFrame()` method.
* Removed the deprecated `TruffleException` that was deprecated in the GraalVM 20.3.0. The `AbstractTruffleException` no longer implements `TruffleException`. `AbstractTruffleException` methods inherited from the `TruffleException` have been removed. As part of this removal, the recommendation for languages how to [handle exceptions](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/InteropLibrary.html#isException-java.lang.Object-) has been updated.
* Added methods to [TruffleContext.Builder](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleContext.Builder.html) that allow throwing custom guest exceptions when the new built context is cancelled, hard-exited, or closed and the corresponding exception is about to reach the outer context. In case the customization is not used and the new context is cancelled, hard-exited, or closed, Truffle newly throws an internal error.
    * Added [TruffleContext.Builder#onCancelled](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleContext.Builder.html#onCancelled-java.lang.Runnable-) that allows throwing a custom guest exception when the new context is cancelled.
    * Added [TruffleContext.Builder#onExited](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleContext.Builder.html#onExited-java.util.function.Consumer-) that allows throwing a custom guest exception when the new context is hard-exited.
    * Added [TruffleContext.Builder#onClosed](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleContext.Builder.html#onClosed-java.lang.Runnable-) that allows throwing a custom guest exception when the new context is closed.
* GR-35093 Deprecated `UnionAssumption`, use arrays of assumptions instead. Deprecated `NeverValidAssumption` and `AlwaysValidAssumption`, use `Assumption.NEVER_VALID` and `Assumption.ALWAYS_VALID` instead. Language implementations should avoid custom `Assumption` subclasses, they lead to performance degradation in the interpreter.
* GR-35093 Added `create()` constructor methods to profiles in `com.oracle.truffle.api.profiles` where appropriate to simplify use with Truffle DSL.

## Version 22.0.0
* Truffle DSL generated code now inherits all annotations on constructor parameters to the static create factory method.
* Added a [Message#getId()](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/library/Message.html#getId--) method returning a unique message id within a library.
* Added a [LibraryFactory#getMessages()](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/library/LibraryFactory.html#getMessages--) method returning a list of messages that the library provides.
*  Changed behavior of `RootNode#getCallTarget()` such that it lazily initializes its call target. This enforces a one-to-one relationship between root nodes and call targets, which avoids several problems, for example, with regard to instrumentation. As a consequence, `RootNode.setCallTarget()` and `TruffleRuntime#createCallTarget()` are deprecated now. Please use `RootNode#getCallTarget()` to access the call target of a root node from now on.
* In `TruffleLanguage.finalizeContext(Object)`, there is a new requirement for leaving all remaining unclosed inner contexts created by the language on all threads where the contexts are still active.
No active inner context is allowed after `TruffleLanguage.finalizeContext(Object)` returns. Not complying with this requirement will result in an internal error. Please note that inactive inner contexts are still closed implicitly by the parent context.
* Added `TruffleContext.closeExited(Node, int)` to hard exit an entered truffle context. See [the documentation](https://github.com/oracle/graal/blob/master/truffle/docs/Exit.md).
* Added `TruffleLanguage.exitContext(Object, ExitMode, int)` to allow languages perform actions before natural/hard context exit. Languages are encouraged to run all their shutdown hooks in exitContext instead of finalizeContext.
* Improved the output format for `engine.TraceCompilation` and `engine.TraceCompilationDetails`. See [Optimizing.md](https://github.com/oracle/graal/blob/master/truffle/docs/Optimizing.md) for details.
* Extended `HostObject` so that it exposes the `length` field and the `clone()` method of Java arrays as interop members. This can be disabled with `HostAccess.Builder.allowArrayAccess(false)`.
* Implicit cast checks are now generated in declaration order where the direct target type is always checked first. Languages implementations are encouraged to optimize their implicit cast declaration order by sorting them starting with the most frequently used type.
* When using the Static Object Model, storage classes can have precise object field types, not just `java.lang.Object`.
* Added `CompilerDirectives.hasNextTier()` to allow language implementations to control profiling in intermediate compilation tiers. In particular, `LoopNode.reportLoopCount()` should also be called in intermediate tiers as part of bytecode interpreters to improve last tier compilation.
* Introduced sharing layers. A sharing layer is a set of language instances that share code within one or more polyglot contexts. In previous versions language instances were shared individually whenever a new language context was created. Instead language instances are now reused for a new context if and only if the entire layer can be shared. A layer can be shared if all initialized languages of a layer support the same context policy and their options are compatible. Please note the following changes on observable language behavior:
    * For any executed Truffle node it can now be assumed that the current language instance will remain constant. This means that the language instance can always be safely stored in the AST even for nodes that are used through the interoperability protocol by other languages. It is still recommend to not store language instances in AST nodes, but use LanguageReferences instead to avoid additional memory footprint.
    * The method LanguageReference.get(Node), if called with an adopted and compilation final node, is now guaranteed to fold to a constant value during compilation.
    * TruffleLanguage.initializeMultipleContexts() is now guaranteed to be called prior to all created contexts of the same language instance. For existing languages this means that any assumption invalidated during initialization of multiple contexts can now become a regular boolean field. This should simplify language implementations as they no longer need to be able to change sharing mode after call targets were already loaded.
    * Language initialization will now fail if new language context is initialized and the language is incompatible to the sharing layer of the current context. For example, if sharing is enabled with a shared language already initialized, any new language with unsupported sharing will now fail to initialize. The recommended solution is to specify all required languages when creating the context in `Context.newBuilder(String...)`. 
    * The method `TruffleLanguage.areOptionsCompatible(OptionValues, OptionValues)` is now also called before the initialization of the first context of a language if sharing is enabled. This allows languages to enable/disable sharing based on a language specific option, and not just  statically. 
    * Language instances are no longer shared for inner contexts if sharing is not enabled for a context, even if the language instance would support sharing in principle. This change was necessary to avoid the need to initialize sharing after the first context was created.
    * More information on code sharing can be found in the [javadoc](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.ContextPolicy.html).
* Added the `--engine.TraceCodeSharing` option that allows to log debug information on code sharing.
* Added the `--engine.ForceCodeSharing` and `--engine.DisableCodeSharing` option that allows to force enable and force disable code sharing. This option is useful for testing to enable or disable sharing across all contexts of a process.
* Removed deprecated in `ArityException`.
* Removed deprecated methods in `ArityException`.
* Removed deprecated object DSL processor that was deprecated for several releases. 
* Removed deprecated encapsulating node accessor methods in `NodeUtil`.
* Removed deprecated method `LoopNode.executeLoop`.
* Removed many deprecated methods in `TruffleLanguage`, `TruffleLanguage.Env` and `TruffleInstrument.Env`. All of which were already deprecated for at least four releases.
* Removed deprecated `GraphPrintVisitor`.

* Added new APIs to `com.oracle.truffle.api.frame.Frame` and `com.oracle.truffle.api.frame.FrameDescriptor`:
    * Added a new "namespace" of index-based slots in `Frame` that is defined during construction of the frame descriptor and cannot be changed afterwards, and that is accessed using `int` indexes instead of `FrameSlot`s.
    * Added a second new "namespace" of slots (called auxiliary slots) in `Frame` that can be added to the frame descriptor dynamically (and which only supports "object" slots).
    * In addition to `get.../set...` methods, the new API also supports `copy` and `swap` of frame slots.
    * The `FrameSlot`-based API methods in `Frame` and `FrameDescriptor` were deprecated.
    * `FrameSlotTypeException` is now an unchecked exception, which simplifies many APIs and removes the need for the `FrameUtil` class.
* Changes to the way frame slots are handled during partial evaluation:
    * Removed the `FrameClearPhase` - now clearing the frame slots in the "clear" intrinsics instead.
    * Added a new `FrameAccessVerificationPhase` that detects improper pairing of frame slot types at merges, inserts deopts and outputs a performance warning: frame slots can now change type freely and will still be optimized by the frame intrinsics optimization, as long as the types are compatible at merges (whereas frame slots used to be restricted to one primitive type in the whole compilation unit).
* Made conversion rules for passing values to native code through the Truffle NFI more lenient.
    * Passing a `double` to a parameter of type `float` is allowed, possibly losing precision.
    * Passing signed integers to parameters of type `uint*` and unsigned integers to parameters of type `sint*` is allowed.

## Version 21.3.0
* Added a `@GenerateWrapper.Ignore` annotation to prevent methods from being instrumented in wrapper classes.
* The native image `TruffleCheckBlackListedMethods` option was deprecated and replaced by the `TruffleCheckBlockListMethods` option.
* Added new [Static Object Model](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/staticobject/package-summary.html) APIs to represent the layout of objects that, once defined, do not change the number and the type of their properties. It is particularly well suited for, but not limited to, the implementation of the object model of static programming languages. For more information, read the [Javadoc](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/staticobject/package-summary.html) and the [tutorial](https://github.com/oracle/graal/blob/master/truffle/docs/StaticObjectModel.md).
* Removed deprecated engine options: `engine.CompilationThreshold` and `engine.InliningTruffleTierOnExpand`
* Added `BytecodeOSRNode` interface to support on-stack replacement (OSR) for bytecode interpreters. OSR can improve start-up performance by switching from interpreted code to compiled code in the middle of execution. It is especially effective for targets with long-running loops, which can get "stuck" running in the interpreter without OSR. Refer to the [Javadoc](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/BytecodeOSRNode.html) and the [OSR guide](https://github.com/oracle/graal/blob/master/truffle/docs/OnStackReplacement.md) for more details.
* Removed support to read language and instrument registrations from `META-INF/truffle` files. Recompiling the TruffleLanguage or TruffleInstrument using the Truffle annotation processor automatically migrates the language or instrument to the new behavior. Languages are already migrated if they were compiled with a version later or equal than 19.3.
* Added [SourceSectionFilter#includes](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/SourceSectionFilter.html##includes-com.oracle.truffle.api.nodes.RootNode-com.oracle.truffle.api.source.SourceSection-java.util.Set-).
* Added [FrameInstance#getCompilationTier](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/frame/FrameInstance.html#getCompilationTier--) and [FrameInstancel#isCompilationRoot](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/frame/FrameInstance.htmll#isCompilationRoot--)
* Added `InteropLibrary.isValidValue(Object)` and `InteropLibrary.isValidProtocolValue(Object)`.
* Added `TruffleContext.evalPublic(Node, Source)` and `TruffleContext.evalInternal(Node, Source)` that allow to evaluate sources in an inner context and access values of the inner context safely.
* Added `TruffleContext.Builder.initializeCreatorContext(boolean)` that allows to disable initialization of the language that created the inner context.
* Added the ability to share values between contexts. Guest languages can now use values of the polyglot embedding API using host interop. This no longer leads to invalid sharing errors.
* Added `ReflectionLibrary.getUncached` method.
* Removed deprecated `TruffleLanguage.Registration#mimeType()`. Split up MIME types into `TruffleLanguage.Registration#characterMimeTypes()` and `TruffleLanguage.Registration#byteMimeTypes()`.
* Added a new and improved way to access the current language context and language instance of the thread.
    * Language and context references can now be stored in static final fields. See the [javadoc](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.ContextReference.html) for the new intended usage.
    * All thread local lookups have an efficient implementation on HotSpot and SubstrateVM, interpreted and compiled, eliminating the need to ever cache the value in the AST.
    * Using a compilation final node passed as parameter, the context and language value can be constant folded if it is known that only one language or context instance can exist.
    * Deprecated all other means of accessing the current context: `TruffleLanguage.getCurrentContext(Class)`, `RootNode.getCurrentContext(Class)`, `ContextReference.get()`, `Node.lookupContextReference(Class)` and `@CachedContext`.
    * Deprecated all other means of accessing the current language: `TruffleLanguage.getCurrentLanguage(Class)`,  `LanguageReference.get()`, `Node.lookupLanguageReference(Class)` and `@CachedLanguage`.
* Removed deprecated `TruffleLanguage.getContextReference()`.
* Added `--engine.TraceDeoptimizeFrame` to trace frame deoptimizations due to `FrameInstance#getFrame(READ_WRITE|MATERIALIZE)`.
* Added loop condition profiling to `LoopNode`, so the `RepeatingNode` no longer needs to profile or inject the loop count. Language implementations should remove loop condition profiles from their repeating nodes since they are redundant now.
* Added `ThreadLocalAction` constructor that allows to configure recurring thread local actions to be performed repeatedly. This allows to build debug tooling that need to gather information in every safepoint poll of a thread.
* Added `ExecuteTracingSupport` interface that allows tracing the calls to `execute` methods of a `Node`. 
* Changed `--engine.InstrumentExceptionsAreThrown` to true by default and deprecated [EventContext#createError](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/EventContext.html#createError-java.lang.RuntimeException-) without replacement. Instrument exception are now thrown by default and observable by the guest language application.
* `TruffleLanguage.Env#getPublicTruffleFile(URI)` and `TruffleLanguage.Env#getInternalTruffleFile(URI)` have been fixed to behave as specified and throw `UnsupportedOperationException` instead of `FileSystemNotFoundException`.
* Added `LibraryFactory.getMessages()` to allow to enumerate all messages of a library.
* Added `Engine.newBuilder(String...)` that also allows to restrict the permitted languages of an engine. The permitted languages of an engine are inherited by all created contexts.

## Version 21.2.0
* Added `TypeDescriptor.subtract(TypeDescriptor)` creating a new `TypeDescriptor` by removing the given type from a union or intersection type.
* Added `CompilerDirectives.blackhole(value)` which can be helpful for benchmarking.
* Added `TruffleLanguage#Env.registerOnDispose(Closeable)` registering `Closeable`s for automatic close on context dispose.
* Added `RootNode#countsTowardsStackTraceLimit()`, replacing `RootNode#isInternal()` as the criterion that determines whether a frame with the given root node counts towards the stack trace limit.
* Added `engine.UsePreInitializedContext` option which can be used to disable usage of pre-initialized context.
* Added `MemoryFence`: provides methods for fine-grained control of memory ordering.
* `ValueProfile.createEqualityProfile()` was deprecated without replacement. `Object.equals(Object)` cannot safely be used on compiled code paths. Use the Truffle Specialization DSL instead to implement caches with equality semantics. Making `Object.equals(Object)` reachable as runtime compiled method will mark too many equals implementations reachable for runtime compilation in a native image.
* Methods annotated with `@Fallback`  of the Truffle specialization DSL now support `@Cached`, `@CachedContext`, `@CachedLanguage`, `@Bind` and dispatched `@CachedLibrary` parameters.
* Deprecated and added methods to support expected arity ranges in `ArityException` instances. Note that the replacement methods now include more strict validations.
* `DebugValue` methods `hashCode()` and `equals()` provide result of the interop `identityHashCode` and `isIdentical` calls on the corresponding guest objects, respectively.
* Enabled by default the traversing compilation queue with dynamic thresholds, see `--engine.TraversingCompilationQueue`, `--engine.DynamicCompilationThresholds`, `--engine.DynamicCompilerThresholdsMinScale`, `--engine.DynamicCompilerThresholdsMinNormalLoad` and `--engine.DynamicCompilerThresholdsMaxNormalLoad`.
* Added `LoopConditionProfile#create()` as an alias of `createCountingProfile()` so it can be used like `@Cached LoopConditionProfile loopProfile`.
* Enabled by default the traversing compilation queue with dynamic thresholds. See [the documentation](https://github.com/oracle/graal/blob/master/truffle/docs/TraversingCompilationQueue.md) for more information.
* Changed behavior of parameterized `Function<Object, Object>` conversion such that an `Object[]` argument is passed through to the guest function as a single array argument. Both raw `Function` and `Function<Object[], Object>` treat an `Object[]` as an array of arguments, like before.
* Added `TruffleContext.pause()` and `TruffleContext.resume(Future<Void>)` to pause and resume execution for a truffle context, respectively.
* Added `DebuggerSession.createPrimitiveValue()` to create a `DebugValue` from a primitive value. Use it instead of `DebugValue.set(primitiveValue)` which is now deprecated.
* Added support for iterators and hash maps to `DebugValue`. The added methods wraps the respective methods of `InteropLibrary`.
* Added support for Truffle libraries to be prepared for AOT. See `ExportLibrary.useForAOT` or the `AOTTutorial` java class for further details.
* The Specialization DSL now generates code to throw an `AssertionError` if a `@Shared` and `@Cached` parameter returns a non-null value and is used in a guard. The `null` state is reserved for the uninitialized state.
* Changed `TruffleLanguage.disposeContext`. In case the underlying polyglot context is being cancelled, `TruffleLanguage.disposeContext` is called even if `TruffleLanguage.finalizeContext` throws a TruffleException or a ThreadDeath exception.

## Version 21.1.0
* Added methods into `Instrumenter` that create bindings to be attached later on. Added `EventBinding.attach()` method.
* Added `TruffleContext.isCancelling()` to check whether a truffle context is being cancelled.
* Added `TruffleInstrument.Env.calculateContextHeapSize(TruffleContext, long, AtomicBoolean)` to calculate the heap size retained by a a context.
* Added `ContextsListener.onLanguageContextCreate`, `ContextsListener.onLanguageContextCreateFailed`, `ContextsListener.onLanguageContextInitialize`, and `ContextsListener.onLanguageContextInitializeFailed`  to allow instruments to listen to language context creation start events, language context creation failure events, language context initialization start events, and language context initialization failure events, respectively.
* Added `CompilerDirectives.isExact(Object, Class)` to check whether a value is of an exact type. This method should be used instead of the `value != null && value.getClass() == exactClass` pattern.
* Added `Frame.clear(FrameSlot)`. This allows the compiler to reason about the liveness of local variables. Languages are recommended to use it when applicable.
* Added `@GenerateAOT` to support preparation for AOT specializing nodes. Read the [AOT tutorial](https://github.com/oracle/graal/blob/master/truffle/docs/AOT.md) to get started with Truffle and AOT compilation.
* Profiles now can be disabled using `Profile.disable()` and reset using `Profile.reset()`.
* Added `--engine.CompileAOTOnCreate` option to trigger AOT compilation on call target create.
* Added new messages to `InteropLibrary` for interacting with buffer-like objects:
    * Added `hasBufferElements(Object)` that returns  `true` if this object supports buffer messages.
    * Added `isBufferWritable(Object)` that returns `true` if this object supports writing buffer elements.
    * Added `getBufferSize(Object)` to return the size of this buffer.
    * Added `readBufferByte(Object, long)`, `readBufferShort(Object, ByteOrder, long)`, `readBufferInt(Object, ByteOrder, long)`, `readBufferLong(Object, ByteOrder, long)`, `readBufferFloat(Object, ByteOrder, long)`  and `readBufferDouble(Object, ByteOrder, long)` to read a primitive from this buffer at the given index.
    * Added `writeBufferByte(Object, long, byte)`, `writeBufferShort(Object, ByteOrder, long, short)`, `writeBufferInt(Object, ByteOrder, long, int)`, `writeBufferLong(Object, ByteOrder, long, long)`, `writeBufferFloat(Object, ByteOrder, long, float)`  and `writeBufferDouble(Object, ByteOrder, long, double)` to write a primitive in this buffer at the given index (supported only if `isBufferWritable(Object)` returns `true`).
* Added `Shape.getLayoutClass()` as a replacement for `Shape.getLayout().getType()`. Returns the DynamicObject subclass provided to `Shape.Builder.layout`.
* Changed the default value of `--engine.MultiTier` from `false` to `true`. This should significantly improve the warmup time of Truffle interpreters.
* The native image build fails if a method known as not suitable for partial evaluation is reachable for runtime compilation. The check can be disabled by the `-H:-TruffleCheckBlackListedMethods` native image option.
* Added `ExactMath.truncate(float)` and `ExactMath.truncate(double)` methods to remove the decimal part (round toward zero) of a float or of a double respectively. These methods are intrinsified.
* Added `SuspendedEvent.prepareUnwindFrame(DebugStackFrame, Object)` to support forced early return values from a debugger.
* Added `DebugScope.convertRawValue(Class<? extends TruffleLanguage<?>>, Object)` to enable wrapping a raw guest language object into a DebugValue.
* Added new messages to the `InteropLibrary` to support iterables and iterators:
	* Added `hasIterator(Object)` that allows to specify that the receiver is an iterable.
    * Added `getIterator(Object)` to return the iterator for an iterable receiver.
    * Added `isIterator(Object)` that allows to specify that the receiver is an iterator.
    * Added `hasIteratorNextElement(Object)`  that allows to specify that the iterator receiver has element(s) to return by calling the `getIteratorNextElement(Object)` method.
    * Added `getIteratorNextElement(Object)` to return the current iterator element.
* Added `TruffleContext.leaveAndEnter(Node, Supplier)` to wait for another thread without triggering multithreading.
* Removed deprecated `TruffleLanguage.Env.getTruffleFile(String)`, `TruffleLanguage.Env.getTruffleFile(URI)` methods.
* Deprecated CompilationThreshold for prefered LastTierCompilationThreshold and SingleTierCompilationThreshold.
* Added new features to the DSL `@NodeChild` annotation:
    * Added `implicit` and `implicitCreate` attributes to allow implicit creation of child nodes by the parent factory method.
    * Added `allowUncached` and `uncached` attributes to allow using `@NodeChild` with `@GenerateUncached`.
* Added `TruffleLanguage.Env#getTruffleFileInternal(String, Predicate<TruffleFile>)` and `TruffleLanguage.Env#getTruffleFileInternal(URI, Predicate<TruffleFile>)` methods performing the guest language standard libraries check using a supplied predicate. These methods have a better performance compared to the `TruffleLanguage.Env#getInternalTruffleFile(String)` and `TruffleLanguage.Env#getInternalTruffleFile(URI)` as the guest language standard libraries check is performed only for files in the language home when IO is not enabled by the Context.
* Added `TruffleLanguage.Env.getLogger(String)` and `TruffleLanguage.Env.getLogger(Class<?>)` creating a context-bound logger. The returned `TruffleLogger` always uses a logging handler and options from Env's context and does not depend on being entered on any thread.
* Added new messages to the `InteropLibrary` to support hash maps:
	* Added `hasHashEntries(Object)` that allows to specify that the receiver provides hash entries.
	* Added `getHashSize(Object)` to return hash entries count.
	* Added `isHashEntryReadable(Object, Object)` that allows to specify that mapping for the given key exists and is readable.
	* Added `readHashValue(Object, Object)` to read the value for the specified key.
	* Added `readHashValueOrDefault(Object, Object, Object)` to read the value for the specified key or to return the default value when the mapping for the specified key does not exist.
	* Added `isHashEntryModifiable(Object, Object)` that allows to specify that mapping for the specified key exists and is writable.
	* Added `isHashEntryInsertable(Object, Object)` that allows to specify that mapping for the specified key does not exist and is writable.
	* Added `isHashEntryWritable(Object, Object)` that allows to specify that mapping is either modifiable or insertable.
	* Added `writeHashEntry(Object, Object, Object)` associating the specified value with the specified key.
	* Added `isHashEntryRemovable(Object, Object)` that allows to specify that mapping for the specified key exists and is removable.
	* Added `removeHashEntry(Object, Object)` removing the mapping for a given key.
	* Added `isHashEntryExisting(Object, Object)` that allows to specify that that mapping for a given key is existing.
	* Added `getHashEntriesIterator(Object)` to return the hash entries iterator.
    * Added `getHashKeysIterator(Object)` to return the hash keys iterator.
    * Added `getHashValuesIterator(Object)` to return the hash values iterator.
* Added `TypeDescriptor.HASH` and `TypeDescriptor.hash(TypeDescriptor, TypeDescriptor)` representing hash map types in the TCK.
* Added support for Truffle safepoints and thread local actions. See `TruffleSafepoint` and `ThreadLocalAction`. There is also a [tutorial](https://github.com/oracle/graal/blob/master/truffle/docs/Safepoints.md) that explains how to adopt and use in language or tool implementations.
* Make the Truffle NFI more modular.
    * Provide option `--language:nfi=none` for disabling native access via the Truffle NFI in native-image even if the NFI is included in the image (e.g. as dependency of another language).
    * Moved `trufflenfi.h` header from the JDK root include directory into the NFI language home (`languages/nfi/include`).

## Version 21.0.0
* If an `AbstractTruffleException` is thrown from the `ContextLocalFactory`, `ContextThreadLocalFactory` or event listener, which is called during the context enter, the exception interop messages are executed without a context being entered. The event listeners called during the context enter are:
    * `ThreadsActivationListener.onEnterThread(TruffleContext)`
    * `ThreadsListener.onThreadInitialized(TruffleContext, Thread)`
    * `TruffleInstrument.onCreate(Env)`
    * `TruffleLanguage.isThreadAccessAllowed(Thread, boolean)`
    * `TruffleLanguage.initializeMultiThreading(Object)`
    * `TruffleLanguage.initializeThread(Object, Thread)`
* Added `HostCompilerDirectives` for directives that guide the host compilations of Truffle interpreters.
    * `HostCompilerDirectives.BytecodeInterpreterSwitch` - to denote methods that contain the instruction-dispatch switch in bytecode interpreters
    * `HostCompilerDirectives.BytecodeInterpreterSwitchBoundary` - to denote methods that do not need to be inlined into the bytecode interpreter switch
* Truffle DSL generated nodes are no longer limited to 64 state bits. Use these state bits responsibly.
* Added support for explicitly selecting a host method overload using the signature in the form of comma-separated fully qualified parameter type names enclosed by parentheses (e.g. `methodName(f.q.TypeName,java.lang.String,int,int[])`).
* Changed the default value of `--engine.MultiTier` from `false` to `true`. This should significantly improve the warmup time of Truffle interpreters.
* Deprecated and added methods to support expected arity ranges in `ArityException` instances. Note that the replacement methods now include more strict validations.


## Version 20.3.0
* Added `RepeatingNode.initialLoopStatus` and `RepeatingNode.shouldContinue` to allow defining a custom loop continuation condition.
* Added new specialization utility to print detailed statistics about specialization instances and execution count. See [Specialization Statistics Tutorial](https://github.com/oracle/graal/blob/master/truffle/docs/SpecializationHistogram.md) for details on how to use it.
* Added new specialization compilation mode that ignores "fast path" specializations and generates calls only to "slow path" specializations. This mode is intended for testing purposes to increase tests coverage. See [Specialization testing documentation](https://github.com/oracle/graal/blob/master/truffle/docs/SpecializationTesting.md) for more details.
* Added [TruffleFile.readSymbolicLink](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleFile.html#readSymbolicLink--) method to read the symbolic link target.
* Added [ReportPolymorphism.Megamorphic](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/dsl/ReportPolymorphism.Megamorphic.html) annotation for expressing the "report only megamorphic specializations" use case when reporting polymorphism.
* Added new flags to inspect expansion during partial evaluation: `--engine.TraceMethodExpansion=truffleTier`, `--engine.TraceNodeExpansion=truffleTier`, `--engine.MethodExpansionStatistics=truffleTier` and `--engine.NodeExpansionStatistics=truffleTier`. Language implementations are encouraged to run with these flags enabled and investigate their output for unexpected results. See [Optimizing.md](https://github.com/oracle/graal/blob/master/truffle/docs/Optimizing.md) for details.
* Enabled by default the elastic allocation of Truffle compiler threads depending on the number of available processors, in both JVM and native modes. The old behavior, 1 or 2 compiler threads, can be explicitly enabled with `--engine.CompilerThreads=0`.
* Added `ThreadsActivationListener` to listen to thread enter and leave events in instruments.
* Added `TruffleInstrument.Env.getOptions(TruffleContext)` to retrieve context specific options for an instrument and `TruffleInstrument.getContextOptions()` to describe them. This is useful if an instrument wants to be configured per context. 
* Added `TruffleContext.isClosed()` to check whether a  truffle context is already closed. This is useful for instruments.
* Added `TruffleContext.closeCancelled` and `TruffleContext.closeResourceExhausted`  to allow instruments and language that create inner contexts to cancel the execution of a context.
* Added `TruffleContext.isActive` in addition to `TruffleContext.isEntered` and improved their documentation to indicate the difference.
* Added `ContextsListener.onContextResetLimit` to allow instruments to listen to context limit reset events from the polyglot API.
* All instances of `TruffleContext` accessible from instruments can now be closed by the instrument. Previously this was only possible for creators of the TruffleContext instance.
* Added the ability to create context and context thread locals in languages and instruments. See [ContextLocal](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/ContextLocal.html) and [ContextThreadLocal](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/ContextThreadLocal.html) for details.
* Removed the hard "maximum node count" splitting limit controlled by `TruffleSplittingMaxNumberOfSplitNodes` as well as the option itself.
* Removed polymorphism reporting from `DynamicObjectLibrary`. If the language wants to report polymorphism for a property access, it should do so manually using a cached specialization.
* The `iterations` for `LoopNode.reportLoopCount(source, iterations)` must now be >= 0.
* Added [NodeLibrary](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/NodeLibrary.html), which provides guest language information associated with a particular Node location, local scope mainly and [TruffleLanguage.getScope](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html#getScope-C-) and [TruffleInstrument.Env.getScope](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/TruffleInstrument.Env.html#getScope-com.oracle.truffle.api.nodes.LanguageInfo-), which provides top scope object of a guest language.
* Deprecated com.oracle.truffle.api.Scope class and methods in TruffleLanguage and TruffleInstrument.Env, which provide the scope information through that class.
* Added scope information into InteropLibrary: [InteropLibrary.isScope](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/InteropLibrary.html#isScope-java.lang.Object-), [InteropLibrary.hasScopeParent](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/InteropLibrary.html#hasScopeParent-java.lang.Object-) and [InteropLibrary.getScopeParent](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/InteropLibrary.html#getScopeParent-java.lang.Object-)
* Added utility method to find an instrumentable parent node [InstrumentableNode.findInstrumentableParent](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/InstrumentableNode.html#findInstrumentableParent-com.oracle.truffle.api.nodes.Node-).
* Deprecated `DebugScope.getArguments()` without replacement. This API was added without use-case.
* Added the [RootNode.isTrivial](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/RootNode.html#isTrivial) method, for specifying root nodes that are always more efficient to inline than not to.
* Added [ByteArraySupport](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/memory/ByteArraySupport.html): a helper class providing safe multi-byte primitive type accesses from byte arrays.
* Added a new base class for Truffle exceptions, see [AbstractTruffleException](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/exception/AbstractTruffleException.html). The original `TruffleException` has been deprecated. Added new interop messages for exception handling replacing the deprecated `TruffleException` methods.
* Added new messages to `InteropLibrary` related to exception handling:
    * Added `getExceptionType(Object)` that allows to specify the type of an exception, e.g. PARSE_ERROR. 
    * Added `isExceptionIncompleteSource(Object)` allows to specify whether the parse error contained unclosed brackets.
    * Added `getExceptionExitStatus(Object)` allows to specify the exit status of an exception of type EXIT.
    * Added `hasExceptionCause(Object)` and `getExceptionCause(Object)` to return the cause of this error
    * Added `hasExceptionStackTrace(Object)` and `getExceptionStackTrace(Object)` to return the guest stack this of this error. 
    * Added `hasExceptionMessage(Object)` and `getExceptionMessage(Object)` to provide an error message of the error.
    * Added `hasExecutableName(Object)` and `getExecutableName(Object)` to provide a method name similar to what was provided in `RootNode.getName()` but for executable objects.
    * Added `hasDeclaringMetaObject(Object)` and `getDeclaringMetaObject(Object)` to provide the meta object of the function. 
* Language implementations are recommended to perform the following steps to upgrade their exception implementation:
    * Convert non-internal guest language exceptions to `AbstractTruffleException`, internal errors should be refactored to no longer implement `TruffleException`.
    * Export new interop messages directly on the `AbstractTruffleException` subclass if necessary. Consider exporting `getExceptionType(Object)`, `getExceptionExitStatus(Object)` and `isExceptionIncompleteSource(Object)`. For other interop messages the default implementation should be sufficient for most use-cases. Consider using `@ExportLibrary(delegateTo=...)` to forward to a guest object stored inside of the exception.
    * Rewrite interop capable guest language try-catch nodes to the new interop pattern for handling exceptions. See `InteropLibrary#isException(Object)` for more information. 
    * Implement the new method `RootNode.translateStackTraceElement` which allows guest languages to transform stack trace elements to accessible guest objects for other languages.
    * Consider making executable interop objects of the guest language implement `InteropLibrary.hasExecutableName(Object)` and `InteropLibrary.hasDeclaringMetaObject(Object)`.
    * Make exception printing in the guest language use `InteropLibrary.getExceptionMessage(Object)`, `InteropLibrary.getExceptionCause(Object)` and `InteropLibrary.getExceptionStackTrace(Object)` for foreign exceptions to print them in the style of the language.
    * Make all exports of `InteropLibrary.throwException(Object)` throw an instance of `AbstractTruffleException`. This contract will be enforced in future versions when `TruffleException` will be removed.
    * Attention: Since [AbstractTruffleException](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/exception/AbstractTruffleException.html) is an abstract base class, not an interface, the exceptions the Truffle NFI throws do not extend UnsatisfiedLinkError anymore. This is an incompatible change for guest languages that relied on the exact exception class. The recommended fix is to catch AbstractTruffleException instead of UnsatisfiedLinkError.
* Added [TruffleInstrument.Env.getEnteredContext](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/TruffleInstrument.Env.html#getEnteredContext--) returning the entered `TruffleContext`.
* Added [DebuggerSession.setShowHostStackFrames](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/DebuggerSession.html#setShowHostStackFrames-boolean-) and host `DebugStackFrame` and `DebugStackTraceElement`. This is useful for debugging of applications that use host interop.
* All Truffle Graal runtime options (-Dgraal.) which were deprecated in GraalVM 20.1 are removed. The Truffle runtime options are no longer specified as Graal options (-Dgraal.). The Graal options must be replaced by corresponding engine options specified using [polyglot API](https://www.graalvm.org/truffle/javadoc/org/graalvm/polyglot/Engine.Builder.html#option-java.lang.String-java.lang.String-).
* Deprecated the `com.oracle.truffle.api.object.dsl` API without replacement. The migration path is to use `DynamicObject` subclasses with the `com.oracle.truffle.api.object` API.
* A node parameter now needs to be provided to TruffleContext.enter() and TruffleContext.leave(Object). The overloads without node parameter are deprecated. This is useful to allow the runtime to compile the enter and leave code better if a node is passed as argument. 
* Added [DebuggerSession.suspendHere](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/DebuggerSession.html#suspendHere-com.oracle.truffle.api.nodes.Node-) to suspend immediately at the current location of the current execution thread.
* Added [RootNode.prepareForAOT](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/RootNode.html#prepareForAOT) that allows to initialize root nodes for compilation that were not yet executed.
* Removed deprecation for `RootNode.getLanguage(Class<?>)`, it is still useful to efficiently access the associated language of a root node.
* Block node partial compilation is no longer eagerly triggered but only when the `--engine.MaximumGraalNodeCount` limit was reached once for a call target.
* Lifted the restriction that the dynamic type of a `DynamicObject` needs to be an instance of `ObjectType`, allowing any non-null object. Deprecated `Shape.getObjectType()` that has been replaced by `Shape.getDynamicType()`.
* Added [TruffleLanguage.Env.createHostAdapterClass](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.Env.html#createHostAdapterClass-java.lang.Class:A-) to allow extending a host class and/or interfaces with a guest object via a generated host adapter class (JVM only).
* Deprecated the old truffle-node-count based inlining heuristic and related options (namely InliningNodeBudget and LanguageAgnosticInlining).
* Added `@GenerateLibrary.pushEncapsulatingNode()` that allows to configure whether encapsulating nodes are pushed or popped.

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
