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

import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.PhiNode.PhiType;
import com.oracle.max.graal.nodes.calc.*;
import com.oracle.max.graal.nodes.spi.*;
import com.oracle.max.graal.nodes.virtual.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * The {@code FrameState} class encapsulates the frame state (i.e. local variables and
 * operand stack) at a particular point in the abstract interpretation.
 */
public final class FrameState extends ValueNode implements FrameStateAccess {

    protected final int localsSize;

    protected final int stackSize;

    protected final int locksSize;

    private boolean rethrowException;

    public static final int BEFORE_BCI = -2;
    public static final int AFTER_BCI = -3;

    @Input    private FrameState outerFrameState;

    @Input    private final NodeInputList<ValueNode> values;

    @Input    private final NodeInputList<Node> virtualObjectMappings;

    public FrameState outerFrameState() {
        return outerFrameState;
    }

    public void setOuterFrameState(FrameState x) {
        updateUsages(this.outerFrameState, x);
        this.outerFrameState = x;
    }

    @Override
    public void setValueAt(int i, ValueNode x) {
        values.set(i, x);
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
    public FrameState(RiMethod method, int bci, int localsSize, int stackSize, int locksSize, boolean rethrowException, Graph graph) {
        super(CiKind.Illegal, graph);
        this.method = method;
        this.bci = bci;
        this.localsSize = localsSize;
        this.stackSize = stackSize;
        this.locksSize = locksSize;
        this.values = new NodeInputList<ValueNode>(this, localsSize + stackSize + locksSize);
        this.virtualObjectMappings = new NodeInputList<Node>(this);
        this.rethrowException = rethrowException;
        //GraalMetrics.FrameStatesCreated++;
        //GraalMetrics.FrameStateValuesCreated += localsSize + stackSize + locksSize;
    }

    public FrameState(RiMethod method, int bci, ValueNode[] locals, ValueNode[] stack, int stackSize, ArrayList<ValueNode> locks, boolean rethrowException, Graph graph) {
        this(method, bci, locals.length, stackSize, locks.size(), rethrowException, graph);
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

    public boolean rethrowException() {
        return rethrowException;
    }

    public RiMethod method() {
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
    public FrameState duplicate(int bci) {
        return duplicate(bci, false);
    }

    public FrameState duplicate(int bci, boolean duplicateOuter) {
        FrameState other = new FrameState(method, bci, localsSize, stackSize, locksSize, rethrowException, graph());
        other.values.setAll(values);
        other.virtualObjectMappings.setAll(virtualObjectMappings);
        FrameState outerFrameState = outerFrameState();
        if (duplicateOuter && outerFrameState != null) {
            outerFrameState = outerFrameState.duplicate(outerFrameState.bci, duplicateOuter);
        }
        other.setOuterFrameState(outerFrameState);
        return other;
    }

    @Override
    public FrameState duplicateWithException(int bci, ValueNode exceptionObject) {
        return duplicateModified(bci, true, CiKind.Void, exceptionObject);
    }

    /**
     * Creates a copy of this frame state with one stack element of type popKind popped from the stack and the
     * values in pushedValues pushed on the stack. The pushedValues are expected to be in slot encoding: a long
     * or double is followed by a null slot.
     */
    public FrameState duplicateModified(int bci, boolean rethrowException, CiKind popKind, ValueNode... pushedValues) {
        int popSlots = popKind.sizeInSlots();
        int pushSlots = pushedValues.length;
        FrameState other = new FrameState(method, bci, localsSize, stackSize - popSlots + pushSlots, locksSize(), rethrowException, graph());
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
    public void storeLocal(int i, ValueNode x) {
        assert i < localsSize : "local variable index out of range: " + i;
        invalidateLocal(i);
        setValueAt(i, x);
        if (ValueUtil.isDoubleWord(x)) {
            // (tw) if this was a double word then kill i+1
            setValueAt(i + 1, null);
        }
        if (i > 0) {
            // if there was a double word at i - 1, then kill it
            ValueNode p = localAt(i - 1);
            if (ValueUtil.isDoubleWord(p)) {
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
    public ValueNode lockAt(int i) {
        assert i >= 0;
        return valueAt(localsSize + stackSize + i);
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
            PhiNode phi = new PhiNode(p.kind, block, PhiType.Value, graph());
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
        PhiNode phi = new PhiNode(p.kind, block, PhiType.Value, graph());
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

    private void checkSize(FrameStateAccess other) {
        if (other.stackSize() != stackSize()) {
            throw new CiBailout("stack sizes do not match");
        } else if (other.localsSize() != localsSize) {
            throw new CiBailout("local sizes do not match");
        }
    }

    public void merge(MergeNode block, FrameStateAccess other) {
        checkSize(other);
        for (int i = 0; i < valuesSize(); i++) {
            ValueNode x = valueAt(i);
            if (x != null) {
                ValueNode y = other.valueAt(i);
                if (x != y || ((x instanceof PhiNode) && ((PhiNode) x).merge() == block)) {
                    if (ValueUtil.typeMismatch(x, y)) {
                        if ((x instanceof PhiNode) && ((PhiNode) x).merge() == block) {
                            x.replaceAtUsages(null);
                            x.delete();
                        }
                        setValueAt(i, null);
                        continue;
                    }
                    PhiNode phi = null;
                    if (i < localsSize) {
                        // this a local
                        phi = setupPhiForLocal(block, i);
                    } else {
                        // this is a stack slot
                        phi = setupPhiForStack(block, i - localsSize);
                    }

                    if (phi.valueCount() == 0) {
                        int size = block.phiPredecessorCount();
                        for (int j = 0; j < size; ++j) {
                            phi.addInput(x);
                        }
                        phi.addInput((x == y) ? phi : y);
                    } else {
                        phi.addInput((x == y) ? phi : y);
                    }

                    assert phi.valueCount() == block.phiPredecessorCount() + (block instanceof LoopBeginNode ? 0 : 1) : "valueCount=" + phi.valueCount() + " predSize= " + block.phiPredecessorCount();
               }
            }
        }
    }

    public MergeNode block() {
        for (Node n : usages()) {
            if (n instanceof MergeNode) {
                return (MergeNode) n;
            }
        }
        return null;
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
    public static interface PhiProcedure {
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

    /**
     * The interface implemented by a client of {@link FrameState#forEachLiveStateValue(ValueProcedure)}.
     */
    public static interface ValueProcedure {
        void doValue(ValueNode value);
    }

    /**
     * Traverses all {@linkplain ValueNode#isLive() live values} of this frame state.
     *
     * @param proc the call back called to process each live value traversed
     */
    public void forEachLiveStateValue(ValueProcedure proc) {
        HashSet<VirtualObjectNode> vobjs = null;
        FrameState current = this;
        do {
            for (int i = 0; i < current.valuesSize(); i++) {
                ValueNode value = current.valueAt(i);
                if (value instanceof VirtualObjectNode) {
                    if (vobjs == null) {
                        vobjs = new HashSet<VirtualObjectNode>();
                    }
                    vobjs.add((VirtualObjectNode) value);
                } else if (value != null) {
                    proc.doValue(value);
                }
            }
            current = current.outerFrameState();
        } while (current != null);

        if (vobjs != null) {
            // collect all VirtualObjectField instances:
            HashMap<VirtualObjectNode, VirtualObjectFieldNode> objectStates = new HashMap<VirtualObjectNode, VirtualObjectFieldNode>();
            current = this;
            do {
                for (int i = 0; i < current.virtualObjectMappingCount(); i++) {
                    VirtualObjectFieldNode field = (VirtualObjectFieldNode) current.virtualObjectMappingAt(i);
                    // null states occur for objects with 0 fields
                    if (field != null && !objectStates.containsKey(field.object())) {
                        objectStates.put(field.object(), field);
                    }
                }
                current = current.outerFrameState();
            } while (current != null);

            do {
                HashSet<VirtualObjectNode> vobjsCopy = new HashSet<VirtualObjectNode>(vobjs);
                for (VirtualObjectNode vobj : vobjsCopy) {
                    if (vobj.fields().length > 0) {
                        boolean[] fieldState = new boolean[vobj.fields().length];
                        FloatingNode currentField = objectStates.get(vobj);
                        assert currentField != null : this;
                        do {
                            if (currentField instanceof VirtualObjectFieldNode) {
                                int index = ((VirtualObjectFieldNode) currentField).index();
                                ValueNode value = ((VirtualObjectFieldNode) currentField).input();
                                if (!fieldState[index]) {
                                    fieldState[index] = true;
                                    if (value instanceof VirtualObjectNode) {
                                        vobjs.add((VirtualObjectNode) value);
                                    } else {
                                        proc.doValue(value);
                                    }
                                }
                                currentField = ((VirtualObjectFieldNode) currentField).lastState();
                            } else {
                                assert currentField instanceof PhiNode : currentField;
                                currentField = (FloatingNode) ((PhiNode) currentField).valueAt(0);
                            }
                        } while (currentField != null);
                    }
                    vobjs.remove(vobj);
                }
            } while (!vobjs.isEmpty());
            assert vobjs.isEmpty() : "at FrameState " + this;
        }
    }

    @Override
    public String toString() {
        return super.toString();
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
            sb.append(String.format("  local[%d] = %-8s : %s%n", i, value == null ? "bogus" : value.kind.javaName, value));
        }
        for (int i = 0; i < stackSize(); ++i) {
            ValueNode value = stackAt(i);
            sb.append(String.format("  stack[%d] = %-8s : %s%n", i, value == null ? "bogus" : value.kind.javaName, value));
        }
        for (int i = 0; i < locksSize(); ++i) {
            ValueNode value = lockAt(i);
            sb.append(String.format("  lock[%d] = %-8s : %s%n", i, value == null ? "bogus" : value.kind.javaName, value));
        }
        return sb.toString();
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitFrameState(this);
    }

    @Override
    public String shortName() {
        return "FrameState@" + bci;
    }

    public void visitFrameState(FrameState i) {
        // nothing to do for now
    }

    @Override
    public Map<Object, Object> getDebugProperties() {
        Map<Object, Object> properties = super.getDebugProperties();
        properties.put("bci", bci);
        properties.put("method", CiUtil.format("%H.%n(%p):%r", method, false));
        StringBuilder str = new StringBuilder();
        for (int i = 0; i < localsSize(); i++) {
            str.append(i == 0 ? "" : ", ").append(localAt(i) == null ? "_" : localAt(i).id());
        }
        properties.put("locals", str.toString());
        str = new StringBuilder();
        for (int i = 0; i < stackSize(); i++) {
            str.append(i == 0 ? "" : ", ").append(stackAt(i) == null ? "_" : stackAt(i).id());
        }
        properties.put("stack", str.toString());
        str = new StringBuilder();
        for (int i = 0; i < locksSize(); i++) {
            str.append(i == 0 ? "" : ", ").append(lockAt(i) == null ? "_" : lockAt(i).id());
        }
        properties.put("locks", str.toString());
        properties.put("rethrowException", rethrowException);
        return properties;
    }

    @Override
    public void delete() {
        FrameState outerFrameState = outerFrameState();
        super.delete();
        if (outerFrameState != null && outerFrameState.usages().isEmpty()) {
            outerFrameState.delete();
        }
    }

    @Override
    public void setRethrowException(boolean b) {
        rethrowException = b;
    }
}
