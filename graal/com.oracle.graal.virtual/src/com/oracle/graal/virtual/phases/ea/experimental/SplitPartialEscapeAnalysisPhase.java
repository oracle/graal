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
package com.oracle.graal.virtual.phases.ea.experimental;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.ResolvedJavaType.Representation;
import com.oracle.graal.api.meta.*;
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
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.schedule.*;
import com.oracle.graal.virtual.nodes.*;
import com.oracle.graal.virtual.phases.ea.*;
import com.oracle.graal.virtual.phases.ea.experimental.EffectList.*;

class EscapeAnalysisIteration {

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

    private final HashSet<VirtualObjectNode> reusedVirtualObjects = new HashSet<>();
    private int virtualIds = 0;

    public EscapeAnalysisIteration(StructuredGraph graph, SchedulePhase schedule, MetaAccessProvider runtime) {
        this.graph = graph;
        this.schedule = schedule;
        this.runtime = runtime;
        this.usages = graph.createNodeBitMap();
    }

    public void run() {
        PartialEscapeClosure closure = new PartialEscapeClosure();
        ReentrantBlockIterator.apply(closure, schedule.getCFG().getStartBlock(), new BlockState(), null);
        ArrayList<Node> obsoleteNodes = new ArrayList<>();
        for (int i = 0; i < closure.effects.size(); i++) {
            Effect effect = closure.effects.get(i);
            effect.apply(graph, obsoleteNodes);
            if (GraalOptions.TraceEscapeAnalysis) {
                if (effect.isVisible()) {
                    int level = closure.effects.levelAt(i);
                    StringBuilder str = new StringBuilder();
                    for (int i2 = 0; i2 < level; i2++) {
                        str.append("    ");
                    }
                    trace(str.append(effect).toString());
                }
            }
        }
        Debug.dump(graph, "after PartialEscapeAnalysis");

        // helper code that determines the paths that keep obsolete nodes alive:
        //
        // NodeFlood flood = graph.createNodeFlood();
        // IdentityHashMap<Node, Node> path = new IdentityHashMap<>();
        // flood.add(graph.start());
        // for (Node current : flood) {
        // if (current instanceof EndNode) {
        // EndNode end = (EndNode) current;
        // flood.add(end.merge());
        // if (!path.containsKey(end.merge())) {
        // path.put(end.merge(), end);
        // }
        // } else {
        // for (Node successor : current.successors()) {
        // flood.add(successor);
        // if (!path.containsKey(successor)) {
        // path.put(successor, current);
        // }
        // }
        // }
        // }
        //
        // for (Node node : obsoleteNodes) {
        // if (node instanceof FixedNode) {
        // assert !flood.isMarked(node);
        // }
        // }
        //
        // for (Node node : graph.getNodes()) {
        // if (node instanceof LocalNode) {
        // flood.add(node);
        // }
        // if (flood.isMarked(node)) {
        // for (Node input : node.inputs()) {
        // flood.add(input);
        // if (!path.containsKey(input)) {
        // path.put(input, node);
        // }
        // }
        // }
        // }
        // for (Node current : flood) {
        // for (Node input : current.inputs()) {
        // flood.add(input);
        // if (!path.containsKey(input)) {
        // path.put(input, current);
        // }
        // }
        // }
        //
        // for (Node node : obsoleteNodes) {
        // if (flood.isMarked(node)) {
        // System.out.println("offending node path:");
        // Node current = node;
        // while (current != null) {
        // System.out.println(current);
        // current = path.get(current);
        // if (current != null && current instanceof FixedNode && !obsoleteNodes.contains(current)) {
        // break;
        // }
        // }
        // }
        // }

        new DeadCodeEliminationPhase().apply(graph);

    }

    private static class ObjectState {

        public final VirtualObjectNode virtual;
        public ValueNode[] fieldState;
        public ValueNode materializedValue;
        public int lockCount;

        public ObjectState(VirtualObjectNode virtual, ValueNode[] fieldState, int lockCount) {
            this.virtual = virtual;
            this.fieldState = fieldState;
            this.lockCount = lockCount;
        }

        public ObjectState(VirtualObjectNode virtual, ValueNode materializedValue, int lockCount) {
            this.virtual = virtual;
            this.materializedValue = materializedValue;
            this.lockCount = lockCount;
        }

        private ObjectState(ObjectState other) {
            virtual = other.virtual;
            fieldState = other.fieldState == null ? null : other.fieldState.clone();
            materializedValue = other.materializedValue;
            lockCount = other.lockCount;
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
        private final HashMap<ValueNode, ValueNode> scalarAliases = new HashMap<>();

        public BlockState() {
        }

        public BlockState(BlockState other) {
            for (Map.Entry<VirtualObjectNode, ObjectState> entry : other.objectStates.entrySet()) {
                objectStates.put(entry.getKey(), entry.getValue().clone());
            }
            for (Map.Entry<ValueNode, VirtualObjectNode> entry : other.objectAliases.entrySet()) {
                objectAliases.put(entry.getKey(), entry.getValue());
            }
            for (Map.Entry<ValueNode, ValueNode> entry : other.scalarAliases.entrySet()) {
                scalarAliases.put(entry.getKey(), entry.getValue());
            }
        }

        public ObjectState objectState(VirtualObjectNode object) {
            assert objectStates.containsKey(object);
            return objectStates.get(object);
        }

        public ObjectState objectStateOptional(VirtualObjectNode object) {
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

        public void materializeBefore(FixedNode fixed, VirtualObjectNode virtual, GraphEffectList materializeEffects) {
            HashSet<VirtualObjectNode> deferred = new HashSet<>();
            GraphEffectList deferredStores = new GraphEffectList();
            materializeChangedBefore(fixed, virtual, deferred, deferredStores, materializeEffects);
            materializeEffects.addAll(deferredStores);
        }

        private void materializeChangedBefore(FixedNode fixed, VirtualObjectNode virtual, HashSet<VirtualObjectNode> deferred, GraphEffectList deferredStores, GraphEffectList materializeEffects) {
            trace("materializing %s at %s", virtual, fixed);
            ObjectState obj = objectState(virtual);
            if (obj.lockCount > 0 && obj.virtual.type().isArrayClass()) {
//                error("array materialized with lock: %s\n", virtual);
                throw new BailoutException("array materialized with lock");
            }

            MaterializeObjectNode materialize = new MaterializeObjectNode(virtual, obj.lockCount > 0);
            ValueNode[] values = new ValueNode[obj.fieldState.length];
            materialize.setProbability(fixed.probability());
            ValueNode[] fieldState = obj.fieldState;
            obj.fieldState = null;
            obj.materializedValue = materialize;
            deferred.add(virtual);
            for (int i = 0; i < fieldState.length; i++) {
                ObjectState valueObj = objectState(fieldState[i]);
                if (valueObj != null) {
                    if (valueObj.materializedValue == null) {
                        materializeChangedBefore(fixed, valueObj.virtual, deferred, deferredStores, materializeEffects);
                    }
                    if (deferred.contains(valueObj.virtual)) {
                        Kind fieldKind;
                        CyclicMaterializeStoreNode store;
                        if (virtual instanceof VirtualArrayNode) {
                            store = new CyclicMaterializeStoreNode(materialize, valueObj.materializedValue, i);
                            fieldKind = ((VirtualArrayNode) virtual).componentType().getKind();
                        } else {
                            VirtualInstanceNode instanceObject = (VirtualInstanceNode) virtual;
                            store = new CyclicMaterializeStoreNode(materialize, valueObj.materializedValue, instanceObject.field(i));
                            fieldKind = instanceObject.field(i).getType().getKind();
                        }
                        deferredStores.addFixedNodeBefore(store, fixed);
                        values[i] = ConstantNode.defaultForKind(fieldKind, graph);
                    } else {
                        values[i] = valueObj.materializedValue;
                    }
                } else {
                    values[i] = fieldState[i];
                }
            }
            deferred.remove(virtual);

            materializeEffects.addMaterialization(materialize, fixed, values);
        }

        private void addAndMarkAlias(VirtualObjectNode virtual, ValueNode node) {
            objectAliases.put(node, virtual);
            for (Node usage : node.usages()) {
                markVirtualUsages(usage);
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

        public void addScalarAlias(ValueNode alias, ValueNode value) {
            scalarAliases.put(alias, value);
        }

        public ValueNode scalarAlias(ValueNode alias) {
            ValueNode result = scalarAliases.get(alias);
            return result == null ? alias : result;
        }

        public Iterable<ObjectState> states() {
            return objectStates.values();
        }

        @Override
        public String toString() {
            return objectStates.toString();
        }
    }

    private class PartialEscapeClosure extends BlockIteratorClosure<BlockState> {

        private final GraphEffectList effects = new GraphEffectList();

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
                // if (node instanceof NewInstanceNode) {
                // if (!((NewInstanceNode) node).instanceClass().name().contains("Key")) {
                // op = null;
                // }
                // } else {
                // op = null;
                // }

                if (op != null) {
                    trace("{{%s}} ", node);
                    VirtualObjectNode virtualObject = op.virtualObject(virtualIds);
                    if (virtualObject.isAlive()) {
                        reusedVirtualObjects.add(virtualObject);
                        state.addAndMarkAlias(virtualObject, virtualObject);
                    } else {
                        effects.addFloatingNode(virtualObject);
                    }
                    state.addObject(virtualObject, new ObjectState(virtualObject, op.fieldState(), 0));
                    state.addAndMarkAlias(virtualObject, (ValueNode) node);
                    effects.deleteFixedNode((FixedWithNextNode) node);
                    virtualIds++;
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
                ObjectState obj = state.objectState(value);
                if (obj != null) {
                    if (obj.materializedValue == null) {
                        state.addAndMarkAlias(obj.virtual, node);
                    } else {
                        effects.replaceFirstInput(node, value, obj.materializedValue);
                    }
                    usageFound = true;
                }
            } else if (node instanceof CheckCastNode) {
                CheckCastNode x = (CheckCastNode) node;
                ObjectState obj = state.objectState(x.object());
                if (obj != null) {
                    if (obj.materializedValue == null) {
                        if (x.targetClass() != null && obj.virtual.type().isSubtypeOf(x.targetClass())) {
                            state.addAndMarkAlias(obj.virtual, x);
                            effects.deleteFixedNode(x);
                        } else {
                            replaceWithMaterialized(x.object(), x, state, obj);
                        }
                    } else {
                        effects.replaceFirstInput(x, x.object(), obj.materializedValue);
                    }
                    usageFound = true;
                }
            } else if (node instanceof IsNullNode) {
                IsNullNode x = (IsNullNode) node;
                if (state.objectState(x.object()) != null) {
                    replaceAtUsages(state, x, graph.unique(ConstantNode.forBoolean(false, graph)));
                    usageFound = true;
                }
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
                    if (obj.materializedValue == null) {
                        effects.replaceFirstInput(node, x.object(), obj.virtual);
                        effects.eliminateMonitor(x);
                    } else {
                        effects.replaceFirstInput(node, x.object(), obj.materializedValue);
                    }
                    usageFound = true;
                }
            } else if (node instanceof CyclicMaterializeStoreNode) {
                CyclicMaterializeStoreNode x = (CyclicMaterializeStoreNode) node;
                ObjectState obj = state.objectState(x.object());
                if (obj != null) {
                    if (obj.virtual instanceof VirtualArrayNode) {
                        obj.fieldState[x.targetIndex()] = x.value();
                    } else {
                        VirtualInstanceNode instance = (VirtualInstanceNode) obj.virtual;
                        int index = instance.fieldIndex(x.targetField());
                        obj.fieldState[index] = x.value();
                    }
                    effects.deleteFixedNode(x);
                    usageFound = true;
                }
            } else if (node instanceof LoadFieldNode) {
                LoadFieldNode x = (LoadFieldNode) node;
                ObjectState obj = state.objectState(x.object());
                if (obj != null) {
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
                            state.addAndMarkAlias(resultObj.virtual, x);
                        } else {
                            replaceAtUsages(state, x, result);
                        }
                        effects.deleteFixedNode(x);
                    } else {
                        effects.replaceFirstInput(x, x.object(), obj.materializedValue);
                    }
                    usageFound = true;
                }
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
                        obj.fieldState[fieldIndex] = state.scalarAlias(value);
                        effects.deleteFixedNode(x);
                    } else {
                        effects.replaceFirstInput(x, object, obj.materializedValue);
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
                        ValueNode indexValue = state.scalarAlias(x.index());
                        int index = indexValue.isConstant() ? indexValue.asConstant().asInt() : -1;
                        if (index < 0 || index >= arrayObj.fieldState.length) {
                            // out of bounds or not constant
                            replaceWithMaterialized(array, x, state, arrayObj);
                        } else {
                            ValueNode result = arrayObj.fieldState[index];
                            ObjectState resultObj = state.objectState(result);
                            if (resultObj != null) {
                                state.addAndMarkAlias(resultObj.virtual, x);
                            } else {
                                replaceAtUsages(state, x, result);
                            }
                            effects.deleteFixedNode(x);
                        }
                    } else {
                        effects.replaceFirstInput(x, array, arrayObj.materializedValue);
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
                        ValueNode indexValue = state.scalarAlias(x.index());
                        int index = indexValue.isConstant() ? indexValue.asConstant().asInt() : -1;
                        if (index < 0 || index >= arrayObj.fieldState.length) {
                            // out of bounds or not constant
                            replaceWithMaterialized(array, x, state, arrayObj);
                            if (valueObj != null) {
                                replaceWithMaterialized(value, x, state, valueObj);
                            }
                        } else {
                            arrayObj.fieldState[index] = state.scalarAlias(value);
                            effects.deleteFixedNode(x);
                        }
                    } else {
                        effects.replaceFirstInput(x, array, arrayObj.materializedValue);
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
                if (obj != null) {
                    replaceWithMaterialized(x.object(), x, state, obj);
                    usageFound = true;
                }
            } else if (node instanceof ArrayLengthNode) {
                ArrayLengthNode x = (ArrayLengthNode) node;
                ObjectState obj = state.objectState(x.array());
                if (obj != null) {
                    replaceAtUsages(state, x, ConstantNode.forInt(((VirtualArrayNode) obj.virtual).entryCount(), graph));
                    effects.deleteFixedNode(x);
                    usageFound = true;
                }
            } else if (node instanceof LoadHubNode) {
                LoadHubNode x = (LoadHubNode) node;
                ObjectState obj = state.objectState(x.object());
                if (obj != null) {
                    replaceAtUsages(state, x, ConstantNode.forConstant(obj.virtual.type().getEncoding(Representation.ObjectHub), runtime, graph));
                    effects.deleteFixedNode(x);
                    usageFound = true;
                }
            } else if (node instanceof ReturnNode) {
                ReturnNode x = (ReturnNode) node;
                ObjectState obj = state.objectState(x.result());
                if (obj != null) {
                    replaceWithMaterialized(x.result(), x, state, obj);
                    usageFound = true;
                }
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

                if (xVirtual ^ yVirtual) {
                    // one of them is virtual: they can never be the same objects
                    replaceAtUsages(state, x, ConstantNode.forBoolean(false, graph));
                    usageFound = true;
                } else if (xVirtual && yVirtual) {
                    // both are virtual: check if they refer to the same object
                    replaceAtUsages(state, x, ConstantNode.forBoolean(xObj == yObj, graph));
                    usageFound = true;
                } else {
                    if (xObj != null || yObj != null) {
                        if (xObj != null) {
                            assert xObj.materializedValue != null;
                            effects.replaceFirstInput(x, x.x(), xObj.materializedValue);
                        }
                        if (yObj != null) {
                            assert yObj.materializedValue != null;
                            effects.replaceFirstInput(x, x.y(), yObj.materializedValue);
                        }
                        usageFound = true;
                    }
                }
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
                                effects.replaceFirstInput(usage, value, valueObj.virtual);
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
                            v = new VirtualObjectState(obj.virtual, fieldState);
                        } else {
                            v = new MaterializedObjectState(obj.virtual, obj.materializedValue);
                        }
                        effects.addVirtualMapping(stateAfter, v, reusedVirtualObjects);
                    }
                }
                usageFound = true;
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

        private void replaceAtUsages(final BlockState state, ValueNode x, ValueNode value) {
            effects.replaceAtUsages(x, value);
            state.addScalarAlias(x, value);
        }

        private void ensureMaterialized(BlockState state, ObjectState obj, FixedNode materializeBefore) {
            assert obj != null;
            if (obj.materializedValue == null) {
                state.materializeBefore(materializeBefore, obj.virtual, effects);
            }
            assert obj.materializedValue != null;
        }

        private void replaceWithMaterialized(ValueNode value, FixedNode usage, BlockState state, ObjectState obj) {
            ensureMaterialized(state, obj, usage);
            effects.replaceFirstInput(usage, value, obj.materializedValue);
        }

        private void replaceWithMaterialized(ValueNode value, Node usage, FixedNode materializeBefore, BlockState state, ObjectState obj) {
            ensureMaterialized(state, obj, materializeBefore);
            effects.replaceFirstInput(usage, value, obj.materializedValue);
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

            newState.scalarAliases.putAll(states.get(0).scalarAliases);
            for (int i = 1; i < states.size(); i++) {
                BlockState state = states.get(i);
                for (Map.Entry<ValueNode, ValueNode> entry : states.get(0).scalarAliases.entrySet()) {
                    if (state.scalarAliases.containsKey(entry.getKey())) {
                        assert state.scalarAliases.get(entry.getKey()) == entry.getValue();
                    } else {
                        newState.scalarAliases.remove(entry.getKey());
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
                        ValueNode singleValue = states.get(0).objectState(object).materializedValue;
                        for (BlockState state : states) {
                            ObjectState obj = state.objectState(object);
                            if (obj.materializedValue == null) {
                                virtual++;
                                singleValue = null;
                            } else {
                                if (obj.materializedValue != singleValue) {
                                    singleValue = null;
                                }
                            }
                            assert obj.lockCount == lockCount : "mismatching lock counts";
                        }

                        if (virtual < states.size()) {
                            if (singleValue == null) {
                                PhiNode materializedValuePhi = new PhiNode(Kind.Object, merge);
                                effects.addFloatingNode(materializedValuePhi);
                                for (int i = 0; i < states.size(); i++) {
                                    BlockState state = states.get(i);
                                    ObjectState obj = state.objectState(object);
                                    materialized |= obj.materializedValue == null;
                                    ensureMaterialized(state, obj, merge.forwardEndAt(i));
                                    effects.addPhiInput(materializedValuePhi, obj.materializedValue);
                                }
                                newState.addObject(object, new ObjectState(object, materializedValuePhi, lockCount));
                            } else {
                                newState.addObject(object, new ObjectState(object, singleValue, lockCount));
                            }
                        } else {
                            assert virtual == states.size();
                            ValueNode[] values = states.get(0).objectState(object).fieldState.clone();
                            PhiNode[] phis = new PhiNode[values.length];
                            int mismatch = 0;
                            for (int i = 1; i < states.size(); i++) {
                                BlockState state = states.get(i);
                                ValueNode[] fields = state.objectState(object).fieldState;
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
                                    ValueNode[] fields = state.objectState(object).fieldState;
                                    for (int index = 0; index < values.length; index++) {
                                        if (phis[index] != null) {
                                            ObjectState obj = state.objectState(fields[index]);
                                            if (obj != null) {
                                                materialized |= obj.materializedValue == null;
                                                ensureMaterialized(state, obj, merge.forwardEndAt(i));
                                                fields[index] = obj.materializedValue;
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
                        effects.setPhiInput(phi, obj.materializedValue, i);
                    }
                }
            }
            boolean materialize = false;
            if (virtualInputs == 0) {
                // nothing to do...
            } else if (virtualInputs == phi.valueCount()) {
                if (sameObject != null) {
                    newState.addAndMarkAlias(sameObject, phi);
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
        protected BlockState afterSplit(FixedNode node, BlockState oldState) {
            return oldState.clone();
        }

        @Override
        protected List<BlockState> processLoop(Loop loop, BlockState initialState) {
            GraphEffectList successEffects = new GraphEffectList();
            HashSet<PhiDesc> phis = new HashSet<>();
            for (int iteration = 0; iteration < 10; iteration++) {
                BlockState state = initialState.clone();
                int checkpoint = effects.checkpoint();

                for (PhiDesc desc : phis) {
                    ObjectState obj = state.objectState(desc.virtualObject);
                    if (obj.materializedValue == null) {
                        ValueNode value = obj.fieldState[desc.fieldIndex];
                        ObjectState valueObj = state.objectState(value);
                        if (valueObj != null) {
                            assert valueObj.materializedValue != null;
                            value = valueObj.materializedValue;
                        }

                        PhiNode phiNode = new PhiNode(value.kind(), loop.loopBegin());
                        effects.addFloatingNode(phiNode);
                        effects.addPhiInput(phiNode, value);
                        obj.fieldState[desc.fieldIndex] = phiNode;
                    }
                }

                for (PhiNode phi : loop.loopBegin().phis()) {
                    if (usages.isMarked(phi) && phi.type() == PhiType.Value) {
                        ObjectState initialObj = initialState.objectState(phi.valueAt(0));
                        if (initialObj != null) {
                            if (initialObj.materializedValue == null) {
                                state.addAndMarkAlias(initialObj.virtual, phi);
                            } else {
                                successEffects.setPhiInput(phi, initialObj.materializedValue, 0);
                            }
                        }
                    }
                }

                effects.incLevel();
                LoopInfo<BlockState> info = ReentrantBlockIterator.processLoop(this, loop, state.clone());

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
                        ObjectState obj = initialState.objectState(virtualObject);
                        if (obj.materializedValue == null) {
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
                ObjectState obj = exitState.objectState(proxy.value());
                if (obj != null) {
                    proxies.put(obj.virtual, proxy);
                }
            }
            for (ObjectState obj : exitState.states()) {
                ObjectState initialObj = initialState.objectStateOptional(obj.virtual);
                if (obj.materializedValue == null) {
                    for (int i = 0; i < obj.fieldState.length; i++) {
                        ValueNode value = obj.fieldState[i];
                        ObjectState valueObj = exitState.objectState(value);
                        if (valueObj == null) {
                            if ((value instanceof PhiNode && ((PhiNode) value).merge() == exitNode.loopBegin()) || initialObj == null || initialObj.materializedValue != null ||
                                            initialObj.fieldState[i] != value) {
                                ValueProxyNode proxy = new ValueProxyNode(value, exitNode, PhiType.Value);
                                obj.fieldState[i] = proxy;
                                effects.addFloatingNode(proxy);
                            }
                        }
                    }
                } else {
                    if (initialObj == null || initialObj.materializedValue == null) {
                        ValueProxyNode proxy = proxies.get(obj.virtual);
                        if (proxy == null) {
                            proxy = new ValueProxyNode(obj.materializedValue, exitNode, PhiType.Value);
                            effects.addFloatingNode(proxy);
                        } else {
                            effects.replaceFirstInput(proxy, proxy.value(), obj.materializedValue);
                            // nothing to do - will be handled in processNode
                        }
                        obj.materializedValue = proxy;
                    } else {
                        assert initialObj.materializedValue == obj.materializedValue : "materialized value is not allowed to change within loops: " + initialObj.materializedValue + " vs. " +
                                        obj.materializedValue;
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
                for (ObjectState state : initialState.states()) {
                    ObjectState endState = loopEndState.objectState(state.virtual);
                    if (state.materializedValue == null) {
                        if (endState.materializedValue == null) {
                            assert state.fieldState.length == endState.fieldState.length;
                            for (int i = 0; endState.fieldState != null && i < state.fieldState.length; i++) {
                                ValueNode value = state.fieldState[i];
                                ValueNode endValue = endState.fieldState[i];
                                ObjectState valueObj = initialState.objectState(value);
                                ObjectState endValueObj = loopEndState.objectState(endValue);

                                if (valueObj != null) {
                                    if (valueObj.materializedValue == null) {
                                        if (endValueObj == null || endValueObj.materializedValue != null || valueObj.virtual != endValueObj.virtual) {
                                            additionalMaterializations.add(valueObj.virtual);
                                        } else {
                                            // endValue is also virtual and refers to the same virtual object, so we're
                                            // good.
                                        }
                                    }
                                } else {
                                    if (value instanceof PhiNode && ((PhiNode) value).merge() == loopBegin) {
                                        if (endValueObj != null) {
                                            if (endValueObj.materializedValue == null) {
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
                        ObjectState initialObj = initialState.objectState(phi.valueAt(0));
                        boolean initialMaterialized = initialObj == null || initialObj.materializedValue != null;

                        ObjectState loopEndObj = loopEndState.objectState(phi.valueAt(loopEnd));
                        if (loopEndObj == null || loopEndObj.materializedValue != null) {
                            if (loopEndObj != null) {
                                successEffects.setPhiInput(phi, loopEndObj.materializedValue, loopBegin.phiPredecessorIndex(loopEnd));
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

            for (ObjectState state : initialState.states()) {
                ObjectState endState = loopEndState.objectState(state.virtual);
                if (state.materializedValue == null) {
                    if (endState.materializedValue == null) {
                        assert state.fieldState.length == endState.fieldState.length;
                        for (int i = 0; i < state.fieldState.length; i++) {
                            ValueNode value = state.fieldState[i];
                            ValueNode endValue = endState.fieldState[i];
                            ObjectState valueObj = initialState.objectState(value);
                            ObjectState endValueObj = loopEndState.objectState(endValue);

                            if (valueObj != null) {
                                if (valueObj.materializedValue == null) {
                                    if (endValueObj == null || endValueObj.materializedValue != null || valueObj.virtual != endValueObj.virtual) {
                                        assert !additionalMaterializations.isEmpty();
                                    } else {
                                        // endValue is also virtual and refers to the same virtual object, so we're
                                        // good.
                                    }
                                } else {
                                    if ((endValueObj != null && endValueObj.materializedValue != valueObj.materializedValue) || (endValueObj == null && valueObj.materializedValue != endValue)) {
                                        phis.add(new PhiDesc(state.virtual, i));
                                    } else {
                                        // either endValue has the same materialized value as value or endValue is the
                                        // same as the materialized value, so we're good.
                                    }
                                }
                            } else {
                                if (value instanceof PhiNode && ((PhiNode) value).merge() == loopBegin) {
                                    if (endValueObj != null) {
                                        if (endValueObj.materializedValue == null) {
                                            assert !additionalMaterializations.isEmpty();
                                        }
                                        successEffects.addPhiInput((PhiNode) value, endValueObj.materializedValue);
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
                    if (endState.materializedValue == null) {
                        // throw new GraalInternalError("un-materialized object state at %s", loopEnd);
                    } else {
                        if (state.materializedValue != endState.materializedValue) {
                            // throw new GraalInternalError("changed materialized value during loop: %s vs %s",
                            // state.materializedValue, endState.materializedValue);
                        }
                    }
                }
            }
        }
    }
}

public class SplitPartialEscapeAnalysisPhase extends Phase {

    private final GraalCodeCacheProvider runtime;

    public SplitPartialEscapeAnalysisPhase(GraalCodeCacheProvider runtime) {
        this.runtime = runtime;
    }

    @Override
    protected void run(StructuredGraph graph) {
        SchedulePhase schedule = new SchedulePhase();
        schedule.apply(graph, false);
        new EscapeAnalysisIteration(graph, schedule, runtime).run();
    }
}
