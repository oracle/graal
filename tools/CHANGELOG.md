# Truffle Tools Changelog

This changelog summarizes major changes between Truffle Tools versions.

## Version 23.1.4
* GR-53413: `CPUSampler` no longer guarantees to keep all contexts on the engine alive. As a result `CPUSampler#getData()` is deprecated and may not return data for all contexts on the engine. The contexts that were already collected by GC won't be in the returned map. `CPUSamplerData#getContext()` is also deprecated and returns null if the context was already collected.
* GR-53413: `CPUSamplerData#getDataList()` was introduced and returns all data collected by the sampler as a list of `CPUSamplerData`. For each context on the engine, including the ones that were already collected, there is a corresponding element in the list. `CPUSamplerData#getContextIndex()` returns the index of the data in the list.

## Version 23.0.0
* GR-41407: Added new option `--dap.SourcePath` to allow to resolve sources with relative paths.

## Version 22.3.0
* GR-40233: The interpretation of the `depth` parameter in [Insight heap dumping](../docs/tools/insight/Insight-Manual.md#heap-dumping) was off by one and was applied to primitive values as well. This is now corrected to match the documentation.

## Version 22.2.0
* GR-37442: Added new options `--heap.cacheSize=<int>` and `--heap.cacheReplacement=flush|lru` to enable memory cache in [Heap Dumping via Insight](../docs/tools/insight/Insight-Manual.md#heap-dumping-cache). 
* GR-37442: Added new method `flush()` to [heap dumping object](../docs/tools/insight/Insight-Manual.md#heap-dumping) to flush cached events to the heap dump file.

## Version 22.1.0
* Dumping JavaScript `Array` as `Object[]` into the `.hprof` file
* [HeapDump.newArray](https://www.graalvm.org/tools/javadoc/org/graalvm/tools/insight/heap/HeapDump.html) to start building an array

## Version 22.0.0
* GR-33316 Remove deprecated cpusampler APIs and CLIs
* GR-34745 Allow short-hand usage of the `--cpusampler` flag to enable and specify output. For example, `--cpusampler=calltree` is equivalent to `--cpusampler --cpusampler.Output=calltree`. NOTE: Since the flame graph output is unreadable in the terminal `--cpusampler=flamegraph` is equivalent to `--cpusampler --cpusampler.Output=flamegraph -cpusampler.OutputFile=flamegraph.svg`.
* GR-34209 Added overload of `CPUSampler.takeSample` with a timeout. By default samples time out when the configured period is exceeded.

## Version 21.3.2
* Dumping JavaScript `Array` as `Object[]` into the `.hprof` file
* [HeapDump.newArray](https://www.graalvm.org/tools/javadoc/org/graalvm/tools/insight/heap/HeapDump.html) to start building an array

## Version 21.3.0
* Reimplemented CPUSampler to use the Truffle language safepoints thus deprecating several API functions.
* Added new option `--cpusampler.SampleContextInitialization` which includes code executed during context initialization in the general sampling profile instead of grouping it into a single entry.
* Default CLI output of CPUSampler was simplified to not include compiled times.
* CPUSampler APIs to distingish compiled from interpreted samples were replaced by a more general API that supports an arbitrary number of compilation tiers.
* Added the --cpusampler.ShowTiers option that shows time spend in each optimization tier.
* Support for hash interoperability in Insight - no need to use `Truffle::Interop.hash_keys_as_members` anymore
* [Cooperative heap dumping](https://www.graalvm.org/tools/javadoc/org/graalvm/tools/insight/heap/package-summary.html) when embedding Insight into Java applications
* Add an SVG flamegraph output format to the CPUSampler, use option `--cpusampler.OutputFormat=flamegraph`.


## Version 21.1.0

* Use `--heap.dump=/path/to/file/to/generate.hprof` to enable [Heap Dumping via Insight](docs/Insight-Manual.md#Heap-Dumping)
* [Insight object API](https://www.graalvm.org/tools/javadoc/org/graalvm/tools/insight/Insight.html) provides access to `charIndex`, `charLength` and `charEndIndex`

## Version 21.0.0

* `--insight` option is no longer considered experimental
* Use `ctx.iterateFrames` to [access whole stack and its variables](docs/Insight-Manual.md#Accessing-whole-Stack)
* Embedders can [inject additional objects](docs/Insight-Embedding.md#Extending-Functionality-of-Insight-Scripts) into Insight scripts

## Version 20.3.0

* [GraalVM Insight](docs/Insight.md) Maven artifact is now `org.graalvm.tools:insight:20.3.0`
* [GraalVM Insight](docs/Insight-Manual.md#intercepting--altering-execution) can intercept execution and modify return values

## Version 20.2.0

* [GraalVM Insight](docs/Insight-Manual.md#modifying-local-variables) can modify values of local variables
* Write [GraalVM Insight](docs/Insight-Manual.md#python) scripts in Python

## Version 20.1.0

* [GraalVM Insight](docs/Insight-Manual.md#hack-into-the-c-code) can access local variables in C, C++ and other LLVM languages
* [GraalVM Insight](docs/Insight.md) is the new name for the former *T*-*Trace* technology

## Version 20.0.0
* Access to source location (see `line`, `column`, etc.) and `sourceFilter` selector in [Insight agent object API](https://www.graalvm.org/tools/javadoc/org/graalvm/tools/insight/Insight.html#VERSION)
* Embedding [Insight](docs/Insight-Embedding.md) into own application is now easily done via [Graal SDK](https://www.graalvm.org/tools/javadoc/org/graalvm/tools/insight/Insight.html#ID)
* Apply [Insight scripts](docs/Insight-Manual.md) C/C++/Julia & co. code - e.g. *hack into C with JavaScript*!
* Better error handling - e.g. propagation of errors from [Insight scripts](docs/Insight-Manual.md) to application code
* Hack your [Insight scripts in R](docs/Insight-Manual.md)!

## Version 19.3.0
* Introducing [GraalVM Insight](docs/Insight.md) - a  multipurpose, flexible tool for instrumenting and monitoring applications at full speed.
* Added a CLI code coverage tool for truffle languages. Enabled with `--coverage`. See `--help:tools` for more details.
