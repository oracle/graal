# Runtime compilation in the Bytecode DSL

Bytecode DSL interpreters, just like Truffle AST interpreters, use runtime compilation to optimize hot code.
Runtime compilation leverages partial evaluation (PE) to specialize  the interpreter by aggressively constant folding interpreter code with respect to a given guest program.

Partial evaluation reduces much of the overhead required to execute the interpreter.
For Bytecode DSL interpreters, PE unrolls the bytecode dispatch loop using the actual bytecode, which can completely remove the overhead of bytecode dispatch.

For technical reasons, there are some limitations to runtime-compiled code that you should be aware of.

## Partial evaluation constants

Because of how Bytecode DSL interpreters execute, sometimes constant data is not determined to be constant by PE.

A `LoadConstant` operation, despite its name, is not guaranteed to produce a PE constant.
`LoadConstant` executes by pushing a constant onto the operand stack (in the `Frame`).
This value is later popped by the operation that consumes it.
When the constant is accessed from the frame by the consuming operation, PE cannot always reduce its value to a PE constant.

A [`@Variadic`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/Variadic.java) operand's length is statically determined by the number of operations parsed during bytecode generation.
However, a variadic operand only has a PE constant length up to a certain length (8) because of some limitations with the current implementation.
PE cannot statically determine the length of larger arrays; there are plans to fix this in a future release.

### Workarounds

If an operand is always a constant value, it can be declared as a [`@ConstantOperand`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/ConstantOperand.java).
Unlike regular (dynamic) operands that are computed and then pushed/popped from the stack, constant operands are read directly from an array of constants, so their values are always PE constants.
Operands whose values guide partial evaluation (e.g., loop counters used to unroll loops) should be declared using `@ConstantOperand`.

If an operand is sometimes (but not always) always a constant, you can speculate over its const-ness using the regular Truffle DSL. For example, the following operation speculates that the array operand length is constant, unrolling the loop when it is, and falling back on a general specialization when the assumption fails:

```java
@Operation
public static final class IterateArray {
    @ExplodeLoop
    @Specialization(guards = "array.length == cachedLength", limit = "1")
    public static void doCachedLength(Object[] array, @Cached("array.length") int cachedLength) {
        for (int i = 0; i < cachedLength; i++) {
            ...
        }
    }

    @Specialization(replaces = "doCachedLength")
    public static void doAnyLength(Object[] array) {
        for (int i = 0; i < array.length; i++) {
            ...
        }
    }
}
```

In general, if your interpreter relies on a value being PE constant (e.g., to unroll a loop) it is a good idea to assert the value is `CompilerAsserts.partialEvaluationConstant` (see [`CompilerAsserts`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api/src/com/oracle/truffle/api/CompilerAsserts.java)).
When these assertions fail, compilation bails out early with a helpful diagnostic message, rather than silently producing sub-optimal code (or failing with a less actionable error message).

## Source information

[Reparsing](UserGuide.md#reparsing) a root node changes its current `BytecodeNode`. If the bytecode changes (i.e., an instrumentation is added), any compiled code for the node will be invalidated.
To avoid unnecessary invalidations, reparsing **does not** invalidate code for source-only updates.

Consequently, the current `BytecodeNode` can be out of date in compiled code if source information is lazily materialized.
You can use `BytecodeNode#ensureSourceInformation` in compiled code to obtain an up-to-date bytecode node with source information.
This method will return the current node, if sources are available, or deoptimize and reparse.
Since most computations involving sources are not PE friendly, you may also wish to put them behind a `@TruffleBoundary` and use `BytecodeRootNode#getBytecodeNode` to obtain an up-to-date bytecode node.