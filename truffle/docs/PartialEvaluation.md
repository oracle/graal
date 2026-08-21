---
layout: docs
toc_group: truffle
link_title: Partial Evaluation
permalink: /graalvm-as-a-platform/language-implementation-framework/PartialEvaluation/
---

# Partial Evaluation in Truffle

## Document purpose

Partial evaluation (PE) is the technique used to compile Truffle interpreters to highly optimized machine code.
This document explains the low-level details of Truffle PE for language implementers.

This is not a guide on how to use Truffle DSL (specializations, inline caches, libraries, etc.).
Those higher-level features build on the low-level mechanisms described here.
For guidelines on using those features, refer to the Javadoc or the specific [DSL guidance](./DSLGuidelines.md) and [Truffle Libraries](./TruffleLibraries.md) docs.

## How partial evaluation works

Abstractly, a computer program is a mapping from input data to output data: `Input -> Output`.
In a given context, some of the inputs can be static, while others remain dynamic.
Partial evaluation optimizes a program with respect to its static inputs.
The result is a version of the original program specialized to those static inputs: `DynamicInput -> Output`.

For example, consider a small function that computes either a signed difference or an absolute difference:

```java
int difference(int a, int b, boolean absolute) {
    if (absolute) {
        return Math.abs(a - b);
    } else {
        return a - b;
    }
}
```

Suppose this function is used in a context where `absolute` is known statically to be `true` and `b` is known statically to be `1`.
Partial evaluation can produce a specialized version of `difference` that only depends on input `a`:

```java
int difference$specialized(int a) {
    return Math.abs(a - 1);
}
```

In the specialized program, the branch where `absolute == false` can be eliminated, and the stable variable `b` can be replaced by its constant value `1`.
The remaining operation, `Math.abs(a - 1)`, is residual code: it still depends on the dynamic input `a` and must execute at run time.
By leveraging the static inputs to a program, PE can produce a simpler program that does less work at run time.

### Partially evaluating an interpreter

A language interpreter is itself a computer program.
It broadly has two kinds of input data: the guest program to interpret, usually represented with an AST or bytecode, and the actual inputs to the guest program.
Guest programs are usually stable inputs, so we can partially evaluate the interpreter with respect to a given guest program to produce a specialized interpreter that runs only that program.
In effect, partially evaluating an interpreter over a guest program produces a compiled version of the guest program.
This technique is known as the [first Futamura projection](https://fi.ftmr.info/PE-Museum/PE-Revised1999.pdf).

Consider the simple AST interpreter below, which implements a small expression language:

```java
public class ExpressionProgram {
    final ExpressionNode expr;
    int execute(int[] args) {
        return expr.execute(args);
    }
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
    public int execute(int[] args) {
        return left.execute(args) + right.execute(args);
    }
}
... // more Expression implementations
```

The interpreter can combine `ExpressionNode`s in arbitrary ways to implement many different expressions.
This generality is necessary to support the language, but it introduces dynamic behaviour that is hard to optimize during normal execution, such as polymorphic `execute` calls.

However, to execute one particular AST, the generality is not necessary.
Suppose we have a concrete AST `new ExpressionProgram(new AddNode(new LoadArgumentNode(1), new LoadArgumentNode(2)))`.
By partially evaluating the interpreter with respect to this stable AST, we can remove the dynamism:

```java
// Partial evaluation starting with the body of ExpressionProgram#execute:
expr.execute(args);
// Since expr is statically an AddNode, PE can inline AddNode#execute:
expr.left.execute(args) + expr.right.execute(args);
// Since expr's children are statically LoadArgumentNodes, PE can inline both calls:
args[expr.left.index] + args[expr.right.index];
// Since the index field of each LoadArgumentNode is final, PE can fold the field values:
args[1] + args[2];
```

Partial evaluation can effectively remove the overhead of interpretation, leaving us with a compiled implementation of the given AST.

### Partial evaluation in Truffle

In Truffle, runtime compilation happens for a given `CallTarget`.
A call target is associated with a particular `RootNode`, which is the stable interpreter entrypoint for that target.
Truffle profiles the run time behaviour of a given call target (call counts, loop counts, etc.), and when it determines the target to be hot, it schedules the call target for runtime compilation.
During runtime compilation, Truffle partially evaluates the interpreter with respect to the call target and then generates optimized machine code for the specialized interpreter using the Graal compiler.

Partial evaluation specializes all code reachable from the call target.
The call target invokes `RootNode#execute`, the entry-point into user-defined code for PE.
During PE, the partial evaluator treats certain values as PE constants and can remove or simplify code such as:
- stable field loads from PE constants
- virtual calls with PE constant receivers
- conditional branches and loops over PE constants

Code that PE cannot simplify remains as residual code in the compiled graph.
For example, code that still depends on frame reads, dynamic receivers, or opaque Java calls usually remains residual.
A PE-friendly interpreter exposes enough stable structure that the residual code produced by PE can focus on the actual guest computation, not interpretation.

For background on the Truffle partial evaluation approach, see [Practical Partial Evaluation for High-Performance Dynamic Language Runtimes](https://chrisseaton.com/truffleruby/pldi17-truffle/pldi17-truffle.pdf).

## Writing interpreters for partial evaluation

This section describes some design considerations when writing interpreters for partial evaluation.
First, it makes the distinction between interpreter and runtime code, and then describes how to write interpreter code for PE.

### Interpreter versus runtime code

Partial evaluation optimizes **all reachable code**.
PE starts from a hot call target and follows the calls and branches it can reach from the root execution.
It does not know which code is important to optimize: left unchecked, it will keep trying to inline and simplify reachable calls until it hits a boundary, an opaque call, or a compilation limit.

Since PE should not inline and optimize everything, it is important to draw a line between interpreter and runtime code:
- Interpreter code is the code PE should see and specialize with respect to the current call target.
It is the hot path that gets aggressively optimized by PE.
- Runtime code is ordinary Java code excluded from PE using `@TruffleBoundary`.
Typically runtime code is too complex for PE, executes infrequently, or does not benefit from the aggressive inlining PE performs (e.g., it has recursive or polymorphic calls).
Java library code -- string manipulation, I/O, hash map operations, etc. -- almost always belongs in runtime code.
Runtime code can still be optimized as ordinary Java code by the host compiler, but calls to runtime code do not get inlined during PE.

Generally, the interpreter code should include the core interpretation abstractions, like `execute` calls and bytecode dispatch loops, so that these abstractions can be optimized away by PE.
It should expose stable interpreter state that allows PE to actually simplify it, for example by devirtualizing method calls or unrolling a bytecode loop.
Runtime code should be ancillary code that the interpreter code calls out to, similar to helper APIs provided by a language runtime.
A focused interpreter path allows PE to focus on the code that actually benefits from PE.

### Writing partially evaluable interpreter code

There are a few guidelines to follow when writing interpreter code:

1. _Expose stable interpreter state to PE_.
   The interpreter code uses abstractions -- field reads, virtual calls, conditional control flow, etc. -- in order to implement the guest language.
   The goal of PE is to remove the overhead of interpretation and produce residual code that focuses on executing the guest program. 
   Partial evaluation can fold field reads, inline calls, and remove unnecessary branches or loops, but only if it knows which values are stable.
   Use appropriate annotations and directives to indicate stable state to PE.

2. _Invalidate before changing stable state_.
   Truffle's PE leverages the speculative nature of Graal compilation: compiled code can make assumptions about the stability of values and behaviour, but those assumptions can later stop holding.
   For correctness, the interpreter must invalidate affected compiled code before making changes to stable state.

3. _Be mindful of code size_.
   It can be tempting to expose as much code as possible to PE, but this can be counter-productive.
   Inlining and loop explosion are useful only when they enable simplifications that justify the extra graph size.
   A PE-friendly interpreter should produce focused compiled code, not merely expose as much Java code as possible to PE.
   Large graph sizes can increase compilation time, cause compilation to exceed size limits, and lead to poorly-optimized code.

The following sections describe the concrete tools to make interpreter code partially evaluable.

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

If `left` and `right` are PE constants, PE can inline their concrete `executeInt` implementations.
This turns a polymorphic interpreter call into direct code specialized to the given children.

#### Profiling stable state with `@CompilationFinal`

Guidelines:
- Use `@CompilationFinal` for fields that are initialized or changed later, but should become stable before compilation.
- Invalidate compiled code before changing a `@CompilationFinal` value that compiled code may depend on.
- Use `@CompilationFinal(dimensions = ...)` for arrays whose elements should be stable.

`@CompilationFinal` is similar to `final` from PE's perspective, but the field is still mutable in Java.
This allows interpreters to profile their own behaviour.
Once a call target is scheduled for runtime compilation, PE will treat `@CompilationFinal` fields like `final` ones, effectively specializing the interpreter using profiled values.

For example, the code below lazily initializes some state only when it executes:

```java
@CompilationFinal private boolean initialized;
void execute(VirtualFrame frame) {
    if (!initialized) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        ... // initialize
        initialized = true;
    }
    ... // use initialized data
}
```

If this code is compiled before initialization occurs, execution along this path will deoptimize.
Initialization will then occur in the interpreter, and the code can later be recompiled with the initialized state.
The second compilation will see `initialized == true` and fold away the initialization block.

Before overwriting a `@CompilationFinal` field, you must invalidate any compiled code that relies on the stability of the old value.
Truffle offers two mechanisms for invalidation:
- _Internal invalidation_: When code modifying a stable value itself makes assumptions about the stable value, it should use `CompilerDirectives.transferToInterpreterAndInvalidate()` before changing it.
  If compiled code reaches this point in execution, it will trigger the current code to deoptimize and invalidate it.
- _External invalidation_: If some call target makes an assumption about a stable value, but some other call target can modify that value, the call targets should use an `Assumption`.
  The code that expects a stable value should create an assumption and check `isValid()` to register the assumption with the compiler; when some code wants to change the value, it can `invalidate()` the assumption, externally triggering invalidation of the compiled code.
An invalidated call target can later be re-optimized, but stable fields must eventually stabilize, otherwise the code can get into a deoptimization loop where it is repeatedly compiled and invalidated.

For arrays, `@CompilationFinal` on the field does not automatically make array elements stable.
Use `dimensions` to describe how many array dimensions should be treated as stable:

```java
// table0 is PE constant
@CompilationFinal private int[][] table0;

// table1 and table1[c1] are PE constant
@CompilationFinal(dimensions = 1) private int[][] table1;

// table2, table2[c1], and table2[c1][c2] are PE constant
@CompilationFinal(dimensions = 2) private int[][] table2;
```

As with object fields, before mutating PE-constant array elements, invalidate any compiled code that relies on the stability of those elements.

#### Loop unrolling with `@ExplodeLoop`

Guidelines:
- Use `@ExplodeLoop` to unroll loops with a PE-constant number of iterations.
- Use `CompilerAsserts.partialEvaluationConstant(...)` for loop bounds, indices, and other loop state that must be PE constant.
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

Without unrolling, `children[i]` does not resolve to a PE constant, and the `executeInt` call cannot be inlined or optimized by PE.

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

`@ExplodeLoop` is not a general-purpose performance annotation.
It duplicates graph nodes and can increase compilation time and compiled code size, so it should be used with discretion.
In general, loops over AST nodes and other static code elements should typically use `@ExplodeLoop`, especially when unrolling at PE time exposes PE constants that enable further optimization by PE.
On the other hand, if later compiler phases could unroll and optimize the loop just as well (i.e., unrolling doesn't help PE optimize better), prefer to let the compiler make unrolling decisions.

`@ExplodeLoop` takes an optional `LoopExplosionKind` that determines unrolling behaviour, like how successive iterations are connected (e.g., with merge points or with nested unrolling).
Refer to the Javadoc for full details.

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
When an unrolled iteration has the same state as some other unrolled iteration, rather than continue unrolling, PE inserts a control flow _merge_ (effectively a branch) to the other iteration.
```
lbl:
<LOAD_ARGUMENT 1>    // unrolled iteration: bci = 0, sp = 0, opcode = LOAD_ARGUMENT
...                  // more unrolled iterations
bci = 0              // unrolled iteration: bci = 10, sp = 0, opcode = BRANCH_BACKWARD
goto lbl             // next iteration: {bci = 0, sp = 0} matches a previous one. branch to it.
```
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

Instead of joining control flow after the control-flow in the `BRANCH_FALSE` handler, `MERGE_EXPLODE` _explodes_ the unrolling separately into each branch:

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

By merging iterations with the same state and exploding the unrolling into each control flow branch, PE effectively compiles the interpreter to the control flow of the actual interpreted program.

This explosion mode is powerful but can be challenging to use.
The partial evaluator uses the compilation `FrameState` to determine whether two loop iterations can be merged; while this roughly corresponds to local variables, it can be error-prone.
For example, unrelated variables like branch profiles can unintentionally be included in the state set and prevent two states from being merged as intended.
A common workaround is to wrap such variables (in a box or array) to prevent them from polluting the state set during loop explosion.
It is important to inspect compiler logs and compiled code to ensure loop explosion works as expected.

#### Runtime code

Guidelines:
- Code that PE cannot usefully optimize should be excluded from partial evaluation using `@TruffleBoundary`.
- Do not pass `VirtualFrame` to `@TruffleBoundary` methods; read required frame values first and pass those values explicitly.

Some methods perform string manipulation, I/O, stream operations, hash map lookups, and other library-heavy operations that are unsuitable for PE.
We call this code _runtime code_.
Annotate runtime code with `@TruffleBoundary` so PE leaves an opaque call in the graph instead of spending compilation budget on code that is not designed to partially evaluate.

It is a compile-time error for a `@TruffleBoundary` method to declare a `VirtualFrame` parameter.
Scalar replacement of the frame is critical for performance, so Truffle does not allow virtual frames to escape into boundary methods that escape analysis cannot see into.
If runtime code needs frame data, read the required values before calling the boundary method and pass those values instead.

Below is an example with code that does not belong on the fast path:

```java
int execute(VirtualFrame frame) {
    if (error(frame)) {
        throw new MyException("bad value: " + frame.getObject(slot));
    }
    return body.executeInt(frame);
}
```

String concatenation is not designed for PE and does not belong on the fast path.
To fix this code, move the String concatenation to runtime code.
Since the concatenation depends on a value in `frame`, read that value and pass it to the boundary method:

```java
int execute(VirtualFrame frame) {
    if (error(frame)) {
        Object value = frame.getObject(slot);
        throw new MyException(makeErrorMessage(value));
    }
    return body.executeInt(frame);
}

@TruffleBoundary
private static String makeErrorMessage(Object value) {
    return "bad value: " + value;
}
```

#### Slow path code

Guidelines:
- Use deoptimizing slow paths to stop compiled execution, invalidate the compiled code, and resume in the interpreter.
- Use `CompilerDirectives.transferToInterpreterAndInvalidate()` when the slow-path check and fallback are local.
- Use `SlowPathException` when nested fast-path helpers need to abort to a caller that handles the fallback.
- Ensure the slow path eventually changes state or handles an uncommon case; otherwise it can cause a [deoptimization cycle](./DeoptCyclePatterns.md).

Slow path code is fallback code that typically handles interpreter re-specialization (e.g., updating a profile).
It should not run in compiled code and requires deoptimization to the interpreter before executing.
Unlike runtime code, slow path code is not meant to be called from compiled code.

When the slow path can be handled locally, use `CompilerDirectives.transferToInterpreterAndInvalidate()`.
This tells compiled code to transfer back to the interpreter and invalidate the current compilation before continuing.
Use this when the code at the slow-path check can handle the fallback itself.

Sometimes, the deoptimizing code does not understand how to handle slow-path behaviour.
Use `SlowPathException` to deoptimize and throw an exception to a catching call site that knows how to handle the slow path.
PE treats `SlowPathException` as a [_skipped_ exception](./DeoptCyclePatterns.md#skipped-exceptions): when PE reaches a path that would allocate or throw a `SlowPathException`, it emits an invalidating deoptimization instead of compiled throw/catch control flow.

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

### Omitting the dimensions for a stable array

`@CompilationFinal` indicates that a field value is PE-constant, but it says nothing about the stability of the value's subfields.
For object fields, the stability of subfields is determined by the annotations used on those fields.
For array fields, use `@CompilationFinal(dimensions = ...)` to indicate whether the elements of the array are stable.

For example:

```java
// table0 is PE-constant
@CompilationFinal private int[][] table0;

// table1 and table1[c1] are PE-constant
@CompilationFinal(dimensions = 1) private int[][] table1;

// table2, table2[c1], and table2[c1][c2] are PE-constant
@CompilationFinal(dimensions = 2) private int[][] table2;
```

As with object fields, before mutating PE-constant elements of an array, invalidate any compiled code that relies on the stability of the elements.

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
