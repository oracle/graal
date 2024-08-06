---
layout: docs
toc_group: truffle
link_title: Truffle Language Safepoint Tutorial
permalink: /graalvm-as-a-platform/language-implementation-framework/Safepoint/
---
# Truffle Language Safepoint Tutorial

As of 21.1 Truffle has support for guest language safepoints.
Truffle safepoints allow to interrupt the guest language execution to perform thread local actions submitted by a language or tool.
A safepoint is a location during the guest language execution where the state is consistent and other operations can read its state.

This replaces previous instrumentation or assumption-based approaches to safepoints, which required the code to be invalidated for a thread local action to be performed.
The new implementation uses fast thread local checks and callee register saved stub calls to optimize for performance and keep the overhead minimal.
This means that for every loop back-edge and method exit we perform an additional non-volatile read which can potentially lead to slight slow-downs.

## Use Cases

Common use-cases of Truffle language safepoints are:

* Cancellation, requested exit or interruptions during guest language execution. The stack is unwound by submitting a thread local action.
* Reading the current stack trace information for other threads than the currently executing thread.
* Enumerating all object references active on the stack.
* Running a guest signal handler or guest finalizer on a given thread.
* Implement guest languages that expose a safepoint mechanism as part of their development toolkit.
* Debuggers evaluating expressions in languages that do not support execution on multiple threads.

## Language Support

Safepoints are explicitly polled by invoking the `TruffleSafepoint.poll(Node)` method.
A Truffle guest language implementation must ensure that a safepoint is polled repeatedly within a constant time interval.
For example, a single arithmetic expression completes within a constant number of CPU cycles.
However, a loop that summarizes values over an array uses a non-constant time dependent on the actual array size.
This typically means that safepoints are best polled at the end of loops and at the end of function or method calls to cover recursion.
In addition, any guest language code that blocks the execution, like guest language locks, need to use the  `TruffleSafepoint.setBlocked(Interrupter)` API to allow cooperative polling of safepoints while the thread is waiting.

Please read more details on what steps language implementations need to take to support thread local actions in the [javadoc](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleSafepoint.html).

## Thread Local Actions

Languages and instruments can submit actions using their environment.

Usage example:

```java

Env env; // language or instrument environment

env.submitThreadLocal(null, new ThreadLocalAction(true /*side-effecting*/, true /*synchronous*/) {
     @Override
     protected void perform(Access access) {
         assert access.getThread() == Thread.currentThread();
     }
});

```

Read more in the [javadoc](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/ThreadLocalAction.html).

## Current Limitations

There is currently no way to run thread local actions while the thread is executing in boundary annotated methods unless the method cooperatively polls safepoints or uses the blocking API.
Unfortunately it is not always possible to cooperatively poll safepoints, for example, if the code currently executes third party native code.
A future improvement will allow to run code for other threads while they are blocked.
This is one of the reasons why it is recommended to use `ThreadLocalAction.Access.getThread()` instead of directly using `Thread.currentThread()`.
When the native call returns it needs to wait for any thread local action that is currently executing for this thread.
This will enable to collect guest language stack traces from other threads while they are blocked by uncooperative native code.
Currently the action will be performed on the next safepoint location when the native code returns.

## Tooling for Debugging

There are several debug options available:

### Excercise safepoints with SafepointALot

SafepointALot is a tool to exercise every safepoint of an application and collect statistics.

If enabled with the `--engine.SafepointALot` option it prints the statistics on the cpu time interval between safepoints at the end of an execution.

For example, running:

```
graalvm/bin/js --engine.SafepointALot js-benchmarks/harness.js -- octane-deltablue.js
```

Prints the following output to the log on context close:

```
DeltaBlue: 540
[engine] Safepoint Statistics
  --------------------------------------------------------------------------------------
   Thread Name         Safepoints | Interval     Avg              Min              Max
  --------------------------------------------------------------------------------------
   main                  48384054 |            0.425 us           0.1 us       44281.1 us
  -------------------------------------------------------------------------------------
   All threads           48384054 |            0.425 us           0.1 us       42281.1 us
```

It is recommended for guest language implementations to try to stay below 1ms on average.
Note that precise timing can depend on CPU and interruptions by the GC.
Since GC times are included in the safepoint interval times, it is expected that the maximum is close to the maximum GC interruption time.
Future versions of this tool will be able to exclude GC interruption times from this statistic.

### Find missing safepoint polls

The option `TraceMissingSafepointPollInterval` helps to find missing safepoint polls, use it like:

```
$ bin/js --experimental-options --engine.TraceMissingSafepointPollInterval=20 -e 'print(6*7)'
...
42
[engine] No TruffleSafepoint.poll() for 36ms on main (stacktrace 1ms after the last poll)
	at java.base/java.lang.StringLatin1.replace(StringLatin1.java:312)
	at java.base/java.lang.String.replace(String.java:2933)
	at java.base/jdk.internal.loader.BuiltinClassLoader.defineClass(BuiltinClassLoader.java:801)
	at java.base/jdk.internal.loader.BuiltinClassLoader.findClassInModuleOrNull(BuiltinClassLoader.java:741)
	at java.base/jdk.internal.loader.BuiltinClassLoader.loadClassOrNull(BuiltinClassLoader.java:665)
	at java.base/jdk.internal.loader.BuiltinClassLoader.loadClass(BuiltinClassLoader.java:639)
	at java.base/jdk.internal.loader.ClassLoaders$AppClassLoader.loadClass(ClassLoaders.java:188)
	at java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:526)
	at org.graalvm.truffle/com.oracle.truffle.polyglot.PolyglotValueDispatch.createInteropValue(PolyglotValueDispatch.java:1694)
	at org.graalvm.truffle/com.oracle.truffle.polyglot.PolyglotLanguageInstance$1.apply(PolyglotLanguageInstance.java:149)
	at org.graalvm.truffle/com.oracle.truffle.polyglot.PolyglotLanguageInstance$1.apply(PolyglotLanguageInstance.java:147)
	at java.base/java.util.concurrent.ConcurrentHashMap.computeIfAbsent(ConcurrentHashMap.java:1708)
	at org.graalvm.truffle/com.oracle.truffle.polyglot.PolyglotLanguageInstance.lookupValueCacheImpl(PolyglotLanguageInstance.java:147)
	at org.graalvm.truffle/com.oracle.truffle.polyglot.PolyglotLanguageInstance.lookupValueCache(PolyglotLanguageInstance.java:137)
	at org.graalvm.truffle/com.oracle.truffle.polyglot.PolyglotLanguageContext.asValue(PolyglotLanguageContext.java:948)
	at org.graalvm.truffle/com.oracle.truffle.polyglot.PolyglotContextImpl.eval(PolyglotContextImpl.java:1686)
	at org.graalvm.truffle/com.oracle.truffle.polyglot.PolyglotContextDispatch.eval(PolyglotContextDispatch.java:60)
	at org.graalvm.polyglot/org.graalvm.polyglot.Context.eval(Context.java:402)
	at org.graalvm.js.launcher/com.oracle.truffle.js.shell.JSLauncher.executeScripts(JSLauncher.java:365)
	at org.graalvm.js.launcher/com.oracle.truffle.js.shell.JSLauncher.launch(JSLauncher.java:93)
	at org.graalvm.launcher/org.graalvm.launcher.AbstractLanguageLauncher.launch(AbstractLanguageLauncher.java:296)
	at org.graalvm.launcher/org.graalvm.launcher.AbstractLanguageLauncher.launch(AbstractLanguageLauncher.java:121)
	at org.graalvm.launcher/org.graalvm.launcher.AbstractLanguageLauncher.runLauncher(AbstractLanguageLauncher.java:168)
...
```

It prints host stacktraces when there was not a safepoint poll in the last N milliseconds, N being the argument to `TraceMissingSafepointPollInterval`.

On HotSpot there can be long delays between guest safepoints due to classloading, so it makes sense to run this with a native image or focus on non-classloading stacktraces. 

### Trace thread local actions

The option `--engine.TraceThreadLocalActions` allows to trace all thread local actions of any origin.

Example output:

```
[engine] [tl] submit                 0  thread[main]                action[SampleAction$8@5672f0d1]     all-threads[alive=4]        side-effecting     asynchronous
[engine] [tl]   perform-start        0  thread[pool-1-thread-410]   action[SampleAction$8@5672f0d1]
[engine] [tl]   perform-start        0  thread[pool-1-thread-413]   action[SampleAction$8@5672f0d1]
[engine] [tl]   perform-start        0  thread[pool-1-thread-412]   action[SampleAction$8@5672f0d1]
[engine] [tl]   perform-done         0  thread[pool-1-thread-413]   action[SampleAction$8@5672f0d1]
[engine] [tl]   perform-done         0  thread[pool-1-thread-410]   action[SampleAction$8@5672f0d1]
[engine] [tl]   perform-start        0  thread[pool-1-thread-411]   action[SampleAction$8@5672f0d1]
[engine] [tl]   perform-done         0  thread[pool-1-thread-412]   action[SampleAction$8@5672f0d1]
[engine] [tl]   perform-done         0  thread[pool-1-thread-411]   action[SampleAction$8@5672f0d1]
[engine] [tl] done                   0  thread[pool-1-thread-411]   action[SampleAction$8@5672f0d1]
```

### Printing guest and host stack frames every time interval.

The option `--engine.TraceStackTraceInterval=1000` allows to set the time interval in milliseconds to repeatedly print the current stack trace.
Note that the stack trace is printed on the next safepoint poll and therefore might not be accurate.

```
graalvm/bin/js --engine.TraceStackTraceInterval=1000 js-benchmarks/harness.js -- octane-deltablue.js
```

Prints the following output:

```
[engine] Stack Trace Thread main: org.graalvm.polyglot.PolyglotException
	at <js> BinaryConstraint.chooseMethod(octane-deltablue.js:359-381:9802-10557)
	at <js> Constraint.satisfy(octane-deltablue.js:176:5253-5275)
	at <js> Planner.incrementalAdd(octane-deltablue.js:597:16779-16802)
	at <js> Constraint.addConstraint(octane-deltablue.js:165:4883-4910)
	at <js> UnaryConstraint(octane-deltablue.js:219:6430-6449)
	at <js> StayConstraint(octane-deltablue.js:297:8382-8431)
	at <js> chainTest(octane-deltablue.js:817:23780-23828)
	at <js> deltaBlue(octane-deltablue.js:883:25703-25716)
	at <js> MeasureDefault(harness.js:552:20369-20383)
	at <js> BenchmarkSuite.RunSingleBenchmark(harness.js:614:22538-22550)
	at <js> RunNextBenchmark(harness.js:340:11560-11614)
	at <js> RunStep(harness.js:141:5673-5686)
	at <js> BenchmarkSuite.RunSuites(harness.js:160:6247-6255)
	at <js> runBenchmarks(harness.js:686-688:24861-25023)
	at <js> main(harness.js:734:26039-26085)
	at <js> :program(harness.js:783:27470-27484)
	at org.graalvm.polyglot.Context.eval(Context.java:348)
	at com.oracle.truffle.js.shell.JSLauncher.executeScripts(JSLauncher.java:347)
	at com.oracle.truffle.js.shell.JSLauncher.launch(JSLauncher.java:88)
	at org.graalvm.launcher.AbstractLanguageLauncher.launch(AbstractLanguageLauncher.java:124)
	at org.graalvm.launcher.AbstractLanguageLauncher.launch(AbstractLanguageLauncher.java:71)
	at com.oracle.truffle.js.shell.JSLauncher.main(JSLauncher.java:73)

[engine] Stack Trace Thread main: org.graalvm.polyglot.PolyglotException
	at <js> EqualityConstraint.execute(octane-deltablue.js:528-530:14772-14830)
	at <js> Plan.execute(octane-deltablue.js:781:22638-22648)
	at <js> chainTest(octane-deltablue.js:824:24064-24077)
	at <js> deltaBlue(octane-deltablue.js:883:25703-25716)
	at <js> MeasureDefault(harness.js:552:20369-20383)
	at <js> BenchmarkSuite.RunSingleBenchmark(harness.js:614:22538-22550)
	at <js> RunNextBenchmark(harness.js:340:11560-11614)
	at <js> RunStep(harness.js:141:5673-5686)
	at <js> BenchmarkSuite.RunSuites(harness.js:160:6247-6255)
	at <js> runBenchmarks(harness.js:686-688:24861-25023)
	at <js> main(harness.js:734:26039-26085)
	at <js> :program(harness.js:783:27470-27484)
	at org.graalvm.polyglot.Context.eval(Context.java:348)
	at com.oracle.truffle.js.shell.JSLauncher.executeScripts(JSLauncher.java:347)
	at com.oracle.truffle.js.shell.JSLauncher.launch(JSLauncher.java:88)
	at org.graalvm.launcher.AbstractLanguageLauncher.launch(AbstractLanguageLauncher.java:124)
	at org.graalvm.launcher.AbstractLanguageLauncher.launch(AbstractLanguageLauncher.java:71)
	at com.oracle.truffle.js.shell.JSLauncher.main(JSLauncher.java:73)
```

## Further Reading

Daloze, Benoit, Chris Seaton, Daniele Bonetta, and Hanspeter Mössenböck.
"Techniques and applications for guest-language safepoints."
In Proceedings of the 10th Workshop on Implementation, Compilation, Optimization of Object-Oriented Languages, Programs and Systems, pp. 1-10. 2015.

[https://dl.acm.org/doi/abs/10.1145/2843915.2843921](https://dl.acm.org/doi/abs/10.1145/2843915.2843921)
