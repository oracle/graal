---
layout: docs
toc_group: truffle
link_title: Partial Evaluation
permalink: /graalvm-as-a-platform/language-implementation-framework/PartialEvaluation/
---

# Writing Truffle Interpreters for Partial Evaluation

## Document purpose

Partial evaluation (PE) is the technique used to compile Truffle interpreters to highly optimized machine code.

This document is aimed at language implementers and describes how to structure interpreter code so that PE can specialize it effectively.

This document is an entry point.
It summarizes the main coding patterns, common mistakes, and a few small examples.
More specialized documentation is available in the docs on [optimization](./Optimizing.md), [host compilation](./HostCompilation.md), [DSL guidance](./DSLGuidelines.md), and [Bytecode DSL runtime compilation](./bytecode_dsl/RuntimeCompilation.md).

## How partial evaluation works

Abstractly, a computer program is a mapping from input data to output data: `Input -> Output`.
In a given context, some of the inputs can be static (stable), while others remain dynamic.
Partial evaluation (PE) is a technique to optimize a given program with respect to some static input data.
The result is a version of the original program *specialized* to its static inputs: `DynamicInput -> Output`.

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
By leveraging the static inputs to a program, PE can produce a simpler, optimized program.

### Partially evaluating an interpreter (the first Futamura projection)

A language interpreter is itself a computer program.
It broadly has two kinds of input data: the program to interpret (the "guest" program), usually represented with an AST or bytecode, and the actual inputs to the guest program.
Since the guest program is (usually) a stable input, we can partially evaluate the interpreter with respect to it.
In doing so, we produce a specialized interpreter that runs only that program; in other words, we produce a *compiled* version of the guest program.
This technique is known as the [first Futamura projection](https://fi.ftmr.info/PE-Museum/PE-Revised1999.pdf).

Consider the simple AST interpreter below, which implements a simple expression language:

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

The interpreter can combine `ExpressionNode`s in arbitrary ways to implement all kinds of different expressions.
This generality is necessary to support the entire language of expressions, but it introduces dynamic behaviour, such as polymorphic `execute` calls, that is hard to optimize during normal execution.

However, to execute one particular AST, the generality is not necessary.
Suppose we have a concrete AST `new ExpressionProgram(new AddNode(new LoadArgumentNode(1), new LoadArgumentNode(2)))`.
By partially evaluating the interpreter with respect to this stable AST, we can remove the dynamism:
```java
// Partial evaluation starts with the body of ExpressionProgram#execute:
expr.execute(args);
// Since expr is statically an AddNode, PE can inline AddNode#execute:
expr.left.execute(args) + expr.right.execute(args);
// Then, since expr's children are statically LoadArgumentNodes, PE can inline both calls:
args[expr.left.index] + args[expr.right.index];
// Finally, since the index field of each LoadArgumentNode is final, PE can inline the constant field values:
args[1] + args[2];
```
Partial evaluation can effectively remove the overhead of interpretation, leaving us with a compiled implementation of the given AST.

### Partial evaluation in Truffle

In Truffle, partial evaluation is performed during *runtime compilation* of a particular `RootNode`.
When a root node is frequently executed (or executes many loop iterations), Truffle identifies it as "hot" and schedules it for runtime compilation.
Runtime compilation uses PE to specialize the interpreter over the stable root node and then translates the specialized interpreter to optimized machine code using the Graal compiler.

Conceptually, partial evaluation optimizes the interpreter starting from `RootNode#execute`.
During PE, the partial evaluator treats certain values as PE constants and can remove or simplify (among other operations):
- stable field loads from PE constants
- virtual calls with PE constant receivers
- conditional branches and loops over PE constants

When designing a Truffle interpreter, it is crucial to consider how the interpreter interacts with PE.
Interpreters must be written carefully to inform PE about stable behaviour, PE-friendly and PE-unfriendly code paths, and other optimization opportunities like loop unrolling.
Additionally, since Truffle interpreters are self-optimizing, stable interpreter data can actually change; the interpreter must deoptimize compiled code before changing that data.
This document describes how to write Truffle interpreters for PE.

For background on the Truffle partial evaluation approach, see [Practical Partial Evaluation for High-Performance Dynamic Language Runtimes](https://chrisseaton.com/truffleruby/pldi17-truffle/pldi17-truffle.pdf).

## Mental model when implementing a Truffle interpreter

The core idea: **partial evaluation optimizes all reachable code.**
PE starts from `RootNode#execute` and follows all the calls and branches it can reach.
It does not know which code you consider important; it optimizes whatever is reachable from `RootNode#execute` (the "fast path").

This leads to three practical rules:

1. Keep the fast path small and PE-friendly.
Fast-path code should have stable structure, stable fields, and straightforward control flow.

2. Move uncommon or complex Java code out of the fast path.
String manipulation, I/O, stream operations, hash map lookups, and other library-heavy code should normally be behind `@TruffleBoundary` or otherwise guarded so PE does not pull it into the compilation.

3. Expose stable interpreter state to PE.
Use the Truffle mechanisms described below to make stable state visible to PE and keep the fast path focused on code that PE can simplify.

A focused fast path gives the compiler a small, predictable program to optimize.
Code that is not written for PE can produce poorly compiled code, increase compilation time, or cause compilation to exceed size limits.

## Writing partially evaluable code

Partial evaluation exploits stable interpreter data to inline calls, fold constants, and remove unnecessary branches or loops.
Code on the fast path should have simple, non-recursive control flow and should be written to expose this stable data.

This section presents examples of fast-path code and the Truffle idioms and intrinsics you can use to guide PE.
Consult the [Truffle javadoc](https://www.graalvm.org/truffle/javadoc/) for complete details about these APIs.

### Stable fields with `final` and `@CompilationFinal`

Guidelines:
- Use `final` for fields whose values are fixed at construction time.
- Use `@CompilationFinal` for fields that are initialized or changed later, but should become stable before compilation.
- Invalidate compiled code before changing a `@CompilationFinal` field.

Partial evaluation starts from a small set of values that are constant for the current compilation.
This set includes the `RootNode` being compiled and ordinary `static final` Java fields.
Transitively, when a PE constant has a field marked `final` or `@CompilationFinal`, that field value is also treated as a PE constant.

Consider the following code:
```java
boolean flag;
int execute(VirtualFrame frame) {
    if (flag) {
        return doTrue(frame);
    } else {
        return doFalse(frame);
    }
}
```
Partial evaluation does not know the value of `flag`, so it will partially evaluate both branches, inlining and optimizing both `doTrue` and `doFalse` calls.
This can pull in more code than necessary, especially if `flag` is a stable value.
Marking the field `final` exposes the stable value to PE:

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
Assuming the receiver is PE constant, PE can treat `flag` as a constant and completely omit the unreachable branch from the compilation.

`@CompilationFinal` is similar to `final`, but declares a mutable field that should stabilize before compilation.
This allows interpreter self-specialization and lazy initialization.
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
The second compilation will see `initialized == true` and fold away the block altogether.

Before overwriting a `@CompilationFinal` field, invalidate any compiled code that relies on the stability of the old value.
Common mechanisms for invalidation are `CompilerDirectives.transferToInterpreterAndInvalidate()` and `Assumption`.
The field must also eventually stabilize, otherwise the code could get into a _deoptimization loop_ where it is repeatedly compiled and invalidated indefinitely.

### Stable AST structure with `@Child` and `@Children`

Guidelines:
- Use `@Child` and `@Children` for AST child nodes so PE can treat the AST structure as stable.
- Prefer these annotations over `@CompilationFinal` for `Node` children, unless you intentionally do not want adoption.

The actual AST structure of Truffle nodes is typically also stable.
Truffle has special `@Child` and `@Children` annotations that imply `@CompilationFinal` semantics for `Node` children.
These annotations also declare the child node(s) as adopted by the parent, which is important for AST operations such as replacement and traversal.
You almost always want to use these annotations for `Node` children instead of `@CompilationFinal`.

Declaring a node's children is especially important because nodes often delegate to their children using virtual calls, such as `execute` methods.
Being able to devirtualize these calls allows PE to perform inlining and further optimizations.
For example, in the `AddNode` below, `left` and `right` become PE constants and their concrete `executeInt` implementations can be inlined into the compilation:
```java
final class AddNode extends ExpressionNode {
    @Child ExpressionNode left;
    @Child ExpressionNode right;

    int executeInt(VirtualFrame frame) {
        return left.executeInt(frame) + right.executeInt(frame);
    }
}
```
When rewriting AST children at run time, use the normal Truffle rewriting operations such as `insert()` and `replace()`, which handle adoption and invalidation automatically.

### Stable dynamic behaviour with profiles, specializations, and caches

The previous sections demonstrate how stable PE constants can allow PE to fold branches and inline virtual calls.
Inevitably the interpreter will also have dynamic code, such as a branch condition depending on an input value, that PE cannot simplify using stable constants.
To address dynamism in interpreters, Truffle provides a variety of mechanisms to profile and specialize interpreters to the dynamic behaviour actually observed at run time.

#### Profiles

Guidelines:
- Use profiles when a dynamic branch or value is stable in practice, but is not a PE constant.
- Store profiles in stable fields so PE can see the profiling state.

In the code below, the branch condition depends on dynamic program state.
As currently written, PE will include both branches in compiled code:

```java
int execute(VirtualFrame frame, TruffleObject dynamicValue) {
    if (isError(dynamicValue)) {
        return doError(frame);
    } else {
        return doRegular(frame);
    }
}
```

However, if `isError(dynamicValue)` is almost always `false` in practice, we can do better.
Truffle provides profiles such as `ConditionProfile`, `ValueProfile`, and `BranchProfile` that allow interpreters to profile and speculate about stable dynamic values and branches.
In this example, we can use a `ConditionProfile` to track what boolean values are actually observed during interpretation:
```java
final ConditionProfile errorProfile = ConditionProfile.create();
int execute(VirtualFrame frame, TruffleObject dynamicValue) {
    if (errorProfile.profile(isError(dynamicValue))) {
        return doError(frame);
    } else {
        return doRegular(frame);
    }
}
```
The `errorProfile` field is a PE constant that records whether execution has ever seen `true` or `false`.
When this code is compiled, if the profile has only seen one outcome, PE can speculatively remove code for the other outcome.
For example, if `isError(dynamicValue)` always evaluates to `false`, PE can omit the `doError` case from compilation, and vice versa.
If the previously-unseen outcome is later observed at run time, the code deoptimizes, updates the profile, and can later be recompiled.

#### Specializations

Guidelines:
- Use specializations to split common fast-path cases from uncommon or complex cases.
- Treat specializations as an implicit mechanism to dynamically profile runtime behaviour and reduce code size.

The Truffle DSL's specialization mechanism is another way to specialize for the behaviour a node actually observes at run time.
Consider a node that adds either integers or strings:

```java
abstract class AddNode extends ExpressionNode {
    abstract Object execute(Object left, Object right);

    @Specialization
    int doInt(int left, int right) {
        return left + right;
    }

    @Specialization
    @TruffleBoundary
    String doString(String left, String right) {
        return left + right;
    }
}
```

Each `AddNode` instance specializes independently based on the values it actually observes.
If one particular `AddNode` only ever sees integers at run time, only `doInt` becomes active for that node instance.
PE then only needs to compile the integer path for that instance.
If that same node later also sees strings, it rewrites to support `doString` as well.
Future compilations of that node must then account for both specializations.

When implementing nodes or operations, consider declaring separate specializations for common and uncommon cases, and for cases that require significantly more code.
Only the specializations reached during interpretation get included in compiled code, which can reduce the complexity of the code submitted for compilation and enable further optimizations.

#### Inline caches

Guidelines:
- Use inline caches when an operation depends on a dynamic fact that is often stable for a node instance, such as a receiver layout.
- Guard cached facts and provide a fallback for cases that do not match the cache.
- Keep cache limits small enough that the compiled fast path stays focused.

Truffle specializations can also collect profiling data using inline caches.
Many languages maintain an object layout descriptor, sometimes called a hidden class.
The simplified example below shows a node whose behaviour depends on that descriptor:

```java
abstract class ReadAttributeNode extends Node {
    @Specialization
    static Object doDefault(MyObject receiver) {
        ObjectLayout layout = receiver.getLayout();
        return layout.readAttribute(receiver);
    }
}
```

The result depends on the receiver's current layout, which is not usually a PE constant.
If a particular `ReadAttributeNode` only ever observes one layout at run time, it is helpful to communicate that fact to PE.
We can declare an inline cache over the layout as follows:

```java
abstract class ReadAttributeNode extends Node {
    @Specialization(guards = "layout == cachedLayout", limit = "1")
    static Object doCached(MyObject receiver,
                    @Bind("receiver.getLayout()") ObjectLayout layout,
                    @Cached("layout") ObjectLayout cachedLayout) {
        return cachedLayout.readAttribute(receiver);
    }

    @Specialization(replaces = "doCached")
    static Object doDefault(MyObject receiver) {
        ObjectLayout layout = receiver.getLayout();
        return layout.readAttribute(receiver);
    }
}
```

The `doCached` specialization includes the guard `layout == cachedLayout`.
As long as that check succeeds, the cached specialization makes `cachedLayout` a PE constant.
PE can then optimize `cachedLayout.readAttribute(receiver)` for that specific layout.
If a compiled `doCached` path later sees a receiver with a different layout, the guard fails.
Execution transfers back to the interpreter, invalidates the compiled code that depended on the cached layout, and rewrites the node to the more general specialization.

Specializations with guards can have multiple cached entries, up to the specified `limit` (3 by default).
When no existing entry satisfies the specialization's guards, a new entry is added if the limit has not been reached; otherwise execution falls through to a later specialization.
The cached specialization is still used after the limit is reached unless some other specialization `replaces` it.
On the fast path, execution searches the cache for an entry satisfying the guards, so there is a tradeoff between cache size and fast path code performance.


### Loop unrolling with `@ExplodeLoop`

Guidelines:
- Use `@ExplodeLoop` to unroll loops that have a PE-constant number of iterations.
- Use `CompilerAsserts.partialEvaluationConstant(...)` calls to ensure variables that influence loop explosion (loop bounds, indices, etc.) are PE constants.
- Loop explosion can be used to fold loop lookups and computations at PE time.

Loops are harder for PE to optimize when the compiler must treat the loop bound or loop index as dynamic.
When a loop has a PE-constant number of iterations, `@ExplodeLoop` instructs PE to unroll the loop, replacing it with straight-line code.
After unrolling, each copy of the loop body has its own loop state, which can expose additional PE constants and enable further optimization.
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

`@ExplodeLoop` should only be used for methods whose loops are bounded by PE constants.
PE naively unrolls the loop body until loop exit, so compilation can fail (hitting graph size limits) if the loop is not actually bounded by PE constants.
Use `CompilerAsserts.partialEvaluationConstant(...)` as a guard rail to catch cases where an expected PE constant is not PE constant.

Loop explosion can also be a convenient way to fold loop-based lookups and computations at PE time.
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

When some code calls `getLiveLocalCount` (assuming `bci` is a PE constant), PE can iterate over the entire PE constant `locals` array, evaluate `isLive` for each of them, and compute the value of `count` during partial evaluation.
This pattern allows interpreters to replace a potentially slow computation in the interpreter with a PE constant during compilation.

`@ExplodeLoop` has several modes for different loop shapes and code-size tradeoffs.
Use the default form for fixed small loops, and consult the Javadoc when choosing a more specialized explosion mode.

### Slow path directives

Guidelines:
- Code that is unsuitable for partial evaluation should be excluded from the fast path.
- Use `@TruffleBoundary` to exclude code not designed for PE from the fast path; this especially applies to Java library code.
- Do not pass `VirtualFrame` to `@TruffleBoundary` methods; instead, read any values of interest from the frame and pass them instead.
- Be intentional about what exceptions should be present on the fast path. Host Java exceptions should never be on the fast path.
- Use `CompilerDirectives.inInterpreter()` and `CompilerDirectives.inCompiledCode()` to separate code that needs to run only in the interpreter or only in compiled code.

Some code should not be on the PE fast path at all.
String manipulation, I/O, stream operations, hash map lookups, and other library-heavy or uncommon operations are not designed to partially evaluate.
They should be isolated so they do not become part of the compiled fast path, usually using `@TruffleBoundary`, which acts as a cutoff for PE.

One important restriction: it is a compile-time error for a `@TruffleBoundary` method to declare a `VirtualFrame` parameter.
Scalar replacement of the frame is critical for performance, so Truffle does not allow virtual frames to escape into boundary methods that escape analysis cannot "see into".
If slow-path code needs frame data, read the required values before calling the boundary method and pass those values instead.

Below is an example with code that does not belong on the fast path:
```java
int execute(VirtualFrame frame) {
    if (error(frame)) {
        throw new MyTruffleException("bad value: " + frame.getObject(slot));
    }
    return body.executeInt(frame);
}
```
String concatenation does not belong on the fast path.
To fix this code, we should move the String concatenation to a boundary method.
Then, since the concatenation depends on a value in `frame`, we should read that value and pass it to the boundary method:

```java
int execute(VirtualFrame frame) {
    if (error(frame)) {
        Object value = frame.getObject(slot);
        throw new MyTruffleException(makeErrorMessage(value));
    }
    return body.executeInt(frame);
}

@TruffleBoundary
private static String makeErrorMessage(Object value) {
    return "bad value: " + value;
}
```

You should also decide whether an exception should be thrown and handled on the fast path.
It is often reasonable to keep `AbstractTruffleException` and `ControlFlowException` throw/catch paths visible to PE, because PE can represent the throw/catch path as regular graph control flow.
These exceptions are also cheap to construct because they avoid ordinary Java stack-trace capture.
If constructing the exception requires PE-unfriendly work, such as the error message construction shown above, put that work behind a boundary and keep the throw itself visible to PE.

In contrast, ordinary Java exceptions do not belong on the fast path: they are expensive to construct, usually represent uncommon or erroneous state that does not merit optimization by PE, and in some cases are [skipped entirely by PE](./DeoptCyclePatterns.md#skipped-exceptions).
Such exceptions should be excluded from the fast path by using `@TruffleBoundary` at throw sites.
At catch sites, if the exception is a genuine error (or the interpreter modifies itself to avoid the path in the future), you can use `CompilerDirectives.transferToInterpreterAndInvalidate()` to exclude it from compilation.

You can also use `CompilerDirectives.inInterpreter()` and `CompilerDirectives.inCompiledCode()` to restrict code to interpreted or compiled execution.
For example, code that performs profiling can be guarded by `CompilerDirectives.inInterpreter()` to disable profiling in compiled code.
Another use case is to put a PE-friendly but expensive computation in compiled code and an equivalent PE-unfriendly but fast computation in the interpreter:

```java
if (CompilerDirectives.inCompiledCode()) {
    // Iterate through a PE-constant array (expensive in interpreter, folds to a constant in PE).
    value = findEntryByIteration(entriesArray, key);
} else {
    // Hash table lookup (cheap in interpreter, does not partially evaluate well).
    value = hashMap.get(key);
}
```

This document uses only a small subset of the APIs in `CompilerDirectives` and `CompilerAsserts`.
These classes contain additional low-level tools, including predicates such as `CompilerDirectives.isPartialEvaluationConstant(...)`, that can be useful in specific scenarios.
Read the Javadoc carefully before using these mechanisms.

## Common mistakes

Below are a few common mistakes users make when writing code for PE.

### Assuming `@CompilationFinal` works on non-PE-constant receivers

It is common to assume that all loads of a `@CompilationFinal` field fold to PE constants.
However, partial evaluation can only fold loads of a PE constant field if its receiver is also a PE constant.
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

If `receiver` is not a PE constant, the `readAttribute` method can observe any arbitrary value for `receiver`, and so PE cannot fold `receiver.layout` to a constant value.
Instead, you should restructure the interpreter so that `layout` is actually PE constant, for example by using an inline cache:

```java
@Specialization(guards = "layout == cachedLayout", limit = "1")
static Object readCached(MyObject receiver,
                @Bind("receiver.getLayout()") ObjectLayout layout,
                @Cached("layout") ObjectLayout cachedLayout) {
    return cachedLayout.read(receiver);
}

@Specialization(replaces = "readCached")
static Object readGeneric(MyObject receiver) {
    return receiver.getLayout().read(receiver);
}
```

This usage pattern is a common source of [deoptimization cycles](./DeoptCyclePatterns.md#compilation-final-field-of-a-non-constant-object).

### Forgetting `@CompilationFinal(dimensions = ...)` for arrays

`@CompilationFinal` indicates that a field value is PE constant, but it says nothing about the stability of the value's subfields.
For object fields, the stability of subfields is determined by the annotations used on those fields.
For array fields, use `@CompilationFinal(dimensions = ...)` to indicate whether the elements of the array are stable:

```java
// table0 is PE constant
@CompilationFinal private int[][] table0;
// table1, table1[c1] are PE constant
@CompilationFinal(dimensions = 1) private int[][] table1;
// table2, table2[c1], table2[c1][c2] are PE constant
@CompilationFinal(dimensions = 2) private int[][] table2;
```

As with object fields, before mutating PE constant elements of an array, you must invalidate any compiled code that relies on the stability of the elements.

### Using `@ExplodeLoop` with unbounded loops

`@ExplodeLoop` is not a general-purpose performance annotation.
Use it when the loop has a PE-constant number of iterations and unrolling exposes something useful to PE, such as constant child nodes, constant metadata, or foldable lookup results.

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
It will repeatedly unroll the loop indefinitely until failing with a `GraphTooBigBailoutException`.

If `values.length` should be a PE constant, use `CompilerAsserts.partialEvaluationConstant(...)` to check this.
When the value is not PE constant, the assertion will cause compilation to fail with a useful error message.

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
Loop explosion can actually be counterproductive in some cases, so not every loop bounded by PE constants should be exploded by PE.
Consider the following example:

```java
@CompilationFinal(dimensions = 1) private final String[] metricNames;

@ExplodeLoop
void reportValue(Object value) {
    for (int i = 0; i < metricNames.length; i++) {
        reportSlowPath(metricNames[i], value);
    }
}

@TruffleBoundary
static void reportSlowPath(String metricName, Object value) {
    // PE-unfriendly work such as formatting, allocation, or I/O.
}
```

Depending on the length of `metricNames`, exploding the loop above can drastically increase code size without any apparent benefit to PE.
Even though the number of iterations is a PE constant, each unrolled iteration is still just another boundary call that PE cannot see through.
Loop explosion is most useful when the explosion reveals stable constants that allow PE to perform further optimization (e.g., revealing PE constant method receivers).
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
