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
package com.oracle.graal.compiler.phases.ea;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.meta.JavaType.*;
import com.oracle.graal.compiler.*;
import com.oracle.graal.compiler.phases.*;
import com.oracle.graal.compiler.schedule.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.cfg.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.PhiNode.PhiType;
import com.oracle.graal.nodes.VirtualState.NodeClosure;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.virtual.*;

class EscapeAnalysisIteration {

    // Metrics
    private static final DebugMetric metricAllocationRemoved = Debug.metric("AllocationRemoved");
    private static final DebugMetric metricAllocationFieldsRemoved = Debug.metric("AllocationFieldsRemoved");
    private static final DebugMetric metricStoreRemoved = Debug.metric("StoreRemoved");
    private static final DebugMetric metricLoadRemoved = Debug.metric("LoadRemoved");
    private static final DebugMetric metricLockRemoved = Debug.metric("LockRemoved");
    private static final DebugMetric metricOtherRemoved = Debug.metric("OtherRemoved");
    private static final DebugMetric metricMaterializations = Debug.metric("Materializations");
    private static final DebugMetric metricMaterializationFields = Debug.metric("MaterializationFields");
    private static final DebugMetric metricLoopBailouts = Debug.metric("LoopBailouts");
    private static final DebugMetric metricMonitorBailouts = Debug.metric("MonitorBailouts");


    private static final ValueNode DUMMY_NODE = new ValueNode(null) {
    };

    public static final void trace(String format, Object... obj) {
        if (GraalOptions.TraceEscapeAnalysis) {
            Debug.log(format, obj);
        }
    }

    public static final void error(String format, Object... obj) {
        System.out.print(String.format(format, obj));
    }

    private final StructuredGraph graph;
    private final MetaAccessProvider runtime;
    private final SchedulePhase schedule;
    private final NodeBitMap usages;
    boolean changed = false;

    private final boolean changeGraph;

    private final HashSet<VirtualObjectNode> reusedVirtualObjects = new HashSet<>();
    private final HashSet<ValueNode> allocations;
    private final ArrayList<ValueNode> obsoleteNodes = new ArrayList<>();
    private int virtualIds = 0;

    public EscapeAnalysisIteration(StructuredGraph graph, SchedulePhase schedule, MetaAccessProvider runtime, HashSet<ValueNode> allocations, boolean changeGraph) {
        this.graph = graph;
        this.schedule = schedule;
        this.runtime = runtime;
        this.allocations = allocations;
        this.changeGraph = changeGraph;
        this.usages = graph.createNodeBitMap();
    }

    public void run() {
        new PartialEscapeIterator(graph, schedule.getCFG().getStartBlock()).apply();

        if (changeGraph) {
            Debug.dump(graph, "after PartialEscapeAnalysis");

            for (ValueNode node : obsoleteNodes) {
                if (node.isAlive() && node instanceof FixedWithNextNode) {
                    FixedWithNextNode x = (FixedWithNextNode) node;
                    FixedNode next = x.next();
                    x.setNext(null);
                    ((FixedWithNextNode) node.predecessor()).setNext(next);
                }
            }
            new DeadCodeEliminationPhase().apply(graph);

            if (changed) {
                Debug.log("escape analysis on %s\n", graph.method());
            }
        }
    }

    private static class ObjectState {

        public final VirtualObjectNode virtual;
        public ValueNode[] fieldState;
        public ValueNode materializedValue;
        public int lockCount;
        public boolean initialized;

        public ObjectState(VirtualObjectNode virtual, ValueNode[] fieldState, int lockCount) {
            this.virtual = virtual;
            this.fieldState = fieldState;
            this.lockCount = lockCount;
            this.initialized = false;
        }

        public ObjectState(VirtualObjectNode virtual, ValueNode materializedValue, int lockCount) {
            this.virtual = virtual;
            this.materializedValue = materializedValue;
            this.lockCount = lockCount;
            this.initialized = true;
        }

        private ObjectState(ObjectState other) {
            virtual = other.virtual;
            fieldState = other.fieldState == null ? null : other.fieldState.clone();
            materializedValue = other.materializedValue;
            lockCount = other.lockCount;
            initialized = other.initialized;
        }

        @Override
        public ObjectState clone() {
            return new ObjectState(this);
        }

        @Override
        public String toString() {
            StringBuilder str = new StringBuilder().append('{');
            if (lockCount > 0) {
                str.append('l').append(lockCount).append(' ');
            }
            if (fieldState != null) {
                for (int i = 0; i < fieldState.length; i++) {
                    str.append(virtual.fieldName(i)).append('=').append(fieldState[i]).append(' ');
                }
            }
            if (materializedValue != null) {
                str.append("mat=").append(materializedValue);
            }

            return str.append('}').toString();
        }
    }

    private class BlockState implements MergeableBlockState<BlockState> {

        private final HashMap<VirtualObjectNode, ObjectState> objectStates = new HashMap<>();
        private final HashMap<ValueNode, VirtualObjectNode> objectAliases = new HashMap<>();

        public BlockState() {
        }

        public BlockState(BlockState other) {
            for (Map.Entry<VirtualObjectNode, ObjectState> entry : other.objectStates.entrySet()) {
                objectStates.put(entry.getKey(), entry.getValue().clone());
            }
            for (Map.Entry<ValueNode, VirtualObjectNode> entry : other.objectAliases.entrySet()) {
                objectAliases.put(entry.getKey(), entry.getValue());
            }
        }

        public ObjectState objectState(VirtualObjectNode object) {
            assert objectStates.containsKey(object);
            return objectStates.get(object);
        }

        public ObjectState objectState(ValueNode value) {
            VirtualObjectNode object = objectAliases.get(value);
            return object == null ? null : objectState(object);
        }

        @Override
        public BlockState clone() {
            return new BlockState(this);
        }

        public void materializeBefore(FixedNode fixed, VirtualObjectNode virtual) {
            if (changeGraph) {
                HashSet<VirtualObjectNode> deferred = new HashSet<>();
                ArrayList<FixedWithNextNode> deferredStores = new ArrayList<>();
                materializeChangedBefore(fixed, virtual, deferred, deferredStores);
                for (FixedWithNextNode write : deferredStores) {
                    write.setProbability(fixed.probability());
                    graph.addBeforeFixed(fixed, write);
                }
            } else {
                materializeUnchangedBefore(virtual);
            }
        }

        private void materializeUnchangedBefore(VirtualObjectNode virtual) {
            trace("materializing %s", virtual);
            ObjectState obj = objectState(virtual);
            if (obj.lockCount > 0) {
                if (changeGraph) {
                    error("object materialized with lock: %s\n", virtual);
                }
                metricMonitorBailouts.increment();
                throw new BailoutException("object materialized with lock");
            }

            ValueNode[] fieldState = obj.fieldState;
            obj.fieldState = null;
            obj.materializedValue = DUMMY_NODE;
            for (int i = 0; i < fieldState.length; i++) {
                ObjectState valueObj = objectState(fieldState[i]);
                if (valueObj != null) {
                    if (valueObj.materializedValue == null) {
                        materializeUnchangedBefore(valueObj.virtual);
                    }
                }
            }
            obj.initialized = true;
        }

        private void materializeChangedBefore(FixedNode fixed, VirtualObjectNode virtual, HashSet<VirtualObjectNode> deferred, ArrayList<FixedWithNextNode> deferredStores) {
            trace("materializing %s at %s", virtual, fixed);
            ObjectState obj = objectState(virtual);
            if (obj.lockCount > 0) {
                error("object materialized with lock: %s\n", virtual);
                metricMonitorBailouts.increment();
                throw new BailoutException("object materialized with lock");
            }

            MaterializeObjectNode materialize = graph.add(new MaterializeObjectNode(virtual));
            materialize.setProbability(fixed.probability());
            ValueNode[] fieldState = obj.fieldState;
            metricMaterializations.increment();
            metricMaterializationFields.add(fieldState.length);
            obj.fieldState = null;
            obj.materializedValue = materialize;
            deferred.add(virtual);
            for (int i = 0; i < fieldState.length; i++) {
                ObjectState valueObj = objectState(fieldState[i]);
                if (valueObj != null) {
                    if (valueObj.materializedValue == null) {
                        materializeChangedBefore(fixed, valueObj.virtual, deferred, deferredStores);
                    }
                    if (deferred.contains(valueObj.virtual)) {
                        Kind fieldKind;
                        if (virtual instanceof VirtualArrayNode) {
                            deferredStores.add(graph.add(new CyclicMaterializeStoreNode(materialize, valueObj.materializedValue, i)));
                            fieldKind = ((VirtualArrayNode) virtual).componentType().kind();
                        } else {
                            VirtualInstanceNode instanceObject = (VirtualInstanceNode) virtual;
                            deferredStores.add(graph.add(new CyclicMaterializeStoreNode(materialize, valueObj.materializedValue, instanceObject.field(i))));
                            fieldKind = instanceObject.field(i).type().kind();
                        }
                        materialize.values().set(i, ConstantNode.defaultForKind(fieldKind, graph));
                    } else {
                        assert valueObj.initialized : "should be initialized: " + virtual + " at " + fixed;
                        materialize.values().set(i, valueObj.materializedValue);
                    }
                } else {
                    materialize.values().set(i, fieldState[i]);
                }
            }
            deferred.remove(virtual);

            obj.initialized = true;
            graph.addBeforeFixed(fixed, materialize);
        }

        private void addAndMarkAlias(VirtualObjectNode virtual, ValueNode node, boolean remove) {
            objectAliases.put(node, virtual);
            for (Node usage : node.usages()) {
                markVirtualUsages(usage);
            }
            if (remove) {
                obsoleteNodes.add(node);
            }
        }

        private void markVirtualUsages(Node node) {
            if (!usages.isNew(node)) {
                usages.mark(node);
            }
            if (node instanceof VirtualState) {
                for (Node usage : node.usages()) {
                    markVirtualUsages(usage);
                }
            }
        }

        public void addObject(VirtualObjectNode virtual, ObjectState state) {
            objectStates.put(virtual, state);
        }

        public Iterable<ObjectState> states() {
            return objectStates.values();
        }

        @Override
        public String toString() {
            return objectStates.toString();
        }
    }

    private class PartialEscapeIterator extends PostOrderBlockIterator<BlockState> {

        public PartialEscapeIterator(StructuredGraph graph, Block start) {
            super(graph, start, new BlockState());
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
                    // only escape analyze allocations that were escape analyzed during the first iteration
                    if (changeGraph && !allocations.contains(node)) {
                        op = null;
                    }
                }

                if (op != null) {
                    trace("{{%s}} ", node);
                    VirtualObjectNode virtualObject = op.virtualObject(virtualIds);
                    if (virtualObject.isAlive()) {
                        reusedVirtualObjects.add(virtualObject);
                        state.addAndMarkAlias(virtualObject, virtualObject, false);
                    } else {
                        if (changeGraph) {
                            virtualObject = graph.add(virtualObject);
                        }
                    }
                    ValueNode[] fieldState = changeGraph ? op.fieldState() : new ValueNode[virtualObject.entryCount()];
                    if (changeGraph) {
                        metricAllocationRemoved.increment();
                        metricAllocationFieldsRemoved.add(fieldState.length);
                    } else {
                        allocations.add((ValueNode) node);
                    }
                    state.addObject(virtualObject, new ObjectState(virtualObject, fieldState, 0));
                    state.addAndMarkAlias(virtualObject, (ValueNode) node, true);
                    virtualIds++;
                } else {
                    if (changeGraph && node instanceof LoopExitNode) {
                        for (ObjectState obj : state.states()) {
                            if (obj.fieldState != null) {
                                for (int i = 0; i < obj.fieldState.length; i++) {
                                    ValueNode value = obj.fieldState[i];
                                    ObjectState valueObj = state.objectState(value);
                                    if (valueObj == null) {
                                        obj.fieldState[i] = graph.unique(new ValueProxyNode(value, (LoopExitNode) node, PhiType.Value));
                                    }
                                }
                            } else {
                                obj.materializedValue = graph.unique(new ValueProxyNode(obj.materializedValue, (LoopExitNode) node, PhiType.Value));
                            }
                        }
                    }

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
                ObjectState obj = state.objectState(value);
                assert obj != null : node;
                if (obj.materializedValue == null) {
                    state.addAndMarkAlias(obj.virtual, node, true);
                } else {
                    if (changeGraph) {
                        node.replaceFirstInput(value, obj.materializedValue);
                    }
                }
                usageFound = true;
            } else if (node instanceof CheckCastNode) {
                CheckCastNode x = (CheckCastNode) node;
                ObjectState obj = state.objectState(x.object());
                assert obj != null : x;
                if (obj.materializedValue == null) {
                    if (x.targetClass() != null && obj.virtual.type().isSubtypeOf(x.targetClass())) {
                        metricOtherRemoved.increment();
                        state.addAndMarkAlias(obj.virtual, x, true);
                        // throw new UnsupportedOperationException("probably incorrect - losing dependency");
                    } else {
                        replaceWithMaterialized(x.object(), x, state, obj);
                    }
                } else {
                    if (changeGraph) {
                        node.replaceFirstInput(x.object(), obj.materializedValue);
                    }
                }
                usageFound = true;
            } else if (node instanceof IsNullNode) {
                IsNullNode x = (IsNullNode) node;
                ObjectState obj = state.objectState(x.object());
                assert obj != null : x;
                if (changeGraph) {
                    graph.replaceFloating(x, graph.unique(ConstantNode.forBoolean(false, graph)));
                    metricOtherRemoved.increment();
                }
                usageFound = true;
            } else if (node instanceof AccessMonitorNode) {
                AccessMonitorNode x = (AccessMonitorNode) node;
                ObjectState obj = state.objectState(x.object());
                if (obj != null) {
                    Debug.log("monitor operation %s on %s\n", x, obj.virtual);
                    if (node instanceof MonitorEnterNode) {
                        obj.lockCount++;
                    } else {
                        assert node instanceof MonitorExitNode;
                        obj.lockCount--;
                    }
                    if (changeGraph) {
                        changed = true;
                        if (obj.materializedValue == null) {
                            metricLockRemoved.increment();
                            node.replaceFirstInput(x.object(), obj.virtual);
                            x.eliminate();
                        } else {
                            node.replaceFirstInput(x.object(), obj.materializedValue);
                        }
                    }
                    usageFound = true;
                }
            } else if (node instanceof CyclicMaterializeStoreNode) {
                CyclicMaterializeStoreNode x = (CyclicMaterializeStoreNode) node;
                ObjectState obj = state.objectState(x.object());
                assert obj != null : x;
                if (obj.virtual instanceof VirtualArrayNode) {
                    obj.fieldState[x.targetIndex()] = x.value();
                } else {
                    VirtualInstanceNode instance = (VirtualInstanceNode) obj.virtual;
                    int index = instance.fieldIndex(x.targetField());
                    obj.fieldState[index] = x.value();
                }
                if (changeGraph) {
                    graph.removeFixed(x);
                }
                usageFound = true;
            } else if (node instanceof LoadFieldNode) {
                LoadFieldNode x = (LoadFieldNode) node;
                ObjectState obj = state.objectState(x.object());
                assert obj != null : x;
                VirtualInstanceNode virtual = (VirtualInstanceNode) obj.virtual;
                int fieldIndex = virtual.fieldIndex(x.field());
                if (fieldIndex == -1) {
                    // the field does not exist in the virtual object
                    ensureMaterialized(state, obj, x);
                }
                if (obj.materializedValue == null) {
                    ValueNode result = obj.fieldState[fieldIndex];
                    ObjectState resultObj = state.objectState(result);
                    if (resultObj != null) {
                        state.addAndMarkAlias(resultObj.virtual, x, true);
                    } else {
                        if (changeGraph) {
                            x.replaceAtUsages(result);
                            graph.removeFixed(x);
                        }
                    }
                    if (changeGraph) {
                        metricLoadRemoved.increment();
                    }
                    changed = true;
                } else {
                    if (changeGraph) {
                        x.replaceFirstInput(x.object(), obj.materializedValue);
                    }
                }
                usageFound = true;
            } else if (node instanceof StoreFieldNode) {
                StoreFieldNode x = (StoreFieldNode) node;
                ValueNode object = x.object();
                ValueNode value = x.value();
                ObjectState obj = state.objectState(object);
                if (obj != null) {
                    VirtualInstanceNode virtual = (VirtualInstanceNode) obj.virtual;
                    int fieldIndex = virtual.fieldIndex(x.field());
                    if (fieldIndex == -1) {
                        // the field does not exist in the virtual object
                        ensureMaterialized(state, obj, x);
                    }
                    if (obj.materializedValue == null) {
                        obj.fieldState[fieldIndex] = value;
                        if (changeGraph) {
                            graph.removeFixed(x);
                            metricStoreRemoved.increment();
                        }
                        changed = true;
                    } else {
                        if (changeGraph) {
                            x.replaceFirstInput(object, obj.materializedValue);
                        }
                        ObjectState valueObj = state.objectState(value);
                        if (valueObj != null) {
                            replaceWithMaterialized(value, x, state, valueObj);
                        }
                    }
                    usageFound = true;
                } else {
                    ObjectState valueObj = state.objectState(value);
                    if (valueObj != null) {
                        replaceWithMaterialized(value, x, state, valueObj);
                        usageFound = true;
                    }
                }
            } else if (node instanceof LoadIndexedNode) {
                LoadIndexedNode x = (LoadIndexedNode) node;
                ValueNode array = x.array();
                ObjectState arrayObj = state.objectState(array);
                if (arrayObj != null) {
                    if (arrayObj.materializedValue == null) {
                        int index = x.index().isConstant() ? x.index().asConstant().asInt() : -1;
                        if (index < 0 || index >= arrayObj.fieldState.length) {
                            // out of bounds or not constant
                            replaceWithMaterialized(array, x, state, arrayObj);
                        } else {
                            ValueNode result = arrayObj.fieldState[index];
                            ObjectState resultObj = state.objectState(result);
                            if (resultObj != null) {
                                state.addAndMarkAlias(resultObj.virtual, x, true);
                            } else {
                                if (changeGraph) {
                                    x.replaceAtUsages(result);
                                    graph.removeFixed(x);
                                }
                            }
                            if (changeGraph) {
                                metricLoadRemoved.increment();
                            }
                            changed = true;
                        }
                    } else {
                        if (changeGraph) {
                            x.replaceFirstInput(array, arrayObj.materializedValue);
                        }
                    }
                    usageFound = true;
                }
            } else if (node instanceof StoreIndexedNode) {
                StoreIndexedNode x = (StoreIndexedNode) node;
                ValueNode array = x.array();
                ValueNode value = x.value();
                ObjectState arrayObj = state.objectState(array);
                ObjectState valueObj = state.objectState(value);

                if (arrayObj != null) {
                    if (arrayObj.materializedValue == null) {
                        int index = x.index().isConstant() ? x.index().asConstant().asInt() : -1;
                        if (index < 0 || index >= arrayObj.fieldState.length) {
                            // out of bounds or not constant
                            replaceWithMaterialized(array, x, state, arrayObj);
                            if (valueObj != null) {
                                replaceWithMaterialized(value, x, state, valueObj);
                            }
                        } else {
                            arrayObj.fieldState[index] = value;
                            if (changeGraph) {
                                graph.removeFixed(x);
                                metricStoreRemoved.increment();
                            }
                            changed = true;
                        }
                    } else {
                        if (changeGraph) {
                            x.replaceFirstInput(array, arrayObj.materializedValue);
                        }
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
                ObjectState obj = state.objectState(x.object());
                replaceWithMaterialized(x.object(), x, state, obj);
                usageFound = true;
            } else if (node instanceof ArrayLengthNode) {
                ArrayLengthNode x = (ArrayLengthNode) node;
                ObjectState obj = state.objectState(x.array());
                assert obj != null : x;
                if (changeGraph) {
                    graph.replaceFixedWithFloating(x, ConstantNode.forInt(((VirtualArrayNode) obj.virtual).entryCount(), graph));
                    metricOtherRemoved.increment();
                }
                changed = true;
                usageFound = true;
            } else if (node instanceof LoadHubNode) {
                LoadHubNode x = (LoadHubNode) node;
                ObjectState obj = state.objectState(x.object());
                assert obj != null : x;
                if (changeGraph) {
                    ConstantNode hub = ConstantNode.forConstant(obj.virtual.type().getEncoding(Representation.ObjectHub), runtime, graph);
                    graph.replaceFixedWithFloating(x, hub);
                    metricOtherRemoved.increment();
                }
                changed = true;
                usageFound = true;
            } else if (node instanceof ReturnNode) {
                ReturnNode x = (ReturnNode) node;
                ObjectState obj = state.objectState(x.result());
                replaceWithMaterialized(x.result(), x, state, obj);
                usageFound = true;
            } else if (node instanceof MethodCallTargetNode) {
                for (ValueNode argument : ((MethodCallTargetNode) node).arguments()) {
                    ObjectState obj = state.objectState(argument);
                    if (obj != null) {
                        replaceWithMaterialized(argument, node, insertBefore, state, obj);
                        usageFound = true;
                    }
                }
            } else if (node instanceof ObjectEqualsNode) {
                ObjectEqualsNode x = (ObjectEqualsNode) node;
                ObjectState xObj = state.objectState(x.x());
                ObjectState yObj = state.objectState(x.y());
                boolean xVirtual = xObj != null && xObj.materializedValue == null;
                boolean yVirtual = yObj != null && yObj.materializedValue == null;

                if (changeGraph) {
                    if (xVirtual ^ yVirtual) {
                        // one of them is virtual: they can never be the same objects
                        graph.replaceFloating(x, ConstantNode.forBoolean(false, graph));
                        usageFound = true;
                        metricOtherRemoved.increment();
                        changed = true;
                    } else if (xVirtual && yVirtual) {
                        // both are virtual: check if they refer to the same object
                        graph.replaceFloating(x, ConstantNode.forBoolean(xObj == yObj, graph));
                        usageFound = true;
                        metricOtherRemoved.increment();
                        changed = true;
                    } else {
                        assert xObj != null || yObj != null;
                        if (xObj != null) {
                            assert xObj.materializedValue != null;
                            node.replaceFirstInput(x.x(), xObj.materializedValue);
                        }
                        if (yObj != null) {
                            assert yObj.materializedValue != null;
                            node.replaceFirstInput(x.y(), yObj.materializedValue);
                        }
                    }
                }
                usageFound = true;
            } else if (node instanceof MergeNode) {
                usageFound = true;
            } else if (node instanceof UnsafeLoadNode || node instanceof UnsafeStoreNode || node instanceof CompareAndSwapNode || node instanceof SafeReadNode) {
                for (ValueNode input : node.inputs().filter(ValueNode.class)) {
                    ObjectState obj = state.objectState(input);
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
                    if (changeGraph) {
                        if (stateAfter.usages().size() > 1) {
                            stateAfter = (FrameState) stateAfter.copyWithInputs();
                            split.setStateAfter(stateAfter);
                        }
                        final HashSet<ObjectState> virtual = new HashSet<>();
                        stateAfter.applyToNonVirtual(new NodeClosure<ValueNode>() {

                            @Override
                            public void apply(Node usage, ValueNode value) {
                                ObjectState valueObj = state.objectState(value);
                                if (valueObj != null) {
                                    virtual.add(valueObj);
                                    usage.replaceFirstInput(value, valueObj.virtual);
                                } else if (value instanceof VirtualObjectNode) {
                                    ObjectState virtualObj = null;
                                    for (ObjectState obj : state.states()) {
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
                        for (ObjectState obj : state.states()) {
                            if (obj.materializedValue == null && obj.lockCount > 0) {
                                virtual.add(obj);
                            }
                        }

                        ArrayDeque<ObjectState> queue = new ArrayDeque<>(virtual);
                        while (!queue.isEmpty()) {
                            ObjectState obj = queue.removeLast();
                            if (obj.materializedValue == null) {
                                for (ValueNode field : obj.fieldState) {
                                    ObjectState fieldObj = state.objectState(field);
                                    if (fieldObj != null) {
                                        if (fieldObj.materializedValue == null && !virtual.contains(fieldObj)) {
                                            virtual.add(fieldObj);
                                            queue.addLast(fieldObj);
                                        }
                                    }
                                }
                            }
                        }
                        for (ObjectState obj : virtual) {
                            EscapeObjectState v;
                            if (obj.materializedValue == null) {
                                ValueNode[] fieldState = obj.fieldState.clone();
                                for (int i = 0; i < fieldState.length; i++) {
                                    ObjectState valueObj = state.objectState(fieldState[i]);
                                    if (valueObj != null) {
                                        if (valueObj.materializedValue == null) {
                                            fieldState[i] = valueObj.virtual;
                                        } else {
                                            fieldState[i] = valueObj.materializedValue;
                                        }
                                    }
                                }
                                v = graph.add(new VirtualObjectState(obj.virtual, fieldState));
                            } else {
                                v = graph.add(new MaterializedObjectState(obj.virtual, obj.materializedValue));
                            }
                            for (int i = 0; i < stateAfter.virtualObjectMappingCount(); i++) {
                                if (stateAfter.virtualObjectMappingAt(i).object() == v.object()) {
                                    if (reusedVirtualObjects.contains(v.object())) {
                                        stateAfter.virtualObjectMappings().remove(i);
                                    } else {
                                        throw new GraalInternalError("unexpected duplicate virtual state at: %s for %s", node, v.object());
                                    }
                                }
                            }
                            stateAfter.addVirtualObjectMapping(v);
                        }
                    }
                    usageFound = true;
                }
            }
            if (!usageFound) {
                for (ValueNode input : node.inputs().filter(ValueNode.class)) {
                    ObjectState obj = state.objectState(input);
                    if (obj != null) {
                        replaceWithMaterialized(input, node, insertBefore, state, obj);
                        usageFound = true;
                    }
                }
                Debug.log("unexpected usage of %s: %s\n", node, node.inputs().snapshot());
            }
        }

        private void ensureMaterialized(BlockState state, ObjectState obj, FixedNode materializeBefore) {
            assert obj != null;
            if (obj.materializedValue == null) {
                state.materializeBefore(materializeBefore, obj.virtual);
            }
            assert obj.materializedValue != null;
        }

        private void replaceWithMaterialized(ValueNode value, FixedNode usage, BlockState state, ObjectState obj) {
            ensureMaterialized(state, obj, usage);
            if (changeGraph) {
                usage.replaceFirstInput(value, obj.materializedValue);
            }
        }

        private void replaceWithMaterialized(ValueNode value, Node usage, FixedNode materializeBefore, BlockState state, ObjectState obj) {
            ensureMaterialized(state, obj, materializeBefore);
            if (changeGraph) {
                usage.replaceFirstInput(value, obj.materializedValue);
            }
        }

        @Override
        protected BlockState merge(MergeNode merge, List<BlockState> states) {
            BlockState newState = new BlockState();

            newState.objectAliases.putAll(states.get(0).objectAliases);
            for (int i = 1; i < states.size(); i++) {
                BlockState state = states.get(i);
                for (Map.Entry<ValueNode, VirtualObjectNode> entry : states.get(0).objectAliases.entrySet()) {
                    if (state.objectAliases.containsKey(entry.getKey())) {
                        assert state.objectAliases.get(entry.getKey()) == entry.getValue();
                    } else {
                        newState.objectAliases.remove(entry.getKey());
                    }
                }
            }

            // Iterative processing:
            // Merging the materialized/virtual state of virtual objects can lead to new materializations, which can
            // lead to new materializations because of phis, and so on.

            boolean materialized;
            do {
                materialized = false;
                // use a hash set to make the values distinct...
                for (VirtualObjectNode object : new HashSet<>(newState.objectAliases.values())) {
                    ObjectState resultState = newState.objectStates.get(object);
                    if (resultState == null || resultState.materializedValue == null) {
                        int virtual = 0;
                        int lockCount = states.get(0).objectState(object).lockCount;
                        for (BlockState state : states) {
                            ObjectState obj = state.objectState(object);
                            if (obj.materializedValue == null) {
                                virtual++;
                            }
                            assert obj.lockCount == lockCount : "mismatching lock counts";
                        }

                        if (virtual < states.size()) {
                            ValueNode materializedValuePhi = changeGraph ? graph.add(new PhiNode(Kind.Object, merge)) : DUMMY_NODE;
                            for (int i = 0; i < states.size(); i++) {
                                BlockState state = states.get(i);
                                ObjectState obj = state.objectState(object);
                                materialized |= obj.materializedValue == null;
                                ensureMaterialized(state, obj, merge.forwardEndAt(i));
                                if (changeGraph) {
                                    ((PhiNode) materializedValuePhi).addInput(obj.materializedValue);
                                }
                            }
                            newState.addObject(object, new ObjectState(object, materializedValuePhi, lockCount));
                        } else {
                            assert virtual == states.size();
                            ValueNode[] values = states.get(0).objectState(object).fieldState.clone();
                            PhiNode[] phis = new PhiNode[values.length];
                            boolean[] phiCreated = new boolean[values.length];
                            int mismatch = 0;
                            for (int i = 1; i < states.size(); i++) {
                                BlockState state = states.get(i);
                                ValueNode[] fields = state.objectState(object).fieldState;
                                for (int index = 0; index < values.length; index++) {
                                    if (!phiCreated[index] && values[index] != fields[index]) {
                                        mismatch++;
                                        if (changeGraph) {
                                            phis[index] = graph.add(new PhiNode(values[index].kind(), merge));
                                        }
                                        phiCreated[index] = true;
                                    }
                                }
                            }
                            if (mismatch > 0) {
                                for (int i = 0; i < states.size(); i++) {
                                    BlockState state = states.get(i);
                                    ValueNode[] fields = state.objectState(object).fieldState;
                                    for (int index = 0; index < values.length; index++) {
                                        if (phiCreated[index]) {
                                            ObjectState obj = state.objectState(fields[index]);
                                            if (obj != null) {
                                                materialized |= obj.materializedValue == null;
                                                ensureMaterialized(state, obj, merge.forwardEndAt(i));
                                                fields[index] = obj.materializedValue;
                                            }
                                            if (changeGraph) {
                                                phis[index].addInput(fields[index]);
                                            }
                                        }
                                    }
                                }
                                for (int index = 0; index < values.length; index++) {
                                    if (phiCreated[index]) {
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
                ObjectState obj = states.get(i).objectState(value);
                if (obj != null) {
                    if (obj.materializedValue == null) {
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
                        if (changeGraph) {
                            phi.setValueAt(i, obj.materializedValue);
                        }
                    }
                }
            }
            boolean materialize = false;
            if (virtualInputs == 0) {
                // nothing to do...
            } else if (virtualInputs == phi.valueCount()) {
                if (sameObject != null) {
                    newState.addAndMarkAlias(sameObject, phi, true);
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
                    ObjectState obj = states.get(i).objectState(value);
                    if (obj != null) {
                        materialized |= obj.materializedValue == null;
                        replaceWithMaterialized(value, phi, merge.forwardEndAt(i), states.get(i), obj);
                    }
                }
            }
            return materialized;
        }

        @Override
        protected BlockState loopBegin(LoopBeginNode loopBegin, BlockState beforeLoopState) {
            BlockState state = beforeLoopState;
            for (ObjectState obj : state.states()) {
                if (obj.fieldState != null) {
                    for (int i = 0; obj.fieldState != null && i < obj.fieldState.length; i++) {
                        ValueNode value = obj.fieldState[i];
                        ObjectState valueObj = state.objectState(value);
                        if (valueObj != null) {
                            ensureMaterialized(state, valueObj, loopBegin.forwardEnd());
                            value = valueObj.materializedValue;
                        }
                    }
                }
            }
            for (ObjectState obj : state.states()) {
                if (obj.fieldState != null) {
                    for (int i = 0; i < obj.fieldState.length; i++) {
                        ValueNode value = obj.fieldState[i];
                        ObjectState valueObj = state.objectState(value);
                        if (valueObj != null) {
                            value = valueObj.materializedValue;
                        }
                        if (changeGraph) {
                            assert value != null;
                            PhiNode valuePhi = graph.add(new PhiNode(value.kind(), loopBegin));
                            valuePhi.addInput(value);
                            obj.fieldState[i] = valuePhi;
                        }
                    }
                }
            }
            for (PhiNode phi : loopBegin.phis()) {
                ObjectState obj = state.objectState(phi.valueAt(0));
                if (obj != null) {
                    ensureMaterialized(state, obj, loopBegin.forwardEnd());
                    if (changeGraph) {
                        phi.setValueAt(0, obj.materializedValue);
                    }
                }
            }
            return state.clone();
        }

        @Override
        protected BlockState loopEnds(LoopBeginNode loopBegin, BlockState loopBeginState, List<BlockState> loopEndStates) {
            BlockState state = loopBeginState.clone();
            List<LoopEndNode> loopEnds = loopBegin.orderedLoopEnds();
            for (ObjectState obj : state.states()) {
                if (obj.fieldState != null) {
                    Iterator<LoopEndNode> iter = loopEnds.iterator();
                    for (BlockState loopEndState : loopEndStates) {
                        LoopEndNode loopEnd = iter.next();
                        ObjectState endObj = loopEndState.objectState(obj.virtual);
                        if (endObj.fieldState == null) {
                            if (changeGraph) {
                                error("object materialized within loop: %s\n", obj.virtual);
                            }
                            metricLoopBailouts.increment();
                            throw new BailoutException("object materialized within loop");
                        }
                        for (int i = 0; endObj.fieldState != null && i < endObj.fieldState.length; i++) {
                            ValueNode value = endObj.fieldState[i];
                            ObjectState valueObj = loopEndState.objectState(value);
                            if (valueObj != null) {
                                ensureMaterialized(loopEndState, valueObj, loopEnd);
                                value = valueObj.materializedValue;
                            }
                            if (changeGraph) {
                                ((PhiNode) obj.fieldState[i]).addInput(value);
                            }
                        }
                    }
                }
            }
            for (PhiNode phi : loopBegin.phis()) {
                if (phi.valueCount() == 1) {
                    if (changeGraph) {
                        phi.replaceAtUsages(phi.valueAt(0));
                    }
                } else {
                    assert phi.valueCount() == loopEndStates.size() + 1;
                    for (int i = 0; i < loopEndStates.size(); i++) {
                        BlockState loopEndState = loopEndStates.get(i);
                        ObjectState obj = loopEndState.objectState(phi.valueAt(i + 1));
                        if (obj != null) {
                            ensureMaterialized(loopEndState, obj, loopEnds.get(i));
                            if (changeGraph) {
                                phi.setValueAt(i + 1, obj.materializedValue);
                            }
                        }
                    }
                }
            }
            return state;
        }

        @Override
        protected BlockState afterSplit(FixedNode node, BlockState oldState) {
            return oldState.clone();
        }
    }
}

public class PartialEscapeAnalysisPhase extends Phase {

    private final TargetDescription target;
    private final GraalCodeCacheProvider runtime;
    private final Assumptions assumptions;

    public PartialEscapeAnalysisPhase(TargetDescription target, GraalCodeCacheProvider runtime, Assumptions assumptions) {
        this.runtime = runtime;
        this.target = target;
        this.assumptions = assumptions;
    }

    @Override
    protected void run(StructuredGraph graph) {
        iteration(graph, 0);
    }


    private void iteration(final StructuredGraph graph, final int num) {
        HashSet<ValueNode> allocations = new HashSet<>();
        SchedulePhase schedule = new SchedulePhase();
        schedule.apply(graph, false);
        EscapeAnalysisIteration iteration = null;
        try {
            iteration = new EscapeAnalysisIteration(graph, schedule, runtime, allocations, false);
            iteration.run();
        } catch (BailoutException e) {
            // do nothing if the if the escape analysis bails out during the analysis iteration...
            return;
        }
        if (iteration.changed) {
            try {
                new EscapeAnalysisIteration(graph, schedule, runtime, allocations, true).run();
                new CanonicalizerPhase(target, runtime, assumptions).apply(graph);
            } catch (BailoutException e) {
                throw new GraalInternalError(e);
            }
            // next round...
            if (num < 2) {
                Debug.scope("next", new Runnable() {
                    @Override
                    public void run() {
                        iteration(graph, num + 1);
                    }
                });
            }
        }
    }
}
