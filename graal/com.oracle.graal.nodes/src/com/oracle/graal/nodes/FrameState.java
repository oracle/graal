/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.max.cri.ci.*;
import com.oracle.max.cri.ri.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.iterators.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.virtual.*;

/**
 * The {@code FrameState} class encapsulates the frame state (i.e. local variables and
 * operand stack) at a particular point in the abstract interpretation.
 */
public final class FrameState extends Node implements Node.IterableNodeType, LIRLowerable {

    protected final int localsSize;

    protected final int stackSize;

    private boolean rethrowException;

    private boolean duringCall;

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
     * @param rethrowException if true the VM should re-throw the exception on top of the stack when deopt'ing using this framestate
     */
    public FrameState(RiResolvedMethod method, int bci, int localsSize, int stackSize, boolean rethrowException, boolean duringCall) {
        assert stackSize >= 0;
        this.method = method;
        this.bci = bci;
        this.localsSize = localsSize;
        this.stackSize = stackSize;
        this.values = new NodeInputList<>(this, localsSize + stackSize);
        this.virtualObjectMappings = new NodeInputList<>(this);
        this.rethrowException = rethrowException;
        this.duringCall = duringCall;
        assert !rethrowException || stackSize == 1 : "must have exception on top of the stack";
    }

    public FrameState(RiResolvedMethod method, int bci, ValueNode[] locals, ValueNode[] stack, int stackSize, boolean rethrowException, boolean duringCall) {
        this.method = method;
        this.bci = bci;
        this.localsSize = locals.length;
        this.stackSize = stackSize;
        final ValueNode[] newValues = new ValueNode[locals.length + stackSize];
        for (int i = 0; i < locals.length; i++) {
            assert locals[i] == null || !locals[i].isDeleted();
            newValues[i] = locals[i];
        }
        for (int i = 0; i < stackSize; i++) {
            assert stack[i] == null || !stack[i].isDeleted();
            newValues[localsSize + i] = stack[i];
        }
        this.values = new NodeInputList<>(this, newValues);
        this.virtualObjectMappings = new NodeInputList<>(this);
        this.rethrowException = rethrowException;
        this.duringCall = duringCall;
        assert !rethrowException || stackSize == 1 : "must have exception on top of the stack";
    }

    public NodeIterable<ValueNode> values() {
        return values;
    }

    public FrameState outerFrameState() {
        return outerFrameState;
    }

    public void setOuterFrameState(FrameState x) {
        updateUsages(this.outerFrameState, x);
        this.outerFrameState = x;
    }

    private void setValueAt(int i, ValueNode x) {
        values.set(i, x);
    }

    public boolean rethrowException() {
        return rethrowException;
    }

    public boolean duringCall() {
        return duringCall;
    }

    public void setDuringCall(boolean b) {
        this.duringCall = b;
    }

    public RiResolvedMethod method() {
        return method;
    }

    public void addVirtualObjectMapping(Node virtualObject) {
        assert virtualObject instanceof VirtualObjectFieldNode || virtualObject instanceof PhiNode || virtualObject instanceof ValueProxyNode : virtualObject;
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

    /**
     * Gets a copy of this frame state.
     */
    public FrameState duplicate() {
        return duplicate(bci);
    }

    public FrameState duplicate(int newBci, boolean duplicateOuter) {
        FrameState other = graph().add(new FrameState(method, newBci, localsSize, stackSize, rethrowException, duringCall));
        other.values.setAll(values);
        other.virtualObjectMappings.setAll(virtualObjectMappings);
        FrameState newOuterFrameState = outerFrameState();
        if (duplicateOuter && newOuterFrameState != null) {
            newOuterFrameState = newOuterFrameState.duplicate(newOuterFrameState.bci, duplicateOuter);
        }
        other.setOuterFrameState(newOuterFrameState);
        return other;
    }

    /**
     * Creates a copy of this frame state with one stack element of type popKind popped from the stack and the
     * values in pushedValues pushed on the stack. The pushedValues are expected to be in slot encoding: a long
     * or double is followed by a null slot.
     */
    public FrameState duplicateModified(int newBci, boolean newRethrowException, CiKind popKind, ValueNode... pushedValues) {
        int popSlots = 0;
        if (popKind != CiKind.Void) {
            if (stackAt(stackSize() - 1) == null) {
                popSlots = 2;
            } else {
                popSlots = 1;
            }
            assert stackAt(stackSize() - popSlots).kind().stackKind() == popKind.stackKind() || (stackAt(stackSize() - popSlots) instanceof BoxedVirtualObjectNode && popKind.isObject());
        }

        int pushSlots = pushedValues.length;
        FrameState other = graph().add(new FrameState(method, newBci, localsSize, stackSize - popSlots + pushSlots, newRethrowException, false));
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
        other.virtualObjectMappings.setAll(virtualObjectMappings);
        other.setOuterFrameState(outerFrameState());
        return other;
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

    public MergeNode block() {
        return usages().filter(MergeNode.class).first();
    }

    public NodeIterable<FrameState> innerFrameStates() {
        return usages().filter(FrameState.class);
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
        // Nothing to do, frame states are processed as part of the handling of AbstractStateSplit nodes.
    }

    private static String toString(FrameState frameState) {
        StringBuilder sb = new StringBuilder();
        String nl = CiUtil.NEW_LINE;
        FrameState fs = frameState;
        while (fs != null) {
            CiUtil.appendLocation(sb, fs.method, fs.bci).append(nl);
            sb.append("locals: [");
            for (int i = 0; i < fs.localsSize(); i++) {
                sb.append(i == 0 ? "" : ", ").append(fs.localAt(i) == null ? "_" : fs.localAt(i).toString(Verbosity.Id));
            }
            sb.append("]").append(nl).append("stack: ");
            for (int i = 0; i < fs.stackSize(); i++) {
                sb.append(i == 0 ? "" : ", ").append(fs.stackAt(i) == null ? "_" : fs.stackAt(i).toString(Verbosity.Id));
            }
            sb.append(nl);
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
    public Map<Object, Object> getDebugProperties() {
        Map<Object, Object> properties = super.getDebugProperties();
        properties.put("bci", bci);
        if (method != null) {
            properties.put("method", CiUtil.format("%H.%n(%p):%r", method));
        } else {
            properties.put("method", "None");
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < localsSize(); i++) {
            sb.append(i == 0 ? "" : ", ").append(localAt(i) == null ? "_" : localAt(i).toString(Verbosity.Id));
        }
        properties.put("locals", sb.toString());
        sb = new StringBuilder();
        for (int i = 0; i < stackSize(); i++) {
            sb.append(i == 0 ? "" : ", ").append(stackAt(i) == null ? "_" : stackAt(i).toString(Verbosity.Id));
        }
        properties.put("stack", sb.toString());
        properties.put("rethrowException", rethrowException);
        properties.put("duringCall", duringCall);
        return properties;
    }

    @Override
    public boolean verify() {
        for (ValueNode value : values) {
            assert assertTrue(value == null || !value.isDeleted(), "frame state must not contain deleted nodes");
            assert assertTrue(value == null || value instanceof VirtualObjectNode || (value.kind() != CiKind.Void && value.kind() != CiKind.Illegal), "unexpected value: %s", value);
        }
        return super.verify();
    }
}
