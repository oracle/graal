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
package com.oracle.max.graal.compiler.gen;

import java.util.*;
import java.util.Map.Entry;

import com.oracle.max.cri.ci.*;
import com.oracle.max.graal.compiler.gen.LIRGenerator.LockScope;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.lir.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.virtual.*;

public class DebugInfoBuilder {
    private final NodeMap<CiValue> nodeOperands;

    public DebugInfoBuilder(NodeMap<CiValue> nodeOperands) {
        this.nodeOperands = nodeOperands;
    }


    private HashMap<VirtualObjectNode, CiVirtualObject> virtualObjects = new HashMap<>();

    public LIRDebugInfo build(FrameState topState, LockScope locks, List<CiStackSlot> pointerSlots, LabelRef exceptionEdge) {
        assert virtualObjects.size() == 0;
        CiFrame frame = computeFrameForState(topState, locks);

        CiVirtualObject[] virtualObjectsArray = null;
        if (virtualObjects.size() != 0) {
            // collect all VirtualObjectField instances:
            IdentityHashMap<VirtualObjectNode, VirtualObjectFieldNode> objectStates = new IdentityHashMap<>();
            FrameState current = topState;
            do {
                for (Node n : current.virtualObjectMappings()) {
                    VirtualObjectFieldNode field = (VirtualObjectFieldNode) n;
                    // null states occur for objects with 0 fields
                    if (field != null && !objectStates.containsKey(field.object())) {
                        objectStates.put(field.object(), field);
                    }
                }
                current = current.outerFrameState();
            } while (current != null);
            // fill in the CiVirtualObject values:
            // during this process new CiVirtualObjects might be discovered, so repeat until no more changes occur.
            boolean changed;
            do {
                changed = false;
                IdentityHashMap<VirtualObjectNode, CiVirtualObject> virtualObjectsCopy = new IdentityHashMap<>(virtualObjects);
                for (Entry<VirtualObjectNode, CiVirtualObject> entry : virtualObjectsCopy.entrySet()) {
                    if (entry.getValue().values() == null) {
                        VirtualObjectNode vobj = entry.getKey();
                        if (vobj instanceof BoxedVirtualObjectNode) {
                            BoxedVirtualObjectNode boxedVirtualObjectNode = (BoxedVirtualObjectNode) vobj;
                            entry.getValue().setValues(new CiValue[]{toCiValue(boxedVirtualObjectNode.getUnboxedValue())});
                        } else {
                            CiValue[] values = new CiValue[vobj.fieldsCount()];
                            entry.getValue().setValues(values);
                            if (values.length > 0) {
                                changed = true;
                                ValueNode currentField = objectStates.get(vobj);
                                assert currentField != null;
                                do {
                                    if (currentField instanceof VirtualObjectFieldNode) {
                                        int index = ((VirtualObjectFieldNode) currentField).index();
                                        if (values[index] == null) {
                                            values[index] = toCiValue(((VirtualObjectFieldNode) currentField).input());
                                        }
                                        currentField = ((VirtualObjectFieldNode) currentField).lastState();
                                    } else {
                                        assert currentField instanceof PhiNode : currentField;
                                        currentField = ((PhiNode) currentField).valueAt(0);
                                    }
                                } while (currentField != null);
                            }
                        }
                    }
                }
            } while (changed);

            virtualObjectsArray = virtualObjects.values().toArray(new CiVirtualObject[virtualObjects.size()]);
            virtualObjects.clear();
        }

        return new LIRDebugInfo(frame, virtualObjectsArray, pointerSlots, exceptionEdge);
    }

    private CiFrame computeFrameForState(FrameState state, LockScope locks) {
        int numLocks = (locks != null && locks.callerState == state.outerFrameState()) ? locks.stateDepth + 1 : 0;

        CiValue[] values = new CiValue[state.valuesSize() + numLocks];
        int valueIndex = 0;

        for (int i = 0; i < state.valuesSize(); i++) {
            values[valueIndex++] = toCiValue(state.valueAt(i));
        }

        LockScope nextLock = locks;
        for (int i = numLocks - 1; i >= 0; i--) {
            assert locks != null && nextLock.callerState == state.outerFrameState() && nextLock.stateDepth == i;

            CiValue owner = toCiValue(nextLock.monitor.object());
            CiValue lockData = nextLock.lockData;
            boolean eliminated = nextLock.monitor.eliminated();
            values[state.valuesSize() + nextLock.stateDepth] = new CiMonitorValue(owner, lockData, eliminated);

            nextLock = nextLock.outer;
        }

        CiFrame caller = null;
        if (state.outerFrameState() != null) {
            caller = computeFrameForState(state.outerFrameState(), nextLock);
        } else {
            if (nextLock != null) {
                throw new CiBailout("unbalanced monitors: found monitor for unknown frame");
            }
        }
        CiFrame frame = new CiFrame(caller, state.method(), state.bci, state.rethrowException(), state.duringCall(), values, state.localsSize(), state.stackSize(), numLocks);
        return frame;
    }

    private CiValue toCiValue(ValueNode value) {
        if (value instanceof VirtualObjectNode) {
            VirtualObjectNode obj = (VirtualObjectNode) value;
            CiVirtualObject ciObj = virtualObjects.get(value);
            if (ciObj == null) {
                ciObj = CiVirtualObject.get(obj.type(), null, virtualObjects.size());
                virtualObjects.put(obj, ciObj);
            }
            return ciObj;

        } else if (value instanceof ConstantNode) {
            return ((ConstantNode) value).value;

        } else if (value != null) {
            CiValue operand = nodeOperands.get(value);
            assert operand != null && (operand instanceof Variable || operand instanceof CiConstant);
            return operand;

        } else {
            // return a dummy value because real value not needed
            return CiValue.IllegalValue;
        }
    }
}
