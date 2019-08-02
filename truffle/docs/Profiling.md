# Profiling Truffle Interpreters

There is no shortage of tools for profiling interpreters written using
Truffle. When running in JVM mode, we can use standard JVM tooling, such as
VisualVM, Java Flight Recorder and Oracle Developer Studio. When run as a Native Image we can use
callgrind from the Valgrind tool suite and other system tools, such as strace.
As a GraalVM language we can also use other GraalVM tools. For a broad enough
definition of profiling, we can also use the Ideal Graph Visualizer (IGV) and
C1 Visualizer to inspect Graal's output.

This document is less about how to use each tool and more about suggestions for extracting
the most useful information from the tools, assuming basic knowledge of their usage.

### Profiling with CPUSampler

The simplest way to profile the application level, for example to find in which
guest-language function(s) most of the time is spent is to use the CPUSampler,
which is part of the `/tools` suite and part of GraalVM. Simply pass `--cpusampler`
to your language launcher:

```bash
$ my-language --cpusampler --cpusampler.Delay=MILLISECONDS -e 'p :hello'
```

You probably want to use a sampling delay with `--cpusampler.Delay=MILLISECONDS`
to only start profiling after warmup. That way, you can easily identify which
functions get compiled and which do not and yet take a significant amount of
time to execute.

See `my-language --help:tools` for more `--cpusampler` options.

### Creating a Flame Graph from CPUSampler

The histogram output from the CPUSampler can be quite large, making it difficult to
analyze. Additionally, as a flat format it isn't possible to analyze a call graph as that
information simply isn't encoded in the output. A flame graph shows the entire call graph
and its structure makes it considerably simpler to see where the application time is being
spent.

Creating the flame graph is a multi-stage process. First, we need to profile the application
with the JSON formatter:

```bash
$ my-language --cpusampler --cpusampler.SampleInternal --cpusampler.Mode=roots --cpusampler.Output=json -e 'p :hello' > simple-app.json
```

Use the `--cpusampler.SampleInternal=true` option if you want to profile internal sources, such as standard library functions.

Using `--cpusampler.Mode=roots` option will sample each function, including
inlined functions, which is more intuitive when looking at a Flame Graph and can
often give a better idea of what is contributing to the overall method execution
time. However, `--cpusampler.Mode=roots` adds extra overhead, so you might want
to try without too. The default is to not include inlined functions in order to
minimize overhead.

The JSON profiler formatter encodes call graph information that isn't available in the
histogram format. To make a flame graph out of this output, however, we need to transform
it into a format that folds the call stack samples into single lines. This can be done
using [stackcollapse-graalvm.rb](https://github.com/eregon/FlameGraph/blob/graalvm/stackcollapse-graalvm.rb)
from Benoit's fork of FlameGraph.

If you haven't yet, you should clone Benoit's [fork of FlameGraph](https://github.com/eregon/FlameGraph/tree/graalvm)
into the parent directory. Now you can run the script to transform the output and
pipe it into the script that will generate the SVG data:

```bash
$ ../FlameGraph/stackcollapse-graalvm.rb simple-app.json | ../FlameGraph/flamegraph.pl > simple-app.svg
```

At this point, you should open the SVG file in a Chromium-based web browser. Your system
might have a different image manipulation application configured as the default application
for SVG files. While loading the file in such an application make render a graph, it likely
will not handle the interactive components of the flame graph. Firefox may work as well,
but Chromium-based browsers seem to have better support and performance for the flame graph
files as of this writing (Dec. 2018).

### Profiling with Oracle Developer Studio

[Oracle Developer Studio](https://www.oracle.com/technetwork/server-storage/developerstudio/overview/index.html) includes a
[performance analyzer](https://www.oracle.com/technetwork/server-storage/solarisstudio/features/performance-analyzer-2292312.html) that can be used with GraalVM.
Developer Studio can be [downloaded from OTN](https://www.oracle.com/technetwork/server-storage/developerstudio/downloads/index.html)
and the current version at time of writing (12.6) provides a perpetual no-cost license for production use and the development of commercial applications.

Using Developer Studio Performance Analyser is straightforward, include the path to the Developer Studio binaries in your `PATH`
and then prefix your normal command-line with `collect`, for example:

```bash
$ collect js mybenchmark.js
```
 
On completion an "experiment" (.er) directory will have been created containing the profiling data for the command execution, `test.1.er` by default.
To view the profiling results, use the `analyzer` tool:

```bash
$ analyzer test.1.er
```

The `analyzer` GUI allows you to view the captured profiling information in several different ways, for example
the timeline of your application, a flat function list, the call tree, a flame graph etc. There is also a command-line tool,
`er_print` which can be used for outputting the profiling information in textual form, for further analysis.
 
For full details, see the [Performance Analyzer](https://docs.oracle.com/cd/E77782_01/html/E77798/index.html) documentation.
