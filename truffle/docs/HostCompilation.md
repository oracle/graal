---
layout: docs
toc_group: truffle
link_title: Host Optimization for Interpreter Code
permalink: /graalvm-as-a-platform/language-implementation-framework/HostOptimization/
---
# Host Compilation for Interpreter Java code

For the following document, we disambiguate between host and guest compilation.

* Host compilation is applied to the Java implementation of the interpreter. If the interpreter runs on HotSpot, this kind of compilation is applied when Truffle interpreters are JIT compiled (or dynamically compiled) as Java applications. This compilation is applied ahead of time during native image generation.
* Guest compilation is applied to guest language code. This kind of compilation uses Partial Evaluation and Futamura projections to derive optimized code from Truffle ASTs and bytecodes.

This section discusses domain-specific host compilations applied to Truffle AST and bytecode interpreters.

## Host Inlining

Truffle interpreters are written to support runtime compilation by applying the first Futamura projection.
Runtime compilable code, also often referred to as partial evaluatable code, has the following characteristics:

* It is naturally designed for high-performance, as it also defines the performance after runtime compilation of a language.
* It is written to avoid recursion, as recursive code cannot quickly be partially evaluated.
* It avoids complex abstractions and third-party code as they are typically not designed for PE.
* The boundaries of partially evaluatable code are reliably defined by methods annotated with `@TruffleBoundary`, blocks dominated by a call to `CompilerDirectives.transferToInterpreter()` or a  block protected by a call to `CompilerDirectives.inInterpreter()`.

Truffle host inlining leverages these properties and forces inlining during host compilation for runtime compilable code paths as much as possible.
The general assumption is that code important for runtime compilation is also important for interpreter execution.
Whenever a PE boundary is detected, the host inlining phase no longer makes any inlining decisions and defers them to later inlining phases better suited for regular Java code.

The source code for this phase can be found in [TruffleHostInliningPhase](https://github.com/oracle/graal/blob/master/compiler/src/org.graalvm.compiler.truffle.compiler/src/org/graalvm/compiler/truffle/compiler/phases/TruffleHostInliningPhase.java).

Truffle host inlining is applied when compiling a method annotated with `@HostCompilerDirectives.BytecodeInterpreterSwitch`.
The maximum node cost for such methods can be configured using `-H:TruffleHostInliningByteCodeInterpreterBudget=100000` for native images and `-Dgraal.TruffleHostInliningByteCodeInterpreterBudget=100000` on HotSpot. 
If a method that is annotated with  `@BytecodeInterpreterSwitch` calls a method with the same annotation then the method is directly inlined as long as the cost of both methods do not exceed the budget.
In other words, any such method will be treated by the inlining phase just as if they would be part of the root bytecode switch method.
This allows bytecode interpreter switches to be composed of multiple methods if needed.

Native image, during closed world analysis, computes all methods that are reachable for runtime compilation.
Any potentially reachable method from `RootNode.execute(...)` is determined as runtime compilable.
For native images, in addition to bytecode interpreter switches, all runtime compilable methods are optimized using Truffle host inlining.
The maximum node cost of such an inlining pass can be configured with `-H:TruffleHostInliningBaseBudget=5000`. 
On HotSpot the set of runtime compilable methods is unknown.
Therefore, we can only rely on regular Java method inlining for methods not annotated as bytecode interpreter switch on HotSpot.

Whenever the maximum budget for a compilation unit is reached, inlining will be stopped. 
The same budget will be used for the exploration of subtrees during inlining.
If a call cannot be fully explored and inlined within the budget, then no decision is taken on the individual subtree.
For the vast majority of runtime compilable methods, this limit will not be reached, as it is prevented by natural PE boundaries as well as polymorphic calls to execute methods of `@Child` nodes.
If there are methods that exceed the budget limit, then the recommendation is to optimize such nodes by adding more PE boundaries.
If a method exceeds the limit, it is likely that the same code also has a high cost for runtime compilation.

The inlining decisions performed by this phase is best debugged with `-H:Log=TruffleHostInliningPhase,~CanonicalizerPhase,~GraphBuilderPhase` for native images or  `-Dgraal.Log=TruffleHostInliningPhase,~CanonicalizerPhase,~GraphBuilderPhase` on HotSpot.

Consider the following example, which shows previously described common patterns of partial evaluatable code in Truffle interpreters:

```java
class BytecodeNode extends Node {

    @CompilationFinal(dimensions = 1) final byte[] ops;
    @Children final BaseNode[] polymorphic = new BaseNode[]{new SubNode1(), new SubNode2()};
    @Child SubNode1 monomorphic = new SubNode1();

    BytecodeNode(byte[] ops) {
        this.ops = ops;
    }

    @BytecodeInterpreterSwitch
    @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
    public void execute() {
        int bci = 0;
        while (bci < ops.length) {
            switch (ops[bci++]) {
                case 0:
                    // regular operation
                    add(21, 21);
                    break;
                case 1:
                    // complex operation in @TruffleBoundary annotated method
                    truffleBoundary();
                    break;
                case 2:
                    // complex operation protected behind inIntepreter
                    if (CompilerDirectives.inInterpreter()) {
                        protectedByInIntepreter();
                    }
                    break;
                case 3:
                    // complex operation dominated by transferToInterpreter
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    dominatedByTransferToInterpreter();
                    break;
                case 4:
                    // first level of recursion is inlined
                    recursive(5);
                    break;
                case 5:
                    // can be inlined is still monomorphic (with profile)
                    monomorphic.execute();
                    break;
                case 6:
                    for (int y = 0; y < polymorphic.length; y++) {
                        // can no longer be inlined (no longer monomorphic)
                        polymorphic[y].execute();
                    }
                    break;
                default:
                    // propagates transferToInterpeter from within the call
                    throw CompilerDirectives.shouldNotReachHere();
            }
        }
    }

    private static int add(int a, int b) {
        return a + b;
    }

    private void protectedByInIntepreter() {
    }

    private void dominatedByTransferToInterpreter() {
    }

    private void recursive(int i) {
        if (i == 0) {
            return;
        }
        recursive(i - 1);
    }

    @TruffleBoundary
    private void truffleBoundary() {
    }

    abstract static class BaseNode extends Node {
        abstract int execute();
    }

    static class SubNode1 extends BaseNode {
        @Override
        int execute() {
            return 42;
        }
    }

    static class SubNode2 extends BaseNode {
        @Override
        int execute() {
            return 42;
        }
    }
}
```


We can run this as a unittest in the Graal repository (see class `HostInliningBytecodeInterpreterExampleTest`) by running the following command line in `graal/compiler`:

```
mx unittest  -Dgraal.Log=TruffleHostInliningPhase,~CanonicalizerPhase,~GraphBuilderPhase -Dgraal.Dump=:3  HostInliningBytecodeInterpreterExampleTest
```

This prints:

```
[thread:1] scope: main
  [thread:1] scope: main.Testing
  Context: HotSpotMethod<HostInliningBytecodeInterpreterExampleTest$BytecodeNode.execute()>
  Context: StructuredGraph:1{HotSpotMethod<HostInliningBytecodeInterpreterExampleTest$BytecodeNode.execute()>}
      [thread:1] scope: main.Testing.EnterpriseHighTier.TruffleHostInliningPhase
      Truffle host inlining completed after 2 rounds. Graph cost changed from 136 to 137 after inlining:
      Root[org.graalvm.compiler.truffle.test.HostInliningBytecodeInterpreterExampleTest$BytecodeNode.execute]
          INLINE org.graalvm.compiler.truffle.test.HostInliningBytecodeInterpreterExampleTest$BytecodeNode.add(int, int)                      [inlined    2, monomorphic false, deopt false, inInterpreter false, propDeopt false, subTreeInvokes    0, subTreeCost    8, incomplete false,  reason null]
          CUTOFF org.graalvm.compiler.truffle.test.HostInliningBytecodeInterpreterExampleTest$BytecodeNode.truffleBoundary()                  [inlined   -1, monomorphic false, deopt false, inInterpreter false, propDeopt false, subTreeInvokes    1, subTreeCost    0, incomplete false,  reason truffle boundary]
          INLINE com.oracle.truffle.api.CompilerDirectives.inInterpreter()                                                                    [inlined    0, monomorphic false, deopt false, inInterpreter false, propDeopt false, subTreeInvokes    0, subTreeCost    6, incomplete false,  reason null]
          CUTOFF org.graalvm.compiler.truffle.test.HostInliningBytecodeInterpreterExampleTest$BytecodeNode.protectedByInIntepreter()          [inlined   -1, monomorphic false, deopt false, inInterpreter  true, propDeopt false, subTreeInvokes    1, subTreeCost    0, incomplete false,  reason protected by inInterpreter()]
          INLINE com.oracle.truffle.api.CompilerDirectives.transferToInterpreterAndInvalidate()                                               [inlined    3, monomorphic false, deopt  true, inInterpreter false, propDeopt false, subTreeInvokes    0, subTreeCost   32, incomplete false,  reason null]
            INLINE com.oracle.truffle.api.CompilerDirectives.inInterpreter()                                                                  [inlined    3, monomorphic false, deopt  true, inInterpreter false, propDeopt false, subTreeInvokes    0, subTreeCost    6, incomplete false,  reason null]
            CUTOFF org.graalvm.compiler.truffle.runtime.hotspot.AbstractHotSpotTruffleRuntime.traceTransferToInterpreter()                    [inlined   -1, monomorphic false, deopt  true, inInterpreter  true, propDeopt false, subTreeInvokes    0, subTreeCost    0, incomplete false,  reason dominated by transferToInterpreter()]
          CUTOFF org.graalvm.compiler.truffle.test.HostInliningBytecodeInterpreterExampleTest$BytecodeNode.dominatedByTransferToInterpreter() [inlined   -1, monomorphic false, deopt  true, inInterpreter false, propDeopt false, subTreeInvokes    0, subTreeCost    0, incomplete false,  reason dominated by transferToInterpreter()]
          INLINE org.graalvm.compiler.truffle.test.HostInliningBytecodeInterpreterExampleTest$BytecodeNode.recursive(int)                     [inlined    4, monomorphic false, deopt false, inInterpreter false, propDeopt false, subTreeInvokes    1, subTreeCost   20, incomplete false,  reason null]
            CUTOFF org.graalvm.compiler.truffle.test.HostInliningBytecodeInterpreterExampleTest$BytecodeNode.recursive(int)                   [inlined   -1, monomorphic false, deopt false, inInterpreter false, propDeopt false, subTreeInvokes    1, subTreeCost    0, incomplete false,  reason recursive]
          INLINE org.graalvm.compiler.truffle.test.HostInliningBytecodeInterpreterExampleTest$BytecodeNode$SubNode1.execute()                 [inlined    1, monomorphic false, deopt false, inInterpreter false, propDeopt false, subTreeInvokes    0, subTreeCost    6, incomplete false,  reason null]
          CUTOFF org.graalvm.compiler.truffle.test.HostInliningBytecodeInterpreterExampleTest$BytecodeNode$BaseNode.execute()                 [inlined   -1, monomorphic false, deopt false, inInterpreter false, propDeopt false, subTreeInvokes    1, subTreeCost    0, incomplete false,  reason not direct call: no type profile]
          CUTOFF com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere()                                                               [inlined   -1, monomorphic false, deopt false, inInterpreter false, propDeopt  true, subTreeInvokes    0, subTreeCost   98, incomplete false,  reason propagates transferToInterpreter]
```

Note that we have also used the `-Dgraal.Dump=:3 ` option, which sends the graphs to any running `IdealGraphVisualizer` instance for further inspection.
To debug CUTOFF decisions for incomplete exploration (entries with `incomplete  true`) use the `-Dgraal.TruffleHostInliningPrintExplored=true` option to see all incomplete subtrees in the log.


