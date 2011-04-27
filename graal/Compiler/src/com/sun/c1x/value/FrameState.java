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

import com.sun.c1x.*;
import com.sun.c1x.graph.*;
import com.sun.c1x.ir.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * The {@code FrameState} class encapsulates the frame state (i.e. local variables and
 * operand stack) at a particular point in the abstract interpretation.
 *
 * @author Ben L. Titzer
 */
public abstract class FrameState {

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
     * The depth of the operand stack.
     * The top of stack value is in {@code values[maxLocals + stackIndex]}.
     */
    protected int stackIndex;

    /**
     * The number of local variables.
     */
    protected final int maxLocals;

    protected final IRScope scope;

    /**
     * The bytecode index to which this frame state applies. This will be {@code -1}
     * iff this state is mutable.
     */
    public final int bci;

    /**
     * The list of locks held by this frame state.
     * This does not include locks held by parent frames.
     */
    protected ArrayList<Value> locks;

    /**
     * The number of minimum stack slots required for doing IR wrangling during
     * {@linkplain GraphBuilder bytecode parsing}. While this may hide stack
     * overflow issues in the original bytecode, the assumption is that such
     * issues must be caught by the verifier.
     */
    private static final int MINIMUM_STACK_SLOTS = 1;

    /**
     * Creates a {@code FrameState} for the given scope and maximum number of stack and local variables.
     *
     * @param irScope the inlining context of the method
     * @param bci the bytecode index of the frame state
     * @param maxLocals maximum number of locals
     * @param maxStack maximum size of the stack
     */
    public FrameState(IRScope irScope, int bci, int maxLocals, int maxStack) {
        this.scope = irScope;
        this.bci = bci;
        this.values = new Value[maxLocals + Math.max(maxStack, MINIMUM_STACK_SLOTS)];
        this.maxLocals = maxLocals;
        C1XMetrics.FrameStatesCreated++;
        C1XMetrics.FrameStateValuesCreated += this.values.length;
    }

    /**
     * Copies the contents of this frame state so that further updates to either stack aren't reflected in the other.
     * @param bci the bytecode index of the copy
     * @param withLocals indicates whether to copy the local state
     * @param withStack indicates whether to copy the stack state
     * @param withLocks indicates whether to copy the lock state
     *
     * @return a new frame state with the specified components
     */
    public MutableFrameState copy(int bci, boolean withLocals, boolean withStack, boolean withLocks) {
        final MutableFrameState other = new MutableFrameState(scope, bci, localsSize(), maxStackSize());
        if (withLocals && withStack) {
            // fast path: use array copy
            int valuesSize = valuesSize();
            assert other.values.length >= valuesSize : "array size: " + other.values.length + ", valuesSize: " + valuesSize + ", maxStackSize: " + maxStackSize() + ", localsSize: " + localsSize();
            assert values.length >= valuesSize : "array size: " + values.length + ", valuesSize: " + valuesSize + ", maxStackSize: " + maxStackSize() + ", localsSize: " + localsSize();
            System.arraycopy(values, 0, other.values, 0, valuesSize);
            other.stackIndex = stackIndex;
        } else {
            if (withLocals) {
                other.replaceLocals(this);
            }
            if (withStack) {
                other.replaceStack(this);
            }
        }
        if (withLocks) {
            other.replaceLocks(this);
        }
        return other;
    }

    /**
     * Gets a mutable copy ({@link MutableFrameState}) of this frame state.
     */
    public MutableFrameState copy() {
        return copy(bci, true, true, true);
    }

    /**
     * Gets an immutable copy of this frame state but without the stack.
     */
    public FrameState immutableCopyWithEmptyStack() {
        return copy(bci, true, false, true);
    }

    /**
     * Gets an immutable copy of this frame state but without the frame info.
     */
    public FrameState immutableCopyCodePosOnly() {
        return copy(bci, false, false, false);
    }

    public boolean isSameAcrossScopes(FrameState other) {
        assert stackSize() == other.stackSize();
        assert localsSize() == other.localsSize();
        assert locksSize() == other.locksSize();
        for (int i = 0; i < stackIndex; i++) {
            Value x = stackAt(i);
            Value y = other.stackAt(i);
            if (x != y && typeMismatch(x, y)) {
                return false;
            }
        }
        if (locks != null) {
            for (int i = 0; i < locks.size(); i++) {
                if (lockAt(i) != other.lockAt(i)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Returns the inlining context associated with this frame state.
     *
     * @return the inlining context
     */
    public IRScope scope() {
        return scope;
    }

    /**
     * Returns the size of the local variables.
     *
     * @return the size of the local variables
     */
    public int localsSize() {
        return maxLocals;
    }

    /**
     * Gets number of locks held by this frame state.
     */
    public int locksSize() {
        return locks == null ? 0 : locks.size();
    }

    public int totalLocksSize() {
        return locksSize() + ((callerState() == null) ? 0 : callerState().totalLocksSize());
    }

    /**
     * Gets the current size (height) of the stack.
     */
    public int stackSize() {
        return stackIndex;
    }

    /**
     * Gets the maximum size (height) of the stack.
]     */
    public int maxStackSize() {
        return values.length - maxLocals;
    }

    /**
     * Checks whether the stack is empty.
     *
     * @return {@code true} the stack is currently empty
     */
    public boolean stackEmpty() {
        return stackIndex == 0;
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
     * Loads the local variable at the specified index.
     *
     * @param i the index of the local variable to load
     * @return the instruction that produced the specified local
     */
    public Value loadLocal(int i) {
        assert i < maxLocals : "local variable index out of range: " + i;
        Value x = values[i];
        if (x != null) {
            if (x.isIllegal()) {
                return null;
            }
            assert x.kind.isSingleWord() || values[i + 1] == null || values[i + 1] instanceof Phi;
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
        assert i < maxLocals : "local variable index out of range: " + i;
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
     * Get the value on the stack at the specified stack index.
     *
     * @param i the index into the stack, with {@code 0} being the bottom of the stack
     * @return the instruction at the specified position in the stack
     */
    public final Value stackAt(int i) {
        assert i < stackIndex;
        return values[i + maxLocals];
    }

    /**
     * Gets the value in the local variables at the specified index.
     *
     * @param i the index into the locals
     * @return the instruction that produced the value for the specified local
     */
    public final Value localAt(int i) {
        assert i < maxLocals : "local variable index out of range: " + i;
        return values[i];
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
            values[maxLocals + i] = new Phi(p.kind, block, -i - 1);
        }
    }

    /**
     * Inserts a phi statement for the local at the specified index.
     * @param block the block begin for which we are creating the phi
     * @param i the index of the local variable for which to create the phi
     */
    public void setupPhiForLocal(BlockBegin block, int i) {
        Value p = values[i];
        if (p instanceof Phi) {
            Phi phi = (Phi) p;
            if (phi.block() == block && phi.isLocal() && phi.localIndex() == i) {
                return;
            }
        }
        storeLocal(i, new Phi(p.kind, block, i));
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
        return maxLocals + stackIndex;
    }

    public final int callerStackSize() {
        FrameState callerState = scope().callerState;
        return callerState == null ? 0 : callerState.stackSize();
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
                        for (int j = 0; j < phi.inputCount(); j++) {
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
        if (other.stackIndex != stackIndex) {
            throw new CiBailout("stack sizes do not match");
        } else if (other.maxLocals != maxLocals) {
            throw new CiBailout("local sizes do not match");
        }
    }

    public void merge(BlockBegin block, FrameState other) {
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
                    if (i < maxLocals) {
                        // this a local
                        setupPhiForLocal(block, i);
                    } else {
                        // this is a stack slot
                        setupPhiForStack(block, i - maxLocals);
                    }
                }
            }
        }
    }

    private static boolean typeMismatch(Value x, Value y) {
        return y == null || x.kind != y.kind;
    }

    private static boolean isDoubleWord(Value x) {
        return x != null && x.kind.isDoubleWord();
    }

    /**
     * The interface implemented by a client of {@link FrameState#forEachPhi(BlockBegin, PhiProcedure)} and
     * {@link FrameState#forEachLivePhi(BlockBegin, PhiProcedure)}.
     */
    public static interface PhiProcedure {
        boolean doPhi(Phi phi);
    }

    /**
     * Traverses all {@linkplain Phi phis} of a given block in this frame state.
     *
     * @param block only phis {@linkplain Phi#block() associated} with this block are traversed
     * @param proc the call back invoked for each live phi traversed
     */
    public boolean forEachPhi(BlockBegin block, PhiProcedure proc) {
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
     * Traverses all live {@linkplain Phi phis} of a given block in this frame state.
     *
     * @param block only phis {@linkplain Phi#block() associated} with this block are traversed
     * @param proc the call back invoked for each live phi traversed
     */
    public final boolean forEachLivePhi(BlockBegin block, PhiProcedure proc) {
        int max = this.valuesSize();
        for (int i = 0; i < max; i++) {
            Value instr = values[i];
            if (instr instanceof Phi && instr.isLive() && !instr.isDeadPhi()) {
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
        valuesDo(this, closure);
    }

    /**
     * Iterates over all the values of a given frame state and its callers, including the stack, locals, and locks.
     * @param closure the closure to apply to each value
     */
    public static void valuesDo(FrameState state, ValueClosure closure) {
        do {
            final int max = state.valuesSize();
            for (int i = 0; i < max; i++) {
                if (state.values[i] != null) {
                    Value newValue = closure.apply(state.values[i]);
                    state.values[i] = newValue;
                }
            }
            if (state.locks != null) {
                for (int i = 0; i < state.locks.size(); i++) {
                    Value instr = state.locks.get(i);
                    if (instr != null) {
                        state.locks.set(i, closure.apply(instr));
                    }
                }
            }
            state = state.callerState();
        } while (state != null);
    }

    /**
     * The interface implemented by a client of {@link FrameState#forEachLiveStateValue(ValueProcedure)}.
     */
    public static interface ValueProcedure {
        void doValue(Value value);
    }

    /**
     * Traverses all {@linkplain Value#isLive() live values} of this frame state and it's callers.
     *
     * @param proc the call back called to process each live value traversed
     */
    public final void forEachLiveStateValue(ValueProcedure proc) {
        FrameState state = this;
        while (state != null) {
            final int max = state.valuesSize();
            for (int i = 0; i < max; i++) {
                Value value = state.values[i];
                if (value != null && value.isLive()) {
                    proc.doValue(value);
                }
            }
            state = state.callerState();
        }
    }

    public static String toString(FrameState fs) {
        StringBuilder sb = new StringBuilder();
        String nl = String.format("%n");
        while (fs != null) {
            if (fs.scope == null) {
                sb.append("<no method>").append(" [bci: ").append(fs.bci).append(']');
                break;
            } else {
                CiUtil.appendLocation(sb, fs.scope.method, fs.bci).append(nl);
                for (int i = 0; i < fs.localsSize(); ++i) {
                    Value value = fs.localAt(i);
                    sb.append(String.format("  local[%d] = %-8s : %s%n", i, value == null ? "bogus" : value.kind.javaName, value));
                }
                for (int i = 0; i < fs.stackSize(); ++i) {
                    Value value = fs.stackAt(i);
                    sb.append(String.format("  stack[%d] = %-8s : %s%n", i, value == null ? "bogus" : value.kind.javaName, value));
                }
                for (int i = 0; i < fs.locksSize(); ++i) {
                    Value value = fs.lockAt(i);
                    sb.append(String.format("  lock[%d] = %-8s : %s%n", i, value == null ? "bogus" : value.kind.javaName, value));
                }
                fs = fs.callerState();
            }
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return toString(this);
    }

    public CiCodePos toCodePos() {
        FrameState caller = callerState();
        CiCodePos callerCodePos = null;
        if (caller != null) {
            callerCodePos = caller.toCodePos();
        }
        return new CiCodePos(callerCodePos, scope.method, bci);
    }

    /**
     * Creates a new {@code MutableFrameState} corresponding to inlining the specified method into this point in this frame state.
     * @param scope the IRScope representing the inlined method
     * @return a new frame state representing the state at the beginning of inlining the specified method into this one
     */
    public MutableFrameState pushScope(IRScope scope) {
        assert scope.caller == this.scope;
        RiMethod method = scope.method;
        return new MutableFrameState(scope, -1, method.maxLocals(), method.maxStackSize());
    }

    /**
     * Creates a new {@code MutableFrameState} corresponding to the state upon returning from this inlined method into the outer
     * IRScope.
     * @return a new frame state representing the state at exit from this frame state
     */
    public MutableFrameState popScope() {
        IRScope callingScope = scope.caller;
        assert callingScope != null;
        FrameState callerState = scope.callerState;
        MutableFrameState res = new MutableFrameState(callingScope, bci, callerState.maxLocals, callerState.maxStackSize() + stackIndex);
        System.arraycopy(callerState.values, 0, res.values, 0, callerState.values.length);
        System.arraycopy(values, maxLocals, res.values, res.maxLocals + callerState.stackIndex, stackIndex);
        res.stackIndex = callerState.stackIndex + stackIndex;
        assert res.stackIndex >= 0;
        res.replaceLocks(callerState);
        return res;
    }

    /**
     * Gets the caller frame state.
     *
     * @return the caller frame state or {@code null} if this is a top-level state
     */
    public FrameState callerState() {
        return scope.callerState;
    }
}
