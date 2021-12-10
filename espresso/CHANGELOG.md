# Espresso Changelog

## Version 22.0.0
### Internal changes
* Espresso adopted the new Frame API.


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
