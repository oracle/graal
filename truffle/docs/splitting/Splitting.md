---
layout: docs
toc_group: truffle
link_title: Splitting Algorithm
permalink: /graalvm-as-a-platform/language-implementation-framework/splitting/Splitting/
---
# Splitting Algorithm

This guide gives an overview of the algorithm used in the implementation of Truffle call target splitting.

The new implementation relies on the language implementations providing information on when a particular node turns polymorphic or increases its "degree" of polymorphism by, for example, adding an entry into an inline cache.
This event is called a "polymorphic specialize".
This information is provided to the runtime by calling the
[Node.reportPolymorphicSpecialize](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/Node.html#reportPolymorphicSpecialize) method after the specialization is complete.

This guide explains what happens after the call to `reportPolymorphicSpecialize`.
You can find more information on how to correctly report polymorphic specializations in the [Reporting Polymorphism](ReportingPolymorphism.md) guide.

## Approach

Detection of suitable splitting candidates relies on the languages reporting polymorphic specializations.
Once the specialization is reported, you can assume that the polymorphism is coming from somewhere in the caller chain of the call target hosting the newly polymorphic node, and that by splitting the right call target (or call targets) you can return this node to a monomorphic state.

The runtime automatically registers standard direct call nodes. Other call-site implementations do not participate in caller tracking or splitting. Caller tracking retains only whether there are zero, one, or multiple known callers; the sole caller is held weakly.

Reporting a polymorphic specialization only identifies and marks call targets for which splitting could result in monomorphization. No call target is cloned while the report is processed.

When a root is prepared for compilation and splitting is enabled, it scans its direct call-site nodes. `RootNode.visitCloneableNodes` visits the ordinary AST by default. Roots that store cloneable nodes outside ordinary child fields override this method and also visit those node trees. A call site whose target is marked is split, provided there are no outstanding factors preventing it such as the [root node not being allowed to be split](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/RootNode.html#isCloningAllowed), the AST being too big, or insufficient splitting budget. Only direct call sites in the root being prepared are considered. Newly cloned targets are not scanned. After execution, a cloned target's own root compilation preparation can split its outgoing direct calls; inline preparation does not split.

If at least one call site is split, compilation preparation is rejected once. This allows the new targets, whose nodes have been returned to an uninitialized state, to execute and gather call-site-specific profiles before the next compilation attempt.

The following pseudo code summarizes the two phases:

```java
reportPolymorphicSpecialize(callTarget)
    if callTarget.needsSplit
        return false
    if numberOfKnownCallers(callTarget) == 0
        return false
    if callCount(callTarget) == 1
        return false

    if numberOfKnownCallers(callTarget) > 1
        callTarget.needsSplit = true
    else
        callTarget.needsSplit = reportPolymorphicSpecialize(caller(callTarget))

    return callTarget.needsSplit

prepareForCompilation(compilationRoot)
    if splitting is disabled
        return true

    return !splitMarkedCalls(compilationRoot)

splitMarkedCalls(root)
    split = false
    for callSite in directCallSites(root)
        if callSite.currentTarget.needsSplit
            clonedTarget = split(callSite)
            if clonedTarget != null
                split = true
                splitMarkedCalls(clonedTarget)
    return split
```

## Runtime Implementation Flow

The following flow maps the algorithm to its runtime implementation:

```text
Node specialization becomes polymorphic
    ↓
Node.reportPolymorphicSpecialize()
    ↓
OptimizedRuntimeSupport.reportPolymorphicSpecialize(source)
    ↓
Find the source node's enclosing RootNode
    ↓
Find that root's OptimizedCallTarget
    ↓
OptimizedCallTarget.polymorphicSpecialize(source)
    ↓
maybeSetNeedsSplit(depth = 0)
    ↓
Check:
  - propagation depth not exceeded
  - target not already marked
  - target has known direct callers
  - target has executed more than once
    ↓
Inspect compact incoming-call tracking
    ↓
┌──────────────────────────────────────────────────────────┐
│ Exactly one known OptimizedDirectCallNode caller          │
│     ↓                                                     │
│ Find that call node's enclosing caller target             │
│     ↓                                                     │
│ Recursively call callerTarget.maybeSetNeedsSplit(depth+1) │
│     ↓                                                     │
│ If caller target needs splitting, mark this target too    │
└──────────────────────────────────────────────────────────┘
                         or
┌───────────────────────────────────────────────────────────┐
│ No unique caller, normally because multiple callers exist │
│     ↓                                                     │
│ Mark this target: needsSplit = true                       │
└───────────────────────────────────────────────────────────┘
    ↓
Propagate the result back down the single-caller chain
    ↓
Return to normal interpreter execution
    ↓
No clone is created yet
    ↓
A caller reaches root compilation preparation
    ↓
OptimizedCallTarget.prepareForCompilation(rootCompilation=true, ...)
    ↓
Enter the compilation root's language without entering a language context
    ↓
TruffleSplittingStrategy.splitForCompilation(compilationRoot)
    ↓
RootNode.visitCloneableNodes(visitor)
    ↓
Collect OptimizedDirectCallNodes, including calls cached by Bytecode DSL operations
    ↓
For each direct call node:
    read callNode.currentCallTarget
    ↓
Is currentCallTarget.needsSplit?
    ├── No  → continue scanning
    └── Yes
          ↓
        Check cloning, recursion, size, source-target, and budget constraints
          ↓
        Reserve splitting budget
          ↓
        Clone the marked target
          ↓
        Outgoing direct calls register as they are copied or reconstructed
          ↓
        Atomically verify the outer call node still points to the source
          ↓
        Transfer incoming caller registration:
          sourceTarget.removeDirectCallNode(outerCallNode)
          clonedTarget.addDirectCallNode(outerCallNode)
          ↓
        Publish:
          outerCallNode.currentCallTarget = clonedTarget
    ↓
Did at least one split succeed?
    ├── No  → continue ordinary compilation preparation
    └── Yes → return false and abort this preparation
                  ↓
              Clones can execute and gather profiles
                  ↓
              A later preparation compiles the cloned structure
```

At the very beginning of the pseudo code you can have early termination conditions.
If the call target is already marked as "needs split", there is no need to continue.
Also, if the call target has no known callers (e.g., it is the "main" of the execution) splitting is not applicable since splitting is inherently tied to duplicating ASTs for a particular call site.
Finally, if this is happening during the first execution of a call target, splitting is pointless since the polymorphic nature of the node is inevitable (i.e., not coming from the callers, but rather an integral property of that call target).

In the second part of the pseudo code two cases are differentiated:

1) The call target has multiple known callers - in this case you can assume that the polymorphism is coming from one of these multiple callers. Thus, you mark the call target as "needs split".

2) The call target has only one known caller - in this case you know that marking this call target as "needs split" cannot help remove the polymorphism. But, the polymorphism could be coming into this call target from its sole caller, which could have multiple callers and could be a candidate for splitting. Thus, you recursively apply the algorithm to the caller of our call target.

Ignore for now the return value of our algorithm and its usage, and consider the following SimpleLanguage example to illustrate why this distinction between one and multiple callers is needed:

```
function add(arg1, arg2) {
    return arg1 + arg2;
}

function double(arg1) {
    return add(arg1, arg1);
}

function callsDouble() {
    double(1);
    double("foo");
}

function main() {
    i = 0;
    while (i < 1000) {
        callsDouble();
    }
}
```

In this example, the node representing `+` in the `add` function will turn polymorphic once `double` is called with the string argument `"foo"` and this will be reported to the runtime and our algorithm will be applied to `add`.
All of the early return checks will fail (`add` is not marked "needs split", it has known callers and this is not its first execution).
Observe that `add` has only one caller (`double`), so you apply the algorithm to `double`.
Early returns all fail, and since `double` has multiple callers, you mark it as "needs split". When `callsDouble` is prepared for compilation, its calls to `double` are split, resulting in the following code representation of the run time state:

```
function add(arg1, arg2) {
    return arg1 + arg2; // + is polymorphic
}

function double(arg1) {
    return add(arg1, arg1);
}

function doubleSplit1(arg1) {
    return add(arg1, arg1);
}

function doubleSplit2(arg1) {
    return add(arg1, arg1);
}

function callsDouble() {
    doubleSplit1(1);
    doubleSplit2("foo");
}

function main() {
    i = 0;
    while (i < 1000) {
        callsDouble();
    }
}
```

As you can see, the source of the polymorphism was split, but that did not solve the issue, since both splits still call the same `add` function and the polymorphism remains.
This is where the algorithms return value comes in to play.
If the algorithm was successful in finding a target to mark then all the transitive callee's of that target need to be marked "needs split" as well.
With this final step in place, the final run time result of our splitting approach for the previous example can be represented as the following source code:

```
function add(arg1, arg2) {
    return arg1 + arg2; // + is polymorphic
}

function addSplit1(arg1, arg2) {
    return arg1 + arg2;

}
function addSplit2(arg1, arg2) {
    return arg1 + arg2;
}

function double(arg1) {
    return add(arg1, arg1);
}

function doubleSplit1(arg1) {
    return addSplit1(arg1, arg1);
}

function doubleSplit2(arg1) {
    return addSplit2(arg1, arg1);
}

function callsDouble() {
    doubleSplit1(1);
    doubleSplit2("foo");
}

function main() {
    i = 0;
    while (i < 1000) {
        callsDouble();
    }
}
```

Final note to observe at this point is that splitting does not remove the original call targets, and they still have polymorphism in their profiles.
A new call to one of these targets is split when its enclosing root is prepared for compilation.
Consider if the `main` of the previous example looked as follows.

```
function main() {
    i = 0;
    while (i < 1000) {
        callsDouble();
    }
    add(1,2); // this line was added
}
```

If the root containing the newly added line is prepared for compilation, you do not want it to compile a call to the `add` function with the polymorphic `+` since the arguments here do not merit the polymorphism.
Since `add` remains marked as "needs split", preparation splits the final call site and delays compilation once so the new target can execute first.
