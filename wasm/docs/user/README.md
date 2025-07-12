# GraalWasm Documentation

GraalWasm is an open-source WebAssembly runtime.
It runs WebAssembly programs in binary format and can be used to embed and leverage WebAssembly modules in Java applications.
GraalWasm is under active development and implements a number of WebAssembly extensions.

## WebAssembly Module System

Using GraalWasm, you can load WebAssembly modules in your application, access them from Java, and make them interoperate with other Graal languages.
To proficiently use GraalWasm, it is important to first understand how GraalWasm maps WebAssembly's module system onto the Polyglot API.

GraalWasm uses the binary format of WebAssembly modules as its language.
A valid [Source](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Source.html) that you can evaluate using GraalWasm is always a single WebAssembly module in the [binary format](https://webassembly.github.io/spec/core/binary/index.html).

Here is one way you can build a WebAssembly `Source`:

```java
Source source = Source.newBuilder("wasm", new File("example.wasm")).build();
```

Or directly from a byte array:

```java
Source source = Source.newBuilder("wasm", ByteSequence.create(in.readAllBytes()), "example").build();
```

When you evaluate a WebAssembly `Source`, the module is parsed and validated, and a module object is returned as the result value.
This module can then be instantiated using `module.`[`newInstance()`](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Value.html#newInstance(java.lang.Object...)).
To access exported functions and bindings, first get the _exports_ member of the instance using `instance.getMember("exports")`, which returns an object containing all the exported members.
You can then use `exports.getMember("bindingName")` to read an exported binding, and either `exports.getMember("functionName").execute(...args)` or `exports.invokeMember("functionName", ...args)` to call an exported function.

```java
// Evaluate the Source.
Value exampleModule = context.eval(source);
// Instantiate the example module (optional: provide an import object).
Value exampleInstance = exampleModule.newInstance();
// Get the exports of the module instance.
Value exampleExports = exampleInstance.getMember("exports");
// Invoke an exported function.
assert exampleExports.invokeMember("factorial", 8).asInt() == 40320;
// Get the value of an exported global.
assert exampleExports.getMember("theAnswerToLife").asInt() == 42;
```

[Source names](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Source.html#getName()) are important in GraalWasm because they are also used to resolve module imports.
If a module tries to import a symbol from module `foo`, then GraalWasm looks for that symbol in the module whose `Source` was named `foo`.
These imports are resolved from the arguments passed to `module.newInstance()` (either the import object or a module argument).
When using the `wasm.EvalReturnsInstance=true` option, the imports are not resolved until the module instance's exports are accessed or executed for the first time.

### Module Instance Objects

By calling [`newInstance()`](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Value.html#newInstance(java.lang.Object...)) on the WebAssembly module value returned by [`Context.eval(Source)`](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.html#eval(org.graalvm.polyglot.Source)), you instantiate the module and create new module instance objects.
Module instance objects expose a read-only "exports" member, an object that contains a member for every symbol that was exported from the WebAssembly module.
You can get a list of all exported symbols using [getMemberKeys](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Value.html#getMemberKeys()), access individual exports using [getMember](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Value.html#getMember(java.lang.String)) and, in the case of mutable globals, use [putMember](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Value.html#putMember(java.lang.String,java.lang.Object)) to set their values.
You can also call functions using [invokeMember](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Value.html#invokeMember(java.lang.String,java.lang.Object...)).

Here is how the various kinds of WebAssembly exports map to polyglot values:

* Functions

  Functions are exported as executable values, which you can call using [execute](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Value.html#execute(java.lang.Object...)).
  Alternatively, you can use [invokeMember](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Value.html#invokeMember(java.lang.String,java.lang.Object...)) on the _exports_ object.
  Function arguments and return values are mapped between WebAssembly value types and polyglot values using the [type mapping](#type-mapping).
  If a function returns multiple values, these are wrapped in an interop array.
  Use [getArrayElement](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Value.html#getArrayElement(long)) to extract the individual return values.

* Globals

  When you access an exported global using `getMember`, you get the global's value, mapped using the [type mapping](#type-mapping).
  If the global is mutable, you can also update its value using `putMember`.
  Currently, setting globals works only for numeric types, whose value is mapped according to the [type mapping](#type-mapping).

* Memories

  Exported memories implement both the array interface and the buffer interface.
  The array interface lets you view the memory as an array of bytes using [getArrayElement](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Value.html#getArrayElement(long)) and [setArrayElement](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Value.html#setArrayElement(long,java.lang.Object)).
  The buffer interface lets you do bulk copies of memory using [readBuffer](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Value.html#readBuffer(long,byte%5B%5D,int,int)) and read and write the Java primitive types from and to the memory using the `readBuffer*` and `writeBuffer*` methods.

* Tables

  Exported tables are opaque and cannot be queried or modified currently.

## Type Mapping

Whenever a WebAssembly value is passed either to Java code or to another Graal language, via a function call, return value, or exported global access, it is mapped to a polyglot value.
The tables below show how this mapping works.
WebAssembly is a statically-typed language and all values (locals, function arguments, return values) have a static type.
Based on this type, GraalWasm interprets a polyglot value as a value of this type or reports a type error if the types do not match.

### WebAssembly Values as Polyglot Values

This table describes for each WebAssembly value type which polyglot interfaces the resulting value implements.

| WebAssembly Type | Polyglot Interface           |
|:-----------------|------------------------------|
| `i32`            | Number that fits in `int`    |
| `i64`            | Number that fits in `long`   |
| `f32`            | Number that fits in `float`  |
| `f64`            | Number that fits in `double` |
| `v128`           | Read-only buffer of 16 bytes |
| `funcref`        | Executable object            |
| `externref`      | Returned verbatim            |

### Passing Arguments to WebAssembly Functions

When calling an exported WebAssembly function, its exact type signature must be respected.
The table below gives the expected argument type for every possible WebAssembly parameter type.

| WebAssembly Parameter Type | Expected Argument Type                                                           |
|:---------------------------|----------------------------------------------------------------------------------|
| `i32`                      | `int`                                                                            |
| `i64`                      | `long`                                                                           |
| `f32`                      | `float`                                                                          |
| `f64`                      | `double`                                                                         |
| `v128`                     | Existing `v128` value received from WebAssembly                                  |
| `funcref`                  | WebAssembly's `ref.null` or an exported WebAssembly function                     |
| `externref`                | Can be anything (only WebAssembly's `ref.null` is seen as null by `ref.is_null`) |

## Options

GraalWasm can be configured with several options.
When using the [Polyglot API](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/package-summary.html), options are passed programmatically to the [Context](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.html) object:

```java
Context.newBuilder("wasm").option("wasm.Builtins", "wasi_snapshot_preview1").build();
```

See the [Polyglot Programming](https://github.com/oracle/graal/blob/master/docs/reference-manual/polyglot-programming.md#passing-options-programmatically) reference for more information on how to set options programmatically.

The available options are divided into stable and experimental options.
Experimental options are provided with no guarantee of future support and can change from version to version.
If an experimental option is used with the `wasm` launcher, the `--experimental-options` option has to be provided.
When using a `Context`, the method [allowExperimentalOptions(true)](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.Builder.html#allowExperimentalOptions(boolean)) has to be called on the [Context.Builder](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.Builder.html).

### Stable Options

The following stable options are provided:

* `--wasm.Builtins`: Exposes some of the GraalWasm-provided built-in modules.
  The syntax for the value is `[<linkingName>:]<builtinModuleName>,[<linkingName>:]<builtinModuleName>,...`.
  The requested modules are comma-separated.
  Every module may optionally be prefixed with a colon-separated linking name.
  If a linking name is given, the module is exported under the given linking name.
  Otherwise, the module is exported under its built-in module name.

  The provided built-in modules are:
   * `spectest`: A module of simple functions useful for writing test cases.
     This module implements the same interface as the [spectest module of the WebAssembly reference interpreter](https://github.com/WebAssembly/spec/blob/main/interpreter/host/spectest.ml).
     Using it enables the execution of the [core WebAssembly specification tests](https://github.com/WebAssembly/spec/tree/main/test/core).
   * `wasi_snapshot_preview1`: GraalWasm's implementation of the [WebAssembly System Interface Snapshot Preview 1](https://github.com/WebAssembly/WASI/blob/main/legacy/preview1/docs.md).
     Covers most of the documented API, except socket and signal support.

* `--wasm.WasiMapDirs`: A list of pre-opened directories that should be accessible through the WebAssembly System Interface API.
  The syntax for the value is `[<virtualDir>::]<hostDir>,[<virtualDir>::]<hostDir>,...`.
  The pre-opened directories are comma-separated.
  Every directory may optionally be prefixed with a double-colon-separated virtual path.
  Inside the WebAssembly module, the directory is available at the virtual path.
  If the virtual path is omitted, the pre-opened directory will be on the same path as on the host filesystem.

  This option must be set to allow modules that use WASI to access the filesystem.
  Access will be granted only to the contents of these pre-opened directories.

### Experimental Options

Note that these options are experimental and are not guaranteed to be maintained or supported in the future.
To use them, the `--experimental-options` option is required, or experimental options have to be enabled on the `Context`.

The options below correspond to feature proposals that add new features to the WebAssembly standard.
The accepted values are `true` for enabling a feature and `false` for disabling a feature.
Features that have already been merged into the WebAssembly spec are enabled by default in GraalWasm.
Features that are not yet merged into the spec are disabled by default.
Users can override the defaults to experiment with upcoming features or opt out of standardized features.

* `--wasm.BulkMemoryAndRefTypes`: Enable support for the [bulk memory operations feature](https://github.com/WebAssembly/spec/blob/master/proposals/bulk-memory-operations/Overview.md) and [reference types feature](https://github.com/WebAssembly/spec/blob/master/proposals/reference-types/Overview.md), exposing instructions for efficient memory initialization and adding support for first-class opaque references.
  Defaults to `true`.

* `--wasm.ExtendedConstExpressions`: Enable support for the [extended constant expressions feature](https://github.com/WebAssembly/extended-const/blob/main/proposals/extended-const/Overview.md), adding limited support for arithmetic instructions inside constant expressions.
  Defaults to `false`.

* `--wasm.Memory64`: Enable support for the [Memory64 feature](https://github.com/WebAssembly/memory64/blob/main/proposals/memory64/Overview.md), letting memories be larger than 4 GiB.
  Defaults to `false`.

* `--wasm.MultiMemory`: Enable support for the [multiple memories feature](https://github.com/WebAssembly/multi-memory/blob/master/proposals/multi-memory/Overview.md), allowing modules to have multiple memories.
  Defaults to `false`.

* `--wasm.MultiValue`: Enable support for the [multi-value feature](https://github.com/WebAssembly/spec/blob/master/proposals/multi-value/Overview.md), letting functions return multiple values.
  Defaults to `true`.

* `--wasm.SaturatingFloatToInt`: Enable support for the [non-trapping float-to-int conversions feature](https://github.com/WebAssembly/spec/blob/master/proposals/nontrapping-float-to-int-conversion/Overview.md), adding float-to-int conversion instructions that saturate instead of failing with a trap.
  Defaults to `true`.

* `--wasm.SignExtensionOps`: Enable support for the [sign-extension operators feature](https://github.com/WebAssembly/spec/blob/master/proposals/sign-extension-ops/Overview.md), adding instructions for extending signed integer values.
  Defaults to `true`.

* `--wasm.SIMD`: Enable support for the [fixed-width SIMD feature](https://github.com/WebAssembly/spec/tree/main/proposals/simd), introducing a new value type, `v128`, and associated instructions for SIMD arithmetic.
  Defaults to `true`.

* `--wasm.Threads`: Enable support for the [threading feature](https://github.com/WebAssembly/threads/blob/master/proposals/threads/Overview.md), letting WebAssembly modules use new instructions for atomic memory access.
  Defaults to `false`.

## Using the GraalWasm Launcher

GraalWasm standalones provide the `wasm` launcher, which you can use to execute programs compiled as WebAssembly binary modules.

```bash
wasm [OPTION...] [--entry-point=FN] FILE [ARG...]
```

* `[OPTION...]`

  The options consist of GraalWasm engine options, prefixed with `--wasm.`, for example `--wasm.WasiMapDirs=preopened-dir`, and any other polyglot engine options.
  When using the `wasm` launcher, the `--wasm.Builtins=wasi_snapshot_preview1` option is set by default so that you can directly execute modules compiled against the [WebAssembly System Interface Snapshot Preview 1](https://github.com/WebAssembly/WASI/blob/main/legacy/preview1/docs.md).

  The available options are documented in [Options](#options).
  You can also get a full list of GraalWasm engine options by passing the `--help:wasm` option to the `wasm` launcher.
  To include internal options, use `--help:wasm:internal`.
  Note that those lists both include stable, supported options, and experimental options.

* `[--entry-point=FN]`

  You can specify the `--entry-point` option to choose which exported function is to be used as the module's entry point, for example `--entry-point=my_custom_main_fn`.
  If the `--entry-point` option is missing, GraalWasm will try to auto-detect the entry point.
  It will first look for an exported function named `_start` and then for an exported function named `_main`.
  The first such function found will be executed as the entry point by the `wasm` launcher.

* `FILE`

  This is the path to the binary module that will be executed.

* `[ARG...]`

  Program arguments that are accessible to the program through the WASI [args_get](https://github.com/WebAssembly/WASI/blob/main/legacy/preview1/docs.md#-args_getargv-pointerpointeru8-argv_buf-pointeru8---result-errno) and [args_sizes_get](https://github.com/WebAssembly/WASI/blob/main/legacy/preview1/docs.md#-args_sizes_get---resultsize-size-errno) functions.

### Related Documentation

- [Embed C in Java Using GraalWasm](https://github.com/graalvm/graal-languages-demos/tree/main/graalwasm/graalwasm-embed-c-code-guide){:target="_blank"}
- [Embedding Languages documentation](https://www.graalvm.org/latest/reference-manual/embed-languages/){:target="_blank"}
- [GraalWasm](https://github.com/oracle/graal/tree/master/wasm){:target="_blank"}