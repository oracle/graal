# Wasm (GraalWasm (GraalVM Implementation of WebAssembly) Changelog

This changelog summarizes major changes to the WebAssembly engine implemented in GraalVM (GraalWasm).

## Version 21.3.0

* (GR-32924) Add support for importing and exporting mutable globals to GraalWasm,
  as defined by the respective WebAssembly proposal:
  https://github.com/WebAssembly/mutable-global/blob/master/proposals/mutable-global/Overview.md
* (GR-26941) Ensure that the module is completely validated when it is first parsed,
  instead of being validated late, during linking and instantiation. 
  All validation errors are now properly reported when `Context#eval` from the Polyglot API is invoked,
  and similarly when invoking `WebAssembly.validate` from the JS-WebAssembly Interface API.
* (GR-33183) Memory-access performance is improved by avoiding a branch due to a out-of-bounds check.
* (GR-32714) Loop-profiling in GraalWasm was made more accurate to enable more loop optimizations.
* (GR-33227) Change the Interop objects in GraalWasm to allow object-caching in the
  JS-WebAssembly Interface: https://webassembly.github.io/spec/js-api/index.html#object-caches
* (GR-32356) Fix bug in which memory or table initialization could lead to an integer overflow.
* (GR-33296) Notify JavaScript when the memory grows (i.e. `mem_grow` instruction executes).
  This improves the compliance of the JS-WebAssembly Interface API.
* (GR-33333) Add support for `BigInt`-to-`i64` conversion in the JS-WebAssembly Interface API:
  https://github.com/WebAssembly/JS-BigInt-integration
* (GR-32590) Improve internal design of GraalWasm to make JS-WebAssembly Interface API
  more compliant.


