/*
 * Copyright (c) 2010, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.c1x.value;

import java.util.*;

import com.sun.c1x.*;
import com.sun.c1x.ir.*;
import com.sun.c1x.util.*;
import com.sun.cri.ci.*;


/**
 * The {@code MutableFrameState} class extends abstract {@link FrameState} with methods modifying the frame state.
 * Only {@code MutableFrameState} can be instantiated and thus the object references in which it is stored decide
 * whether it is used as immutable or not. Thus and because they can be shared at different places in the compiler,
 * {@link FrameState}s must not be cast to {@code MutableFrameState}s. Instead, a new copy must be created using
 * {@link FrameState#copy()}.
 * Contrariwise and as an optimization, an instance referenced as {@code MutableFrameState} can be assigned to
 * a variable, field, or method parameter of type {@link FrameState} without creating an immutable copy before
 * (using {@link #immutableCopy(int)}) if the state is not mutated after the assignment.
 *
 * @author Michael Duller
 */
public final class MutableFrameState extends FrameState {

    public MutableFrameState(IRScope irScope, int bci, int maxLocals, int maxStack) {
        super(irScope, bci, maxLocals, maxStack);
    }

    /**
     * Replace the local variables in this frame state with the local variables from the specified frame state. This is
     * used in inlining.
     *
     * @param with the frame state containing the new local variables
     */
    public void replaceLocals(FrameState with) {
        assert with.maxLocals == maxLocals;
        System.arraycopy(with.values, 0, values, 0, maxLocals);
    }

    /**
     * Replace the stack in this frame state with the stack from the specified frame state. This is used in inlining.
     *
     * @param with the frame state containing the new local variables
     */
    public void replaceStack(FrameState with) {
        System.arraycopy(with.values, with.maxLocals, values, maxLocals, with.stackIndex);
        stackIndex = with.stackIndex;
        assert stackIndex >= 0;
    }

    /**
     * Replace the locks in this frame state with the locks from the specified frame state. This is used in inlining.
     *
     * @param with the frame state containing the new local variables
     */
    public void replaceLocks(FrameState with) {
        if (with.locks == null) {
            locks = null;
        } else {
            locks = Util.uncheckedCast(with.locks.clone());
        }
    }

    /**
     * Clears all values on this stack.
     */
    public void clearStack() {
        stackIndex = 0;
    }

    public void clearLocals() {
        for (int i = 0; i < maxLocals; i++) {
            values[i] = null;
        }
    }

    /**
     * Truncates this stack to the specified size.
     * @param size the size to truncate to
     */
    public void truncateStack(int size) {
        stackIndex = size;
        assert stackIndex >= 0;
    }

    /**
     * Pushes an instruction onto the stack with the expected type.
     * @param kind the type expected for this instruction
     * @param x the instruction to push onto the stack
     */
    public void push(CiKind kind, Value x) {
        assert kind != CiKind.Void;
        xpush(assertKind(kind, x));
        if (kind.sizeInSlots() == 2) {
            xpush(null);
        }
    }

    /**
     * Pushes a value onto the stack without checking the type.
     * @param x the instruction to push onto the stack
     */
    public void xpush(Value x) {
        assert stackIndex >= 0;
        assert maxLocals + stackIndex < values.length;
        values[maxLocals + stackIndex++] = x;
    }

    /**
     * Pushes a value onto the stack and checks that it is an int.
     * @param x the instruction to push onto the stack
     */
    public void ipush(Value x) {
        xpush(assertInt(x));
    }

    /**
     * Pushes a value onto the stack and checks that it is a float.
     * @param x the instruction to push onto the stack
     */
    public void fpush(Value x) {
        xpush(assertFloat(x));
    }

    /**
     * Pushes a value onto the stack and checks that it is an object.
     * @param x the instruction to push onto the stack
     */
    public void apush(Value x) {
        xpush(assertObject(x));
    }

    /**
     * Pushes a value onto the stack and checks that it is a word.
     * @param x the instruction to push onto the stack
     */
    public void wpush(Value x) {
        xpush(assertWord(x));
    }

    /**
     * Pushes a value onto the stack and checks that it is a JSR return address.
     * @param x the instruction to push onto the stack
     */
    public void jpush(Value x) {
        xpush(assertJsr(x));
    }

    /**
     * Pushes a value onto the stack and checks that it is a long.
     *
     * @param x the instruction to push onto the stack
     */
    public void lpush(Value x) {
        xpush(assertLong(x));
        xpush(null);
    }

    /**
     * Pushes a value onto the stack and checks that it is a double.
     * @param x the instruction to push onto the stack
     */
    public void dpush(Value x) {
        xpush(assertDouble(x));
        xpush(null);
    }

    /**
     * Pops an instruction off the stack with the expected type.
     * @param kind the expected type
     * @return the instruction on the top of the stack
     */
    public Value pop(CiKind kind) {
        if (kind.sizeInSlots() == 2) {
            xpop();
        }
        return assertKind(kind, xpop());
    }

    /**
     * Pops a value off of the stack without checking the type.
     * @return x the instruction popped off the stack
     */
    public Value xpop() {
        assert stackIndex >= 1;
        return values[maxLocals + --stackIndex];
    }

    /**
     * Pops a value off of the stack and checks that it is an int.
     * @return x the instruction popped off the stack
     */
    public Value ipop() {
        return assertInt(xpop());
    }

    /**
     * Pops a value off of the stack and checks that it is a float.
     * @return x the instruction popped off the stack
     */
    public Value fpop() {
        return assertFloat(xpop());
    }

    /**
     * Pops a value off of the stack and checks that it is an object.
     * @return x the instruction popped off the stack
     */
    public Value apop() {
        return assertObject(xpop());
    }

    /**
     * Pops a value off of the stack and checks that it is a word.
     * @return x the instruction popped off the stack
     */
    public Value wpop() {
        return assertWord(xpop());
    }

    /**
     * Pops a value off of the stack and checks that it is a JSR return address.
     * @return x the instruction popped off the stack
     */
    public Value jpop() {
        return assertJsr(xpop());
    }

    /**
     * Pops a value off of the stack and checks that it is a long.
     * @return x the instruction popped off the stack
     */
    public Value lpop() {
        assertHigh(xpop());
        return assertLong(xpop());
    }

    /**
     * Pops a value off of the stack and checks that it is a double.
     * @return x the instruction popped off the stack
     */
    public Value dpop() {
        assertHigh(xpop());
        return assertDouble(xpop());
    }

    private static Value assertKind(CiKind kind, Value x) {
        assert x != null && (x.kind == kind || !isTypesafe()) : "kind=" + kind + ", value=" + x + ((x == null) ? "" : ", value.kind=" + x.kind);
        return x;
    }

    private static Value assertLong(Value x) {
        assert x != null && (x.kind == CiKind.Long || !isTypesafe());
        return x;
    }

    private static Value assertJsr(Value x) {
        assert x != null && (x.kind == CiKind.Jsr || !isTypesafe());
        return x;
    }

    private static Value assertInt(Value x) {
        assert x != null && (x.kind == CiKind.Int || !isTypesafe());
        return x;
    }

    private static Value assertFloat(Value x) {
        assert x != null && (x.kind == CiKind.Float || !isTypesafe());
        return x;
    }

    private static Value assertObject(Value x) {
        assert x != null && (x.kind == CiKind.Object || !isTypesafe());
        return x;
    }

    private static Value assertWord(Value x) {
        assert x != null && (x.kind == CiKind.Word || !isTypesafe());
        return x;
    }

    private static Value assertDouble(Value x) {
        assert x != null && (x.kind == CiKind.Double || !isTypesafe());
        return x;
    }

    /**
     * Pop the specified number of slots off of this stack and return them as an array of instructions.
     * @param size the number of arguments off of the stack
     * @return an array containing the arguments off of the stack
     */
    public Value[] popArguments(int size) {
        int base = stackIndex - size;
        Value[] r = new Value[size];
        int y = maxLocals + base;
        for (int i = 0; i < size; ++i) {
            assert values[y] != null || values[y - 1].kind.jvmSlots == 2;
            r[i] = values[y++];
        }
        stackIndex = base;
        assert stackIndex >= 0;
        return r;
    }

    /**
     * Locks a new object within the specified IRScope.
     * @param scope the IRScope in which this locking operation occurs
     * @param obj the object being locked
     */
    public void lock(IRScope scope, Value obj, int totalNumberOfLocks) {
        if (locks == null) {
            locks = new ArrayList<Value>(4);
        }
        locks.add(obj);
        scope.updateMaxLocks(totalNumberOfLocks);
    }

    /**
     * Unlock the lock on the top of the stack.
     */
    public void unlock() {
        locks.remove(locks.size() - 1);
    }

    /**
     * Gets an immutable copy of this state.
     * @param bci the bytecode index of the new frame state
     */
    public FrameState immutableCopy(int bci) {
        return copy(bci, true, true, true);
    }

    /**
     * Determines if the current compilation is typesafe.
     */
    private static boolean isTypesafe() {
        return C1XCompilation.compilation().isTypesafe();
    }

    private static void assertHigh(Value x) {
        assert x == null;
    }

}
