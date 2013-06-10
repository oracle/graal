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

import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.Virtualizable.EscapeState;
import com.oracle.graal.nodes.virtual.*;

public abstract class PartialEscapeBlockState<T extends PartialEscapeBlockState<T>> extends EffectsBlockState<T> {

    protected final IdentityHashMap<VirtualObjectNode, ObjectState> objectStates = new IdentityHashMap<>();
    protected final IdentityHashMap<ValueNode, VirtualObjectNode> objectAliases;

    /**
     * Final subclass of PartialEscapeBlockState, for performance and to make everything behave
     * nicely with generics.
     */
    public static final class Final extends PartialEscapeBlockState<Final> {

        public Final() {
        }

        public Final(Final other) {
            super(other);
        }
    }

    protected PartialEscapeBlockState() {
        objectAliases = new IdentityHashMap<>();
    }

    protected PartialEscapeBlockState(PartialEscapeBlockState<T> other) {
        super(other);
        for (Map.Entry<VirtualObjectNode, ObjectState> entry : other.objectStates.entrySet()) {
            objectStates.put(entry.getKey(), entry.getValue().cloneState());
        }
        objectAliases = new IdentityHashMap<>(other.objectAliases);
    }

    public ObjectState getObjectState(VirtualObjectNode object) {
        assert objectStates.containsKey(object);
        return objectStates.get(object);
    }

    public ObjectState getObjectStateOptional(VirtualObjectNode object) {
        return objectStates.get(object);
    }

    public ObjectState getObjectState(ValueNode value) {
        VirtualObjectNode object = objectAliases.get(value);
        return object == null ? null : getObjectState(object);
    }

    public void materializeBefore(FixedNode fixed, VirtualObjectNode virtual, EscapeState state, GraphEffectList materializeEffects) {
        PartialEscapeClosure.METRIC_MATERIALIZATIONS.increment();
        List<AllocatedObjectNode> objects = new ArrayList<>(2);
        List<ValueNode> values = new ArrayList<>(8);
        List<int[]> locks = new ArrayList<>(2);
        List<ValueNode> otherAllocations = new ArrayList<>(2);
        materializeWithCommit(fixed, virtual, objects, locks, values, otherAllocations, state);

        materializeEffects.addMaterializationBefore(fixed, objects, locks, values, otherAllocations);
    }

    private void materializeWithCommit(FixedNode fixed, VirtualObjectNode virtual, List<AllocatedObjectNode> objects, List<int[]> locks, List<ValueNode> values, List<ValueNode> otherAllocations,
                    EscapeState state) {
        ObjectState obj = getObjectState(virtual);

        ValueNode[] entries = obj.getEntries();
        ValueNode representation = virtual.getMaterializedRepresentation(fixed, entries, obj.getLocks());
        obj.escape(representation, state);
        if (representation instanceof AllocatedObjectNode) {
            objects.add((AllocatedObjectNode) representation);
            locks.add(obj.getLocks());
            int pos = values.size();
            while (values.size() < pos + entries.length) {
                values.add(null);
            }
            for (int i = 0; i < entries.length; i++) {
                ObjectState entryObj = getObjectState(entries[i]);
                if (entryObj != null) {
                    if (entryObj.isVirtual()) {
                        materializeWithCommit(fixed, entryObj.getVirtualObject(), objects, locks, values, otherAllocations, state);
                    }
                    values.set(pos + i, entryObj.getMaterializedValue());
                } else {
                    values.set(pos + i, entries[i]);
                }
            }
            objectMaterialized(virtual, (AllocatedObjectNode) representation, values.subList(pos, pos + entries.length));
        } else {
            VirtualUtil.trace("materialized %s as %s", virtual, representation);
            otherAllocations.add(representation);
            assert obj.getLocks().length == 0;
        }
    }

    protected void objectMaterialized(VirtualObjectNode virtual, AllocatedObjectNode representation, List<ValueNode> values) {
        VirtualUtil.trace("materialized %s as %s with values %s", virtual, representation, values);
    }

    void addAndMarkAlias(VirtualObjectNode virtual, ValueNode node, NodeBitMap usages) {
        objectAliases.put(node, virtual);
        if (node.isAlive()) {
            for (Node usage : node.usages()) {
                markVirtualUsages(usage, usages);
            }
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

    public Iterable<ObjectState> getStates() {
        return objectStates.values();
    }

    public Collection<VirtualObjectNode> getVirtualObjects() {
        return objectAliases.values();
    }

    @Override
    public String toString() {
        return super.toString() + ", Object Aliases: " + objectAliases + ", Object States: " + objectStates;
    }

    @Override
    public void meetAliases(List<T> states) {
        super.meetAliases(states);
        objectAliases.putAll(states.get(0).objectAliases);
        for (int i = 1; i < states.size(); i++) {
            meetMaps(objectAliases, states.get(i).objectAliases);
        }
    }

    @Override
    public boolean equivalentTo(T other) {
        if (!compareMaps(objectAliases, other.objectAliases) || !compareMaps(objectStates, other.objectStates)) {
            return false;
        }
        return super.equivalentTo(other);
    }

    protected static <K, V> boolean compareMaps(Map<K, V> left, Map<K, V> right) {
        if (left.size() != right.size()) {
            return false;
        }
        return compareMapsNoSize(left, right);
    }

    protected static <K, V> boolean compareMapsNoSize(Map<K, V> left, Map<K, V> right) {
        if (left == right) {
            return true;
        }
        for (Map.Entry<K, V> entry : right.entrySet()) {
            K key = entry.getKey();
            V value = entry.getValue();
            assert value != null;
            V otherValue = left.get(key);
            if (otherValue != value && !value.equals(otherValue)) {
                return false;
            }
        }
        return true;
    }

    protected static <U, V> void meetMaps(Map<U, V> target, Map<U, V> source) {
        Iterator<Map.Entry<U, V>> iter = target.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<U, V> entry = iter.next();
            if (source.containsKey(entry.getKey())) {
                assert source.get(entry.getKey()) == entry.getValue();
            } else {
                iter.remove();
            }
        }
    }

}
