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
For example, adding `--cpusampler.ShowTiers=true` will show all the compilation tiers encountered during execution as shown bellow.

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

Alternatively `--cpusampler.ShowTiers=0,2` will only show interpreted time and time spent in tier two compiled code, as shown bellow.

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


## Host Profiling using Graal IR Instrumentation

When optimizing interpreter performance, it can be helpful to profile the interpreter itself.
Especially for bytecode interpreters, regular Java sampling tools do not show very useful output as they only show time spent in a method.
More detailed profiling using Java bytecode instrumentation also does not work very well as it breaks partial evaluation and hinders compiler optimizations.
Graal supports instrumentation on the Graal IR level that allows to instrument without significant impact on optimizations performed.

When Graal is configured with Truffle extensions, it provides an additional option `-Dgraal.TruffleHostCounters=true` to enable read, write and invoke profiling.
This option is currently only available on HotSpot in jargraal or libgraal configuration.

On _HotSpot_, the following command line is recommended to print host counters:

```
./latest_graalvm_home/bin/js \
        --jvm --vm.XX:+UnlockExperimentalVMOptions --vm.XX:+UnlockDiagnosticVMOptions --experimental-options \
        --vm.XX:JVMCICounterSize=200000 --vm.XX:+DebugNonSafepoints \
        --vm.Dgraal.TruffleHostCounters=true \
        --engine.Compilation=false \
        ./deltablue.js
        
```

Note the following:
* `--vm.XX:JVMCICounterSize=20000` configures the number of available counters. If the counter size is not big enough, the run will fail. Increase the counter size to resolve problems.
* `--vm.XX:+DebugNonSafepoints` enables additional node source positions, for a more precise output.
* `--vm.Dgraal.TruffleHostCounters=true` enables the host counters
* `--engine.Compilation=false` disables runtime compilation to benchmark interpreter-only performance.
* `--vm.Dgraal.TimedDynamicCounters=10000` allows to print the statistics every 10 seconds. It resets the data each time it is printed. This can be useful to hide some unwanted warmup behavior of the run.
* `--vm.Dgraal.BenchmarkCounterPrintingCutoff=false` prints all the entries in the list and not just the most important ones. Be prepared that this prints a lot of text.
* `--vm.Dgraal.BenchmarkCountersFile=statistics.csv` prints the statistics as machine-readable CSV to a given file.


Here is a sample output of the command line:

```
====== dynamic counters (29284 in total) ======
=========== invoke (dynamic counters):
         23,562,297   1%  com.oracle.truffle.js.nodes.control.AbstractBlockNode.executeVoid(AbstractBlockNode.java:80) (51|Invoke#Indirect#JavaScriptNode.executeVoid)
                          com.oracle.truffle.js.nodes.control.AbstractBlockNode.executeVoid(AbstractBlockNode.java:1)
                          org.graalvm.compiler.truffle.runtime.OptimizedBlockNode.executeGeneric(OptimizedBlockNode.java:78)
                          com.oracle.truffle.js.nodes.control.AbstractBlockNode.execute(AbstractBlockNode.java:75)
                target => com.oracle.truffle.js.nodes.JavaScriptNode.executeVoid(VirtualFrame)
         24,334,186   1%  com.oracle.truffle.js.nodes.control.AbstractBlockNode.executeVoid(AbstractBlockNode.java:80) (43|Invoke#Indirect#JavaScriptNode.executeVoid)
                          com.oracle.truffle.js.nodes.control.AbstractBlockNode.executeVoid(AbstractBlockNode.java:1)
                          org.graalvm.compiler.truffle.runtime.OptimizedBlockNode.executeVoid(OptimizedBlockNode.java:123)
                          com.oracle.truffle.js.nodes.control.AbstractBlockNode.executeVoid(AbstractBlockNode.java:70)
                target => com.oracle.truffle.js.nodes.JavaScriptNode.executeVoid(VirtualFrame)
         27,589,168   1%  com.oracle.truffle.js.nodes.JavaScriptNode.executeVoid(JavaScriptNode.java:192) (5|Invoke#Direct#JavaScriptNode.execute)
                target => com.oracle.truffle.js.nodes.JavaScriptNode.execute(VirtualFrame)
         27,972,248   1%  com.oracle.truffle.js.nodes.control.DiscardResultNode.execute(DiscardResultNode.java:88) (6|Invoke#Indirect#JavaScriptNode.execute)
                target => com.oracle.truffle.js.nodes.JavaScriptNode.execute(VirtualFrame)
         35,332,991   1%  org.graalvm.compiler.truffle.runtime.OptimizedCallTarget.callDirect(OptimizedCallTarget.java:490) (161|Invoke#Direct#OptimizedCallTarget.profileArguments)
                          org.graalvm.compiler.truffle.runtime.OptimizedDirectCallNode.call(OptimizedDirectCallNode.java:68)
                          com.oracle.truffle.js.nodes.function.JSFunctionCallNode$UnboundJSFunctionCacheNode.executeCall(JSFunctionCallNode.java:1314)
                          com.oracle.truffle.js.nodes.function.JSFunctionCallNode.executeCall(JSFunctionCallNode.java:247)
                          com.oracle.truffle.js.nodes.function.JSFunctionCallNode$InvokeNode.execute(JSFunctionCallNode.java:740)
                target => org.graalvm.compiler.truffle.runtime.OptimizedCallTarget.profileArguments(Object[])
         35,332,991   1%  org.graalvm.compiler.truffle.runtime.OptimizedCallTarget.doInvoke(OptimizedCallTarget.java:545) (163|Invoke#Direct#OptimizedCallTarget.callBoundary)
                          org.graalvm.compiler.truffle.runtime.OptimizedCallTarget.callDirect(OptimizedCallTarget.java:491)
                          org.graalvm.compiler.truffle.runtime.OptimizedDirectCallNode.call(OptimizedDirectCallNode.java:68)
                          com.oracle.truffle.js.nodes.function.JSFunctionCallNode$UnboundJSFunctionCacheNode.executeCall(JSFunctionCallNode.java:1314)
                          com.oracle.truffle.js.nodes.function.JSFunctionCallNode.executeCall(JSFunctionCallNode.java:247)
                          com.oracle.truffle.js.nodes.function.JSFunctionCallNode$InvokeNode.execute(JSFunctionCallNode.java:740)
                target => org.graalvm.compiler.truffle.runtime.OptimizedCallTarget.callBoundary(Object[])
         41,372,334   1%  com.oracle.truffle.js.nodes.access.PropertyNode.evaluateTarget(PropertyNode.java:185) (13|Invoke#Indirect#JavaScriptNode.execute)
                          com.oracle.truffle.js.nodes.function.JSFunctionCallNode$InvokeNode.executeTarget(JSFunctionCallNode.java:747)
                          com.oracle.truffle.js.nodes.function.JSFunctionCallNode$InvokeNode.execute(JSFunctionCallNode.java:736)
                target => com.oracle.truffle.js.nodes.JavaScriptNode.execute(VirtualFrame)
         41,372,334   1%  com.oracle.truffle.js.nodes.access.PropertyGetNode.getValueOrUndefined(PropertyGetNode.java:203) (26|Invoke#Direct#PropertyGetNode.getValueOrDefault)
                          com.oracle.truffle.js.nodes.access.PropertyNode.executeWithTarget(PropertyNode.java:152)
                          com.oracle.truffle.js.nodes.access.PropertyNode.executeWithTarget(PropertyNode.java:144)
                          com.oracle.truffle.js.nodes.function.JSFunctionCallNode$InvokeNode.executeFunctionWithTarget(JSFunctionCallNode.java:751)
                          com.oracle.truffle.js.nodes.function.JSFunctionCallNode$InvokeNode.execute(JSFunctionCallNode.java:738)
                target => com.oracle.truffle.js.nodes.access.PropertyGetNode.getValueOrDefault(Object, Object, Object)
         41,491,661   1%  com.oracle.truffle.js.nodes.access.JSWriteCurrentFrameSlotNodeGen.execute_generic4(JSWriteCurrentFrameSlotNodeGen.java:124) (68|Invoke#Indirect#JavaScriptNode.execute)
                          com.oracle.truffle.js.nodes.access.JSWriteCurrentFrameSlotNodeGen.execute(JSWriteCurrentFrameSlotNodeGen.java:43)
                target => com.oracle.truffle.js.nodes.JavaScriptNode.execute(VirtualFrame)
         44,795,491   2%  com.oracle.truffle.js.nodes.binary.DualNode.execute(DualNode.java:118) (6|Invoke#Indirect#JavaScriptNode.executeVoid)
                target => com.oracle.truffle.js.nodes.JavaScriptNode.executeVoid(VirtualFrame)
         44,795,491   2%  com.oracle.truffle.js.nodes.binary.DualNode.execute(DualNode.java:119) (10|Invoke#Indirect#JavaScriptNode.execute)
                target => com.oracle.truffle.js.nodes.JavaScriptNode.execute(VirtualFrame)
         46,476,004   2%  com.oracle.truffle.js.nodes.access.PropertyGetNode.getValueOrDefault(PropertyGetNode.java:471) (123|Invoke#Indirect#PropertyCacheNode$ReceiverCheckNode.accept)
                target => com.oracle.truffle.js.nodes.access.PropertyCacheNode$ReceiverCheckNode.accept(Object)
         56,537,436   2%  com.oracle.truffle.js.nodes.access.PropertyNode.evaluateTarget(PropertyNode.java:185) (4|Invoke#Indirect#JavaScriptNode.execute)
                          com.oracle.truffle.js.nodes.access.PropertyNode.execute(PropertyNode.java:138)
                target => com.oracle.truffle.js.nodes.JavaScriptNode.execute(VirtualFrame)
         56,537,436   2%  com.oracle.truffle.js.nodes.access.PropertyGetNode.getValueOrUndefined(PropertyGetNode.java:203) (10|Invoke#Direct#PropertyGetNode.getValueOrDefault)
                          com.oracle.truffle.js.nodes.access.PropertyNode.executeWithTarget(PropertyNode.java:152)
                          com.oracle.truffle.js.nodes.access.PropertyNode.execute(PropertyNode.java:139)
                target => com.oracle.truffle.js.nodes.access.PropertyGetNode.getValueOrDefault(Object, Object, Object)
         59,860,216   2%  com.oracle.truffle.js.nodes.function.FunctionBodyNode.execute(FunctionBodyNode.java:73) (6|Invoke#Indirect#JavaScriptNode.execute)
                target => com.oracle.truffle.js.nodes.JavaScriptNode.execute(VirtualFrame)
         61,527,369   2%  com.oracle.truffle.js.nodes.function.FunctionRootNode.executeInRealm(FunctionRootNode.java:149) (27|Invoke#Indirect#JavaScriptNode.execute)
                          com.oracle.truffle.js.runtime.JavaScriptRealmBoundaryRootNode.execute(JavaScriptRealmBoundaryRootNode.java:88)
                target => com.oracle.truffle.js.nodes.JavaScriptNode.execute(VirtualFrame)
         63,102,432   3%  org.graalvm.compiler.truffle.runtime.OptimizedCallTarget.callBoundary(OptimizedCallTarget.java:558) (8|Invoke#Direct#OptimizedCallTarget.interpreterCall)
                target => org.graalvm.compiler.truffle.runtime.OptimizedCallTarget.interpreterCall()
         63,102,432   3%  org.graalvm.compiler.truffle.runtime.OptimizedCallTarget.callBoundary(OptimizedCallTarget.java:561) (31|Invoke#Direct#OptimizedCallTarget.profiledPERoot)
                target => org.graalvm.compiler.truffle.runtime.OptimizedCallTarget.profiledPERoot(Object[])
         80,281,300   3%  com.oracle.truffle.js.nodes.access.JSWriteCurrentFrameSlotNodeGen.executeVoid(JSWriteCurrentFrameSlotNodeGen.java:330) (79|Invoke#Direct#JSWriteCurrentFrameSlotNodeGen.execute)
                target => com.oracle.truffle.js.nodes.access.JSWriteCurrentFrameSlotNodeGen.execute(VirtualFrame)
        133,853,675   6%  com.oracle.truffle.js.nodes.access.PropertyGetNode.getValueOrDefault(PropertyGetNode.java:478) (50|Invoke#Indirect#PropertyGetNode$GetCacheNode.getValue)
                target => com.oracle.truffle.js.nodes.access.PropertyGetNode$GetCacheNode.getValue(Object, Object, Object, PropertyGetNode, boolean)
      2,095,091,970 total
=========== read (dynamic counters):
        210,351,750   0%  com.oracle.truffle.api.impl.FrameWithoutBoxing.getIndexedLocals(FrameWithoutBoxing.java:612) (308|Read)
                          com.oracle.truffle.api.impl.FrameWithoutBoxing.getObject(FrameWithoutBoxing.java:626)
                          com.oracle.truffle.js.nodes.access.JSReadCurrentFrameSlotNode.doObject(JSReadFrameSlotNode.java:236)
                          com.oracle.truffle.js.nodes.access.JSReadCurrentFrameSlotNodeGen.execute(JSReadCurrentFrameSlotNodeGen.java:45)
        210,351,750   0%  org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.loadHubOrNullIntrinsic(HotSpotReplacementsUtil.java) (309|Read)
                          org.graalvm.compiler.hotspot.replacements.InstanceOfSnippets.instanceofExact(InstanceOfSnippets.java:136)
                          org.graalvm.compiler.hotspot.replacements.InstanceOfSnippets.instanceofExact(InstanceOfSnippets.java)
                          com.oracle.truffle.api.impl.FrameWithoutBoxing.getIndexedLocals(FrameWithoutBoxing.java:612)
                          com.oracle.truffle.api.impl.FrameWithoutBoxing.getObject(FrameWithoutBoxing.java:626)
                          com.oracle.truffle.js.nodes.access.JSReadCurrentFrameSlotNode.doObject(JSReadFrameSlotNode.java:236)
                          com.oracle.truffle.js.nodes.access.JSReadCurrentFrameSlotNodeGen.execute(JSReadCurrentFrameSlotNodeGen.java:45)
        210,351,750   0%  com.oracle.truffle.api.impl.FrameWithoutBoxing.getIndexedTags(FrameWithoutBoxing.java:620) (310|Read)
                          com.oracle.truffle.api.impl.FrameWithoutBoxing.getTag(FrameWithoutBoxing.java:491)
                          com.oracle.truffle.api.impl.FrameWithoutBoxing.isObject(FrameWithoutBoxing.java:758)
                          com.oracle.truffle.js.nodes.access.JSReadCurrentFrameSlotNodeGen.execute(JSReadCurrentFrameSlotNodeGen.java:44)
        210,351,750   0%  com.oracle.truffle.api.impl.FrameWithoutBoxing.getTag(FrameWithoutBoxing.java:491) (311|Read)
                          com.oracle.truffle.api.impl.FrameWithoutBoxing.isObject(FrameWithoutBoxing.java:758)
                          com.oracle.truffle.js.nodes.access.JSReadCurrentFrameSlotNodeGen.execute(JSReadCurrentFrameSlotNodeGen.java:44)
        210,351,750   0%  com.oracle.truffle.api.impl.FrameWithoutBoxing.getTag(FrameWithoutBoxing.java:491) (312|Read)
                          com.oracle.truffle.api.impl.FrameWithoutBoxing.isObject(FrameWithoutBoxing.java:758)
                          com.oracle.truffle.js.nodes.access.JSReadCurrentFrameSlotNodeGen.execute(JSReadCurrentFrameSlotNodeGen.java:44)
        210,351,750   0%  sun.misc.Unsafe.getObject(Unsafe.java) (92|Read)
                          com.oracle.truffle.api.impl.FrameWithoutBoxing.unsafeGetObject(FrameWithoutBoxing.java:569)
                          com.oracle.truffle.api.impl.FrameWithoutBoxing.getObject(FrameWithoutBoxing.java:626)
                          com.oracle.truffle.js.nodes.access.JSReadCurrentFrameSlotNode.doObject(JSReadFrameSlotNode.java:236)
                          com.oracle.truffle.js.nodes.access.JSReadCurrentFrameSlotNodeGen.execute(JSReadCurrentFrameSlotNodeGen.java:45)
        212,659,363   0%  com.oracle.truffle.js.nodes.access.JSReadCurrentFrameSlotNodeGen.execute(JSReadCurrentFrameSlotNodeGen.java:27) (299|Read)
        212,659,363   0%  org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.loadHubOrNullIntrinsic(HotSpotReplacementsUtil.java) (300|Read)
                          org.graalvm.compiler.hotspot.replacements.InstanceOfSnippets.instanceofExact(InstanceOfSnippets.java:136)
                          org.graalvm.compiler.hotspot.replacements.InstanceOfSnippets.instanceofExact(InstanceOfSnippets.java)
                          com.oracle.truffle.js.nodes.access.JSReadCurrentFrameSlotNodeGen.execute(JSReadCurrentFrameSlotNodeGen.java:44)
        212,659,363   0%  com.oracle.truffle.js.nodes.access.JSReadCurrentFrameSlotNodeGen.execute(JSReadCurrentFrameSlotNodeGen.java:34) (301|Read)
        237,414,674   0%  no-location in com.oracle.truffle.js.nodes.access.PropertyGetNode$ObjectPropertyGetNode.getValue(Object, Object, Object, PropertyGetNode, boolean)
     26,164,670,942 total
=========== write (dynamic counters):
         35,332,991   1%  org.graalvm.compiler.truffle.runtime.OptimizedDirectCallNode.incrementCallCount(OptimizedDirectCallNode.java:162) (374|Write)
                          org.graalvm.compiler.truffle.runtime.OptimizedDirectCallNode.call(OptimizedDirectCallNode.java:62)
                          com.oracle.truffle.js.nodes.function.JSFunctionCallNode$UnboundJSFunctionCacheNode.executeCall(JSFunctionCallNode.java:1314)
                          com.oracle.truffle.js.nodes.function.JSFunctionCallNode.executeCall(JSFunctionCallNode.java:247)
                          com.oracle.truffle.js.nodes.function.JSFunctionCallNode$InvokeNode.execute(JSFunctionCallNode.java:740)
         38,394,646   1%  java.util.Arrays.fill(Arrays.java:3638) (102|Write)
                          com.oracle.truffle.api.impl.FrameWithoutBoxing.<init>(FrameWithoutBoxing.java:166)
         62,186,320   2%  org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.writeTlabTop(HotSpotReplacementsUtil.java:267) (575|Write)
                          org.graalvm.compiler.hotspot.replacements.HotSpotAllocationSnippets.writeTlabTop(HotSpotAllocationSnippets.java:484)
                          org.graalvm.compiler.replacements.AllocationSnippets.allocateArrayImpl(AllocationSnippets.java:94)
                          org.graalvm.compiler.hotspot.replacements.HotSpotAllocationSnippets.allocateArray(HotSpotAllocationSnippets.java:168)
                          org.graalvm.compiler.hotspot.replacements.HotSpotAllocationSnippets.allocateArray(HotSpotAllocationSnippets.java)
                          com.oracle.truffle.api.impl.FrameWithoutBoxing.<init>(FrameWithoutBoxing.java:168)
         62,186,320   2%  org.graalvm.compiler.replacements.AllocationSnippets.formatArray(AllocationSnippets.java:294) (576|Write)
                          org.graalvm.compiler.replacements.AllocationSnippets.allocateArrayImpl(AllocationSnippets.java:96)
                          org.graalvm.compiler.hotspot.replacements.HotSpotAllocationSnippets.allocateArray(HotSpotAllocationSnippets.java:168)
                          org.graalvm.compiler.hotspot.replacements.HotSpotAllocationSnippets.allocateArray(HotSpotAllocationSnippets.java)
                          com.oracle.truffle.api.impl.FrameWithoutBoxing.<init>(FrameWithoutBoxing.java:168)
         62,186,320   2%  org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.initializeObjectHeader(HotSpotReplacementsUtil.java:465) (577|Write)
                          org.graalvm.compiler.hotspot.replacements.HotSpotAllocationSnippets.initializeObjectHeader(HotSpotAllocationSnippets.java:429)
                          org.graalvm.compiler.replacements.AllocationSnippets.formatArray(AllocationSnippets.java:297)
                          org.graalvm.compiler.replacements.AllocationSnippets.allocateArrayImpl(AllocationSnippets.java:96)
                          org.graalvm.compiler.hotspot.replacements.HotSpotAllocationSnippets.allocateArray(HotSpotAllocationSnippets.java:168)
                          org.graalvm.compiler.hotspot.replacements.HotSpotAllocationSnippets.allocateArray(HotSpotAllocationSnippets.java)
                          com.oracle.truffle.api.impl.FrameWithoutBoxing.<init>(FrameWithoutBoxing.java:168)
         62,186,320   2%  org.graalvm.compiler.nodes.extended.StoreHubNode.write(StoreHubNode.java) (580|Write)
                          org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.initializeObjectHeader(HotSpotReplacementsUtil.java:466)
                          org.graalvm.compiler.hotspot.replacements.HotSpotAllocationSnippets.initializeObjectHeader(HotSpotAllocationSnippets.java:429)
                          org.graalvm.compiler.replacements.AllocationSnippets.formatArray(AllocationSnippets.java:297)
                          org.graalvm.compiler.replacements.AllocationSnippets.allocateArrayImpl(AllocationSnippets.java:96)
                          org.graalvm.compiler.hotspot.replacements.HotSpotAllocationSnippets.allocateArray(HotSpotAllocationSnippets.java:168)
                          org.graalvm.compiler.hotspot.replacements.HotSpotAllocationSnippets.allocateArray(HotSpotAllocationSnippets.java)
                          com.oracle.truffle.api.impl.FrameWithoutBoxing.<init>(FrameWithoutBoxing.java:168)
         62,186,458   2%  org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.writeTlabTop(HotSpotReplacementsUtil.java:267) (298|Write)
                          org.graalvm.compiler.hotspot.replacements.HotSpotAllocationSnippets.writeTlabTop(HotSpotAllocationSnippets.java:484)
                          org.graalvm.compiler.replacements.AllocationSnippets.allocateArrayImpl(AllocationSnippets.java:94)
                          org.graalvm.compiler.hotspot.replacements.HotSpotAllocationSnippets.allocateArray(HotSpotAllocationSnippets.java:168)
                          org.graalvm.compiler.hotspot.replacements.HotSpotAllocationSnippets.allocateArray(HotSpotAllocationSnippets.java)
                          com.oracle.truffle.api.impl.FrameWithoutBoxing.<init>(FrameWithoutBoxing.java:164)
         62,186,458   2%  org.graalvm.compiler.replacements.AllocationSnippets.formatArray(AllocationSnippets.java:294) (299|Write)
                          org.graalvm.compiler.replacements.AllocationSnippets.allocateArrayImpl(AllocationSnippets.java:96)
                          org.graalvm.compiler.hotspot.replacements.HotSpotAllocationSnippets.allocateArray(HotSpotAllocationSnippets.java:168)
                          org.graalvm.compiler.hotspot.replacements.HotSpotAllocationSnippets.allocateArray(HotSpotAllocationSnippets.java)
                          com.oracle.truffle.api.impl.FrameWithoutBoxing.<init>(FrameWithoutBoxing.java:164)
         62,186,458   2%  org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.initializeObjectHeader(HotSpotReplacementsUtil.java:465) (300|Write)
                          org.graalvm.compiler.hotspot.replacements.HotSpotAllocationSnippets.initializeObjectHeader(HotSpotAllocationSnippets.java:429)
                          org.graalvm.compiler.replacements.AllocationSnippets.formatArray(AllocationSnippets.java:297)
                          org.graalvm.compiler.replacements.AllocationSnippets.allocateArrayImpl(AllocationSnippets.java:96)
                          org.graalvm.compiler.hotspot.replacements.HotSpotAllocationSnippets.allocateArray(HotSpotAllocationSnippets.java:168)
                          org.graalvm.compiler.hotspot.replacements.HotSpotAllocationSnippets.allocateArray(HotSpotAllocationSnippets.java)
                          com.oracle.truffle.api.impl.FrameWithoutBoxing.<init>(FrameWithoutBoxing.java:164)
         62,186,458   2%  org.graalvm.compiler.nodes.extended.StoreHubNode.write(StoreHubNode.java) (303|Write)
                          org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.initializeObjectHeader(HotSpotReplacementsUtil.java:466)
                          org.graalvm.compiler.hotspot.replacements.HotSpotAllocationSnippets.initializeObjectHeader(HotSpotAllocationSnippets.java:429)
                          org.graalvm.compiler.replacements.AllocationSnippets.formatArray(AllocationSnippets.java:297)
                          org.graalvm.compiler.replacements.AllocationSnippets.allocateArrayImpl(AllocationSnippets.java:96)
                          org.graalvm.compiler.hotspot.replacements.HotSpotAllocationSnippets.allocateArray(HotSpotAllocationSnippets.java:168)
                          org.graalvm.compiler.hotspot.replacements.HotSpotAllocationSnippets.allocateArray(HotSpotAllocationSnippets.java)
                          com.oracle.truffle.api.impl.FrameWithoutBoxing.<init>(FrameWithoutBoxing.java:164)
         62,186,794   2%  org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.writeTlabTop(HotSpotReplacementsUtil.java:267) (645|Write)
                          org.graalvm.compiler.hotspot.replacements.HotSpotAllocationSnippets.writeTlabTop(HotSpotAllocationSnippets.java:484)
                          org.graalvm.compiler.replacements.AllocationSnippets.allocateArrayImpl(AllocationSnippets.java:94)
                          org.graalvm.compiler.hotspot.replacements.HotSpotAllocationSnippets.allocateArray(HotSpotAllocationSnippets.java:168)
                          org.graalvm.compiler.hotspot.replacements.HotSpotAllocationSnippets.allocateArray(HotSpotAllocationSnippets.java)
                          com.oracle.truffle.api.impl.FrameWithoutBoxing.<init>(FrameWithoutBoxing.java:169)
         62,186,794   2%  org.graalvm.compiler.replacements.AllocationSnippets.formatArray(AllocationSnippets.java:294) (646|Write)
                          org.graalvm.compiler.replacements.AllocationSnippets.allocateArrayImpl(AllocationSnippets.java:96)
                          org.graalvm.compiler.hotspot.replacements.HotSpotAllocationSnippets.allocateArray(HotSpotAllocationSnippets.java:168)
                          org.graalvm.compiler.hotspot.replacements.HotSpotAllocationSnippets.allocateArray(HotSpotAllocationSnippets.java)
                          com.oracle.truffle.api.impl.FrameWithoutBoxing.<init>(FrameWithoutBoxing.java:169)
         62,186,794   2%  org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.initializeObjectHeader(HotSpotReplacementsUtil.java:465) (647|Write)
                          org.graalvm.compiler.hotspot.replacements.HotSpotAllocationSnippets.initializeObjectHeader(HotSpotAllocationSnippets.java:429)
                          org.graalvm.compiler.replacements.AllocationSnippets.formatArray(AllocationSnippets.java:297)
                          org.graalvm.compiler.replacements.AllocationSnippets.allocateArrayImpl(AllocationSnippets.java:96)
                          org.graalvm.compiler.hotspot.replacements.HotSpotAllocationSnippets.allocateArray(HotSpotAllocationSnippets.java:168)
                          org.graalvm.compiler.hotspot.replacements.HotSpotAllocationSnippets.allocateArray(HotSpotAllocationSnippets.java)
                          com.oracle.truffle.api.impl.FrameWithoutBoxing.<init>(FrameWithoutBoxing.java:169)
         62,186,794   2%  org.graalvm.compiler.nodes.extended.StoreHubNode.write(StoreHubNode.java) (650|Write)
                          org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.initializeObjectHeader(HotSpotReplacementsUtil.java:466)
                          org.graalvm.compiler.hotspot.replacements.HotSpotAllocationSnippets.initializeObjectHeader(HotSpotAllocationSnippets.java:429)
                          org.graalvm.compiler.replacements.AllocationSnippets.formatArray(AllocationSnippets.java:297)
                          org.graalvm.compiler.replacements.AllocationSnippets.allocateArrayImpl(AllocationSnippets.java:96)
                          org.graalvm.compiler.hotspot.replacements.HotSpotAllocationSnippets.allocateArray(HotSpotAllocationSnippets.java:168)
                          org.graalvm.compiler.hotspot.replacements.HotSpotAllocationSnippets.allocateArray(HotSpotAllocationSnippets.java)
                          com.oracle.truffle.api.impl.FrameWithoutBoxing.<init>(FrameWithoutBoxing.java:169)
         62,186,797   2%  org.graalvm.compiler.replacements.AllocationSnippets.fillMemoryAligned(AllocationSnippets.java:231) (651|Write)
                          org.graalvm.compiler.replacements.AllocationSnippets.fillMemory(AllocationSnippets.java:181)
                          org.graalvm.compiler.replacements.AllocationSnippets.zeroMemory(AllocationSnippets.java:157)
                          org.graalvm.compiler.replacements.AllocationSnippets.formatArray(AllocationSnippets.java:299)
                          org.graalvm.compiler.replacements.AllocationSnippets.allocateArrayImpl(AllocationSnippets.java:96)
                          org.graalvm.compiler.hotspot.replacements.HotSpotAllocationSnippets.allocateArray(HotSpotAllocationSnippets.java:168)
                          org.graalvm.compiler.hotspot.replacements.HotSpotAllocationSnippets.allocateArray(HotSpotAllocationSnippets.java)
                          com.oracle.truffle.api.impl.FrameWithoutBoxing.<init>(FrameWithoutBoxing.java:169)
         62,188,130   2%  java.util.Arrays.fill(Arrays.java:3638) (95|Write)
                          com.oracle.truffle.api.impl.FrameWithoutBoxing.<init>(FrameWithoutBoxing.java:166)
         65,369,226   2%  com.oracle.truffle.api.impl.FrameWithoutBoxing.<init>(FrameWithoutBoxing.java:185) (112|Write)
         65,369,226   2%  com.oracle.truffle.api.impl.FrameWithoutBoxing.<init>(FrameWithoutBoxing.java:186) (115|Write)
         65,369,226   2%  com.oracle.truffle.api.impl.FrameWithoutBoxing.<init>(FrameWithoutBoxing.java:187) (118|Write)
         65,369,226   2%  com.oracle.truffle.api.impl.FrameWithoutBoxing.<init>(FrameWithoutBoxing.java:188) (120|Write)
         65,369,226   2%  com.oracle.truffle.api.impl.FrameWithoutBoxing.<init>(FrameWithoutBoxing.java:189) (123|Write)
         65,369,226   2%  com.oracle.truffle.api.impl.FrameWithoutBoxing.<init>(FrameWithoutBoxing.java:190) (126|Write)
         65,369,226   2%  com.oracle.truffle.api.impl.FrameWithoutBoxing.<init>(FrameWithoutBoxing.java:191) (130|Write)
         65,369,226   2%  com.oracle.truffle.api.impl.FrameWithoutBoxing.<init>(FrameWithoutBoxing.java:192) (134|Write)
         65,369,226   2%  com.oracle.truffle.api.impl.FrameWithoutBoxing.<init>(FrameWithoutBoxing.java:193) (137|Write)
         76,186,144   2%  org.graalvm.compiler.replacements.AllocationSnippets.fillMemoryAligned(AllocationSnippets.java:231) (304|Write)
                          org.graalvm.compiler.replacements.AllocationSnippets.fillMemory(AllocationSnippets.java:181)
                          org.graalvm.compiler.replacements.AllocationSnippets.zeroMemory(AllocationSnippets.java:157)
                          org.graalvm.compiler.replacements.AllocationSnippets.formatArray(AllocationSnippets.java:299)
                          org.graalvm.compiler.replacements.AllocationSnippets.allocateArrayImpl(AllocationSnippets.java:96)
                          org.graalvm.compiler.hotspot.replacements.HotSpotAllocationSnippets.allocateArray(HotSpotAllocationSnippets.java:168)
                          org.graalvm.compiler.hotspot.replacements.HotSpotAllocationSnippets.allocateArray(HotSpotAllocationSnippets.java)
                          com.oracle.truffle.api.impl.FrameWithoutBoxing.<init>(FrameWithoutBoxing.java:164)
         80,584,819   2%  com.oracle.truffle.api.impl.FrameWithoutBoxing.verifyIndexedSet(FrameWithoutBoxing.java:736) (170|Write)
                          com.oracle.truffle.api.impl.FrameWithoutBoxing.setObject(FrameWithoutBoxing.java:631)
                          com.oracle.truffle.js.nodes.access.JSWriteCurrentFrameSlotNode.doObject(JSWriteFrameSlotNode.java:259)
                          com.oracle.truffle.js.nodes.access.JSWriteCurrentFrameSlotNodeGen.execute_generic4(JSWriteCurrentFrameSlotNodeGen.java:157)
                          com.oracle.truffle.js.nodes.access.JSWriteCurrentFrameSlotNodeGen.execute(JSWriteCurrentFrameSlotNodeGen.java:43)
         80,584,819   2%  sun.misc.Unsafe.putObject(Unsafe.java) (176|Write)
                          com.oracle.truffle.api.impl.FrameWithoutBoxing.unsafePutObject(FrameWithoutBoxing.java:579)
                          com.oracle.truffle.api.impl.FrameWithoutBoxing.setObject(FrameWithoutBoxing.java:632)
                          com.oracle.truffle.js.nodes.access.JSWriteCurrentFrameSlotNode.doObject(JSWriteFrameSlotNode.java:259)
                          com.oracle.truffle.js.nodes.access.JSWriteCurrentFrameSlotNodeGen.execute_generic4(JSWriteCurrentFrameSlotNodeGen.java:157)
                          com.oracle.truffle.js.nodes.access.JSWriteCurrentFrameSlotNodeGen.execute(JSWriteCurrentFrameSlotNodeGen.java:43)
        100,578,686   3%  org.graalvm.compiler.replacements.AllocationSnippets.fillMemoryAligned(AllocationSnippets.java:231) (581|Write)
                          org.graalvm.compiler.replacements.AllocationSnippets.fillMemory(AllocationSnippets.java:181)
                          org.graalvm.compiler.replacements.AllocationSnippets.zeroMemory(AllocationSnippets.java:157)
                          org.graalvm.compiler.replacements.AllocationSnippets.formatArray(AllocationSnippets.java:299)
                          org.graalvm.compiler.replacements.AllocationSnippets.allocateArrayImpl(AllocationSnippets.java:96)
                          org.graalvm.compiler.hotspot.replacements.HotSpotAllocationSnippets.allocateArray(HotSpotAllocationSnippets.java:168)
                          org.graalvm.compiler.hotspot.replacements.HotSpotAllocationSnippets.allocateArray(HotSpotAllocationSnippets.java)
                          com.oracle.truffle.api.impl.FrameWithoutBoxing.<init>(FrameWithoutBoxing.java:168)
      2,882,562,695 total
```

## Guest Profiling using Graal IR Instrumentation

The same instrumentation for host code also works for runtime compiled guest code.
Please refer to host profiling for additional details.

On _HotSpot_, the following command line is recommended to print guest counters:

```
./latest_graalvm_home/bin/js \
        --jvm --vm.XX:+UnlockDiagnosticVMOptions \
        --vm.XX:JVMCICounterSize=200000 --vm.XX:+DebugNonSafepoints \
        --vm.Dgraal.TruffleGuestCounters=true \
        ./deltablue.js
        
```

Sample output printed after the execution is completed. 
Note that in addition to host stack traces, we now also see guest AST nodes with guest application source locations.
        
====== dynamic counters (19195 in total) ======
=========== invoke (dynamic counters):

           ... omitted output....
           
            719,179   1%  org.graalvm.compiler.truffle.runtime.OptimizedCallTarget.doInvoke(OptimizedCallTarget.java:545) (227|Invoke#Direct#OptimizedCallTarget.callBoundary)
                          org.graalvm.compiler.truffle.runtime.OptimizedCallTarget.callDirect(OptimizedCallTarget.java:491)
                          org.graalvm.compiler.truffle.runtime.OptimizedDirectCallNode.call(OptimizedDirectCallNode.java:68)
                          com.oracle.truffle.js.nodes.function.JSFunctionCallNode$UnboundJSFunctionCacheNode.executeCall(JSFunctionCallNode.java:1314)
                          com.oracle.truffle.js.nodes.function.JSFunctionCallNode.executeCall(JSFunctionCallNode.java:247)
                          com.oracle.truffle.js.nodes.function.JSFunctionCallNode$InvokeNode.execute(JSFunctionCallNode.java:740)
                          com.oracle.truffle.js.nodes.control.DiscardResultNode.execute(DiscardResultNode.java:88)
                          com.oracle.truffle.js.nodes.binary.DualNode.execute(DualNode.java:119)
                          com.oracle.truffle.js.nodes.function.FunctionBodyNode.execute(FunctionBodyNode.java:73)
                          com.oracle.truffle.js.nodes.function.FunctionRootNode.executeInRealm(FunctionRootNode.java:149)
                          com.oracle.truffle.js.runtime.JavaScriptRealmBoundaryRootNode.execute(JavaScriptRealmBoundaryRootNode.java:88)
                          org.graalvm.compiler.truffle.runtime.OptimizedCallTarget.executeRootNode(OptimizedCallTarget.java:656)
                          org.graalvm.compiler.truffle.runtime.OptimizedCallTarget.profiledPERoot(OptimizedCallTarget.java:628)
                target => org.graalvm.compiler.truffle.runtime.OptimizedCallTarget.callBoundary(Object[])
                          Source AST Nodes:
                            <js> ScaleConstraint.execute(deltablue.js:1055) (19|org.graalvm.compiler.truffle.runtime.OptimizedDirectCallNode)
                            <js> :anonymous(deltablue.js:516) (16|com.oracle.truffle.js.nodes.function.JSFunctionCallNode$FunctionInstanceCacheNode)
                            <js> :anonymous(deltablue.js:516) (6|com.oracle.truffle.js.nodes.function.JSFunctionCallNode$Invoke0Node)
                            <js> :anonymous(deltablue.js:516) (5|com.oracle.truffle.js.nodes.control.DiscardResultNode)
                            <js> :anonymous(deltablue.js:516) (2|com.oracle.truffle.js.nodes.binary.DualNode)
                            <js> :anonymous(deltablue.js:516) (1|com.oracle.truffle.js.nodes.function.FunctionBodyNode)
                            <js> :anonymous(deltablue.js:516) (0|com.oracle.truffle.js.nodes.function.FunctionRootNode)
          6,000,060  15%  org.graalvm.compiler.truffle.runtime.OptimizedCallTarget.doInvoke(OptimizedCallTarget.java:545) (264|Invoke#Direct#OptimizedCallTarget.callBoundary)
                          org.graalvm.compiler.truffle.runtime.OptimizedCallTarget.callDirect(OptimizedCallTarget.java:491)
                          org.graalvm.compiler.truffle.runtime.OptimizedDirectCallNode.call(OptimizedDirectCallNode.java:68)
                          com.oracle.truffle.js.nodes.function.JSFunctionCallNode$UnboundJSFunctionCacheNode.executeCall(JSFunctionCallNode.java:1314)
                          com.oracle.truffle.js.nodes.function.JSFunctionCallNode.executeCall(JSFunctionCallNode.java:247)
                          com.oracle.truffle.js.nodes.function.JSFunctionCallNode$CallNode.execute(JSFunctionCallNode.java:537)
                          com.oracle.truffle.js.nodes.JavaScriptNode.executeVoid(JavaScriptNode.java:192)
                          com.oracle.truffle.js.nodes.control.AbstractBlockNode.executeVoid(AbstractBlockNode.java:80)
                          com.oracle.truffle.js.nodes.control.AbstractBlockNode.executeVoid(AbstractBlockNode.java:1)
                          org.graalvm.compiler.truffle.runtime.OptimizedBlockNode.executeVoid(OptimizedBlockNode.java:123)
                          com.oracle.truffle.js.nodes.control.AbstractBlockNode.executeVoid(AbstractBlockNode.java:70)
                          com.oracle.truffle.js.nodes.control.AbstractRepeatingNode.executeBody(AbstractRepeatingNode.java:67)
                          com.oracle.truffle.js.nodes.control.WhileNode$WhileDoRepeatingNode.executeRepeating(WhileNode.java:238)
                          com.oracle.truffle.api.nodes.RepeatingNode.executeRepeatingWithValue(RepeatingNode.java:112)
                          org.graalvm.compiler.truffle.runtime.OptimizedOSRLoopNode.execute(OptimizedOSRLoopNode.java:124)
                          com.oracle.truffle.js.nodes.control.WhileNode.executeVoid(WhileNode.java:181)
                          com.oracle.truffle.js.nodes.control.AbstractBlockNode.executeVoid(AbstractBlockNode.java:80)
                          com.oracle.truffle.js.nodes.control.AbstractBlockNode.executeVoid(AbstractBlockNode.java:1)
                          org.graalvm.compiler.truffle.runtime.OptimizedBlockNode.executeVoid(OptimizedBlockNode.java:123)
                          com.oracle.truffle.js.nodes.control.AbstractBlockNode.executeVoid(AbstractBlockNode.java:70)
                          com.oracle.truffle.js.nodes.control.VoidBlockNode.execute(VoidBlockNode.java:61)
                          com.oracle.truffle.js.nodes.control.AbstractBlockNode.executeGeneric(AbstractBlockNode.java:85)
                          com.oracle.truffle.js.nodes.control.AbstractBlockNode.executeGeneric(AbstractBlockNode.java:1)
                          org.graalvm.compiler.truffle.runtime.OptimizedBlockNode.executeGeneric(OptimizedBlockNode.java:80)
                          com.oracle.truffle.js.nodes.control.AbstractBlockNode.execute(AbstractBlockNode.java:75)
                          com.oracle.truffle.js.nodes.function.FunctionBodyNode.execute(FunctionBodyNode.java:73)
                          com.oracle.truffle.js.nodes.function.FunctionRootNode.executeInRealm(FunctionRootNode.java:149)
                          com.oracle.truffle.js.runtime.JavaScriptRealmBoundaryRootNode.execute(JavaScriptRealmBoundaryRootNode.java:88)
                          org.graalvm.compiler.truffle.runtime.OptimizedCallTarget.executeRootNode(OptimizedCallTarget.java:656)
                          org.graalvm.compiler.truffle.runtime.OptimizedCallTarget.profiledPERoot(OptimizedCallTarget.java:628)
                target => org.graalvm.compiler.truffle.runtime.OptimizedCallTarget.callBoundary(Object[])
                          Source AST Nodes:
                            <js> :anonymous(deltablue.js:516) (42|org.graalvm.compiler.truffle.runtime.OptimizedDirectCallNode)
                            <js> Vector.forEach(deltablue.js:90) (41|com.oracle.truffle.js.nodes.function.JSFunctionCallNode$UnboundFunctionDataCacheNode)
                            <js> Vector.forEach(deltablue.js:90) (28|com.oracle.truffle.js.nodes.function.JSFunctionCallNode$Call1Node)
                            <js> Vector.forEach(deltablue.js:89) (26|com.oracle.truffle.js.nodes.control.VoidBlockNode)
                            <js> Vector.forEach(deltablue.js:89) (27|org.graalvm.compiler.truffle.runtime.OptimizedBlockNode)
                            <js> Vector.forEach(deltablue.js:89) (26|com.oracle.truffle.js.nodes.control.VoidBlockNode)
                            <js> Vector.forEach(deltablue.js:89) (18|com.oracle.truffle.js.nodes.control.WhileNode$WhileDoRepeatingNode)
                            <js> Vector.forEach(deltablue.js:89) (17|org.graalvm.compiler.truffle.runtime.OptimizedOSRLoopNode$OptimizedDefaultOSRLoopNode)
                            <js> Vector.forEach(deltablue.js:89) (16|com.oracle.truffle.js.nodes.control.WhileNode)
                            <js> Vector.forEach(deltablue.js:88) (8|com.oracle.truffle.js.nodes.control.VoidBlockNode)
                            <js> Vector.forEach(deltablue.js:88) (9|org.graalvm.compiler.truffle.runtime.OptimizedBlockNode)
                            <js> Vector.forEach(deltablue.js:88) (8|com.oracle.truffle.js.nodes.control.VoidBlockNode)
                            <js> Vector.forEach(deltablue.js:88) (2|com.oracle.truffle.js.nodes.control.ExprBlockNode)
                            <js> Vector.forEach(deltablue.js:88) (3|org.graalvm.compiler.truffle.runtime.OptimizedBlockNode)
                            <js> Vector.forEach(deltablue.js:88) (2|com.oracle.truffle.js.nodes.control.ExprBlockNode)
                            <js> Vector.forEach(deltablue.js:88) (1|com.oracle.truffle.js.nodes.function.FunctionBodyNode)
                            <js> Vector.forEach(deltablue.js:88) (0|com.oracle.truffle.js.nodes.function.FunctionRootNode)
         37,842,523 total
=========== read (dynamic counters):

           ... omitted output....
           
          7,391,766   0%  sun.misc.Unsafe.getLong(Unsafe.java) (326|Read)
                          com.oracle.truffle.object.UnsafeAccess.unsafeGetLong(UnsafeAccess.java:67)
                          com.oracle.truffle.object.CoreLocations$DynamicLongFieldLocation.getLong(CoreLocations.java:997)
                          com.oracle.truffle.object.CoreLocations$PrimitiveLocationDecorator.getLongInternal(CoreLocations.java:734)
                          com.oracle.truffle.object.CoreLocations$IntLocationDecorator.getInt(CoreLocations.java:793)
                          com.oracle.truffle.js.nodes.access.PropertyGetNode$IntPropertyGetNode.getValueInt(PropertyGetNode.java:691)
                          com.oracle.truffle.js.nodes.access.PropertyGetNode.getValueInt(PropertyGetNode.java:250)
                          com.oracle.truffle.js.nodes.access.PropertyNode.executeInt(PropertyNode.java:166)
                          com.oracle.truffle.js.nodes.access.PropertyNode.executeInt(PropertyNode.java:158)
                          com.oracle.truffle.js.nodes.binary.JSLessThanNodeGen.executeBoolean_int_int0(JSLessThanNodeGen.java:186)
                          com.oracle.truffle.js.nodes.binary.JSLessThanNodeGen.executeBoolean(JSLessThanNodeGen.java:159)
                          com.oracle.truffle.js.nodes.control.StatementNode.executeConditionAsBoolean(StatementNode.java:58)
                          com.oracle.truffle.js.nodes.control.AbstractRepeatingNode.executeCondition(AbstractRepeatingNode.java:63)
                          com.oracle.truffle.js.nodes.control.WhileNode$WhileDoRepeatingNode.executeRepeating(WhileNode.java:237)
                          com.oracle.truffle.api.nodes.RepeatingNode.executeRepeatingWithValue(RepeatingNode.java:112)
                          org.graalvm.compiler.truffle.runtime.OptimizedOSRLoopNode.execute(OptimizedOSRLoopNode.java:124)
                          com.oracle.truffle.js.nodes.control.WhileNode.executeVoid(WhileNode.java:181)
                          com.oracle.truffle.js.nodes.control.AbstractBlockNode.executeVoid(AbstractBlockNode.java:80)
                          com.oracle.truffle.js.nodes.control.AbstractBlockNode.executeVoid(AbstractBlockNode.java:1)
                          org.graalvm.compiler.truffle.runtime.OptimizedBlockNode.executeVoid(OptimizedBlockNode.java:123)
                          com.oracle.truffle.js.nodes.control.AbstractBlockNode.executeVoid(AbstractBlockNode.java:70)
                          com.oracle.truffle.js.nodes.control.VoidBlockNode.execute(VoidBlockNode.java:61)
                          com.oracle.truffle.js.nodes.control.AbstractBlockNode.executeGeneric(AbstractBlockNode.java:85)
                          com.oracle.truffle.js.nodes.control.AbstractBlockNode.executeGeneric(AbstractBlockNode.java:1)
                          org.graalvm.compiler.truffle.runtime.OptimizedBlockNode.executeGeneric(OptimizedBlockNode.java:80)
                          com.oracle.truffle.js.nodes.control.AbstractBlockNode.execute(AbstractBlockNode.java:75)
                          com.oracle.truffle.js.nodes.function.FunctionBodyNode.execute(FunctionBodyNode.java:73)
                          com.oracle.truffle.js.nodes.function.FunctionRootNode.executeInRealm(FunctionRootNode.java:149)
                          com.oracle.truffle.js.runtime.JavaScriptRealmBoundaryRootNode.execute(JavaScriptRealmBoundaryRootNode.java:88)
                          org.graalvm.compiler.truffle.runtime.OptimizedCallTarget.executeRootNode(OptimizedCallTarget.java:656)
                          org.graalvm.compiler.truffle.runtime.OptimizedCallTarget.profiledPERoot(OptimizedCallTarget.java:628)
                          Source AST Nodes:
                            <js> Vector.forEach(deltablue.js:89) (24|com.oracle.truffle.js.nodes.access.PropertyGetNode$IntPropertyGetNode)
                            <js> Vector.forEach(deltablue.js:89) (23|com.oracle.truffle.js.nodes.access.PropertyGetNode)
                            <js> Vector.forEach(deltablue.js:89) (21|com.oracle.truffle.js.nodes.access.PropertyNode)
                            <js> Vector.forEach(deltablue.js:89) (19|com.oracle.truffle.js.nodes.binary.JSLessThanNodeGen)
                            <js> Vector.forEach(deltablue.js:89) (18|com.oracle.truffle.js.nodes.control.WhileNode$WhileDoRepeatingNode)
                            <js> Vector.forEach(deltablue.js:89) (17|org.graalvm.compiler.truffle.runtime.OptimizedOSRLoopNode$OptimizedDefaultOSRLoopNode)
                            <js> Vector.forEach(deltablue.js:89) (16|com.oracle.truffle.js.nodes.control.WhileNode)
                            <js> Vector.forEach(deltablue.js:88) (8|com.oracle.truffle.js.nodes.control.VoidBlockNode)
                            <js> Vector.forEach(deltablue.js:88) (9|org.graalvm.compiler.truffle.runtime.OptimizedBlockNode)
                            <js> Vector.forEach(deltablue.js:88) (8|com.oracle.truffle.js.nodes.control.VoidBlockNode)
                            <js> Vector.forEach(deltablue.js:88) (2|com.oracle.truffle.js.nodes.control.ExprBlockNode)
                            <js> Vector.forEach(deltablue.js:88) (3|org.graalvm.compiler.truffle.runtime.OptimizedBlockNode)
                            <js> Vector.forEach(deltablue.js:88) (2|com.oracle.truffle.js.nodes.control.ExprBlockNode)
                            <js> Vector.forEach(deltablue.js:88) (1|com.oracle.truffle.js.nodes.function.FunctionBodyNode)
                            <js> Vector.forEach(deltablue.js:88) (0|com.oracle.truffle.js.nodes.function.FunctionRootNode)
          7,391,766   0%  com.oracle.truffle.api.object.DynamicObject.getShape(DynamicObject.java:173) (321|Read)
                          com.oracle.truffle.object.ShapeImpl.check(ShapeImpl.java:948)
                          com.oracle.truffle.js.nodes.access.PropertyGetNode.getValueInt(PropertyGetNode.java:234)
                          com.oracle.truffle.js.nodes.access.PropertyNode.executeInt(PropertyNode.java:166)
                          com.oracle.truffle.js.nodes.access.PropertyNode.executeInt(PropertyNode.java:158)
                          com.oracle.truffle.js.nodes.binary.JSLessThanNodeGen.executeBoolean_int_int0(JSLessThanNodeGen.java:186)
                          com.oracle.truffle.js.nodes.binary.JSLessThanNodeGen.executeBoolean(JSLessThanNodeGen.java:159)
                          com.oracle.truffle.js.nodes.control.StatementNode.executeConditionAsBoolean(StatementNode.java:58)
                          com.oracle.truffle.js.nodes.control.AbstractRepeatingNode.executeCondition(AbstractRepeatingNode.java:63)
                          com.oracle.truffle.js.nodes.control.WhileNode$WhileDoRepeatingNode.executeRepeating(WhileNode.java:237)
                          com.oracle.truffle.api.nodes.RepeatingNode.executeRepeatingWithValue(RepeatingNode.java:112)
                          org.graalvm.compiler.truffle.runtime.OptimizedOSRLoopNode.execute(OptimizedOSRLoopNode.java:124)
                          com.oracle.truffle.js.nodes.control.WhileNode.executeVoid(WhileNode.java:181)
                          com.oracle.truffle.js.nodes.control.AbstractBlockNode.executeVoid(AbstractBlockNode.java:80)
                          com.oracle.truffle.js.nodes.control.AbstractBlockNode.executeVoid(AbstractBlockNode.java:1)
                          org.graalvm.compiler.truffle.runtime.OptimizedBlockNode.executeVoid(OptimizedBlockNode.java:123)
                          com.oracle.truffle.js.nodes.control.AbstractBlockNode.executeVoid(AbstractBlockNode.java:70)
                          com.oracle.truffle.js.nodes.control.VoidBlockNode.execute(VoidBlockNode.java:61)
                          com.oracle.truffle.js.nodes.control.AbstractBlockNode.executeGeneric(AbstractBlockNode.java:85)
                          com.oracle.truffle.js.nodes.control.AbstractBlockNode.executeGeneric(AbstractBlockNode.java:1)
                          org.graalvm.compiler.truffle.runtime.OptimizedBlockNode.executeGeneric(OptimizedBlockNode.java:80)
                          com.oracle.truffle.js.nodes.control.AbstractBlockNode.execute(AbstractBlockNode.java:75)
                          com.oracle.truffle.js.nodes.function.FunctionBodyNode.execute(FunctionBodyNode.java:73)
                          com.oracle.truffle.js.nodes.function.FunctionRootNode.executeInRealm(FunctionRootNode.java:149)
                          com.oracle.truffle.js.runtime.JavaScriptRealmBoundaryRootNode.execute(JavaScriptRealmBoundaryRootNode.java:88)
                          org.graalvm.compiler.truffle.runtime.OptimizedCallTarget.executeRootNode(OptimizedCallTarget.java:656)
                          org.graalvm.compiler.truffle.runtime.OptimizedCallTarget.profiledPERoot(OptimizedCallTarget.java:628)
                          Source AST Nodes:
                            <js> Vector.forEach(deltablue.js:89) (23|com.oracle.truffle.js.nodes.access.PropertyGetNode)
                            <js> Vector.forEach(deltablue.js:89) (21|com.oracle.truffle.js.nodes.access.PropertyNode)
                            <js> Vector.forEach(deltablue.js:89) (19|com.oracle.truffle.js.nodes.binary.JSLessThanNodeGen)
                            <js> Vector.forEach(deltablue.js:89) (18|com.oracle.truffle.js.nodes.control.WhileNode$WhileDoRepeatingNode)
                            <js> Vector.forEach(deltablue.js:89) (17|org.graalvm.compiler.truffle.runtime.OptimizedOSRLoopNode$OptimizedDefaultOSRLoopNode)
                            <js> Vector.forEach(deltablue.js:89) (16|com.oracle.truffle.js.nodes.control.WhileNode)
                            <js> Vector.forEach(deltablue.js:88) (8|com.oracle.truffle.js.nodes.control.VoidBlockNode)
                            <js> Vector.forEach(deltablue.js:88) (9|org.graalvm.compiler.truffle.runtime.OptimizedBlockNode)
                            <js> Vector.forEach(deltablue.js:88) (8|com.oracle.truffle.js.nodes.control.VoidBlockNode)
                            <js> Vector.forEach(deltablue.js:88) (2|com.oracle.truffle.js.nodes.control.ExprBlockNode)
                            <js> Vector.forEach(deltablue.js:88) (3|org.graalvm.compiler.truffle.runtime.OptimizedBlockNode)
                            <js> Vector.forEach(deltablue.js:88) (2|com.oracle.truffle.js.nodes.control.ExprBlockNode)
                            <js> Vector.forEach(deltablue.js:88) (1|com.oracle.truffle.js.nodes.function.FunctionBodyNode)
                            <js> Vector.forEach(deltablue.js:88) (0|com.oracle.truffle.js.nodes.function.FunctionRootNode)
         34,073,368   4%  no-location in org.graalvm.compiler.truffle.runtime.OptimizedCallTarget.profiledPERoot(Object[])
        828,123,983 total
=========== write (dynamic counters):

           ... omitted output....
           
          7,019,402   1%  com.oracle.truffle.js.runtime.JSArguments.createInitial(JSArguments.java:77) (489|Write)
                          com.oracle.truffle.js.runtime.JSArguments.createOneArg(JSArguments.java:88)
                          com.oracle.truffle.js.nodes.function.JSFunctionCallNode$Call1Node.createArguments(JSFunctionCallNode.java:617)
                          com.oracle.truffle.js.nodes.function.JSFunctionCallNode$CallNode.execute(JSFunctionCallNode.java:537)
                          com.oracle.truffle.js.nodes.JavaScriptNode.executeVoid(JavaScriptNode.java:192)
                          com.oracle.truffle.js.nodes.control.AbstractBlockNode.executeVoid(AbstractBlockNode.java:80)
                          com.oracle.truffle.js.nodes.control.AbstractBlockNode.executeVoid(AbstractBlockNode.java:1)
                          org.graalvm.compiler.truffle.runtime.OptimizedBlockNode.executeVoid(OptimizedBlockNode.java:123)
                          com.oracle.truffle.js.nodes.control.AbstractBlockNode.executeVoid(AbstractBlockNode.java:70)
                          com.oracle.truffle.js.nodes.control.AbstractRepeatingNode.executeBody(AbstractRepeatingNode.java:67)
                          com.oracle.truffle.js.nodes.control.WhileNode$WhileDoRepeatingNode.executeRepeating(WhileNode.java:238)
                          com.oracle.truffle.api.nodes.RepeatingNode.executeRepeatingWithValue(RepeatingNode.java:112)
                          org.graalvm.compiler.truffle.runtime.OptimizedOSRLoopNode.execute(OptimizedOSRLoopNode.java:124)
                          com.oracle.truffle.js.nodes.control.WhileNode.executeVoid(WhileNode.java:181)
                          com.oracle.truffle.js.nodes.control.AbstractBlockNode.executeVoid(AbstractBlockNode.java:80)
                          com.oracle.truffle.js.nodes.control.AbstractBlockNode.executeVoid(AbstractBlockNode.java:1)
                          org.graalvm.compiler.truffle.runtime.OptimizedBlockNode.executeVoid(OptimizedBlockNode.java:123)
                          com.oracle.truffle.js.nodes.control.AbstractBlockNode.executeVoid(AbstractBlockNode.java:70)
                          com.oracle.truffle.js.nodes.control.VoidBlockNode.execute(VoidBlockNode.java:61)
                          com.oracle.truffle.js.nodes.control.AbstractBlockNode.executeGeneric(AbstractBlockNode.java:85)
                          com.oracle.truffle.js.nodes.control.AbstractBlockNode.executeGeneric(AbstractBlockNode.java:1)
                          org.graalvm.compiler.truffle.runtime.OptimizedBlockNode.executeGeneric(OptimizedBlockNode.java:80)
                          com.oracle.truffle.js.nodes.control.AbstractBlockNode.execute(AbstractBlockNode.java:75)
                          com.oracle.truffle.js.nodes.function.FunctionBodyNode.execute(FunctionBodyNode.java:73)
                          com.oracle.truffle.js.nodes.function.FunctionRootNode.executeInRealm(FunctionRootNode.java:149)
                          com.oracle.truffle.js.runtime.JavaScriptRealmBoundaryRootNode.execute(JavaScriptRealmBoundaryRootNode.java:88)
                          org.graalvm.compiler.truffle.runtime.OptimizedCallTarget.executeRootNode(OptimizedCallTarget.java:656)
                          org.graalvm.compiler.truffle.runtime.OptimizedCallTarget.profiledPERoot(OptimizedCallTarget.java:628)
                          Source AST Nodes:
                            <js> Vector.forEach(deltablue.js:90) (28|com.oracle.truffle.js.nodes.function.JSFunctionCallNode$Call1Node)
                            <js> Vector.forEach(deltablue.js:89) (26|com.oracle.truffle.js.nodes.control.VoidBlockNode)
                            <js> Vector.forEach(deltablue.js:89) (27|org.graalvm.compiler.truffle.runtime.OptimizedBlockNode)
                            <js> Vector.forEach(deltablue.js:89) (26|com.oracle.truffle.js.nodes.control.VoidBlockNode)
                            <js> Vector.forEach(deltablue.js:89) (18|com.oracle.truffle.js.nodes.control.WhileNode$WhileDoRepeatingNode)
                            <js> Vector.forEach(deltablue.js:89) (17|org.graalvm.compiler.truffle.runtime.OptimizedOSRLoopNode$OptimizedDefaultOSRLoopNode)
                            <js> Vector.forEach(deltablue.js:89) (16|com.oracle.truffle.js.nodes.control.WhileNode)
                            <js> Vector.forEach(deltablue.js:88) (8|com.oracle.truffle.js.nodes.control.VoidBlockNode)
                            <js> Vector.forEach(deltablue.js:88) (9|org.graalvm.compiler.truffle.runtime.OptimizedBlockNode)
                            <js> Vector.forEach(deltablue.js:88) (8|com.oracle.truffle.js.nodes.control.VoidBlockNode)
                            <js> Vector.forEach(deltablue.js:88) (2|com.oracle.truffle.js.nodes.control.ExprBlockNode)
                            <js> Vector.forEach(deltablue.js:88) (3|org.graalvm.compiler.truffle.runtime.OptimizedBlockNode)
                            <js> Vector.forEach(deltablue.js:88) (2|com.oracle.truffle.js.nodes.control.ExprBlockNode)
                            <js> Vector.forEach(deltablue.js:88) (1|com.oracle.truffle.js.nodes.function.FunctionBodyNode)
                            <js> Vector.forEach(deltablue.js:88) (0|com.oracle.truffle.js.nodes.function.FunctionRootNode)
          7,019,402   1%  com.oracle.truffle.js.runtime.JSArguments.createInitial(JSArguments.java:77) (486|Write)
                          com.oracle.truffle.js.runtime.JSArguments.createOneArg(JSArguments.java:88)
                          com.oracle.truffle.js.nodes.function.JSFunctionCallNode$Call1Node.createArguments(JSFunctionCallNode.java:617)
                          com.oracle.truffle.js.nodes.function.JSFunctionCallNode$CallNode.execute(JSFunctionCallNode.java:537)
                          com.oracle.truffle.js.nodes.JavaScriptNode.executeVoid(JavaScriptNode.java:192)
                          com.oracle.truffle.js.nodes.control.AbstractBlockNode.executeVoid(AbstractBlockNode.java:80)
                          com.oracle.truffle.js.nodes.control.AbstractBlockNode.executeVoid(AbstractBlockNode.java:1)
                          org.graalvm.compiler.truffle.runtime.OptimizedBlockNode.executeVoid(OptimizedBlockNode.java:123)
                          com.oracle.truffle.js.nodes.control.AbstractBlockNode.executeVoid(AbstractBlockNode.java:70)
                          com.oracle.truffle.js.nodes.control.AbstractRepeatingNode.executeBody(AbstractRepeatingNode.java:67)
                          com.oracle.truffle.js.nodes.control.WhileNode$WhileDoRepeatingNode.executeRepeating(WhileNode.java:238)
                          com.oracle.truffle.api.nodes.RepeatingNode.executeRepeatingWithValue(RepeatingNode.java:112)
                          org.graalvm.compiler.truffle.runtime.OptimizedOSRLoopNode.execute(OptimizedOSRLoopNode.java:124)
                          com.oracle.truffle.js.nodes.control.WhileNode.executeVoid(WhileNode.java:181)
                          com.oracle.truffle.js.nodes.control.AbstractBlockNode.executeVoid(AbstractBlockNode.java:80)
                          com.oracle.truffle.js.nodes.control.AbstractBlockNode.executeVoid(AbstractBlockNode.java:1)
                          org.graalvm.compiler.truffle.runtime.OptimizedBlockNode.executeVoid(OptimizedBlockNode.java:123)
                          com.oracle.truffle.js.nodes.control.AbstractBlockNode.executeVoid(AbstractBlockNode.java:70)
                          com.oracle.truffle.js.nodes.control.VoidBlockNode.execute(VoidBlockNode.java:61)
                          com.oracle.truffle.js.nodes.control.AbstractBlockNode.executeGeneric(AbstractBlockNode.java:85)
                          com.oracle.truffle.js.nodes.control.AbstractBlockNode.executeGeneric(AbstractBlockNode.java:1)
                          org.graalvm.compiler.truffle.runtime.OptimizedBlockNode.executeGeneric(OptimizedBlockNode.java:80)
                          com.oracle.truffle.js.nodes.control.AbstractBlockNode.execute(AbstractBlockNode.java:75)
                          com.oracle.truffle.js.nodes.function.FunctionBodyNode.execute(FunctionBodyNode.java:73)
                          com.oracle.truffle.js.nodes.function.FunctionRootNode.executeInRealm(FunctionRootNode.java:149)
                          com.oracle.truffle.js.runtime.JavaScriptRealmBoundaryRootNode.execute(JavaScriptRealmBoundaryRootNode.java:88)
                          org.graalvm.compiler.truffle.runtime.OptimizedCallTarget.executeRootNode(OptimizedCallTarget.java:656)
                          org.graalvm.compiler.truffle.runtime.OptimizedCallTarget.profiledPERoot(OptimizedCallTarget.java:628)
                          Source AST Nodes:
                            <js> Vector.forEach(deltablue.js:90) (28|com.oracle.truffle.js.nodes.function.JSFunctionCallNode$Call1Node)
                            <js> Vector.forEach(deltablue.js:89) (26|com.oracle.truffle.js.nodes.control.VoidBlockNode)
                            <js> Vector.forEach(deltablue.js:89) (27|org.graalvm.compiler.truffle.runtime.OptimizedBlockNode)
                            <js> Vector.forEach(deltablue.js:89) (26|com.oracle.truffle.js.nodes.control.VoidBlockNode)
                            <js> Vector.forEach(deltablue.js:89) (18|com.oracle.truffle.js.nodes.control.WhileNode$WhileDoRepeatingNode)
                            <js> Vector.forEach(deltablue.js:89) (17|org.graalvm.compiler.truffle.runtime.OptimizedOSRLoopNode$OptimizedDefaultOSRLoopNode)
                            <js> Vector.forEach(deltablue.js:89) (16|com.oracle.truffle.js.nodes.control.WhileNode)
                            <js> Vector.forEach(deltablue.js:88) (8|com.oracle.truffle.js.nodes.control.VoidBlockNode)
                            <js> Vector.forEach(deltablue.js:88) (9|org.graalvm.compiler.truffle.runtime.OptimizedBlockNode)
                            <js> Vector.forEach(deltablue.js:88) (8|com.oracle.truffle.js.nodes.control.VoidBlockNode)
                            <js> Vector.forEach(deltablue.js:88) (2|com.oracle.truffle.js.nodes.control.ExprBlockNode)
                            <js> Vector.forEach(deltablue.js:88) (3|org.graalvm.compiler.truffle.runtime.OptimizedBlockNode)
                            <js> Vector.forEach(deltablue.js:88) (2|com.oracle.truffle.js.nodes.control.ExprBlockNode)
                            <js> Vector.forEach(deltablue.js:88) (1|com.oracle.truffle.js.nodes.function.FunctionBodyNode)
                            <js> Vector.forEach(deltablue.js:88) (0|com.oracle.truffle.js.nodes.function.FunctionRootNode)
        429,688,807 total
============================
```

