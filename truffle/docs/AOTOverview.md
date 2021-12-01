---
layout: docs
toc_group: truffle
link_title: Truffle AOT Overview
permalink: /graalvm-as-a-platform/language-implementation-framework/AOTOverview/
---
# Truffle AOT Overview

There are several different flavors of AOT preinitialization, compilation, and caching supported in Truffle.
This document is intended to provide an overview of these capabilities.

Note that some of the features mentioned here are only supported in GraalVM EE.

### Preinitialization of the First Context

Native image allows running Java code in static initializers at image build time.
After static initialization was run, values referenced from static fields are snapshotted and persisted in the image.
Context preinitialization leverages this feature by creating and initializing a language context at image build time to be used by the first context that gets created in an isolate or process at runtime.
This typically improves the initialization time of the first context significantly.

Context preinitialization can be enabled by setting the system property `-Dpolyglot.image-build-time.PreinitializeContexts=ruby,llvm` at image build time.
A language needs to implement [TruffleLanguage.patchContext](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html#patchContext-C-com.oracle.truffle.api.TruffleLanguage.Env-) and return true to support context preinitialization.
In addition, languages need to be careful not to bind any host-specific data or create objects that would not be allowed to be stored in a native image, like java.lang.Thread instances.

For more information see [TruffleLanguage.patchContext](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html#patchContext-C-com.oracle.truffle.api.TruffleLanguage.Env-) javadoc.


### Code sharing within the same Isolate/Process

A polyglot engine can be used in order to determine the scope of code sharing between contexts.
An example of how that can be done can be found in the [reference manual](../../docs/reference-manual/embedding/embed-languages.md#code-caching-across-multiple-contexts).
When a language is initialized for a polyglot context, a new language instance is requested from an engine.
If the language supports [ContextPolicy.SHARED](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.ContextPolicy.html#SHARED), then the language instance will be reused for an engine instance.
The source parsing cache is associated with a language instance, so parsing happens once per language instance.
Languages may choose to disallow reuse of a language instance for a new additional context by implementing [TruffleLanguage.areOptionsCompatible](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html#areOptionsCompatible-org.graalvm.options.OptionValues-org.graalvm.options.OptionValues-).
This allows languages to assume specific context options to be compilation final for all root nodes created by the language.
An exception from this rule is `InteropLibrary`, where nodes may be shared unconditionally between languages instances.


### Supporting Context Independent Code

Codesharing requires that all code data structures are independent of their context.
For example, code is context-independent if it can be executed with one context and then executed again with a new context without deoptimizing the code.
A good test to verify a language implementation's context independence is to create a context with an explicit engine, run a test application, and then verify that the second context does not cause deoptimizations when running the same deterministic application.

The Truffle framework announces the potential use of a language instance in multiple contexts by calling [TruffleLanguage.initializeMultipleContexts](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html#initializeMultipleContexts--), typically even before the first context is created.
The framework is able to initialize multiple contexts before the first context is created when an explicit engine is used or `--engine.CacheStore` is set to `true`.

The following criteria should be satisfied when supporting context independent code:

* All speculation on runtime value identity must be disabled with multiple contexts initialized, as they will lead to a guaranteed deoptimization when used with the second context.
* Function inline caches should be modified and implemented as a two-level inline cache. The first level speculates on the function instance's identity and the second level on the underlying CallTarget instance. The first level cache must be disabled if multiple contexts are initialized, as this would unnecessarily cause deoptimization.
* The DynamicObject root Shape instance should be stored in the language instance instead of the language context. Otherwise, any inline cache on shapes will not stabilize and ultimately end up in the generic state.
* All Node implementations must not store context-dependent data structures or context-dependent runtime values.
* Loading and parsing of sources, even with language-internal builtins, should be performed using [TruffleLanguage.Env.parse](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html#parse-com.oracle.truffle.api.TruffleLanguage.ParsingRequest-) to cache Source parsing per language instance.
* All assumption instances should be stored in the language instance instead of the context. With multiple contexts initialized, the context instance read using context references may no longer be a constant. In this case any assumption read from the context would not be folded and they would cause significant runtime performance overhead. Assumptions from the language can be folded by the compiler in both single and multiple context mode.

It is expected that an AST created for multiple contexts is compiled to less efficient machine code as it does not allow for speculation on the identity of runtime values.
For example, instead of speculating on the function instance in an inline cache, it is necessary to speculate on the contained CallTarget.
This is slower because it requires an additional read to access the CallTarget stored in the function.
It may be costly to create context independent code, therefore, speculation on runtime values should still be performed if multiple contexts are not initialized.

[SimpleLanguage](https://github.com/graalvm/simplelanguage/blob/master/language/src/main/java/com/oracle/truffle/sl/SLLanguage.java#L196) and [JavaScript](https://github.com/oracle/graaljs/blob/master/graal-js/src/com.oracle.truffle.js/src/com/oracle/truffle/js/lang/JavaScriptLanguage.java) are two languages that already support context independent code and might be useful as a guidance on concrete problems.


### Persistent Context Independent Code with Auxiliary Engine Caching (EE)

GraalVM Enterprise Edition supports persisting code data structures to disk.
This enables to almost eliminate warmup time for the first run of an application in an isolate/process.
The SVM auxiliary image feature is used to persist and load the necessary data structures to the disk.
Persisting the image can take a significant amount of time as compilation needs to be performed.
However, loading is designed to be as fast as possible, typically almost instantaneous.

Engine caching is enabled using options and functional even if the context was created without an explicit engine.

More information on engine caching can be found in the engine caching [tutorial](AuxiliaryEngineCachingEnterprise.md).


### Compilation without Profiling

By default, if language functions are created but never executed, they are not compiled when they are stored in an auxiliary engine cache image.
Auxiliary engine caching supports triggering compilation for root nodes that were loaded but never executed.
In such a case the framework calls the [RootNode.prepareForAOT](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/RootNode.html#prepareForAOT--) method.

More information on making a language implementation ready for compilation without prior execution can be found in the [AOT tutorial](AOT.md).
Note that not every language can be compiled without prior execution and produce efficient machine code.
Statically typed languages are typically more suitable for this.


### Application Snapshotting

It is planned to also support persisting runtime values of polyglot context instances to disk.
More information will appear here as soon as this feature is implemented.
