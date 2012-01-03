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
package com.oracle.max.graal.nodes;

import java.util.*;

import com.oracle.max.cri.ci.*;
import com.oracle.max.cri.ri.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.PhiNode.PhiType;
import com.oracle.max.graal.nodes.spi.*;
import com.oracle.max.graal.nodes.virtual.*;

/**
 * The {@code FrameState} class encapsulates the frame state (i.e. local variables and
 * operand stack) at a particular point in the abstract interpretation.
 */
public final class FrameState extends Node implements FrameStateAccess, Node.IterableNodeType, LIRLowerable {

    protected final int localsSize;

    protected final int stackSize;

    protected final int locksSize;

    private boolean rethrowException;

    /**
     * This BCI should be used for frame states that are built for code with no meaningful BCI.
     */
    public static final int UNKNOWN_BCI = -4;

    /**
     * When a node whose frame state has this BCI value is inlined, its frame state
     * will be replaced with the frame state before the inlined invoke node.
     */
    public static final int BEFORE_BCI = -1;

    /**
     * When a node whose frame state has this BCI value is inlined, its frame state
     * will be replaced with the frame state {@linkplain Invoke#stateAfter() after}
     * the inlined invoke node.
     */
    public static final int AFTER_BCI = -2;

    /**
     * When a node whose frame state has this BCI value is inlined, its frame state
     * will be replaced with the frame state at the exception edge of the inlined
     * invoke node.
     */
    public static final int AFTER_EXCEPTION_BCI = -3;

    @Input private FrameState outerFrameState;

    @Input private final NodeInputList<ValueNode> values;

    @Input private final NodeInputList<Node> virtualObjectMappings;

    public FrameState outerFrameState() {
        return outerFrameState;
    }

    public void setOuterFrameState(FrameState x) {
        updateUsages(this.outerFrameState, x);
        this.outerFrameState = x;
    }

    public FrameState outermostFrameState() {
        FrameState fs = this;
        while (fs.outerFrameState() != null) {
            fs = fs.outerFrameState();
        }
        return fs;
    }

    public void setValueAt(int i, ValueNode x) {
        values.set(i, x);
    }

    /**
     * The bytecode index to which this frame state applies. This will be {@code -1}
     * iff this state is mutable.
     */
    public final int bci;

    private final RiResolvedMethod method;

    /**
     * Creates a {@code FrameState} for the given scope and maximum number of stack and local variables.
     *
     * @param method the method for this frame state
     * @param bci the bytecode index of the frame state
     * @param localsSize number of locals
     * @param stackSize size of the stack
     * @param lockSize number of locks
     * @param rethrowException if true the VM should re-throw the exception on top of the stack when deopt'ing using this framestate
     */
    public FrameState(RiResolvedMethod method, int bci, int localsSize, int stackSize, int locksSize, boolean rethrowException) {
        assert stackSize >= 0;
        this.method = method;
        this.bci = bci;
        this.localsSize = localsSize;
        this.stackSize = stackSize;
        this.locksSize = locksSize;
        this.values = new NodeInputList<>(this, localsSize + stackSize + locksSize);
        this.virtualObjectMappings = new NodeInputList<>(this);
        this.rethrowException = rethrowException;
        assert !rethrowException || stackSize == 1 : "must have exception on top of the stack";
    }

    public FrameState(RiResolvedMethod method, int bci, ValueNode[] locals, ValueNode[] stack, int stackSize, List<MonitorObject> locks, boolean rethrowException) {
        this.method = method;
        this.bci = bci;
        this.localsSize = locals.length;
        this.stackSize = stackSize;
        this.locksSize = locks.size();
        final ValueNode[] newValues = new ValueNode[locals.length + stackSize + locks.size()];
        for (int i = 0; i < locals.length; i++) {
            newValues[i] = locals[i];
        }
        for (int i = 0; i < stackSize; i++) {
            newValues[localsSize + i] = stack[i];
        }
        for (int i = 0; i < locks.size(); i++) {
            newValues[locals.length + stackSize + i] = locks.get(i);
        }
        this.values = new NodeInputList<>(this, newValues);
        this.virtualObjectMappings = new NodeInputList<>(this);
        this.rethrowException = rethrowException;
        assert !rethrowException || stackSize == 1 : "must have exception on top of the stack";
    }

    public boolean rethrowException() {
        return rethrowException;
    }

    public RiResolvedMethod method() {
        return method;
    }

    public void addVirtualObjectMapping(Node virtualObject) {
        assert virtualObject instanceof VirtualObjectFieldNode || virtualObject instanceof PhiNode : virtualObject;
        virtualObjectMappings.add(virtualObject);
    }

    public int virtualObjectMappingCount() {
        return virtualObjectMappings.size();
    }

    public Node virtualObjectMappingAt(int i) {
        return virtualObjectMappings.get(i);
    }

    public Iterable<Node> virtualObjectMappings() {
        return virtualObjectMappings;
    }

    /**
     * Gets a copy of this frame state.
     */
    public FrameState duplicate(int newBci) {
        return duplicate(newBci, false);
    }

    public FrameState duplicate(int newBci, boolean duplicateOuter) {
        FrameState other = graph().add(new FrameState(method, newBci, localsSize, stackSize, locksSize, rethrowException));
        other.values.setAll(values);
        other.virtualObjectMappings.setAll(virtualObjectMappings);
        FrameState newOuterFrameState = outerFrameState();
        if (duplicateOuter && newOuterFrameState != null) {
            newOuterFrameState = newOuterFrameState.duplicate(newOuterFrameState.bci, duplicateOuter);
        }
        other.setOuterFrameState(newOuterFrameState);
        return other;
    }

    @Override
    public FrameState duplicateWithException(int newBci, ValueNode exceptionObject) {
        return duplicateModified(newBci, true, CiKind.Void, exceptionObject);
    }

    /**
     * Creates a copy of this frame state with one stack element of type popKind popped from the stack and the
     * values in pushedValues pushed on the stack. The pushedValues are expected to be in slot encoding: a long
     * or double is followed by a null slot.
     */
    public FrameState duplicateModified(int newBci, boolean newRethrowException, CiKind popKind, ValueNode... pushedValues) {
        int popSlots = popKind == CiKind.Void ? 0 : isTwoSlot(popKind) ? 2 : 1;
        int pushSlots = pushedValues.length;
        FrameState other = graph().add(new FrameState(method, newBci, localsSize, stackSize - popSlots + pushSlots, locksSize(), newRethrowException));
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
        other.virtualObjectMappings.setAll(virtualObjectMappings);
        other.setOuterFrameState(outerFrameState());
        return other;
    }

    public boolean isCompatibleWith(FrameStateAccess other) {
        if (stackSize() != other.stackSize() || localsSize() != other.localsSize() || locksSize() != other.locksSize()) {
            return false;
        }
        for (int i = 0; i < stackSize(); i++) {
            ValueNode x = stackAt(i);
            ValueNode y = other.stackAt(i);
            if (x != y && ValueUtil.typeMismatch(x, y)) {
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

    public boolean equals(FrameStateAccess other) {
        if (stackSize() != other.stackSize() || localsSize() != other.localsSize() || locksSize() != other.locksSize()) {
            return false;
        }
        for (int i = 0; i < stackSize(); i++) {
            ValueNode x = stackAt(i);
            ValueNode y = other.stackAt(i);
            if (x != y) {
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
    // TODO this duplicates code in FrameStateBuilder and needs to go away
    public void storeLocal(int i, ValueNode x) {
        assert i < localsSize : "local variable index out of range: " + i;
        invalidateLocal(i);
        setValueAt(i, x);
        if (isTwoSlot(x.kind())) {
            // (tw) if this was a double word then kill i+1
            setValueAt(i + 1, null);
        }
        if (i > 0) {
            // if there was a double word at i - 1, then kill it
            ValueNode p = localAt(i - 1);
            if (p != null && isTwoSlot(p.kind())) {
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
    public ValueNode localAt(int i) {
        assert i < localsSize : "local variable index out of range: " + i;
        return valueAt(i);
    }

    /**
     * Get the value on the stack at the specified stack index.
     *
     * @param i the index into the stack, with {@code 0} being the bottom of the stack
     * @return the instruction at the specified position in the stack
     */
    public ValueNode stackAt(int i) {
        assert i >= 0 && i < (localsSize + stackSize);
        return valueAt(localsSize + i);
    }

    /**
     * Retrieves the lock at the specified index in the lock stack.
     * @param i the index into the lock stack
     * @return the instruction which produced the object at the specified location in the lock stack
     */
    public MonitorObject lockAt(int i) {
        assert i >= 0;
        return (MonitorObject) valueAt(localsSize + stackSize + i);
    }

    /**
     * Inserts a phi statement into the stack at the specified stack index.
     * @param block the block begin for which we are creating the phi
     * @param i the index into the stack for which to create a phi
     */
    public PhiNode setupPhiForStack(MergeNode block, int i) {
        ValueNode p = stackAt(i);
        if (p != null) {
            if (p instanceof PhiNode) {
                PhiNode phi = (PhiNode) p;
                if (phi.merge() == block) {
                    return phi;
                }
            }
            PhiNode phi = graph().unique(new PhiNode(p.kind(), block, PhiType.Value));
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
    public PhiNode setupPhiForLocal(MergeNode block, int i) {
        ValueNode p = localAt(i);
        if (p instanceof PhiNode) {
            PhiNode phi = (PhiNode) p;
            if (phi.merge() == block) {
                return phi;
            }
        }
        PhiNode phi = graph().unique(new PhiNode(p.kind(), block, PhiType.Value));
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
    public ValueNode valueAt(int i) {
        assert i < (localsSize + stackSize + locksSize);
        return values.isEmpty() ? null : values.get(i);
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

    private boolean checkSize(FrameStateAccess other) {
        assert other.stackSize() == stackSize() : "stack sizes do not match";
        assert other.localsSize() == localsSize : "local sizes do not match";
        return true;
    }

    public void merge(MergeNode block, FrameStateAccess other) {
        assert checkSize(other);
        for (int i = 0; i < valuesSize(); i++) {
            ValueNode currentValue = valueAt(i);
            ValueNode otherValue = other.valueAt(i);
            if (currentValue != otherValue) {
                if (block.isPhiAtMerge(currentValue)) {
                    addToPhi((PhiNode) currentValue, otherValue);
                } else {
                    setValueAt(i, combineValues(currentValue, otherValue, block));
                }
            }
        }
    }

    private ValueNode combineValues(ValueNode currentValue, ValueNode otherValue, MergeNode block) {
        if (currentValue == null || otherValue == null || currentValue.kind() != otherValue.kind()) {
            return null;
        }

        PhiNode phi = graph().unique(new PhiNode(currentValue.kind(), block, PhiType.Value));
        for (int j = 0; j < block.phiPredecessorCount(); ++j) {
            phi.addInput(currentValue);
        }
        phi.addInput(otherValue);
        assert phi.valueCount() == block.phiPredecessorCount() + 1 : "valueCount=" + phi.valueCount() + " predSize= " + block.phiPredecessorCount();
        return phi;
    }

    private static void addToPhi(PhiNode phiNode, ValueNode otherValue) {
        if (otherValue == null || otherValue.kind() != phiNode.kind()) {
            phiNode.replaceAtUsages(null);
            phiNode.safeDelete();
        } else {
            phiNode.addInput(otherValue);
        }
    }

    public void mergeLoop(LoopBeginNode block, FrameStateAccess other) {
        assert checkSize(other);
        for (int i = 0; i < valuesSize(); i++) {
            PhiNode currentValue = (PhiNode) valueAt(i);
            if (currentValue != null) {
                assert currentValue.merge() == block;
                assert currentValue.valueCount() == 1;
                ValueNode otherValue = other.valueAt(i);
                if (otherValue == currentValue) {
                    deleteRedundantPhi(currentValue, currentValue.firstValue());
                } else if (otherValue == null || otherValue.kind() != currentValue.kind()) {
                    deleteInvalidPhi(currentValue);
                } else {
                    currentValue.addInput(otherValue);
                }
            }
        }
    }

    public void deleteRedundantPhi(PhiNode redundantPhi, ValueNode phiValue) {
        Collection<PhiNode> phiUsages = redundantPhi.usages().filter(PhiNode.class).snapshot();
        redundantPhi.replaceAndDelete(phiValue);
        for (Node n : phiUsages) {
            PhiNode phiNode = (PhiNode) n;
            checkRedundantPhi(phiNode);
        }
    }

    private void checkRedundantPhi(PhiNode phiNode) {
        if (phiNode.isDeleted() || phiNode.valueCount() == 1) {
            return;
        }

        ValueNode singleValue = phiNode.singleValue();
        if (singleValue != null) {
            deleteRedundantPhi(phiNode, singleValue);
        }
    }

    private void deleteInvalidPhi(PhiNode phiNode) {
        if (!phiNode.isDeleted()) {
            Collection<PhiNode> phiUsages = phiNode.usages().filter(PhiNode.class).snapshot();
            phiNode.replaceAtUsages(null);
            phiNode.delete();
            for (Node n : phiUsages) {
                deleteInvalidPhi((PhiNode) n);
            }
        }
    }

    public MergeNode block() {
        return usages().filter(MergeNode.class).first();
    }

    public StateSplit stateSplit() {
        for (Node n : usages()) {
            if (n instanceof StateSplit) {
                return (StateSplit) n;
            }
        }
        return null;
    }

    public Iterable<FrameState> innerFrameStates() {
        final Iterator<Node> iterator = usages().iterator();
        return new Iterable<FrameState>() {
            @Override
            public Iterator<FrameState> iterator() {
                return new Iterator<FrameState>() {
                    private Node next;
                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                    @Override
                    public FrameState next() {
                        forward();
                        if (!hasNext()) {
                            throw new NoSuchElementException();
                        }
                        FrameState res = (FrameState) next;
                        next = null;
                        return res;
                    }
                    @Override
                    public boolean hasNext() {
                        forward();
                        return next != null;
                    }
                    private void forward() {
                        while (!(next instanceof FrameState) && iterator.hasNext()) {
                            next = iterator.next();
                        }
                    }
                };
            }
        };
    }

    /**
     * The interface implemented by a client of {@link FrameState#forEachPhi(MergeNode, PhiProcedure)} and
     * {@link FrameState#forEachLivePhi(MergeNode, PhiProcedure)}.
     */
    public interface PhiProcedure {
        boolean doPhi(PhiNode phi);
    }

    /**
     * Checks whether this frame state has any {@linkplain PhiNode phi} statements.
     */
    public boolean hasPhis() {
        for (int i = 0; i < valuesSize(); i++) {
            ValueNode value = valueAt(i);
            if (value instanceof PhiNode) {
                return true;
            }
        }
        return false;
    }

    public String toDetailedString() {
        StringBuilder sb = new StringBuilder();
        String nl = String.format("%n");
        sb.append("[bci: ").append(bci).append("]");
        if (rethrowException()) {
            sb.append(" rethrows Exception");
        }
        sb.append(nl);
        for (int i = 0; i < localsSize(); ++i) {
            ValueNode value = localAt(i);
            sb.append(String.format("  local[%d] = %-8s : %s%n", i, value == null ? "bogus" : value.kind().javaName, value));
        }
        for (int i = 0; i < stackSize(); ++i) {
            ValueNode value = stackAt(i);
            sb.append(String.format("  stack[%d] = %-8s : %s%n", i, value == null ? "bogus" : value.kind().javaName, value));
        }
        for (int i = 0; i < locksSize(); ++i) {
            ValueNode value = lockAt(i);
            sb.append(String.format("  lock[%d] = %-8s : %s%n", i, value == null ? "bogus" : value.kind().javaName, value));
        }
        return sb.toString();
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
        // Nothing to do, frame states are processed as part of the handling of AbstractStateSplit nodes.
    }

    public static String toString(FrameState frameState) {
        StringBuilder sb = new StringBuilder();
        String nl = CiUtil.NEW_LINE;
        FrameState fs = frameState;
        while (fs != null) {
            CiUtil.appendLocation(sb, fs.method, fs.bci).append(nl);
            for (int i = 0; i < fs.localsSize(); ++i) {
                ValueNode value = fs.localAt(i);
                sb.append(String.format("  local[%d] = %-8s : %s%n", i, value == null ? "bogus" : value.kind().javaName, value));
            }
            for (int i = 0; i < fs.stackSize(); ++i) {
                ValueNode value = fs.stackAt(i);
                sb.append(String.format("  stack[%d] = %-8s : %s%n", i, value == null ? "bogus" : value.kind().javaName, value));
            }
            for (int i = 0; i < fs.locksSize(); ++i) {
                ValueNode value = fs.lockAt(i);
                sb.append(String.format("  lock[%d] = %-8s : %s%n", i, value == null ? "bogus" : value.kind().javaName, value));
            }
            fs = fs.outerFrameState();
        }
        return sb.toString();
    }

    @Override
    public String toString(Verbosity verbosity) {
        if (verbosity == Verbosity.Debugger) {
            return toString(this);
        } else if (verbosity == Verbosity.Name) {
            return super.toString(Verbosity.Name) + "@" + bci;
        } else {
            return super.toString(verbosity);
        }
    }

    public void insertLoopPhis(LoopBeginNode loopBegin) {
        for (int i = 0; i < stackSize(); i++) {
            // always insert phis for the stack
            ValueNode x = stackAt(i);
            if (x != null) {
                setupPhiForStack(loopBegin, i).addInput(x);
            }
        }
        for (int i = 0; i < localsSize(); i++) {
            ValueNode x = localAt(i);
            if (x != null) {
                setupPhiForLocal(loopBegin, i).addInput(x);
            }
        }
    }

    @Override
    public Map<Object, Object> getDebugProperties() {
        Map<Object, Object> properties = super.getDebugProperties();
        properties.put("bci", bci);
        properties.put("method", CiUtil.format("%H.%n(%p):%r", method));
        StringBuilder str = new StringBuilder();
        for (int i = 0; i < localsSize(); i++) {
            str.append(i == 0 ? "" : ", ").append(localAt(i) == null ? "_" : localAt(i).toString(Verbosity.Id));
        }
        properties.put("locals", str.toString());
        str = new StringBuilder();
        for (int i = 0; i < stackSize(); i++) {
            str.append(i == 0 ? "" : ", ").append(stackAt(i) == null ? "_" : stackAt(i).toString(Verbosity.Id));
        }
        properties.put("stack", str.toString());
        str = new StringBuilder();
        for (int i = 0; i < locksSize(); i++) {
            str.append(i == 0 ? "" : ", ").append(lockAt(i) == null ? "_" : lockAt(i).toString(Verbosity.Id));
        }
        properties.put("locks", str.toString());
        properties.put("rethrowException", rethrowException);
        return properties;
    }

    public CiCodePos toCodePos() {
        FrameState caller = outerFrameState();
        CiCodePos callerCodePos = null;
        if (caller != null) {
            callerCodePos = caller.toCodePos();
        }
        return new CiCodePos(callerCodePos, method, bci);
    }

    @Override
    public boolean verify() {
        for (ValueNode value : values) {
            assert assertTrue(value == null || value instanceof VirtualObjectNode || (value.kind() != CiKind.Void && value.kind() != CiKind.Illegal), "unexpected value: %s", value);
        }
        return super.verify();
    }

    // TODO this duplicates code in FrameStateBuilder and needs to go away
    public static boolean isTwoSlot(CiKind kind) {
        assert kind != CiKind.Void && kind != CiKind.Illegal;
        return kind == CiKind.Long || kind == CiKind.Double;
    }
}
