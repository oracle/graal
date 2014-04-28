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

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.phases.schedule.*;
import com.oracle.graal.virtual.phases.ea.PEReadEliminationBlockState.ReadCacheEntry;

public class PEReadEliminationClosure extends PartialEscapeClosure<PEReadEliminationBlockState> {

    public PEReadEliminationClosure(SchedulePhase schedule, MetaAccessProvider metaAccess, ConstantReflectionProvider constantReflection, Assumptions assumptions) {
        super(schedule, metaAccess, constantReflection, assumptions);
    }

    @Override
    protected PEReadEliminationBlockState getInitialState() {
        return new PEReadEliminationBlockState();
    }

    @Override
    protected boolean processNode(Node node, PEReadEliminationBlockState state, GraphEffectList effects, FixedWithNextNode lastFixedNode) {
        boolean deleted = super.processNode(node, state, effects, lastFixedNode);
        if (!deleted) {
            if (node instanceof LoadFieldNode) {
                LoadFieldNode load = (LoadFieldNode) node;
                if (!load.isVolatile()) {
                    ValueNode object = GraphUtil.unproxify(load.object());
                    ValueNode cachedValue = state.getReadCache(object, load.field(), this);
                    if (cachedValue != null) {
                        effects.replaceAtUsages(load, cachedValue);
                        addScalarAlias(load, cachedValue);
                        deleted = true;
                    } else {
                        state.addReadCache(object, load.field(), load, this);
                    }
                } else {
                    processIdentity(state, ANY_LOCATION);
                }
            } else if (node instanceof StoreFieldNode) {
                StoreFieldNode store = (StoreFieldNode) node;
                if (!store.isVolatile()) {
                    ValueNode object = GraphUtil.unproxify(store.object());
                    ValueNode cachedValue = state.getReadCache(object, store.field(), this);

                    ValueNode value = getScalarAlias(store.value());
                    if (GraphUtil.unproxify(value) == GraphUtil.unproxify(cachedValue)) {
                        effects.deleteFixedNode(store);
                        deleted = true;
                    }
                    state.killReadCache(store.field());
                    state.addReadCache(object, store.field(), value, this);
                } else {
                    processIdentity(state, ANY_LOCATION);
                }
            } else if (node instanceof ArrayLengthNode) {
                ArrayLengthNode length = (ArrayLengthNode) node;
                ValueNode array = GraphUtil.unproxify(length.array());
                ValueNode cachedValue = state.getReadCache(array, ARRAY_LENGTH_LOCATION, this);
                if (cachedValue != null) {
                    effects.replaceAtUsages(length, cachedValue);
                    addScalarAlias(length, cachedValue);
                    deleted = true;
                } else {
                    state.addReadCache(array, ARRAY_LENGTH_LOCATION, length, this);
                }
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
        }
        return deleted;
    }

    private static void processIdentity(PEReadEliminationBlockState state, LocationIdentity identity) {
        if (identity instanceof ResolvedJavaField) {
            state.killReadCache((ResolvedJavaField) identity);
        } else if (identity == ANY_LOCATION) {
            state.killReadCache();
        }
    }

    @Override
    protected void processLoopExit(LoopExitNode exitNode, PEReadEliminationBlockState initialState, PEReadEliminationBlockState exitState, GraphEffectList effects) {
        super.processLoopExit(exitNode, initialState, exitState, effects);

        for (Map.Entry<ReadCacheEntry, ValueNode> entry : exitState.getReadCache().entrySet()) {
            if (initialState.getReadCache().get(entry.getKey()) != entry.getValue()) {
                ValueNode value = exitState.getReadCache(entry.getKey().object, entry.getKey().identity, this);
                if (!(value instanceof ProxyNode) || ((ProxyNode) value).proxyPoint() != exitNode) {
                    ProxyNode proxy = new ValueProxyNode(value, exitNode);
                    effects.addFloatingNode(proxy, "readCacheProxy");
                    entry.setValue(proxy);
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
                    PhiNode phiNode = getCachedPhi(entry, value.stamp().unrestricted());
                    mergeEffects.addFloatingNode(phiNode, "mergeReadCache");
                    for (int i = 0; i < states.size(); i++) {
                        afterMergeEffects.addPhiInput(phiNode, states.get(i).getReadCache(key.object, key.identity, PEReadEliminationClosure.this));
                    }
                    newState.readCache.put(key, phiNode);
                } else if (value != null) {
                    newState.readCache.put(key, value);
                }
            }
            for (PhiNode phi : merge.phis()) {
                if (phi.getKind() == Kind.Object) {
                    for (Map.Entry<ReadCacheEntry, ValueNode> entry : states.get(0).readCache.entrySet()) {
                        if (entry.getKey().object == phi.valueAt(0)) {
                            mergeReadCachePhi(phi, entry.getKey().identity, states);
                        }
                    }

                }
            }
        }

        private void mergeReadCachePhi(PhiNode phi, LocationIdentity identity, List<PEReadEliminationBlockState> states) {
            ValueNode[] values = new ValueNode[phi.valueCount()];
            for (int i = 0; i < phi.valueCount(); i++) {
                ValueNode value = states.get(i).getReadCache(phi.valueAt(i), identity, PEReadEliminationClosure.this);
                if (value == null) {
                    return;
                }
                values[i] = value;
            }

            PhiNode phiNode = getCachedPhi(new ReadCacheEntry(identity, phi), values[0].stamp().unrestricted());
            mergeEffects.addFloatingNode(phiNode, "mergeReadCachePhi");
            for (int i = 0; i < values.length; i++) {
                afterMergeEffects.addPhiInput(phiNode, values[i]);
            }
            newState.readCache.put(new ReadCacheEntry(identity, phi), phiNode);
        }
    }
}
