
## Truffle Branches Instrumentation

In Truffle languages, it is common that the AST implementations contain fast and slow
execution paths, usually based on some condition, such as a profile. These execution
paths are organized into different conditional branches. In these cases, it is often
helpful to know if running the program actually exercised the code in both of those
executions paths.

The branch instrumentation functionality instruments `if`-statements in target methods
to track which of the branches have been taken during execution. Branch instrumentation
does this by instrumenting branches with code that writes to a global
table. Each branch has an entry in this table. When the program ends, the
contents of the table are decoded and dumped to the standard output in readable
form.

There are several flags that control how branch instrumentation works. These flags are
specified as system properties:

- `--engine.InstrumentBranches` - controls whether instrumentation is on (`true`
  or `false`, default is `false`)
- `--engine.InstrumentFilter` - filters methods in which instrumentation
  should be done (method filter syntax, essentially `<package>.<class>.<method>[.<signature>]`)
- `--engine.InstrumentationTableSize` - controls the maximum number of
  instrumented locations
- `--engine.InstrumentBranchesPerInlineSite` - controls whether instrumentation
  provides separate branch profiles for each guest language function/compilation unit
  (default is `false`).


### Example Usage

Here is an example of how to enable branch instrumentation on a program.

When using instrumentation to detect hot or infrequently used branches in a Truffle
language implementation, we usually start by finding a language node with a
problematic method. The following command runs a unit test for the Simple Language,
and instruments all the `if`-statements:

```
mx --jdk jvmci sl --engine.BackgroundCompilation=false \
  --engine.InstrumentBranches \
  '--engine.InstrumentFilter=*.*.*' \
  ../truffle/truffle/com.oracle.truffle.sl.test/src/tests/LoopObjectDyn.sl
```

We get the following output:

```
Execution profile (sorted by hotness)
=====================================
  0: *****************************************************
  1: **************************

com.oracle.truffle.sl.nodes.access.SLPropertyCacheNode.namesEqual(SLPropertyCacheNode.java:109) [bci: 120]
[0] state = IF(if=36054#, else=0#)

com.oracle.truffle.sl.nodes.controlflow.SLWhileRepeatingNode.executeRepeating(SLWhileRepeatingNode.java:102) [bci: 5]
[1] state = BOTH(if=18000#, else=18#)
```

This output tells us that both branches were visited in the `if`-statement in the file
`SLWhileRepeatingNode.java` at line 102, and only the `true` branch was visited for
the `if`-statement in the file `SLPropertyCacheNode.java` at line 109.
However, it does not tell us e.g. where this specific `SLPropertyCacheNode` node was
used from -- the same `execute` method can be called from many different Simple Language
nodes, and we may wish to distinguish these occurrences. We therefore set the
per-inline-site flag to `true`, and change the filter to focus only on
`SLPropertyCacheNode`:

```
mx --jdk jvmci sl -Dgraal.TruffleBackgroundCompilation=false \
  --engine.InstrumentBranchesPerInlineSite \
  --engine.InstrumentBranches \
  '--engine.InstrumentFilter=*.SLPropertyCacheNode.*' \
  ../truffle/truffle/com.oracle.truffle.sl.test/src/tests/LoopObjectDyn.sl
```

This time we get more output, because the method `namesEqual` was inlined at
multiple sites (each site is represented by its inlining chain). The following output
fragment first shows us the histogram with the `if`-statement ID and its occurrence
count. It then shows the exact call stacks and execution counts for the branches.
For example, for `[1]`, when `namesEqual` is called from `executeRead`, the `true`
branch is taken `18018` times. When the `namesEqual` is called from `executeWrite`
(`[0]`), the `true` branch is taken only `18` times:

```
Execution profile (sorted by hotness)
=====================================
  1: ***************************************
  2: ***************************************
  0:
  3:

com.oracle.truffle.sl.nodes.access.SLPropertyCacheNode.namesEqual(SLPropertyCacheNode.java:109) [bci: 120]
com.oracle.truffle.sl.nodes.access.SLReadPropertyCacheNodeGen.executeRead(SLReadPropertyCacheNodeGen.java:76) [bci: 88]
com.oracle.truffle.sl.nodes.access.SLReadPropertyNode.read(SLReadPropertyNode.java:71) [bci: 7]
com.oracle.truffle.sl.nodes.access.SLReadPropertyNodeGen.executeGeneric(SLReadPropertyNodeGen.java:30) [bci: 35]
com.oracle.truffle.sl.nodes.SLExpressionNode.executeLong(SLExpressionNode.java:81) [bci: 2]
com.oracle.truffle.sl.nodes.expression.SLLessThanNodeGen.executeBoolean_long_long0(SLLessThanNodeGen.java:42) [bci: 5]
com.oracle.truffle.sl.nodes.expression.SLLessThanNodeGen.executeBoolean(SLLessThanNodeGen.java:33) [bci: 14]
com.oracle.truffle.sl.nodes.controlflow.SLWhileRepeatingNode.evaluateCondition(SLWhileRepeatingNode.java:133) [bci: 5]
com.oracle.truffle.sl.nodes.controlflow.SLWhileRepeatingNode.executeRepeating(SLWhileRepeatingNode.java:102) [bci: 2]
org.graalvm.compiler.truffle.OptimizedOSRLoopNode.executeLoop(OptimizedOSRLoopNode.java:113) [bci: 61]
com.oracle.truffle.sl.nodes.controlflow.SLWhileNode.executeVoid(SLWhileNode.java:69) [bci: 5]
com.oracle.truffle.sl.nodes.controlflow.SLBlockNode.executeVoid(SLBlockNode.java:84) [bci: 37]
com.oracle.truffle.sl.nodes.controlflow.SLFunctionBodyNode.executeGeneric(SLFunctionBodyNode.java:81) [bci: 5]
com.oracle.truffle.sl.nodes.SLRootNode.execute(SLRootNode.java:78) [bci: 28]
[1] state = IF(if=18018#, else=0#)

...

com.oracle.truffle.sl.nodes.access.SLPropertyCacheNode.namesEqual(SLPropertyCacheNode.java:109) [bci: 120]
com.oracle.truffle.sl.nodes.access.SLWritePropertyCacheNodeGen.executeWrite(SLWritePropertyCacheNodeGen.java:111) [bci: 244]
com.oracle.truffle.sl.nodes.access.SLWritePropertyNode.write(SLWritePropertyNode.java:73) [bci: 9]
com.oracle.truffle.sl.nodes.access.SLWritePropertyNodeGen.executeGeneric(SLWritePropertyNodeGen.java:33) [bci: 47]
com.oracle.truffle.sl.nodes.access.SLWritePropertyNodeGen.executeVoid(SLWritePropertyNodeGen.java:41) [bci: 2]
com.oracle.truffle.sl.nodes.controlflow.SLBlockNode.executeVoid(SLBlockNode.java:84) [bci: 37]
com.oracle.truffle.sl.nodes.controlflow.SLFunctionBodyNode.executeGeneric(SLFunctionBodyNode.java:81) [bci: 5]
com.oracle.truffle.sl.nodes.SLRootNode.execute(SLRootNode.java:78) [bci: 28]
[0] state = IF(if=18#, else=0#)

...
```

## Truffle Call Boundary Instrumentation

The Truffle call boundary instrumentation tool instruments callsites to methods that
have a `TruffleCallBoundary` annotation, and counts the calls to those methods. It is
controlled by the following set of flags:

- `--engine.InstrumentBoundaries` - controls whether instrumentation is on (`true`
  or `false`, default is `false`)
- `--engine.InstrumentFilter` - filters methods in which instrumentation
  should be done (method filter syntax, essentially `<package>.<class>.<method>[.<signature>]`)
- `--engine.InstrumentationTableSize` - controls the maximum number of
  instrumented locations
- `--engine.InstrumentBoundariesPerInlineSite` - controls whether instrumentation
  is done per a declaration of an Truffle boundary call (`false`), or per every call
  stack where that callsite was inlined (`true`)

This tool can be used together with the branch instrumentation tool.

Assume that you need to find frequently occurring methods that were not, for example,
inlined. The usual steps in identifying the Truffle call boundaries is to first run the
program the `InstrumentBoundariesPerInlineSite` flag set to `false`, and
then, after identifying the problematic methods, set that flag to `true` and set the
`InstrumentFilter` to identify the particular call stacks for those methods.
