/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.compiler.value;

import static com.oracle.max.graal.compiler.value.ValueUtil.*;
import static java.lang.reflect.Modifier.*;

import java.util.*;

import com.oracle.max.graal.compiler.ir.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;


public class FrameStateBuilder implements FrameStateAccess {

    private final Graph graph;

    private final Value[] locals;
    private final Value[] stack;
    private final ArrayList<Value> locks;

    private int stackIndex;

    private final RiMethod method;

    public FrameStateBuilder(RiMethod method, Graph graph) {
        assert graph != null;
        this.method = method;
        this.graph = graph;
        this.locals = new Value[method.maxLocals()];
        // we always need at least one stack slot (for exceptions)
        int stackSize = Math.max(1, method.maxStackSize());
        this.stack = new Value[stackSize];

        int javaIndex = 0;
        int index = 0;
        if (!isStatic(method.accessFlags())) {
            // add the receiver and assume it is non null
            Local local = new Local(method.holder().kind(), javaIndex, graph);
            local.inputs().set(0, graph.start());
            local.setDeclaredType(method.holder());
            storeLocal(javaIndex, local);
            javaIndex = 1;
            index = 1;
        }
        RiSignature sig = method.signature();
        int max = sig.argumentCount(false);
        RiType accessingClass = method.holder();
        for (int i = 0; i < max; i++) {
            RiType type = sig.argumentTypeAt(i, accessingClass);
            CiKind kind = type.kind().stackKind();
            Local local = new Local(kind, index, graph);
            local.inputs().set(0, graph.start());
            if (type.isResolved()) {
                local.setDeclaredType(type);
            }
            storeLocal(javaIndex, local);
            javaIndex += kind.sizeInSlots();
            index++;
        }
        this.locks = new ArrayList<Value>();
    }

    public void initializeFrom(FrameState other) {
        assert locals.length == other.localsSize() : "expected: " + locals.length + ", actual: " + other.localsSize();
        assert stack.length >= other.stackSize() : "expected: <=" + stack.length + ", actual: " + other.stackSize();

        this.stackIndex = other.stackSize();
        for (int i = 0; i < other.localsSize(); i++) {
            locals[i] = other.localAt(i);
        }
        for (int i = 0; i < other.stackSize(); i++) {
            stack[i] = other.stackAt(i);
        }
        locks.clear();
        for (int i = 0; i < other.locksSize(); i++) {
            locks.add(other.lockAt(i));
        }
    }

    public FrameState create(int bci) {
        return new FrameState(method, bci, locals, stack, stackIndex, locks, graph);
    }

    @Override
    public FrameState duplicateWithEmptyStack(int bci) {
        FrameState frameState = new FrameState(method, bci, locals, new Value[0], 0, locks, graph);
        frameState.setOuterFrameState(outerFrameState());
        return frameState;
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
        assert x == null || !x.isDeleted();
        stack[stackIndex++] = x;
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

    public void pushReturn(CiKind kind, Value x) {
        if (kind != CiKind.Void) {
            push(kind.stackKind(), x);
        }
    }

    /**
     * Pops an instruction off the stack with the expected type.
     * @param kind the expected type
     * @return the instruction on the top of the stack
     */
    public Value pop(CiKind kind) {
        assert kind != CiKind.Void;
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
        Value result = stack[--stackIndex];
        assert result == null || !result.isDeleted();
        return result;
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

    /**
     * Pop the specified number of slots off of this stack and return them as an array of instructions.
     * @param size the number of arguments off of the stack
     * @return an array containing the arguments off of the stack
     */
    public Value[] popArguments(int size) {
        int base = stackIndex - size;
        Value[] r = new Value[size];
        for (int i = 0; i < size; ++i) {
            assert stack[base + i] != null || stack[base + i - 1].kind.jvmSlots == 2;
            r[i] = stack[base + i];
        }
        stackIndex = base;
        return r;
    }

    public CiKind peekKind() {
        Value top = stackAt(stackSize() - 1);
        if (top == null) {
            top = stackAt(stackSize() - 2);
            assert top != null;
            assert top.kind.isDoubleWord();
        }
        return top.kind;
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
     * Clears all values on this stack.
     */
    public void clearStack() {
        stackIndex = 0;
    }

    /**
     * Loads the local variable at the specified index.
     *
     * @param i the index of the local variable to load
     * @return the instruction that produced the specified local
     */
    public Value loadLocal(int i) {
        Value x = locals[i];
        if (x != null) {
            if (x instanceof Phi && ((Phi) x).isDead()) {
                return null;
            }
            assert x.kind.isSingleWord() || locals[i + 1] == null || locals[i + 1] instanceof Phi;
        }
        return x;
    }

    /**
     * Stores a given local variable at the specified index. If the value is a {@linkplain CiKind#isDoubleWord() double word},
     * then the next local variable index is also overwritten.
     *
     * @param i the index at which to store
     * @param x the instruction which produces the value for the local
     */
    public void storeLocal(int i, Value x) {
        locals[i] = x;
        if (isDoubleWord(x)) {
            // (tw) if this was a double word then kill i+1
            locals[i + 1] = null;
        }
        if (i > 0) {
            // if there was a double word at i - 1, then kill it
            Value p = locals[i - 1];
            if (isDoubleWord(p)) {
                locals[i - 1] = null;
            }
        }
    }

    /**
     * Locks a new object within the specified IRScope.
     * @param scope the IRScope in which this locking operation occurs
     * @param obj the object being locked
     */
    public void lock(Value obj) {
        locks.add(obj);
    }

    /**
     * Unlock the lock on the top of the stack.
     */
    public void unlock() {
        locks.remove(locks.size() - 1);
    }

    /**
     * Get the value on the stack at the specified stack index.
     *
     * @param i the index into the stack, with {@code 0} being the bottom of the stack
     * @return the instruction at the specified position in the stack
     */
    public final Value stackAt(int i) {
        return stack[i];
    }

    /**
     * Gets the value in the local variables at the specified index.
     *
     * @param i the index into the locals
     * @return the instruction that produced the value for the specified local
     */
    public final Value localAt(int i) {
        return locals[i];
    }

    /**
     * Retrieves the lock at the specified index in the lock stack.
     * @param i the index into the lock stack
     * @return the instruction which produced the object at the specified location in the lock stack
     */
    public final Value lockAt(int i) {
        return locks.get(i);
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
     * Gets number of locks held by this frame state.
     */
    public int locksSize() {
        return locks.size();
    }

    /**
     * Gets the current size (height) of the stack.
     */
    public int stackSize() {
        return stackIndex;
    }

    public Iterator<Value> locals() {
        return new ValueArrayIterator(locals);
    }

    public Iterator<Value> stack() {
        return new ValueArrayIterator(locals);
    }

    public List<Value> locks() {
        return Collections.unmodifiableList(locks);
    }


    private static class ValueArrayIterator implements Iterator<Value> {
        private final Value[] array;
        private int index;
        private int length;

        public ValueArrayIterator(Value[] array, int length) {
            assert length <= array.length;
            this.array = array;
            this.index = 0;
        }

        public ValueArrayIterator(Value[] array) {
            this(array, array.length);
        }

        @Override
        public boolean hasNext() {
            return index < array.length;
        }

        @Override
        public Value next() {
            return array[index++];
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("cannot remove from array");
        }

    }


    @Override
    public FrameState duplicate(int bci) {
        return create(bci);
    }

    @Override
    public Value valueAt(int i) {
        if (i < locals.length) {
            return locals[i];
        } else if (i < locals.length + stackIndex) {
            return stack[i - locals.length];
        } else {
            return locks.get(i - locals.length - stack.length);
        }
    }

    @Override
    public void setValueAt(int i, Value v) {
        if (i < locals.length) {
            locals[i] = v;
        } else if (i < locals.length + stackIndex) {
            stack[i - locals.length] = v;
        } else {
            locks.set(i - locals.length - stack.length, v);
        }
    }

    @Override
    public FrameState outerFrameState() {
        return null;
    }
}
