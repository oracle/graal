# GraalVM SDK Changelog

This changelog summarizes major changes between GraalVM SDK versions. The main focus is on APIs exported by GraalVM SDK.

## Version 23.1.5
* (GR-54674) Added the options `engine.TraceSourceCache` and `engine.TraceSourceCacheDetails` to allow tracing of source cache hits, misses, failures and evictions.

## Version 23.1.3
* (GR-50682) The Truffle languages and instrument implementations are now loaded exclusively using the context class loader if it is set and Truffle is found there. If the context class loader is not set or Truffle is not found, then the system class loader is used instead. Previously, the context and system class loader were used to load Truffle languages and instruments which causes issues if the context class loader does not delegate to the system class loader and classes are loaded from both. Context class loaders that do not delegate to the system class loader are commonly used to implement hot-reload functionality.

## Version 23.1.0
* (GR-43819) The GraalVM SDK was split into several more fine-grained modules. The use of the graalvm-sdk module is now deprecated. Please update your Maven and module dependencies accordingly. Note that all APIs remain compatible. The following new modules are available:
	* `org.graalvm.nativeimage` A framework that allows to customize native image generation.
	* `org.graalvm.polyglot´: A framework that allows to embed polyglot language implementations in Java.
	* `org.graalvm.word`: A low-level framework for machine-word-sized values in Java.
	* `org.graalvm.collections`: A collections framework for GraalVM components.
	Old Maven configuration:
    ```xml
    <dependency>
      <groupId>org.graalvm.sdk</groupId>
      <artifactId>graal-sdk</artifactId>
      <version>${graalvm.version}</version>
    </dependency>
    ```
    New Maven configuration:
    ```xml
    <dependency>
      <groupId>org.graalvm.sdk</groupId>
      <artifactId>nativeimage</artifactId>
      <version>${graalvm.version}</version>
    </dependency>
    <dependency>
      <groupId>org.graalvm.polyglot</groupId>
      <artifactId>polyglot</artifactId>
      <version>${graalvm.version}</version>
    </dependency>
    <dependency>
      <groupId>org.graalvm.sdk</groupId>
      <artifactId>word</artifactId>
      <version>${graalvm.version}</version>
    </dependency>
    <dependency>
      <groupId>org.graalvm.sdk</groupId>
      <artifactId>collections</artifactId>
      <version>${graalvm.version}</version>
    </dependency>
    ```
* (GR-43819) The `org.graalvm.polyglot` package and module is no longer contained in the boot module of a GraalVM JDK. Please depend on `org.graalvm.polyglot:polyglot` using Maven instead.
* (GR-47917) Added class-path isolation if polyglot is used from the class-path. At class initialization time and if polyglot is used from the class-path then the polyglot implementation spawns a module class loader with the polyglot runtime and language implementations. This allows to use an optimized runtime even if languages and polyglot are used from the class-path. Note that for best performance, it is recommended to load polyglot and the languages from the module-path.
* (GR-43819) Removed the deprecated APIs in `org.graalvm.nativeimage.RuntimeOptions` and added a new replacement API.
* (GR-46556) Provide [documentation and example code](https://www.graalvm.org/latest/reference-manual/embed-languages/#compatibility-with-jsr-223-scriptengine) on how to use Truffle languages via the ScriptEngine API. The example code can be inlined and modified for testing, we still recommend to use the Polyglot API for full control over embeddings.
* (GR-45896) JLine3 upgrade from 3.16 to 3.23. The JLine3 bundle that is used is customized and contains only `jline3-reader`, `jline3-terminal`, and `jline3-builtins` JLine3 components.
* (GR-44222) Polyglot contexts and engines now print a warning when deprecated options were used. To resolve this migrate the option using the deprecation instructions or set the `engine.WarnOptionDeprecation` to `false` to suppress this warning. It is recommended to prefer migration over suppression whenever possible. 
* (GR-46345) Added `Engine#copyResources(Path, String...)` to unpack the specified language and instrument resources into the target directory. This method is designed for creating pre-built installations of internal resources, specifically for standalone applications.
* (GR-36213) Added `HostAccess.Builder#useModuleLookup(Lookup)` to allow guest applications to access host classes from named modules. Passing `MethodHandles#lookup()` from a named module is the intended usage.
* (GR-48133) Native Image API: Added ability to promote jars from the class-path to the module-path in the native image driver. Use `ForceOnModulePath = ${module-name}`. Promoting a module to the module-path is equivalent to specifying it on the module-path in combination with exporting the module using `--add-modules ${module-name}` to the unnamed module.

## Version 23.0.0
* (GR-26758) Added the [TraceLimits](https://www.graalvm.org/reference-manual/embed-languages/sandbox-resource-limits#determining-sandbox-resource-limits) option to the Truffle Sandbox to measure a guest application's resource consumption and obtain realistic sandbox parameters.
* (GR-25849) (GR-41634) Added a new way to configure the IO access using the new class `IOAccess`. The IO access configuration determines how a guest language can access the host IO. The `IOAccess` class provides a predefined configuration to [disable](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/io/IOAccess.html#NONE) host IO access, or to [enable](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/io/IOAccess.html#ALL) full host IO access. A custom configuration can be created using an IOAccess [builder](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/io/IOAccess.html#newBuilder--).
* Deprecated `Context.Builder#allowIO(boolean)` To migrate, use `builder.allowIO(IOAccess.ALL)` to enable unrestricted IO operations on the host system, or `builder.allowIO(IOAccess.NONE)` to disable IO operations.
* Deprecated `Context.Builder#fileSystem(FileSystem)`. To migrate, use `builder.allowIO(IOAccess.newBuilder().fileSystem(fileSystem).build())`.
* Added automatic copying of language resources for embedding Truffle languages in native image. Documentation available [here](https://www.graalvm.org/reference-manual/embed-languages/#build-native-executables-from-polyglot-applications).
* (GR-41716) Added `HostAccess.Builder.allowMutableTargetMappings(HostAccess.MutableTargetMapping[])` to explicitly enable type coercion from guest objects to mutable Java host objects such as `java.util.Map` or `java.util.List`.
* (GR-42876) Added [FileSystem#newFileSystem](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/io/FileSystem.html#newFileSystem-java.nio.file.FileSystem-) creating a polyglot FileSystem for given Java NIO FileSystem.
* (GR-43820) Deprecated `org.graalvm.nativeimage.RuntimeOptions#getOptions` methods and `org.graalvm.nativeimage.RuntimeOptions.OptionClass` enum. These elements were mistakenly made API and will be removed in a future version. If your codebase depends on any of these please let us know.
* (GR-43997) Introduced the `LockFreePool` concurrent collection, and change the `LockFreePrefixTree` API to allow custom allocation policies.
* (GR-25539) Added `Value#fitsInBigInteger()` and `Value#asBigInteger()` to access guest or host number values that fit into `java.math.BigInteger` without loss of precision. `Value.as(BigInteger.class)` is also supported for such values. 
* (GR-25539) (potentially breaking-change) By default, all host values of type `java.lang.BigInteger` will now be interpreted as number values (`Value.isNumber()`). Previously, they were not interpreted as numbers. In order to restore the old behavior set `HostAccess.Builder.allowBigIntegerNumberAccess(boolean)` to false. Note that language support for interpreting numbers that do not fit into long values may vary. Some languages, like JavaScript, may require explicit conversions of host big integers. Other languages, like Ruby or Python can use big integers without explicit conversion. The same applies to values passed across guest languages.
* (GR-30473) Added the [SandboxPolicy](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/SandboxPolicy.html) that presets and validates context or engine configurations to make them suitable as a code sandbox. The policy is set by passing it to the [Engine.Builder#sandbox(SandboxPolicy)](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Engine.Builder.html#sandbox-org.graalvm.polyglot.SandboxPolicy-) or [Context.Builder#sandbox(SandboxPolicy)](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.Builder.html#sandbox-org.graalvm.polyglot.SandboxPolicy-) builder method.
* (GR-30473) For each SandboxPolicy a predefined host access policy was added:
    * [CONSTRAINED](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/HostAccess.html#CONSTRAINED) satisfies the `SandboxPolicy#CONSTRAINED` requirements. This host access is the default value for a Context with `SandboxPolicy#CONSTRAINED`.
    * [ISOLATED](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/HostAccess.html#ISOLATED) satisfies the `SandboxPolicy#ISOLATED` requirements. This host access is the default value for a Context with `SandboxPolicy#ISOLATED`.
    * [UNTRUSTED](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/HostAccess.html#UNTRUSTED) satisfies the `SandboxPolicy#UNTRUSTED` requirements. This host access is the default value for a Context with `SandboxPolicy#UNTRUSTED`.

## Version 22.3.0
* (GR-39852) Native Image API: Added FieldValueTransformer API
* (GR-35358) Added `Context.Builder.allowInnerContextOptions(boolean)` which allows the context to spawn inner contexts and modify and override language options. The default value for this privilege is set depending `Context.Builder.allowAllPrivilages(boolean)` is set or not. Do not enable this privilege in security sensitive scenarios.
* (GR-40198) Introduce public API for programmatic JNI / Resource / Proxy / Serialization registration from Feature classes during the image build.
* (GR-38909) Added Native Image com.oracle.svm.core.annotate annotation classes (@Alias, @TargetClass, @Substitute, ...).

## Version 22.2.0
* (GR-38925) Added `Value.hasMetaParents() and Value.getMetaParents()` that allow lookup of the hierarchy of parents for meta objects (e.g. super class or implemented interface of Java classes).
* (GR-38351) Added [FileSystem#allowLanguageHomeAccess](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/io/FileSystem.html#allowLanguageHomeAccess-org.graalvm.polyglot.io.FileSystem-) returning a `FileSystem` that forwards access to files in the language home to the default file system.
* (GR-38351) Added [FileSystem#newReadOnlyFileSystem](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/io/FileSystem.html#newReadOnlyFileSystem-org.graalvm.polyglot.io.FileSystem-) returning a read-only decorator for the given file system.
* Changed the behavior of [`Context.close()`](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.html#close--) (as well as `Context.close(false)` which is equivalent). In case the context was cancelled during the close operation or the context was exited during the close operation at request of the guest application, or it was already cancelled or exited before the close operation begins,
the close operation throws a [`PolyglotException`](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/PolyglotException) with [`PolyglotException.isCancelled()`](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/PolyglotException#isCancelled--) or [`PolyglotException.isExit()`](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/PolyglotException#isExit--), respectively, equal to `true`.
* (GR-29138)(EE-only) Added the ability to spawn a native-image isolate for a each `Engine` or `Context` in a native launcher or library.  This feature was previously supported only for the JVM deployment (GR-22699).
* Added [HostAccess.Builder.allowAccessInheritance](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/HostAccess.Builder.html#allowAccessInheritance-boolean-) to inherit access to methods that have been explicitly exported in an interface or superclass vs. only explicitly vetted method implementations (e.g. via `@HostAccess.Export`).
* Made `HostAccess.Builder.allowAccessInheritance(false)` the default. This restricts the set of accessible methods and might break existing code. To restore the previous behavior of `HostAccess.EXPLICIT`, you can use `HostAccess.newBuilder(HostAccess.EXPLICIT).allowAccessInheritance(true).build()`.
* Added List#add support for polyglot values that are mapped to java.util.List.

## Version 22.1.0
* Changed the default [`Object` target type mapping (`Value.as(Object.class)`)](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Value.html#as-java.lang.Class-) for values that have both array elements and members from `Map` to `List`.
  Note: This is an incompatible change. Embedders relying on the dynamic type `Map` after a `Object` target type coercion will have to migrate their code.
  The previous behavior can be restored using a custom [target type mapping](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/HostAccess.Builder.html#targetTypeMapping-java.lang.Class-java.lang.Class-java.util.function.Predicate-java.util.function.Function-), e.g.:
  ```java
  HostAccess access = HostAccess.newBuilder(HostAccess.EXPLICIT)
          .targetTypeMapping(Value.class, Object.class, v -> v.hasMembers() && v.hasArrayElements(), v -> v.as(Map.class))
          .build();
  try (Context c = Context.newBuilder().hostAccess(access).build()) {
      // run application
  }
  ```
* (GR-35010) Added API for Truffle Languages (`Language#getWebsite()`) and Instruments (`Instrument#getWebsite()`) to provide website information.
* (GR-33851) Dropped Java 8 support.

## Version 22.0.0
* (GR-31170) Native Image API: Added `WINDOWS_AARCH64` Platform.
* (GR-33657) Native Image API: Added `CEntryPoint#include` attribute which can be used to controll if the entry point should be automatically added to the shared library.
* (GR-22699)(EE-only) Added the ability to spawn a native-image isolate for a each `Engine` or `Context` by calling `Context.Builder.option("engine.SpawnIsolate", "true")`.  This enables heap isolation between the host and guest applications. Using isolates improves security, startup and warmup time of polyglot languages. In this mode, calls between host and guest are more costly as they need to cross a native boundary. It is recommended to use the `HostAccess.SCOPED` policy with this mode to avoid strong cyclic references between host and guest. This mode is experimental in this release and only supported for a limited set of languages. 

## Version 21.3.0
* Added the ability to share values between contexts. Please see  `Context.Builder.allowValueSharing(boolean)` for further details. 
* (GR-20286) Polyglot API: Added support for scoped values in guest-to-host callbacks. [Scoped values](https://www.graalvm.org/reference-manual/embed-languages/#controlling-host-callback-parameter-scoping) are automatically released when the callback returns. They can be configured in `HostAccess`.

## Version 21.2.0
* `AllowVMInspection` is enabled in the native launchers, `SIGQUIT` can be used to generate thread dumps. Performance counters are disabled by default, they can be enabled in the graalvm enterprise by the `--vm.XX:+UsePerfData` option.
* Changed behavior of `Value.as(TypeLiteral<Function<Object, Object>>).apply()`: When the function is called with an `Object[]` argument, it is passed through as a single argument rather than an array of arguments.
* Updated the required JVMCI version for Polyglot Embeddings in this release. All GraalVM JDK versions (8, 11, 16) already contain the updated JVMCI version and there is no further action required. If you are using a different JDK than GraalVM and you have configured the Graal compiler on the upgrade module path you will need one of the following JDK versions that include [JDK-8264016](https://bugs.openjdk.java.net/browse/JDK-8264016) for full compatibility:

  * Other JDK 11: Oracle JDK 11.0.13 (2021-10-19), OpenJDK is still to be determined.
  * Other JDK 16: No current plans to update JVMCI.
  * Other JDK 17: The new JVMCI version is already integrated into early access builds.

  If your JVMCI version is outdated you will be able to use GraalVM embeddings, but forced context cancellation (`Context.close(true)`) and interrupt (`Context.interrupt(Duration)`) will throw an error. We recommend the following workarounds:

  * Do not use forced context cancellation or interrupt. All other features are still supported.
  * Switch to the fallback runtime by removing graal.jar from the upgrade-module-path. Note that this will significantly worsen performance and should only be a last resort.
  * Wait with upgrading to 21.2 until the JDK version has support for the new JVMCI version.

## Version 21.1.0
* Added new methods  in `Value` for interacting with buffer-like objects:
    * Added `Value.hasBufferElements()` that returns  `true` if this object supports buffer messages.
    * Added `Value.isBufferWritable()` that returns `true` if this object supports writing buffer elements.
    * Added `Value.getBufferSize()` to return the size of this buffer.
    * Added `Value.readBufferByte(long)`, `Value.readBufferShort(ByteOrder, long)`, `Value.readBufferInt(ByteOrder, long)`, `Value.readBufferLong(ByteOrder, long)`, `Value.readBufferFloat(ByteOrder, long)`  and `Value.readBufferDouble(ByteOrder, long)` to read a primitive from this buffer at the given index.
    * Added `Value.writeBufferByte(long, byte)`, `Value.writeBufferShort(ByteOrder, long, short)`, `Value.writeBufferInt(ByteOrder, long, int)`, `Value.writeBufferLong(ByteOrder, long, long)`, `Value.writeBufferFloat(ByteOrder, long, float)`  and `Value.writeBufferDouble(ByteOrder, long, double)` to write a primitive in this buffer at the given index (supported only if `Value.isBufferWritable()` returns `true`).
* Added `Value` methods supporting iterables and iterators:
    * Added `hasIterator()` specifying that the `Value` is an iterable.
    * Added `getIterator()` to return the iterator for an iterable `Value`.
    * Added `isIterator()`  specifying that the `Value` is an iterator.
    * Added `hasIteratorNextElement()`  to test that the iterator `Value` has more elements to return by calling the `getIteratorNextElement()` method.
    * Added `getIteratorNextElement()` to return the current iterator element.
* Added `HostAccess.Builder.allowIterableAccess()` to allow the guest application to access Java `Iterables` as values with iterators (true by default for `HostAccess.ALL` and `HostAccess.Builder.allowListAccess(true)`, false otherwise).
* Added `HostAccess.Builder.allowIteratorAccess()` to allow the guest application to access Java `Iterators` (true by default for `HostAccess.ALL`, `HostAccess.Builder.allowListAccess(true)` and `HostAccess.Builder.allowIterableAccess(true)`,  false otherwise).
* Added `ProxyIterable` and `ProxyIterator` to proxy iterable and iterator guest values.
* Added `Value` methods supporting hash maps:
    * Added `hasHashEntries()` specifying that the `Value` provides hash entries.
    * Added `getHashSize()` to return hash entries count.
    * Added `hasHashEntry(Object)` specifying that the mapping for the specified key exists.
    * Added `getHashValue(Object)` returning the value for the specified key.
    * Added `getHashValueOrDefault(Object, Object)` returning the value for the specified key or a default value if the mapping for given key does not exist.
    * Added `putHashEntry(Object, Object)` associating the specified value with the specified key.
    * Added `removeHashEntry(Object)` removing the mapping for a given key.
    * Added `getHashEntriesIterator()` returning a hash entries iterator.
    * Added `getHashKeysIterator()` returning a hash keys iterator.
    * Added `getHashValuesIterator()` returning a hash values iterator.
* Added `HostAccess.Builder.allowMapAccess(boolean)` to allow the guest application to access Java `Map` as values with hash entries (true by default for `HostAccess.ALL`, false otherwise).
* Added `ProxyHashMap` to proxy map guest values.
* When `HostAccess.Builder.allowMapAccess(boolean)` is enabled the Java `HashMap.Entry` is interpreted as a guest value with two array elements.
* Added `Context.safepoint()` to manually poll thread local of a polyglot context while a host method is executed. For example, this allows the context to check for potential interruption or cancellation.
* `Value.putMember(String, Object)` now throws `UnsupportedOperationException` instead of `IllegalArgumentException` if the member is not writable.
* `Value.removeMember(String)` now throws `UnsupportedOperationException` instead of returning `false` if the member is not removable.
* `Value.invokeMember(String, Object...)` now throws `UnsupportedOperationException` instead of `IllegalArgumentException` if the member is not invokable.

## Version 21.0.0
* Added support for explicitly selecting a host method overload using the signature in the form of comma-separated fully qualified parameter type names enclosed by parentheses (e.g. `methodName(f.q.TypeName,java.lang.String,int,int[])`).
* Deprecated host method selection by JNI mangled signature, replaced by the aforementioned new form. Scheduled for removal in 21.2.

## Version 20.3.0
* Added a `log.file` option that allows redirection of all language, instrument or engine logging to a file. The handler configured with the `Context.Builder.logHandler` method has precedence over the new option.
* The option `-Dgraal.LogFile` is no longer inherited by the polyglot engine. Use the `log.file` option or configure a log handler instead.
* In host interop, `null` now adheres to Java semantics:
	* (Host interop's) `null` has no meta-object (e.g. `Value.getMetaObject()` returns `null`)
	* `Value.isMetaInstance(Object)` behaves like `instanceof` with respect to `null` (e.g. `null` is **NOT** an instance of any meta-object)
* Removed handling of `--jvm.*` and `--native.*` launcher options, which were deprecated since 1.0.0 RC14.
* Added the ability to specify a `TargetMappingPrecedence` of target type mappings for `HostAccess`  configurations that influence conversion order and precedence in relation to default  mappings and other target type mappings.
* Added `PolyglotException.isInterrupted()` to determine if an error was caused by an interruption of an application thread. The interrupted exceptions are no longer `PolyglotException.isCancelled()` but `PolyglotException.isInterrupted()`.
* All Truffle Graal runtime options (-Dgraal.) which were deprecated in GraalVM 20.1 are removed. The Truffle runtime options are no longer specified as Graal options (-Dgraal.). The Graal options must be replaced by corresponding engine options specified using [polyglot API](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Engine.Builder.html#option-java.lang.String-java.lang.String-).
* Added `Engine.getCachedSources()` to return the sources that were previously cached by the engine.
* Added support a default `OptionType` for Java enums. `OptionType.defaultType(Class<?>)` is now always supported for `enum` classes.
* Added `Context.interrupt(Duration)` to interrupt a polyglot Context execution. The interrupt is non-destructive meaning that the polyglot Context can still be used for further execution.
* Added `Value.as(Class)` support for converting values to abstract host classes with a default constructor.
* Added `HostAccess.Builder.allowAllClassImplementations` to allow converting values to abstract host classes using `Value.as` and host interop (true by default for `HostAccess.ALL`, false otherwise).

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
