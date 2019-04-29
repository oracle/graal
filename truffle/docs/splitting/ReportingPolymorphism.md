# Reporting Polymorphic Specializations to the Runtime

This document gives a short overview of what is required of language
implementers in order to leverage the monomorphization (splitting) strategy.
More information on how it works can be found in the [Splitting](Splitting.md)
file.

In simple terms, the monomorphization heuristic relies on the language
reporting polymorphic specializations for each node that could potentially be
returned to a monomorphic state through splitting. In this context a
polymorphic specialization is any node rewriting which results in the node
changing "how polymorphic" it is. This includes, but is not limited to,
activating another specialization, increasing the number of instances of an
active specialization, excluding a specialization, etc. 

## Manual Reporting of Polymorphic Specializations 

To facilitate reporting of polymorphic specializations we introduce a new API
into the `Node` class:
[Node#reportPolymorphicSpecialize](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/Node.html#reportPolymorphicSpecialize).
This method can be used to manually report polymorphic specializations, but only
in cases when this cannot be automated by using the DSL.

## Automated Reporting of Polymorphic Specializations

Since the Truffle DSL automates much of the transitions between specializations,
we added the `@ReportPolymorphism` [annotation for automated reporting of
polymorphic
specializations](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/dsl/ReportPolymorphism.html).
This annotation instructs the DSL include checks for polymorphism after
specializations and too call `Node#reportPolymorphicSpecialize` if needed.

For an example on how to use this annotation, consider the
`com.oracle.truffle.sl.nodes.SLStatementNode`. It is the base class for all
Simple Language nodes and, since the `ReportPolymorphism` annotation is
inherited, simply annotating this class will enable reporting of polymorphic
specializations for all Simple Language nodes. Bellow is the diff of the change
that adds this annotation to he `SLStatementNode`

```
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

Applying the `ReportPolymorphism` annotation to all nodes of a language is the
simplest way to facilitate the monomorphization, but it could cause reporting
of polymorphic specializations in cases where that does not necessarily make
sense. In order to give the language developer more control over which nodes
and which specializations are taken into consideration for reporting
polymorphism we introduced the `@ReportPolymorphism.Exclude`
[annotation](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/dsl/ReportPolymorphism.Exclude.html)
which is applicable to classes (disabling automated reporting for the entire
class) or to individual specializations (excluding those specializations from
consideration when checking for polymorphism).

### Tools Suppot

Knowing which nodes should and should not report polymorphic specializations is
for the language developer to conclude through either knowledge of the language
in question, or though experimentation with the effects of including/excluding
particular nodes/specializations. To aid language developers in better
understanding the impact of reporting polymorphic specializations we provide
some tool support that could be useful for this task.

#### Tracing individual splits

Adding the `-Dgraal.TraceTruffleSplitting=true` argument to the command line
when executing your guest language code will, in real time, print information
about each split the runtime makes. 

A small part of the output from running one of the JavaScript benchmarks with
said flag enabled follows.

```
...
[truffle] split   0-37d4349f-1     multiplyScalar |ASTSize      40/   40 |Calls/Thres       2/    3 |CallsAndLoop/Thres       2/ 1000 |Inval#              0 |SourceSection octane-raytrace.js~441-444:12764-12993
[truffle] split   1-2ea41516-1     :anonymous |ASTSize       8/    8 |Calls/Thres       3/    3 |CallsAndLoop/Thres       3/ 1000 |Inval#              0 |SourceSection octane-raytrace.js~269:7395-7446
[truffle] split   2-3a44431a-1     :anonymous |ASTSize      28/   28 |Calls/Thres       4/    5 |CallsAndLoop/Thres       4/ 1000 |Inval#              0 |SourceSection octane-raytrace.js~35-37:1163-1226
[truffle] split   3-3c7f66c4-1     Function.prototype.apply |ASTSize      18/   18 |Calls/Thres       7/    8 |CallsAndLoop/Thres       7/ 1000 |Inval#              0 |SourceSection octane-raytrace.js~36:1182-1219
...
```
#### Tracing a splitting summary

Adding the `-Dgraal.TruffleTraceSplittingSummary=true` argument to the command
line when executing your guest language code will, after the execution is
complete, print out a summary of the gathered data regarding splitting. This
includes how many splits there were, how large is the splitting budget and how
much of it was used, how many splits were forced, a list of split target names
and how many times they were split and a list of nodes that reported polymorphic
specializations and how many. 

A slightly simplified output of running one of the JavaScript benchmarks with
said flag enabled follows.

```
[truffle] Split count                             :       9783
[truffle] Split limit                             :      15342
[truffle] Split count                             :          0
[truffle] Split limit                             :        574
[truffle] Splits                                  :        591
[truffle] Forced splits                           :          0
[truffle] Nodes created through splitting         :       9979
[truffle] Nodes created without splitting         :      10700
[truffle] Increase in nodes                       :     93.26%
[truffle] Split nodes wasted                      :        390
[truffle] Percent of split nodes wasted           :      3.91%
[truffle] Targets wasted due to splitting         :         27
[truffle] Total nodes executed                    :       7399

[truffle] --- SPLIT TARGETS
[truffle] initialize                              :         60
[truffle] Function.prototype.apply                :        117
[truffle] Array.prototype.push                    :          7
[truffle] initialize                              :          2
[truffle] magnitude                               :         17
[truffle] :anonymous                              :        117
[truffle] add                                     :          5
...

[truffle] --- NODES
[truffle] class ANode                             :         42
[truffle] class AnotherNode                       :        198
[truffle] class YetAnotherNode                    :          1
...
```
#### Dumping polymorphic specializations to IGV

NOTE: Consider reading [Splitting](Splitting.md) before this section, as the
dumped data is directly related to how splitting works.

Adding the `-Dgraal.TruffleDumpPolymorphicSpecialize=true` argument to the command
line when executing your guest language code will, every time a call target is
marked "needs split" dump a graph showing all the callers of the call target in
question, as well as a chain of nodes (linked by child connections as well as
direct call node to callee root node links) ending in the node that called
`Node#reportPolymorphicSpecialize`.

