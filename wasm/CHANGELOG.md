# GraalVM WebAssembly (GraalWasm) Changelog

This changelog summarizes major changes to the WebAssembly engine implemented in GraalVM (GraalWasm).

## Version 23.1.0

* Implemented the [extended const expressions](https://github.com/WebAssembly/extended-const) proposal. The feature can be enabled with the option `--wasm.ExtendedConstExpressions=true`.
* Implemented the [SIMD](https://github.com/WebAssembly/simd) proposal. This feature is enabled by default and can be disabled with the option `--wasm.SIMD=false`.

## Version 23.0.0

* Added experimental debugging support for DWARFv4. This enables debugging of C, C++, and Rust applications.
* Added experimental support for [Memory64](https://github.com/WebAssembly/memory64). The feature can be enabled with the option `--wasm.Memory64=true`.
* Implemented the [Bulk-Memory](https://github.com/WebAssembly/bulk-memory-operations) and [Reference-Types](https://github.com/WebAssembly/reference-types) proposal. They can be disabled with the option `--wasm.BulkMemoryAndRefTypes`.

## Version 22.3.0

* Implemented the [Multi-Value](https://github.com/WebAssembly/multi-value) proposal. It can be disabled with the
  option `--wasm.MultiValue=false`.
* Sign-Extension-Ops and Saturating-Float-To-Int conversions are now enabled by default.

## Version 22.1.0

* Changed the representation of WebAssembly control flow structures to a flat model. Loops and branches are now
  represented by jumps rather than separated AST nodes.

## Version 22.0.0

* Implemented the [Sign-Extension-Ops](https://github.com/WebAssembly/sign-extension-ops) proposal. It is available
  behind the experimental option `--wasm.SignExtensionOps`.
* GraalWasm adopted the new Frame API.

## Version 21.3.0

* (GR-32924) Added support for importing and exporting mutable globals to GraalWasm, as defined by the respective
  WebAssembly proposal:
  https://github.com/WebAssembly/mutable-global/blob/master/proposals/mutable-global/Overview.md
* (GR-26941) Moved the module-validation checks to an earlier stage, so that the module is completely validated during
  parsing, instead of being validated late, during linking and instantiation. All validation errors are now properly
  reported when `Context#eval` from the Polyglot API is invoked, and similarly when invoking `WebAssembly.validate` from
  the JS-WebAssembly Interface API.
* (GR-33183) Memory-access performance was improved by avoiding a branch due to a out-of-bounds check.
* (GR-32714) Loop-profiling in GraalWasm was made more accurate to enable more loop optimizations.
* (GR-33227) Changed the Interop objects in GraalWasm to allow object-caching in the JS-WebAssembly
  Interface: https://webassembly.github.io/spec/js-api/index.html#object-caches
* (GR-32356) Fixed a bug that caused an overflow during memory or table initialization.
* (GR-33296) Added notifications to JavaScript when the memory grows (i.e., when `mem_grow` instruction executes). This
  improves the compliance of the JS-WebAssembly Interface API.
* (GR-33333) Added support for `BigInt`-to-`i64` conversion in the JS-WebAssembly Interface API:
  https://github.com/WebAssembly/JS-BigInt-integration
* (GR-32590) Improved internal implementation of GraalWasm to make JS-WebAssembly Interface API more compliant.


