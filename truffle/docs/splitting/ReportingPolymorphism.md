---
layout: docs
toc_group: truffle
link_title: Reporting Polymorphic Specializations to Runtime
permalink: /graalvm-as-a-platform/language-implementation-framework/splitting/ReportingPolymorphism/
---
# Reporting Polymorphic Specializations to Runtime

This guide gives an overview of what is required of language implementers in order to leverage the monomorphization (splitting) strategy.
More information on how it works can be found in the [Splitting](Splitting.md) guide.

In simple terms, the monomorphization heuristic relies on the language reporting polymorphic specializations for each node that could potentially be returned to a monomorphic state through splitting.
In this context a polymorphic specialization is any node rewriting which results in the node changing "how polymorphic" it is. This includes, but is not limited to, activating another specialization, increasing the number of instances of an active specialization, excluding a specialization, etc..

## Manual Reporting of Polymorphic Specializations

To facilitate reporting of polymorphic specializations, a new API was introduced
into the `Node` class: [Node#reportPolymorphicSpecialize](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/Node.html#reportPolymorphicSpecialize).
This method can be used to manually report polymorphic specializations, but only in cases when this cannot be automated by using the DSL.

## Automated Reporting of Polymorphic Specializations

Since the Truffle DSL automates much of the transitions between specializations, the `@ReportPolymorphism` [annotation for automated reporting of polymorphic specializations](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/dsl/ReportPolymorphism.html) was added.
This annotation instructs the DSL to include checks for polymorphism after specializations and to call `Node#reportPolymorphicSpecialize` if needed.

For an example on how to use this annotation, consider the `com.oracle.truffle.sl.nodes.SLStatementNode`. It is the base class for all
SimpleLanguage nodes and, since the `ReportPolymorphism` annotation is inherited, simply annotating this class will enable reporting of polymorphic specializations for all SimpleLanguage nodes.
Below is the diff of the change that adds this annotation to `SLStatementNode`:

```shell
diff --git
a/truffle/src/com.oracle.truffle.sl/src/com/oracle/truffle/sl/nodes/SLStatementNode.java
b/truffle/src/com.oracle.truffle.sl/src/com/oracle/truffle/sl/nodes/SLStatementNode.java
index 788cc20..89448b2 100644
---
a/truffle/src/com.oracle.truffle.sl/src/com/oracle/truffle/sl/nodes/SLStatementNode.java
+++
b/truffle/src/com.oracle.truffle.sl/src/com/oracle/truffle/sl/nodes/SLStatementNode.java
@@ -43,6 +43,7 @@ package com.oracle.truffle.sl.nodes;
 import java.io.File;

 import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
+import com.oracle.truffle.api.dsl.ReportPolymorphism;
 import com.oracle.truffle.api.frame.VirtualFrame;
 import com.oracle.truffle.api.instrumentation.GenerateWrapper;
 import com.oracle.truffle.api.instrumentation.InstrumentableNode;
@@ -62,6 +63,7 @@ import com.oracle.truffle.api.source.SourceSection;
  */
 @NodeInfo(language = "SL", description = "The abstract base node for all SL
statements")
 @GenerateWrapper
+@ReportPolymorphism
 public abstract class SLStatementNode extends Node implements
InstrumentableNode {

     private static final int NO_SOURCE = -1;
```

### Controlling Automated Reporting of Polymorphic Specializations

#### Excluding particular nodes and specializations

Applying the `ReportPolymorphism` annotation to all nodes of a language is the simplest way to facilitate the monomorphization, but it could cause reporting of polymorphic specializations in cases where that does not necessarily make sense.
In order to give the language developer more control over which nodes and which specializations are taken into consideration for reporting polymorphism, the `@ReportPolymorphism.Exclude` [annotation](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/dsl/ReportPolymorphism.Exclude.html) was introduced which is applicable to classes (disabling automated reporting for the entire class) or to individual specializations (excluding those specializations from consideration when checking for polymorphism).

#### Reporting only on Megamorphic Cases

As of version 20.3.0 a new annotation was added: [ReportPolymorphism.Megamorphic](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/dsl/ReportPolymorphism.Megamorphic.html).
This annotation can only be applied to specializations, as marks that specialization as megamorphic as it is intended to be used on expensive "generic" specializations that should be fixed by monomorphization.
The effect of adding this annotation is that, once the annotated specialisation becomes active, the node will report polymorphism to the runtime independent of the state of other specializations.

This annotation can be used separately from `@ReportPolymorphism`, i.e., a node does *not* need to be annotated with `@ReportPolymorphism` for the megamorphic annotation to work.
If both annotations are used, then both polymorphic and megamorphic activations will be reported as polymorphism.

### Tools Support

Knowing which nodes should and should not report polymorphic specializations is for the language developer to conclude.
This can be done either through domain knowledge (which nodes of the language are expensive when polymorphic), or through experimentation (measuring the effects of including/excluding particular nodes/specializations).
To aid language developers in better understanding the impact of reporting polymorphic specializations some tools support was provided.

#### Tracing individual splits

Adding the `--engine.TraceSplitting` argument to the command line when executing your guest language code will, in real time, print information about each split the runtime makes.

A small part of the output from running one of the JavaScript benchmarks with the flag enabled follows.

```shell
...
[engine] split   0-37d4349f-1     multiplyScalar |ASTSize      40/   40 |Calls/Thres       2/    3 |CallsAndLoop/Thres       2/ 1000 |Inval#              0 |SourceSection octane-raytrace.js~441-444:12764-12993
[engine] split   1-2ea41516-1     :anonymous |ASTSize       8/    8 |Calls/Thres       3/    3 |CallsAndLoop/Thres       3/ 1000 |Inval#              0 |SourceSection octane-raytrace.js~269:7395-7446
[engine] split   2-3a44431a-1     :anonymous |ASTSize      28/   28 |Calls/Thres       4/    5 |CallsAndLoop/Thres       4/ 1000 |Inval#              0 |SourceSection octane-raytrace.js~35-37:1163-1226
[engine] split   3-3c7f66c4-1     Function.prototype.apply |ASTSize      18/   18 |Calls/Thres       7/    8 |CallsAndLoop/Thres       7/ 1000 |Inval#              0 |SourceSection octane-raytrace.js~36:1182-1219
...
```
#### Tracing a splitting summary

Adding the `--engine.TraceSplittingSummary` argument to the command line when executing your guest language code will, after the execution is complete, print out a summary of the gathered data regarding splitting.
This includes how many splits there were, how large is the splitting budget and how much of it was used, how many splits were forced, a list of split target names and how many times they were split and a list of nodes that reported polymorphic specializations and how many.

A slightly simplified output of running one of the JavaScript benchmarks with the flag enabled follows.

```shell
[engine] Splitting Statistics
Split count                             :       9783
Split limit                             :      15342
Split count                             :          0
Split limit                             :        574
Splits                                  :        591
Forced splits                           :          0
Nodes created through splitting         :       9979
Nodes created without splitting         :      10700
Increase in nodes                       :     93.26%
Split nodes wasted                      :        390
Percent of split nodes wasted           :      3.91%
Targets wasted due to splitting         :         27
Total nodes executed                    :       7399

--- SPLIT TARGETS
initialize                              :         60
Function.prototype.apply                :        117
Array.prototype.push                    :          7
initialize                              :          2
magnitude                               :         17
:anonymous                              :        117
add                                     :          5
...

--- NODES
class ANode                             :         42
class AnotherNode                       :        198
class YetAnotherNode                    :          1
...
```

#### Tracing polymorphic specializations

Consider reading the [Splitting](Splitting.md) guide before this section, as the dumped data is directly related to how splitting works.

To better understand how reporting polymorphism impacts which call targets are considered for splitting one can use the `--engine.SplittingTraceEvents` option.
This option will print, in real time, a log detailing which nodes are reporting polymorphism and how that is affecting the call targets.
See the following examples.

##### Example 1

```shell
[engine] [poly-event] Polymorphic event! Source: JSObjectWriteElementTypeCacheNode@e3c0e40   WorkerTask.run
[engine] [poly-event] Early return: false callCount: 1, numberOfKnownCallNodes: 1            WorkerTask.run
```

This log section tells that the `JSObjectWriteElementTypeCacheNode` in the `WorkerTask.run` method turned polymorphic and reported it.
It also tells that this is the first time that `WorkerTask.run` is being executed (`callCount: 1`), thus you do not mark it as "needs split" (`Early return: false`)

##### Example 2

```shell
[engine] [poly-event] Polymorphic event! Source: WritePropertyNode@50313382                  Packet.addTo
[engine] [poly-event] One caller! Analysing parent.                                          Packet.addTo
[engine] [poly-event]   One caller! Analysing parent.                                        HandlerTask.run
[engine] [poly-event]     One caller! Analysing parent.                                      TaskControlBlock.run
[engine] [poly-event]       Early return: false callCount: 1, numberOfKnownCallNodes: 1      Scheduler.schedule
[engine] [poly-event]     Return: false                                                      TaskControlBlock.run
[engine] [poly-event]   Return: false                                                        HandlerTask.run
[engine] [poly-event] Return: false                                                          Packet.addTo
```

In this example the source of the polymorphic specialization is `WritePropertyNode` in `Packet.addTo`.
Since this call target has only one known caller, you can analyse its parent in the call tree (i.e., the caller).
This is, in the example, `HandlerTask.run` and the same applies to it as well, leading to `TaskControlBlock.run`, and by the same token to `Scheduler.schedule`.
`Scheduler.schedule` has a `callCount` of 1, i.e., this is its first execution, so you do not mark it as "needs split" (`Early return: false`).

##### Example 3

```shell
[engine] [poly-event] Polymorphic event! Source: JSObjectWriteElementTypeCacheNode@3e44f2a5  Scheduler.addTask
[engine] [poly-event] Set needs split to true                                                Scheduler.addTask
[engine] [poly-event] Return: true                                                           Scheduler.addTask
```

In this example the source of the polymorphic specialization is `JSObjectWriteElementTypeCacheNode` in `Scheduler.addTask`.
This call target is immediately marked as "needs split", since all the criteria to do so are met.

##### Example 3

```shell
[engine] [poly-event] Polymorphic event! Source: WritePropertyNode@479cbee5                  TaskControlBlock.checkPriorityAdd
[engine] [poly-event] One caller! Analysing parent.                                          TaskControlBlock.checkPriorityAdd
[engine] [poly-event]   Set needs split to true                                              Scheduler.queue
[engine] [poly-event]   Return: true                                                         Scheduler.queue
[engine] [poly-event] Set needs split to true via parent                                     TaskControlBlock.checkPriorityAdd
[engine] [poly-event] Return: true                                                           TaskControlBlock.checkPriorityAdd
```

In this example the source of the polymorphic specialization is `WritePropertyNode` in `TaskControlBlock.checkPriorityAdd`.
Since it has only one caller, you look at that caller (`Scheduler.queue`), and since all the criteria necessary seem to be met, you mark it as "needs split".

#### Dumping polymorphic specializations to IGV

Consider reading the [Splitting](Splitting.md) guide before this section, as the dumped data is directly related to how splitting works.

Adding the `--engine.SplittingDumpDecisions` argument to the command line when executing your guest language code will, every time a call target is marked "needs split", dump a graph showing a chain of nodes (linked by child connections as well as direct call node to callee root node links) ending in the node that called `Node#reportPolymorphicSpecialize`.
