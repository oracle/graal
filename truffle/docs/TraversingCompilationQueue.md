---
layout: docs
toc_group: truffle
link_title: Truffle Approach to the Compilation Queue
permalink: /graalvm-as-a-platform/language-implementation-framework/TraversingCompilationQueue/
---
# Truffle Approach to the Compilation Queue

As of version 21.2.0 Truffle has a new approach to compilation queueing.
This document gives motivation and an overview of this approach.

## What is a Compilation queue?

During execution of guest code each Truffle call target counts how many times it was executed as well as how many loop iterations happened during those executions (i.e. the target's "call and loop count").
Once this counter reaches a certain threshold the call target is deemed "hot" and scheduled for compilation.
In order to minimize the impact this has on the execution of the guest code the notion that the target should be compiled is made concrete as a [compilation task](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.runtime/src/com/oracle/truffle/runtime/CompilationTask.java) and placed into a [compilation queue](https://github.com/oracle/graal/blob/master/compiler/src/com.oracle.truffle.runtime/src/com/oracle/truffle/runtime/BackgroundCompileQueue.java) to await compilation.
The Truffle runtime spawns several compiler threads (`--engine.CompilerThreads`) that take tasks from the queue and compile the specified call targets.

The initial implementation of the compilation queue in Truffle was a straightforward FIFO queue.
This approach has important limitations with respect to warmup characteristics of the guest code execution.
Namely, not all call targets are equally important to compile.
The aim is to identify targets which account for more execution time and compile them first, thus reaching better performance sooner.
Since call targets are queued for compilation when a counter reaches a certain threshold a FIFO queue would compile targets in order of reaching that threshold, which in practise does not correlate to actual execution time.

Consider the following toy JavaScript example:

```js
function lowUsage() {
    for (i = 0; i < COMPILATION_THRESHOLD; i++) {
        // Do something
    }
}

function highUsage() {
    for (i = 0; i < 100 * COMPILATION_THRESHOLD; i++) {
        // Do something
    }
}

while(true) {
    lowUsage();
    highUsage();
}
```

Both the `lowUsage` and the `highUsage` function will reach a high enough call and loop count threshold even on the first execution, but the `lowUsage` function will reach it first.
Using a FIFO queue, we would compile the `lowUsage` function first, even though this example illustrates that the `highUsage` function should be compiled first in order to reach better performance sooner.

## Traversing Compilation Queue

The new compilation queue in Truffle, colloquially called ["Traversing Compilation Queue"](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.runtime/src/com/oracle/truffle/runtime/TraversingBlockingQueue.java), takes a more dynamic approach to selecting the order in which targets are compiled.
Every time a compiler thread requests the next compilation task the queue will traverse all the entries in the queue and pick the one with the highest priority.

A task's priority is [determined based on several factors](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.runtime/src/com/oracle/truffle/runtime/CompilationTask.java#L209).

For starters, targets scheduled for [first-tier compilation](https://medium.com/graalvm/multi-tier-compilation-in-graalvm-5fbc65f92402) (i.e. first-tier tasks) always have higher priority than second-tier tasks.
The rational behind this is that performance difference between executing code in the interpreter and executing it in first-tier compiled code is much greater then the difference between tier-one and tier-two compiled code, meaning that we get more benefit from compiling these targets sooner.
Also, first-tier compilations are usually take less time, thus one compiler thread can finish multiple first-tier compilations in the same time it takes to complete one second-tier compilation.
This approach has been shown to underperform in certain scenarios and might be improved upon in the coming versions.

When comparing two tasks of the same tier, we first consider their compilation history and give priority to tasks which were previously compiled with a higher compiler tier.
For example, if a call target get first-tier compiled, then gets invalidated for some reason and then gets queued for a first-tier compilation again, it takes priority over all other first tier targets that have never before been compiled.
The reasoning is that if it was previously compiled, it is obviously important and should not be penalized more than necessary by its invalidation.

Finally, if the two previous conditions can't differentiate the priority between two tasks we give priority to the task with the higher "weight".
The weight is a function of the target's call and loop count and time.
It is defined as a product of the target's call and loop count with the rate at which that call and loop count has grown in the past 1ms.
Using the target's call and loop count as a proxy for amount of time spent executing that call target, this metric aims to balance total time spent executing that call target with the recent growth of that time.
This gives a priority boost to targets that are currently "very hot" when comparing to targets that were "hot" but are not being executed a lot currently.

For performance reasons the weight for tasks is cached and reused for a period of 1ms. If the cached value is older than 1ms, it is recalculated.

The traversing compilation queue is on by default as of version 21.2.0 and can be disabled using `--engine.TraversingCompilationQueue=false`.

## Dynamic Compilation Thresholds

One problem of the traversing compilation queue is that it needs to traverse all the entries in the queue to get up-to-date weights and choose the highest priority task.
This does not have a significant performance impact as long as the size of the queue remains reasonable.
This means that in order to always choose the highest priority task in a reasonable about of time we need to ensure that the queue does not grow indefinitely.

This is achieved by an approach we call ["dynamic compilation thresholds"](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.runtime/src/com/oracle/truffle/runtime/DynamicThresholdsQueue.java).
Simply put, dynamic compilation thresholds means that the compilation threshold (the one each call target's call and loop count is compared against when determining whether to compile it) may change over time depending on the state of the queue.
If the queue is overloaded we aim to increase the compilation thresholds to reduce the number of incoming compilation tasks, i.e. targets need to be "more hot" to get scheduled for compilation.
On the other hand, if the queue is close to empty, we can reduce the compilation thresholds to allow more targets to get scheduled for compilation, i.e. the compilation threads are in danger of idling so let's give them even "less hot" targets to compile.

We call this changing of the thresholds "scaling" as the thresholds are in practice just multiple by a "scale factor" determined by a `scale` function.
The scale function takes as input the "load" of the queue, which is the number of tasks in the queue divided by the number of compiler threads.
We intentionally control for the number of compiler threads since the raw number of tasks in the queue is not a good proxy of how much compilation pressure there is.
For example, let's assume that an average compilation takes 100ms and that there are 160 tasks in the queue.
A runtime with 16 threads will finish all the tasks in approximately `10 * 100ms` i.e. 1 second.
On the other hand, a runtime with 2 compiler thread will take approximately `80 * 100ms`, i.e. 8 seconds.

The scale function is defined by 3 parameters: `--engine.DynamicCompilationThresholdsMinScale`, `--engine.DynamicCompilationThresholdsMinNormalLoad` and `DynamicCompilationThresholdsMaxNormalLoad`.

The `--engine.DynamicCompilationThresholdsMinScale` option defines how low we are willing to scale the thresholds.
It has a default value of 0.1, meaning that the compilation thresholds will never be scaled below 10% of their default value.
This in practice means that, by definition, `scale(0) = DynamicCompilationThresholdsMinScale` or for default values `scale(0) = 0.1`

The `--engine.DynamicCompilationThresholdsMinNormalLoad` option defines the minimal load at which compilation thresholds will not be scaled.
This means that as long as the load of the queue is above this value the runtime will not *scale down* the compilation thresholds.
This in practice means that, by definition, `scale(DynamicCompilationThresholdsMinScale) = 1` or for default values `scale(10) = 1`

The `--engine.DynamicCompilationThresholdsMaxNormalLoad` option defines the maximal load at which compilation thresholds will not be scaled.
This means that as long as the load of the queue is below this value the runtime will not *scale up* the compilation thresholds.
This in practice means that, by definition, `scale(DynamicCompilationThresholdsMaxScale) = 1` or for default values `scale(90) = 1`

So far we've defined the `scale` function at 3 points.
For all values between those points the `scale` function is a straight line connecting those two points.
This means that for all values between the minimal and maximal normal load the scale function is 1 by definition.
For values between 0 and the minimal normal load the `scale` function grows linearly between the minimal scale and 1.
Let's define the slope of this function as `s`.
Now, for the remainder of the functions domain, i.e. the values greater than the maximum normal load, we define `scale` to be a linear function with slope `s` passing through the point `(DynamicCompilationThresholdsMaxNormalLoad, 1)`.

The following is an ASCII art plot of the scale function which should illustrate how it's defined.

```
          ^ scale
          |
          |                                            /
          |                                           /
          |                                          /
          |                                         /
          |                                        /
          |                                       /
        1 |..... ________________________________/
          |     /.                               .
          |    / .                               .
          |   /  .                               .
          |  /   .                               .
          | /    .                               .
MinScale >|/     .                               .
          |      .                               .
          |_______________________________________________________> load
         0       ^                               ^
              MinNormalLoad                   MaxNormalLoad
```

The dynamic thresholds only work with the traversing compilation queue and are on by default as of version 21.2.0.
They can be disabled with `--engine.DynamicCompilationThresholds=false`.
