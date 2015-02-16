/*
 * Copyright (c) 2009, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.bytecode.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.iterators.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.virtual.*;

/**
 * The {@code FrameState} class encapsulates the frame state (i.e. local variables and operand
 * stack) at a particular point in the abstract interpretation.
 *
 * This can be used as debug or deoptimization information.
 */
@NodeInfo(nameTemplate = "FrameState@{p#method/s}:{p#bci}")
public final class FrameState extends VirtualState implements IterableNodeType {
    public static final NodeClass TYPE = NodeClass.get(FrameState.class);

    private static final DebugMetric METRIC_FRAMESTATE_COUNT = Debug.metric("FrameStateCount");

    protected final int localsSize;

    protected final int stackSize;

    /**
     * @see BytecodeFrame#rethrowException
     */
    protected boolean rethrowException;

    protected final boolean duringCall;

    @OptionalInput(value = InputType.State) FrameState outerFrameState;

    /**
     * Contains the locals, the expressions and the locked objects, in this order.
     */
    @OptionalInput NodeInputList<ValueNode> values;

    @OptionalInput(InputType.Association) NodeInputList<MonitorIdNode> monitorIds;

    @OptionalInput(InputType.State) NodeInputList<EscapeObjectState> virtualObjectMappings;

    /**
     * The bytecode index to which this frame state applies.
     */
    public final int bci;

    protected final ResolvedJavaMethod method;

    public FrameState(FrameState outerFrameState, ResolvedJavaMethod method, int bci, int localsSize, int stackSize, int lockSize, boolean rethrowException, boolean duringCall,
                    List<MonitorIdNode> monitorIds, List<EscapeObjectState> virtualObjectMappings) {
        super(TYPE);
        assert stackSize >= 0;
        this.outerFrameState = outerFrameState;
        this.method = method;
        this.bci = bci;
        this.localsSize = localsSize;
        this.stackSize = stackSize;
        this.values = new NodeInputList<>(this, localsSize + stackSize + lockSize);

        if (monitorIds != null && monitorIds.size() > 0) {
            this.monitorIds = new NodeInputList<>(this, monitorIds);
        }

        if (virtualObjectMappings != null && virtualObjectMappings.size() > 0) {
            this.virtualObjectMappings = new NodeInputList<>(this, virtualObjectMappings);
        }

        this.rethrowException = rethrowException;
        this.duringCall = duringCall;
        assert !this.rethrowException || this.stackSize == 1 : "must have exception on top of the stack";
        assert this.locksSize() == this.monitorIdCount();
        METRIC_FRAMESTATE_COUNT.increment();
    }

    public FrameState(FrameState outerFrameState, ResolvedJavaMethod method, int bci, List<ValueNode> values, int localsSize, int stackSize, boolean rethrowException, boolean duringCall,
                    List<MonitorIdNode> monitorIds, List<EscapeObjectState> virtualObjectMappings) {
        this(outerFrameState, method, bci, localsSize, stackSize, values.size() - localsSize - stackSize, rethrowException, duringCall, monitorIds, virtualObjectMappings);
        for (int i = 0; i < values.size(); ++i) {
            this.values.initialize(i, values.get(i));
        }
    }

    public FrameState(int bci) {
        this(null, null, bci, 0, 0, 0, false, false, null, Collections.<EscapeObjectState> emptyList());
        assert bci == BytecodeFrame.BEFORE_BCI || bci == BytecodeFrame.AFTER_BCI || bci == BytecodeFrame.AFTER_EXCEPTION_BCI || bci == BytecodeFrame.UNKNOWN_BCI ||
                        bci == BytecodeFrame.INVALID_FRAMESTATE_BCI;
    }

    public FrameState(FrameState outerFrameState, ResolvedJavaMethod method, int bci, ValueNode[] locals, ValueNode[] stack, int stackSize, ValueNode[] locks, List<MonitorIdNode> monitorIds,
                    boolean rethrowException, boolean duringCall) {
        this(outerFrameState, method, bci, locals.length, stackSize, locks.length, rethrowException, duringCall, monitorIds, Collections.<EscapeObjectState> emptyList());
        createValues(locals, stack, locks);
    }

    private void createValues(ValueNode[] locals, ValueNode[] stack, ValueNode[] locks) {
        int index = 0;
        for (int i = 0; i < locals.length; ++i) {
            this.values.initialize(index++, locals[i]);
        }
        for (int i = 0; i < stackSize; ++i) {
            this.values.initialize(index++, stack[i]);
        }
        for (int i = 0; i < locks.length; ++i) {
            this.values.initialize(index++, locks[i]);
        }
    }

    public NodeInputList<ValueNode> values() {
        return values;
    }

    public NodeInputList<MonitorIdNode> monitorIds() {
        return monitorIds;
    }

    public FrameState outerFrameState() {
        return outerFrameState;
    }

    public void setOuterFrameState(FrameState x) {
        updateUsages(this.outerFrameState, x);
        this.outerFrameState = x;
    }

    /**
     * @see BytecodeFrame#rethrowException
     */
    public boolean rethrowException() {
        return rethrowException;
    }

    public boolean duringCall() {
        return duringCall;
    }

    public ResolvedJavaMethod method() {
        return method;
    }

    public void addVirtualObjectMapping(EscapeObjectState virtualObject) {
        if (virtualObjectMappings == null) {
            virtualObjectMappings = new NodeInputList<>(this);
        }
        virtualObjectMappings.add(virtualObject);
    }

    public int virtualObjectMappingCount() {
        if (virtualObjectMappings == null) {
            return 0;
        }
        return virtualObjectMappings.size();
    }

    public EscapeObjectState virtualObjectMappingAt(int i) {
        return virtualObjectMappings.get(i);
    }

    public NodeInputList<EscapeObjectState> virtualObjectMappings() {
        return virtualObjectMappings;
    }

    /**
     * Gets a copy of this frame state.
     */
    public FrameState duplicate(int newBci) {
        return graph().add(new FrameState(outerFrameState(), method, newBci, values, localsSize, stackSize, rethrowException, duringCall, monitorIds, virtualObjectMappings));
    }

    /**
     * Gets a copy of this frame state.
     */
    public FrameState duplicate() {
        return duplicate(bci);
    }

    /**
     * Duplicates a FrameState, along with a deep copy of all connected VirtualState (outer
     * FrameStates, VirtualObjectStates, ...).
     */
    @Override
    public FrameState duplicateWithVirtualState() {
        FrameState newOuterFrameState = outerFrameState();
        if (newOuterFrameState != null) {
            newOuterFrameState = newOuterFrameState.duplicateWithVirtualState();
        }
        ArrayList<EscapeObjectState> newVirtualMappings = null;
        if (virtualObjectMappings != null) {
            newVirtualMappings = new ArrayList<>(virtualObjectMappings.size());
            for (EscapeObjectState state : virtualObjectMappings) {
                newVirtualMappings.add(state.duplicateWithVirtualState());
            }
        }
        return graph().add(new FrameState(newOuterFrameState, method, bci, values, localsSize, stackSize, rethrowException, duringCall, monitorIds, newVirtualMappings));
    }

    /**
     * Creates a copy of this frame state with one stack element of type popKind popped from the
     * stack and the values in pushedValues pushed on the stack. The pushedValues will be formatted
     * correctly in slot encoding: a long or double will be followed by a null slot.
     */
    public FrameState duplicateModifiedDuringCall(int newBci, Kind popKind) {
        return duplicateModified(newBci, rethrowException, true, popKind);
    }

    public FrameState duplicateModified(int newBci, boolean newRethrowException, Kind popKind, ValueNode... pushedValues) {
        return duplicateModified(newBci, newRethrowException, duringCall, popKind, pushedValues);
    }

    /**
     * Creates a copy of this frame state with the top of stack replaced with with
     * {@code pushedValue} which must be of type {@code popKind}.
     */
    public FrameState duplicateModified(Kind popKind, ValueNode pushedValue) {
        assert pushedValue != null && pushedValue.getKind() == popKind;
        return duplicateModified(bci, rethrowException, duringCall, popKind, pushedValue);
    }

    /**
     * Creates a copy of this frame state with one stack element of type popKind popped from the
     * stack and the values in pushedValues pushed on the stack. The pushedValues will be formatted
     * correctly in slot encoding: a long or double will be followed by a null slot. The bci will be
     * changed to newBci.
     */
    private FrameState duplicateModified(int newBci, boolean newRethrowException, boolean newDuringCall, Kind popKind, ValueNode... pushedValues) {
        ArrayList<ValueNode> copy = new ArrayList<>(values.subList(0, localsSize + stackSize));
        if (popKind != Kind.Void) {
            if (stackAt(stackSize() - 1) == null) {
                copy.remove(copy.size() - 1);
            }
            ValueNode lastSlot = copy.get(copy.size() - 1);
            assert lastSlot.getKind().getStackKind() == popKind.getStackKind();
            copy.remove(copy.size() - 1);
        }
        for (ValueNode node : pushedValues) {
            copy.add(node);
            if (node.getKind().needsTwoSlots()) {
                copy.add(null);
            }
        }
        int newStackSize = copy.size() - localsSize;
        copy.addAll(values.subList(localsSize + stackSize, values.size()));

        assert checkStackDepth(bci, stackSize, duringCall, newBci, newStackSize, newDuringCall);
        return graph().add(new FrameState(outerFrameState(), method, newBci, copy, localsSize, newStackSize, newRethrowException, newDuringCall, monitorIds, virtualObjectMappings));
    }

    /**
     * Perform a few sanity checks on the transformation of the stack state. The current expectation
     * is that a stateAfter is being transformed into a stateDuring, so the stack depth may change.
     */
    private boolean checkStackDepth(int oldBci, int oldStackSize, boolean oldDuringCall, int newBci, int newStackSize, boolean newDuringCall) {
        /*
         * It would be nice to have a complete check of the shape of the FrameState based on a
         * dataflow of the bytecodes but for now just check for obvious expression stack depth
         * mistakes.
         */
        byte[] codes = method.getCode();
        if (codes == null) {
            /* Graph was constructed manually. */
            return true;
        }
        byte newCode = codes[newBci];
        if (oldBci == newBci) {
            assert oldStackSize == newStackSize || oldDuringCall != newDuringCall : "bci is unchanged, stack depth shouldn't change";
        } else {
            byte oldCode = codes[oldBci];
            assert Bytecodes.lengthOf(newCode) + newBci == oldBci || Bytecodes.lengthOf(oldCode) + oldBci == newBci : "expecting roll back or forward";
        }
        assert !newDuringCall || Bytecodes.isInvoke(newCode) || newStackSize + Bytecodes.stackEffectOf(newCode) >= 0 : "stack underflow at " + Bytecodes.nameOf(newCode);
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
     * Gets the number of locked monitors in this frame state.
     */
    public int locksSize() {
        return values.size() - localsSize - stackSize;
    }

    /**
     * Gets the number of locked monitors in this frame state and all
     * {@linkplain #outerFrameState() outer} frame states.
     */
    public int nestedLockDepth() {
        int depth = locksSize();
        for (FrameState outer = outerFrameState(); outer != null; outer = outer.outerFrameState()) {
            depth += outer.locksSize();
        }
        return depth;
    }

    /**
     * Gets the value in the local variables at the specified index.
     *
     * @param i the index into the locals
     * @return the instruction that produced the value for the specified local
     */
    public ValueNode localAt(int i) {
        assert i >= 0 && i < localsSize : "local variable index out of range: " + i;
        return values.get(i);
    }

    /**
     * Get the value on the stack at the specified stack index.
     *
     * @param i the index into the stack, with {@code 0} being the bottom of the stack
     * @return the instruction at the specified position in the stack
     */
    public ValueNode stackAt(int i) {
        assert i >= 0 && i < stackSize;
        return values.get(localsSize + i);
    }

    /**
     * Get the monitor owner at the specified index.
     *
     * @param i the index into the list of locked monitors.
     * @return the lock owner at the given index.
     */
    public ValueNode lockAt(int i) {
        assert i >= 0 && i < locksSize();
        return values.get(localsSize + stackSize + i);
    }

    /**
     * Get the MonitorIdNode that corresponds to the locked object at the specified index.
     */
    public MonitorIdNode monitorIdAt(int i) {
        assert monitorIds != null && i >= 0 && i < locksSize();
        return monitorIds.get(i);
    }

    public int monitorIdCount() {
        if (monitorIds == null) {
            return 0;
        } else {
            return monitorIds.size();
        }
    }

    public NodeIterable<FrameState> innerFrameStates() {
        return usages().filter(FrameState.class);
    }

    private static String toString(FrameState frameState) {
        StringBuilder sb = new StringBuilder();
        String nl = CodeUtil.NEW_LINE;
        FrameState fs = frameState;
        while (fs != null) {
            MetaUtil.appendLocation(sb, fs.method, fs.bci).append(nl);
            sb.append("locals: [");
            for (int i = 0; i < fs.localsSize(); i++) {
                sb.append(i == 0 ? "" : ", ").append(fs.localAt(i) == null ? "_" : fs.localAt(i).toString(Verbosity.Id));
            }
            sb.append("]").append(nl).append("stack: [");
            for (int i = 0; i < fs.stackSize(); i++) {
                sb.append(i == 0 ? "" : ", ").append(fs.stackAt(i) == null ? "_" : fs.stackAt(i).toString(Verbosity.Id));
            }
            sb.append("]").append(nl).append("locks: [");
            for (int i = 0; i < fs.locksSize(); i++) {
                sb.append(i == 0 ? "" : ", ").append(fs.lockAt(i) == null ? "_" : fs.lockAt(i).toString(Verbosity.Id));
            }
            sb.append(']').append(nl);
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

    @Override
    public Map<Object, Object> getDebugProperties(Map<Object, Object> map) {
        Map<Object, Object> properties = super.getDebugProperties(map);
        if (method != null) {
            // properties.put("method", MetaUtil.format("%H.%n(%p):%r", method));
            StackTraceElement ste = method.asStackTraceElement(bci);
            if (ste.getFileName() != null && ste.getLineNumber() >= 0) {
                properties.put("sourceFile", ste.getFileName());
                properties.put("sourceLine", ste.getLineNumber());
            }
        }
        if (bci == BytecodeFrame.AFTER_BCI) {
            properties.put("bci", "AFTER_BCI");
        } else if (bci == BytecodeFrame.AFTER_EXCEPTION_BCI) {
            properties.put("bci", "AFTER_EXCEPTION_BCI");
        } else if (bci == BytecodeFrame.INVALID_FRAMESTATE_BCI) {
            properties.put("bci", "INVALID_FRAMESTATE_BCI");
        } else if (bci == BytecodeFrame.BEFORE_BCI) {
            properties.put("bci", "BEFORE_BCI");
        } else if (bci == BytecodeFrame.UNKNOWN_BCI) {
            properties.put("bci", "UNKNOWN_BCI");
        } else if (bci == BytecodeFrame.UNWIND_BCI) {
            properties.put("bci", "UNWIND_BCI");
        }
        properties.put("locksSize", values.size() - stackSize - localsSize);
        return properties;
    }

    @Override
    public boolean verify() {
        assertTrue(locksSize() == monitorIdCount(), "mismatch in number of locks");
        for (ValueNode value : values) {
            assertTrue(value == null || !value.isDeleted(), "frame state must not contain deleted nodes");
            assertTrue(value == null || value instanceof VirtualObjectNode || (value.getKind() != Kind.Void), "unexpected value: %s", value);
        }
        return super.verify();
    }

    @Override
    public void applyToNonVirtual(NodeClosure<? super ValueNode> closure) {
        for (ValueNode value : values) {
            if (value != null) {
                closure.apply(this, value);
            }
        }

        if (monitorIds != null) {
            for (MonitorIdNode monitorId : monitorIds) {
                if (monitorId != null) {
                    closure.apply(this, monitorId);
                }
            }
        }

        if (virtualObjectMappings != null) {
            for (EscapeObjectState state : virtualObjectMappings) {
                state.applyToNonVirtual(closure);
            }
        }

        if (outerFrameState() != null) {
            outerFrameState().applyToNonVirtual(closure);
        }
    }

    @Override
    public void applyToVirtual(VirtualClosure closure) {
        closure.apply(this);
        if (virtualObjectMappings != null) {
            for (EscapeObjectState state : virtualObjectMappings) {
                state.applyToVirtual(closure);
            }
        }
        if (outerFrameState() != null) {
            outerFrameState().applyToVirtual(closure);
        }
    }

    @Override
    public boolean isPartOfThisState(VirtualState state) {
        if (state == this) {
            return true;
        }
        if (outerFrameState() != null && outerFrameState().isPartOfThisState(state)) {
            return true;
        }
        if (virtualObjectMappings != null) {
            for (EscapeObjectState objectState : virtualObjectMappings) {
                if (objectState.isPartOfThisState(state)) {
                    return true;
                }
            }
        }
        return false;
    }
}
