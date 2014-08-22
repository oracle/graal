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
public class FrameState extends VirtualState implements IterableNodeType {

    private static final DebugMetric METRIC_FRAMESTATE_COUNT = Debug.metric("FrameStateCount");

    protected final int localsSize;

    protected final int stackSize;

    /**
     * @see BytecodeFrame#rethrowException
     */
    private boolean rethrowException;

    private boolean duringCall;

    @OptionalInput(value = InputType.State) FrameState outerFrameState;

    /**
     * Contains the locals, the expressions and the locked objects, in this order.
     */
    @OptionalInput NodeInputList<ValueNode> values;

    @OptionalInput(InputType.Association) NodeInputList<MonitorIdNode> monitorIds;

    @Input(InputType.State) NodeInputList<EscapeObjectState> virtualObjectMappings;

    /**
     * The bytecode index to which this frame state applies.
     */
    public final int bci;

    private final ResolvedJavaMethod method;

    /**
     * Creates a {@code FrameState} with the given locals, stack expressions and locked monitors.
     *
     * @param method the method for this frame state
     * @param bci the bytecode index of the frame state
     * @param values the locals, stack expressions and locked objects, in this order
     * @param localsSize the number of locals in the values list
     * @param stackSize the number of stack expressions in the values list
     * @param rethrowException if true, this FrameState will throw an exception (taken from the top
     *            of the expression stack) during deoptimization
     * @param duringCall true if this FrameState describes the state during a call
     * @param monitorIds one MonitorIdNode for each locked object
     * @param virtualObjectMappings a description of the current state for every virtual object
     */
    public static FrameState create(FrameState outerFrameState, ResolvedJavaMethod method, int bci, List<ValueNode> values, int localsSize, int stackSize, boolean rethrowException,
                    boolean duringCall, List<MonitorIdNode> monitorIds, List<EscapeObjectState> virtualObjectMappings) {
        return new FrameStateGen(outerFrameState, method, bci, values, localsSize, stackSize, rethrowException, duringCall, monitorIds, virtualObjectMappings);
    }

    protected FrameState(FrameState outerFrameState, ResolvedJavaMethod method, int bci, List<ValueNode> values, int localsSize, int stackSize, boolean rethrowException, boolean duringCall,
                    List<MonitorIdNode> monitorIds, List<EscapeObjectState> virtualObjectMappings) {
        assert stackSize >= 0;
        this.outerFrameState = outerFrameState;
        this.method = method;
        this.bci = bci;
        this.localsSize = localsSize;
        this.stackSize = stackSize;
        this.values = new NodeInputList<>(this, values);
        this.monitorIds = new NodeInputList<>(this, monitorIds);
        this.virtualObjectMappings = new NodeInputList<>(this, virtualObjectMappings);
        this.rethrowException = rethrowException;
        this.duringCall = duringCall;
        assert !this.rethrowException || this.stackSize == 1 : "must have exception on top of the stack";
        assert this.locksSize() == this.monitorIds.size();
        METRIC_FRAMESTATE_COUNT.increment();
    }

    /**
     * Simple constructor used to create marker FrameStates.
     *
     * @param bci marker bci, needs to be &lt; 0
     */
    public static FrameState create(int bci) {
        return new FrameStateGen(bci);
    }

    protected FrameState(int bci) {
        this(null, null, bci, Collections.<ValueNode> emptyList(), 0, 0, false, false, Collections.<MonitorIdNode> emptyList(), Collections.<EscapeObjectState> emptyList());
        assert bci == BytecodeFrame.BEFORE_BCI || bci == BytecodeFrame.AFTER_BCI || bci == BytecodeFrame.AFTER_EXCEPTION_BCI || bci == BytecodeFrame.UNKNOWN_BCI ||
                        bci == BytecodeFrame.INVALID_FRAMESTATE_BCI;
    }

    public static FrameState create(ResolvedJavaMethod method, int bci, ValueNode[] locals, List<ValueNode> stack, ValueNode[] locks, MonitorIdNode[] monitorIds, boolean rethrowException,
                    boolean duringCall) {
        return new FrameStateGen(method, bci, locals, stack, locks, monitorIds, rethrowException, duringCall);
    }

    protected FrameState(ResolvedJavaMethod method, int bci, ValueNode[] locals, List<ValueNode> stack, ValueNode[] locks, MonitorIdNode[] monitorIds, boolean rethrowException, boolean duringCall) {
        this(null, method, bci, createValues(locals, stack, locks), locals.length, stack.size(), rethrowException, duringCall, Arrays.asList(monitorIds), Collections.<EscapeObjectState> emptyList());
    }

    private static List<ValueNode> createValues(ValueNode[] locals, List<ValueNode> stack, ValueNode[] locks) {
        List<ValueNode> newValues = new ArrayList<>(locals.length + stack.size() + locks.length);
        for (ValueNode value : locals) {
            newValues.add(value);
            assert value == null || value.isAlive();
        }
        for (ValueNode value : stack) {
            newValues.add(value);
            assert value == null || value.isAlive();
        }
        for (ValueNode value : locks) {
            newValues.add(value);
            assert value == null || value.isAlive();
        }
        return newValues;
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

    public void setDuringCall(boolean b) {
        this.duringCall = b;
    }

    public ResolvedJavaMethod method() {
        return method;
    }

    public void addVirtualObjectMapping(EscapeObjectState virtualObject) {
        virtualObjectMappings.add(virtualObject);
    }

    public int virtualObjectMappingCount() {
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
        return graph().add(FrameState.create(outerFrameState(), method, newBci, values, localsSize, stackSize, rethrowException, duringCall, monitorIds, virtualObjectMappings));
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
        ArrayList<EscapeObjectState> newVirtualMappings = new ArrayList<>(virtualObjectMappings.size());
        for (EscapeObjectState state : virtualObjectMappings) {
            newVirtualMappings.add(state.duplicateWithVirtualState());
        }
        return graph().add(FrameState.create(newOuterFrameState, method, bci, values, localsSize, stackSize, rethrowException, duringCall, monitorIds, newVirtualMappings));
    }

    /**
     * Creates a copy of this frame state with one stack element of type popKind popped from the
     * stack and the values in pushedValues pushed on the stack. The pushedValues will be formatted
     * correctly in slot encoding: a long or double will be followed by a null slot.
     */
    public FrameState duplicateModified(int newBci, boolean newRethrowException, Kind popKind, ValueNode... pushedValues) {
        return duplicateModified(newBci, method, newRethrowException, popKind, pushedValues);
    }

    /**
     * Creates a copy of this frame state with one stack element of type popKind popped from the
     * stack and the values in pushedValues pushed on the stack. The pushedValues will be formatted
     * correctly in slot encoding: a long or double will be followed by a null slot.
     */
    public FrameState duplicateModified(Kind popKind, ValueNode... pushedValues) {
        return duplicateModified(bci, method, rethrowException, popKind, pushedValues);
    }

    private FrameState duplicateModified(int newBci, ResolvedJavaMethod newMethod, boolean newRethrowException, Kind popKind, ValueNode... pushedValues) {
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
            if (node.getKind() == Kind.Long || node.getKind() == Kind.Double) {
                copy.add(null);
            }
        }
        int newStackSize = copy.size() - localsSize;
        copy.addAll(values.subList(localsSize + stackSize, values.size()));

        return graph().add(FrameState.create(outerFrameState(), newMethod, newBci, copy, localsSize, newStackSize, newRethrowException, false, monitorIds, virtualObjectMappings));
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
        assert i >= 0 && i < locksSize();
        return monitorIds.get(i);
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
        assertTrue(locksSize() == monitorIds.size(), "mismatch in number of locks");
        for (ValueNode value : values) {
            assertTrue(value == null || !value.isDeleted(), "frame state must not contain deleted nodes");
            assertTrue(value == null || value instanceof VirtualObjectNode || (value.getKind() != Kind.Void), "unexpected value: %s", value);
        }
        return super.verify();
    }

    @Override
    public void applyToNonVirtual(NodeClosure<? super ValueNode> closure) {
        for (ValueNode value : values.nonNull()) {
            closure.apply(this, value);
        }
        for (MonitorIdNode monitorId : monitorIds) {
            if (monitorId != null) {
                closure.apply(this, monitorId);
            }
        }
        for (EscapeObjectState state : virtualObjectMappings) {
            state.applyToNonVirtual(closure);
        }
        if (outerFrameState() != null) {
            outerFrameState().applyToNonVirtual(closure);
        }
    }

    @Override
    public void applyToVirtual(VirtualClosure closure) {
        closure.apply(this);
        for (EscapeObjectState state : virtualObjectMappings) {
            state.applyToVirtual(closure);
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
        for (EscapeObjectState objectState : virtualObjectMappings) {
            if (objectState.isPartOfThisState(state)) {
                return true;
            }
        }
        return false;
    }
}
