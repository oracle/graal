# Espresso Changelog

## Version 22.3.0
### User-visible changes
* Interop invokable members of espresso objects can now also be read.
* Added the `addPath` invokable member to Espresso Bindings if `java.UseBindingsLoader=true`. Allows adding new path to the classloader associated with the bindings.
* Added option `java.PolyglotTypeConverters` that can be set to declare a type conversion function that maps a host meta qualified name to a type converter class in an embedded Espresso context.
* Added option`java.PolyglotInterfaceMappings` that can be set to a semicolon-separated list of 1:1 interface type mappings to automatically construct guest proxies for host objects that implement declared interfaces in the list.

### Internal changes
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
