# The Truffle approach to function inlining

Truffle provides automated inlining for all languages built upon the framework.
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
early approach suffered from multiple issues, chief one being that it relied on
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
poor decisions made by the inliner i.e. the resulting compilation unit would be
too big to compile. 

## Language-agnostic inlining

The main design goal of the new inlining approach is to use the number of graal
nodes after partial evaluation as a proxy for call target size. This is a much
better size proxy since partial evaluation is removes all the abstractions of
the AST and results in a graph that is much closer to the low-level
instructions that the call target actually performs. This results in a more
precise cost model when deciding weather or not to inline a call target and
removes much of the language specific information that the AST carries (hence
the name: Language-agnostic inlining). 

This is achieved by performing partial evaluation on every candidate call
target and then making the inlining decision after that (as opposed to the
legacy inlining which made decisions before doing any partial evaluation). Both
the amount of partial evaluation that will be done as well the amount that will
be inlined are controlled by a notion of a budget. These are the "exploration
budget" and "inlining budget" respectively, both expressed in terms of graal
node counts.

The downside of this approach is that we need to do partial evaluation even on
call targets which we ultimately decide not to inline.  This results in a
measurable increase in average compilation time compared to legacy inlining
(aprox. 10%). 

## Observing and impacting the inlining.

The inliner keeps an internal call tree to keep track of the states of
individual targets, as well as the inlining decisions that were made. The
following sections explain the states in which targets in the call tree can be,
as well as how to find out which decisions were made during compilations.

### Call tree states

Nodes in the inline call tree represent *calls* to particular targets. This
means that if one target calls another twice, we will see this as two nodes
despite it being the same call target.

Each node can be in one of 6 states explained here:
* Inlined - This state means that the call was inlined. Initially, only the
  root of the compilation is in this state since it is implicitly "inlined"
(i.e. part of the compilation unit).
* Cutoff - This state means that the call target was not partially evaluated,
  thus was not even considered for inlining. This is normally due to the
inliner hitting it's exploration budget limitations 
* Expanded - This state means that the call target was partially evaluated
  (thus, considered for inlining) but a decision was made not to inline. This
could be due to inlining budget limitations or the target being deemed too
expensive to inline (e.g. inlining a target with multiple outgoing "Cutoff"
calls would introduce more calls to the compilation unit).
* Removed - This state means that this call is present in the AST but partial
  evaluation removed the call. This is an advantage over the legacy inlining
which made the decisions ahead of time and had no way of noticing such
situations.
* Indirect - This state denotes an indirect call. We cannot inline indirect
  call.
* BailedOut - This state should be very rare and is considered a performance
  problem. It means that partial evaluation of the target resulted in a
BailoutException. This means there is some problem with that particular target,
but rather than quit the entire compilation we treat that call as not possible
to inline.

### Tracing Inlining decisions

### Dumping Inlining decisions

### Controlling Inlining budget
