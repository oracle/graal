# Runtime compilation in Bytecode DSL

Bytecode DSL interpreters use runtime compilation to optimize hot code.
Just like AST interpreters, runtime compilation is achieved using partial evaluation (PE).

With Bytecode DSL interpreters, PE unrolls the bytecode dispatch loop using the actual bytecode, which completely removes the overhead of the bytecode dispatch.
However, there are some limitations to PE, which we discuss below, as well as some workarounds to enable more effective compilations.

## Limitations

Because of how the bytecode interpreters execute, sometimes data you'd expect to be PE constant is actually not.

A `LoadConstant` operation, despite its name, is not guaranteed to produce a PE constant.
`LoadConstant` executes by pushing a constant onto the operand stack (in the `Frame`), and thuis value is later popped by the operation that consumes it.
Partial evaluation cannot always statically determine the popped value that comes out of the frame.

A [`@Variadic`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/Variadic.java) operand, whose length is statically determined during bytecode generation, only has a PE constant length up to a certain length (8).
Because of the current implementation of variadic arguments, PE cannot determine the length of larger arrays (there are plans to fix this in a future release).

## Workarounds

If an operand is always a constant value, it can be declared as a [`@ConstantOperand`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/ConstantOperand.java).
Unlike regular (dynamic) operands that are computed and then pushed/popped from the stack, constant operands are read directly from an array of constants, so their values are always PE constants.
Operands whose values guide partial evaluation (e.g., loop counters used to unroll loops) should generally be declared using `@ConstantOperand`.

If an operand is sometimes (but not always) always a constant, you can speculate over its const-ness using the regular Truffle DSL. For example, the following operation speculates that the operand length is constant, falling back on a general specialization when the assumption fails:

```
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