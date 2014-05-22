/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.oracle.graal.java;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.java.BciBlockMapping.BciBlock;
import com.oracle.graal.java.BciBlockMapping.LocalLiveness;

public abstract class AbstractFrameStateBuilder<T extends KindProvider, S extends AbstractFrameStateBuilder<T, S>> {

    protected final ResolvedJavaMethod method;
    protected int stackSize;
    protected final T[] locals;
    protected final T[] stack;
    protected T[] lockedObjects;

    /**
     * @see BytecodeFrame#rethrowException
     */
    protected boolean rethrowException;

    public AbstractFrameStateBuilder(ResolvedJavaMethod method, T[] l, T[] s, T[] lo) {
        this.method = method;
        this.locals = l;
        this.stack = s;
        this.lockedObjects = lo;
    }

    protected AbstractFrameStateBuilder(S other) {
        this.method = other.method;
        this.stackSize = other.stackSize;
        this.locals = other.locals.clone();
        this.stack = other.stack.clone();
        this.lockedObjects = other.lockedObjects == getEmtpyArray() ? getEmtpyArray() : other.lockedObjects.clone();
        this.rethrowException = other.rethrowException;

        assert locals.length == method.getMaxLocals();
        assert stack.length == Math.max(1, method.getMaxStackSize());
    }

    public abstract S copy();

    protected abstract T[] getEmtpyArray();

    public abstract boolean isCompatibleWith(S other);

    public void clearNonLiveLocals(BciBlock block, LocalLiveness liveness, boolean liveIn) {
        /*
         * (lstadler) if somebody is tempted to remove/disable this clearing code: it's possible to
         * remove it for normal compilations, but not for OSR compilations - otherwise dead object
         * slots at the OSR entry aren't cleared. it is also not enough to rely on PiNodes with
         * Kind.Illegal, because the conflicting branch might not have been parsed.
         */
        if (liveness == null) {
            return;
        }
        if (liveIn) {
            for (int i = 0; i < locals.length; i++) {
                if (!liveness.localIsLiveIn(block, i)) {
                    locals[i] = null;
                }
            }
        } else {
            for (int i = 0; i < locals.length; i++) {
                if (!liveness.localIsLiveOut(block, i)) {
                    locals[i] = null;
                }
            }
        }
    }

    /**
     * @see BytecodeFrame#rethrowException
     */
    public boolean rethrowException() {
        return rethrowException;
    }

    /**
     * @see BytecodeFrame#rethrowException
     */
    public void setRethrowException(boolean b) {
        rethrowException = b;
    }

    /**
     * Returns the size of the local variables.
     *
     * @return the size of the local variables
     */
    public int localsSize() {
        return locals.length;
    }

    /**
     * Gets the current size (height) of the stack.
     */
    public int stackSize() {
        return stackSize;
    }

    /**
     * @return the current lock depth
     */
    public int lockDepth() {
        return lockedObjects.length;
    }

    /**
     * Gets the value in the local variables at the specified index, without any sanity checking.
     *
     * @param i the index into the locals
     * @return the instruction that produced the value for the specified local
     */
    public T localAt(int i) {
        return locals[i];
    }

    /**
     * Get the value on the stack at the specified stack index.
     *
     * @param i the index into the stack, with {@code 0} being the bottom of the stack
     * @return the instruction at the specified position in the stack
     */
    public T stackAt(int i) {
        return stack[i];
    }

    /**
     * Gets the value in the lock at the specified index, without any sanity checking.
     *
     * @param i the index into the lock
     * @return the instruction that produced the value for the specified lock
     */
    public T lockAt(int i) {
        return lockedObjects[i];
    }

    public void storeLock(int i, T lock) {
        lockedObjects[i] = lock;
    }

    /**
     * Loads the local variable at the specified index, checking that the returned value is non-null
     * and that two-stack values are properly handled.
     *
     * @param i the index of the local variable to load
     * @return the instruction that produced the specified local
     */
    public T loadLocal(int i) {
        T x = locals[i];
        assert !isTwoSlot(x.getKind()) || locals[i + 1] == null;
        assert i == 0 || locals[i - 1] == null || !isTwoSlot(locals[i - 1].getKind());
        return x;
    }

    /**
     * Stores a given local variable at the specified index. If the value occupies two slots, then
     * the next local variable index is also overwritten.
     *
     * @param i the index at which to store
     * @param x the instruction which produces the value for the local
     */
    public void storeLocal(int i, T x) {
        assert x == null || x.getKind() != Kind.Void && x.getKind() != Kind.Illegal : "unexpected value: " + x;
        locals[i] = x;
        if (x != null && isTwoSlot(x.getKind())) {
            // if this is a double word, then kill i+1
            locals[i + 1] = null;
        }
        if (x != null && i > 0) {
            T p = locals[i - 1];
            if (p != null && isTwoSlot(p.getKind())) {
                // if there was a double word at i - 1, then kill it
                locals[i - 1] = null;
            }
        }
    }

    public void storeStack(int i, T x) {
        assert x == null || (stack[i] == null || x.getKind() == stack[i].getKind()) : "Method does not handle changes from one-slot to two-slot values or non-alive values";
        stack[i] = x;
    }

    /**
     * Pushes an instruction onto the stack with the expected type.
     *
     * @param kind the type expected for this instruction
     * @param x the instruction to push onto the stack
     */
    public void push(Kind kind, T x) {
        assert x.getKind() != Kind.Void && x.getKind() != Kind.Illegal;
        xpush(assertKind(kind, x));
        if (isTwoSlot(kind)) {
            xpush(null);
        }
    }

    /**
     * Pushes a value onto the stack without checking the type.
     *
     * @param x the instruction to push onto the stack
     */
    public void xpush(T x) {
        assert x == null || (x.getKind() != Kind.Void && x.getKind() != Kind.Illegal);
        stack[stackSize++] = x;
    }

    /**
     * Pushes a value onto the stack and checks that it is an int.
     *
     * @param x the instruction to push onto the stack
     */
    public void ipush(T x) {
        xpush(assertInt(x));
    }

    /**
     * Pushes a value onto the stack and checks that it is a float.
     *
     * @param x the instruction to push onto the stack
     */
    public void fpush(T x) {
        xpush(assertFloat(x));
    }

    /**
     * Pushes a value onto the stack and checks that it is an object.
     *
     * @param x the instruction to push onto the stack
     */
    public void apush(T x) {
        xpush(assertObject(x));
    }

    /**
     * Pushes a value onto the stack and checks that it is a long.
     *
     * @param x the instruction to push onto the stack
     */
    public void lpush(T x) {
        xpush(assertLong(x));
        xpush(null);
    }

    /**
     * Pushes a value onto the stack and checks that it is a double.
     *
     * @param x the instruction to push onto the stack
     */
    public void dpush(T x) {
        xpush(assertDouble(x));
        xpush(null);
    }

    public void pushReturn(Kind kind, T x) {
        if (kind != Kind.Void) {
            push(kind.getStackKind(), x);
        }
    }

    /**
     * Pops an instruction off the stack with the expected type.
     *
     * @param kind the expected type
     * @return the instruction on the top of the stack
     */
    public T pop(Kind kind) {
        assert kind != Kind.Void;
        if (isTwoSlot(kind)) {
            xpop();
        }
        return assertKind(kind, xpop());
    }

    /**
     * Pops a value off of the stack without checking the type.
     *
     * @return x the instruction popped off the stack
     */
    public T xpop() {
        T result = stack[--stackSize];
        return result;
    }

    /**
     * Pops a value off of the stack and checks that it is an int.
     *
     * @return x the instruction popped off the stack
     */
    public T ipop() {
        return assertInt(xpop());
    }

    /**
     * Pops a value off of the stack and checks that it is a float.
     *
     * @return x the instruction popped off the stack
     */
    public T fpop() {
        return assertFloat(xpop());
    }

    /**
     * Pops a value off of the stack and checks that it is an object.
     *
     * @return x the instruction popped off the stack
     */
    public T apop() {
        return assertObject(xpop());
    }

    /**
     * Pops a value off of the stack and checks that it is a long.
     *
     * @return x the instruction popped off the stack
     */
    public T lpop() {
        assertHigh(xpop());
        return assertLong(xpop());
    }

    /**
     * Pops a value off of the stack and checks that it is a double.
     *
     * @return x the instruction popped off the stack
     */
    public T dpop() {
        assertHigh(xpop());
        return assertDouble(xpop());
    }

    /**
     * Pop the specified number of slots off of this stack and return them as an array of
     * instructions.
     *
     * @return an array containing the arguments off of the stack
     */
    public T[] popArguments(int slotSize, int argSize) {
        int base = stackSize - slotSize;
        List<T> r = new ArrayList<>(argSize);
        int stackindex = 0;
        while (stackindex < slotSize) {
            T element = stack[base + stackindex];
            assert element != null;
            r.add(element);
            stackindex += stackSlots(element.getKind());
        }
        stackSize = base;
        return r.toArray(getEmtpyArray());
    }

    /**
     * Peeks an element from the operand stack.
     *
     * @param argumentNumber The number of the argument, relative from the top of the stack (0 =
     *            top). Long and double arguments only count as one argument, i.e., null-slots are
     *            ignored.
     * @return The peeked argument.
     */
    public T peek(int argumentNumber) {
        int idx = stackSize() - 1;
        for (int i = 0; i < argumentNumber; i++) {
            if (stackAt(idx) == null) {
                idx--;
                assert isTwoSlot(stackAt(idx).getKind());
            }
            idx--;
        }
        return stackAt(idx);
    }

    public static int stackSlots(Kind kind) {
        return isTwoSlot(kind) ? 2 : 1;
    }

    /**
     * Clears all values on this stack.
     */
    public void clearStack() {
        stackSize = 0;
    }

    protected static boolean isTwoSlot(Kind kind) {
        assert kind != Kind.Void && kind != Kind.Illegal;
        return kind == Kind.Long || kind == Kind.Double;
    }

    private T assertKind(Kind kind, T x) {
        assert x != null && x.getKind() == kind : "kind=" + kind + ", value=" + x + ((x == null) ? "" : ", value.kind=" + x.getKind());
        return x;
    }

    private T assertLong(T x) {
        assert x != null && (x.getKind() == Kind.Long);
        return x;
    }

    private T assertInt(T x) {
        assert x != null && (x.getKind() == Kind.Int);
        return x;
    }

    private T assertFloat(T x) {
        assert x != null && (x.getKind() == Kind.Float);
        return x;
    }

    private T assertObject(T x) {
        assert x != null && (x.getKind() == Kind.Object);
        return x;
    }

    private T assertDouble(T x) {
        assert x != null && (x.getKind() == Kind.Double);
        return x;
    }

    private void assertHigh(T x) {
        assert x == null;
    }

}
