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


## Debugging Host Inlining

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

## Tuning Host Inlining

After learning how to debug and trace host inlining decisions, it is time to look at some ways to tune it.
As a first step, it is necessary to identify compilation units essential for good interpreter performance.
To do this, a Truffle interpreter can be executed in interpreter-only mode by setting the `engine.Compilation` flag to `false`. 
After that, a Java profiler may be used to identify hot spots in the execution.
For further details on profiling, see [Profiling.md](https://github.com/oracle/graal/blob/master/truffle/docs/Profiling.md)
If you are looking for advice on how and when to optimize Truffle interpreters, see [Optimizing.md](https://github.com/oracle/graal/blob/master/truffle/docs/Optimizing.md)

After identifying a hot method, for example, the bytecode dispatch loop in a Truffle bytecode interpreter, we can further investigate using host inlining logging as described in the previous section.
Interesting entries are prefixed with `CUTOFF` and have a `reason` that explains the reason for the individual cutoff.

Common reasons for `CUTOFF` entries are:
* `dominated by transferToInterpreter()` or `protected by inInterpreter()`: This means that the is call performed in a slow-path. Host inlining will not decide on such calls and just mark them as CUTOFF. 
* `target method not inlinable` this happens for host VM methods that cannot be inlined. There is typically not much we can do about that.
* `Out of budget` we ran out of budget for inlining this method. This happens if the cost of the method becomes too high.

Additionally, to avoid the explosion of code size, host inlining has a built-in heuristic to detect call subtrees that are considered too complex to inline. 
For example, the tracing may print the following:

```
CUTOFF com.oracle.truffle.espresso.nodes.BytecodeNode.putPoolConstant(VirtualFrame, int, char, int)   [inlined   -1, explored    0, monomorphic false, deopt false, inInterpreter false, propDeopt false, graphSize 1132, subTreeCost 5136, invokes    1, subTreeInvokes   12, forced false, incomplete false,  reason call has too many fast-path invokes - too complex, please optimize, see truffle/docs/HostOptimization.md
```

This indicates that there are too many fast-path invokes (by default 10) in the subtree, it also stops exploring after that number.
The `-Dgraal.TruffleHostInliningPrintExplored=true` flag may be provided to see the entire subtree for the decision.
The following calls are considered fast-path invokes:

* Invokes where the target method is annotated by `@TruffleBoundary`.
* Invokes that are polymorphic or where no monomorphic profiling feedback is available. For example, a call to a subexpression's execute method.
* Invokes that are recursive.
* Invokes that are too complex themselves. For example, invokes that have too many fast-path invokes.

The following calls are _not_ considered fast-path invokes:

* Invokes that can be inlined using the host inlining heuristic.
* Invokes in a slow-path, like any invoke that is dominated by `transferToInterpreter()` or protected by `isInterpreter()`. 
* Invokes that cannot be inlined due to limitations of the host VM, like calls to `Throwable.fillInStackTrace()`.
* Invokes that are no longer reachable.

It is impossible to avoid fast-path invokes entirely, as, for example, child nodes need to be executed in an AST.
It is theoretically possible to avoid all fast-path invokes in a bytecode interpreter. 
In practice, languages will rely on `@TruffleBoundary` to the runtime to implement more complex bytecodes.

In the following sections, we discuss techniques on how to improve host interpreter code:

### Optimization: Manually cut code paths with @HostCompilerDirectives.InliningCutoff

As mentioned in the previous section, a heuristic automatically cuts inlining subtrees with too many calls in them.
One way to optimize this is by using the [@InliningCutoff](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/HostCompilerDirectives.InliningCutoff.html) annotation.

Consider the following example:

```
abstract class AddNode extends Node {

   abstract Object execute(Object a, Object b);

   @Specialization int doInt(int a, int b) { return a + b; }
   
   @Specialization double doDouble(double a, double b) { return a + b; }
   
   @Specialization double doGeneric(Object a, Object b, @Cached LookupAndCallNode callNode) { 
       return callNode.execute("__add", a, b); 
   }
}

```

In this example, the specializations `doInt` and `doDouble` are very simple, but there is also the `doGeneric` specialization, which calls into a complex lookup chain.
Assuming that the `LookupAndCallNode.execute` is a very complex method with more than ten fast-path subtree calls, we could not expect the execute method to get inlined.
Host inlining currently does not support automatic component analysis; though it can be specified manually using the `@InliningCutoff` annotation:

```
abstract class AddNode extends Node {

   abstract Object execute(Object a, Object b);

   @Specialization int doInt(int a, int b) { return a + b; }
   
   @Specialization double doDouble(double a, double b) { return a + b; }
   
   @HostCompilerDirectives.InliningCutoff
   @Specialization double doGeneric(Object a, Object b, @Cached LookupAndCallNode callNode) { 
       return callNode.execute("__add__", a, b); 
   }
}

```

After changing the code, host inlining may now decide to inline the execute method of the `AddNode` if it fits into the host inlining budget, but force a `CUTOFF` at the `doGeneric(...)` method call.
Please see the [javadoc](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/HostCompilerDirectives.InliningCutoff.html) on other use-cases for using this annotation.


### Optimization: Deduplicating calls from branches that fold during partial evaluation

The following is an example where the code is efficient for compilation using partial evaluation but is not ideal for host compilation.

```
@Child HelperNode helperNode;

final boolean negate;
// ....

int execute(int argument) {
	if (negate) {
		return helperNode.execute(-argument);
	} else {
         return helperNode.execute(argument);
	}
}
```

When this code is compiled using partial evaluation, this code is efficient as the condition is guaranteed to fold to a single case, as the `negate` field is compilation final. 
During host optimization, the `negate` field is not compilation final, and the compiler would either inline the code twice or decide not to inline the execute method.
In order to avoid this the code can be rewritten as follows:

```
@Child HelperNode helperNode;

final boolean negate;
// ....

int execute(int argument) {
    int negatedArgument;
    if (negate) {
        negatedArgument = -argument;
    } else {
        negatedArgument = argument;
    }
    return helperNode.execute(negatedArgument);
}
```

Similar code patterns can arise indirectly through code generation if many specializations with the same method body are used.
Host compilers typically have a hard time optimizing such patterns automatically.


### Optimization: Extract complex slow-path code in separate methods

Consider the following example:

```
int execute(int argument) {
	if (argument == 0) {
	   CompilerDirectives.transferToInterpeterAndInvalidate();
	   throw new RuntimeException("Invalid zero argument " + argument);
	}
	return argument;
}
```

The Java compiler generates bytecode equivalent to the following code:

```
int execute(int argument) {
	if (argument == 0) {
	   CompilerDirectives.transferToInterpeterAndInvalidate();
	   throw new RuntimeException(new StringBuilder("Invalid zero argument ").append(argument).build());
	}
	return argument;
}
```

While this code is efficient for partial evaluation, this code takes up unnecessary space during host inlining.
It is therefore recommended to extract a single method for the slow-path part of the code:


```
int execute(int argument) {
	if (argument == 0) {
	   CompilerDirectives.transferToInterpeterAndInvalidate();
	   throw invalidZeroArgument(argument);
	}
	return argument;
}

RuntimeException invalidZeroArgument(int argument) {
   throw new RuntimeException("Invalid zero argument " + argument);
}

```

