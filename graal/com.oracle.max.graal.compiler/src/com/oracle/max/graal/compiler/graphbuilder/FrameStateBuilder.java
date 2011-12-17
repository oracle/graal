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
package com.oracle.max.graal.compiler.graphbuilder;

import static com.oracle.max.graal.nodes.ValueUtil.*;
import static java.lang.reflect.Modifier.*;

import java.util.*;

import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.PhiNode.PhiType;
import com.oracle.max.graal.nodes.spi.*;
import com.oracle.max.graal.nodes.type.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;


public class FrameStateBuilder implements FrameStateAccess {

    private final StructuredGraph graph;

    private final ValueNode[] locals;
    private final ValueNode[] stack;
    private final ArrayList<MonitorObject> locks;

    private int stackIndex;
    private boolean rethrowException;

    private final RiResolvedMethod method;

    public FrameStateBuilder(RiResolvedMethod method, int maxLocals, int maxStackSize, StructuredGraph graph) {
        assert graph != null;
        this.method = method;
        this.graph = graph;
        this.locals = new ValueNode[maxLocals];
        // we always need at least one stack slot (for exceptions)
        int stackSize = Math.max(1, maxStackSize);
        this.stack = new ValueNode[stackSize];

        int javaIndex = 0;
        int index = 0;
        if (!isStatic(method.accessFlags())) {
            // add the receiver
            LocalNode local = graph.unique(new LocalNode(javaIndex, StampFactory.declaredNonNull(method.holder())));
            storeLocal(javaIndex, local);
            javaIndex = 1;
            index = 1;
        }
        RiSignature sig = method.signature();
        int max = sig.argumentCount(false);
        RiType accessingClass = method.holder();
        for (int i = 0; i < max; i++) {
            RiType type = sig.argumentTypeAt(i, accessingClass);
            CiKind kind = type.kind(false).stackKind();
            Stamp stamp;
            if (kind == CiKind.Object && type instanceof RiResolvedType) {
                RiResolvedType resolvedType = (RiResolvedType) type;
                stamp = StampFactory.declared(resolvedType);
            } else {
                stamp = StampFactory.forKind(kind);
            }
            LocalNode local = graph.unique(new LocalNode(index, stamp));
            storeLocal(javaIndex, local);
            javaIndex += stackSlots(kind);
            index++;
        }
        this.locks = new ArrayList<MonitorObject>();
    }

    @Override
    public String toString() {
        return String.format("FrameStateBuilder[stackSize=%d]", stackIndex);
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
        this.rethrowException = other.rethrowException();
    }

    public FrameState create(int bci) {
        return graph.add(new FrameState(method, bci, locals, stack, stackIndex, locks, rethrowException));
    }

    public FrameState duplicateWithException(int bci, ValueNode exceptionObject) {
        FrameState frameState = graph.add(new FrameState(method, bci, locals, new ValueNode[]{exceptionObject}, 1, locks, true));
        frameState.setOuterFrameState(outerFrameState());
        return frameState;
    }

    /**
     * Pushes an instruction onto the stack with the expected type.
     * @param kind the type expected for this instruction
     * @param x the instruction to push onto the stack
     */
    public void push(CiKind kind, ValueNode x) {
        assert kind != CiKind.Void;
        xpush(assertKind(kind, x));
        if (isTwoSlot(kind)) {
            xpush(null);
        }
    }

    /**
     * Pushes a value onto the stack without checking the type.
     * @param x the instruction to push onto the stack
     */
    public void xpush(ValueNode x) {
        assert x == null || !x.isDeleted();
        assert x == null || (x.kind() != CiKind.Void && x.kind() != CiKind.Illegal) : "unexpected value: " + x;
        stack[stackIndex++] = x;
    }

    /**
     * Pushes a value onto the stack and checks that it is an int.
     * @param x the instruction to push onto the stack
     */
    public void ipush(ValueNode x) {
        xpush(assertInt(x));
    }

    /**
     * Pushes a value onto the stack and checks that it is a float.
     * @param x the instruction to push onto the stack
     */
    public void fpush(ValueNode x) {
        xpush(assertFloat(x));
    }

    /**
     * Pushes a value onto the stack and checks that it is an object.
     * @param x the instruction to push onto the stack
     */
    public void apush(ValueNode x) {
        xpush(assertObject(x));
    }

    /**
     * Pushes a value onto the stack and checks that it is a JSR return address.
     * @param x the instruction to push onto the stack
     */
    public void jpush(ValueNode x) {
        xpush(assertJsr(x));
    }

    /**
     * Pushes a value onto the stack and checks that it is a long.
     *
     * @param x the instruction to push onto the stack
     */
    public void lpush(ValueNode x) {
        xpush(assertLong(x));
        xpush(null);
    }

    /**
     * Pushes a value onto the stack and checks that it is a double.
     * @param x the instruction to push onto the stack
     */
    public void dpush(ValueNode x) {
        xpush(assertDouble(x));
        xpush(null);
    }

    public void pushReturn(CiKind kind, ValueNode x) {
        if (kind != CiKind.Void) {
            push(kind.stackKind(), x);
        }
    }

    /**
     * Pops an instruction off the stack with the expected type.
     * @param kind the expected type
     * @return the instruction on the top of the stack
     */
    public ValueNode pop(CiKind kind) {
        assert kind != CiKind.Void;
        if (isTwoSlot(kind)) {
            xpop();
        }
        return assertKind(kind, xpop());
    }

    /**
     * Pops a value off of the stack without checking the type.
     * @return x the instruction popped off the stack
     */
    public ValueNode xpop() {
        ValueNode result = stack[--stackIndex];
        assert result == null || !result.isDeleted();
        return result;
    }

    /**
     * Pops a value off of the stack and checks that it is an int.
     * @return x the instruction popped off the stack
     */
    public ValueNode ipop() {
        return assertInt(xpop());
    }

    /**
     * Pops a value off of the stack and checks that it is a float.
     * @return x the instruction popped off the stack
     */
    public ValueNode fpop() {
        return assertFloat(xpop());
    }

    /**
     * Pops a value off of the stack and checks that it is an object.
     * @return x the instruction popped off the stack
     */
    public ValueNode apop() {
        return assertObject(xpop());
    }

    /**
     * Pops a value off of the stack and checks that it is a JSR return address.
     * @return x the instruction popped off the stack
     */
    public ValueNode jpop() {
        return assertJsr(xpop());
    }

    /**
     * Pops a value off of the stack and checks that it is a long.
     * @return x the instruction popped off the stack
     */
    public ValueNode lpop() {
        assertHigh(xpop());
        return assertLong(xpop());
    }

    /**
     * Pops a value off of the stack and checks that it is a double.
     * @return x the instruction popped off the stack
     */
    public ValueNode dpop() {
        assertHigh(xpop());
        return assertDouble(xpop());
    }

    /**
     * Pop the specified number of slots off of this stack and return them as an array of instructions.
     * @param size the number of arguments off of the stack
     * @return an array containing the arguments off of the stack
     */
    public ValueNode[] popArguments(int slotSize, int argSize) {
        int base = stackIndex - slotSize;
        ValueNode[] r = new ValueNode[argSize];
        int argIndex = 0;
        int stackindex = 0;
        while (stackindex < slotSize) {
            ValueNode element = stack[base + stackindex];
            assert element != null;
            r[argIndex++] = element;
            stackindex += stackSlots(element.kind());
        }
        stackIndex = base;
        return r;
    }

    /**
     * Peeks an element from the operand stack.
     * @param argumentNumber The number of the argument, relative from the top of the stack (0 = top).
     *        Long and double arguments only count as one argument, i.e., null-slots are ignored.
     * @return The peeked argument.
     */
    public ValueNode peek(int argumentNumber) {
        int idx = stackSize() - 1;
        for (int i = 0; i < argumentNumber; i++) {
            if (stackAt(idx) == null) {
                idx--;
                assert isTwoSlot(stackAt(idx).kind());
            }
            idx--;
        }
        return stackAt(idx);
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
    public ValueNode loadLocal(int i) {
        ValueNode x = locals[i];
        if (x != null) {
            if (x instanceof PhiNode) {
                assert ((PhiNode) x).type() == PhiType.Value;
                if (x.isDeleted()) {
                    return null;
                }
            }
            assert !isTwoSlot(x.kind()) || locals[i + 1] == null || locals[i + 1] instanceof PhiNode;
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
    public void storeLocal(int i, ValueNode x) {
        assert x == null || (x.kind() != CiKind.Void && x.kind() != CiKind.Illegal) : "unexpected value: " + x;
        locals[i] = x;
        if (isTwoSlot(x.kind())) {
            // (tw) if this was a double word then kill i+1
            locals[i + 1] = null;
        }
        if (i > 0) {
            // if there was a double word at i - 1, then kill it
            ValueNode p = locals[i - 1];
            if (p != null && isTwoSlot(p.kind())) {
                locals[i - 1] = null;
            }
        }
    }

    /**
     * Locks a new object within the specified IRScope.
     * @param scope the IRScope in which this locking operation occurs
     * @param obj the object being locked
     */
    public void lock(MonitorObject obj) {
        assert obj == null || (obj.kind() != CiKind.Void && obj.kind() != CiKind.Illegal) : "unexpected value: " + obj;
        locks.add(obj);
    }

    /**
     * Unlock the lock on the top of the stack.
     */
    public void unlock(MonitorObject obj) {
        assert locks.get(locks.size() - 1) == obj;
        locks.remove(locks.size() - 1);
    }

    /**
     * Get the value on the stack at the specified stack index.
     *
     * @param i the index into the stack, with {@code 0} being the bottom of the stack
     * @return the instruction at the specified position in the stack
     */
    public final ValueNode stackAt(int i) {
        return stack[i];
    }

    /**
     * Gets the value in the local variables at the specified index.
     *
     * @param i the index into the locals
     * @return the instruction that produced the value for the specified local
     */
    public final ValueNode localAt(int i) {
        return locals[i];
    }

    /**
     * Retrieves the lock at the specified index in the lock stack.
     * @param i the index into the lock stack
     * @return the instruction which produced the object at the specified location in the lock stack
     */
    public final MonitorObject lockAt(int i) {
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

    public Iterator<ValueNode> locals() {
        return new ValueArrayIterator(locals);
    }

    public Iterator<ValueNode> stack() {
        return new ValueArrayIterator(locals);
    }

    public List<MonitorObject> locks() {
        return Collections.unmodifiableList(locks);
    }


    private static class ValueArrayIterator implements Iterator<ValueNode> {
        private final ValueNode[] array;
        private int index;

        public ValueArrayIterator(ValueNode[] array, int length) {
            assert length <= array.length;
            this.array = array;
            this.index = 0;
        }

        public ValueArrayIterator(ValueNode[] array) {
            this(array, array.length);
        }

        @Override
        public boolean hasNext() {
            return index < array.length;
        }

        @Override
        public ValueNode next() {
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
    public ValueNode valueAt(int i) {
        if (i < locals.length) {
            return locals[i];
        } else if (i < locals.length + stackIndex) {
            return stack[i - locals.length];
        } else {
            return locks.get(i - locals.length - stack.length);
        }
    }

    @Override
    public FrameState outerFrameState() {
        return null;
    }

    public FrameState duplicateWithoutStack(int bci) {
        FrameState frameState = graph.add(new FrameState(method, bci, locals, new ValueNode[0], 0, locks, false));
        frameState.setOuterFrameState(outerFrameState());
        return frameState;
    }

    @Override
    public boolean rethrowException() {
        return rethrowException;
    }

    @Override
    public void setRethrowException(boolean b) {
        rethrowException = b;
    }

    public static int stackSlots(CiKind kind) {
        return isTwoSlot(kind) ? 2 : 1;
    }

    public static boolean isTwoSlot(CiKind kind) {
        assert kind != CiKind.Void && kind != CiKind.Illegal;
        return kind == CiKind.Long || kind == CiKind.Double;
    }
}
