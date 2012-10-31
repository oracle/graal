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
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.virtual.*;
import com.oracle.graal.phases.graph.ReentrantBlockIterator.MergeableBlockState;
import com.oracle.graal.virtual.nodes.*;

class BlockState extends MergeableBlockState<BlockState> {

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
    public BlockState cloneState() {
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
        if (obj.getLockCount() > 0 && obj.virtual.type().isArrayClass()) {
            throw new BailoutException("array materialized with lock");
        }

        MaterializeObjectNode materialize = new MaterializeObjectNode(virtual, obj.getLockCount() > 0);
        ValueNode[] values = new ValueNode[obj.getEntries().length];
        materialize.setProbability(fixed.probability());
        ValueNode[] fieldState = obj.getEntries();
        obj.setMaterializedValue(materialize);
        deferred.add(virtual);
        for (int i = 0; i < fieldState.length; i++) {
            ObjectState valueObj = objectState(fieldState[i]);
            if (valueObj != null) {
                if (valueObj.getMaterializedValue() == null) {
                    materializeChangedBefore(fixed, valueObj.virtual, deferred, deferredStores, materializeEffects);
                }
                if (deferred.contains(valueObj.virtual)) {
                    Kind fieldKind;
                    CyclicMaterializeStoreNode store;
                    if (virtual instanceof VirtualArrayNode) {
                        store = new CyclicMaterializeStoreNode(materialize, valueObj.getMaterializedValue(), i);
                        fieldKind = ((VirtualArrayNode) virtual).componentType().getKind();
                    } else {
                        VirtualInstanceNode instanceObject = (VirtualInstanceNode) virtual;
                        store = new CyclicMaterializeStoreNode(materialize, valueObj.getMaterializedValue(), instanceObject.field(i));
                        fieldKind = instanceObject.field(i).getType().getKind();
                    }
                    deferredStores.addFixedNodeBefore(store, fixed);
                    values[i] = ConstantNode.defaultForKind(fieldKind, fixed.graph());
                } else {
                    values[i] = valueObj.getMaterializedValue();
                }
            } else {
                values[i] = fieldState[i];
            }
        }
        deferred.remove(virtual);

        materializeEffects.addMaterialization(materialize, fixed, values);
    }

    void addAndMarkAlias(VirtualObjectNode virtual, ValueNode node, NodeBitMap usages) {
        objectAliases.put(node, virtual);
        for (Node usage : node.usages()) {
            markVirtualUsages(usage, usages);
        }
    }

    private void markVirtualUsages(Node node, NodeBitMap usages) {
        if (!usages.isNew(node)) {
            usages.mark(node);
        }
        if (node instanceof VirtualState) {
            for (Node usage : node.usages()) {
                markVirtualUsages(usage, usages);
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

    public Iterable<VirtualObjectNode> virtualObjects() {
        return objectAliases.values();
    }

    @Override
    public String toString() {
        return objectStates.toString();
    }

    public static BlockState meetAliases(List<BlockState> states) {
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
        return newState;
    }
}
