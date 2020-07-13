# The Truffle approach to function inlining

Truffle provides automated inlining for all languages built with the framework.
Since the 20.2.0 release we introduced a new approach to inlining. This
document describes how the new approach works, compares it to the legacy
inlining approach and motivates the design choices made for the new approach.

## Inlining in a nutshell

In short: Inlining is the process of replacing a call to a function with the
body of that function. 

This removes the overhead of the call but more importantly it opens up more
optimization opportunities for later phases of the compiler.  The down side of
the process is that the size of the compilation grows with each inlined
function. Overly large compilation units are hard to optimize and there is
finite memory for installing code. 

Because of all this, choosing which functions to inline is a delicate trade-off
between the expected gains of inlining a function versus the cost of the
increase of the size of the compilation unit.

## The issue with Truffle legacy inlining

Truffle has had an approach to inlining for quite a while. Unfortunately, this
early approach suffered from multiple issues, main one being that it relied on
the number of Truffle AST Nodes in a call target to approximate the size of the
call target. 

AST nodes are a very poor proxy for actual code size of the call target since
there is no guarantee how much code a single AST node will produce. For
example, an addition node specialized for adding two integers will produce
significantly less code than that same node if specialized for adding integers,
doubles and strings. Not to mention a different node and not to mention nodes
from different languages. This made it impossible to have a single inlining
approach that would work reliably across all the Truffle languages. 

One notable thing about the legacy inlining is that, since it only uses
information from the AST, inlining decisions are made before partial evaluation
begins. This means that we only ever partially evaluate call targets that we
decided to inline. The advantage of this approach is that no time is spend on
partial evaluation of call targets that don't end up being inlined. On the
other hand this resulted in frequent compilation problems stemming from the
poor decisions made by the inliner e.g. the resulting compilation unit would be
too big to compile. 

## Language-agnostic inlining

The main design goal of the new inlining approach is to use the number of Graal
nodes after partial evaluation as a proxy for call target size. This is a much
better size proxy since partial evaluation removes all the abstractions of the
AST and results in a graph that is much closer to the low-level instructions
that the call target actually performs. This results in a more precise cost
model when deciding whether or not to inline a call target and removes much of
the language specific information that the AST carries (hence the name:
Language-agnostic inlining). 

This is achieved by performing partial evaluation on every candidate call
target and then making the inlining decision after that (as opposed to the
legacy inlining which made decisions before doing any partial evaluation). Both
the amount of partial evaluation that will be done as well as the amount that
will be inlined are controlled by a notion of a budget. These are the
"exploration budget" and "inlining budget" respectively, both expressed in
terms of Graal node counts.

The downside of this approach is that we need to do partial evaluation even on
call targets which we ultimately decide not to inline.  This results in a
measurable increase in average compilation time compared to legacy inlining
(aprox. 10%). 

## Observing and impacting the inlining

The inliner keeps an internal call tree to keep track of the states of
individual calls to targets, as well as the inlining decisions that were made.
The following sections explain the states in which calls in the call tree can
be, as well as how to find out which decisions were made during compilations.

### Call tree states

[Nodes](../../compiler/src/org.graalvm.compiler.truffle.compiler/src/org/graalvm/compiler/truffle/compiler/phases/inlining/CallNode.java)
in the inline [call
tree](../../compiler/src/org.graalvm.compiler.truffle.compiler/src/org/graalvm/compiler/truffle/compiler/phases/inlining/CallTree.java)
represent *calls* to particular targets. This means that if one target calls
another twice, we will see this as two nodes despite it being the same call
target.

Each node can be in one of 6 states explained here:
* *Inlined* - This state means that the call was inlined. Initially, only the
  root of the compilation is in this state since it is implicitly "inlined"
(i.e. part of the compilation unit).
* *Cutoff* - This state means that the call target was not partially evaluated,
  thus was not even considered for inlining. This is normally due to the
inliner hitting its exploration budget limitations 
* *Expanded* - This state means that the call target was partially evaluated
  (thus, considered for inlining) but a decision was made not to inline. This
could be due to inlining budget limitations or the target being deemed too
expensive to inline (e.g. inlining a small target with multiple outgoing
"Cutoff" calls would just introduce more calls to the compilation unit).
* *Removed* - This state means that this call is present in the AST but partial
  evaluation removed the call. This is an advantage over the legacy inlining
which made the decisions ahead of time and had no way of noticing such
situations.
* *Indirect* - This state denotes an indirect call. We cannot inline indirect
  call.
* *BailedOut* - This state should be very rare and is considered a performance
  problem. It means that partial evaluation of the target resulted in a
`BailoutException` i.e. could not be completed successfully. This means there is
some problem with that particular target, but rather than quit the entire
compilation we treat that call as not possible to inline.

### Tracing inlining decisions

Truffle provides an engine option to trace the final state of the call tree,
including a lot of accompanying data, during compilation. This option is
`TraceInlining` and can be set in all the usual ways: by adding
`--engine.TraceInlining=true` to the command line of Truffle language
launchers, adding `-Dpolyglot.engine.TraceInlining=true` to the command line if
running a regular java program that executes Truffle languages, or [setting the
option explicitly for an
engine](https://www.graalvm.org/truffle/javadoc/org/graalvm/polyglot/Engine.Builder.html#option-java.lang.String-java.lang.String-)

Here is an example output of TraceInlining for a JavaScript function:

```
[engine] inline start     M.CollidePolygons                                           |call diff        0.00 |Recursion Depth      0 |Explore/inline ratio     1.07 |IR Nodes        27149 |Frequency        1.00 |Truffle Callees     14 |Forced          false |Depth               0
[engine] Inlined            M.FindMaxSeparation <opt>                                 |call diff       -8.99 |Recursion Depth      0 |Explore/inline ratio      NaN |IR Nodes         4617 |Frequency        1.00 |Truffle Callees      7 |Forced          false |Depth               1
[engine] Inlined              parseInt <opt>                                          |call diff       -1.00 |Recursion Depth      0 |Explore/inline ratio      NaN |IR Nodes          111 |Frequency        1.00 |Truffle Callees      0 |Forced           true |Depth               2
[engine] Inlined              M.EdgeSeparation                                        |call diff       -3.00 |Recursion Depth      0 |Explore/inline ratio      NaN |IR Nodes         4097 |Frequency        1.00 |Truffle Callees      2 |Forced          false |Depth               2
[engine] Inlined                parseInt <opt>                                        |call diff       -1.00 |Recursion Depth      0 |Explore/inline ratio      NaN |IR Nodes          111 |Frequency        1.00 |Truffle Callees      0 |Forced           true |Depth               3
[engine] Inlined                parseInt <opt>                                        |call diff       -1.00 |Recursion Depth      0 |Explore/inline ratio      NaN |IR Nodes          111 |Frequency        1.00 |Truffle Callees      0 |Forced           true |Depth               3
[engine] Inlined              parseInt <opt>                                          |call diff       -1.00 |Recursion Depth      0 |Explore/inline ratio      NaN |IR Nodes          111 |Frequency        1.00 |Truffle Callees      0 |Forced           true |Depth               2
[engine] Expanded             M.EdgeSeparation                                        |call diff        1.00 |Recursion Depth      0 |Explore/inline ratio      NaN |IR Nodes         4097 |Frequency        1.00 |Truffle Callees      2 |Forced          false |Depth               2
[engine] Inlined              parseInt <opt>                                          |call diff       -1.00 |Recursion Depth      0 |Explore/inline ratio      NaN |IR Nodes          111 |Frequency        1.00 |Truffle Callees      0 |Forced           true |Depth               2
[engine] Inlined              M.EdgeSeparation                                        |call diff       -3.00 |Recursion Depth      0 |Explore/inline ratio      NaN |IR Nodes         4097 |Frequency        1.00 |Truffle Callees      2 |Forced          false |Depth               2
[engine] Inlined                parseInt <opt>                                        |call diff       -1.00 |Recursion Depth      0 |Explore/inline ratio      NaN |IR Nodes          111 |Frequency        1.00 |Truffle Callees      0 |Forced           true |Depth               3
[engine] Inlined                parseInt <opt>                                        |call diff       -1.00 |Recursion Depth      0 |Explore/inline ratio      NaN |IR Nodes          111 |Frequency        1.00 |Truffle Callees      0 |Forced           true |Depth               3
[engine] Cutoff               M.EdgeSeparation                                        |call diff        0.01 |Recursion Depth      0 |Explore/inline ratio      NaN |IR Nodes            0 |Frequency        0.01 |Truffle Callees      2 |Forced          false |Depth               2
[engine] Cutoff             M.FindMaxSeparation <opt>                                 |call diff        1.00 |Recursion Depth      0 |Explore/inline ratio      NaN |IR Nodes            0 |Frequency        1.00 |Truffle Callees      7 |Forced          false |Depth               1
[engine] Cutoff             M.FindIncidentEdge <opt>                                  |call diff        1.00 |Recursion Depth      0 |Explore/inline ratio      NaN |IR Nodes            0 |Frequency        1.00 |Truffle Callees     19 |Forced          false |Depth               1
[engine] Cutoff             parseInt <opt>                                            |call diff        1.00 |Recursion Depth      0 |Explore/inline ratio      NaN |IR Nodes            0 |Frequency        1.00 |Truffle Callees      0 |Forced           true |Depth               1
[engine] Cutoff             parseInt <opt>                                            |call diff        0.98 |Recursion Depth      0 |Explore/inline ratio      NaN |IR Nodes            0 |Frequency        0.98 |Truffle Callees      0 |Forced           true |Depth               1
[engine] Cutoff             A.Set <split-16abdeb5>                                    |call diff        1.00 |Recursion Depth      0 |Explore/inline ratio      NaN |IR Nodes            0 |Frequency        1.00 |Truffle Callees      0 |Forced          false |Depth               1
[engine] Cutoff             A.Normalize <split-866f516>                               |call diff        1.00 |Recursion Depth      0 |Explore/inline ratio      NaN |IR Nodes            0 |Frequency        1.00 |Truffle Callees      1 |Forced          false |Depth               1
[engine] Cutoff             A.Set <split-1f7fe4ae>                                    |call diff        1.00 |Recursion Depth      0 |Explore/inline ratio      NaN |IR Nodes            0 |Frequency        1.00 |Truffle Callees      0 |Forced          false |Depth               1
[engine] Cutoff             M.ClipSegmentToLine                                       |call diff        1.00 |Recursion Depth      0 |Explore/inline ratio      NaN |IR Nodes            0 |Frequency        1.00 |Truffle Callees      2 |Forced          false |Depth               1
[engine] Cutoff             M.ClipSegmentToLine                                       |call diff        1.00 |Recursion Depth      0 |Explore/inline ratio      NaN |IR Nodes            0 |Frequency        1.00 |Truffle Callees      2 |Forced          false |Depth               1
[engine] Cutoff             A.SetV <split-7c14e725>                                   |call diff        1.00 |Recursion Depth      0 |Explore/inline ratio      NaN |IR Nodes            0 |Frequency        1.00 |Truffle Callees      0 |Forced          false |Depth               1
[engine] Cutoff             A.SetV <split-6029dec7>                                   |call diff        1.00 |Recursion Depth      0 |Explore/inline ratio      NaN |IR Nodes            0 |Frequency        1.00 |Truffle Callees      0 |Forced          false |Depth               1
[engine] Inlined            L.Set <split-2ef5921d>                                    |call diff       -3.97 |Recursion Depth      0 |Explore/inline ratio      NaN |IR Nodes          205 |Frequency        1.98 |Truffle Callees      1 |Forced          false |Depth               1
[engine] Inlined              set <split-969378b>                                     |call diff       -1.98 |Recursion Depth      0 |Explore/inline ratio      NaN |IR Nodes          716 |Frequency        1.98 |Truffle Callees      0 |Forced          false |Depth               2
[engine] Inlined            set                                                       |call diff       -1.98 |Recursion Depth      0 |Explore/inline ratio      NaN |IR Nodes          381 |Frequency        1.98 |Truffle Callees      0 |Forced          false |Depth               1
[engine] inline done      M.CollidePolygons                                           |call diff        0.00 |Recursion Depth      0 |Explore/inline ratio     1.07 |IR Nodes        27149 |Frequency        1.00 |Truffle Callees     14 |Forced          false |Depth               0
```

### Dumping inlining decisions

The same information that is provided in textual form through tracing is also
available in the [IGV](Optimizing.md) dumps. The graphs are part of the `Graal
Graphs` group in a `Call Tree` subgroup. The graphs show the state of the call
tree before inlining and after.

### Controlling inlining budget

NOTE: The default values for inlining related budgets were carefully chosen
with consideration for compilation time, performance and compiler stability in
mind. Changing these parameters can impact all of these.

Language-agnostic inlining provides two options to control the amount of
exploration and the amount of inlining the compiler can do. There are
`InliningExpansionBudget` and `InliningInliningBudget` respectively. Both are
expressed in terms of Graal Node count. They can be controlled as any other
engine options (i.e. the same way as described in the "Tracing inlining
decisions" section).

`InliningExpansionBudget` controls at which point the inliner will stop
partially evaluating candidates. Increasing this budget can thus have a very
negative impact on average compilation time (notably on the time spent doing
partial evaluation), but may provide more candidates for inlining.

`InliningInliningBudget` controls how many Graal nodes the compilation unit is
allowed to have as a result of inlining. Increasing this budget will likely
result in more candidates being inlined, which will result in a larger
compilation unit. This, in turn might slow down compilation, notably in the
post partial evaluation phases since larger graphs take more time to optimize.
It may also improve performance (removed calls, optimization phases have a
bigger picture) or hurt performance (e.g. graph too big to optimize correctly
or to compile at all).
