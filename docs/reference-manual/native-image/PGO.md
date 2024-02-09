---
layout: docs
toc_group: pgo
link_title: Profile-Guided Optimizations
permalink: /reference-manual/native-image/pgo/PGO
redirect_from: /reference-manual/native-image/PGO/
---

# Profile Guided Optimization for Native Image

One of the advantages that JIT compilers have over AOT compilers is the ability to analyze the run-time behaviour of the application they are compiling.
For example, HotSpot keeps track of how many times each branch of an `if` statement is executed.
This information is passed to the tier-2 JIT compiler (i.e. Graal) as information that we call a profile.
The profile is a summary of how a particular method has been executing during run time.
The JIT compiler then assumes that the method will continue to behave in the same manner, and uses the information in the profile to optimize that method better.

AOT compilers typically do not have profiling information, and are usually limited to a static view of the code they are compiling.
This means that, barring heuristics, an AOT compiler sees each branch of every if statement as equally likely to happen at run time,
each method is as likely to be invoked as every other and each loop will repeat an equal number of times.
This puts the AOT compiler at a disadvantage - without profile information, it is hard to generate machine code of the same quality as a JIT compiler.

Profile Guided Optimization (PGO), described in this document,
is a technique that brings profile information to an AOT compiler to improve the quality of it's output in terms of performance and size.

## What is a _profile_?

As mentioned earlier, during JIT compilation, a profile is a summary of the run-time behaviour of the methods in the code.
The same is true for AOT compilers, with the caveat that we have no runtime to provide this information to the compiler since compilation happens ahead-of-time.
This makes the gathering of the profiles more challenging, but at a high level the content of the profile is very similar.
In practice, the profile is a summarised log of how many times certain events happened during run time.
These events are chosen based on what information will be useful for the compiler to make better decisions.
Examples of such events are:
- How many times was this method called?
- How many times did this `if`-statement take the true branch? How many times did it take the false branch?
- How many times did this method allocate an object?
- How many times was a `String` value passed to a particular `instanceof` check?

## How Do I Obtain a Profile of My Application?

When running an application on a runtime with a JIT compiler the profiling of the application is handled by the runtime environment, with no extra steps needed from the developer.
While this is undoubtedly simpler, the profiling that the runtime does is not free - it introduces execution overheads of the code being profiled -
both in terms of execution time and memory usage.
This causes warmup issues -
the application will reach predictable peak performance only after sufficient time has passed for the key parts of the application to be profiled and JIT compiled.
For long-running applications, this overhead usually pays for itself, yielding a performance boost later.
On the other hand, for short-lived applications and applications that need to start with good, predictable performance as soon as possible, this is counterproductive.

Gathering a profile for an AOT-compiled application is more involved, and requires extra steps by the developer, but introduces no overhead in the final application.
Here, profiles must be gathered by observing the application while it is running.
This is commonly done by compiling the application in a special mode that inserts *instrumentation code* into the application binary.
The instrumentation code increments counters for the events that are of interest to the profile.
We call this an *instrumented image*, and the process of adding these counters is called *instrumentation*.
Naturally, the instrumented image of the application will not be as performant as the default build due to the overhead of the instrumentation code,
so it is not recommended to regularly run instrumented images in production.
But, executing synthetic representative workloads on the instrumented build allows us to gather a representative profile of the application
(just as the runtime would do for the JIT compiler).
When building an optimized image, the AOT compiler has both the static view and the dynamic profile of the application -
an optimized image performs better than the default AOT-compiled image.

## How Does a Profile "Guide" Optimization?

Compiler optimizations often have to make decisions during compilation.
For example, in the following method, the function-inlining optimization needs to decide which call sites to inline, and which not.

```
private int run(String[] args) {
    if (args.length < 3) {
        return handleNotEnoughArguments(args);
    } else {
        return doActualWork(args);
    }
}
```

For illustrative purposes, let's imagine that the inlining optimization has a limit on how much code can be generated, and can hence only inline one of the calls.
Looking only at the static view of the code being compiled, both the `doActualWork` and `handleNotEnoughArguments` invocations look pretty much indistinguishable.
Without any heuristics, the phase would have to guess which is the better choice to inline.
However, making the incorrect choice can lead to code that is less efficient.
Let's assume that `run` is most commonly called with the right number of arguments at run time.
Then inlining `handleNotEnoughArguments` would increase the code size of the compilation unit without giving any performance benefit since the call to `doActualWork`
needs to still be made most of the time.

Having a run-time profile of the application can give the compiler data with which differentiating between which call should be inlined is trivial.
For example, if our run-time profile recorded this if condition as being `false` 100 times and `true` 3 times - we probably should inline `doActualWork`.
This is the essence of PGO - using information from the profile i.e. from the run time behaviour of the application being compiled,
to give the compiler grounding in data when making decisions.
The actual decisions and the actual events the profile records vary from phase to phase, but the preceding example illustrates the general idea.

Notice here that PGO expects a representative workload to be run on the instrumented build of the application.
Providing a counter-productive profile - i.e. a profile that records the exact opposite of the actual run-time behaviour of the app - will be counter-productive.
In our example this would be running the instrumented build with a workload that invokes the run method with too few arguments,
while the actual application does not.
This would lead to the inlining phase choosing to inline `handleNotEnoughArguments` reducing the performance of the optimized build.

Hence, the goal is to gather profiles on workload that match the production workloads as much as possible.
The gold standard for this is to run the exact same workloads we expect to run in production on the instrumented build.

For an overview of how to use PGO for Native Image on an example application please consult the [Basic Usage of Profile-Guided Optimizations](PGO-Basic-Usage.md) document.
