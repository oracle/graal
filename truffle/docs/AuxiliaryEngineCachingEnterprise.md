---
layout: docs
toc_group: truffle
link_title: Auxiliary Engine Caching
permalink: /graalvm-as-a-platform/language-implementation-framework/AuxiliaryEngineCachingEnterprise/
---
# Auxiliary Engine Caching

The following document describes how the auxiliary engine cache of GraalVM works.

This feature is only available in Oracle GraalVM. In GraalVM Community Edition, these options are not available.

## Introduction

Warmup of Truffle guest language programs can take a significant amount of time.
Warmup consists of work that is repeated every time a program is executed until peak performance is reached.
This includes:
1. Loading and parsing the guest application into Truffle AST data structures.
2. Execution and profiling of the guest application in the interpreter.
3. Compilation of the AST to machine code.

Within a single OS process, the work performed during warmup can be shared by specifying an [explicit engine](../../docs/reference-manual/embedding/embed-languages.md#code-caching-across-multiple-contexts).
This requires language implementations to disable context-related optimizations to avoid deoptimizations between contexts that share code.
Auxiliary engine caching builds upon the mechanism for disabling context-related optimizations and adds the capability to persist an engine with ASTs and optimized machine code to disk.
This way, the work performed during warmup can be significantly reduced in the first application context of a new process.

We use the SVM auxiliary image feature to persist and load the necessary data structures to the disk.
Persisting the image can take significant time as compilation needs to be performed.
However, loading is designed to be as fast as possible, typically almost instantaneous.
This reduces the warmup time of an application significantly.

## Getting Started

Starting from Oracle GraalVM installation, you first need to (re)build an image with auxiliary engine caching capabilities.
For example, one can rebuild the JavaScript image by adding the auxiliary engine cache feature:

```
graalvm/bin/native-image --macro:js-launcher -H:+AuxiliaryEngineCache -H:ReservedAuxiliaryImageBytes=1073741824
```

The `--macro` argument value depends on the guest language
By default, auxiliary images of up to 1GB are possible.
The maximum size can be increased or decreased as needed.
The amount of reserved bytes does not actually impact the memory consumed by the application.
In future versions, the auxiliary engine cache will be enabled by default when the `--macro:js-launcher` macro is used.

After rebuilding the JavaScript launcher, the feature is used as follows:

Create a new file `fib.js`:

```
function fib(n) {
   if (n == 1 || n == 2) {
       return 1;
   }
   return fib(n - 1) + fib(n - 2);
}
console.log(fib(32))
```

In order to persist the engine of a profiling run to disk use the following command line:

```
graalvm/bin/js --experimental-options --engine.TraceCache=true --engine.CacheStore=fib.image fib.js
```

The ` --engine.TraceCache=true` option is optional and allows you to see what is going on.

The output is as follows:

```
[engine] [cache] No load engine cache configured.
2178309
[engine] [cache] Preparing engine for store (compile policy hot)...
[engine] [cache] Force compile targets mode: hot
[engine] [cache] Prepared engine in 1 ms.
[engine] [cache] Persisting engine for store ...
[engine] [cache] Persisted engine in 20 ms.
[engine] [cache] Detecting changes (update policy always)...
[engine] [cache]     New image contains         1 sources and  82 function roots.
[engine] [cache]     Always persist policy.
[engine] [cache] Writing image to fib.image...
[engine] [cache] Finished writing 1,871,872 bytes in 4 ms.
```

The engine can now be loaded from disk using the following command:

```
graalvm/bin/js --experimental-options --engine.TraceCache --engine.CacheLoad=fib.image fib.js
```

which prints:

```
[engine] [cache] Try loading image './fib.image'...
[engine] [cache] Loaded image in 0 ms. 1,871,872 bytes   1 sources  82 roots
[engine] [cache] Engine from image successfully patched with new options.
2178309
[engine] [cache] No store engine cache configured.
```

Since there is no need to warm up the application, the application's execution time should be significantly improved.

## Usage

The cache store and load operations can be controlled using the following options:

* `--engine.Cache=<path>` Loads and stores the cached engine from/to  `path`.
* `--engine.CacheStore=<path>` Stores the cached engine to  `path`.
* `--engine.CacheLoad=<path>` Loads the cached engine from `path`.
* `--engine.CachePreinitializeContext=<boolean>` Preinitialize a new context in the image (default `true`).
* `--engine.TraceCache=<boolean>` Enables debug output.
* `--engine.TraceCompilation=<boolean>` Prints forced compilations.

The compilation of roots may be forced when an image is stored using the `--engine.CacheCompile=<policy>` option. The supported policies are:

* `none`: No compilations will be persisted, and existing compilations will be invalidated.
* `compiled`: No compilations will be forced, but finished compilations will be persisted.
* `hot`: All started compilations will be completed and then persisted. (default)
* `aot`: All started, and AOT compilable roots will be forced to compile and persisted.
* `executed`: All executed and all AOT compilable roots will be forced to compile.

By default, all started compilations in the compile queue will be completed and then persisted.
Whether a function root is AOT compilable is determined by the language.
A language supports AOT by implementing `RootNode.prepareForAOT()`.

An update policy can be specified if both load and store operations are set using the `--engine.UpdatePolicy=<policy>` option.
Available policies are:

* `always` Always persist.
* `newsource` Store if new source was loaded that was not contained in the previously loaded image.
* `newroot` Store if a new root was loaded and not contained in the previously loaded image.
* `never` Never persist.

## Known Restrictions

* There are generally no restrictions on the kind of applications that can be persisted.
If the language supports a shared context policy, auxiliary engine caching should work.
If the language does not support it, then no data will be persisted.

* The persisted auxiliary engine image can only be used with the same SVM native image that it was created with.
Using the engine image with any other native-image will fail.

* There can only be one active auxiliary image per native-image isolate.
Trying to load multiple auxiliary images at the same time will fail.
Currently, auxiliary images can also not be unloaded, but it is planned to lift this restriction in the future.

## Security Considerations

All data that is persisted to disk represents code only and no application context-specific data like global variables.
However, profiled ASTs and code may contain artifacts of the optimizations performed in a Truffle AST.
For example, it is possible that runtime strings are used for optimizations and therefore persisted to an engine image.

## Development and Debugging on NativeImage

There are several options useful for debugging auxiliary engines caching when running on NativeImage:

* `-XX:+TraceAuxiliaryImageClassHistogram` Prints a class histogram of all the objects contained in an image when persisting.
* `-XX:+TraceAuxiliaryImageReferenceTree` Prints a class reference tree of all the objects contained in an image when persisting.

## Development and Debugging on HotSpot

It can be useful to debug language implementation issues related to auxiliary image on HotSpot.
On Oracle GraalVM in JVM mode, we have additional options that can be used to help debug issues with this feature:
Since storing partial heaps on HotSpot is not supported, these debug features do not work on HotSpot.

* `--engine.DebugCacheStore=<boolean>` Prepares the engine for caching and stores it to a static field instead of writing it to disk.
* `--engine.DebugCacheLoad=<boolean>` Prepares the engine to use the engine stored in the static field instead of reading it from disk.
* `--engine.DebugCacheCompile=<boolean>` Policy to use to force compilation for executed call targets before persisting the engine. This supports the same values as `--engine.CacheCompile`.
* `--engine.DebugCacheTrace=<boolean>` Enables tracing for the engine cache debug feature.

For example:

```
js --experimental-options --engine.TraceCompilation --engine.DebugCacheTrace --engine.DebugCacheStore --engine.DebugCacheCompile=executed fib.js
```

Prints the following output:

```
[engine] opt done         fib                                                         |ASTSize            32 |Time   231( 147+84  )ms |Tier             Last |DirectCallNodes I    6/D    8 |GraalNodes   980/ 1857 |CodeSize         7611 |CodeAddress 0x10e20e650 |Source       fib.js:2
2178309
[engine] [cache] Preparing debug engine for storage...
[engine] [cache] Force compile targets mode: executed
[engine] [cache] Force compiling 4 roots for engine caching.
[engine] opt done         @72fa3b00                                                   |ASTSize             3 |Time   211( 166+45  )ms |Tier             Last |DirectCallNodes I    2/D    1 |GraalNodes   500/ 1435 |CodeSize         4658 |CodeAddress 0x10e26c8d0 |Source            n/a
[engine] opt done         :program                                                    |ASTSize            25 |Time   162( 123+39  )ms |Tier             Last |DirectCallNodes I    1/D    1 |GraalNodes   396/ 1344 |CodeSize         4407 |CodeAddress 0x10e27fd50 |Source       fib.js:1
[engine] opt done         Console.log                                                 |ASTSize             3 |Time    26(  11+15  )ms |Tier             Last |DirectCallNodes I    0/D    0 |GraalNodes    98/  766 |CodeSize         2438 |CodeAddress 0x10e285710 |Source    <builtin>:1
[engine] [cache] Stored debug engine in memory.
```

This allows rapidly iterating on problems related to the compilation as well as to attach a Java debugger.
A Java debugger can be attached using `--vm.Xdebug --vm.Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=8000`.

Debugging the loading of persisted engines is more difficult as writing an engine to disk is not supported on HotSpot.
However, it is possible to use the polyglot embedding API to simulate this use-case in a unit test.
See the `com.oracle.truffle.enterprise.test.DebugEngineCacheTest` class as an example.
