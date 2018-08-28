# Graal SDK Changelog

This changelog summarizes major changes between Graal SDK versions. The main focus is on APIs exported by Graal SDK.

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
