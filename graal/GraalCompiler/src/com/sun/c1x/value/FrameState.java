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
import com.sun.cri.ri.*;

/**
 * The {@code FrameState} class encapsulates the frame state (i.e. local variables and
 * operand stack) at a particular point in the abstract interpretation.
 */
public final class FrameState extends Value implements FrameStateAccess {

    private static final int INPUT_COUNT = 1;

    private static final int INPUT_OUTER_FRAME_STATE = 0;

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

    public FrameState outerFrameState() {
        return (FrameState) inputs().get(super.inputCount() + INPUT_OUTER_FRAME_STATE);
    }

    public FrameState setOuterFrameState(FrameState n) {
        return (FrameState) inputs().set(super.inputCount() + INPUT_OUTER_FRAME_STATE, n);
    }

    @Override
    public void setValueAt(int i, Value x) {
        inputs().set(INPUT_COUNT + i, x);
    }

    /**
     * The bytecode index to which this frame state applies. This will be {@code -1}
     * iff this state is mutable.
     */
    public final int bci;

    public final RiMethod method;

    /**
     * Creates a {@code FrameState} for the given scope and maximum number of stack and local variables.
     *
     * @param bci the bytecode index of the frame state
     * @param localsSize number of locals
     * @param stackSize size of the stack
     * @param lockSize number of locks
     */
    public FrameState(RiMethod method, int bci, int localsSize, int stackSize, int locksSize, Graph graph) {
        super(CiKind.Illegal, localsSize + stackSize + locksSize + INPUT_COUNT, SUCCESSOR_COUNT, graph);
        this.method = method;
        this.bci = bci;
        this.localsSize = localsSize;
        this.stackSize = stackSize;
        this.locksSize = locksSize;
        C1XMetrics.FrameStatesCreated++;
        C1XMetrics.FrameStateValuesCreated += localsSize + stackSize + locksSize;
    }

    FrameState(RiMethod method, int bci, Value[] locals, Value[] stack, int stackSize, ArrayList<Value> locks, Graph graph) {
        this(method, bci, locals.length, stackSize, locks.size(), graph);
        for (int i = 0; i < locals.length; i++) {
            setValueAt(i, locals[i]);
        }
        for (int i = 0; i < stackSize; i++) {
            setValueAt(localsSize + i, stack[i]);
        }
        for (int i = 0; i < locks.size(); i++) {
            setValueAt(locals.length + stackSize + i, locks.get(i));
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
        FrameState other = new FrameState(method, bci, localsSize, 0, locksSize(), graph());
        for (int i = 0; i < localsSize; i++) {
            other.setValueAt(i, localAt(i));
        }
        for (int i = 0; i < locksSize; i++) {
            other.setValueAt(localsSize + i, lockAt(i));
        }
        other.setOuterFrameState(outerFrameState());
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
        FrameState other = new FrameState(method, bci, localsSize, stackSize - popSlots + pushSlots, locksSize(), graph());
        for (int i = 0; i < localsSize; i++) {
            other.setValueAt(i, localAt(i));
        }
        for (int i = 0; i < stackSize - popSlots; i++) {
            other.setValueAt(localsSize + i, stackAt(i));
        }
        int slot = localsSize + stackSize - popSlots;
        for (int i = 0; i < pushSlots; i++) {
            other.setValueAt(slot++, pushedValues[i]);
        }
        for (int i = 0; i < locksSize; i++) {
            other.setValueAt(localsSize + other.stackSize + i, lockAt(i));
        }
        other.setOuterFrameState(outerFrameState());
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
        if (other.outerFrameState() != outerFrameState()) {
            return false;
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
        setValueAt(i, null);
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
        setValueAt(i, x);
        if (isDoubleWord(x)) {
            // (tw) if this was a double word then kill i+1
            setValueAt(i + 1, null);
        }
        if (i > 0) {
            // if there was a double word at i - 1, then kill it
            Value p = localAt(i - 1);
            if (isDoubleWord(p)) {
                setValueAt(i - 1, null);
            }
        }
    }

    /**
     * Gets the value in the local variables at the specified index.
     *
     * @param i the index into the locals
     * @return the instruction that produced the value for the specified local
     */
    public Value localAt(int i) {
        assert i < localsSize : "local variable index out of range: " + i;
        return valueAt(i);
    }

    /**
     * Get the value on the stack at the specified stack index.
     *
     * @param i the index into the stack, with {@code 0} being the bottom of the stack
     * @return the instruction at the specified position in the stack
     */
    public Value stackAt(int i) {
        assert i >= 0 && i < (localsSize + stackSize);
        return valueAt(localsSize + i);
    }

    /**
     * Retrieves the lock at the specified index in the lock stack.
     * @param i the index into the lock stack
     * @return the instruction which produced the object at the specified location in the lock stack
     */
    public Value lockAt(int i) {
        assert i >= 0;
        return valueAt(localsSize + stackSize + i);
    }

    /**
     * Inserts a phi statement into the stack at the specified stack index.
     * @param block the block begin for which we are creating the phi
     * @param i the index into the stack for which to create a phi
     */
    public Phi setupPhiForStack(Merge block, int i) {
        Value p = stackAt(i);
        if (p != null) {
            if (p instanceof Phi) {
                Phi phi = (Phi) p;
                if (phi.block() == block) {
                    return phi;
                }
            }
            Phi phi = new Phi(p.kind, block, graph());
            setValueAt(localsSize + i, phi);
            return phi;
        }
        return null;
    }

    /**
     * Inserts a phi statement for the local at the specified index.
     * @param block the block begin for which we are creating the phi
     * @param i the index of the local variable for which to create the phi
     */
    public Phi setupPhiForLocal(Merge block, int i) {
        Value p = localAt(i);
        if (p instanceof Phi) {
            Phi phi = (Phi) p;
            if (phi.block() == block) {
                return phi;
            }
        }
        Phi phi = new Phi(p.kind, block, graph());
        storeLocal(i, phi);
        return phi;
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
    public Value valueAt(int i) {
        assert i < (localsSize + stackSize + locksSize);
        return (Value) inputs().get(INPUT_COUNT + i);
    }

    /**
     * The number of operand stack slots and local variables in this frame.
     * This method should typically only be used in conjunction with {@link #valueAt(int)}.
     * To iterate the stack slots, the {@link #stackAt(int)} and {@link #stackSize()} methods should be used.
     * To iterate the local variables, the {@link #localAt(int)} and {@link #localsSize()} methods should be used.
     *
     * @return the number of local variables in this frame
     */
    public int valuesSize() {
        return localsSize + stackSize;
    }

    private void checkSize(FrameStateAccess other) {
        if (other.stackSize() != stackSize()) {
            throw new CiBailout("stack sizes do not match");
        } else if (other.localsSize() != localsSize) {
            throw new CiBailout("local sizes do not match");
        }
    }

    public void merge(Merge block, FrameStateAccess other) {
        checkSize(other);
        for (int i = 0; i < valuesSize(); i++) {
            Value x = valueAt(i);
            if (x != null) {
                Value y = other.valueAt(i);
                if (x != y || ((x instanceof Phi) && ((Phi) x).block() == block)) {
                    if (typeMismatch(x, y)) {
                        if (x instanceof Phi) {
                            Phi phi = (Phi) x;
                            if (phi.block() == block) {
                                phi.makeDead();
                            }
                        }
                        setValueAt(i, null);
                        continue;
                    }
                    Phi phi = null;
                    if (i < localsSize) {
                        // this a local
                        phi = setupPhiForLocal(block, i);
                    } else {
                        // this is a stack slot
                        phi = setupPhiForStack(block, i - localsSize);
                    }

                    Phi originalPhi = phi;
                    if (phi.valueCount() == 0) {
                        int size = block.predecessors().size();
                        for (int j = 0; j < size; ++j) {
                            phi = phi.addInput(x);
                        }
                        phi = phi.addInput((x == y) ? phi : y);
                    } else {
                        phi = phi.addInput((x == y) ? phi : y);
                    }
                    if (originalPhi != phi) {
                        for (int j = 0; j < other.localsSize() + other.stackSize(); ++j) {
                            if (other.valueAt(j) == originalPhi) {
                                other.setValueAt(j, phi);
                            }
                        }
                    }

                    if (block instanceof LoopBegin) {
//                        assert phi.valueCount() == ((LoopBegin) block).loopEnd().predecessors().size() + 1 : "loop, valueCount=" + phi.valueCount() + " predSize= " + ((LoopBegin) block).loopEnd().predecessors().size();
                    } else {
                        assert phi.valueCount() == block.predecessors().size() + 1 : "valueCount=" + phi.valueCount() + " predSize= " + block.predecessors().size();
                    }
               }
            }
        }
    }

    public Merge block() {
        for (Node usage : usages()) {
            if (usage instanceof Merge) {
                return (Merge) usage;
            }
        }
        return null;
    }

    /**
     * The interface implemented by a client of {@link FrameState#forEachPhi(Merge, PhiProcedure)} and
     * {@link FrameState#forEachLivePhi(Merge, PhiProcedure)}.
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
    public void forEachLiveStateValue(ValueProcedure proc) {
        for (int i = 0; i < valuesSize(); i++) {
            Value value = valueAt(i);
            if (value != null) {
                proc.doValue(value);
            }
        }
        if (outerFrameState() != null) {
            outerFrameState().forEachLiveStateValue(proc);
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
    public void accept(ValueVisitor v) {
        v.visitFrameState(this);
    }

    @Override
    public void print(LogStream out) {
        out.print("FrameState");
    }

    @Override
    public FrameState copy() {
        return new FrameState(method, bci, localsSize, stackSize, locksSize, graph());
    }


    private FrameState copy(int newBci) {
        return new FrameState(method, newBci, localsSize, stackSize, locksSize, graph());
    }

    @Override
    public String shortName() {
        return "FrameState@" + bci;
    }

    public void visitFrameState(FrameState i) {
        // nothing to do for now
    }

    @Override
    public Node copy(Graph into) {
        FrameState x = new FrameState(method, bci, localsSize, stackSize, locksSize, into);
        return x;
    }
}
