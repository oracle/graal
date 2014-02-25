/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;
import java.util.Map.Entry;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.virtual.*;
import com.oracle.graal.virtual.nodes.*;

/**
 * Builds {@link LIRFrameState}s from {@link FrameState}s.
 */
public class DebugInfoBuilder {

    protected final NodeMap<Value> nodeOperands;

    public DebugInfoBuilder(NodeMap<Value> nodeOperands) {
        this.nodeOperands = nodeOperands;
    }

    protected HashMap<VirtualObjectNode, VirtualObject> virtualObjects = new HashMap<>();
    protected IdentityHashMap<VirtualObjectNode, EscapeObjectState> objectStates = new IdentityHashMap<>();

    public LIRFrameState build(FrameState topState, LabelRef exceptionEdge) {
        assert virtualObjects.size() == 0;
        assert objectStates.size() == 0;

        // collect all VirtualObjectField instances:
        FrameState current = topState;
        do {
            for (EscapeObjectState state : current.virtualObjectMappings()) {
                if (objectStates == null) {
                    objectStates = new IdentityHashMap<>();
                }
                if (!objectStates.containsKey(state.object())) {
                    if (!(state instanceof MaterializedObjectState) || ((MaterializedObjectState) state).materializedValue() != state.object()) {
                        objectStates.put(state.object(), state);
                    }
                }
            }
            current = current.outerFrameState();
        } while (current != null);

        BytecodeFrame frame = computeFrameForState(topState);

        VirtualObject[] virtualObjectsArray = null;
        if (virtualObjects.size() != 0) {
            // fill in the VirtualObject values:
            // during this process new VirtualObjects might be discovered, so repeat until no more
            // changes occur.
            boolean changed;
            do {
                changed = false;
                IdentityHashMap<VirtualObjectNode, VirtualObject> virtualObjectsCopy = new IdentityHashMap<>(virtualObjects);
                for (Entry<VirtualObjectNode, VirtualObject> entry : virtualObjectsCopy.entrySet()) {
                    if (entry.getValue().getValues() == null) {
                        VirtualObjectNode vobj = entry.getKey();
                        Value[] values = new Value[vobj.entryCount()];
                        if (values.length > 0) {
                            changed = true;
                            VirtualObjectState currentField = (VirtualObjectState) objectStates.get(vobj);
                            assert currentField != null;
                            int pos = 0;
                            for (int i = 0; i < vobj.entryCount(); i++) {
                                if (!currentField.fieldValues().get(i).isConstant() || currentField.fieldValues().get(i).asConstant().getKind() != Kind.Illegal) {
                                    values[pos++] = toValue(currentField.fieldValues().get(i));
                                } else {
                                    assert currentField.fieldValues().get(i - 1).kind() == Kind.Double || currentField.fieldValues().get(i - 1).kind() == Kind.Long : vobj + " " + i + " " +
                                                    currentField.fieldValues().get(i - 1);
                                }
                            }
                            if (pos != vobj.entryCount()) {
                                values = Arrays.copyOf(values, pos);
                            }
                        }
                        entry.getValue().setValues(values);
                    }
                }
            } while (changed);

            virtualObjectsArray = virtualObjects.values().toArray(new VirtualObject[virtualObjects.size()]);
            virtualObjects.clear();
        }
        objectStates.clear();

        return newLIRFrameState(exceptionEdge, frame, virtualObjectsArray);
    }

    protected LIRFrameState newLIRFrameState(LabelRef exceptionEdge, BytecodeFrame frame, VirtualObject[] virtualObjectsArray) {
        return new LIRFrameState(frame, virtualObjectsArray, exceptionEdge);
    }

    protected BytecodeFrame computeFrameForState(FrameState state) {
        int numLocals = state.localsSize();
        int numStack = state.stackSize();
        int numLocks = state.locksSize();

        Value[] values = new Value[numLocals + numStack + numLocks];
        computeLocals(state, numLocals, values);
        computeStack(state, numLocals, numStack, values);
        computeLocks(state, values);

        BytecodeFrame caller = null;
        if (state.outerFrameState() != null) {
            caller = computeFrameForState(state.outerFrameState());
        }
        assert state.bci >= FrameState.BEFORE_BCI : "bci == " + state.bci;
        return new BytecodeFrame(caller, state.method(), state.bci, state.rethrowException(), state.duringCall(), values, numLocals, numStack, numLocks);
    }

    protected void computeLocals(FrameState state, int numLocals, Value[] values) {
        for (int i = 0; i < numLocals; i++) {
            values[i] = computeLocalValue(state, i);
        }
    }

    protected Value computeLocalValue(FrameState state, int i) {
        return toValue(state.localAt(i));
    }

    protected void computeStack(FrameState state, int numLocals, int numStack, Value[] values) {
        for (int i = 0; i < numStack; i++) {
            values[numLocals + i] = computeStackValue(state, i);
        }
    }

    protected Value computeStackValue(FrameState state, int i) {
        return toValue(state.stackAt(i));
    }

    protected void computeLocks(FrameState state, Value[] values) {
        for (int i = 0; i < state.locksSize(); i++) {
            values[state.localsSize() + state.stackSize() + i] = computeLockValue(state, i);
        }
    }

    protected Value computeLockValue(FrameState state, int i) {
        return toValue(state.lockAt(i));
    }

    private static final DebugMetric STATE_VIRTUAL_OBJECTS = Debug.metric("StateVirtualObjects");
    private static final DebugMetric STATE_ILLEGALS = Debug.metric("StateIllegals");
    private static final DebugMetric STATE_VARIABLES = Debug.metric("StateVariables");
    private static final DebugMetric STATE_CONSTANTS = Debug.metric("StateConstants");

    protected Value toValue(ValueNode value) {
        if (value instanceof VirtualObjectNode) {
            VirtualObjectNode obj = (VirtualObjectNode) value;
            EscapeObjectState state = objectStates.get(obj);
            if (state == null && obj.entryCount() > 0) {
                // null states occur for objects with 0 fields
                throw new GraalInternalError("no mapping found for virtual object %s", obj);
            }
            if (state instanceof MaterializedObjectState) {
                return toValue(((MaterializedObjectState) state).materializedValue());
            } else {
                assert obj.entryCount() == 0 || state instanceof VirtualObjectState;
                VirtualObject vobject = virtualObjects.get(value);
                if (vobject == null) {
                    vobject = VirtualObject.get(obj.type(), null, virtualObjects.size());
                    virtualObjects.put(obj, vobject);
                }
                STATE_VIRTUAL_OBJECTS.increment();
                return vobject;
            }
        } else if (value instanceof ConstantNode) {
            STATE_CONSTANTS.increment();
            return ((ConstantNode) value).getValue();

        } else if (value != null) {
            STATE_VARIABLES.increment();
            Value operand = nodeOperands.get(value);
            assert operand != null && (operand instanceof Variable || operand instanceof Constant) : operand + " for " + value;
            return operand;

        } else {
            // return a dummy value because real value not needed
            STATE_ILLEGALS.increment();
            return Value.ILLEGAL;
        }
    }
}
