# Espresso Changelog

## Version 25.0.0
### User-visible changes
* Added experimental support for JVMCI. It can be enabled with the `java.EnableJVMCI` option.
* Added experimentation support for `-javaagent`. It can also be enabled from the polyglot API with `java.JavaAgent.$i` option set to `/path/to/jar=agent-options` where `$i` starts at 0 and increments by 1 for each extra java agent.
* Added the `org.graalvm.continuations.IdentityHashCodes` class, providing utilities for restoring identity hashcodes. This may be used for more properly deserializing continuations. 
* Added support for guest Java version 25.
* Introduce a `EnableAdvancedRedefinition` option that controls whether things like method or field addition/removal and class hierarchy changes are enabled.
  It controls the access to such features both for JDWP and java agents.
  Previously, this was enabled by default for JDWP, in this release it must be explicitly turned on.

## Version 24.2.0
### User-visible changes
* Added support for automatically using generic type parameters for incoming host objects, to allow more seamless communication between host and Espresso contexts.
* Continuation suspension now clears slots based on liveness analysis. This ensures the set of captured variables is predictable and deterministic.
* Custom type converters now always take precedence over built-in converters when passing objects from the host to the embedded Espresso context.
* Espresso's [hash interop](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/InteropLibrary.html#hasHashEntries(java.lang.Object)) implementation now throws an unsupported operation exception when attempting to modify an unmodifiable guest map.
* `Polyglot.cast()` now applies custom type converters and interface type mappings, same as `Polyglot.castWithGenerics()`.
### Noteworthy fixes
* The `<ProcessReferences>` builtin no longer hangs when multi-threading is disabled.

## Version 24.1.0
### User-visible changes
* Added `java.RuntimeResourceId` to allow customizing the truffle resource used to locate the java standard library used by espresso.
  The resource called "espresso-runtime-<RuntimeResourceId>" will be used. By default, "jdk21" then "openjdk21" are attempted.
* Espresso can now use TRegex to execute java.util.regex patterns. TRegex offers better performance than the standard implementation. Use `java.UseTRegex` to enable this engine.
* The interop `readBuffer` method can now be used from the guest Interop API and guest ByteBuffer objects implement this interop message.
* Many issues with espresso's JDWP implementation were fixed, improving the user experience when debugging with a Java IDE.
* Adds the `org.graalvm.continuations` package, along with support for continuations. see `docs/continuations.md` for more details.

## Version 24.0.0
### User-visible changes
* Added support for transparently converting common JDK exception types that flow from host to an embedded Espresso context.
* Added support for foreign `BigInteger` when calling espresso via interop. Adopted `fitsInBigInteger` and `asBigInteger` Truffle interop messages.

## Version 23.1.0
### User-visible changes
* Add `java.EnablePreview` option (`--enable-preview` command line option).
* Support JDK 21.
  Note that virtual threads are implemented as bound thread, not through continuations.
* Improved Management API implementation.
* Added support for Language sharing.
* Added boolean option `java.BuiltInPolyglotCollections` that enables transparent conversion of host collection objects that flows into an embedded Espresso context. Supported interfaces are: List, Set, Map, Iterator and Iterable.

## Version 23.0.0
### User-visible changes
* The Truffle InteropLibrary has/getMetaParents API is now fully implemented.
* Support JDK 20.
* Support foreign `Instant`, `TimeZone`, `Time`, `Date`, `Duration` when calling espresso via interop.
* Support calling overloaded and varargs methods through interop.
* Improve interop with foreign exceptions. A stack trace is now available and type mapping can be used to explicitly map exceptions.

## Version 22.3.0
### User-visible changes
* Interop invokable members of espresso objects can now also be read.
* Added the `addPath` invokable member to Espresso Bindings if `java.UseBindingsLoader=true`. Allows adding new path to the classloader associated with the bindings.
* Added option `java.PolyglotTypeConverters` that can be set to declare a type conversion function that maps a host meta qualified name to a type converter class in an embedded Espresso context.
* Added option`java.PolyglotInterfaceMappings` that can be set to a semicolon-separated list of 1:1 interface type mappings to automatically construct guest proxies for host objects that implement declared interfaces in the list.

### Noteworthy fixes
* Fix some conversions at interop boundaries: when an espresso-to-espresso conversion was seen, then an espresso-to-primitive conversion happens. The latter would fail.  
* Fix exit status on uncaught exceptions in the main thread.

## Version 22.1.0
### User-visible changes
* Added HotSwap support for changing the super class and implemented interfaces.
* Added HotSwap support for 'Move Field in Hierarchy' refactoring where state is preserved.
* HotSwap support for changing fields and class access modifiers are now turned on by default.
### Internal changes
* The truffle `AbstractTruffleException` API is now fully adopted.
* Better integration with the Truffle safepoint API
* Add new implementation for reading jimages (`libs/modules`) . It is used by default, `--java.JImage=native` can be used to revert to the old implementation.
* New command: `<ProcessReferences>`. Allows embedders to manually trigger reference processing in single-threaded mode.

## Version 22.0.0
### User-visible changes
* New HotSwap support for changing fields. Enable with the `--java.ArbitraryChangesSupport=true` experimental flag.
* New HotSwap support for changing class access modifiers. Enable with the `--java.ArbitraryChangesSupport=true` experimental flag.
* Added support for running native code with the LLVM runtime. This can be enabled with the `--java.NativeBackend=nfi-llvm` experimental flag.
  Native JDK libraries can be installed with `gu install espresso-llvm`. When installed, those libraries will be picked up by the `nfi-llvm` native backend.
  This allows to bypass some limitations of the default native backend (`nfi-dlmopen`). In particular, it avoids crashes that can happen on some glibc versions when using multiple contexts.
### Internal changes
* Espresso adopted the new Frame API.
### Noteworthy fixes
* Fix Strings sometimes not properly displayed in the debugger view through JDWP


## Version 21.3.0
### User-visible changes
* Java 17 guest and host support.
* Support using interop buffer-like object as a `byte[]` in espresso.
* Add buffer interop messages to the explicit interop API.
* The Polyglot API is not enabled by default anymore. When needed, contexts should be created with the `java.Polyglot` option set to `true`.
* The HotSwapAPI is not auto-enabled by debugging anymore. When needed, contexts should be created with the `java.HotSwapAPI` option set to `true`.
* Avoid illegal reflective access warnings with host Java >= 11.
### Internal changes
* The Static Object Model has moved to Truffle.
* On-Stack-Replacement using the new Truffle bytecode OSR API.
* Detect and report trivial methods.
* Enable saturated type flows.
### Noteworthy fixes
* Performance and functional related to JDWP.
* Fix some crashes observed when using the JNA.
