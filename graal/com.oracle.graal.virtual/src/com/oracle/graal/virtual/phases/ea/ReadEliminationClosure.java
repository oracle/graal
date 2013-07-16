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
import com.oracle.graal.nodes.PhiNode.PhiType;
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.phases.schedule.*;
import com.oracle.graal.virtual.phases.ea.ReadEliminationBlockState.ReadCacheEntry;

public class ReadEliminationClosure extends EffectsClosure<ReadEliminationBlockState> {

    public ReadEliminationClosure(SchedulePhase schedule) {
        super(schedule);
    }

    @Override
    protected ReadEliminationBlockState getInitialState() {
        return new ReadEliminationBlockState();
    }

    @Override
    protected boolean processNode(Node node, ReadEliminationBlockState state, GraphEffectList effects, FixedWithNextNode lastFixedNode) {
        boolean deleted = false;
        if (node instanceof LoadFieldNode) {
            LoadFieldNode load = (LoadFieldNode) node;
            ValueNode object = GraphUtil.unproxify(load.object());
            ValueNode cachedValue = state.getReadCache(object, load.field());
            if (cachedValue != null) {
                effects.replaceAtUsages(load, cachedValue);
                state.addScalarAlias(load, cachedValue);
                deleted = true;
            } else {
                state.addReadCache(object, load.field(), load);
            }
        } else if (node instanceof StoreFieldNode) {
            StoreFieldNode store = (StoreFieldNode) node;
            ValueNode object = GraphUtil.unproxify(store.object());
            ValueNode cachedValue = state.getReadCache(object, store.field());

            if (state.getScalarAlias(store.value()) == cachedValue) {
                effects.deleteFixedNode(store);
                deleted = true;
            }
            state.killReadCache(store.field());
            state.addReadCache(object, store.field(), store.value());
        } else if (node instanceof MemoryCheckpoint.Single) {
            LocationIdentity identity = ((MemoryCheckpoint.Single) node).getLocationIdentity();
            processIdentity(state, identity);
        } else if (node instanceof MemoryCheckpoint.Multi) {
            for (LocationIdentity identity : ((MemoryCheckpoint.Multi) node).getLocationIdentities()) {
                processIdentity(state, identity);
            }
        }
        return deleted;
    }

    private static void processIdentity(ReadEliminationBlockState state, LocationIdentity identity) {
        if (identity instanceof ResolvedJavaField) {
            state.killReadCache((ResolvedJavaField) identity);
        } else if (identity == ANY_LOCATION) {
            state.killReadCache();
        }
    }

    @Override
    protected void processLoopExit(LoopExitNode exitNode, ReadEliminationBlockState initialState, ReadEliminationBlockState exitState, GraphEffectList effects) {
        for (Map.Entry<ReadCacheEntry, ValueNode> entry : exitState.getReadCache().entrySet()) {
            if (initialState.getReadCache().get(entry.getKey()) != entry.getValue()) {
                ProxyNode proxy = new ProxyNode(exitState.getReadCache(entry.getKey().object, entry.getKey().identity), exitNode, PhiType.Value, null);
                effects.addFloatingNode(proxy, "readCacheProxy");
                entry.setValue(proxy);
            }
        }
    }

    @Override
    protected ReadEliminationBlockState cloneState(ReadEliminationBlockState other) {
        return new ReadEliminationBlockState(other);
    }

    @Override
    protected MergeProcessor createMergeProcessor(Block merge) {
        return new ReadEliminationMergeProcessor(merge);
    }

    private class ReadEliminationMergeProcessor extends EffectsClosure<ReadEliminationBlockState>.MergeProcessor {

        private final HashMap<Object, PhiNode> materializedPhis = new HashMap<>();

        public ReadEliminationMergeProcessor(Block mergeBlock) {
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

        @Override
        protected void merge(List<ReadEliminationBlockState> states) {
            super.merge(states);

            mergeReadCache(states);
        }

        private void mergeReadCache(List<ReadEliminationBlockState> states) {
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
                    PhiNode phiNode = getCachedPhi(entry, value.kind());
                    mergeEffects.addFloatingNode(phiNode, "mergeReadCache");
                    for (int i = 0; i < states.size(); i++) {
                        afterMergeEffects.addPhiInput(phiNode, states.get(i).getReadCache(key.object, key.identity));
                    }
                    newState.readCache.put(key, phiNode);
                } else if (value != null) {
                    newState.readCache.put(key, value);
                }
            }
            for (PhiNode phi : merge.phis()) {
                if (phi.kind() == Kind.Object) {
                    for (Map.Entry<ReadCacheEntry, ValueNode> entry : states.get(0).readCache.entrySet()) {
                        if (entry.getKey().object == phi.valueAt(0)) {
                            mergeReadCachePhi(phi, entry.getKey().identity, states);
                        }
                    }

                }
            }
        }

        private void mergeReadCachePhi(PhiNode phi, ResolvedJavaField identity, List<ReadEliminationBlockState> states) {
            ValueNode[] values = new ValueNode[phi.valueCount()];
            for (int i = 0; i < phi.valueCount(); i++) {
                ValueNode value = states.get(i).getReadCache(phi.valueAt(i), identity);
                if (value == null) {
                    return;
                }
                values[i] = value;
            }

            PhiNode phiNode = getCachedPhi(new ReadCacheEntry(identity, phi), values[0].kind());
            mergeEffects.addFloatingNode(phiNode, "mergeReadCachePhi");
            for (int i = 0; i < values.length; i++) {
                afterMergeEffects.addPhiInput(phiNode, values[i]);
            }
            newState.readCache.put(new ReadCacheEntry(identity, phi), phiNode);
        }
    }
}
