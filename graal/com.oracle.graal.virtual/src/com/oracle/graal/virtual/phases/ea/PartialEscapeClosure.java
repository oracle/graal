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
package com.oracle.graal.virtual.phases.ea;

import static com.oracle.graal.virtual.phases.ea.PartialEscapeAnalysisPhase.*;

import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.meta.ResolvedJavaType.Representation;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.PhiNode.PhiType;
import com.oracle.graal.nodes.VirtualState.NodeClosure;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.virtual.*;
import com.oracle.graal.phases.graph.*;
import com.oracle.graal.phases.graph.ReentrantBlockIterator.BlockIteratorClosure;
import com.oracle.graal.phases.graph.ReentrantBlockIterator.LoopInfo;
import com.oracle.graal.phases.schedule.*;
import com.oracle.graal.virtual.nodes.*;

class PartialEscapeClosure extends BlockIteratorClosure<BlockState> {


    private static final DebugMetric metricAllocationRemoved = Debug.metric("AllocationRemoved ");

    private final GraphEffectList effects = new GraphEffectList();
    private final HashSet<VirtualObjectNode> reusedVirtualObjects = new HashSet<>();
    private int virtualIds = 0;

    private final NodeBitMap usages;
    private final SchedulePhase schedule;
    private final MetaAccessProvider runtime;

    public PartialEscapeClosure(NodeBitMap usages, SchedulePhase schedule, MetaAccessProvider runtime) {
        this.usages = usages;
        this.schedule = schedule;
        this.runtime = runtime;
    }

    public GraphEffectList getEffects() {
        return effects;
    }

    public int getVirtualIdCount() {
        return virtualIds;
    }

    @Override
    protected void processBlock(Block block, BlockState state) {
        trace("\nBlock: %s (", block);
        List<ScheduledNode> nodeList = schedule.getBlockToNodesMap().get(block);

        FixedWithNextNode lastFixedNode = null;
        for (Node node : nodeList) {
            EscapeOp op = null;
            if (node instanceof EscapeAnalyzable) {
                op = ((EscapeAnalyzable) node).getEscapeOp();
            }

            if (op != null) {
                trace("{{%s}} ", node);
                VirtualObjectNode virtualObject = op.virtualObject(virtualIds);
                if (virtualObject.isAlive()) {
                    reusedVirtualObjects.add(virtualObject);
                    state.addAndMarkAlias(virtualObject, virtualObject, usages);
                } else {
                    effects.addFloatingNode(virtualObject);
                }
                ValueNode[] fieldState = op.fieldState();
                for (int i = 0; i < fieldState.length; i++) {
                    fieldState[i] = state.getScalarAlias(fieldState[i]);
                }
                state.addObject(virtualObject, new ObjectState(virtualObject, fieldState, 0));
                state.addAndMarkAlias(virtualObject, (ValueNode) node, usages);
                effects.deleteFixedNode((FixedWithNextNode) node);
                virtualIds++;
                metricAllocationRemoved.increment();
            } else {
                if (usages.isMarked(node)) {
                    trace("[[%s]] ", node);
                    processNode((ValueNode) node, lastFixedNode == null ? null : lastFixedNode.next(), state);
                } else {
                    trace("%s ", node);
                }
            }

            if (node instanceof FixedWithNextNode && node.isAlive()) {
                lastFixedNode = (FixedWithNextNode) node;
            }
        }
        trace(")\n    end state: %s\n", state);
    }

    private void processNode(final ValueNode node, FixedNode insertBefore, final BlockState state) {
        boolean usageFound = false;
        if (node instanceof PiNode || node instanceof ValueProxyNode) {
            ValueNode value = node instanceof PiNode ? ((PiNode) node).object() : ((ValueProxyNode) node).value();
            ObjectState obj = state.getObjectState(value);
            if (obj != null) {
                if (obj.isVirtual()) {
                    state.addAndMarkAlias(obj.virtual, node, usages);
                } else {
                    effects.replaceFirstInput(node, value, obj.getMaterializedValue());
                }
                usageFound = true;
            }
        } else if (node instanceof CheckCastNode) {
            CheckCastNode x = (CheckCastNode) node;
            ObjectState obj = state.getObjectState(x.object());
            if (obj != null) {
                if (obj.isVirtual()) {
                    if (obj.virtual.type().isSubtypeOf(x.type())) {
                        state.addAndMarkAlias(obj.virtual, x, usages);
                        effects.deleteFixedNode(x);
                    } else {
                        replaceWithMaterialized(x.object(), x, state, obj);
                    }
                } else {
                    effects.replaceFirstInput(x, x.object(), obj.getMaterializedValue());
                }
                usageFound = true;
            }
        } else if (node instanceof IsNullNode) {
            IsNullNode x = (IsNullNode) node;
            if (state.getObjectState(x.object()) != null) {
                replaceAtUsages(state, x, ConstantNode.forBoolean(false, node.graph()));
                usageFound = true;
            }
        } else if (node instanceof AccessMonitorNode) {
            AccessMonitorNode x = (AccessMonitorNode) node;
            ObjectState obj = state.getObjectState(x.object());
            if (obj != null) {
                Debug.log("monitor operation %s on %s\n", x, obj.virtual);
                if (node instanceof MonitorEnterNode) {
                    obj.incLockCount();
                } else {
                    assert node instanceof MonitorExitNode;
                    obj.decLockCount();
                }
                if (obj.isVirtual()) {
                    effects.replaceFirstInput(node, x.object(), obj.virtual);
                    effects.eliminateMonitor(x);
                } else {
                    effects.replaceFirstInput(node, x.object(), obj.getMaterializedValue());
                }
                usageFound = true;
            }
        } else if (node instanceof CyclicMaterializeStoreNode) {
            CyclicMaterializeStoreNode x = (CyclicMaterializeStoreNode) node;
            ObjectState obj = state.getObjectState(x.object());
            if (obj != null) {
                if (obj.virtual instanceof VirtualArrayNode) {
                    obj.setEntry(x.targetIndex(), x.value());
                } else {
                    VirtualInstanceNode instance = (VirtualInstanceNode) obj.virtual;
                    obj.setEntry(instance.fieldIndex(x.targetField()), x.value());
                }
                effects.deleteFixedNode(x);
                usageFound = true;
            }
        } else if (node instanceof LoadFieldNode) {
            LoadFieldNode x = (LoadFieldNode) node;
            ObjectState obj = state.getObjectState(x.object());
            if (obj != null) {
                VirtualInstanceNode virtual = (VirtualInstanceNode) obj.virtual;
                int fieldIndex = virtual.fieldIndex(x.field());
                if (fieldIndex == -1) {
                    // the field does not exist in the virtual object
                    ensureMaterialized(state, obj, x);
                }
                if (obj.isVirtual()) {
                    ValueNode result = obj.getEntry(fieldIndex);
                    ObjectState resultObj = state.getObjectState(result);
                    if (resultObj != null) {
                        state.addAndMarkAlias(resultObj.virtual, x, usages);
                    } else {
                        replaceAtUsages(state, x, result);
                    }
                    effects.deleteFixedNode(x);
                } else {
                    effects.replaceFirstInput(x, x.object(), obj.getMaterializedValue());
                }
                usageFound = true;
            }
        } else if (node instanceof StoreFieldNode) {
            StoreFieldNode x = (StoreFieldNode) node;
            ValueNode object = x.object();
            ValueNode value = x.value();
            ObjectState obj = state.getObjectState(object);
            if (obj != null) {
                VirtualInstanceNode virtual = (VirtualInstanceNode) obj.virtual;
                int fieldIndex = virtual.fieldIndex(x.field());
                if (fieldIndex == -1) {
                    // the field does not exist in the virtual object
                    ensureMaterialized(state, obj, x);
                }
                if (obj.isVirtual()) {
                    obj.setEntry(fieldIndex, state.getScalarAlias(value));
                    effects.deleteFixedNode(x);
                } else {
                    effects.replaceFirstInput(x, object, obj.getMaterializedValue());
                    ObjectState valueObj = state.getObjectState(value);
                    if (valueObj != null) {
                        replaceWithMaterialized(value, x, state, valueObj);
                    }
                }
                usageFound = true;
            } else {
                ObjectState valueObj = state.getObjectState(value);
                if (valueObj != null) {
                    replaceWithMaterialized(value, x, state, valueObj);
                    usageFound = true;
                }
            }
        } else if (node instanceof LoadIndexedNode) {
            LoadIndexedNode x = (LoadIndexedNode) node;
            ValueNode array = x.array();
            ObjectState arrayObj = state.getObjectState(array);
            if (arrayObj != null) {
                if (arrayObj.isVirtual()) {
                    ValueNode indexValue = state.getScalarAlias(x.index());
                    int index = indexValue.isConstant() ? indexValue.asConstant().asInt() : -1;
                    if (index < 0 || index >= arrayObj.getEntries().length) {
                        // out of bounds or not constant
                        replaceWithMaterialized(array, x, state, arrayObj);
                    } else {
                        ValueNode result = arrayObj.getEntry(index);
                        ObjectState resultObj = state.getObjectState(result);
                        if (resultObj != null) {
                            state.addAndMarkAlias(resultObj.virtual, x, usages);
                        } else {
                            replaceAtUsages(state, x, result);
                        }
                        effects.deleteFixedNode(x);
                    }
                } else {
                    effects.replaceFirstInput(x, array, arrayObj.getMaterializedValue());
                }
                usageFound = true;
            }
        } else if (node instanceof StoreIndexedNode) {
            StoreIndexedNode x = (StoreIndexedNode) node;
            ValueNode array = x.array();
            ValueNode value = x.value();
            ObjectState arrayObj = state.getObjectState(array);
            ObjectState valueObj = state.getObjectState(value);

            if (arrayObj != null) {
                if (arrayObj.isVirtual()) {
                    ValueNode indexValue = state.getScalarAlias(x.index());
                    int index = indexValue.isConstant() ? indexValue.asConstant().asInt() : -1;
                    if (index < 0 || index >= arrayObj.getEntries().length) {
                        // out of bounds or not constant
                        replaceWithMaterialized(array, x, state, arrayObj);
                        if (valueObj != null) {
                            replaceWithMaterialized(value, x, state, valueObj);
                        }
                    } else {
                        arrayObj.setEntry(index, state.getScalarAlias(value));
                        effects.deleteFixedNode(x);
                    }
                } else {
                    effects.replaceFirstInput(x, array, arrayObj.getMaterializedValue());
                    if (valueObj != null) {
                        replaceWithMaterialized(value, x, state, valueObj);
                    }
                }
                usageFound = true;
            } else {
                if (valueObj != null) {
                    replaceWithMaterialized(value, x, state, valueObj);
                    usageFound = true;
                }
            }
        } else if (node instanceof RegisterFinalizerNode) {
            RegisterFinalizerNode x = (RegisterFinalizerNode) node;
            ObjectState obj = state.getObjectState(x.object());
            if (obj != null) {
                replaceWithMaterialized(x.object(), x, state, obj);
                usageFound = true;
            }
        } else if (node instanceof ArrayLengthNode) {
            ArrayLengthNode x = (ArrayLengthNode) node;
            ObjectState obj = state.getObjectState(x.array());
            if (obj != null) {
                replaceAtUsages(state, x, ConstantNode.forInt(((VirtualArrayNode) obj.virtual).entryCount(), node.graph()));
                effects.deleteFixedNode(x);
                usageFound = true;
            }
        } else if (node instanceof LoadHubNode) {
            LoadHubNode x = (LoadHubNode) node;
            ObjectState obj = state.getObjectState(x.object());
            if (obj != null) {
                replaceAtUsages(state, x, ConstantNode.forConstant(obj.virtual.type().getEncoding(Representation.ObjectHub), runtime, node.graph()));
                effects.deleteFixedNode(x);
                usageFound = true;
            }
        } else if (node instanceof ReturnNode) {
            ReturnNode x = (ReturnNode) node;
            ObjectState obj = state.getObjectState(x.result());
            if (obj != null) {
                replaceWithMaterialized(x.result(), x, state, obj);
                usageFound = true;
            }
        } else if (node instanceof MethodCallTargetNode) {
            for (ValueNode argument : ((MethodCallTargetNode) node).arguments()) {
                ObjectState obj = state.getObjectState(argument);
                if (obj != null) {
                    replaceWithMaterialized(argument, node, insertBefore, state, obj);
                    usageFound = true;
                }
            }
        } else if (node instanceof ObjectEqualsNode) {
            ObjectEqualsNode x = (ObjectEqualsNode) node;
            ObjectState xObj = state.getObjectState(x.x());
            ObjectState yObj = state.getObjectState(x.y());
            boolean xVirtual = xObj != null && xObj.isVirtual();
            boolean yVirtual = yObj != null && yObj.isVirtual();

            if (xVirtual ^ yVirtual) {
                // one of them is virtual: they can never be the same objects
                replaceAtUsages(state, x, ConstantNode.forBoolean(false, node.graph()));
                usageFound = true;
            } else if (xVirtual && yVirtual) {
                // both are virtual: check if they refer to the same object
                replaceAtUsages(state, x, ConstantNode.forBoolean(xObj == yObj, node.graph()));
                usageFound = true;
            } else {
                if (xObj != null || yObj != null) {
                    if (xObj != null) {
                        assert !xObj.isVirtual();
                        effects.replaceFirstInput(x, x.x(), xObj.getMaterializedValue());
                    }
                    if (yObj != null) {
                        assert !yObj.isVirtual();
                        effects.replaceFirstInput(x, x.y(), yObj.getMaterializedValue());
                    }
                    usageFound = true;
                }
            }
        } else if (node instanceof MergeNode) {
            usageFound = true;
        } else if (node instanceof UnsafeLoadNode || node instanceof UnsafeStoreNode || node instanceof CompareAndSwapNode || node instanceof SafeReadNode) {
            for (ValueNode input : node.inputs().filter(ValueNode.class)) {
                ObjectState obj = state.getObjectState(input);
                if (obj != null) {
                    replaceWithMaterialized(input, node, insertBefore, state, obj);
                    usageFound = true;
                }
            }
        }
        if (node.isAlive() && node instanceof StateSplit) {
            StateSplit split = (StateSplit) node;
            FrameState stateAfter = split.stateAfter();
            if (stateAfter != null) {
                if (stateAfter.usages().size() > 1) {
                    stateAfter = (FrameState) stateAfter.copyWithInputs();
                    split.setStateAfter(stateAfter);
                }
                final HashSet<ObjectState> virtual = new HashSet<>();
                stateAfter.applyToNonVirtual(new NodeClosure<ValueNode>() {

                    @Override
                    public void apply(Node usage, ValueNode value) {
                        ObjectState valueObj = state.getObjectState(value);
                        if (valueObj != null) {
                            virtual.add(valueObj);
                            effects.replaceFirstInput(usage, value, valueObj.virtual);
                        } else if (value instanceof VirtualObjectNode) {
                            ObjectState virtualObj = null;
                            for (ObjectState obj : state.getStates()) {
                                if (value == obj.virtual) {
                                    virtualObj = obj;
                                    break;
                                }
                            }
                            if (virtualObj != null) {
                                virtual.add(virtualObj);
                            }
                        }
                    }
                });
                for (ObjectState obj : state.getStates()) {
                    if (obj.isVirtual() && obj.getLockCount() > 0) {
                        virtual.add(obj);
                    }
                }

                ArrayDeque<ObjectState> queue = new ArrayDeque<>(virtual);
                while (!queue.isEmpty()) {
                    ObjectState obj = queue.removeLast();
                    if (obj.isVirtual()) {
                        for (ValueNode field : obj.getEntries()) {
                            ObjectState fieldObj = state.getObjectState(field);
                            if (fieldObj != null) {
                                if (fieldObj.isVirtual() && !virtual.contains(fieldObj)) {
                                    virtual.add(fieldObj);
                                    queue.addLast(fieldObj);
                                }
                            }
                        }
                    }
                }
                for (ObjectState obj : virtual) {
                    EscapeObjectState v;
                    if (obj.isVirtual()) {
                        ValueNode[] fieldState = obj.getEntries().clone();
                        for (int i = 0; i < fieldState.length; i++) {
                            ObjectState valueObj = state.getObjectState(fieldState[i]);
                            if (valueObj != null) {
                                if (valueObj.isVirtual()) {
                                    fieldState[i] = valueObj.virtual;
                                } else {
                                    fieldState[i] = valueObj.getMaterializedValue();
                                }
                            }
                        }
                        v = new VirtualObjectState(obj.virtual, fieldState);
                    } else {
                        v = new MaterializedObjectState(obj.virtual, obj.getMaterializedValue());
                    }
                    effects.addVirtualMapping(stateAfter, v, reusedVirtualObjects);
                }
            }
            usageFound = true;
        }
        if (!usageFound) {
            for (ValueNode input : node.inputs().filter(ValueNode.class)) {
                ObjectState obj = state.getObjectState(input);
                if (obj != null) {
                    replaceWithMaterialized(input, node, insertBefore, state, obj);
                    usageFound = true;
                }
            }
            Debug.log("unexpected usage of %s: %s\n", node, node.inputs().snapshot());
        }
    }

    private void replaceAtUsages(final BlockState state, ValueNode x, ValueNode value) {
        effects.replaceAtUsages(x, value);
        state.addScalarAlias(x, value);
    }

    private void ensureMaterialized(BlockState state, ObjectState obj, FixedNode materializeBefore) {
        assert obj != null;
        if (obj.isVirtual()) {
            state.materializeBefore(materializeBefore, obj.virtual, effects);
        }
        assert !obj.isVirtual();
    }

    private void replaceWithMaterialized(ValueNode value, FixedNode usage, BlockState state, ObjectState obj) {
        ensureMaterialized(state, obj, usage);
        effects.replaceFirstInput(usage, value, obj.getMaterializedValue());
    }

    private void replaceWithMaterialized(ValueNode value, Node usage, FixedNode materializeBefore, BlockState state, ObjectState obj) {
        ensureMaterialized(state, obj, materializeBefore);
        effects.replaceFirstInput(usage, value, obj.getMaterializedValue());
    }

    @Override
    protected BlockState merge(MergeNode merge, List<BlockState> states) {

        BlockState newState = BlockState.meetAliases(states);

        // Iterative processing:
        // Merging the materialized/virtual state of virtual objects can lead to new materializations, which can
        // lead to new materializations because of phis, and so on.

        boolean materialized;
        do {
            materialized = false;
            // use a hash set to make the values distinct...
            for (VirtualObjectNode object : newState.getVirtualObjects()) {
                ObjectState resultState = newState.getObjectStateOptional(object);
                if (resultState == null || resultState.isVirtual()) {
                    int virtual = 0;
                    ObjectState startObj = states.get(0).getObjectState(object);
                    int lockCount = startObj.getLockCount();
                    ValueNode singleValue = startObj.isVirtual() ? null : startObj.getMaterializedValue();
                    for (BlockState state : states) {
                        ObjectState obj = state.getObjectState(object);
                        if (obj.isVirtual()) {
                            virtual++;
                            singleValue = null;
                        } else {
                            if (obj.getMaterializedValue() != singleValue) {
                                singleValue = null;
                            }
                        }
                        assert obj.getLockCount() == lockCount : "mismatching lock counts";
                    }

                    if (virtual < states.size()) {
                        if (singleValue == null) {
                            PhiNode materializedValuePhi = new PhiNode(Kind.Object, merge);
                            effects.addFloatingNode(materializedValuePhi);
                            for (int i = 0; i < states.size(); i++) {
                                BlockState state = states.get(i);
                                ObjectState obj = state.getObjectState(object);
                                materialized |= obj.isVirtual();
                                ensureMaterialized(state, obj, merge.forwardEndAt(i));
                                effects.addPhiInput(materializedValuePhi, obj.getMaterializedValue());
                            }
                            newState.addObject(object, new ObjectState(object, materializedValuePhi, lockCount));
                        } else {
                            newState.addObject(object, new ObjectState(object, singleValue, lockCount));
                        }
                    } else {
                        assert virtual == states.size();
                        ValueNode[] values = startObj.getEntries().clone();
                        PhiNode[] phis = new PhiNode[values.length];
                        int mismatch = 0;
                        for (int i = 1; i < states.size(); i++) {
                            BlockState state = states.get(i);
                            ValueNode[] fields = state.getObjectState(object).getEntries();
                            for (int index = 0; index < values.length; index++) {
                                if (phis[index] == null && values[index] != fields[index]) {
                                    mismatch++;
                                    phis[index] = new PhiNode(values[index].kind(), merge);
                                    effects.addFloatingNode(phis[index]);
                                }
                            }
                        }
                        if (mismatch > 0) {
                            for (int i = 0; i < states.size(); i++) {
                                BlockState state = states.get(i);
                                ValueNode[] fields = state.getObjectState(object).getEntries();
                                for (int index = 0; index < values.length; index++) {
                                    if (phis[index] != null) {
                                        ObjectState obj = state.getObjectState(fields[index]);
                                        if (obj != null) {
                                            materialized |= obj.isVirtual();
                                            ensureMaterialized(state, obj, merge.forwardEndAt(i));
                                            fields[index] = obj.getMaterializedValue();
                                        }
                                        effects.addPhiInput(phis[index], fields[index]);
                                    }
                                }
                            }
                            for (int index = 0; index < values.length; index++) {
                                if (phis[index] != null) {
                                    values[index] = phis[index];
                                }
                            }
                        }
                        newState.addObject(object, new ObjectState(object, values, lockCount));
                    }
                }
            }

            for (PhiNode phi : merge.phis().snapshot()) {
                if (usages.isMarked(phi) && phi.type() == PhiType.Value) {
                    materialized |= processPhi(newState, merge, phi, states);
                }
            }
        } while (materialized);

        return newState;
    }

    private boolean processPhi(BlockState newState, MergeNode merge, PhiNode phi, List<BlockState> states) {
        assert states.size() == phi.valueCount();
        int virtualInputs = 0;
        boolean materialized = false;
        VirtualObjectNode sameObject = null;
        ResolvedJavaType sameType = null;
        int sameEntryCount = -1;
        for (int i = 0; i < phi.valueCount(); i++) {
            ValueNode value = phi.valueAt(i);
            ObjectState obj = states.get(i).getObjectState(value);
            if (obj != null) {
                if (obj.isVirtual()) {
                    virtualInputs++;
                    if (i == 0) {
                        sameObject = obj.virtual;
                        sameType = obj.virtual.type();
                        sameEntryCount = obj.virtual.entryCount();
                    } else {
                        if (sameObject != obj.virtual) {
                            sameObject = null;
                        }
                        if (sameType != obj.virtual.type()) {
                            sameType = null;
                        }
                        if (sameEntryCount != obj.virtual.entryCount()) {
                            sameEntryCount = -1;
                        }
                    }
                } else {
                    effects.setPhiInput(phi, i, obj.getMaterializedValue());
                }
            }
        }
        boolean materialize = false;
        if (virtualInputs == 0) {
            // nothing to do...
        } else if (virtualInputs == phi.valueCount()) {
            if (sameObject != null) {
                newState.addAndMarkAlias(sameObject, phi, usages);
            } else if (sameType != null && sameEntryCount != -1) {
                materialize = true;
                // throw new GraalInternalError("merge required for %s", sameType);
            } else {
                materialize = true;
            }
        } else {
            materialize = true;
        }

        if (materialize) {
            for (int i = 0; i < phi.valueCount(); i++) {
                ValueNode value = phi.valueAt(i);
                ObjectState obj = states.get(i).getObjectState(value);
                if (obj != null) {
                    materialized |= obj.isVirtual();
                    replaceWithMaterialized(value, phi, merge.forwardEndAt(i), states.get(i), obj);
                }
            }
        }
        return materialized;
    }

    @Override
    protected BlockState afterSplit(FixedNode node, BlockState oldState) {
        return oldState.cloneState();
    }

    @Override
    protected List<BlockState> processLoop(Loop loop, BlockState initialState) {
        GraphEffectList successEffects = new GraphEffectList();
        HashSet<PhiDesc> phis = new HashSet<>();
        for (int iteration = 0; iteration < 10; iteration++) {
            BlockState state = initialState.cloneState();
            int checkpoint = effects.checkpoint();

            for (PhiDesc desc : phis) {
                ObjectState obj = state.getObjectState(desc.virtualObject);
                if (obj.isVirtual()) {
                    ValueNode value = obj.getEntry(desc.fieldIndex);
                    ObjectState valueObj = state.getObjectState(value);
                    if (valueObj != null) {
                        assert !valueObj.isVirtual();
                        value = valueObj.getMaterializedValue();
                    }

                    PhiNode phiNode = new PhiNode(value.kind(), loop.loopBegin());
                    effects.addFloatingNode(phiNode);
                    effects.addPhiInput(phiNode, value);
                    obj.setEntry(desc.fieldIndex, phiNode);
                }
            }

            for (PhiNode phi : loop.loopBegin().phis()) {
                if (usages.isMarked(phi) && phi.type() == PhiType.Value) {
                    ObjectState initialObj = initialState.getObjectState(phi.valueAt(0));
                    if (initialObj != null) {
                        if (initialObj.isVirtual()) {
                            state.addAndMarkAlias(initialObj.virtual, phi, usages);
                        } else {
                            successEffects.setPhiInput(phi, 0, initialObj.getMaterializedValue());
                        }
                    }
                }
            }

            effects.incLevel();
            LoopInfo<BlockState> info = ReentrantBlockIterator.processLoop(this, loop, state.cloneState());

            List<BlockState> loopEndStates = info.endStates;
            List<Block> predecessors = loop.header.getPredecessors();
            HashSet<VirtualObjectNode> additionalMaterializations = new HashSet<>();
            int oldPhiCount = phis.size();
            for (int i = 1; i < predecessors.size(); i++) {
                processLoopEnd(loop.loopBegin(), (LoopEndNode) predecessors.get(i).getEndNode(), state, loopEndStates.get(i - 1), successEffects, additionalMaterializations, phis);
            }
            if (additionalMaterializations.isEmpty() && oldPhiCount == phis.size()) {
                effects.addAll(successEffects);

                assert info.exitStates.size() == loop.exits.size();
                for (int i = 0; i < loop.exits.size(); i++) {
                    BlockState exitState = info.exitStates.get(i);
                    assert exitState != null : "no loop exit state at " + loop.exits.get(i) + " / " + loop.header;
                    processLoopExit((LoopExitNode) loop.exits.get(i).getBeginNode(), state, exitState);
                }

                effects.decLevel();
                return info.exitStates;
            } else {
                successEffects.clear();
                effects.backtrack(checkpoint);
                effects.decLevel();
                for (VirtualObjectNode virtualObject : additionalMaterializations) {
                    ObjectState obj = initialState.getObjectState(virtualObject);
                    if (obj.isVirtual()) {
                        initialState.materializeBefore(loop.loopBegin().forwardEnd(), virtualObject, effects);
                    }
                }
            }
        }

        throw new GraalInternalError("too many iterations at %s", loop);
    }

    private void processLoopExit(LoopExitNode exitNode, BlockState initialState, BlockState exitState) {
        HashMap<VirtualObjectNode, ValueProxyNode> proxies = new HashMap<>();

        for (ValueProxyNode proxy : exitNode.proxies()) {
            ObjectState obj = exitState.getObjectState(proxy.value());
            if (obj != null) {
                proxies.put(obj.virtual, proxy);
            }
        }
        for (ObjectState obj : exitState.getStates()) {
            ObjectState initialObj = initialState.getObjectStateOptional(obj.virtual);
            if (obj.isVirtual()) {
                for (int i = 0; i < obj.getEntries().length; i++) {
                    ValueNode value = obj.getEntry(i);
                    ObjectState valueObj = exitState.getObjectState(value);
                    if (valueObj == null) {
                        if ((value instanceof PhiNode && ((PhiNode) value).merge() == exitNode.loopBegin()) || initialObj == null || !initialObj.isVirtual() || initialObj.getEntry(i) != value) {
                            ValueProxyNode proxy = new ValueProxyNode(value, exitNode, PhiType.Value);
                            obj.setEntry(i, proxy);
                            effects.addFloatingNode(proxy);
                        }
                    }
                }
            } else {
                if (initialObj == null || initialObj.isVirtual()) {
                    ValueProxyNode proxy = proxies.get(obj.virtual);
                    if (proxy == null) {
                        proxy = new ValueProxyNode(obj.getMaterializedValue(), exitNode, PhiType.Value);
                        effects.addFloatingNode(proxy);
                    } else {
                        effects.replaceFirstInput(proxy, proxy.value(), obj.getMaterializedValue());
                        // nothing to do - will be handled in processNode
                    }
                    obj.updateMaterializedValue(proxy);
                } else {
                    assert initialObj.getMaterializedValue() == obj.getMaterializedValue() : "materialized value is not allowed to change within loops: " + initialObj.getMaterializedValue() +
                                    " vs. " + obj.getMaterializedValue();
                }
            }
        }
    }

    private final class PhiDesc {

        public final VirtualObjectNode virtualObject;
        public final int fieldIndex;

        public PhiDesc(VirtualObjectNode virtualObject, int fieldIndex) {
            this.virtualObject = virtualObject;
            this.fieldIndex = fieldIndex;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = fieldIndex;
            result = prime * result + ((virtualObject == null) ? 0 : virtualObject.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            PhiDesc other = (PhiDesc) obj;
            return virtualObject == other.virtualObject && fieldIndex == other.fieldIndex;
        }
    }

    private void processLoopEnd(LoopBeginNode loopBegin, LoopEndNode loopEnd, BlockState initialState, BlockState loopEndState, GraphEffectList successEffects,
                    Set<VirtualObjectNode> additionalMaterializations, HashSet<PhiDesc> phis) {
        assert loopEnd.loopBegin() == loopBegin;
        boolean materialized;
        do {
            materialized = false;
            for (ObjectState state : initialState.getStates()) {
                ObjectState endState = loopEndState.getObjectState(state.virtual);
                if (state.isVirtual()) {
                    if (endState.isVirtual()) {
                        assert state.getEntries().length == endState.getEntries().length;
                        for (int i = 0; endState.isVirtual() && i < state.getEntries().length; i++) {
                            ValueNode value = state.getEntry(i);
                            ValueNode endValue = endState.getEntry(i);
                            ObjectState valueObj = initialState.getObjectState(value);
                            ObjectState endValueObj = loopEndState.getObjectState(endValue);

                            if (valueObj != null) {
                                if (valueObj.isVirtual()) {
                                    if (endValueObj == null || !endValueObj.isVirtual() || valueObj.virtual != endValueObj.virtual) {
                                        additionalMaterializations.add(valueObj.virtual);
                                    } else {
                                        // endValue is also virtual and refers to the same virtual object, so we're
                                        // good.
                                    }
                                }
                            } else {
                                if (value instanceof PhiNode && ((PhiNode) value).merge() == loopBegin) {
                                    if (endValueObj != null) {
                                        if (endValueObj.isVirtual()) {
                                            loopEndState.materializeBefore(loopEnd, endValueObj.virtual, successEffects);
                                            materialized = true;
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        additionalMaterializations.add(state.virtual);
                    }
                }
            }
            for (PhiNode phi : loopBegin.phis().snapshot()) {
                if (usages.isMarked(phi) && phi.type() == PhiType.Value) {
                    ObjectState initialObj = initialState.getObjectState(phi.valueAt(0));
                    boolean initialMaterialized = initialObj == null || !initialObj.isVirtual();

                    ObjectState loopEndObj = loopEndState.getObjectState(phi.valueAt(loopEnd));
                    if (loopEndObj == null || !loopEndObj.isVirtual()) {
                        if (loopEndObj != null) {
                            successEffects.setPhiInput(phi, loopBegin.phiPredecessorIndex(loopEnd), loopEndObj.getMaterializedValue());
                        }
                        if (!initialMaterialized) {
                            additionalMaterializations.add(initialObj.virtual);
                        }
                    } else {
                        if (initialMaterialized) {
                            loopEndState.materializeBefore(loopEnd, loopEndObj.virtual, successEffects);
                            materialized = true;
                        } else {
                            if (loopEndObj.virtual != initialObj.virtual) {
                                additionalMaterializations.add(initialObj.virtual);
                            }
                        }
                    }
                }
            }
        } while (materialized);

        for (ObjectState state : initialState.getStates()) {
            ObjectState endState = loopEndState.getObjectState(state.virtual);
            if (state.isVirtual()) {
                if (endState.isVirtual()) {
                    assert state.getEntries().length == endState.getEntries().length;
                    for (int i = 0; i < state.getEntries().length; i++) {
                        ValueNode value = state.getEntry(i);
                        ValueNode endValue = endState.getEntry(i);
                        ObjectState valueObj = initialState.getObjectState(value);
                        ObjectState endValueObj = loopEndState.getObjectState(endValue);

                        if (valueObj != null) {
                            if (valueObj.isVirtual()) {
                                if (endValueObj == null || !endValueObj.isVirtual() || valueObj.virtual != endValueObj.virtual) {
                                    assert !additionalMaterializations.isEmpty();
                                } else {
                                    // endValue is also virtual and refers to the same virtual object, so we're
                                    // good.
                                }
                            } else {
                                if ((endValueObj != null && endValueObj.getMaterializedValue() != valueObj.getMaterializedValue()) || (endValueObj == null && valueObj.getMaterializedValue() != endValue)) {
                                    phis.add(new PhiDesc(state.virtual, i));
                                } else {
                                    // either endValue has the same materialized value as value or endValue is the
                                    // same as the materialized value, so we're good.
                                }
                            }
                        } else {
                            if (value instanceof PhiNode && ((PhiNode) value).merge() == loopBegin) {
                                if (endValueObj != null) {
                                    if (endValueObj.isVirtual()) {
                                        assert !additionalMaterializations.isEmpty();
                                    }
                                    successEffects.addPhiInput((PhiNode) value, endValueObj.getMaterializedValue());
                                } else {
                                    successEffects.addPhiInput((PhiNode) value, endValue);
                                }
                            } else if (value != endValue) {
                                phis.add(new PhiDesc(state.virtual, i));
                            }
                        }
                    }
                } else {
                    // endState.materializedValue != null
                    assert !additionalMaterializations.isEmpty();
                }
            } else {
                // state.materializedValue != null
                if (endState.isVirtual()) {
                    // throw new GraalInternalError("un-materialized object state at %s", loopEnd);
                } else {
                    if (state.getMaterializedValue() != endState.getMaterializedValue()) {
                        // throw new GraalInternalError("changed materialized value during loop: %s vs %s",
                        // state.materializedValue, endState.materializedValue);
                    }
                }
            }
        }
    }
}
