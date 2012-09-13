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

class EscapeRecord {

    public final ResolvedJavaType type;
    public final EscapeField[] fields;
    public final HashMap<Object, Integer> fieldMap = new HashMap<>();
    public final VirtualObjectNode virtualObject;

    public EscapeRecord(ResolvedJavaType type, EscapeField[] fields, VirtualObjectNode virtualObject) {
        this.type = type;
        this.fields = fields;
        this.virtualObject = virtualObject;
        for (int i = 0; i < fields.length; i++) {
            fieldMap.put(fields[i].representation(), i);
        }
    }

    @Override
    public String toString() {
        return MetaUtil.toJavaName(type, false) + "@" + (System.identityHashCode(this) % 10000);
    }
}

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
    private final NodeBitMap visitedNodes;
    private boolean changed = false;

    private final boolean changeGraph;

    private final HashSet<ValueNode> allocations;
    private final ArrayList<ValueNode> obsoleteNodes = new ArrayList<>();
    private int virtualIds = 0;

    public EscapeAnalysisIteration(StructuredGraph graph, SchedulePhase schedule, MetaAccessProvider runtime, HashSet<ValueNode> allocations, boolean changeGraph) {
        this.graph = graph;
        this.schedule = schedule;
        this.runtime = runtime;
        this.allocations = allocations;
        this.changeGraph = changeGraph;
        this.visitedNodes = graph.createNodeBitMap();
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

            if (GraalOptions.TraceEscapeAnalysis) {
                for (Node node : graph.getNodes()) {
                    if (!visitedNodes.isMarked(node) && !(node instanceof VirtualState) && !(node instanceof VirtualObjectNode)) {
                        trace("unvisited node: %s", node);
                    }
                }
            }
        }
    }

    private static class ObjectState {

        public final EscapeRecord record;
        public ValueNode[] fieldState;
        public ValueNode materializedValue;
        public int lockCount;
        public boolean initialized;

        public ObjectState(EscapeRecord record, ValueNode[] fieldState, int lockCount) {
            this.record = record;
            this.fieldState = fieldState;
            this.lockCount = lockCount;
            this.initialized = false;
        }

        public ObjectState(EscapeRecord record, ValueNode materializedValue, int lockCount) {
            this.record = record;
            this.materializedValue = materializedValue;
            this.lockCount = lockCount;
            this.initialized = true;
        }

        private ObjectState(ObjectState other) {
            record = other.record;
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
                    str.append(record.fields[i].name()).append('=').append(fieldState[i]).append(' ');
                }
            }
            if (materializedValue != null) {
                str.append("mat=").append(materializedValue);
            }

            return str.append('}').toString();
        }
    }

    private class BlockState implements MergeableBlockState<BlockState> {

        private final HashMap<EscapeRecord, ObjectState> recordStates = new HashMap<>();
        private final HashMap<ValueNode, EscapeRecord> recordAliases = new HashMap<>();

        public BlockState() {
        }

        public BlockState(BlockState other) {
            for (Map.Entry<EscapeRecord, ObjectState> entry : other.recordStates.entrySet()) {
                recordStates.put(entry.getKey(), entry.getValue().clone());
            }
            for (Map.Entry<ValueNode, EscapeRecord> entry : other.recordAliases.entrySet()) {
                recordAliases.put(entry.getKey(), entry.getValue());
            }
        }

        public ObjectState objectState(EscapeRecord record) {
            assert recordStates.containsKey(record);
            return recordStates.get(record);
        }

        public ObjectState objectState(ValueNode value) {
            EscapeRecord record = recordAliases.get(value);
            return record == null ? null : objectState(record);
        }

        @Override
        public BlockState clone() {
            return new BlockState(this);
        }

        public void materializeBefore(FixedNode fixed, EscapeRecord record) {
            if (changeGraph) {
                HashSet<EscapeRecord> deferred = new HashSet<>();
                ArrayList<FixedWithNextNode> deferredStores = new ArrayList<>();
                materializeBefore(fixed, record, deferred, deferredStores);
                for (FixedWithNextNode write : deferredStores) {
                    write.setProbability(fixed.probability());
                    graph.addBeforeFixed(fixed, write);
                }
            } else {
                materializeUnchangedBefore(record);
            }
        }

        private void materializeUnchangedBefore(EscapeRecord record) {
            trace("materializing %s", record);
            ObjectState obj = objectState(record);
            if (obj.lockCount > 0) {
                if (changeGraph) {
                    error("object materialized with lock: %s\n", record);
                }
                throw new BailoutException("object materialized with lock");
            }

            ValueNode[] fieldState = obj.fieldState;
            obj.fieldState = null;
            obj.materializedValue = DUMMY_NODE;
            for (int i = 0; i < fieldState.length; i++) {
                ObjectState valueObj = objectState(fieldState[i]);
                if (valueObj != null) {
                    if (valueObj.materializedValue == null) {
                        materializeUnchangedBefore(valueObj.record);
                    }
                }
            }
            obj.initialized = true;
        }

        private void materializeBefore(FixedNode fixed, EscapeRecord record, HashSet<EscapeRecord> deferred, ArrayList<FixedWithNextNode> deferredStores) {
            trace("materializing %s at %s", record, fixed);
            ObjectState obj = objectState(record);
            if (obj.lockCount > 0) {
                error("object materialized with lock: %s\n", record);
                throw new BailoutException("object materialized with lock");
            }

            MaterializeObjectNode materialize = graph.add(new MaterializeObjectNode(record.type, record.fields));
            materialize.setProbability(fixed.probability());
            ValueNode[] fieldState = obj.fieldState;
            metricMaterializations.increment();
            metricMaterializationFields.add(fieldState.length);
            obj.fieldState = null;
            obj.materializedValue = materialize;
            deferred.add(record);
            for (int i = 0; i < fieldState.length; i++) {
                ObjectState valueObj = objectState(fieldState[i]);
                if (valueObj != null) {
                    if (valueObj.materializedValue == null) {
                        materializeBefore(fixed, valueObj.record, deferred, deferredStores);
                    }
                    if (deferred.contains(valueObj.record)) {
                        if (record.type.isArrayClass()) {
                            deferredStores.add(graph.add(new StoreIndexedNode(materialize, ConstantNode.forInt(i, graph), record.type.componentType().kind(), valueObj.materializedValue, -1)));
                        } else {
                            deferredStores.add(graph.add(new StoreFieldNode(materialize, (ResolvedJavaField) record.fields[i].representation(), valueObj.materializedValue, -1)));
                        }
                        materialize.values().set(i, ConstantNode.defaultForKind(record.fields[i].type().kind(), graph));
                    } else {
                        assert valueObj.initialized : "should be initialized: " + record + " at " + fixed;
                        materialize.values().set(i, valueObj.materializedValue);
                    }
                } else {
                    materialize.values().set(i, fieldState[i]);
                }
            }
            deferred.remove(record);

            obj.initialized = true;
            graph.addBeforeFixed(fixed, materialize);
        }

        private void addAndMarkAlias(EscapeRecord record, ValueNode node) {
            addAlias(record, node);
            for (Node usage : node.usages()) {
                assert !visitedNodes.isMarked(usage) : "used by already visited node: " + node + " -> " + usage;
                usages.mark(usage);
                if (usage instanceof VirtualState) {
                    markVirtualUsages(usage);
                }
            }
            obsoleteNodes.add(node);
        }

        private void markVirtualUsages(Node node) {
            usages.mark(node);
            if (node instanceof VirtualState) {
                for (Node usage : node.usages()) {
                    markVirtualUsages(usage);
                }
            }
        }

        public void addAlias(EscapeRecord record, ValueNode alias) {
            recordAliases.put(alias, record);
        }

        public void addRecord(EscapeRecord record, ObjectState state) {
            recordStates.put(record, state);
        }

        public Iterable<ObjectState> states() {
            return recordStates.values();
        }

        @Override
        public String toString() {
            return recordStates.toString();
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
                    changed = true;
                    trace("{{%s}} ", node);
                    ResolvedJavaType type = op.type();
                    EscapeField[] fields = op.fields();
                    VirtualObjectNode virtualObject = changeGraph ? graph.add(new VirtualObjectNode(virtualIds, type, fields.length)) : null;
                    EscapeRecord record = new EscapeRecord(type, fields, virtualObject);
                    ValueNode[] fieldState = changeGraph ? op.fieldState() : new ValueNode[fields.length];
                    if (changeGraph) {
                        metricAllocationRemoved.increment();
                        metricAllocationFieldsRemoved.add(fieldState.length);
                    } else {
                        allocations.add((ValueNode) node);
                    }
                    state.addRecord(record, new ObjectState(record, fieldState, 0));
                    state.addAndMarkAlias(record, (ValueNode) node);
                    virtualIds++;
                } else {
                    visitedNodes.mark(node);

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
                    state.addAndMarkAlias(obj.record, node);
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
                    if (x.targetClass() != null && obj.record.type.isSubtypeOf(x.targetClass())) {
                        metricOtherRemoved.increment();
                        state.addAndMarkAlias(obj.record, x);
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
            } else if (node instanceof IsTypeNode) {
                throw new GraalInternalError("a newly created object can never be an object hub");
            } else if (node instanceof AccessMonitorNode) {
                AccessMonitorNode x = (AccessMonitorNode) node;
                ObjectState obj = state.objectState(x.object());
                if (obj != null) {
                    Debug.log("monitor operation %s on %s\n", x, obj.record);
                    if (node instanceof MonitorEnterNode) {
                        obj.lockCount++;
                    } else {
                        assert node instanceof MonitorExitNode;
                        obj.lockCount--;
                    }
                    if (changeGraph) {
                        if (obj.materializedValue == null) {
                            metricLockRemoved.increment();
                            node.replaceFirstInput(x.object(), obj.record.virtualObject);
                            x.eliminate();
                        } else {
                            node.replaceFirstInput(x.object(), obj.materializedValue);
                        }
                    }
                    usageFound = true;
                }
            } else if (node instanceof LoadFieldNode) {
                LoadFieldNode x = (LoadFieldNode) node;
                ObjectState obj = state.objectState(x.object());
                assert obj != null : x;
                if (!obj.record.fieldMap.containsKey(x.field())) {
                    // the field does not exist in the virtual object
                    ensureMaterialized(state, obj, x);
                }
                if (obj.materializedValue == null) {
                    int index = obj.record.fieldMap.get(x.field());
                    ValueNode result = obj.fieldState[index];
                    ObjectState resultObj = state.objectState(result);
                    if (resultObj != null) {
                        state.addAndMarkAlias(resultObj.record, x);
                    } else {
                        if (changeGraph) {
                            x.replaceAtUsages(result);
                            graph.removeFixed(x);
                        }
                    }
                    if (changeGraph) {
                        metricLoadRemoved.increment();
                    }
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
                    if (!obj.record.fieldMap.containsKey(x.field())) {
                        // the field does not exist in the virtual object
                        ensureMaterialized(state, obj, x);
                    }
                    if (obj.materializedValue == null) {
                        int index = obj.record.fieldMap.get(x.field());
                        obj.fieldState[index] = value;
                        if (changeGraph) {
                            graph.removeFixed(x);
                            metricStoreRemoved.increment();
                        }
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
                                state.addAndMarkAlias(resultObj.record, x);
                            } else {
                                if (changeGraph) {
                                    x.replaceAtUsages(result);
                                    graph.removeFixed(x);
                                }
                            }
                            if (changeGraph) {
                                metricLoadRemoved.increment();
                            }
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
                    graph.replaceFixedWithFloating(x, ConstantNode.forInt(obj.record.fields.length, graph));
                    metricOtherRemoved.increment();
                }
                usageFound = true;
            } else if (node instanceof ReadHubNode) {
                ReadHubNode x = (ReadHubNode) node;
                ObjectState obj = state.objectState(x.object());
                assert obj != null : x;
                if (changeGraph) {
                    ConstantNode hub = ConstantNode.forConstant(obj.record.type.getEncoding(Representation.ObjectHub), runtime, graph);
                    graph.replaceFixedWithFloating(x, hub);
                    metricOtherRemoved.increment();
                }
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
                    } else if (xVirtual && yVirtual) {
                        // both are virtual: check if they refer to the same object
                        graph.replaceFloating(x, ConstantNode.forBoolean(xObj == yObj, graph));
                        usageFound = true;
                        metricOtherRemoved.increment();
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
                                    usage.replaceFirstInput(value, valueObj.record.virtualObject);
                                } else if (value instanceof VirtualObjectNode) {
                                    ObjectState virtualObj = null;
                                    for (ObjectState obj : state.states()) {
                                        if (value == obj.record.virtualObject) {
                                            virtualObj = obj;
                                            break;
                                        }
                                    }
                                    assert virtualObj != null;
                                    virtual.add(virtualObj);
                                }
                            }
                        });
                        for (ObjectState obj : state.states()) {
                            if (obj.lockCount > 0) {
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
                                            fieldState[i] = valueObj.record.virtualObject;
                                        } else {
                                            fieldState[i] = valueObj.materializedValue;
                                        }
                                    }
                                }
                                v = graph.add(new VirtualObjectState(obj.record.virtualObject, fieldState));
                            } else {
                                v = graph.add(new MaterializedObjectState(obj.record.virtualObject, obj.materializedValue));
                            }
                            for (EscapeObjectState s : stateAfter.virtualObjectMappings()) {
                                if (s.object() == v.object()) {
                                    throw new GraalInternalError("unexpected duplicate virtual state at: %s for %s", node, v.object());
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
                state.materializeBefore(materializeBefore, obj.record);
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

            newState.recordAliases.putAll(states.get(0).recordAliases);
            for (int i = 1; i < states.size(); i++) {
                BlockState state = states.get(i);
                for (Map.Entry<ValueNode, EscapeRecord> entry : states.get(0).recordAliases.entrySet()) {
                    if (state.recordAliases.containsKey(entry.getKey())) {
                        assert state.recordAliases.get(entry.getKey()) == entry.getValue();
                    } else {
                        newState.recordAliases.remove(entry.getKey());
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
                for (EscapeRecord record : new HashSet<>(newState.recordAliases.values())) {
                    ObjectState resultState = newState.recordStates.get(record);
                    if (resultState == null || resultState.materializedValue == null) {
                        int virtual = 0;
                        int lockCount = states.get(0).objectState(record).lockCount;
                        for (BlockState state : states) {
                            ObjectState obj = state.objectState(record);
                            if (obj.materializedValue == null) {
                                virtual++;
                            }
                            assert obj.lockCount == lockCount : "mismatching lock counts";
                        }

                        if (virtual < states.size()) {
                            ValueNode materializedValuePhi = changeGraph ? graph.add(new PhiNode(Kind.Object, merge)) : DUMMY_NODE;
                            for (int i = 0; i < states.size(); i++) {
                                BlockState state = states.get(i);
                                ObjectState obj = state.objectState(record);
                                materialized |= obj.materializedValue == null;
                                ensureMaterialized(state, obj, merge.forwardEndAt(i));
                                if (changeGraph) {
                                    ((PhiNode) materializedValuePhi).addInput(obj.materializedValue);
                                }
                            }
                            newState.addRecord(record, new ObjectState(record, materializedValuePhi, lockCount));
                        } else {
                            assert virtual == states.size();
                            ValueNode[] values = states.get(0).objectState(record).fieldState.clone();
                            PhiNode[] phis = new PhiNode[values.length];
                            boolean[] phiCreated = new boolean[values.length];
                            int mismatch = 0;
                            for (int i = 1; i < states.size(); i++) {
                                BlockState state = states.get(i);
                                ValueNode[] fields = state.objectState(record).fieldState;
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
                                    ValueNode[] fields = state.objectState(record).fieldState;
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
                            newState.addRecord(record, new ObjectState(record, values, lockCount));
                        }
                    }
                }

                for (PhiNode phi : merge.phis().snapshot()) {
                    if (usages.isMarked(phi) && phi.type() == PhiType.Value) {
                        visitedNodes.mark(phi);
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
            EscapeRecord sameRecord = null;
            ResolvedJavaType sameType = null;
            int sameFieldCount = -1;
            for (int i = 0; i < phi.valueCount(); i++) {
                ValueNode value = phi.valueAt(i);
                ObjectState obj = states.get(i).objectState(value);
                if (obj != null) {
                    if (obj.materializedValue == null) {
                        virtualInputs++;
                        if (i == 0) {
                            sameRecord = obj.record;
                            sameType = obj.record.type;
                            sameFieldCount = obj.record.fields.length;
                        } else {
                            if (sameRecord != obj.record) {
                                sameRecord = null;
                            }
                            if (sameType != obj.record.type) {
                                sameType = null;
                            }
                            if (sameFieldCount != obj.record.fields.length) {
                                sameFieldCount = -1;
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
                if (sameRecord != null) {
                    newState.addAndMarkAlias(sameRecord, phi);
                } else if (sameType != null && sameFieldCount != -1) {
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
                        ObjectState endObj = loopEndState.objectState(obj.record);
                        if (endObj.fieldState == null) {
                            if (changeGraph) {
                                error("object materialized within loop: %s\n", obj.record);
                            }
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
        HashSet<ValueNode> allocations = new HashSet<>();
        SchedulePhase schedule = new SchedulePhase();
        schedule.apply(graph, false);
        try {
            new EscapeAnalysisIteration(graph, schedule, runtime, allocations, false).run();
        } catch (BailoutException e) {
            // do nothing if the if the escape analysis bails out during the analysis iteration...
            return;
        }
        try {
            new EscapeAnalysisIteration(graph, schedule, runtime, allocations, true).run();
            new CanonicalizerPhase(target, runtime, assumptions).apply(graph);
        } catch (BailoutException e) {
            throw new GraalInternalError(e);
        }
    }

    public static boolean isValidConstantIndex(AccessIndexedNode x) {
        Constant index = x.index().asConstant();
        if (x.array() instanceof NewArrayNode) {
            Constant length = ((NewArrayNode) x.array()).dimension(0).asConstant();
            return index != null && length != null && index.asInt() >= 0 && index.asInt() < length.asInt();
        } else {
            return false;
        }
    }
}
