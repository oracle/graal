package com.oracle.graal.java;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.nodes.*;

public abstract class AbstractFrameStateBuilder<T> {

    protected final ResolvedJavaMethod method;
    protected final StructuredGraph graph;
    protected int stackSize;

    public AbstractFrameStateBuilder(ResolvedJavaMethod method, StructuredGraph graph) {
        assert graph != null;
        this.method = method;
        this.graph = graph;
    }

    protected AbstractFrameStateBuilder(AbstractFrameStateBuilder other) {
        assert other.graph != null;
        this.method = other.method;
        this.graph = other.graph;
    }

    /**
     * Returns the size of the local variables.
     * 
     * @return the size of the local variables
     */
    public abstract int localsSize();

    /**
     * Gets the current size (height) of the stack.
     */
    public int stackSize() {
        return stackSize;
    }

    /**
     * Gets the value in the local variables at the specified index, without any sanity checking.
     * 
     * @param i the index into the locals
     * @return the instruction that produced the value for the specified local
     */
    public abstract T localAt(int i);

    /**
     * Get the value on the stack at the specified stack index.
     * 
     * @param i the index into the stack, with {@code 0} being the bottom of the stack
     * @return the instruction at the specified position in the stack
     */
    public abstract T stackAt(int i);

    /**
     * Loads the local variable at the specified index, checking that the returned value is non-null
     * and that two-stack values are properly handled.
     * 
     * @param i the index of the local variable to load
     * @return the instruction that produced the specified local
     */
    public abstract T loadLocal(int i);

    /**
     * Stores a given local variable at the specified index. If the value occupies
     * {@linkplain HIRFrameStateBuilder#isTwoSlot(Kind) two slots}, then the next local variable index
     * is also overwritten.
     * 
     * @param i the index at which to store
     * @param x the instruction which produces the value for the local
     */
    public abstract void storeLocal(int i, T x);

    public abstract void storeStack(int i, T x);

    /**
     * Pushes an instruction onto the stack with the expected type.
     * 
     * @param kind the type expected for this instruction
     * @param x the instruction to push onto the stack
     */
    public abstract void push(Kind kind, T x);

    /**
     * Pushes a value onto the stack without checking the type.
     * 
     * @param x the instruction to push onto the stack
     */
    public abstract void xpush(T x);

    /**
     * Pushes a value onto the stack and checks that it is an int.
     * 
     * @param x the instruction to push onto the stack
     */
    public abstract void ipush(T x);

    /**
     * Pushes a value onto the stack and checks that it is a float.
     * 
     * @param x the instruction to push onto the stack
     */
    public abstract void fpush(T x);

    /**
     * Pushes a value onto the stack and checks that it is an object.
     * 
     * @param x the instruction to push onto the stack
     */
    public abstract void apush(T x);

    /**
     * Pushes a value onto the stack and checks that it is a long.
     * 
     * @param x the instruction to push onto the stack
     */
    public abstract void lpush(T x);

    /**
     * Pushes a value onto the stack and checks that it is a double.
     * 
     * @param x the instruction to push onto the stack
     */
    public abstract void dpush(T x);

    public abstract void pushReturn(Kind kind, T x);

    /**
     * Pops an instruction off the stack with the expected type.
     * 
     * @param kind the expected type
     * @return the instruction on the top of the stack
     */
    public abstract T pop(Kind kind);

    /**
     * Pops a value off of the stack without checking the type.
     * 
     * @return x the instruction popped off the stack
     */
    public abstract T xpop();

    /**
     * Pops a value off of the stack and checks that it is an int.
     * 
     * @return x the instruction popped off the stack
     */
    public abstract T ipop();

    /**
     * Pops a value off of the stack and checks that it is a float.
     * 
     * @return x the instruction popped off the stack
     */
    public abstract T fpop();

    /**
     * Pops a value off of the stack and checks that it is an object.
     * 
     * @return x the instruction popped off the stack
     */
    public abstract T apop();

    /**
     * Pops a value off of the stack and checks that it is a long.
     * 
     * @return x the instruction popped off the stack
     */
    public abstract T lpop();

    /**
     * Pops a value off of the stack and checks that it is a double.
     * 
     * @return x the instruction popped off the stack
     */
    public abstract T dpop();

    /**
     * Pop the specified number of slots off of this stack and return them as an array of
     * instructions.
     * 
     * @return an array containing the arguments off of the stack
     */
    public abstract T[] popArguments(int slotSize, int argSize);

    /**
     * Peeks an element from the operand stack.
     * 
     * @param argumentNumber The number of the argument, relative from the top of the stack (0 =
     *            top). Long and double arguments only count as one argument, i.e., null-slots are
     *            ignored.
     * @return The peeked argument.
     */
    public abstract T peek(int argumentNumber);

    /**
     * Clears all values on this stack.
     */
    public void clearStack() {
        stackSize = 0;
    }

}
