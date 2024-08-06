/*
 * Copyright (c) 2011, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.graal.compiler.virtual.phases.ea;

import static jdk.graal.compiler.core.common.GraalOptions.ReadEliminationMaxLoopVisits;

import java.util.Iterator;
import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.MapCursor;
import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.core.common.cfg.CFGLoop;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.LoopExitNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.ProxyNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.ValueProxyNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.calc.IntegerEqualsNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.extended.GuardedNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.extended.RawLoadNode;
import jdk.graal.compiler.nodes.extended.RawStoreNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.StoreFieldNode;
import jdk.graal.compiler.nodes.memory.MemoryAccess;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.memory.MultiMemoryKill;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.memory.SingleMemoryKill;
import jdk.graal.compiler.nodes.memory.WriteNode;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.virtual.phases.ea.ReadEliminationBlockState.CacheEntry;
import jdk.graal.compiler.virtual.phases.ea.ReadEliminationBlockState.LoadCacheEntry;
import jdk.graal.compiler.virtual.phases.ea.ReadEliminationBlockState.UnsafeLoadCacheEntry;
import jdk.vm.ci.meta.JavaKind;

/**
 * This closure initially handled a set of nodes that is disjunct from
 * {@link PEReadEliminationClosure}, but over time both have evolved so that there's a significant
 * overlap.
 */
public class ReadEliminationClosure extends EffectsClosure<ReadEliminationBlockState> {
    protected final boolean considerGuards;

    public ReadEliminationClosure(ControlFlowGraph cfg, boolean considerGuards) {
        super(null, cfg);
        this.considerGuards = considerGuards;
    }

    @Override
    protected ReadEliminationBlockState getInitialState() {
        return new ReadEliminationBlockState();
    }

    @Override
    protected boolean processNode(Node node, ReadEliminationBlockState state, GraphEffectList effects, FixedWithNextNode lastFixedNode) {
        boolean deleted = false;
        if (node.getNodeClass().isMemoryKill() && MemoryKill.isMemoryKill(node)) {
            if (MemoryKill.isSingleMemoryKill(node)) {
                LocationIdentity identity = ((SingleMemoryKill) node).getKilledLocationIdentity();
                if (identity.isSingle() && (node instanceof WriteNode || node instanceof StoreFieldNode || node instanceof RawStoreNode)) {
                    if (node instanceof WriteNode || node instanceof StoreFieldNode) {
                        ValueNode value = null;
                        ValueNode object = null;
                        if (node instanceof StoreFieldNode) {
                            StoreFieldNode store = (StoreFieldNode) node;
                            value = getScalarAlias(store.value());
                            object = GraphUtil.unproxify(store.object());
                        } else if (node instanceof WriteNode) {
                            WriteNode write = (WriteNode) node;
                            value = getScalarAlias(write.value());
                            object = GraphUtil.unproxify(write.getAddress());
                        } else {
                            throw GraalError.shouldNotReachHereUnexpectedValue(node); // ExcludeFromJacocoGeneratedReport
                        }
                        LoadCacheEntry identifier = new LoadCacheEntry(object, identity);
                        ValueNode cachedValue = state.getCacheEntry(identifier);
                        if (GraphUtil.unproxify(value) == GraphUtil.unproxify(cachedValue)) {
                            effects.deleteNode(node);
                            deleted = true;
                        }
                        // will be a field location identity not killing array accesses
                        killReadCacheByIdentity(state, identifier.identity, node);
                        state.addCacheEntry(identifier, value);
                    } else if (node instanceof RawStoreNode) {
                        RawStoreNode write = (RawStoreNode) node;
                        ValueNode object = GraphUtil.unproxify(write.object());
                        UnsafeLoadCacheEntry identifier = new UnsafeLoadCacheEntry(object, write.offset(),
                                        write.getKilledLocationIdentity(), write.accessKind());
                        ValueNode cachedValue = state.getCacheEntry(identifier);
                        ValueNode value = getScalarAlias(write.value());
                        if (GraphUtil.unproxify(value) == GraphUtil.unproxify(cachedValue)) {
                            effects.deleteNode(write);
                            deleted = true;
                        }
                        killReadCacheByIdentity(state, write.getKilledLocationIdentity(), node);
                        state.addCacheEntry(identifier, value);
                    }
                } else {
                    killReadCacheByIdentity(state, identity, node);
                }
            } else if (MemoryKill.isMultiMemoryKill(node)) {
                for (LocationIdentity identity : ((MultiMemoryKill) node).getKilledLocationIdentities()) {
                    killReadCacheByIdentity(state, identity, node);
                }
            } else {
                throw GraalError.shouldNotReachHere("Unknown memory kill " + node); // ExcludeFromJacocoGeneratedReport
            }
        } else {
            if (node instanceof MemoryAccess) {
                if (((MemoryAccess) node).getLocationIdentity().isSingle()) {
                    if (node instanceof RawLoadNode) {
                        RawLoadNode load = (RawLoadNode) node;
                        ValueNode object = GraphUtil.unproxify(load.object());
                        UnsafeLoadCacheEntry identifier = new UnsafeLoadCacheEntry(object, load.offset(),
                                        load.getLocationIdentity(), load.accessKind());
                        ValueNode cachedValue = state.getCacheEntry(identifier);
                        if (cachedValue != null && areValuesReplaceable(load, cachedValue, considerGuards)) {
                            if (load.accessKind() == JavaKind.Boolean) {
                                // perform boolean coercion
                                LogicNode cmp = IntegerEqualsNode.create(cachedValue, ConstantNode.forInt(0), NodeView.DEFAULT);
                                ValueNode boolValue = ConditionalNode.create(cmp, ConstantNode.forBoolean(false),
                                                ConstantNode.forBoolean(true), NodeView.DEFAULT);
                                effects.ensureFloatingAdded(boolValue);
                                cachedValue = boolValue;
                            }
                            effects.replaceAtUsages(load, cachedValue, load);
                            addScalarAlias(load, cachedValue);
                            deleted = true;
                        } else {
                            state.addCacheEntry(identifier, load);
                        }
                    } else {
                        assert node instanceof FixedNode : Assertions.errorMessage(node, state, effects, lastFixedNode);
                        // regular high tier memory access
                        LocationIdentity location = ((MemoryAccess) node).getLocationIdentity();
                        if (location.isSingle()) {
                            ValueNode object = null;
                            if (node instanceof LoadFieldNode) {
                                object = ((LoadFieldNode) node).object();
                            } else if (node instanceof ReadNode read) {
                                if (!read.getBarrierType().canReadEliminate()) {
                                    assert deleted == false;
                                    return false;
                                }
                                object = ((ReadNode) node).getAddress();
                            } else {
                                // unknown node, no elimination possible
                                assert deleted == false;
                                return false;
                            }
                            object = GraphUtil.unproxify(object);
                            ValueNode access = (ValueNode) node;
                            LoadCacheEntry identifier = new LoadCacheEntry(object, location);
                            ValueNode cachedValue = state.getCacheEntry(identifier);

                            if (cachedValue != null && areValuesReplaceable(access, cachedValue, considerGuards)) {
                                effects.replaceAtUsages(access, cachedValue, (FixedNode) access);
                                addScalarAlias(access, cachedValue);
                                deleted = true;
                            } else {
                                state.addCacheEntry(identifier, access);
                            }
                        }
                    }
                }
            }
        }
        if (deleted) {
            effects.addLog(cfg.graph.getOptimizationLog(),
                            optimizationLog -> optimizationLog.withProperty("deletedNodeClass", node.getNodeClass().shortName()).report(ReadEliminationClosure.class, "ReadElimination", node));
        }
        return deleted;
    }

    protected static boolean areValuesReplaceable(ValueNode originalValue, ValueNode replacementValue, boolean considerGuards) {
        return originalValue.stamp(NodeView.DEFAULT).isCompatible(replacementValue.stamp(NodeView.DEFAULT)) &&
                        (!considerGuards || (getGuard(originalValue) == null || getGuard(originalValue) == getGuard(replacementValue)));
    }

    private static GuardingNode getGuard(ValueNode node) {
        if (node instanceof GuardedNode) {
            GuardedNode guardedNode = (GuardedNode) node;
            return guardedNode.getGuard();
        }
        return null;
    }

    private static void killReadCacheByIdentity(ReadEliminationBlockState state, LocationIdentity identity, Node kill) {
        state.killReadCache(kill, identity, null, null);
    }

    @Override
    protected void processLoopExit(LoopExitNode exitNode, ReadEliminationBlockState initialState, ReadEliminationBlockState exitState, GraphEffectList effects) {
        if (exitNode.graph().isBeforeStage(StageFlag.VALUE_PROXY_REMOVAL)) {
            MapCursor<CacheEntry<?>, ValueNode> entry = exitState.getReadCache().getEntries();
            while (entry.advance()) {
                if (initialState.getReadCache().get(entry.getKey()) != entry.getValue()) {
                    ProxyNode proxy = new ValueProxyNode(exitState.getCacheEntry(entry.getKey()), exitNode);
                    effects.addFloatingNode(proxy, "readCacheProxy");
                    exitState.getReadCache().put(entry.getKey(), proxy);
                }
            }
        }
    }

    @Override
    protected ReadEliminationBlockState cloneState(ReadEliminationBlockState other) {
        return new ReadEliminationBlockState(other);
    }

    @Override
    protected MergeProcessor createMergeProcessor(HIRBlock merge) {
        return new ReadEliminationMergeProcessor(merge);
    }

    private class ReadEliminationMergeProcessor extends EffectsClosure<ReadEliminationBlockState>.MergeProcessor {

        private final EconomicMap<Object, ValuePhiNode> materializedPhis = EconomicMap.create(Equivalence.DEFAULT);

        ReadEliminationMergeProcessor(HIRBlock mergeBlock) {
            super(mergeBlock);
        }

        protected ValuePhiNode getCachedPhi(CacheEntry<?> virtual, Stamp stamp) {
            ValuePhiNode result = materializedPhis.get(virtual);
            if (result == null) {
                result = createValuePhi(stamp);
                materializedPhis.put(virtual, result);
            }
            return result;
        }

        @Override
        protected void merge(List<ReadEliminationBlockState> states) {
            MapCursor<CacheEntry<?>, ValueNode> cursor = states.get(0).readCache.getEntries();
            while (cursor.advance()) {
                CacheEntry<?> key = cursor.getKey();
                ValueNode value = cursor.getValue();
                boolean phi = false;
                for (int i = 1; i < states.size(); i++) {
                    ValueNode otherValue = states.get(i).readCache.get(key);
                    // E.g. unsafe loads / stores with different access kinds have different stamps
                    // although location, object and offset are the same. In this case we cannot
                    // create a phi nor can we set a common value.
                    if (otherValue == null || !value.stamp(NodeView.DEFAULT).isCompatible(otherValue.stamp(NodeView.DEFAULT))) {
                        value = null;
                        phi = false;
                        break;
                    }
                    if (!phi && otherValue != value) {
                        phi = true;
                    }
                }
                if (phi) {
                    PhiNode phiNode = getCachedPhi(key, value.stamp(NodeView.DEFAULT).unrestricted());
                    mergeEffects.addFloatingNode(phiNode, "mergeReadCache");
                    for (int i = 0; i < states.size(); i++) {
                        ValueNode v = states.get(i).getCacheEntry(key);
                        assert phiNode.stamp(NodeView.DEFAULT).isCompatible(v.stamp(NodeView.DEFAULT)) : "Cannot create read elimination phi for inputs with incompatible stamps.";
                        setPhiInput(phiNode, i, v);
                    }
                    newState.addCacheEntry(key, phiNode);
                } else if (value != null) {
                    // case that there is the same value on all branches
                    newState.addCacheEntry(key, value);
                }
            }
            /*
             * For object phis, see if there are known reads on all predecessors, for which we could
             * create new phis.
             */
            for (PhiNode phi : getPhis()) {
                if (phi.getStackKind() == JavaKind.Object) {
                    for (CacheEntry<?> entry : states.get(0).readCache.getKeys()) {
                        if (entry.object == getPhiValueAt(phi, 0)) {
                            mergeReadCachePhi(phi, entry, states);
                        }
                    }
                }
            }
        }

        private void mergeReadCachePhi(PhiNode phi, CacheEntry<?> identifier, List<ReadEliminationBlockState> states) {
            ValueNode[] values = new ValueNode[states.size()];
            values[0] = states.get(0).getCacheEntry(identifier.duplicateWithObject(getPhiValueAt(phi, 0)));
            if (values[0] != null) {
                for (int i = 1; i < states.size(); i++) {
                    ValueNode value = states.get(i).getCacheEntry(identifier.duplicateWithObject(getPhiValueAt(phi, i)));
                    // e.g. unsafe loads / stores with same identity and different access kinds see
                    // mergeReadCache(states)
                    if (value == null || !values[i - 1].stamp(NodeView.DEFAULT).isCompatible(value.stamp(NodeView.DEFAULT))) {
                        return;
                    }
                    values[i] = value;
                }

                CacheEntry<?> newIdentifier = identifier.duplicateWithObject(phi);
                PhiNode phiNode = getCachedPhi(newIdentifier, values[0].stamp(NodeView.DEFAULT).unrestricted());
                mergeEffects.addFloatingNode(phiNode, "mergeReadCachePhi");
                for (int i = 0; i < values.length; i++) {
                    setPhiInput(phiNode, i, values[i]);
                }
                newState.addCacheEntry(newIdentifier, phiNode);
            }
        }
    }

    @Override
    protected void processKilledLoopLocations(CFGLoop<HIRBlock> loop, ReadEliminationBlockState initialState, ReadEliminationBlockState mergedStates) {
        assert initialState != null;
        assert mergedStates != null;
        if (initialState.readCache.size() > 0) {
            LoopKillCache loopKilledLocations = loopLocationKillCache.get(loop);
            // we have fully processed this loop the first time, remember to cache it the next time
            // it is visited
            if (loopKilledLocations == null) {
                loopKilledLocations = new LoopKillCache(1/* 1.visit */);
                loopLocationKillCache.put(loop, loopKilledLocations);
            } else {
                OptionValues options = loop.getHeader().getBeginNode().getOptions();
                if (loopKilledLocations.visits() > ReadEliminationMaxLoopVisits.getValue(options)) {
                    // we have processed the loop too many times, kill all locations so the inner
                    // loop will never be processed more than once again on visit
                    loopKilledLocations.setKillsAll();
                } else {
                    // we have fully processed this loop >1 times, update the killed locations
                    EconomicSet<LocationIdentity> forwardEndLiveLocations = EconomicSet.create(Equivalence.DEFAULT);
                    for (CacheEntry<?> entry : initialState.readCache.getKeys()) {
                        forwardEndLiveLocations.add(entry.getIdentity());
                    }
                    for (CacheEntry<?> entry : mergedStates.readCache.getKeys()) {
                        forwardEndLiveLocations.remove(entry.getIdentity());
                    }
                    // every location that is alive before the loop but not after is killed by the
                    // loop
                    for (LocationIdentity location : forwardEndLiveLocations) {
                        loopKilledLocations.rememberLoopKilledLocation(location);
                    }
                    if (debug.isLogEnabled()) {
                        debug.log("[Early Read Elimination] Setting loop killed locations of loop at node %s with %s",
                                        loop.getHeader().getBeginNode(), forwardEndLiveLocations);
                    }
                }
                // remember the loop visit
                loopKilledLocations.visited();
            }
        }
    }

    @Override
    protected ReadEliminationBlockState stripKilledLoopLocations(CFGLoop<HIRBlock> loop, ReadEliminationBlockState originalInitialState) {
        ReadEliminationBlockState initialState = super.stripKilledLoopLocations(loop, originalInitialState);
        LoopKillCache loopKilledLocations = loopLocationKillCache.get(loop);
        if (loopKilledLocations != null && loopKilledLocations.loopKillsLocations()) {
            Iterator<CacheEntry<?>> it = initialState.readCache.getKeys().iterator();
            while (it.hasNext()) {
                CacheEntry<?> entry = it.next();
                if (loopKilledLocations.containsLocation(entry.getIdentity())) {
                    it.remove();
                }
            }
        }
        return initialState;
    }
}
