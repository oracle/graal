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

import java.util.*;

import com.oracle.graal.graph.*;
import com.sun.c1x.*;
import com.sun.c1x.ir.*;
import com.sun.cri.ci.*;

import static com.sun.c1x.value.ValueUtil.*;

/**
 * The {@code FrameState} class encapsulates the frame state (i.e. local variables and
 * operand stack) at a particular point in the abstract interpretation.
 */
public class FrameState {

    /**
     * The operand stack and local variables.
     * The local variables occupy the index range {@code [0 .. maxLocals)}.
     * The operand stack occupies the index range {@code [maxLocals .. values.length)}.
     * The top of the operand stack is at index {@code maxLocals + stackIndex}.
     * This does not include the operand stack or local variables of parent frames.
     *
     * {@linkplain CiKind#isDoubleWord() Double-word} local variables and
     * operand stack values occupy 2 slots in this array with the second slot
     * being {@code null}.
     */
    protected final Value[] values;

    /**
     * The number of local variables.
     */
    protected final int localsSize;

    protected final int stackSize;

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
    public FrameState(int bci, int localsSize, int stackSize, int lockSize) {
        this.bci = bci;
        this.values = new Value[localsSize + stackSize + lockSize];
        this.localsSize = localsSize;
        this.stackSize = stackSize;
        C1XMetrics.FrameStatesCreated++;
        C1XMetrics.FrameStateValuesCreated += this.values.length;
    }

    FrameState(int bci, Value[] locals, Value[] stack, int stackSize, ArrayList<Value> locks) {
        this(bci, locals.length, stackSize, locks.size());
        System.arraycopy(locals, 0, values, 0, locals.length);
        System.arraycopy(stack, 0, values, locals.length, stackSize);
        for (int i = 0; i < locks.size(); i++) {
            values[locals.length + stackSize + i] = locks.get(i);
        }
    }

    /**
     * Gets a immutable copy ({@link FrameState}) of this frame state.
     */
    public FrameState copy() {
        FrameState other = new FrameState(bci, localsSize, stackSize, locksSize());
        System.arraycopy(values, 0, other.values, 0, values.length);
        return other;
    }

    /**
     * Gets an immutable copy of this frame state but without the stack.
     */
    public FrameState copyWithEmptyStack() {
        FrameState other = new FrameState(bci, localsSize, 0, locksSize());
        System.arraycopy(values, 0, other.values, 0, localsSize);
        System.arraycopy(values, localsSize + stackSize, other.values, localsSize, locksSize());
        return other;
    }

    public boolean isCompatibleWith(FrameState other) {
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
     * Returns the size of the local variables.
     *
     * @return the size of the local variables
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
        return values.length - localsSize - stackSize;
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
        values[i] = null;
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
        values[i] = x;
        if (isDoubleWord(x)) {
            // (tw) if this was a double word then kill i+1
            values[i + 1] = null;
        }
        if (i > 0) {
            // if there was a double word at i - 1, then kill it
            Value p = values[i - 1];
            if (isDoubleWord(p)) {
                values[i - 1] = null;
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
        return values[i];
    }

    /**
     * Get the value on the stack at the specified stack index.
     *
     * @param i the index into the stack, with {@code 0} being the bottom of the stack
     * @return the instruction at the specified position in the stack
     */
    public final Value stackAt(int i) {
        assert i >= 0 && i < (localsSize + stackSize);
        return values[localsSize + i];
    }

    /**
     * Retrieves the lock at the specified index in the lock stack.
     * @param i the index into the lock stack
     * @return the instruction which produced the object at the specified location in the lock stack
     */
    public final Value lockAt(int i) {
        assert i >= 0;
        return values[localsSize + stackSize + i];
    }

    /**
     * Inserts a phi statement into the stack at the specified stack index.
     * @param block the block begin for which we are creating the phi
     * @param i the index into the stack for which to create a phi
     * @param graph
     */
    public void setupPhiForStack(BlockBegin block, int i, Graph graph) {
        Value p = stackAt(i);
        if (p != null) {
            if (p instanceof Phi) {
                Phi phi = (Phi) p;
                if (phi.block() == block && phi.isOnStack() && phi.stackIndex() == i) {
                    return;
                }
            }
            values[localsSize + i] = new Phi(p.kind, block, -i - 1, graph);
        }
    }

    /**
     * Inserts a phi statement for the local at the specified index.
     * @param block the block begin for which we are creating the phi
     * @param i the index of the local variable for which to create the phi
     * @param graph
     */
    public void setupPhiForLocal(BlockBegin block, int i, Graph graph) {
        Value p = values[i];
        if (p instanceof Phi) {
            Phi phi = (Phi) p;
            if (phi.block() == block && phi.isLocal() && phi.localIndex() == i) {
                return;
            }
        }
        storeLocal(i, new Phi(p.kind, block, i, graph));
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
        return values[i];
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
        final int max = valuesSize();
        for (int i = 0; i < max; i++) {
            Value x = values[i];
            Value y = other.values[i];
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

    private void checkSize(FrameState other) {
        if (other.stackSize() != stackSize()) {
            throw new CiBailout("stack sizes do not match");
        } else if (other.localsSize != localsSize) {
            throw new CiBailout("local sizes do not match");
        }
    }

    public void merge(BlockBegin block, FrameState other, Graph graph) {
        checkSize(other);
        for (int i = 0; i < valuesSize(); i++) {
            Value x = values[i];
            if (x != null) {
                Value y = other.values[i];
                if (x != y) {
                    if (typeMismatch(x, y)) {
                        if (x instanceof Phi) {
                            Phi phi = (Phi) x;
                            if (phi.block() == block) {
                                phi.makeDead();
                            }
                        }
                        values[i] = null;
                        continue;
                    }
                    if (i < localsSize) {
                        // this a local
                        setupPhiForLocal(block, i, graph);
                    } else {
                        // this is a stack slot
                        setupPhiForStack(block, i - localsSize, graph);
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
     * Traverses all live {@linkplain Phi phis} of a given block in this frame state.
     *
     * @param block only phis {@linkplain Phi#block() associated} with this block are traversed
     * @param proc the call back invoked for each live phi traversed
     */
    public final boolean forEachLivePhi(BlockBegin block, PhiProcedure proc) {
        int max = this.valuesSize();
        for (int i = 0; i < max; i++) {
            Value instr = values[i];
            if (instr instanceof Phi && !instr.isDeadPhi()) {
                Phi phi = (Phi) instr;
                if (block == null || phi.block() == block) {
                    if (!proc.doPhi(phi)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Checks whether this frame state has any {@linkplain Phi phi} statements.
     */
    public boolean hasPhis() {
        int max = valuesSize();
        for (int i = 0; i < max; i++) {
            Value value = values[i];
            if (value instanceof Phi) {
                return true;
            }
        }
        return false;
    }

    /**
     * Iterates over all the values in this frame state and its callers, including the stack, locals, and locks.
     * @param closure the closure to apply to each value
     */
    public void valuesDo(ValueClosure closure) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] != null) {
                Value newValue = closure.apply(values[i]);
                values[i] = newValue;
            }
        }
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
        final int max = this.valuesSize();
        for (int i = 0; i < max; i++) {
            Value value = this.values[i];
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
}
