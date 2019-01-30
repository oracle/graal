# How it works - Splitting

This document gives a short overview of the algorithm used in the
implementation of Truffle call target splitting. 

The new implementation relies on the language implementations providing
information on when a particular node turns polymorphic or increases its
"degree" of polymorphism by for example adding an entry into an inline cache. We
call this event a "polymorphic specialize".  This information is provided to the
runtime by calling the
[Node.reportPolymorphicSpecialize](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/Node.html#reportPolymorphicSpecialize)
method after the specialization is complete. In this document we will only look
at what happens after the call to `reportPolymorphicSpecialize`. You can find
more information on how to correctly report polymorphic specializations in the
[Reporting Polymorphism](ReportingPolymorphism.md) file.

## The Approach

Detecting of suitable splitting candidates relies on the languages reporting
polymorphic specializations. Once the specialization is reported we make the
assumption that the polymorphism is coming from somewhere in the caller chain of
the call target hosting the newly polymorphic node, and that by splitting the
right call target (or call targets) we can return this node to a monomorphic
state. 

We identify the call targets for which we conclude that splitting could result
in monomorphization and mark them as "needs split". During further execution, if
the interpreter is about to execute a direct call to a call target that is
marked as "needs split", that call target will be split (provided there are no
outstanding factors preventing it such as the [root node not being allowed to be
split](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/RootNode.html#isCloningAllowed),
the AST being too big, etc.). This results in a new call target with a clean
profile (i.e. all its nodes are returned to an uninitialized state) to be
re-profiled specifically for this call site, since it is the only call site
calling this new call target.

Following recursive algorithm (expressed as pseudo code) is a simplified version
of the approach used to decide which call targets need to be marked "needs
split". This algorithm is applied to every call target once one of its nodes
reports a polymorphic specialization. The full implementation can be found in
`org.graalvm.compiler.truffle.runtime.OptimizedCallTarget#maybeSetNeedsSplit`

```
setNeedsSplit(callTarget)
    if callTarget.needsSplit
        return false
    if sizeof(knownCallers(callTarget)) == 0
        return false
    if callCount(callTarget) == 1
        return false

    if sizeof(knownCallers(callTarget)) > 1
        callTarget.needsSplit = true
    else
        callTarget.needsSplit = setNeedsSplit(caller(callTarget))
    
    return callTarget.needsSplit
```

At the very beginning of the pseudo code we have several early termination
conditions. If the call target is already marked as "needs split" we have no
need to continue. Also, if the call targets has no known callers (e.g. it is the
"main" of the execution) splitting is not applicable since splitting is
inherently tied to duplicating ASTs for a particular call site. Finally, if this
is happening during the first execution of call target, splitting is pointless
since the polymorphic nature of the node is inevitable (i.e. not coming from the
callers, but rather an integral property of that call target).

In the second part of the pseudo code we differentiate two cases: 

1) The call target has multiple known callers - in this case we can assume that the
polymorphism is coming from one of these multiple callers. Thus, we mark the
call target as "needs split".

2) The call target has only one known caller - in this case we know that marking
this call target as "needs split" cannot help remove the polymorphism. But, the
polymorphism could be coming into this call target from its sole caller, which
could have multiple callers and could be a candidate for splitting. Thus, we
recursively apply the algorithm to the caller of our call target.

Ignore for now the return value of our algorithm and its usage, and consider the
following Simple Language example to illustrate why this distinction between one
and multiple callers is needed:

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

In this example, the node representing `+` in the `add` function will turn
polymorphic once `double` is called with the string argument `"foo"` and this
will be reported to the runtime and our algorithm will be applied to `add`. All
of the early return checks will fail (`add` is not marked "needs split", it has
known callers and this is not its first execution). We observe that `add` has
only one caller (`double`) so we apply the algorithm to `double`. Early returns
all fail, and since `double` has multiple callers we mark it as "needs split"
and on later iterations calls to `double` are split resulting in the following
code representation of the run time state: 

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

As we can see, we did split the source of the polymorphism, but we did not solve
the issue, since both slits still call the same `add` function and the
polymorphism remains. This is where the algorithms return value comes in to
play. If the algorithm was successful in finding a target to mark than all the
transitive callee's of that target need to be marked "needs split" as well. With
this final step in place, the final run time result of our splitting approach
for the previous example can be represent as the following source code:
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

Final note to observe at this point is that the splitting does not remove the
original call targets, and that they still have polymorphism in their profiles.
Thus, even if new calls to these call targets are created, they will also be
split. Consider if the `main` of the previous example looked as follows.

```
function main() {
    i = 0;
    while (i < 1000) {
        callsDouble();
    }
    add(1,2); // this line was added
}
```

Once the execution reaches the newly added line we do not want it to call the
`add` function with the polymorphic `+` since the arguments here don't merit the
polymorphism. Luckily, since add was already marked as "needs split" it will
remain so during the entire execution, and the this finall call to `add` with
cause another split of the `add` functions.
