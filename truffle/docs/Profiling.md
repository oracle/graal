---
layout: docs
toc_group: truffle
link_title: Profiling Truffle Interpreters
permalink: /graalvm-as-a-platform/language-implementation-framework/Profiling/
---
# Profiling Truffle Interpreters

There is no shortage of tools for profiling interpreters written using Truffle.
When running in JVM mode you can use standard JVM tooling such as VisualVM, Java Flight Recorder, and Oracle Developer Studio. When running in Native Image you can use `callgrind` from the Valgrind tool suite, and other system tools such as `strace`.
As a language running on GraalVM, other GraalVM tools can be used.
For a broad enough definition of profiling, you can also use the [Ideal Graph Visualizer (IGV)](https://docs.oracle.com/en/graalvm/enterprise/21/docs/tools/igv/) and C1 Visualizer to inspect the compiler output.

This guide is less about how to use each tool and more about suggestions for extracting the most useful information from the tools, assuming a basic knowledge of their usage.

## Profiling with CPU Sampler

The simplest way to profile the application level, for example, to find in which guest-language function(s) most of the time is spent, is to use CPU Sampler, which is part of the `/tools` suite and part of GraalVM.
Simply pass `--cpusampler` to your language launcher:

```shell
language-launcher --cpusampler --cpusampler.Delay=MILLISECONDS -e 'p :hello'
```

You probably want to use a sampling delay with `--cpusampler.Delay=MILLISECONDS` to only start profiling after warmup. That way, you can easily identify which functions get compiled and which do not and yet take a significant amount of time to execute.

See `language-launcher --help:tools` for more `--cpusampler` options.

## Creating a Flame Graph from CPU Sampler

The histogram output from CPUSampler can be quite large, making it difficult to analyze.
Additionally, as a flat format it is nto possible to analyze a call graph as that information simply is not encoded in the output.
A flame graph shows the entire call graph.
Its structure makes it considerably simpler to see where the application time is being spent.

Creating the flame graph is a multi-stage process. First, we need to profile the application with the JSON formatter:

```shell
language-launcher --cpusampler --cpusampler.SampleInternal --cpusampler.Mode=roots --cpusampler.Output=json -e 'p :hello' > simple-app.json
```

Use the `--cpusampler.SampleInternal=true` option if you want to profile internal sources, such as standard library functions.

Using the `--cpusampler.Mode=roots` option will sample each function, including inlined functions, which is more intuitive when looking at a flame graph and can often give a better idea of what is contributing to the overall method execution time. However, `--cpusampler.Mode=roots` adds extra overhead, so you might want to try without too.
The default is to not include inlined functions in order to minimize overhead.

The JSON formatter encodes call graph information that isn't available in the histogram format.
To make a flame graph out of this output, however, we need to transform it into a format that folds the call stack samples into single lines.
This can be done using [stackcollapse-graalvm.rb](https://github.com/eregon/FlameGraph/blob/graalvm/stackcollapse-graalvm.rb) from Benoit Daloze's fork of FlameGraph.

If you have not yet, you should clone this [fork of FlameGraph](https://github.com/eregon/FlameGraph/tree/graalvm) into the parent directory.
Now you can run the script to transform the output and pipe it into the script that will generate the SVG data:

```shell
../FlameGraph/stackcollapse-graalvm.rb simple-app.json | ../FlameGraph/flamegraph.pl > simple-app.svg
```

At this point, you should open the SVG file in a Chromium-based web browser.
Your system might have a different image manipulation application configured as the default application for SVG files.
While loading the file in such an application may render a graph, it likely will not handle the interactive components of the flame graph. Firefox may work as well, but Chromium-based browsers currently seem to have better support and performance for the flame graph files.

## Profiling with Oracle Developer Studio

[Oracle Developer Studio](https://www.oracle.com/technetwork/server-storage/developerstudio/overview/index.html) includes a
[Performance Analyzer](https://www.oracle.com/technetwork/server-storage/solarisstudio/features/performance-analyzer-2292312.html) that can be used with GraalVM.
Developer Studio can be [downloaded from OTN](https://www.oracle.com/technetwork/server-storage/developerstudio/downloads/index.html) and the current version at time of writing (12.6) provides a perpetual no-cost license for production use and the development of commercial applications.

Using the Developer Studio Performance Analyser is straightforward. Include the path to the Developer Studio binaries in your `PATH` and then prefix your normal command-line with `collect`.
For example:

```shell
collect js mybenchmark.js
```

On completion an "experiment" (.er) directory will have been created containing the profiling data for the command execution, `test.1.er` by default.
To view the profiling results, use the `analyzer` tool:

```shell
analyzer test.1.er
```

The `analyzer` GUI allows you to view the captured profiling information in several different ways, e.g., the timeline of your application, a flat function list, the call tree, a flame graph, etc.
There is also a command-line tool, `er_print`, which can be used for outputting the profiling information in textual form, for further analysis.

For full details, see the [Performance Analyzer](https://docs.oracle.com/cd/E77782_01/html/E77798/index.html) documentation.
