## Configure Sandbox Resource Limits

The 20.3 release of GraalVM introduced the Sandbox Resource Limits feature that allows for the limiting of resources used by guest applications.
Note: This feature is available with GraalVM Enterprise only. It is being actively worked on, and other options to control resource usage by guest applications are expected in future releases as well.

Here you will learn how to configure Sandbox Resource Limits using the options in the [Polyglot API](https://www.graalvm.org/truffle/javadoc/org/graalvm/polyglot/ResourceLimits.html).

## Sandbox Options

All resource-limit options are prefixed with the `sandbox` option group and can be listed using the help of any language launcher available in GraalVM, e.g., `js --help:tools`.
Polyglot options can be provided through the language launcher, using the Polyglot Embedding API, or on the JVM using a system property.
For more details, see [Polyglot Options](/reference-manual/polyglot-programming/#polyglot-options).

Note: All sandbox options are currently experimental. Therefore, in the examples below, it is assumed that experimental options are enabled (e.g., with `--experimental-options`).

The resource limits may be configured using the following options:

* `--sandbox.MaxStatements=<long>`: to limit the maximum number of guest language statements
* `--sandbox.MaxStatementsIncludeInternal=<boolean>`: whether or not to include internal sources in the maximum statements computation
* `--sandbox.MaxCPUTime=1000ms`: to limit the total maximum CPU time spent running the application
* `--sandbox.MaxCPUTimeCheckInterval=10ms`: to set time interval to check the active CPU time for a context
* `--sandbox.MaxStackFrames=<int>`: to limit the maximum number of guest stack frames
* `--sandbox.MaxThreads=<int>`: to limit the number of threads that can be concurrently used by a context
* `--sandbox.MaxASTDepth=<int>`: to limit the maximum depth of AST nodes for a guest language function

Different configurations may be provided for each `Context` instance.
In addition, the limits may be reset at any point time during the execution.

## Limit the Maximum Active CPU Time

The `sandbox.MaxCPUTime` option allows you to specify the maximum CPU time spent running the application.
The maximum [CPU time](https://docs.oracle.com/en/java/javase/11/docs/api/java.management/java/lang/management/ThreadMXBean.html#getThreadCpuTime(long)) specifies how long a context can be active until it is automatically cancelled and the context is closed.
By default the time limit is checked every 10 milliseconds.
This can be customized using the `sandbox.MaxCPUTimeCheckInterval` option.
Both maximum CPU time limit and check interval must be positive.

By default, no CPU time limit is enforced.
If the time limit is exceeded, then the polyglot context is cancelled and the execution stops by throwing a [`PolyglotException`](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/PolyglotException.html) which returns `true` for `isResourceExhausted()`.
As soon as the time limit is triggered, no further application code can be executed with this context.
It will continuously throw a `PolyglotException` for any method of the polyglot context that will be invoked.

The used CPU time of a context typically does not include time spent waiting for synchronization or I/O.
The CPU time of all threads will be added and checked against the CPU time limit.
This can mean that if two threads execute the same context, then the time limit will be exceeded twice as fast.

The time limit is enforced by a separate, high-priority thread that will be woken regularly.
There is no guarantee that the context will be cancelled within the accuracy specified.
The accuracy may be significantly missed, e.g., if the host VM causes a full garbage collection.
If the time limit is never exceeded, then the throughput of the guest context is not affected.
If the time limit is exceeded for one context, then it may slow down the throughput for other contexts with the same explicit engine temporarily.

Available units to specify time durations are `ms` for milliseconds, `s` for seconds, `m` for minutes, `h` for hours, and `d` for days.
Specifying negative values or no time unit in the CPU time limit option is not allowed.

The time limit is applied to the context and all inner contexts it spawns. Therefore, new
inner contexts cannot be used to exceed the time limit.

#### Example Usage

```java
try (Context context = Context.newBuilder("js")
                           .allowExperimentalOptions(true)
                           .option("sandbox.MaxCPUTime", "500ms")
                           .option("sandbox.MaxCPUTimeCheckInterval", "5ms")
                       .build();) {
    try {
        context.eval("js", "while(true);");
        assert false;
    } catch (PolyglotException e) {
        // Triggered after 500ms;
        // Context is closed and can no longer be used
        // Error message: Maximum CPU time limit of 500ms exceeded.
        assert e.isCancelled();
        assert e.isResourceExhausted();
    }
}
```

## Limit the Number of Executed Statements

The `--sandbox.MaxStatement` option allows you to control the maximum number of statements a context may execute until the the context will be cancelled.
After the statement limit has been triggered for a context, it is no longer usable and every use of the context will throw a `PolyglotException` that returns `true` for `PolyglotException.isCancelled()`.
The statement limit is independent of the number of threads executing and is applied per context.
It is also possible to specify this limit using the [`ResourceLimits` API](https://www.graalvm.org/truffle/javadoc/org/graalvm/polyglot/ResourceLimits.html).

By default, there is no statement limit applied.
The limit may be set to a negative number to disable it.
Whether this limit is applied, internal sources only can be configured using `sandbox.MaxStatementsIncludeInternal`.

By default, the limit does not include statements of sources that are marked internal.
If a shared engine is used, then the same internal configuration must be used for all contexts of an engine.
The maximum statement limit can be configured for each context of an engine separately.

The statement limit is applied to the context and all inner contexts it spawns.
Therefore, new inner contexts cannot be used to exceed the statement limit.

Attaching a statement limit to a context reduces the throughput of all guest applications with the same engine.
The statement counter needs to be updated with every statement that is executed.
It is recommended to benchmark the use of the statement limit before it is used in production.

The complexity of a single statement may not be constant time, depending on the guest language.
For example, statements that execute JavaScript builtins, like `Array.sort`, may account for a single statement, but its execution time is dependent on the size of the array.
The statement count limit is therefore not suitable to perform time boxing and must be combined with other more reliable measures like the CPU time limit.

```java
try (Context context = Context.newBuilder("js")
                           .allowExperimentalOptions(true)
                           .option("sandbox.MaxStatements", "2")
                           .option("sandbox.MaxStatementsIncludeInternal", "false")
                       .build();) {
    try {
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
}
```

### Limit the AST Depth of Functions

You can limit the maximum expression depth of a guest language function with the `--sandbox.MaxASTDepth` option.
Only instrumentable nodes count towards the limit.
If the limit is exceeded, evaluation of the code fails and the context is canceled.
Inner contexts inherit the parent's limit.

The AST depth can give an estimate of the complexity and code size of a function as well as its stack frame size.
Limiting the AST depth can serve as a safeguard against arbitrary stack space usage by a single function.

### Limit the Number of Stack Frames

With the `--sandbox.MaxStackFrames` option you can specify the maximum number of frames a context can push on the stack.
Exceeding the limit results in cancellation of the context.
A thread-local stack frame counter is incremented on function enter and decremented on function return.
Also, inner contexts inherit the parent's limit, but do not count towards it.
Resetting resource limits does not affect the stack frame counter.

The stack frame limit in itself can serve as a safeguard against infinite recursion.
If used together with the AST depth limit, it can be used to estimate total stack space usage.

### Limit the Number of Active Threads

You can limit the number of threads that can be used by a context at the same point in time using the `--sandbox.MaxThreads` option.
By default, an arbitary number of threads can be used.
If a set limit is exceeded, entering the context fails with a PolyglotException and the polyglot context is canceled.
Resetting resource limits does not affect thread limits.

### Reset Resource Limits

With the [Polyglot API](https://www.graalvm.org/sdk/javadoc/) it is possible to reset the CPU time and statements count at any point in time using the [`Context.resetLimits`](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.html#resetLimits--) method.
This can be useful if a known and trusted initialization script should be excluded from limit.

```java
try (Context context = Context.newBuilder("js")
                           .allowExperimentalOptions(true)
                           .option("sandbox.MaxCPUTime", "500ms")
                       .build();) {
    try {
        context.eval("js", /* initialization script */);
        context.resetLimits();
        context.eval("js", /* user script */);
        assert false;
    } catch (PolyglotException e) {
        assert e.isCancelled();
        assert e.isResourceExhausted();
    }
}
```
