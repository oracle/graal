/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.gen;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Map;
import java.util.Queue;

import jdk.internal.jvmci.code.BytecodeFrame;
import jdk.internal.jvmci.code.VirtualObject;
import jdk.internal.jvmci.common.JVMCIError;
import jdk.internal.jvmci.meta.JavaConstant;
import jdk.internal.jvmci.meta.JavaKind;
import jdk.internal.jvmci.meta.JavaType;
import jdk.internal.jvmci.meta.JavaValue;
import jdk.internal.jvmci.meta.ResolvedJavaField;
import jdk.internal.jvmci.meta.ResolvedJavaType;
import jdk.internal.jvmci.meta.Value;

import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.DebugMetric;
import com.oracle.graal.graph.Node;
import com.oracle.graal.lir.LIRFrameState;
import com.oracle.graal.lir.LabelRef;
import com.oracle.graal.lir.Variable;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.FrameState;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.spi.NodeValueMap;
import com.oracle.graal.nodes.util.GraphUtil;
import com.oracle.graal.nodes.virtual.EscapeObjectState;
import com.oracle.graal.nodes.virtual.VirtualObjectNode;
import com.oracle.graal.virtual.nodes.MaterializedObjectState;
import com.oracle.graal.virtual.nodes.VirtualObjectState;

/**
 * Builds {@link LIRFrameState}s from {@link FrameState}s.
 */
public class DebugInfoBuilder {

    protected final NodeValueMap nodeValueMap;

    public DebugInfoBuilder(NodeValueMap nodeValueMap) {
        this.nodeValueMap = nodeValueMap;
    }

    protected final Map<VirtualObjectNode, VirtualObject> virtualObjects = Node.newMap();
    protected final Map<VirtualObjectNode, EscapeObjectState> objectStates = Node.newIdentityMap();

    protected final Queue<VirtualObjectNode> pendingVirtualObjects = new ArrayDeque<>();

    public LIRFrameState build(FrameState topState, LabelRef exceptionEdge) {
        assert virtualObjects.size() == 0;
        assert objectStates.size() == 0;
        assert pendingVirtualObjects.size() == 0;

        // collect all VirtualObjectField instances:
        FrameState current = topState;
        do {
            if (current.virtualObjectMappingCount() > 0) {
                for (EscapeObjectState state : current.virtualObjectMappings()) {
                    if (!objectStates.containsKey(state.object())) {
                        if (!(state instanceof MaterializedObjectState) || ((MaterializedObjectState) state).materializedValue() != state.object()) {
                            objectStates.put(state.object(), state);
                        }
                    }
                }
            }
            current = current.outerFrameState();
        } while (current != null);

        BytecodeFrame frame = computeFrameForState(topState);

        VirtualObject[] virtualObjectsArray = null;
        if (virtualObjects.size() != 0) {
            // fill in the VirtualObject values
            VirtualObjectNode vobjNode;
            while ((vobjNode = pendingVirtualObjects.poll()) != null) {
                VirtualObject vobjValue = virtualObjects.get(vobjNode);
                assert vobjValue.getValues() == null;

                JavaValue[] values = new JavaValue[vobjNode.entryCount()];
                JavaKind[] slotKinds = new JavaKind[vobjNode.entryCount()];
                if (values.length > 0) {
                    VirtualObjectState currentField = (VirtualObjectState) objectStates.get(vobjNode);
                    assert currentField != null;
                    int pos = 0;
                    for (int i = 0; i < vobjNode.entryCount(); i++) {
                        if (!currentField.values().get(i).isConstant() || currentField.values().get(i).asJavaConstant().getJavaKind() != JavaKind.Illegal) {
                            ValueNode value = currentField.values().get(i);
                            values[pos] = toJavaValue(value);
                            slotKinds[pos] = toSlotKind(value);
                            pos++;
                        } else {
                            assert currentField.values().get(i - 1).getStackKind() == JavaKind.Double || currentField.values().get(i - 1).getStackKind() == JavaKind.Long : vobjNode + " " + i + " " +
                                            currentField.values().get(i - 1);
                        }
                    }
                    if (pos != vobjNode.entryCount()) {
                        values = Arrays.copyOf(values, pos);
                        slotKinds = Arrays.copyOf(slotKinds, pos);
                    }
                }
                assert checkValues(vobjValue.getType(), values, slotKinds);
                vobjValue.setValues(values, slotKinds);
            }

            virtualObjectsArray = virtualObjects.values().toArray(new VirtualObject[virtualObjects.size()]);
            virtualObjects.clear();
        }
        objectStates.clear();

        return newLIRFrameState(exceptionEdge, frame, virtualObjectsArray);
    }

    private boolean checkValues(ResolvedJavaType type, JavaValue[] values, JavaKind[] slotKinds) {
        assert (values == null) == (slotKinds == null);
        if (values != null) {
            assert values.length == slotKinds.length;
            if (!type.isArray()) {
                ResolvedJavaField[] fields = type.getInstanceFields(true);
                int fieldIndex = 0;
                for (int i = 0; i < values.length; i++) {
                    ResolvedJavaField field = fields[fieldIndex++];
                    JavaKind valKind = slotKinds[i].getStackKind();
                    JavaKind fieldKind = storageKind(field.getType());
                    if (fieldKind == JavaKind.Object) {
                        assert valKind.isObject() : field + ": " + valKind + " != " + fieldKind;
                    } else {
                        if ((valKind == JavaKind.Double || valKind == JavaKind.Long) && fieldKind == JavaKind.Int) {
                            assert storageKind(fields[fieldIndex].getType()) == JavaKind.Int;
                            fieldIndex++;
                        } else {
                            assert valKind == fieldKind.getStackKind() : field + ": " + valKind + " != " + fieldKind;
                        }
                    }
                }
                assert fields.length == fieldIndex : type + ": fields=" + Arrays.toString(fields) + ", field values=" + Arrays.toString(values);
            } else {
                JavaKind componentKind = storageKind(type.getComponentType()).getStackKind();
                if (componentKind == JavaKind.Object) {
                    for (int i = 0; i < values.length; i++) {
                        assert slotKinds[i].isObject() : slotKinds[i] + " != " + componentKind;
                    }
                } else {
                    for (int i = 0; i < values.length; i++) {
                        assert slotKinds[i] == componentKind || componentKind.getBitCount() >= slotKinds[i].getBitCount() ||
                                        (componentKind == JavaKind.Int && slotKinds[i].getBitCount() >= JavaKind.Int.getBitCount()) : slotKinds[i] + " != " + componentKind;
                    }
                }
            }
        }
        return true;
    }

    /*
     * Customization point for subclasses. For example, Word types have a kind Object, but are
     * internally stored as a primitive value. We do not know about Word types here, but subclasses
     * do know.
     */
    protected JavaKind storageKind(JavaType type) {
        return type.getJavaKind();
    }

    protected LIRFrameState newLIRFrameState(LabelRef exceptionEdge, BytecodeFrame frame, VirtualObject[] virtualObjectsArray) {
        return new LIRFrameState(frame, virtualObjectsArray, exceptionEdge);
    }

    protected BytecodeFrame computeFrameForState(FrameState state) {
        try {
            assert state.bci != BytecodeFrame.INVALID_FRAMESTATE_BCI;
            assert state.bci != BytecodeFrame.UNKNOWN_BCI;
            assert state.bci != BytecodeFrame.BEFORE_BCI || state.locksSize() == 0;
            assert state.bci != BytecodeFrame.AFTER_BCI || state.locksSize() == 0;
            assert state.bci != BytecodeFrame.AFTER_EXCEPTION_BCI || state.locksSize() == 0;
            assert !(state.method().isSynchronized() && state.bci != BytecodeFrame.BEFORE_BCI && state.bci != BytecodeFrame.AFTER_BCI && state.bci != BytecodeFrame.AFTER_EXCEPTION_BCI) ||
                            state.locksSize() > 0;
            assert state.verify();

            int numLocals = state.localsSize();
            int numStack = state.stackSize();
            int numLocks = state.locksSize();

            JavaValue[] values = new JavaValue[numLocals + numStack + numLocks];
            JavaKind[] slotKinds = new JavaKind[numLocals + numStack];
            computeLocals(state, numLocals, values, slotKinds);
            computeStack(state, numLocals, numStack, values, slotKinds);
            computeLocks(state, values);

            BytecodeFrame caller = null;
            if (state.outerFrameState() != null) {
                caller = computeFrameForState(state.outerFrameState());
            }
            return new BytecodeFrame(caller, state.method(), state.bci, state.rethrowException(), state.duringCall(), values, slotKinds, numLocals, numStack, numLocks);
        } catch (JVMCIError e) {
            throw e.addContext("FrameState: ", state);
        }
    }

    protected void computeLocals(FrameState state, int numLocals, JavaValue[] values, JavaKind[] slotKinds) {
        for (int i = 0; i < numLocals; i++) {
            ValueNode local = state.localAt(i);
            values[i] = toJavaValue(local);
            slotKinds[i] = toSlotKind(local);
        }
    }

    protected void computeStack(FrameState state, int numLocals, int numStack, JavaValue[] values, JavaKind[] slotKinds) {
        for (int i = 0; i < numStack; i++) {
            ValueNode stack = state.stackAt(i);
            values[numLocals + i] = toJavaValue(stack);
            slotKinds[numLocals + i] = toSlotKind(stack);
        }
    }

    protected void computeLocks(FrameState state, JavaValue[] values) {
        for (int i = 0; i < state.locksSize(); i++) {
            values[state.localsSize() + state.stackSize() + i] = computeLockValue(state, i);
        }
    }

    protected JavaValue computeLockValue(FrameState state, int i) {
        return toJavaValue(state.lockAt(i));
    }

    private static final DebugMetric STATE_VIRTUAL_OBJECTS = Debug.metric("StateVirtualObjects");
    private static final DebugMetric STATE_ILLEGALS = Debug.metric("StateIllegals");
    private static final DebugMetric STATE_VARIABLES = Debug.metric("StateVariables");
    private static final DebugMetric STATE_CONSTANTS = Debug.metric("StateConstants");

    private static JavaKind toSlotKind(ValueNode value) {
        if (value == null) {
            return JavaKind.Illegal;
        } else {
            return value.getStackKind();
        }
    }

    protected JavaValue toJavaValue(ValueNode value) {
        try {
            if (value instanceof VirtualObjectNode) {
                VirtualObjectNode obj = (VirtualObjectNode) value;
                EscapeObjectState state = objectStates.get(obj);
                if (state == null && obj.entryCount() > 0) {
                    // null states occur for objects with 0 fields
                    throw new JVMCIError("no mapping found for virtual object %s", obj);
                }
                if (state instanceof MaterializedObjectState) {
                    return toJavaValue(((MaterializedObjectState) state).materializedValue());
                } else {
                    assert obj.entryCount() == 0 || state instanceof VirtualObjectState;
                    VirtualObject vobject = virtualObjects.get(obj);
                    if (vobject == null) {
                        vobject = VirtualObject.get(obj.type(), virtualObjects.size());
                        virtualObjects.put(obj, vobject);
                        pendingVirtualObjects.add(obj);
                    }
                    STATE_VIRTUAL_OBJECTS.increment();
                    return vobject;
                }
            } else {
                // Remove proxies from constants so the constant can be directly embedded.
                ValueNode unproxied = GraphUtil.unproxify(value);
                if (unproxied instanceof ConstantNode) {
                    STATE_CONSTANTS.increment();
                    return unproxied.asJavaConstant();

                } else if (value != null) {
                    STATE_VARIABLES.increment();
                    Value operand = nodeValueMap.operand(value);
                    assert operand != null && (operand instanceof Variable || operand instanceof JavaConstant) : operand + " for " + value;
                    return (JavaValue) operand;

                } else {
                    // return a dummy value because real value not needed
                    STATE_ILLEGALS.increment();
                    return Value.ILLEGAL;
                }
            }
        } catch (JVMCIError e) {
            throw e.addContext("toValue: ", value);
        }
    }
}
