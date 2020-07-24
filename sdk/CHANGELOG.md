# GraalVM SDK Changelog

This changelog summarizes major changes between GraalVM SDK versions. The main focus is on APIs exported by GraalVM SDK.

## Version 20.2.0
* Added `-Dpolyglot.engine.AllowExperimentalOptions=true` to allow experimental options for all polyglot engines of a host VM. This system property is intended to be used for testing only and should not be enabled in production environments.
* Added [a factory method](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/io/FileSystem.html#newDefaultFileSystem--) creating a FileSystem based on the host Java NIO. The obtained instance can be used as a delegate in a decorating filesystem.
* Added `PolyglotException.isResourceExhausted()` to determine if an error was caused by a resource limit (e.g. OutOfMemoryError) that was exceeded.
* Added `Context.parse(Source)` to parse but not evaluate a source. Parsing a source allows to trigger e.g. syntax validation prior to executing the code.
* Added optional [FileSystem.isSameFile](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/io/FileSystem.html#isSameFile-java.nio.file.Path-java.nio.file.Path-java.nio.file.LinkOption...-) method testing if the given paths refer to the same physical file. The method can be overridden by the `FileSystem` implementer with a more efficient test.
* Added `EconomicMap.putIfAbsent(K, V)` to associate a value with the specified key if not already present in the map.

## Version 20.1.0
* The `PerformanceWarningsAreFatal` and `TracePerformanceWarnings` engine options take a comma separated list of performance warning types. Allowed warning types are `call` to enable virtual call warnings, `instanceof` to enable virtual instance of warnings and `store` to enables virtual store warnings. There are also `all` and `none` types to enable (disable) all performance warnings.
* The `<language-id>.home` system property that can be used in some development scenarios to specify a language's directory is deprecated. The `org.graalvm.language.<language-uid>.home` property should be used instead. Setting this new system property is reflected by the `HomeFinder` API.
* Added `CompilationFailureAction` engine option which deprecates `CompilationExceptionsArePrinted `, `CompilationExceptionsAreThrown`, `CompilationExceptionsAreFatal` and `PerformanceWarningsAreFatal` options.
* Added `TreatPerformanceWarningsAsErrors` engine option which deprecates the `PerformanceWarningsAreFatal` option. To replace the `PerformanceWarningsAreFatal` option use the `TreatPerformanceWarningsAsErrors` with `CompilationFailureAction` set to `ExitVM`.
* Added `bailout` into performance warning kinds used by `TracePerformanceWarnings`, `PerformanceWarningsAreFatal` and `CompilationExceptionsAreFatal` options.
* Added [OptionDescriptor.getDeprecationMessage](https://www.graalvm.org/sdk/javadoc/org/graalvm/options/OptionDescriptor.html#getDeprecationMessage--) returning the option deprecation reason. Added [OptionDescriptor.Builder.deprecationMessage()](https://www.graalvm.org/sdk/javadoc/org/graalvm/options/OptionDescriptor.Builder.html#deprecationMessage-java.lang.String-) to set the option deprecation reason.
* Added `Value.isMetaObject()`, `Value.getMetaQualifiedName()`, `Value.getMetaSimpleName()` and `Value.isMetaInstance(Object)` to allow language agnostic access to meta-objects like classes or types.  
* The result of `Value.getMetaObject()` will now return always [meta-objects](Value.isMetaObject). It is recommended but not required to change uses of meta-objects to use `Value.getMetaQualifiedName()` instead of `Value.toString()` to return a type name. 
* Added [Context.Builder.hostClassLoader](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.Builder.html#hostClassLoader-java.lang.ClassLoader-) to allow an embedder to specify a context ClassLoader for code execution.


## Version 20.0.0
* The deprecated `graalvm.home` and `graalvm.version` system properties have been removed, use the [HomeFinder](https://www.graalvm.org/sdk/javadoc/org/graalvm/home/HomeFinder.html) instead.
* Added `EventContext.createError` which allows to introduce guest application errors in execution listeners/nodes.
* Deprecated `Instrumenter.attachExecutionEventListener` and `ExecutionEventListener.onInputValue` as explicit input filters are not supported by event listeners. Use ExecutionEventNodes instead.
* Added [Context.Builder.currentWorkingDirectory](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.Builder.html#currentWorkingDirectory-java.nio.file.Path-) to set the current working directory used by the guest application to resolve relative paths.
* The algorithm used to generate a unique [URI](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Source.html#getURI--) for a `Source` built without an `URI` was changed to SHA-256.
* All Truffle Graal runtime options (-Dgraal.) will be deprecated with 20.1. The Truffle runtime options are no longer specified as Graal options (-Dgraal.). The Graal options must be replaced by corresponding engine options specified using [polyglot API](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Engine.Builder.html#option-java.lang.String-java.lang.String-). The `TRUFFLE_STRICT_OPTION_DEPRECATION` environment variable can be used to detect usages of deprecated Graal options. When the `TRUFFLE_STRICT_OPTION_DEPRECATION` is set to `true` and the deprecated Graal option is used the engine throws a `PolyglotException` listing the used deprecated options and corresponding replacements.


## Version 19.3.0
* The default temporary directory can be configured by [FileSystem](http://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/io/FileSystem.html#getTempDirectory--).
* Added `org.graalvm.polyglot.ResourceLimits` that allows to specify context specific time and statement count execution limits.
* Added [HomeFinder](http://www.graalvm.org/sdk/javadoc/org/graalvm/home/HomeFinder.html), a utility class to find various paths of the running GraalVM.
* Contexts can now be closed if they are still explicitly entered using `Context.enter` on the current thread. This allows for simpler error recovery code.
* Added `Value.getContext()` to access the context a value is associated with.
* Added `org.graalvm.home.Version` version utility that allows to create, validate and compare GraalVM versions.
* Added Value API methods for interacting with exception objects: [Value#isException](http://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Value.html#isException--) and [Value#throwException](http://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Value.html#throwException--).
* Added target type mapping from exception objects to [PolyglotException](http://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Value.html#as-java.lang.Class-).

## Version 19.2.0
* Added support for date, time, timezone and duration values in polyglot
	* Added methods to identify polyglot date, time, timezone and duration values in `Value`. See `Value.isDate`, `Value.isTime`, `Value.isTimeZone`, `Value.isDuration`. 
	* Polyglot languages now interpret the `java.time` host values of type `LocalDate`, `LocalTime`, `LocalDateTime`, `ZonedDateTime`, `Instant`, `ZoneId` and `Duration`. They are mapped to the appropriate guest language types.
	* Added `ProxyDate`, `ProxyTime`, `ProxyTimeZone`, `ProxyInstant` and `ProxyDuration` to proxy date time and duration related guest values.
* Added `Context.Builder.timeZone(ZoneId)` to configure the default timezone of polyglot contexts.
* Added [OptionKey.mapOf](https://www.graalvm.org/truffle/javadoc/org/graalvm/options/OptionKey.html#mapOf) to group/accumulate key=value pairs for options whose keys are not known beforehand e.g. user-defined properties.
* Added ability to configure custom polyglot access configuration with `PolyglotAccess.newBuilder()`. It allows to configure fine-grained access control for polyglot bindings and between polyglot languages.

## Version 19.1.0
* Restricting guest languages from sub-process creation by [Context.Builder.allowCreateProcess](http://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.Builder.html#allowCreateProcess-boolean-). Use `Context.newBuilder().allowCreateProcess(true)` to allow guest languages to create a new sub-process.
* Added a possibility to control sub-process creation using a custom [ProcessHandler](http://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/io/ProcessHandler.html) implementation. Use `Context.newBuilder().processHandler(handler)` to install a custom `ProcessHandler`.
* Restricting access to the host environment variables via [EnvironmentAccess](http://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/EnvironmentAccess.html) configurations. Use `EnvironmentAccess.INHERIT` to allow guest languages to read process environment variables.
* Deprecated `OptionValues#set`, [OptionValues](https://www.graalvm.org/sdk/javadoc/org/graalvm/options/OptionValues.html) should be read-only. If the value needs to be changed, it can be stored in the language or instrument and read from there.
* Removed deprecated `OptionCategory.DEBUG` (use `OptionCategory.INTERNAL` instead).
* The path separator can now be configured by [FileSystem](http://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/io/FileSystem.html#getPathSeparator--).

## Version 19.0.0
* `Value.as(Interface.class)` now requires interface classes to be annotated with `HostAccess.Implementable` in `EXPLICIT` host access mode. Added new APIs to configure implementable behavior in HostAccess.

## Version 1.0.0 RC16
* `--experimental-options` can now also be passed after polyglot options on the command line.
* `--version` changed default message to `IMPLEMENTATION-NAME (ENGINE-NAME GRAALVM-VERSION)`

## Version 1.0.0 RC15
* Renamed 'Graal SDK' to 'GraalVM SDK'
* Added optional [FileSystem.getMimeType](http://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/io/FileSystem.html#getMimeType-java.nio.file.Path-) and [FileSystem.getEncoding](http://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/io/FileSystem.html#getEncoding-java.nio.file.Path-) methods. These methods can be used by `FileSystem` implementer to provide file MIME type and encoding.
* Added a possibility to set an [encoding in Source builder](http://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Source.Builder.html#encoding-java.nio.charset.Charset-)
* (**incompatible change**) Restricting access to the host language via [HostAccess](http://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/HostAccess.html) configurations. Use `Context.newBuilder().allowHostAccess(HostAccess.ALL)` to get previous behavior. Configurations that use `allowAllAccess(true)` are not affected by this incompatible change.
* Deprecated `Context.Builder.hostClassFilter` and added the new method `Context.Builder.allowHostClassLookup` as a replacement. The name was changed for clarity and now also accepts `null` to indicate that no host class lookup is allowed.
* (**incompatible change**) Restricting polyglot access for guest languages via [PolyglotAccess](http://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/PolyglotAccess.html) configurations. Use `Context.newBuilder().allowPolyglotAccess(PolyglotAccess.ALL)` to get previous behavior. Configurations that use `allowAllAccess(true)` are not affected by this incompatible change.
* Removed deprecated API class `ProxyPrimitive`.
* Started adding several options under `--engine` like `--engine.TraceCompilation`, which can also be set on the `Engine`. These options will progressively replace the `-Dgraal.*Truffle*` properties. The list can be seen by passing `--help:expert` to any language launcher.
* Experimental options now require `--experimental-options` on the command line to be passed to GraalVM language launchers, or [Context.Builder#allowExperimentalOptions](http://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.Builder.html#allowExperimentalOptions-boolean-) and [Engine.Builder#allowExperimentalOptions](http://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Engine.Builder.html#allowExperimentalOptions-boolean-) to be set in other scenarios.
* Added new API for target type mappings using the new HostAccess API.
* (**incompatible change**) Removed default lossy string coercions. Previous behavior can be restored using the following [snippets](https://github.com/oracle/graal/tree/master/truffle/src/com.oracle.truffle.api.test/src/com/oracle/truffle/api/test/examples/TargetMappings.java).

## Version 1.0.0 RC14
* Added [Context.Builder#allowExperimentalOptions](http://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.Builder.html#allowExperimentalOptions-boolean-) to control whether experimental options can be passed to a Context.
* Added [Engine.Builder#allowExperimentalOptions](http://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Engine.Builder.html#allowExperimentalOptions-boolean-) to control whether experimental instrument and engine options can be passed.
* Removed deprecated API class `ProxyPrimitive`.
* Restricting access (**incompatible change**) to host interop via [HostAccess](http://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/HostAccess.html) configurations. Use `Context.newBuilder().allowHostAccess(HostAccess.PUBLIC)` to get previous behavior.
* Restricting access (**incompatible change**) to the host language via [HostAccess](http://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/HostAccessPolicy.html) configurations. Use `Context.newBuilder().allowHostAccess(HostAccess.ALL)` to get previous behavior. Configurations that use `allowAllAccess(true)` are not affected by this incompatible change.
* Deprecated `Context.Builder.hostClassFilter` and added the new method `Context.Builder.allowHostClassLookup` as a replacement. The name was changed change for clarity and now also allows `null` values to indicate that no host class lookup is allowed.
* Deprecated `defaultValue` of `OptionType`. Default value of `OptionKey` is sufficient.
* `--vm.*` should now be used instead of `--native.*` or `--jvm.*` to pass VM options in GraalVM language launchers (the old style of option is still supported but deprecated and shows warnings on stderr). `--native` and `--jvm` should still be used to select the VM mode.
* `--jvm.help` or `--native.help` are deprecated in favor of `--help:vm`.

## Version 1.0.0 RC13
* [OptionCategory.DEBUG](https://www.graalvm.org/truffle/javadoc/org/graalvm/options/OptionCategory.html) has been renamed to `OptionCategory.INTERNAL` for clarity.
* Added `"static"` member to class objects that provides access to the class's static members.
* [OptionStability](https://www.graalvm.org/truffle/javadoc/org/graalvm/options/OptionStability.html) has been added for specifying the stability of an option.

## Version 1.0 RC11
* Added [SourceSection.hasLines()](http://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/SourceSection.html#hasLines--), [SourceSection.hasColumns()](http://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/SourceSection.html#hasColumns--) and [SourceSection.hasCharIndex()](http://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/SourceSection.html#hasCharIndex--) to distinguish which positions are defined and which are not.
* Added [FileSystem.getSeparator()](http://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/io/FileSystem.html#getSeparator--) to remove a dependency on NIO `FileSystem` for custom `Path` implementations.
* Added support for automatic string to primitive type conversion using the [Value API](http://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Value.html#as-java.lang.Class-).

## Version 1.0 RC10
* Added [FileSystem.setCurrentWorkingDirectory](http://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/io/FileSystem.html#setCurrentWorkingDirectory-java.nio.file.Path-) method to set a current working directory for relative paths resolution in the polyglot FileSystem.

## Version 1.0 RC9
* Added a [Context.Builder.logHandler](http://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.Builder.html#logHandler-java.io.OutputStream-) and [Engine.Builder.logHandler](http://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Engine.Builder.html#logHandler-java.io.OutputStream-) methods to install a logging handler writing into a given `OutputStream`.
* `Value.asValue(Object)` now also works if no currently entered context is available.
* Primitives, host and `Proxy` values can now be shared between multiple context and engine instances. They no longer throw an `IllegalArgumentException` when shared. Primitive types are `Boolean`, `Byte`, `Short`, `Integer`, `Long`, `Float`, `Double`, `Character` and `String` of the `java.lang` package. Non primitive values originating from guest languages are not sharable.

## Version 1.0 RC8
* Added `MessageTransport` and `MessageEndpoint` to virtualize transport of messages to a peer URI.
* Added `Value.canInvokeMember()` and `Value.invokeMember()` to invoke a member of an object value.

## Version 1.0 RC7
* Graal SDK was relicensed from GPLv2 with CPE to Universal Permissive License (UPL).

## Version 1.0 RC6
* Added new `ByteSequence` utility to the IO package that is intended to be used as immutable byte sequence representation.
* Added support for byte based sources:
	* Byte based sources may be constructed using a `ByteSequence` or from a `File` or `URL`. Whether sources are interpreted as character or byte based sources depends on the specified language.
	* `Source.hasBytes()` and `Source.hasCharacters()` may be used to find out whether a source is character or byte based.
	* Byte based sources throw an `UnsupportedOperationException` if methods that access characters, line numbers or column numbers.
	* Added `Source.getBytes()` to access the contents of byte based sources.
* Added support for MIME types to sources:
	* MIME types can now be assigned using `Source.Builder.mimeType(String)` to sources in addition to the target language.
	* The MIME type of a source allows languages support different kinds of input.
	* `Language` instances allow access to the default and supported MIME types using `Language.getMimeTypes()` and `Language.getDefaultMimeType()`.
	* MIME types are automatically detected if the source is constructed from a `File` or `URL` if it is not specified explicitly.
	* Deprecated `Source.getInputStream()`. Use `Source.getCharacters()` or `Source.getBytes()` instead.
* Context methods now consistently throw `IllegalArgumentException` instead of `IllegalStateException` for unsupported sources or missing / inaccessible languages.
* Added `Engine.findHome()` to find the GraalVM home folder.

## Version 1.0 RC5
* `PolyglotException.getGuestObject()` now returns `null` to indicate that no exception object is available instead of returning a `Value` instance that returns `true` for `isNull()`.
* Added new [execution listener](http://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/management/ExecutionListener.html) API that allows for simple, efficient and fine grained introspection of executed code.

## Version 1.0 RC3

* Added support for [logging](http://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.Builder.html#logHandler-java.util.logging.Handler-) in Truffle languages and instruments.

## Version 1.0 RC2
* Added `Value.asValue(Object)` to convert a Java object into its value representation using the currently entered context.
* Added `Context.getCurrent()` to lookup the current context to allow Java methods called by a Graal guest language to evaluate additional code in the current context.
* Removed deprecated `Context.exportSymbol` and `Context.importSymbol`.
* Removed deprecated `Source.getCode`.
* The code cache for sources is now weak. Code can be garbage collected if a source is no longer referenced but the Context or Engine is still active.
* Added `Source.Builder.cached(boolean)` to configure caching behavior by source.

## Version 1.0 RC1
* Added Context.Builder#allowHostClassLoading to allow loading of new classes by the guest language.
* Added `Value.getSourceLocation()` to find a function `SourceSection`.

## Version 0.33
* Expose Runtime name as Engine#getImplementationName();
* Deprecate Context#exportSymbol, Context#importSymbol, Context#lookup use Context#getBindings, Context#getPolyglotBindings instead.
* Remove deprecated API Engine#getLanguage, Engine#getInstrument.
* Remove deprecated Language#isHost.
* Deprecate ProxyPrimitive without replacement.
* Added Context.Builder#allAccess that allows to declare that a context has all access by default, also for new access rights.

## Version 0.31

* Added Value#as(Class) and Value.as(TypeLiteral) to convert to Java types.
* Added Context#asValue(Object) to convert Java values back to the polyglot Value representation.
* Added Value#isProxyObject() and Value#asProxyObject().

## Version 0.29

* Introduced Context.enter() and Context.leave() that allows explicitly entering and leaving the context to improve performance of performing many simple operations.
* Introduced Value.executeVoid to allow execution of functions more efficiently if not return value is expected.


## Version 0.26

* Initial revision of the polyglot API introduced.
* Initial revision of the native image API introduced.
* Initial revision of the options API introduced.
