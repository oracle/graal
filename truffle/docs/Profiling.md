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
For a broad enough definition of profiling, you can also use the [Ideal Graph Visualizer (IGV)](../../docs/tools/ideal-graph-visualizer.md) and C1 Visualizer to inspect the compiler output.

This guide is less about how to use each tool and more about suggestions for extracting the most useful information from the tools, assuming a basic knowledge of their usage.

## Profiling with CPU Sampler

The simplest way to profile the application level, for example, to find in which guest-language function(s) most of the time is spent, is to use CPU Sampler, which is part of the `/tools` suite and part of GraalVM.
Simply pass `--cpusampler` to your language launcher:

```shell
language-launcher --cpusampler --cpusampler.Delay=MILLISECONDS -e 'p :hello'
```

You probably want to use a sampling delay with `--cpusampler.Delay=MILLISECONDS` to only start profiling after warmup. That way, you can easily identify which functions get compiled and which do not and yet take a significant amount of time to execute.

See `language-launcher --help:tools` for more `--cpusampler` options.

### Getting Compilation Data from the CPU Sampler

The CPU sampler does not show information about time spent in compiled code. 
This was, at least in part, motivated by the introduction of multi-tier compilation where "compiled code" was not descriptive enough.
Using the `--cpusampler.ShowTiers` option allows users to control whether they wish to see compilation data at all, as well as to specify exactly which compilation tiers should be considered in the report.
For example, adding `--cpusampler.ShowTiers=true` will show all the compilation tiers encountered during execution as shown below.

```
-----------------------------------------------------------------------------------------------------------------------------------------------------------
Sampling Histogram. Recorded 553 samples with period 10ms.
  Self Time: Time spent on the top of the stack.
  Total Time: Time spent somewhere on the stack.
  T0: Percent of time spent in interpreter.
  T1: Percent of time spent in code compiled by tier 1 compiler.
  T2: Percent of time spent in code compiled by tier 2 compiler.
-----------------------------------------------------------------------------------------------------------------------------------------------------------
Thread[main,5,main]
 Name              ||             Total Time    |   T0   |   T1   |   T2   ||              Self Time    |   T0   |   T1   |   T2   || Location
-----------------------------------------------------------------------------------------------------------------------------------------------------------
 accept            ||             4860ms  87.9% |  31.1% |  18.3% |  50.6% ||             4860ms  87.9% |  31.1% |  18.3% |  50.6% || ../primes.js~13-22:191-419
 :program          ||             5530ms 100.0% | 100.0% |   0.0% |   0.0% ||              360ms   6.5% | 100.0% |   0.0% |   0.0% || ../primes.js~1-46:0-982
 next              ||             5150ms  93.1% |  41.7% |  39.4% |  18.8% ||              190ms   3.4% | 100.0% |   0.0% |   0.0% || ../primes.js~31-37:537-737
 DivisibleByFilter ||              190ms   3.4% |  89.5% |  10.5% |   0.0% ||              100ms   1.8% |  80.0% |  20.0% |   0.0% || ../primes.js~7-23:66-421
 AcceptFilter      ||               30ms   0.5% | 100.0% |   0.0% |   0.0% ||               20ms   0.4% | 100.0% |   0.0% |   0.0% || ../primes.js~1-5:0-63
 Primes            ||               40ms   0.7% | 100.0% |   0.0% |   0.0% ||                0ms   0.0% |   0.0% |   0.0% |   0.0% || ../primes.js~25-38:424-739
-----------------------------------------------------------------------------------------------------------------------------------------------------------
```

Alternatively `--cpusampler.ShowTiers=0,2` will only show interpreted time and time spent in tier two compiled code, as shown below.

```
-----------------------------------------------------------------------------------------------------------------------------------------
Sampling Histogram. Recorded 620 samples with period 10ms.
  Self Time: Time spent on the top of the stack.
  Total Time: Time spent somewhere on the stack.
  T0: Percent of time spent in interpreter.
  T2: Percent of time spent in code compiled by tier 2 compiler.
-----------------------------------------------------------------------------------------------------------------------------------------
Thread[main,5,main]
 Name              ||             Total Time    |   T0   |   T2   ||              Self Time    |   T0   |   T2   || Location
-----------------------------------------------------------------------------------------------------------------------------------------
 accept            ||             5510ms  88.9% |  30.9% |  52.3% ||             5510ms  88.9% |  30.9% |  52.3% || ../primes.js~13-22:191-419
 :program          ||             6200ms 100.0% | 100.0% |   0.0% ||              320ms   5.2% | 100.0% |   0.0% || ../primes.js~1-46:0-982
 next              ||             5870ms  94.7% |  37.3% |  20.6% ||              190ms   3.1% |  89.5% |  10.5% || ../primes.js~31-37:537-737
 DivisibleByFilter ||              330ms   5.3% | 100.0% |   0.0% ||              170ms   2.7% | 100.0% |   0.0% || ../primes.js~7-23:66-421
 AcceptFilter      ||               20ms   0.3% | 100.0% |   0.0% ||               10ms   0.2% | 100.0% |   0.0% || ../primes.js~1-5:0-63
 Primes            ||               20ms   0.3% | 100.0% |   0.0% ||                0ms   0.0% |   0.0% |   0.0% || ../primes.js~25-38:424-739
-----------------------------------------------------------------------------------------------------------------------------------------
```

## Creating a Flame Graph from CPU Sampler

The histogram output from CPUSampler can be quite large, making it difficult to analyze.
Additionally, as a flat format it is nto possible to analyze a call graph as that information simply is not encoded in the output.
A flame graph shows the entire call graph.
Its structure makes it considerably simpler to see where the application time is being spent.

Creating the flame graph is a multi-stage process. First, we need to profile the application with the JSON formatter:

```shell
language-launcher --cpusampler --cpusampler.SampleInternal --cpusampler.Output=json -e 'p :hello' > simple-app.json
```

Use the `--cpusampler.SampleInternal=true` option if you want to profile internal sources, such as standard library functions.

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
