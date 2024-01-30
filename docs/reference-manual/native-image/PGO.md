---
layout: docs
toc_group: pgo
link_title: Profile-Guided Optimizations
permalink: /reference-manual/native-image/pgo/PGO
redirect_from: /reference-manual/native-image/PGO/
---

# Profile Guided Optimization for Native Image

One of the biggest advantages that JIT compilers have over AOT compilers is the ability to make use of the runtime observing the run time behaviour of the application they are compiling.
For example HotSpot keeps track of how many times each branch of an if statement in hot methods is executed.
Such information is passed on to the tier 2 JIT compiler (i.e. Graal) in the form of what we call a "profile" - a summary of how a method to be JIT compiled has been behaving so far during run time.
The JIT compiler then assumes that the method will continue to behave in the same manner and uses this "profile" as an additional source of information to better optimize that method.

AOT compilers typically don't have such information and are usually limited to a static view of the code they are compiling.
This means that, barring heuristics, an AOT compiler sees each branch of every if statement as equally likely to happen at run time, 
each method is as likely to be invoked as every other and each loop will repeat an equal number of times.
This puts the AOT compiler at a big disadvantage - lacking "profile" information makes it hard for the AOT compiler to reach the same quality of output as a JIT compiler.

Profile Guided Optimization described in this document is a technique for bringing profile information to an AOT compiler to improve the quality of it's output in terms of performance and size.

## What exactly is a "profile"?

As mentioned before, in the JIT compilation world, a profile is a summary of the behaviour of a particular method during run time.
The same holds for AOT compilers with the caveat that we have no runtime to provide this information to the compiler since compilation happens ahead-of-time - i.e. ahead-of-run-time.
This makes gathering the profile more challenging, but at a high level the content of the profile is very similar.
In practice, the profile is a summarised log of how many times certain events happened during run time.
These events are chosen based on what information will be useful for the compiler to make better decisions.
Examples of such events are:
- How many times was this method called?
- How many times did this if take the true branch? The false branch?
- How many times did this method allocate an object?
- How many times was `String` the run-time type of the value for the interface-typed argument of this method?

## How do I get a "profile"?

When running an application on a runtime with a JIT compiler the profiling of the application is handled by said runtime, with no extra steps for the developer.
While this is undoubtedly simpler the profiling that the runtime does is not free - in introduces overhead in execution of the code being profiled - 
both in terms of execution time and memory usage. 
This results in warmup issues - 
the application reaches predictable peak performance only after sufficient time has passed for the key parts of the application to be profiled and JIT compiled.
For long running applications this overhead usually pays for itself with the extra performance boost that comes after. 
On the other hand, for short lived applications and applications that need to start with good, predictable performance as soon as possible - this is a waste.

Gathering a profile for an AOT compiled application is more involved i.e. requires extra steps by the developer, but introduces no overhead to the final application.
Profiles need to be gathered by observing the application while it's running.
This is commonly done by building a special version of the application which includes in it counters for events of interest.
We call this an "instrumented build" because the process of adding said counters is called "instrumentation".
Naturally, the instrumented building of the application will not perform as good as the default build due to all the overhead of the counters, 
so it is not recommended to run instrumented images in production.
But, executing synthetic representative workloads on the instrumented build allows us to gather a representative profile of the application 
(just as the runtime would do for the JIT compiler).
Using that profile to help the AOT compiler produce what we call an "optimized build" of the application - 
a build where the compiler had both the static view and the dynamic profile of the application - which should preform better than the default build.

## How does a profile "guide" optimization?

Compiler optimizations often have to make decisions during compilation.
For example, the function inlining phase would, given the following method, have to decide which call site to inline and which not to.

```
private int run(String[] args) {
    if (args.length < 3) {
        return handleNotEnoughArguments(args);
    } else {
        return doActualWork(args);
    }
}
```

For illustrative purposes let's imagine that the inlining phase can only inline one of the calls.
Looking only at the static view of the code being compiled, both the `doActualWork` and `handleNotEnoughArguments` invocations look pretty much indistinguishable.
Barring any heuristics the phase would have to guess which is the better choice to inline.
Making the wrong choice can lead to a worse output from the compiler. 
Let's assume `run` is most commonly called with the right number of arguments at run time.
Then inlining `handleNotEnoughArguments` would increase the code size of the compilation unit without giving any performance benefit since the call to `doActualWork` needs to still be made every time.

Having a run-time profile of the application can give the compiler data with which differentiating between which call should be inlined is trivial.
For example, if our run-time profile recorded this if condition as being `false` 100 times and `true` 3 times - we probably should inline `doActualWork`.
This is the essence of PGO - using information from the profile i.e. from the run time behaviour of the application being compiled, 
to give the compiler grounding in data when making decisions.
The actual decisions and the actual events the profile records vary from phase to phase, but this illustrated the general idea.

Notice here that PGO expects a representative workload to be run on the instrumented build of the application.
Providing a counter-productive profile - i.e. a profile that records the exact opposite of the actual run-time behaviour of the app - will be counter-productive.
In our example this would be running the instrumented build with a workload that invokes the run method with too few arguments,
while the actual application does not.
This would lead to the inlining phase choosing to inline `handleNotEnoughArguments` reducing the performance of the optimized build.

Hence, the goal is to gather profiles on workload that match the production workloads as much as possible.
The gold standard for this is to run the exact same workloads we expect to run in production on the instrumented build.
Unfortunately, this is not always a simple thing to do, and running representative workloads is a big challenge in using PGO.

For an overview of how to use PGO for Native Image on an example application please consult the [Basic Usage of Profile-Guided Optimizations](PGO-Basic-Usage.md) document.
