---
layout: docs
toc_group: truffle
link_title: Partial Evaluation
permalink: /graalvm-as-a-platform/language-implementation-framework/PartialEvaluation/
---

# Partial Evaluation in Truffle

## Document purpose

Truffle uses partial evaluation (PE) in combination with dynamic speculation to compile interpreters to highly optimized machine code.
This document explains the low-level primitives and concepts underlying Truffle PE for language implementers; it is intended as a technical foundation rather than a user guide.

Truffle provides many higher-level features (including DSL specializations, inline caches, and libraries) that make these primitives easier to use.
For practical guidance on those features, refer to their Javadoc, the [DSL guidance](./DSLGuidelines.md), and the [Truffle Libraries](./TruffleLibraries.md) documentation.

## Motivation

It is desirable for an interpreter to be fast, but supporting dynamic language semantics can often conflict with high performance.
The conventional approach to achieve high performance is to combine an interpreter with a language-specific compiler/JIT that translates and optimizes the language to machine code.
This approach gives the compiler direct control but often requires semantics, profiling, and optimization knowledge to be represented in both interpreter and compiler implementations.

With the Truffle framework, implementers write an interpreter that specializes itself for behaviour observed at run time and generalizes when those observations no longer hold.
The Graal compiler, which optimizes Java code, derives optimized machine code using the interpreter code and a particular guest program.
By treating stable interpreter state and guest-program structure as compilation constants, partial evaluation can remove most interpreter abstractions, avoiding the need for a language-specific compiler.

The Truffle approach simplifies the implementation effort, because optimized code is derived directly from the interpreter.
However, the trade-off is that interpreter code must be written carefully to be PE-friendly and must correctly manage specialization and invalidation.

## How partial evaluation works

Abstractly, a computer program is a mapping from input data to output data: `Input -> Output`.
In a given context, some of the inputs can be static, while others remain dynamic.
Partial evaluation optimizes a program with respect to its static inputs.
The result is a version of the original program specialized to those static inputs: `DynamicInput -> Output`.

For example, consider a small function that rounds a floating point number to an integer, rounding up or down depending on a parameter:

```java
int round(double value, boolean roundUp) {
    if (roundUp) {
        return (int) Math.ceil(value);
    } else {
        return (int) Math.floor(value);
    }
}
```

Suppose this function is used in a context where `roundUp` is known statically to be `true`.
Partial evaluation can produce a specialized version of `round` where `roundUp` is statically `true`:

```java
int round$specialized(double value) {
   if (true) {
      return (int) Math.ceil(value);
   } else {
      return (int) Math.floor(value);
   }
}
```

This static information often enables subsequent optimizations.
In this example, PE can eliminate the `if-else` block altogether:
```java
int round$specialized(double value) {
    return (int) Math.ceil(value);
}
```

This transformation is called _partial evaluation_ because it evaluates part of the program ahead of time using its static inputs.
The result is a simpler program that only depends on `value`.
The output, called the _residual_ program, contains the remainder of the program that cannot be evaluated until its dynamic inputs (in this case, `value`) are supplied at run time.
Since PE performs some evaluation ahead of time, the residual program is often simpler and performs less work at run time.

### Partially evaluating an interpreter

A language interpreter is itself a computer program.
It broadly has two kinds of input data:
1. The guest program to interpret. Programs are typically represented as:
   1. An abstract syntax tree (AST), which forms a structured tree of nodes. AST interpreters execute by evaluating each AST node in a tree-walk fashion.
   2. Bytecode, which forms a linear sequence of low-level instructions. Bytecode interpreters execute by repeatedly fetching and executing instructions from a bytecode array.
2. The inputs to the guest program.

Guest programs are usually stable inputs, so we can partially evaluate the interpreter with respect to a given guest program to produce a specialized interpreter that runs only that program.
In effect, partially evaluating an interpreter over a guest program removes much of the interpretation overhead, producing code specialized for that program, much like a compiler would.
This technique is known as the [first Futamura projection](https://fi.ftmr.info/PE-Museum/PE-Revised1999.pdf).

Consider the simple AST interpreter below, which implements a small expression language:

```java
public int interpret(ExpressionNode program, int[] args) {
    return program.execute(args);
}

public interface ExpressionNode {
    int execute(int[] args);
}
public class LoadArgumentNode implements ExpressionNode {
    final int index;
    int execute(int[] args) {
        return args[index];
    }
}
public class AddNode implements ExpressionNode {
    final ExpressionNode left;
    final ExpressionNode right;
    int execute(int[] args) {
        return left.execute(args) + right.execute(args);
    }
}
```

The `interpret` method can take a variety of different `ExpressionNode` programs as input.
Suppose we have a concrete AST `new AddNode(new LoadArgumentNode(1), new LoadArgumentNode(2))`.
We can partially evaluate `interpret` with respect to this stable AST.

Partial evaluation starts by replacing the `program` parameter with the stable value of the AST.
We call this stable value a _partial evaluation (PE) constant_.

```java
public int interpret$specialized(int[] args) {
    // program = new AddNode(new LoadArgumentNode(1), new LoadArgumentNode(2));
    return program.execute(args);
}
```

Then, since `program` is statically known to be `AddNode`, the `execute` call becomes monomorphic: it can only resolve to one method, `AddNode#execute`.
PE will generally inline any monomorphic method call it reaches.
After inlining `AddNode#execute`, the code looks like:

```java
public int interpret$specialized(int[] args) {
    // program = new AddNode(new LoadArgumentNode(1), new LoadArgumentNode(2));
    return program.left.execute(args) + program.right.execute(args);
}
```

Now, notice that `program` is a PE constant `AddNode`, and that `AddNode` declares both `left` and `right` to be `final`.
Partial evaluation can treat them transitively as PE constants, which allows it to determine that both are `LoadArgumentNode`s.
It can then determine that both `execute` calls are monomorphic, and inline `LoadArgumentNode#execute` twice:

```java
public int interpret$specialized(int[] args) {
    // program = new AddNode(new LoadArgumentNode(1), new LoadArgumentNode(2));
    return args[program.left.index] + args[program.right.index];
}
```

Again, since `index` is a final field of `LoadArgumentNode`, and `program.left` and `program.right` are both PE constants, `program.left.index` and `program.right.index` are transitively PE constants.
Their values can be inlined into the method body:

```java
public int interpret$specialized(int[] args) {
    // program = new AddNode(new LoadArgumentNode(1), new LoadArgumentNode(2));
    return args[1] + args[2];
}
```

Observe how similar the remaining code looks to the input program.
Partial evaluation removed the indirection introduced by the interpreter: there are no `execute` calls or field reads in the residual program.
This is why partial evaluation of an interpreter over a given program is often described as producing a "compiled" version of the program.

### Partial evaluation in Truffle

Truffle performs runtime compilation on a `CallTarget`, which corresponds to a particular `RootNode`.
Truffle profiles the run time behaviour of a given call target (call counts, loop counts, etc.), and when it determines the target to be hot, it schedules the call target for runtime compilation.
During runtime compilation, Truffle partially evaluates the interpreter with respect to the call target and then generates optimized machine code for the specialized interpreter using the Graal compiler.

The entrypoint for compilation is the `RootNode`'s `execute` method.
Partial evaluation specializes all code reachable from this method.
The set of initial PE constants contains the root node and any `static final` constants in the program.
Transitively, any stable fields of a PE constant object are also PE constants.
Among other things, partial evaluation uses these stable values to:
- replace stable field loads with PE constant values
- inline/devirtualize virtual calls over PE constant receivers
- simplify conditional branches and loops over PE constants

The program produced by PE, called the _residual_ program, performs the remaining computation that could not be simplified by PE.
Often, this code depends on program inputs or values observed at run time (like argument loads, or a virtual method dispatch).
A PE-friendly interpreter should expose enough stable structure that the residual program produced by PE can focus on the actual guest computation, not interpretation.

For background on the Truffle partial evaluation approach, see [Practical Partial Evaluation for High-Performance Dynamic Language Runtimes](https://chrisseaton.com/truffleruby/pldi17-truffle/pldi17-truffle.pdf).

## Writing interpreters for partial evaluation

This section describes some design considerations when writing interpreters for partial evaluation.

### Exposing stable interpreter state

The interpreter code uses abstractions -- field reads, virtual calls, conditional control flow, etc. -- in order to implement the guest language.
The goal of PE is to remove the overhead of interpretation and produce residual code that focuses on executing the guest program.
Partial evaluation can fold field reads, inline calls, and remove unnecessary branches or loops, but only if it knows which values are stable.
Use appropriate annotations and directives to indicate stable state to PE.

Truffle's PE leverages the speculative nature of Graal compilation: compiled code can make assumptions about the stability of values and behaviour, but those assumptions can later stop holding.
For correctness, the interpreter must also invalidate affected compiled code before making changes to stable state.

#### Static interpreter structure with `final`, `@Child`, and `@Children`

Guidelines:
- Use `final` for fields whose values are fixed at construction time.
- Use `@Child` and `@Children` for AST child nodes whose structure should be stable during PE.
- Remember that field values only fold when the receiver object is PE constant.

Partial evaluation starts from a small set of values that are constant for the current compilation.
This set includes the root node for the compiled call target and ordinary `static final` Java fields.
Transitively, when a PE constant has a `final` field, that field value can also be treated as a PE constant.

Consider the following code:

```java
final boolean flag;
int execute(VirtualFrame frame) {
    if (flag) {
        return doTrue(frame);
    } else {
        return doFalse(frame);
    }
}
```

If the receiver is PE constant, PE can inline the constant value of `flag` and then omit the unreachable branch from the compilation.
Without a stable field, PE would have to compile both branches because `flag` could have either value at run time.

The same idea applies to child nodes.
Truffle's `@Child` and `@Children` annotations declare stable node children to PE.
Declaring node children is important because nodes often delegate to their children using virtual `execute` methods.
When the child field is PE constant, PE can see the concrete receiver type and inline the target method.
For example:

```java
final class AddNode extends ExpressionNode {
    @Child ExpressionNode left;
    @Child ExpressionNode right;

    int executeInt(VirtualFrame frame) {
        return left.executeInt(frame) + right.executeInt(frame);
    }
}
```

If a particular `AddNode` is PE constant, its `left` and `right` children are also PE constants, allowing PE to inline their concrete `executeInt` implementations.
This turns a polymorphic interpreter call into direct code specialized to the given children.

#### Profiling stable state with `@CompilationFinal`

Guidelines:
- Use `@CompilationFinal` for fields that are initialized or changed later, but should become stable before compilation.
- Invalidate compiled code before changing a `@CompilationFinal` value that compiled code may depend on.
- `@CompilationFinal` treats all array dimensions as stable by default. Use `@CompilationFinal(dimensions = ...)` to override this.

`@CompilationFinal` is similar to `final` from PE's perspective, but the field is still mutable in Java.
This allows interpreters to profile their own behaviour.
Once a call target is scheduled for runtime compilation, PE will treat `@CompilationFinal` fields like `final` ones, effectively specializing the interpreter using profiled values.

For example, the code below profiles the types of arguments it encounters using `@CompilationFinal` fields:

```java
@CompilationFinal private boolean intsSeen;
@CompilationFinal private boolean stringsSeen;
Object execute(VirtualFrame frame) {
    Object arg1 = ...;
    Object arg2 = ...;
    if (intsSeen && arg1 instanceof Integer i1 && arg2 instanceof Integer i2) {
        return i1 + i2;
    } else if (stringsSeen && arg1 instanceof String s1 && arg2 instanceof String s2) {
        return s1 + s2;
    }

    // No active case matched. Invalidate and re-specialize.
    CompilerDirectives.transferToInterpreterAndInvalidate();
    if (arg1 instanceof Integer i1 && arg2 instanceof Integer i2) {
        intsSeen = true;
        return i1 + i2;
    } else if (arg1 instanceof String s1 && arg2 instanceof String s2) {
        stringsSeen = true;
        return s1 + s2;
    }
    throw CompilerDirectives.shouldNotReachHere();
}
```

At the start of interpretation, `intsSeen == stringsSeen == false`, so the code supports no argument types.
On first execution, if two `int` arguments are passed, `execute` will fall through to the specialization path and set `intsSeen == true`.
Subsequent calls will handle `int` arguments without re-specialization.
If this code is compiled, PE will see `intsSeen == true` and `stringsSeen == false`, and it will emit code that roughly performs:
```java
Object execute$compiled(VirtualFrame frame) {
    Object arg1 = ...;
    Object arg2 = ...;
    if (arg1 instanceof Integer i1 && arg2 instanceof Integer i2) {
        return i1 + i2;
    }
   CompilerDirectives.transferToInterpreterAndInvalidate();
}
```
This code is simpler and specialized for the types profiled at run time.
Importantly, if `String` arguments were passed in the future, the code would fall through to `CompilerDirectives.transferToInterpreterAndInvalidate()`, which invalidates the compiled code and restores execution in the interpreter (see [Re-specializing the interpreter in the slow path](#re-specializing-the-interpreter-in-the-slow-path) below).
After resuming in the interpreter, the node can re-specialize itself to handle `String` arguments, and can eventually be recompiled.

Before overwriting a `@CompilationFinal` field, you must invalidate any compiled code that relies on the stability of the old value.
Truffle offers two mechanisms for invalidation:
- _Internal invalidation_: When code modifying a stable value itself makes assumptions about the stable value, it should use `CompilerDirectives.transferToInterpreterAndInvalidate()` before changing it.
  If compiled code reaches this point in execution, it will trigger the current code to deoptimize and invalidate it.
- _External invalidation_: If some call target makes an assumption about a stable value, but some other call target can modify that value, the call targets should use an `Assumption`.
  The code that expects a stable value should create an assumption and check `isValid()` to register the assumption with the compiler; when some code wants to change the value, it can `invalidate()` the assumption, externally triggering invalidation of the compiled code.
An invalidated call target can later be re-optimized, but stable fields must eventually stabilize, otherwise the code can get into a deoptimization loop where it is repeatedly compiled and invalidated.

For arrays, use `@CompilationFinal(dimensions=...)` to indicate how many dimensions of an array are stable.
When `dimensions` is omitted, all dimensions are implicitly stable:

```java
// table0, table0[c1], table0[c1][c2] are PE constant
@CompilationFinal(dimensions = 2) private int[][] table0;

// table1 and table1[c1] are PE constant; table1[c1][c2] is not.
@CompilationFinal(dimensions = 1) private int[][] table1;

// table2 is PE constant; table2[c1] and table2[c1][c2] are not.
@CompilationFinal(dimensions = 0) private int[][] table2;

// equivalent to table0 (dimensions = 2)
@CompilationFinal private int[][] table3;
```

As with object fields, before mutating PE-constant array elements, invalidate any compiled code that relies on the stability of those elements.

#### Invalidation and re-specialization using slow path code

Guidelines:
- Use deoptimizing slow paths to stop compiled execution, invalidate the compiled code, and resume in the interpreter.
- Use `CompilerDirectives.transferToInterpreterAndInvalidate()` when the slow-path check and fallback are local.
- Use `SlowPathException` when nested fast-path helpers need to abort to a caller that handles the fallback.
- Ensure the slow path eventually changes state or handles an uncommon case; otherwise it can cause a [deoptimization cycle](./DeoptCyclePatterns.md).

Slow path code is fallback code that typically handles interpreter re-specialization (e.g., updating a profile).
It should not run in compiled code and requires deoptimization to the interpreter before executing.
In the previous section, `execute` contains a slow path responsible for re-specializing for `int` and `String` arguments.

When the slow path can be handled locally (which is most cases), use `CompilerDirectives.transferToInterpreterAndInvalidate()`.
This directive tells compiled code to transfer back to the interpreter and invalidate the current compilation before continuing.
Use this when the code at the slow-path check can re-specialize and handle the unexpected case itself.

Sometimes, the deoptimizing code does not understand how to handle slow-path behaviour.
Use `SlowPathException` to deoptimize and throw an exception to a catching call site that knows how to handle the slow path.
PE treats `SlowPathException` as a [_skipped_ exception](./DeoptCyclePatterns.md#skipped-exceptions): when PE reaches a path that would allocate or throw a `SlowPathException`, it emits an invalidating deoptimization instead of compiled throw/catch control flow.

### Shaping loops for partial evaluation

Guidelines:
- Use `@ExplodeLoop` to unroll loops with a PE-constant number of iterations.
- Use `CompilerAsserts.partialEvaluationConstant(...)` to assert that loop variables influencing unrolling are PE constant.
- Use loop explosion if unrolling exposes stable state that PE can further optimize.

Partial evaluation has limited effectiveness with loops: it can optimize loop-invariant values, but not values that vary across iterations.
For loops with a PE-constant number of iterations, you can use `@ExplodeLoop` to unroll the loop body during partial evaluation.
After explosion, each copy of the loop body has its own loop state, which can expose additional PE constants and enable further optimization.

For example, consider a node that loops over its children:

```java
@Children private ExpressionNode[] children;

@ExplodeLoop
int execute(VirtualFrame frame) {
    int result = 0;
    for (int i = 0; i < children.length; i++) {
        CompilerAsserts.partialEvaluationConstant(i);
        result += children[i].executeInt(frame);
    }
    return result;
}
```

If there are 3 children, PE can unroll this loop and then inline the method calls because each indexed child load resolves to a PE-constant receiver:

```java
// after unrolling:
int result = 0;
result += children[0].executeInt(frame);
result += children[1].executeInt(frame);
result += children[2].executeInt(frame);
return result;

// after inlining:
int result = 0;
result += Child0Node_executeInt(frame);
result += Child1Node_executeInt(frame);
result += Child2Node_executeInt(frame);
return result;
```

Without unrolling, `children[i]` does not resolve to a PE constant, and the `executeInt` call cannot be monomorphized by PE.

Loop explosion can also fold loop-based computations during PE.
For example, assume `locals` is a PE-constant array of metadata objects:

```java
@CompilationFinal(dimensions = 1) private final LocalInfo[] locals;

@ExplodeLoop
int getLiveLocalCount(int bci) {
    CompilerAsserts.partialEvaluationConstant(bci);
    int count = 0;
    for (int i = 0; i < locals.length; i++) {
        CompilerAsserts.partialEvaluationConstant(i);
        if (locals[i].isLive(bci)) {
            count++;
        }
    }
    CompilerAsserts.partialEvaluationConstant(count);
    return count;
}
```

When `bci` is a PE constant, PE can iterate over the entire PE-constant `locals` array, evaluate `isLive` for each entry, and compute `count` during partial evaluation.
This replaces a loop-based metadata computation with a constant in the compiled graph.

There are some caveats to consider when using `@ExplodeLoop`:

- `@ExplodeLoop` takes an optional `LoopExplosionKind` that controls the exact unrolling behaviour, like how successive iterations are connected (e.g., with merge points or with nested unrolling).
The special [`MERGE_EXPLODE`](#merge_explode-for-bytecode-dispatch-loops) mode is explained below.
Refer to the Javadoc for full details.
- `@ExplodeLoop` only applies to the annotated method.
The control flow of inlined callees is not recursively unrolled.
Use `@EarlyInline` for helper methods that should participate in the caller's unrolling; such callees will be inlined before PE performs loop unrolling.
- `@ExplodeLoop` is not a general-purpose performance annotation.
It duplicates graph nodes and can increase compilation time and compiled code size, so it should be used with discretion.
In general, loops over AST nodes and other static code elements should typically use `@ExplodeLoop`, especially when unrolling at PE time exposes PE constants that enable further optimization by PE.
On the other hand, if later compiler phases could unroll and optimize the loop just as well (i.e., unrolling doesn't help PE optimize better), prefer to let the compiler make unrolling decisions.

#### `MERGE_EXPLODE` for bytecode dispatch loops

There is a special `LoopExplosionKind`, `MERGE_EXPLODE`, which is intended for use with bytecode interpreters.
Bytecode interpreters often contain bytecode dispatch loops with the following shape:

```java
@CompilationFinal(dimensions = 1) private final byte[] bytecodes;

@ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.MERGE_EXPLODE)
Object executeFromBci(VirtualFrame frame) {
    int bci = 0;
    int sp = 0;
    while (true) {
        CompilerAsserts.partialEvaluationConstant(bci);
        switch (bytecodes[bci]) {
            case LOAD_ARGUMENT:
                ... // <LOAD_ARGUMENT n>
                bci += 2;
                sp++;
                continue;
            case ADD:
                ... // <ADD>
                bci += 1;
                sp--;
                continue;
            case RETURN:
                return frame.getObject(sp - 1);
        }
    }
}
```
Such a dispatch loop consists of a loop with a `switch` table describing the execution semantics of each opcode in the language.
It also has some set of state controlling the dispatch from iteration to iteration, like `bci` and `sp` variables.

Unrolling bytecode dispatch loops is critical: by unrolling, the `bci` used in each iteration is PE constant, and so `bytecodes[bci]` is a PE constant, and we can eliminate the interpreter dispatch from compiled code.
For example, PE of the above loop with bytecodes `[LOAD_ARGUMENT, 1, LOAD_ARGUMENT, 2, ADD, RETURN]` would generate code of the form:
```
<LOAD_ARGUMENT 1>                // unrolled iteration: bci = 0, sp = 0, opcode = LOAD_ARGUMENT
<LOAD_ARGUMENT 2>                // unrolled iteration: bci = 2, sp = 1, opcode = LOAD_ARGUMENT
<ADD>                            // unrolled iteration: bci = 4, sp = 2, opcode = ADD
return frame.getObject(sp - 1)   // unrolled iteration: bci = 5, sp = 1, opcode = RETURN
```

Unrolling bytecode loops is not as simple as other loops: the instruction set often has control flow instructions that make the unrolling unbounded or even intractable.
For example, consider a hypothetical bytecode program with a `BRANCH_BACKWARD` instruction:
```
lbl:
   LOAD_ARGUMENT 1,
   ... // more loop body
   BRANCH_BACKWARD lbl
...
```

A naive loop explosion would unroll the body of this loop indefinitely, because the Java loop does not end:
```
<LOAD_ARGUMENT 1>    // unrolled iteration: bci = 0, sp = 0, opcode = LOAD_ARGUMENT
...                  // more unrolled iterations
bci = 0              // unrolled iteration: bci = 10, sp = 0, opcode = BRANCH_BACKWARD
<LOAD_ARGUMENT 1>    // unrolled iteration: bci = 0, sp = 0, opcode = LOAD_ARGUMENT
...                  // more unrolled iterations
bci = 0              // unrolled iteration: bci = 10, sp = 0, opcode = BRANCH_BACKWARD
... // continue forever
```

`MERGE_EXPLODE` resolves this problem by examining the _state_ of each iteration: for simplicity, think of this as the state of local variables at the start of the iteration.
When an unrolled iteration has the same state as some other unrolled iteration, PE stops unrolling and connects the current path to the existing code with the matching state.
```
lbl:
<LOAD_ARGUMENT 1>    // unrolled iteration: bci = 0, sp = 0, opcode = LOAD_ARGUMENT
...                  // more unrolled iterations
bci = 0              // unrolled iteration: bci = 10, sp = 0, opcode = BRANCH_BACKWARD
goto lbl             // next iteration: {bci = 0, sp = 0} matches a previous one. branch to it.
```
This merging of identical states is the "merge" part of `MERGE_EXPLODE`.
Merging iterations with the same state is critical to prevent PE from unrolling dispatch loops indefinitely.

Conditional control flow is also problematic for loop unrolling.
Consider a `BRANCH_FALSE` instruction, whose handler might look something like:
```java
case BRANCH_FALSE:
    boolean condition = (boolean) frame.getObject(sp - 1);
    if (condition) {
        bci += 2; // continue with the next instruction
    } else {
        bci = bytecodes[bci + 1]; // branch to encoded branch target
    }
    sp--;
    continue;
```
After unrolling a `BRANCH_FALSE`, there are two possible states for the subsequent iteration: `bci` could point at the next instruction, or the instruction targeted by the branch.
The next instruction to evaluate depends on which branch was taken.

Take this program as an example:
```
   LOAD_ARGUMENT 1
   BRANCH_FALSE lbl
   LOAD_ARGUMENT 2
   RETURN
lbl:
   LOAD_ARGUMENT 3
   RETURN
```

Instead of joining control flow of the two paths after `BRANCH_FALSE`, `MERGE_EXPLODE` continues unrolling separately into each branch:

```
<LOAD_ARGUMENT 1>    // unrolled iteration: bci = 0, sp = 0, opcode = LOAD_ARGUMENT
if (condition) {     // unrolled iteration: bci = 2, sp = 1, opcode = BRANCH_FALSE
  <LOAD_ARGUMENT 2>  // unrolled iteration: bci = 4, sp = 0, opcode = LOAD_ARGUMENT
  <RETURN>           // unrolled iteration: bci = 6, sp = 1, opcode = RETURN
} else {
  <LOAD_ARGUMENT 3>  // unrolled iteration: bci = 7, sp = 0, opcode = LOAD_ARGUMENT
  <RETURN>           // unrolled iteration: bci = 9, sp = 1, opcode = RETURN
}
```
Truffle refers to this kind of unrolling as "exploding", which is why the mode is called `MERGE_EXPLODE`.
By merging iterations with the same state and exploding the unrolling into each control flow branch, PE effectively compiles the interpreter to the control flow of the actual interpreted program.

`MERGE_EXPLODE` is powerful but requires some extra care to get right:
1. The partial evaluator uses the host interpreter state to determine whether two loop iterations can be merged; while this roughly corresponds to local variables, it can be error-prone.
   For example, unrelated variables like branch profiles can unintentionally be included in the state set and prevent two states from being merged as intended.
   A common workaround, since object comparison is based on identity, is to wrap these values in an object (e.g., an array) to prevent them from introducing unique state sets during loop explosion.
   It is important to inspect compiler logs and compiled code to ensure loop explosion works as expected.
2. Because `@ExplodeLoop` does not explode the control flow of inlined callees, outlining bytecode handlers can cause problems if they update dispatch state.
   For example, if the `BRANCH_FALSE` handler above was outlined to a helper function, PE would not continue loop explosion through its two branches.
   It would instead try to explode with the combined state of both branches, which would not have a PE-constant `bci`.
   For such cases, use the `@EarlyInline` annotation so that `MERGE_EXPLODE` explodes _through_ the control flow of the helper method.

### Separating interpreter and runtime code

Guidelines:
- Keep the core guest-language execution path in interpreter code that PE can see and simplify.
- Put complex code that does not benefit from PE behind `@TruffleBoundary` as runtime code.

Partial evaluation tries to optimize all code reachable from `RootNode#execute`.
As PE inlines calls and simplifies branches, it can transitively pull more calls into the compilation.
It can be tempting to think that exposing as much code as possible to PE would improve performance, but this can actually be counter-productive.
Partial evaluation does not use host code profiles (e.g., JVM branch profiles), so code not designed for PE -- that is, code without [explicit profiles](#profiling-stable-state-with-compilationfinal) -- usually just explodes the size of the compiler graph.
Often, this leads to poorly-optimized code, increases compilation time, and causes compilation failures (due to size limits).

Thus, when writing Truffle interpreters, it is important to draw a line between _interpreter code_ and _runtime code_.
Interpreter code is the code PE should see and specialize with respect to the current call target.
It is the hot path that gets aggressively optimized by PE.
Generally, the interpreter code should include the core interpretation abstractions, like `execute` calls and bytecode dispatch loops, so that these abstractions can be optimized away by PE.
Well-written interpreter code is focused and exposes stable profiling state that allows it to be simplified by PE (e.g., by devirtualizing method calls or unrolling a bytecode loop).

Runtime code is code that is unsuitable for PE, usually because it is too complex, lacks profiles, or does not partially evaluate well.
Use the `@TruffleBoundary` annotation to prevent runtime code from being inlined during partial evaluation.
Some kinds of code should usually be excluded from PE:
1. Recursive code, which typically increases code size without meaningfully simplifying code, should usually be excluded.
2. Complex code with many branches or many virtual calls can cause PE to pull in a significant amount of code that optimizes poorly, so it should usually be excluded, unless PE can substantially simplify it (using profiles, PE-constant arguments, etc.).
3. Third-party code that was not designed for PE should usually be excluded. This includes JDK library code, like string, I/O, and hash map operations.

Sometimes `@TruffleBoundary` placement is more of an engineering decision that depends on whether a code path really benefits from PE or just inhibits it by introducing more complexity.

### Working with `VirtualFrame`s

Guidelines:
- Pass a `VirtualFrame` only through interpreter calls that PE can see through.
- Do not pass a `VirtualFrame` through polymorphic or `@TruffleBoundary` calls unless it is deliberately materialized.

Interpreters need a mechanism to store variables and other guest program state.
In Truffle, this mechanism is the `VirtualFrame`, which is an object containing _slots_ that can be written to and read from.
Frame accesses incur memory loads/stores, which can significantly reduce performance of compiled code.

An important optimization performed in Truffle compilation is scalar replacement of the `VirtualFrame`.
Scalar replacement elides the allocation of the `VirtualFrame` object and decomposes it into its component values, which allows the compiler to represent frame values directly, often in registers or stack locations.
This allows compiled code to access frame slots without the memory indirection of the `VirtualFrame`.
If the compiled code deoptimizes, the VM reconstructs the frame object before resuming execution in the interpreter.

The compiler can only perform scalar replacement if partial escape analysis (PEA) determines that the `VirtualFrame` does not escape the current compilation.
Concretely, this means the frame should only be passed into interpreter methods that PE can see through, and not through polymorphic or `@TruffleBoundary` method calls.
Since scalar replacement is so critical for performance, the compiler will abort compilation if the `VirtualFrame` escapes (unless materialization is explicitly requested using `frame.materialize()`).

## Common mistakes

Below are a few common mistakes users make when writing code for PE.

### Assuming stable fields fold on non-PE-constant receivers

It is common to assume that all loads of a `@CompilationFinal` field fold to PE constants.
However, partial evaluation can only fold loads of a PE-constant field if its receiver is also a PE constant.
If the receiver is a dynamic value, then the value of its PE-constant field could be any arbitrary value.

Consider the following code example:

```java
final class MyObject {
    @CompilationFinal ObjectLayout layout;
}

Object readAttribute(MyObject receiver) {
    return receiver.layout.read(receiver);
}
```

If `receiver` is not a PE constant, the `readAttribute` method can observe any arbitrary `MyObject`.
Therefore, PE cannot fold `receiver.layout` to a constant value.
If the layout is stable in practice, the interpreter should expose the stable layout through PE-constant interpreter state (e.g., a `@CompilationFinal` profile) so that it can be exploited by PE.

This usage pattern is a common source of [deoptimization cycles](./DeoptCyclePatterns.md#compilation-final-field-of-a-non-constant-object).

### Using `@ExplodeLoop` with unbounded loops

`@ExplodeLoop` should only be used when PE can prove that loop explosion terminates.
The usual case is a loop with a PE-constant number of iterations.

The following code is suspicious:

```java
@ExplodeLoop
void processInts(int[] values) {
    for (int i = 0; i < values.length; i++) {
        // some work
    }
}
```

If `values.length` is not a PE constant, PE has no way to determine if the loop explosion will terminate.
It may repeatedly duplicate the loop body until compilation fails with a graph-size bailout.

If `values.length` should be a PE constant, use `CompilerAsserts.partialEvaluationConstant(...)` to defensively check this:

```java
@ExplodeLoop
void processInts(int[] values) {
    CompilerAsserts.partialEvaluationConstant(values.length);
    for (int i = 0; i < values.length; i++) {
        // some work
    }
}
```

### Excessive use of `@ExplodeLoop`

You should also be deliberate about using `@ExplodeLoop`: just because you can does not mean you should.
Consider the following example:

```java
@CompilationFinal(dimensions = 1) private final String[] metricNames;

@ExplodeLoop
void reportValue(Object value) {
    for (int i = 0; i < metricNames.length; i++) {
        reportRuntime(metricNames[i], value);
    }
}

@TruffleBoundary
static void reportRuntime(String metricName, Object value) {
    // Runtime code such as formatting, allocation, or I/O.
}
```

Depending on the length of `metricNames`, exploding the loop above can drastically increase code size without any apparent benefit to PE.
Even though the number of iterations is a PE constant, each unrolled iteration is still just another boundary call that PE cannot see through.
Loop explosion is most useful when the explosion reveals stable constants that allow PE to perform further optimization.
Other loops, especially uncommon loops or loops that are not performance-sensitive, are less likely to benefit from explosion.
When in doubt, profile actual applications to inform loop explosion decisions.

## When PE does not do what you expect

When partially evaluable code is not optimized as expected, there are a few tell-tale symptoms:

- performance warnings from `--engine.TracePerformanceWarnings=(call|instanceof|store|all)`, especially warnings about virtual calls that PE could not inline
- repeated deoptimization or invalidation reported by `--engine.TraceCompilation`, which often indicates unstable state that was treated as stable
- graph-size bailouts reported by compilation logs, for example with `--engine.TraceCompilation`, which often indicate that PE reached too much code or exploded too much control flow
- unexpected Java methods or Truffle nodes in `--engine.MethodExpansionStatistics=truffleTier` or `--engine.NodeExpansionStatistics=truffleTier`
- remaining `Invoke` nodes, unexpected branches, or unexpected loops in the Graal graph after partial evaluation (see [Optimizing Truffle interpreters](./Optimizing.md) for graph dumping instructions)

These symptoms usually mean that some part of the fast path is not as stable or PE-friendly as expected.
For example, a receiver might not be a PE constant, a loop bound might not be PE constant, a profile might not have stabilized, or slow path code may be unintentionally reachable to PE.

For more details on debugging, see [Optimizing Truffle interpreters](./Optimizing.md).
For common causes of repeated deoptimization, see [Deoptimization Cycle Patterns](./DeoptCyclePatterns.md).

## Further reading

- [Optimizing Truffle interpreters](./Optimizing.md) describes the main runtime compilation debugging workflow.
- [Deoptimization Cycle Patterns](./DeoptCyclePatterns.md) describes common patterns that cause repeated deoptimization.
- [Truffle DSL Guidelines](./DSLGuidelines.md) describes DSL-specific recommendations for writing optimizable nodes.
- [Host Compilation for Interpreter Java code](./HostCompilation.md) describes how Truffle interpreter code is optimized as Java code.
- [Bytecode DSL runtime compilation](./bytecode_dsl/RuntimeCompilation.md) describes PE-specific considerations for Bytecode DSL interpreters.
- [Practical Partial Evaluation for High-Performance Dynamic Language Runtimes](https://chrisseaton.com/truffleruby/pldi17-truffle/pldi17-truffle.pdf) describes the Truffle partial evaluation approach in detail.
- [Partial Evaluation of Computation Process--An Approach to a Compiler-Compiler](https://fi.ftmr.info/PE-Museum/PE-Revised1999.pdf) introduces the Futamura projections.
- [Partial Evaluation and Automatic Program Generation](https://pages.cs.wisc.edu/~horwitz/CS704-NOTES/PAPERS/JonesPartialEvaluation.pdf) thoroughly discusses the theory and implementation of PE.
