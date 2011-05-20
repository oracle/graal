/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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

import static com.sun.c1x.value.ValueUtil.*;

import java.util.*;

import com.oracle.graal.graph.*;
import com.sun.c1x.*;
import com.sun.c1x.debug.*;
import com.sun.c1x.ir.*;
import com.sun.cri.ci.*;

/**
 * The {@code FrameState} class encapsulates the frame state (i.e. local variables and
 * operand stack) at a particular point in the abstract interpretation.
 */
public final class FrameState extends Value implements FrameStateAccess {

    protected final int localsSize;

    protected final int stackSize;

    protected final int locksSize;

    private static final int SUCCESSOR_COUNT = 0;

    @Override
    protected int inputCount() {
        return super.inputCount() + localsSize + stackSize + locksSize;
    }

    @Override
    protected int successorCount() {
        return super.successorCount() + SUCCESSOR_COUNT;
    }

    /**
     * The bytecode index to which this frame state applies. This will be {@code -1}
     * iff this state is mutable.
     */
    public final int bci;

    /**
     * Creates a {@code FrameState} for the given scope and maximum number of stack and local variables.
     *
     * @param bci the bytecode index of the frame state
     * @param localsSize number of locals
     * @param stackSize size of the stack
     * @param lockSize number of locks
     */
    public FrameState(int bci, int localsSize, int stackSize, int locksSize, Graph graph) {
        super(CiKind.Illegal, localsSize + stackSize + locksSize, SUCCESSOR_COUNT, graph);
        this.bci = bci;
        this.localsSize = localsSize;
        this.stackSize = stackSize;
        this.locksSize = locksSize;
        C1XMetrics.FrameStatesCreated++;
        C1XMetrics.FrameStateValuesCreated += localsSize + stackSize + locksSize;
    }

    FrameState(int bci, Value[] locals, Value[] stack, int stackSize, ArrayList<Value> locks, Graph graph) {
        this(bci, locals.length, stackSize, locks.size(), graph);
        for (int i = 0; i < locals.length; i++) {
            inputs().set(i, locals[i]);
        }
        for (int i = 0; i < stackSize; i++) {
            inputs().set(localsSize + i, stack[i]);
        }
        for (int i = 0; i < locks.size(); i++) {
            inputs().set(locals.length + stackSize + i, locks.get(i));
        }
    }

    /**
     * Gets a copy of this frame state.
     */
    public FrameState duplicate(int bci) {
        FrameState other = copy(bci);
        other.inputs().setAll(inputs());
        return other;
    }

    /**
     * Gets a copy of this frame state without the stack.
     */
    @Override
    public FrameState duplicateWithEmptyStack(int bci) {
        FrameState other = new FrameState(bci, localsSize, 0, locksSize(), graph());
        for (int i = 0; i < localsSize; i++) {
            other.inputs().set(i, localAt(i));
        }
        for (int i = 0; i < locksSize; i++) {
            other.inputs().set(localsSize + i, lockAt(i));
        }
        return other;
    }

    /**
     * Creates a copy of this frame state with one stack element of type popKind popped from the stack and the
     * values in pushedValues pushed on the stack. The pushedValues are expected to be in slot encoding: a long
     * or double is followed by a null slot.
     */
    public FrameState duplicateModified(int bci, CiKind popKind, Value... pushedValues) {
        int popSlots = popKind.sizeInSlots();
        int pushSlots = pushedValues.length;
        FrameState other = new FrameState(bci, localsSize, stackSize - popSlots + pushSlots, locksSize(), graph());
        for (int i = 0; i < localsSize; i++) {
            other.inputs().set(i, localAt(i));
        }
        for (int i = 0; i < stackSize - popSlots; i++) {
            other.inputs().set(localsSize + i, stackAt(i));
        }
        int slot = localsSize + stackSize - popSlots;
        for (int i = 0; i < pushSlots; i++) {
            other.inputs().set(slot++, pushedValues[i]);
        }
        for (int i = 0; i < locksSize; i++) {
            other.inputs().set(localsSize + other.stackSize + i, lockAt(i));
        }
        return other;
    }

    public boolean isCompatibleWith(FrameStateAccess other) {
        if (stackSize() != other.stackSize() || localsSize() != other.localsSize() || locksSize() != other.locksSize()) {
            return false;
        }
        for (int i = 0; i < stackSize(); i++) {
            Value x = stackAt(i);
            Value y = other.stackAt(i);
            if (x != y && typeMismatch(x, y)) {
                return false;
            }
        }
        for (int i = 0; i < locksSize(); i++) {
            if (lockAt(i) != other.lockAt(i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Gets the size of the local variables.
     */
    public int localsSize() {
        return localsSize;
    }

    /**
     * Gets the current size (height) of the stack.
     */
    public int stackSize() {
        return stackSize;
    }

    /**
     * Gets number of locks held by this frame state.
     */
    public int locksSize() {
        return locksSize;
    }

    /**
     * Invalidates the local variable at the specified index. If the specified index refers to a doubleword local, then
     * invalidates the high word as well.
     *
     * @param i the index of the local to invalidate
     */
    public void invalidateLocal(int i) {
        // note that for double word locals, the high slot should already be null
        // unless the local is actually dead and the high slot is being reused;
        // in either case, it is not necessary to null the high slot
        inputs().set(i, null);
    }

    /**
     * Stores a given local variable at the specified index. If the value is a {@linkplain CiKind#isDoubleWord() double word},
     * then the next local variable index is also overwritten.
     *
     * @param i the index at which to store
     * @param x the instruction which produces the value for the local
     */
    public void storeLocal(int i, Value x) {
        assert i < localsSize : "local variable index out of range: " + i;
        invalidateLocal(i);
        inputs().set(i, x);
        if (isDoubleWord(x)) {
            // (tw) if this was a double word then kill i+1
            inputs().set(i + 1, null);
        }
        if (i > 0) {
            // if there was a double word at i - 1, then kill it
            Value p = localAt(i - 1);
            if (isDoubleWord(p)) {
                inputs().set(i - 1, null);
            }
        }
    }

    /**
     * Gets the value in the local variables at the specified index.
     *
     * @param i the index into the locals
     * @return the instruction that produced the value for the specified local
     */
    public final Value localAt(int i) {
        assert i < localsSize : "local variable index out of range: " + i;
        return (Value) inputs().get(i);
    }

    /**
     * Get the value on the stack at the specified stack index.
     *
     * @param i the index into the stack, with {@code 0} being the bottom of the stack
     * @return the instruction at the specified position in the stack
     */
    public final Value stackAt(int i) {
        assert i >= 0 && i < (localsSize + stackSize);
        return (Value) inputs().get(localsSize + i);
    }

    /**
     * Retrieves the lock at the specified index in the lock stack.
     * @param i the index into the lock stack
     * @return the instruction which produced the object at the specified location in the lock stack
     */
    public final Value lockAt(int i) {
        assert i >= 0;
        return (Value) inputs().get(localsSize + stackSize + i);
    }

    /**
     * Inserts a phi statement into the stack at the specified stack index.
     * @param block the block begin for which we are creating the phi
     * @param i the index into the stack for which to create a phi
     */
    public void setupPhiForStack(BlockBegin block, int i) {
        Value p = stackAt(i);
        if (p != null) {
            if (p instanceof Phi) {
                Phi phi = (Phi) p;
                if (phi.block() == block && phi.isOnStack() && phi.stackIndex() == i) {
                    return;
                }
            }
            inputs().set(localsSize + i, new Phi(p.kind, block, -i - 1, graph()));
        }
    }

    /**
     * Inserts a phi statement for the local at the specified index.
     * @param block the block begin for which we are creating the phi
     * @param i the index of the local variable for which to create the phi
     */
    public void setupPhiForLocal(BlockBegin block, int i) {
        Value p = localAt(i);
        if (p instanceof Phi) {
            Phi phi = (Phi) p;
            if (phi.block() == block && phi.isLocal() && phi.localIndex() == i) {
                return;
            }
        }
        storeLocal(i, new Phi(p.kind, block, i, graph()));
    }

    /**
     * Gets the value at a specified index in the set of operand stack and local values represented by this frame.
     * This method should only be used to iterate over all the values in this frame, irrespective of whether
     * they are on the stack or in local variables.
     * To iterate the stack slots, the {@link #stackAt(int)} and {@link #stackSize()} methods should be used.
     * To iterate the local variables, the {@link #localAt(int)} and {@link #localsSize()} methods should be used.
     *
     * @param i a value in the range {@code [0 .. valuesSize()]}
     * @return the value at index {@code i} which may be {@code null}
     */
    public final Value valueAt(int i) {
        assert i < (localsSize + stackSize);
        return (Value) inputs().get(i);
    }

    /**
     * The number of operand stack slots and local variables in this frame.
     * This method should typically only be used in conjunction with {@link #valueAt(int)}.
     * To iterate the stack slots, the {@link #stackAt(int)} and {@link #stackSize()} methods should be used.
     * To iterate the local variables, the {@link #localAt(int)} and {@link #localsSize()} methods should be used.
     *
     * @return the number of local variables in this frame
     */
    public final int valuesSize() {
        return localsSize + stackSize;
    }

    public void checkPhis(BlockBegin block, FrameState other) {
        checkSize(other);
        for (int i = 0; i < valuesSize(); i++) {
            Value x = valueAt(i);
            Value y = other.valueAt(i);
            if (x != null && x != y) {
                if (x instanceof Phi) {
                    Phi phi = (Phi) x;
                    if (phi.block() == block) {
                        for (int j = 0; j < phi.phiInputCount(); j++) {
                            if (phi.inputIn(other) == null) {
                                throw new CiBailout("phi " + phi + " has null operand at new predecessor");
                            }
                        }
                        continue;
                    }
                }
                throw new CiBailout("instruction is not a phi or null at " + i);
            }
        }
    }

    private void checkSize(FrameStateAccess other) {
        if (other.stackSize() != stackSize()) {
            throw new CiBailout("stack sizes do not match");
        } else if (other.localsSize() != localsSize) {
            throw new CiBailout("local sizes do not match");
        }
    }

    public void merge(BlockBegin block, FrameStateAccess other) {
        checkSize(other);
        for (int i = 0; i < valuesSize(); i++) {
            Value x = valueAt(i);
            if (x != null) {
                Value y = other.valueAt(i);
                if (x != y) {
                    if (typeMismatch(x, y)) {
                        if (x instanceof Phi) {
                            Phi phi = (Phi) x;
                            if (phi.block() == block) {
                                phi.makeDead();
                            }
                        }
                        inputs().set(i, null);
                        continue;
                    }
                    if (i < localsSize) {
                        // this a local
                        setupPhiForLocal(block, i);
                    } else {
                        // this is a stack slot
                        setupPhiForStack(block, i - localsSize);
                    }
                }
            }
        }
    }


    /**
     * The interface implemented by a client of {@link FrameState#forEachPhi(BlockBegin, PhiProcedure)} and
     * {@link FrameState#forEachLivePhi(BlockBegin, PhiProcedure)}.
     */
    public static interface PhiProcedure {
        boolean doPhi(Phi phi);
    }

    /**
     * Checks whether this frame state has any {@linkplain Phi phi} statements.
     */
    public boolean hasPhis() {
        for (int i = 0; i < valuesSize(); i++) {
            Value value = valueAt(i);
            if (value instanceof Phi) {
                return true;
            }
        }
        return false;
    }

    /**
     * The interface implemented by a client of {@link FrameState#forEachLiveStateValue(ValueProcedure)}.
     */
    public static interface ValueProcedure {
        void doValue(Value value);
    }

    /**
     * Traverses all {@linkplain Value#isLive() live values} of this frame state.
     *
     * @param proc the call back called to process each live value traversed
     */
    public final void forEachLiveStateValue(ValueProcedure proc) {
        for (int i = 0; i < valuesSize(); i++) {
            Value value = valueAt(i);
            if (value != null) {
                proc.doValue(value);
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        String nl = String.format("%n");
        sb.append("[bci: ").append(bci).append("]").append(nl);
        for (int i = 0; i < localsSize(); ++i) {
            Value value = localAt(i);
            sb.append(String.format("  local[%d] = %-8s : %s%n", i, value == null ? "bogus" : value.kind.javaName, value));
        }
        for (int i = 0; i < stackSize(); ++i) {
            Value value = stackAt(i);
            sb.append(String.format("  stack[%d] = %-8s : %s%n", i, value == null ? "bogus" : value.kind.javaName, value));
        }
        for (int i = 0; i < locksSize(); ++i) {
            Value value = lockAt(i);
            sb.append(String.format("  lock[%d] = %-8s : %s%n", i, value == null ? "bogus" : value.kind.javaName, value));
        }
        return sb.toString();
    }

    @Override
    public BlockBegin block() {
        return null;
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitFrameState(this);
    }

    @Override
    public void print(LogStream out) {
        out.print("FrameState");
    }

    @Override
    public FrameState copy() {
        return new FrameState(bci, localsSize, stackSize, locksSize, graph());
    }


    private FrameState copy(int newBci) {
        return new FrameState(newBci, localsSize, stackSize, locksSize, graph());
    }

    @Override
    public String shortName() {
        return "FrameState@" + bci;
    }

    public void visitFrameState(FrameState i) {
        // nothing to do for now
    }
}
