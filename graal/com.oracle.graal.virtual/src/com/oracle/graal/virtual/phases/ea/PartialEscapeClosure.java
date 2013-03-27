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

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.PhiNode.PhiType;
import com.oracle.graal.nodes.VirtualState.NodeClosure;
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.spi.Virtualizable.EscapeState;
import com.oracle.graal.nodes.virtual.*;
import com.oracle.graal.phases.graph.*;
import com.oracle.graal.phases.graph.ReentrantBlockIterator.BlockIteratorClosure;
import com.oracle.graal.phases.graph.ReentrantBlockIterator.LoopInfo;
import com.oracle.graal.phases.schedule.*;
import com.oracle.graal.virtual.nodes.*;

class PartialEscapeClosure extends BlockIteratorClosure<BlockState> {

    public static final DebugMetric METRIC_MATERIALIZATIONS = Debug.metric("Materializations");
    public static final DebugMetric METRIC_MATERIALIZATIONS_PHI = Debug.metric("MaterializationsPhi");
    public static final DebugMetric METRIC_MATERIALIZATIONS_MERGE = Debug.metric("MaterializationsMerge");
    public static final DebugMetric METRIC_MATERIALIZATIONS_UNHANDLED = Debug.metric("MaterializationsUnhandled");
    public static final DebugMetric METRIC_MATERIALIZATIONS_LOOP_REITERATION = Debug.metric("MaterializationsLoopReiteration");
    public static final DebugMetric METRIC_MATERIALIZATIONS_LOOP_END = Debug.metric("MaterializationsLoopEnd");
    public static final DebugMetric METRIC_ALLOCATION_REMOVED = Debug.metric("AllocationsRemoved");

    private final NodeBitMap usages;
    private final SchedulePhase schedule;

    private final GraphEffectList effects = new GraphEffectList();

    private final VirtualizerToolImpl tool;

    public PartialEscapeClosure(NodeBitMap usages, SchedulePhase schedule, MetaAccessProvider metaAccess, Assumptions assumptions) {
        this.usages = usages;
        this.schedule = schedule;
        tool = new VirtualizerToolImpl(effects, usages, metaAccess, assumptions);
    }

    public GraphEffectList getEffects() {
        return effects;
    }

    public int getNewVirtualObjectCount() {
        return tool.getNewVirtualObjectCount();
    }

    @Override
    protected void processBlock(Block block, BlockState state) {
        trace("\nBlock: %s (", block);
        List<ScheduledNode> nodeList = schedule.getBlockToNodesMap().get(block);

        FixedWithNextNode lastFixedNode = null;
        for (Node node : nodeList) {
            if (usages.isMarked(node) || node instanceof VirtualizableAllocation) {
                trace("[[%s]] ", node);
                processNode((ValueNode) node, lastFixedNode == null ? null : lastFixedNode.next(), state);
            } else {
                trace("%s ", node);
            }

            if (node instanceof FixedWithNextNode && node.isAlive()) {
                lastFixedNode = (FixedWithNextNode) node;
            }
        }
        trace(")\n    end state: %s\n", state);
    }

    private void processNode(final ValueNode node, FixedNode insertBefore, final BlockState state) {
        tool.reset(state, node);
        if (node instanceof Virtualizable) {
            ((Virtualizable) node).virtualize(tool);
        }
        if (tool.isDeleted()) {
            return;
        }
        if (node instanceof StateSplit) {
            StateSplit split = (StateSplit) node;
            FrameState stateAfter = split.stateAfter();
            if (stateAfter != null) {
                if (stateAfter.usages().count() > 1) {
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
                    effects.addVirtualMapping(stateAfter, v);
                }
            }
        }
        if (tool.isCustomAction()) {
            return;
        }
        for (ValueNode input : node.inputs().filter(ValueNode.class)) {
            ObjectState obj = state.getObjectState(input);
            if (obj != null) {
                trace("replacing input %s at %s: %s", input, node, obj);
                replaceWithMaterialized(input, node, insertBefore, state, obj, METRIC_MATERIALIZATIONS_UNHANDLED);
            }
        }
    }

    private void ensureMaterialized(BlockState state, ObjectState obj, FixedNode materializeBefore, DebugMetric metric) {
        assert obj != null;
        if (obj.getState() == EscapeState.Virtual) {
            metric.increment();
            state.materializeBefore(materializeBefore, obj.virtual, EscapeState.Global, effects);
        } else {
            assert obj.getState() == EscapeState.Global;
        }
        assert !obj.isVirtual();
    }

    private void replaceWithMaterialized(ValueNode value, Node usage, FixedNode materializeBefore, BlockState state, ObjectState obj, DebugMetric metric) {
        ensureMaterialized(state, obj, materializeBefore, metric);
        effects.replaceFirstInput(usage, value, obj.getMaterializedValue());
    }

    @Override
    protected BlockState merge(MergeNode merge, List<BlockState> states) {

        BlockState newState = BlockState.meetAliases(states);

        // Iterative processing:
        // Merging the materialized/virtual state of virtual objects can lead to new
        // materializations, which can
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
                    boolean locksMatch = true;
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
                        locksMatch &= obj.getLockCount() == lockCount;
                    }

                    assert virtual < states.size() || locksMatch : "mismatching lock counts at " + merge;

                    if (virtual < states.size()) {
                        if (singleValue == null) {
                            PhiNode materializedValuePhi = new PhiNode(Kind.Object, merge);
                            effects.addFloatingNode(materializedValuePhi);
                            for (int i = 0; i < states.size(); i++) {
                                BlockState state = states.get(i);
                                ObjectState obj = state.getObjectState(object);
                                materialized |= obj.isVirtual();
                                ensureMaterialized(state, obj, merge.forwardEndAt(i), METRIC_MATERIALIZATIONS_MERGE);
                                effects.addPhiInput(materializedValuePhi, obj.getMaterializedValue());
                            }
                            newState.addObject(object, new ObjectState(object, materializedValuePhi, EscapeState.Global, lockCount));
                        } else {
                            newState.addObject(object, new ObjectState(object, singleValue, EscapeState.Global, lockCount));
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
                                            ensureMaterialized(state, obj, merge.forwardEndAt(i), METRIC_MATERIALIZATIONS_MERGE);
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
                        newState.addObject(object, new ObjectState(object, values, EscapeState.Virtual, lockCount));
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
                    replaceWithMaterialized(value, phi, merge.forwardEndAt(i), states.get(i), obj, METRIC_MATERIALIZATIONS_PHI);
                }
            }
        }
        return materialized;
    }

    @Override
    protected BlockState cloneState(BlockState oldState) {
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
                        METRIC_MATERIALIZATIONS_LOOP_REITERATION.increment();
                        initialState.materializeBefore(loop.loopBegin().forwardEnd(), virtualObject, EscapeState.Global, effects);
                    } else {
                        assert obj.getState() == EscapeState.Global;
                    }
                }
            }
        }

        throw new GraalInternalError("too many iterations at %s", loop);
    }

    private void processLoopExit(LoopExitNode exitNode, BlockState initialState, BlockState exitState) {
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
                        if ((value instanceof PhiNode && ((PhiNode) value).merge() == exitNode.loopBegin()) || initialObj == null || !initialObj.isVirtual() || initialObj.getEntry(i) != value) {
                            ProxyNode proxy = new ProxyNode(value, exitNode, PhiType.Value);
                            obj.setEntry(i, proxy);
                            effects.addFloatingNode(proxy);
                        }
                    }
                }
            } else {
                if (initialObj == null || initialObj.isVirtual()) {
                    ProxyNode proxy = proxies.get(obj.virtual);
                    if (proxy == null) {
                        proxy = new ProxyNode(obj.getMaterializedValue(), exitNode, PhiType.Value);
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
                                        // endValue is also virtual and refers to the same virtual
                                        // object, so we're
                                        // good.
                                    }
                                }
                            } else {
                                if (value instanceof PhiNode && ((PhiNode) value).merge() == loopBegin) {
                                    if (endValueObj != null) {
                                        if (endValueObj.isVirtual()) {
                                            METRIC_MATERIALIZATIONS_LOOP_END.increment();
                                            loopEndState.materializeBefore(loopEnd, endValueObj.virtual, EscapeState.Global, successEffects);
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
                            loopEndState.materializeBefore(loopEnd, loopEndObj.virtual, EscapeState.Global, successEffects);
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
                                    // endValue is also virtual and refers to the same virtual
                                    // object, so we're
                                    // good.
                                }
                            } else {
                                if ((endValueObj != null && endValueObj.getMaterializedValue() != valueObj.getMaterializedValue()) ||
                                                (endValueObj == null && valueObj.getMaterializedValue() != endValue)) {
                                    phis.add(new PhiDesc(state.virtual, i));
                                } else {
                                    // either endValue has the same materialized value as value or
                                    // endValue is the
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
                        // throw new
                        // GraalInternalError("changed materialized value during loop: %s vs %s",
                        // state.materializedValue, endState.materializedValue);
                    }
                }
            }
        }
    }
}
