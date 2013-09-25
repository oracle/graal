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

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.PhiNode.PhiType;
import com.oracle.graal.nodes.VirtualState.NodeClosure;
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.spi.Virtualizable.EscapeState;
import com.oracle.graal.nodes.virtual.*;
import com.oracle.graal.phases.schedule.*;
import com.oracle.graal.virtual.nodes.*;

public abstract class PartialEscapeClosure<BlockT extends PartialEscapeBlockState<BlockT>> extends EffectsClosure<BlockT> {

    public static final DebugMetric METRIC_MATERIALIZATIONS = Debug.metric("Materializations");
    public static final DebugMetric METRIC_MATERIALIZATIONS_PHI = Debug.metric("MaterializationsPhi");
    public static final DebugMetric METRIC_MATERIALIZATIONS_MERGE = Debug.metric("MaterializationsMerge");
    public static final DebugMetric METRIC_MATERIALIZATIONS_UNHANDLED = Debug.metric("MaterializationsUnhandled");
    public static final DebugMetric METRIC_MATERIALIZATIONS_LOOP_REITERATION = Debug.metric("MaterializationsLoopReiteration");
    public static final DebugMetric METRIC_MATERIALIZATIONS_LOOP_END = Debug.metric("MaterializationsLoopEnd");
    public static final DebugMetric METRIC_ALLOCATION_REMOVED = Debug.metric("AllocationsRemoved");

    public static final DebugMetric METRIC_MEMORYCHECKOINT = Debug.metric("MemoryCheckpoint");

    private final NodeBitMap usages;
    private final VirtualizerToolImpl tool;
    private final Map<Invoke, Double> hints = new IdentityHashMap<>();

    /**
     * Final subclass of PartialEscapeClosure, for performance and to make everything behave nicely
     * with generics.
     */
    public static final class Final extends PartialEscapeClosure<PartialEscapeBlockState.Final> {

        public Final(SchedulePhase schedule, MetaAccessProvider metaAccess, Assumptions assumptions) {
            super(schedule, metaAccess, assumptions);
        }

        @Override
        protected PartialEscapeBlockState.Final getInitialState() {
            return new PartialEscapeBlockState.Final();
        }

        @Override
        protected PartialEscapeBlockState.Final cloneState(PartialEscapeBlockState.Final oldState) {
            return new PartialEscapeBlockState.Final(oldState);
        }
    }

    public PartialEscapeClosure(SchedulePhase schedule, MetaAccessProvider metaAccess, Assumptions assumptions) {
        super(schedule);
        this.usages = schedule.getCFG().graph.createNodeBitMap();
        this.tool = new VirtualizerToolImpl(usages, metaAccess, assumptions);
    }

    public Map<Invoke, Double> getHints() {
        return hints;
    }

    /**
     * @return true if the node was deleted, false otherwise
     */
    @Override
    protected boolean processNode(Node node, BlockT state, GraphEffectList effects, FixedWithNextNode lastFixedNode) {
        boolean isMarked = usages.isMarked(node);
        if (isMarked || node instanceof VirtualizableRoot) {
            VirtualUtil.trace("[[%s]] ", node);
            FixedNode nextFixedNode = lastFixedNode == null ? null : lastFixedNode.next();
            return processNode((ValueNode) node, nextFixedNode, state, effects, isMarked);
        } else {
            VirtualUtil.trace("%s ", node);
            return false;
        }
    }

    private boolean processNode(final ValueNode node, FixedNode insertBefore, final BlockT state, final GraphEffectList effects, boolean isMarked) {
        tool.reset(state, node, insertBefore, effects);
        if (node instanceof Virtualizable) {
            ((Virtualizable) node).virtualize(tool);
        }
        if (tool.isDeleted()) {
            return !(node instanceof CommitAllocationNode || node instanceof AllocatedObjectNode);
        }
        if (isMarked) {
            if (node instanceof NodeWithState) {
                NodeWithState nodeWithState = (NodeWithState) node;
                FrameState stateAfter = nodeWithState.getState();
                if (stateAfter != null) {
                    if (stateAfter.usages().count() > 1) {
                        if (nodeWithState instanceof StateSplit) {
                            StateSplit split = (StateSplit) nodeWithState;
                            stateAfter = (FrameState) stateAfter.copyWithInputs();
                            split.setStateAfter(stateAfter);
                        } else {
                            throw GraalInternalError.shouldNotReachHere();
                        }
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
                        if (obj.isVirtual() && obj.hasLocks()) {
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
                        effects.addVirtualMapping(stateAfter, v);
                    }
                }
            }
            for (ValueNode input : node.inputs().filter(ValueNode.class)) {
                ObjectState obj = state.getObjectState(input);
                if (obj != null) {
                    if (obj.isVirtual() && node instanceof MethodCallTargetNode) {
                        Invoke invoke = ((MethodCallTargetNode) node).invoke();
                        hints.put(invoke, 5d);
                    }
                    VirtualUtil.trace("replacing input %s at %s: %s", input, node, obj);
                    replaceWithMaterialized(input, node, insertBefore, state, obj, effects, METRIC_MATERIALIZATIONS_UNHANDLED);
                }
            }
        }
        return false;
    }

    private static void ensureMaterialized(PartialEscapeBlockState state, ObjectState obj, FixedNode materializeBefore, GraphEffectList effects, DebugMetric metric) {
        assert obj != null;
        if (obj.getState() == EscapeState.Virtual) {
            metric.increment();
            state.materializeBefore(materializeBefore, obj.virtual, EscapeState.Global, effects);
        } else {
            assert obj.getState() == EscapeState.Global;
        }
        assert !obj.isVirtual();
    }

    private static void replaceWithMaterialized(ValueNode value, Node usage, FixedNode materializeBefore, PartialEscapeBlockState state, ObjectState obj, GraphEffectList effects, DebugMetric metric) {
        ensureMaterialized(state, obj, materializeBefore, effects, metric);
        effects.replaceFirstInput(usage, value, obj.getMaterializedValue());
    }

    @Override
    protected void processLoopExit(LoopExitNode exitNode, BlockT initialState, BlockT exitState, GraphEffectList effects) {
        HashMap<VirtualObjectNode, ProxyNode> proxies = new HashMap<>();

        for (ProxyNode proxy : exitNode.proxies()) {
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
                        if (exitNode.loopBegin().isPhiAtMerge(value) || initialObj == null || !initialObj.isVirtual() || initialObj.getEntry(i) != value) {
                            ProxyNode proxy = new ProxyNode(value, exitNode, PhiType.Value, null);
                            obj.setEntry(i, proxy);
                            effects.addFloatingNode(proxy, "virtualProxy");
                        }
                    }
                }
            } else {
                if (initialObj == null || initialObj.isVirtual()) {
                    ProxyNode proxy = proxies.get(obj.virtual);
                    if (proxy == null) {
                        proxy = new ProxyNode(obj.getMaterializedValue(), exitNode, PhiType.Value, null);
                        effects.addFloatingNode(proxy, "proxy");
                    } else {
                        effects.replaceFirstInput(proxy, proxy.value(), obj.getMaterializedValue());
                        // nothing to do - will be handled in processNode
                    }
                    obj.updateMaterializedValue(proxy);
                } else {
                    if (initialObj.getMaterializedValue() == obj.getMaterializedValue()) {
                        Debug.log("materialized value changes within loop: %s vs. %s at %s", initialObj.getMaterializedValue(), obj.getMaterializedValue(), exitNode);
                    }
                }
            }
        }
    }

    @Override
    protected MergeProcessor createMergeProcessor(Block merge) {
        return new MergeProcessor(merge);
    }

    protected class MergeProcessor extends EffectsClosure<BlockT>.MergeProcessor {

        private final HashMap<Object, PhiNode> materializedPhis = new HashMap<>();
        private final IdentityHashMap<VirtualObjectNode, PhiNode[]> valuePhis = new IdentityHashMap<>();
        private final IdentityHashMap<PhiNode, PhiNode[]> valueObjectMergePhis = new IdentityHashMap<>();
        private final IdentityHashMap<PhiNode, VirtualObjectNode> valueObjectVirtuals = new IdentityHashMap<>();

        public MergeProcessor(Block mergeBlock) {
            super(mergeBlock);
        }

        protected <T> PhiNode getCachedPhi(T virtual, Kind kind) {
            PhiNode result = materializedPhis.get(virtual);
            if (result == null) {
                result = new PhiNode(kind, merge);
                materializedPhis.put(virtual, result);
            }
            return result;
        }

        private PhiNode[] getValuePhis(VirtualObjectNode virtual) {
            PhiNode[] result = valuePhis.get(virtual);
            if (result == null) {
                result = new PhiNode[virtual.entryCount()];
                valuePhis.put(virtual, result);
            }
            return result;
        }

        private PhiNode[] getValueObjectMergePhis(PhiNode phi, int entryCount) {
            PhiNode[] result = valueObjectMergePhis.get(phi);
            if (result == null) {
                result = new PhiNode[entryCount];
                valueObjectMergePhis.put(phi, result);
            }
            return result;
        }

        private VirtualObjectNode getValueObjectVirtual(PhiNode phi, VirtualObjectNode virtual) {
            VirtualObjectNode result = valueObjectVirtuals.get(phi);
            if (result == null) {
                result = virtual.duplicate();
                valueObjectVirtuals.put(phi, result);
            }
            return result;
        }

        @Override
        protected void merge(List<BlockT> states) {
            super.merge(states);

            /*
             * Iterative processing: Merging the materialized/virtual state of virtual objects can
             * lead to new materializations, which can lead to new materializations because of phis,
             * and so on.
             */

            HashSet<VirtualObjectNode> virtualObjects = new HashSet<>(newState.getVirtualObjects());
            boolean materialized;
            do {
                mergeEffects.clear();
                afterMergeEffects.clear();
                materialized = false;
                for (VirtualObjectNode object : virtualObjects) {
                    ObjectState[] objStates = new ObjectState[states.size()];
                    for (int i = 0; i < states.size(); i++) {
                        objStates[i] = states.get(i).getObjectState(object);
                    }
                    int virtual = 0;
                    ObjectState startObj = objStates[0];
                    boolean locksMatch = true;
                    ValueNode singleValue = startObj.isVirtual() ? null : startObj.getMaterializedValue();
                    for (ObjectState obj : objStates) {
                        if (obj.isVirtual()) {
                            virtual++;
                            singleValue = null;
                        } else {
                            if (obj.getMaterializedValue() != singleValue) {
                                singleValue = null;
                            }
                        }
                        locksMatch &= obj.locksEqual(startObj);
                    }

                    if (virtual < states.size() || !locksMatch) {
                        if (singleValue == null) {
                            PhiNode materializedValuePhi = getCachedPhi(object, Kind.Object);
                            mergeEffects.addFloatingNode(materializedValuePhi, "materializedPhi");
                            for (int i = 0; i < states.size(); i++) {
                                PartialEscapeBlockState state = states.get(i);
                                ObjectState obj = objStates[i];
                                materialized |= obj.isVirtual();
                                Block predecessor = mergeBlock.getPredecessors().get(i);
                                ensureMaterialized(state, obj, predecessor.getEndNode(), blockEffects.get(predecessor), METRIC_MATERIALIZATIONS_MERGE);
                                afterMergeEffects.addPhiInput(materializedValuePhi, obj.getMaterializedValue());
                            }
                            newState.addObject(object, new ObjectState(object, materializedValuePhi, EscapeState.Global, null));
                        } else {
                            newState.addObject(object, new ObjectState(object, singleValue, EscapeState.Global, null));
                        }
                    } else {
                        assert virtual == states.size();
                        ValueNode[] values = startObj.getEntries().clone();
                        PhiNode[] phis = getValuePhis(object);
                        for (int index = 0; index < values.length; index++) {
                            for (int i = 1; i < states.size(); i++) {
                                ValueNode[] fields = objStates[i].getEntries();
                                if (phis[index] == null && values[index] != fields[index]) {
                                    Kind kind = values[index].kind();
                                    if (kind == Kind.Illegal) {
                                        // Can happen if one of the values is virtual and is only
                                        // materialized in the following loop.
                                        kind = Kind.Object;
                                    }
                                    phis[index] = new PhiNode(kind, merge);
                                }
                            }
                        }
                        outer: for (int index = 0; index < values.length; index++) {
                            if (phis[index] != null) {
                                mergeEffects.addFloatingNode(phis[index], "virtualMergePhi");
                                for (int i = 0; i < states.size(); i++) {
                                    if (!objStates[i].isVirtual()) {
                                        break outer;
                                    }
                                    ValueNode[] fields = objStates[i].getEntries();
                                    ObjectState obj = states.get(i).getObjectState(fields[index]);
                                    if (obj != null) {
                                        materialized |= obj.isVirtual();
                                        Block predecessor = mergeBlock.getPredecessors().get(i);
                                        ensureMaterialized(states.get(i), obj, predecessor.getEndNode(), blockEffects.get(predecessor), METRIC_MATERIALIZATIONS_MERGE);
                                        fields[index] = obj.getMaterializedValue();
                                    }
                                    afterMergeEffects.addPhiInput(phis[index], fields[index]);
                                }
                                values[index] = phis[index];
                            }
                        }
                        newState.addObject(object, new ObjectState(object, values, EscapeState.Virtual, startObj.getLocks()));
                    }
                }

                for (PhiNode phi : merge.phis()) {
                    if (usages.isMarked(phi) && phi.type() == PhiType.Value) {
                        materialized |= processPhi(phi, states);
                    }
                }
            } while (materialized);
        }

        private boolean processPhi(PhiNode phi, List<BlockT> states) {
            assert states.size() == phi.valueCount();
            int virtualInputs = 0;
            boolean materialized = false;
            VirtualObjectNode sameObject = null;
            ResolvedJavaType sameType = null;
            int sameEntryCount = -1;
            boolean hasIdentity = false;
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
                            hasIdentity |= obj.virtual.hasIdentity();
                        }
                    } else {
                        afterMergeEffects.setPhiInput(phi, i, obj.getMaterializedValue());
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
                    if (!hasIdentity) {
                        VirtualObjectNode virtual = getValueObjectVirtual(phi, states.get(0).getObjectState(phi.valueAt(0)).virtual);

                        PhiNode[] phis = getValueObjectMergePhis(phi, virtual.entryCount());
                        for (int i = 0; i < virtual.entryCount(); i++) {
                            assert virtual.entryKind(i) != Kind.Object;
                            if (phis[i] == null) {
                                phis[i] = new PhiNode(virtual.entryKind(i), merge);
                            }
                            mergeEffects.addFloatingNode(phis[i], "valueObjectPhi");
                            for (int i2 = 0; i2 < phi.valueCount(); i2++) {
                                afterMergeEffects.addPhiInput(phis[i], states.get(i2).getObjectState(phi.valueAt(i2)).getEntry(i));
                            }
                        }
                        mergeEffects.addFloatingNode(virtual, "valueObjectNode");
                        newState.addObject(virtual, new ObjectState(virtual, Arrays.copyOf(phis, phis.length, ValueNode[].class), EscapeState.Virtual, null));
                        newState.addAndMarkAlias(virtual, virtual, usages);
                        newState.addAndMarkAlias(virtual, phi, usages);
                    } else {
                        materialize = true;
                    }
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
                        Block predecessor = mergeBlock.getPredecessors().get(i);
                        replaceWithMaterialized(value, phi, predecessor.getEndNode(), states.get(i), obj, blockEffects.get(predecessor), METRIC_MATERIALIZATIONS_PHI);
                    }
                }
            }
            return materialized;
        }
    }
}
