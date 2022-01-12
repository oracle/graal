# Espresso Changelog

## Version 22.1.0

## Version 22.0.0
### User-visible changes
* Support changes to fields in class redefinition. Enable with the `--java.ArbitraryChangesSupport=true` experimental flag.
* Support changes to class access modifiers in class redefinition. Enable with the `--java.ArbitraryChangesSupport=true` experimental flag.
* Added support for running native code with the LLVM runtime. This can be enabled with the `--java.NativeBackend=nfi-llvm` experimental flag.
  Native JDK libraries can be installed with `gu install espresso-llvm`. When installed, those libraries will be picked up by the `nfi-llvm` native backend.
  This allows to bypass some limitations of the default native backend (`nfi-dlmopen`). In particular, it avoids crashes that can happen on some glibc versions when using multiple contexts.
### Internal changes
* Espresso adopted the new Frame API.
### Noteworthy fixes
* Fix strings not being displayed properly in debugger through JDWP in some cases


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
