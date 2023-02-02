---
layout: docs
toc_group: embedding
link_title: Enterprise Sandbox Resource Limits
permalink: /reference-manual/embed-languages/sandbox-resource-limits/
---

## Enterprise Sandbox Resource Limits

GraalVM Enterprise provides the experimental Sandbox Resource Limits feature that allows for the limiting of resources used by guest applications.
These resource limits are not available in the Community Edition of GraalVM.
The following document describes how to configure sandbox resource limits using options in GraalVM Polyglot API.

In general all resource limit options are prefixed with `sandbox` option group and they can be listed using the help of any language launcher provided in GraalVM, e.g., `js --help:tools`.
Polyglot options can be provided through the language launcher, using the polyglot embedding API of the Graal SDK, or on the JVM using a system property.
For better understanding of the examples it is recommended to read the [polyglot embedding guide](embed-languages.md) of the reference manual first.

Currently all sandbox options are experimental therefore in these examples it is assumed that experimental options are enabled (e.g., with `--experimental-options`).
The options are a best effort approach to limiting resource usage of guest applications.

The resource limits may be configured using the following options:

<!-- BEGIN: sandbox-options -->
- `--sandbox.AllocatedBytesCheckEnabled=true|false` : Specifies whether checking of allocated bytes for an execution context is enabled. Is set to 'true' by default.
- `--sandbox.AllocatedBytesCheckFactor=[0.0, inf)` : Specifies a factor of MaxHeapMemory the allocation of which triggers retained heap memory computation. When allocated bytes for an execution context reach the specified factor, computation of bytes retained in the heap by the context is initiated. Is set to '1.0' by default.
- `--sandbox.AllocatedBytesCheckInterval=[1, inf)ms|s|m|h|d` : Time interval to check allocated bytes for an execution context. Exceeding certain number of allocated bytes triggers computation of bytes retained in the heap by the context. Is set to '10ms' by default.
- `--sandbox.MaxASTDepth=[1, inf)` : Maximum AST depth of a function (default: no limit).
- `--sandbox.MaxCPUTime=[1, inf)ms|s|m|h|d` : Limits the total maximum CPU time that was spent running the application. No limit is set by default. Example value: '100ms'.
- `--sandbox.MaxCPUTimeCheckInterval=[1, inf)ms|s|m|h|d` : Time interval to check the active CPU time for an execution context. Is set to '10ms' by default.
- `--sandbox.MaxHeapMemory=[1, inf)B|KB|MB|GB` : Specifies the maximum heap memory that can be retained by the application during its run. No limit is set by default and setting the related expert options has no effect. Example value: '100MB'.
- `--sandbox.MaxStackFrames=[1, inf)` : Limits the maximum number of guest stack frames (default: no limit).
- `--sandbox.MaxStatements=[1, inf)` : Limits the maximum number of guest language statements executed. The execution is cancelled with an resource exhausted error when it is exceeded.
- `--sandbox.MaxStatementsIncludeInternal` : Configures whether to include internal sources in the max statements computation.
- `--sandbox.MaxThreads=[1, inf)` : Limits the number of threads that can be entered by a context at the same point in time (default: no limit).
- `--sandbox.RetainedBytesCheckFactor=[0.0, inf)` : Specifies a factor of total heap memory of the host VM the exceeding of which stops the world. When the total number of bytes allocated in the heap for the whole host VM exceeds the factor, the following process is initiated. Execution for all engines with at least one memory-limited execution context is paused. Retained bytes in the heap for each memory-limited context are computed. Contexts exceeding their limits are cancelled. The execution is resumed. Is set to '0.7' by default.
- `--sandbox.RetainedBytesCheckInterval=[1, inf)ms|s|m|h|d` : Specifies the minimum time interval between two computations of retained bytes in the heap for a single execution context. Is set to '10ms' by default.
- `--sandbox.TraceLimits=true|false` : Records the maximum amount of resources used during execution, and reports a summary of resource limits to the log file upon application exit. Users may also provide limits to enforce while tracing. This flag can be used to estimate an application's optimal sandbox parameters, either by tracing the limits of a stress test or peak usage.
- `--sandbox.UseLowMemoryTrigger=true|false` : Specifies whether stopping the world is enabled. When enabled, engines with at least one memory limited execution context are paused when the total number of bytes allocated in the heapfor the whole host VM exceeds the specified factor of total heap memory of the host VM. Is set to 'true' by default.
<!-- END: sandbox-options -->

Different configurations may be provided for each polyglot embedding `Context` instance.
In addition to that the limits may be reset at any point of time during the execution. Resetting is only aplicable to `sandbox.MaxStatements` and `sandbox.MaxCPUTime`.

A guest language might choose to create an inner context within the outer execution context. The limits are applied to the outer context and all inner contexts it spawns.
It is not possible to specify a separate limit for inner contexts and it is also not possible to escape any limit by creating an inner context.

## Limiting the active CPU time

The `sandbox.MaxCPUTime` option allows you to specify the maximum CPU time spent running the application.
The maximum [CPU time](https://docs.oracle.com/en/java/javase/11/docs/api/java.management/java/lang/management/ThreadMXBean.html#getThreadCpuTime\(long\)) specifies how long a context can be active until it is automatically cancelled and the context is closed.
By default the time limit is checked every 10 milliseconds.
This can be customized using the `sandbox.MaxCPUTimeCheckInterval` option.
Both maximum CPU time limit and check interval must be positive.
By default no CPU time limit is enforced.
If the time limit is exceeded then the polyglot context is cancelled and the execution stops by throwing a [`PolyglotException`](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/PolyglotException.html) which returns `true` for `isResourceExhausted()`.
As soon as the time limit is triggered, no further application code can be executed with this context.
It will continuously throw a `PolyglotException` for any method of the polyglot context that will be invoked.

The used CPU time of a context includes time spent in callbacks to host code.
This is also the case when running with [Polyglot Isolates].

The used CPU time of a context typically does not include time spent waiting for synchronization or IO.
The CPU time of all threads will be added and checked against the CPU time limit.
This can mean that if two threads execute the same context then the time limit will be exceeded twice as fast.

The time limit is enforced by a separate high-priority thread that will be woken regularly.
There is no guarantee that the context will be cancelled within the accuracy specified.
The accuracy may be significantly missed, e.g. if the host VM causes a full garbage collection.
If the time limit is never exceeded then the throughput of the guest context is not affected.
If the time limit is exceeded for one context then it may slow down the throughput for other contexts with the same explicit engine temporarily.

Available units to specify time durations are `ms` for milliseconds, `s` for seconds, `m` for minutes, `h` for hours and `d` for days.
It is not allowed specify negative values or no time unit with CPU time limit options.

### Example Usage

```java
try (Context context = Context.newBuilder("js")
                           .allowExperimentalOptions(true)
                           .option("sandbox.MaxCPUTime", "500ms")
                           .option("sandbox.MaxCPUTimeCheckInterval", "5ms")
                       .build();) {
    context.eval("js", "while(true);");
    assert false;
} catch (PolyglotException e) {
    // triggered after 500ms;
    // context is closed and can no longer be used
    // error message: Maximum CPU time limit of 500ms exceeded.
    assert e.isCancelled();
    assert e.isResourceExhausted();
}
```

## Limiting the number of executed statements

Specifies the maximum number of statements a context may execute until the the context will be cancelled.
After the statement limit was triggered for a context, it is no longer usable and every use of the context will throw a `PolyglotException` that returns `true` for `PolyglotException.isCancelled()`.
The statement limit is independent of the number of threads executing and is applied per context.
It is also possible to specify this limit using the `[ResourceLimits]()` API of the polyglot embedding API.

By default there is no statement limit applied.
The limit may be set to a negative number to disable it.
Whether this limit is applied internal sources only can be configured using `sandbox.MaxStatementsIncludeInternal`.
By default the limit does not include statements of sources that are marked internal.
If a shared engine is used then the same internal configuration must be used for all contexts of an engine.
The maximum statement limit can be configured for each context of an engine separately.

Attaching a statement limit to a context reduces the throughput of all guest applications with the same engine.
The statement counter needs to be updated with every statement that is executed.
It is recommended to benchmark the use of the statement limit before it is used in production.

The complexity of a single statement may not be constant time depending on the guest language.
For example, statements that execute JavaScript builtins, like `Array.sort`, may account for a single statement, but its execution time is dependent on the size of the array.
The statement count limit is therefore not suitable to perform time boxing and must be combined with other more reliable measures like the CPU time limit.

```java
try (Context context = Context.newBuilder("js")
                           .allowExperimentalOptions(true)
                           .option("sandbox.MaxStatements", "2")
                           .option("sandbox.MaxStatementsIncludeInternal", "false")
                       .build();) {
    context.eval("js", "purpose = 41");
    context.eval("js", "purpose++");
    context.eval("js", "purpose++"); // triggers max statements
    assert false;
} catch (PolyglotException e) {
    // context is closed and can no longer be used
    // error message: Maximum statements limit of 2 exceeded.
    assert e.isCancelled();
    assert e.isResourceExhausted();
}
```

## Limiting the AST depth of functions

A limit on the maximum expression depth of a guest language function.
Only instrumentable nodes count towards the limit.
If the limit is exceeded, evaluation of the code fails and the context is canceled.

The AST depth can give an estimate of the complexity of a function as well as its stack frame size.
Limiting the AST depth can serve as a safeguard against arbitrary stack space usage by a single function.

## Limiting the number of stack frames

Specifies the maximum number of frames a context can push on the stack.
Exceeding the limit results in cancellation of the context.
A thread-local stack frame counter is incremented on function enter and decremented on function return.
Resetting resource limits does not affect the stack frame counter.

The stack frame limit in itself can serve as a safeguard against infinite recursion.
If used together with the AST depth limit it can be used to estimate total stack space usage.

## Limiting the number of active threads

Limits the number of threads that can be used by a context at the same point in time.
By default, an arbitrary number of threads can be used.
If a set limit is exceeded, entering the context fails with a `PolyglotException` and the polyglot context is canceled.
Resetting resource limits does not affect thread limits.

## Limiting the maximum heap memory

The `sandbox.MaxHeapMemory` option allows you to specify the maximum heap memory the application is allowed to retain during its run.
`sandbox.MaxHeapMemory` must be positive. This option is only supported on a HotSpot-based VM.
Enabling this option in a native executable will result in a `PolyglotException`.
The option is also not supported with [Polyglot Isolates], which have different means of controlling memory consumption.
When exceeding of the limit is detected, the corresponding context is automatically cancelled and then closed.

Only objects residing in the guest application count towards the limit - memory allocated during callbacks to host code does not.
The efficacy of this option (also) depends on the garbage collector used.

#### Example Usage

```java
try (Context context = Context.newBuilder("js")
                           .allowExperimentalOptions(true)
                           .option("sandbox.MaxHeapMemory", "100MB")
                       .build()) {
    context.eval("js", "var r = {}; var o = r; while(true) { o.o = {}; o = o.o; };");
    assert false;
} catch (PolyglotException e) {
    // triggered after the retained size is greater than 100MB;
    // context is closed and can no longer be used
    // error message: Maximum heap memory limit of 104857600 bytes exceeded. Current memory at least...
    assert e.isCancelled();
    assert e.isResourceExhausted();
}
```

## Determining Sandbox Resource Limits

The sandbox.TraceLimits option allows you to trace a running process and record the maximum resource utilization. This can be used to estimate parameters to define a sandbox for the workload. For example, a web server's sandbox parameters could be obtained by enabling this option and either stress-testing the server, or letting the server run during peak usage. When this option is enabled, the report is saved to the log file after the workload completes. Users can change the location of the log file by using --log.file=<path> with a language launcher or -Dpolyglot.log.file=<path> when using a java launcher. Each resource limit in the report can be passed directly to a sandbox option in order to enforce the limit.

See, for example, how to trace limits for a Python workload:

```
graalpy --log.file=limits.log --experimental-options --sandbox.TraceLimits=true workload.py

limits.log:
[trace-limits] Sandbox Limits Statistics:
HEAP                                12MB
CPU                                   7s
STATEMENTS                       9441565
STACKFRAMES                           29
THREADS                                1
ASTDEPTH                              15

Sandbox Command Line Options:
--sandbox.MaxHeapMemory=12MB --sandbox.MaxCPUTime=7s --sandbox.MaxStatements=9441565 --sandbox.MaxStackFrames=29 --sandbox.MaxThreads=1 --sandbox.MaxASTDepth=15
```

#### Implementation details and expert options

The limit is checked by retained size computation triggered either based on [allocated](https://docs.oracle.com/en/java/javase/11/docs/api/jdk.management/com/sun/management/ThreadMXBean.html#getThreadAllocatedBytes\(long\)) bytes or on [low memory notification](https://docs.oracle.com/en/java/javase/11/docs/api/java.management/java/lang/management/MemoryMXBean.html).

The allocated bytes are checked by a separate high-priority thread that will be woken regularly.
There is one such thread for each memory-limited context (one with `sandbox.MaxHeapMemory` set).
The retained bytes computation is done by yet another high-priority thread that is started from the allocated bytes checking thread as needed.
The retained bytes computation thread also cancels the context if the heap memory limit is exeeded.
Additionaly, when low memory trigger is invoked, all contexts on engines with at least one memory-limited context are paused together with their allocation checkers.
All individual retained size computations are cancelled.
Retained bytes in the heap for each memory-limited context are computed by a single high-priority thread.
Contexts exceeding their limits are cancelled, and then the execution is resumed.

The main goal of the heap memory limits is to prevent heap memory depletion related errors in most cases and thus enable the host VM to run smoothly even in the presence of misbehaving contexts.
The implementation is best effort. This means that there is no guarantee on the accuracy of the heap memory limit.
There is also no guarantee that setting a heap memory limit will prevent the context from causing `OutOfMemory` errors.
Guest applications that allocate many objects in quick succession have a lower accuracy than applications which allocate objects rarely.
The guest code execution will only be paused if the host heap memory is exhausted and a low memory trigger of the host VM is invoked.
Note that the scope of the pause is an engine, so a context without the `sandbox.MaxHeapMemory` option set is also paused in case it shares the engine with other context that is memory-limited.
Also note that if one context is cancelled other contexts with the same explicit engine may be slowed down. How the size retained by a context is computed can be
customized using the expert options `sandbox.AllocatedBytesCheckInterval`, `sandbox.AllocatedBytesCheckEnabled`, `sandbox.AllocatedBytesCheckFactor`, `sandbox.RetainedBytesCheckInterval`, `sandbox.RetainedBytesCheckFactor`, and `sandbox.UseLowMemoryTrigger` described below.

Retained size computation for a context is triggered when a retained bytes estimate exceeds a certain factor of specified `sandbox.MaxHeapMemory`.
The estimate is based on heap memory
[allocated](https://docs.oracle.com/en/java/javase/11/docs/api/jdk.management/com/sun/management/ThreadMXBean.html#getThreadAllocatedBytes\(long\)) by threads where the context has been active.
More precisely, the estimate is the result of previous retained bytes computation, if available, plus bytes allocated since the start of the previous computation.
By default the factor of `sandbox.MaxHeapMemory` is 1.0 and it can be customized by the `sandbox.AllocatedBytesCheckFactor` option.
The factor must be positive.
For example, let `sandbox.MaxHeapMemory` be 100MB and `sandbox.AllocatedBytesCheckFactor` be 0.5.
The retained size computation is first triggered when allocated bytes reach 50MB.
Let the computed retained size be 25MB, then the next retained size computation is triggered when additional 25MB is allocated, etc.

By default, allocated bytes are checked every 10 milliseconds. This can be configured by `sandbox.AllocatedBytesCheckInterval`.
The smallest possible interval is 1ms. Any smaller value is interpreted as 1ms.

The beginnings of two retained size computations of the same context must be by default at least 10 milliseconds apart.
This can be configured by the `sandbox.RetainedBytesCheckInterval` option. The interval must be positive.

The allocated bytes checking for a context can be disabled by the `sandbox.AllocatedBytesCheckEnabled` option.
By default it is enabled ("true"). If disabled ("false"), retained size checking for the context can be triggered only by the low memory trigger.

When the total number of bytes allocated in the heap for the whole host VM exceeds a certain factor of the total heap memory of the VM, [low memory notification](https://docs.oracle.com/en/java/javase/11/docs/api/java.management/java/lang/management/MemoryMXBean.html) is invoked and initiates the following process.
The execution for all engines with at least one execution context which has the `sandbox.MaxHeapMemory` option set is paused, retained bytes in the heap for each memory-limited context are computed, contexts exceeding their limits are cancelled, and then the execution is resumed.
The default factor is 0.7. This can be configuted by the `sandbox.RetainedBytesCheckFactor` option.
The factor must be between 0.0 and 1.0. All contexts using the `sandbox.MaxHeapMemory` option must use the same value for `sandbox.RetainedBytesCheckFactor`.

The described low memory trigger can be disabled by the `sandbox.UseLowMemoryTrigger` option.
By default it is enabled ("true"). If disabled ("false"), retained size checking for the execution context can be triggered only by the allocated bytes checker.
All contexts using the `sandbox.MaxHeapMemory` option must use the same value for `sandbox.UseLowMemoryTrigger`.

If exceeding of the heap memory limit is detected then the polyglot context is cancelled and the execution stops by throwing a [`PolyglotException`](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/PolyglotException.html) which returns `true` for `isResourceExhausted()`.
As soon as the memory limit is triggered, no further application code can be executed with this context.
It will continuously throw a `PolyglotException` for any method of the polyglot context that will be invoked.

Available units to specify time durations are `ms` for milliseconds, `s` for seconds, `m` for minutes, `h` for hours and `d` for days.
It is not allowed to specify negative values or no time unit with max heap memory options.

Available units to specify sizes are `B` for bytes, `KB` for kilobytes, `MB` for megabytes, and `GB` for gigabytes.
It is not allowed to specify negative values or no size unit with max heap memory options.

Resetting resource limits using `Context.resetLimits` does not affect the heap memory limit.

## Resetting Resource Limits

With the polyglot embedding API it is possible to reset the limits at any point in time using the [`Context.resetLimits`](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.html#resetLimits--) method.
This can be useful if a known and trusted initialization script should be excluded from limit. Resetting the limits is not applicable to all limits.

### Example Usage

```java
try (Context context = Context.newBuilder("js")
                           .allowExperimentalOptions(true)
                           .option("sandbox.MaxCPUTime", "500ms")
                       .build();) {
    context.eval("js", /*... initialization script ...*/);
    context.resetLimits();
    context.eval("js", /*... user script ...*/);
    assert false;
} catch (PolyglotException e) {
    assert e.isCancelled();
    assert e.isResourceExhausted();
}
```
