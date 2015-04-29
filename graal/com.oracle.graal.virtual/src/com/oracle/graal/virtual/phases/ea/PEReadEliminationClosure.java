/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.api.meta.LocationIdentity.*;

import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.nodes.virtual.*;
import com.oracle.graal.phases.schedule.*;
import com.oracle.graal.virtual.phases.ea.PEReadEliminationBlockState.ReadCacheEntry;

public class PEReadEliminationClosure extends PartialEscapeClosure<PEReadEliminationBlockState> {

    private static final EnumMap<Kind, LocationIdentity> UNBOX_LOCATIONS;
    static {
        UNBOX_LOCATIONS = new EnumMap<>(Kind.class);
        for (Kind kind : Kind.values()) {
            UNBOX_LOCATIONS.put(kind, NamedLocationIdentity.immutable("PEA unbox " + kind.getJavaName()));
        }
    }

    public PEReadEliminationClosure(SchedulePhase schedule, MetaAccessProvider metaAccess, ConstantReflectionProvider constantReflection) {
        super(schedule, metaAccess, constantReflection);
    }

    @Override
    protected PEReadEliminationBlockState getInitialState() {
        return new PEReadEliminationBlockState();
    }

    @Override
    protected boolean processNode(Node node, PEReadEliminationBlockState state, GraphEffectList effects, FixedWithNextNode lastFixedNode) {
        if (super.processNode(node, state, effects, lastFixedNode)) {
            return true;
        }

        if (node instanceof LoadFieldNode) {
            return processLoadField((LoadFieldNode) node, state, effects);
        } else if (node instanceof StoreFieldNode) {
            return processStoreField((StoreFieldNode) node, state, effects);
        } else if (node instanceof LoadIndexedNode) {
            return processLoadIndexed((LoadIndexedNode) node, state, effects);
        } else if (node instanceof StoreIndexedNode) {
            return processStoreIndexed((StoreIndexedNode) node, state, effects);
        } else if (node instanceof ArrayLengthNode) {
            return processArrayLength((ArrayLengthNode) node, state, effects);
        } else if (node instanceof UnboxNode) {
            return processUnbox((UnboxNode) node, state, effects);
        } else if (node instanceof UnsafeLoadNode) {
            return processUnsafeLoad((UnsafeLoadNode) node, state, effects);
        } else if (node instanceof UnsafeStoreNode) {
            return processUnsafeStore((UnsafeStoreNode) node, state, effects);
        } else if (node instanceof MemoryCheckpoint.Single) {
            METRIC_MEMORYCHECKPOINT.increment();
            LocationIdentity identity = ((MemoryCheckpoint.Single) node).getLocationIdentity();
            processIdentity(state, identity);
        } else if (node instanceof MemoryCheckpoint.Multi) {
            METRIC_MEMORYCHECKPOINT.increment();
            for (LocationIdentity identity : ((MemoryCheckpoint.Multi) node).getLocationIdentities()) {
                processIdentity(state, identity);
            }
        }

        return false;
    }

    private boolean processStore(FixedNode store, ValueNode object, LocationIdentity identity, int index, ValueNode value, PEReadEliminationBlockState state, GraphEffectList effects) {
        ValueNode unproxiedObject = GraphUtil.unproxify(object);
        ValueNode cachedValue = state.getReadCache(object, identity, index, this);

        ValueNode finalValue = getScalarAlias(value);
        boolean result = false;
        if (GraphUtil.unproxify(finalValue) == GraphUtil.unproxify(cachedValue)) {
            effects.deleteNode(store);
            result = true;
        }
        state.killReadCache(identity, index);
        state.addReadCache(unproxiedObject, identity, index, finalValue, this);
        return result;
    }

    private boolean processLoad(FixedNode load, ValueNode object, LocationIdentity identity, int index, PEReadEliminationBlockState state, GraphEffectList effects) {
        ValueNode unproxiedObject = GraphUtil.unproxify(object);
        ValueNode cachedValue = state.getReadCache(unproxiedObject, identity, index, this);
        if (cachedValue != null) {
            effects.replaceAtUsages(load, cachedValue);
            addScalarAlias(load, cachedValue);
            return true;
        } else {
            state.addReadCache(unproxiedObject, identity, index, load, this);
            return false;
        }
    }

    private boolean processUnsafeLoad(UnsafeLoadNode load, PEReadEliminationBlockState state, GraphEffectList effects) {
        if (load.offset().isConstant()) {
            ResolvedJavaType type = StampTool.typeOrNull(load.object());
            if (type != null && type.isArray()) {
                long offset = load.offset().asJavaConstant().asLong();
                int index = VirtualArrayNode.entryIndexForOffset(offset, load.accessKind(), type.getComponentType(), Integer.MAX_VALUE);
                ValueNode object = GraphUtil.unproxify(load.object());
                LocationIdentity location = NamedLocationIdentity.getArrayLocation(type.getComponentType().getKind());
                ValueNode cachedValue = state.getReadCache(object, location, index, this);
                if (cachedValue != null && load.stamp().isCompatible(cachedValue.stamp())) {
                    effects.replaceAtUsages(load, cachedValue);
                    addScalarAlias(load, cachedValue);
                    return true;
                } else {
                    state.addReadCache(object, location, index, load, this);
                }
            }
        }
        return false;
    }

    private boolean processUnsafeStore(UnsafeStoreNode store, PEReadEliminationBlockState state, GraphEffectList effects) {
        ResolvedJavaType type = StampTool.typeOrNull(store.object());
        if (type != null && type.isArray()) {
            LocationIdentity location = NamedLocationIdentity.getArrayLocation(type.getComponentType().getKind());
            if (store.offset().isConstant()) {
                long offset = store.offset().asJavaConstant().asLong();
                int index = VirtualArrayNode.entryIndexForOffset(offset, store.accessKind(), type.getComponentType(), Integer.MAX_VALUE);
                return processStore(store, store.object(), location, index, store.value(), state, effects);
            } else {
                processIdentity(state, location);
            }
        } else {
            state.killReadCache();
        }
        return false;
    }

    private boolean processArrayLength(ArrayLengthNode length, PEReadEliminationBlockState state, GraphEffectList effects) {
        return processLoad(length, length.array(), ARRAY_LENGTH_LOCATION, -1, state, effects);
    }

    private boolean processStoreField(StoreFieldNode store, PEReadEliminationBlockState state, GraphEffectList effects) {
        if (store.isVolatile()) {
            state.killReadCache();
            return false;
        }
        return processStore(store, store.object(), store.field().getLocationIdentity(), -1, store.value(), state, effects);
    }

    private boolean processLoadField(LoadFieldNode load, PEReadEliminationBlockState state, GraphEffectList effects) {
        if (load.isVolatile()) {
            state.killReadCache();
            return false;
        }
        return processLoad(load, load.object(), load.field().getLocationIdentity(), -1, state, effects);
    }

    private boolean processStoreIndexed(StoreIndexedNode store, PEReadEliminationBlockState state, GraphEffectList effects) {
        LocationIdentity arrayLocation = NamedLocationIdentity.getArrayLocation(store.elementKind());
        if (store.index().isConstant()) {
            int index = ((JavaConstant) store.index().asConstant()).asInt();
            return processStore(store, store.array(), arrayLocation, index, store.value(), state, effects);
        } else {
            state.killReadCache(arrayLocation, -1);
        }
        return false;
    }

    private boolean processLoadIndexed(LoadIndexedNode load, PEReadEliminationBlockState state, GraphEffectList effects) {
        if (load.index().isConstant()) {
            int index = ((JavaConstant) load.index().asConstant()).asInt();
            LocationIdentity arrayLocation = NamedLocationIdentity.getArrayLocation(load.elementKind());
            return processLoad(load, load.array(), arrayLocation, index, state, effects);
        }
        return false;
    }

    private boolean processUnbox(UnboxNode unbox, PEReadEliminationBlockState state, GraphEffectList effects) {
        return processLoad(unbox, unbox.getValue(), UNBOX_LOCATIONS.get(unbox.getBoxingKind()), -1, state, effects);
    }

    private static void processIdentity(PEReadEliminationBlockState state, LocationIdentity identity) {
        if (identity.isAny()) {
            state.killReadCache();
        } else {
            state.killReadCache(identity, -1);
        }
    }

    @Override
    protected void processLoopExit(LoopExitNode exitNode, PEReadEliminationBlockState initialState, PEReadEliminationBlockState exitState, GraphEffectList effects) {
        super.processLoopExit(exitNode, initialState, exitState, effects);

        if (exitNode.graph().hasValueProxies()) {
            for (Map.Entry<ReadCacheEntry, ValueNode> entry : exitState.getReadCache().entrySet()) {
                if (initialState.getReadCache().get(entry.getKey()) != entry.getValue()) {
                    ValueNode value = exitState.getReadCache(entry.getKey().object, entry.getKey().identity, entry.getKey().index, this);
                    assert value != null : "Got null from read cache, entry's value:" + entry.getValue();
                    if (!(value instanceof ProxyNode) || ((ProxyNode) value).proxyPoint() != exitNode) {
                        ProxyNode proxy = new ValueProxyNode(value, exitNode);
                        effects.addFloatingNode(proxy, "readCacheProxy");
                        entry.setValue(proxy);
                    }
                }
            }
        }
    }

    @Override
    protected PEReadEliminationBlockState cloneState(PEReadEliminationBlockState other) {
        return new PEReadEliminationBlockState(other);
    }

    @Override
    protected MergeProcessor createMergeProcessor(Block merge) {
        return new ReadEliminationMergeProcessor(merge);
    }

    private class ReadEliminationMergeProcessor extends MergeProcessor {

        public ReadEliminationMergeProcessor(Block mergeBlock) {
            super(mergeBlock);
        }

        @Override
        protected void merge(List<PEReadEliminationBlockState> states) {
            super.merge(states);

            mergeReadCache(states);
        }

        private void mergeReadCache(List<PEReadEliminationBlockState> states) {
            for (Map.Entry<ReadCacheEntry, ValueNode> entry : states.get(0).readCache.entrySet()) {
                ReadCacheEntry key = entry.getKey();
                ValueNode value = entry.getValue();
                boolean phi = false;
                for (int i = 1; i < states.size(); i++) {
                    ValueNode otherValue = states.get(i).readCache.get(key);
                    if (otherValue == null) {
                        value = null;
                        phi = false;
                        break;
                    }
                    if (!phi && otherValue != value) {
                        phi = true;
                    }
                }
                if (phi) {
                    PhiNode phiNode = getPhi(entry, value.stamp().unrestricted());
                    mergeEffects.addFloatingNode(phiNode, "mergeReadCache");
                    for (int i = 0; i < states.size(); i++) {
                        setPhiInput(phiNode, i, states.get(i).getReadCache(key.object, key.identity, key.index, PEReadEliminationClosure.this));
                    }
                    newState.readCache.put(key, phiNode);
                } else if (value != null) {
                    newState.readCache.put(key, value);
                }
            }
            for (PhiNode phi : getPhis()) {
                if (phi.getKind() == Kind.Object) {
                    for (Map.Entry<ReadCacheEntry, ValueNode> entry : states.get(0).readCache.entrySet()) {
                        if (entry.getKey().object == getPhiValueAt(phi, 0)) {
                            mergeReadCachePhi(phi, entry.getKey().identity, entry.getKey().index, states);
                        }
                    }
                }
            }
        }

        private void mergeReadCachePhi(PhiNode phi, LocationIdentity identity, int index, List<PEReadEliminationBlockState> states) {
            ValueNode[] values = new ValueNode[states.size()];
            for (int i = 0; i < states.size(); i++) {
                ValueNode value = states.get(i).getReadCache(getPhiValueAt(phi, i), identity, index, PEReadEliminationClosure.this);
                if (value == null) {
                    return;
                }
                values[i] = value;
            }

            PhiNode phiNode = getPhi(new ReadCacheEntry(identity, phi, index), values[0].stamp().unrestricted());
            mergeEffects.addFloatingNode(phiNode, "mergeReadCachePhi");
            for (int i = 0; i < values.length; i++) {
                setPhiInput(phiNode, i, values[i]);
            }
            newState.readCache.put(new ReadCacheEntry(identity, phi, index), phiNode);
        }
    }
}
