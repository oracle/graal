# Graal SDK Changelog

This changelog summarizes major changes between Graal SDK versions. The main focus is on APIs exported by Graal SDK.

## Version 1.0.0 RC13
* [OptionCategory.DEBUG](https://www.graalvm.org/truffle/javadoc/org/graalvm/options/OptionCategory.html) has been renamed to `OptionCategory.INTERNAL` for clarity.

## Version 1.0 RC11
* Added [SourceSection.hasLines()](http://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/SourceSection.html#hasLines--), [SourceSection.hasColumns()](http://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/SourceSection.html#hasColumns--) and [SourceSection.hasCharIndex()](http://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/SourceSection.html#hasCharIndex--) to distinguish which positions are defined and which are not.
* Added [FileSystem.getSeparator()](http://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/io/FileSystem.html#getSeparator--) to remove a dependency on NIO `FileSystem` for custom `Path` implementations.
* Added support for automatic string to primitive type conversion using the [Value API](http://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Value.html#as-java.lang.Class-).

## Version 1.0 RC10
* Added [FileSystem.setCurrentWorkingDirectory](http://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/io/FileSystem.html#setCurrentWorkingDirectory-java.nio.file.Path-) method to set a current working directory for relative paths resolution in the polyglot FileSystem.

## Version 1.0 RC9
* Added a [Context.Builder.logHandler](http://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.Builder.html#logHandler-java.io.OutputStream-) and [Engine.Builder.logHandler](http://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Engine.Builder.html#logHandler-java.io.OutputStream-) methods to install a logging handler writing into a given `OutputStream`
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
