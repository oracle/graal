---
layout: docs
toc_group: pgo
link_title: Profile-Guided Optimization
permalink: /reference-manual/native-image/optimizations-and-performance/PGO/
---

# Profile-Guided Optimization

## What is Profile-Guided Optimization?

One advantage that a just-in-time (JIT) compiler has over an ahead-of-time (AOT) compiler is its ability to analyze the run-time behavior of an application.
For example, HotSpot keeps track of how many times each branch of an `if` statement is executed.
This information, called a "profile", is passed to a tier-2 JIT compiler (such as Graal).
The tier-2 JIT compiler then assumes that the `if` statement will continue to behave in the same manner, and uses the information from the profile to optimize that statement.

An AOT compiler typically does not have profiling information, and is usually limited to a static view of the code.
This means that, barring heuristics, an AOT compiler sees each branch of every `if` statement as equally likely to occur at run time; each method is as likely to be invoked as any other; and each loop repeats the same number of times.
This puts an AOT compiler at a disadvantage&mdash;without profiling information, it is difficult to generate machine code of the same quality as a JIT compiler.

Profile-Guided Optimization (PGO) is a technique that brings profile information to an AOT compiler to improve the quality of its output in terms of performance and size.

> Note: PGO is not available in GraalVM Community Edition.

### What Is a _Profile_?

A profile is a summarized log of how many times certain events occurred during an application's run time.
The events are chosen according to which information can be useful for the compiler to make better decisions.
Examples include:
- How many times was this method called?
- How many times did this `if` statement take the `true` branch? How many times did it take the `false` branch?
- How many times did this method allocate an object?
- How many times was a `String` value passed to a particular `instanceof` check?

## How Do I Obtain a Profile of My Application?

When running an application on a JVM with a JIT compiler, the profiling of the application is handled by the runtime environment, with no extra steps needed from a developer.
However, creating a profile adds execution time and memory usage overheads to the performance of the application that is being profiled.
This causes warmup issues: the application will reach predictable peak performance only after sufficient time has passed for the application code to be profiled and JIT-compiled.
For long-running applications, this overhead usually pays for itself, yielding a performance boost later.
On the other hand, for short-lived applications and applications that need to start with predictable performance as soon as possible, this is counterproductive.

Gathering a profile for an AOT-compiled application is more involved, and requires extra steps by the developer, but introduces no overhead in the final application.
A profile must be gathered by observing the application while it is running.
This is commonly achieved by compiling the application in a special mode that inserts *instrumentation code* into the application binary.
The instrumentation code increments counters for the events that are of interest to the profile.
A binary that includes instrumentation code is then called an *instrumented binary*, and the process of adding these counters is called *instrumentation*.

Naturally, an instrumented binary of the application is not as performant as a default binary due to the overhead of the instrumentation code, so it is not recommended to run it in production.
But, running synthetic representative workloads on the instrumented binary provides a representative profile of the application's behavior.
When building an optimized application, the AOT compiler has both a static view and a dynamic profile of the application.
Thus, the optimized application performs better than the default AOT-compiled application.

## How Does a Profile "Guide" Optimization?

During compilation, a compiler has to make decisions about optimizations.
For example, in the following method, the function-inlining optimization needs to decide which call sites to inline, and which not.

```java
private int run(String[] args) {
    if (args.length < 3) {
        return handleNotEnoughArguments(args);
    } else {
        return doActualWork(args);
    }
}
```

For illustrative purposes, imagine that the inlining optimization has a limit on how much code can be generated, and can hence only inline one of the calls.
Looking only at the static view of the code being compiled, both the `doActualWork()` and `handleNotEnoughArguments()` invocations look pretty much indistinguishable.
Without any heuristics, the phase would have to guess which is the better choice to inline.
However, making the incorrect choice can lead to code that is less efficient.
Assume that `run()` is most commonly called with the right number of arguments at run time, then inlining `handleNotEnoughArguments` would increase the code size of the compilation unit without giving any performance benefit since the call to `doActualWork()`
needs to still be made most of the time.

Having a run-time profile of the application can give the compiler data to differentiate between the calls.
For example, if the run-time profile recorded the `if` condition as being `false` 100 times and `true` 3 times, then it should inline `doActualWork()`. 
This is the essence of PGO - using information from the profile to give the compiler grounding in data when making certain decisions.
The actual decisions and the actual events the profile records vary from phase to phase, but the preceding example illustrates the general idea.

Notice that PGO expects a representative workload to be run on the instrumented binary of the application.
Providing a counter-productive profile (a profile that records the exact opposite of the actual runtime behavior of the application) will be counter-productive.
For the above example, this would be running the instrumented binary with a workload that invokes the `run()` method with too few arguments, while the actual application does not.
This would lead to the inlining phase choosing to inline `handleNotEnoughArguments` reducing the performance of the optimized binary.

Hence, the goal is to gather profiles on workload that match the production workloads as much as possible.
The gold standard for this is to run the exact same workloads you expect to run in production on the instrumented binary.

For a more detailed usage overview, go to [Basic Usage of Profile-Guided Optimization](PGO-Basic-Usage.md) documentation.

### Further Reading

* [Basic Usage of Profile-Guided Optimization](PGO-Basic-Usage.md)
* [Inspecting a Profile in a Build Report](PGO-Build-Report.md)
* [Creating LCOV Coverage Reports](PGO-LCOV.md)
* [Merging Profiles from Multiple Sources](PGO-Merging-Profiles.md)
* [Tracking Profile Quality Over Time](PGO-Profile-Quality.md)
* [The _iprof_ File Format](PGO-IprofFileFormat.md)
* [The _iprof_ JSON Schema](assets/iprof-v1.0.0.schema.json)
* [Frequently Asked Questions](PGO-FAQ.md)