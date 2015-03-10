/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.phases.common;

import static com.oracle.graal.api.meta.LocationIdentity.*;
import static com.oracle.graal.graph.Graph.NodeEvent.*;

import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.graph.Graph.NodeEventScope;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.util.*;
import com.oracle.graal.phases.graph.*;
import com.oracle.graal.phases.graph.ReentrantNodeIterator.LoopInfo;
import com.oracle.graal.phases.graph.ReentrantNodeIterator.NodeIteratorClosure;

public class FloatingReadPhase extends Phase {

    private boolean createFloatingReads;
    private boolean createMemoryMapNodes;

    public static class MemoryMapImpl implements MemoryMap {

        private final Map<LocationIdentity, MemoryNode> lastMemorySnapshot;

        public MemoryMapImpl(MemoryMapImpl memoryMap) {
            lastMemorySnapshot = CollectionsFactory.newMap(memoryMap.lastMemorySnapshot);
        }

        public MemoryMapImpl(StartNode start) {
            lastMemorySnapshot = CollectionsFactory.newMap();
            lastMemorySnapshot.put(ANY_LOCATION, start);
        }

        public MemoryMapImpl() {
            lastMemorySnapshot = CollectionsFactory.newMap();
        }

        @Override
        public MemoryNode getLastLocationAccess(LocationIdentity locationIdentity) {
            MemoryNode lastLocationAccess;
            if (locationIdentity.isImmutable()) {
                return null;
            } else {
                lastLocationAccess = lastMemorySnapshot.get(locationIdentity);
                if (lastLocationAccess == null) {
                    lastLocationAccess = lastMemorySnapshot.get(ANY_LOCATION);
                    assert lastLocationAccess != null;
                }
                return lastLocationAccess;
            }
        }

        @Override
        public Collection<LocationIdentity> getLocations() {
            return lastMemorySnapshot.keySet();
        }

        public Map<LocationIdentity, MemoryNode> getMap() {
            return lastMemorySnapshot;
        }
    }

    public FloatingReadPhase() {
        this(true, false);
    }

    /**
     * @param createFloatingReads specifies whether {@link FloatableAccessNode}s like
     *            {@link ReadNode} should be converted into floating nodes (e.g.,
     *            {@link FloatingReadNode}s) where possible
     * @param createMemoryMapNodes a {@link MemoryMapNode} will be created for each return if this
     *            is true
     */
    public FloatingReadPhase(boolean createFloatingReads, boolean createMemoryMapNodes) {
        this.createFloatingReads = createFloatingReads;
        this.createMemoryMapNodes = createMemoryMapNodes;
    }

    /**
     * Removes nodes from a given set that (transitively) have a usage outside the set.
     */
    private static Set<Node> removeExternallyUsedNodes(Set<Node> set) {
        boolean change;
        do {
            change = false;
            for (Iterator<Node> iter = set.iterator(); iter.hasNext();) {
                Node node = iter.next();
                for (Node usage : node.usages()) {
                    if (!set.contains(usage)) {
                        change = true;
                        iter.remove();
                        break;
                    }
                }
            }
        } while (change);
        return set;
    }

    @Override
    protected void run(StructuredGraph graph) {
        Map<LoopBeginNode, Set<LocationIdentity>> modifiedInLoops = Node.newIdentityMap();
        ReentrantNodeIterator.apply(new CollectMemoryCheckpointsClosure(modifiedInLoops), graph.start(), CollectionsFactory.newSet());
        HashSetNodeEventListener listener = new HashSetNodeEventListener(EnumSet.of(NODE_ADDED, ZERO_USAGES));
        try (NodeEventScope nes = graph.trackNodeEvents(listener)) {
            ReentrantNodeIterator.apply(new FloatingReadClosure(modifiedInLoops, createFloatingReads, createMemoryMapNodes), graph.start(), new MemoryMapImpl(graph.start()));
        }

        for (Node n : removeExternallyUsedNodes(listener.getNodes())) {
            if (n.isAlive() && n instanceof FloatingNode) {
                n.replaceAtUsages(null);
                GraphUtil.killWithUnusedFloatingInputs(n);
            }
        }
        if (createFloatingReads) {
            assert !graph.isAfterFloatingReadPhase();
            graph.setAfterFloatingReadPhase(true);
        }
    }

    public static MemoryMapImpl mergeMemoryMaps(AbstractMergeNode merge, List<? extends MemoryMap> states) {
        MemoryMapImpl newState = new MemoryMapImpl();

        Set<LocationIdentity> keys = CollectionsFactory.newSet();
        for (MemoryMap other : states) {
            keys.addAll(other.getLocations());
        }
        assert checkNoImmutableLocations(keys);

        for (LocationIdentity key : keys) {
            int mergedStatesCount = 0;
            boolean isPhi = false;
            MemoryNode merged = null;
            for (MemoryMap state : states) {
                MemoryNode last = state.getLastLocationAccess(key);
                if (isPhi) {
                    ((MemoryPhiNode) merged).addInput(ValueNodeUtil.asNode(last));
                } else {
                    if (merged == last) {
                        // nothing to do
                    } else if (merged == null) {
                        merged = last;
                    } else {
                        MemoryPhiNode phi = merge.graph().addWithoutUnique(new MemoryPhiNode(merge, key));
                        for (int j = 0; j < mergedStatesCount; j++) {
                            phi.addInput(ValueNodeUtil.asNode(merged));
                        }
                        phi.addInput(ValueNodeUtil.asNode(last));
                        merged = phi;
                        isPhi = true;
                    }
                }
                mergedStatesCount++;
            }
            newState.lastMemorySnapshot.put(key, merged);
        }
        return newState;

    }

    private static boolean checkNoImmutableLocations(Set<LocationIdentity> keys) {
        keys.forEach(t -> {
            assert !t.isImmutable();
        });
        return true;
    }

    public static class CollectMemoryCheckpointsClosure extends NodeIteratorClosure<Set<LocationIdentity>> {

        private final Map<LoopBeginNode, Set<LocationIdentity>> modifiedInLoops;

        public CollectMemoryCheckpointsClosure(Map<LoopBeginNode, Set<LocationIdentity>> modifiedInLoops) {
            this.modifiedInLoops = modifiedInLoops;
        }

        @Override
        protected Set<LocationIdentity> processNode(FixedNode node, Set<LocationIdentity> currentState) {
            if (node instanceof MemoryCheckpoint.Single) {
                currentState.add(((MemoryCheckpoint.Single) node).getLocationIdentity());
            } else if (node instanceof MemoryCheckpoint.Multi) {
                for (LocationIdentity identity : ((MemoryCheckpoint.Multi) node).getLocationIdentities()) {
                    currentState.add(identity);
                }
            }
            return currentState;
        }

        @Override
        protected Set<LocationIdentity> merge(AbstractMergeNode merge, List<Set<LocationIdentity>> states) {
            Set<LocationIdentity> result = CollectionsFactory.newSet();
            for (Set<LocationIdentity> other : states) {
                result.addAll(other);
            }
            return result;
        }

        @Override
        protected Set<LocationIdentity> afterSplit(AbstractBeginNode node, Set<LocationIdentity> oldState) {
            return CollectionsFactory.newSet(oldState);
        }

        @Override
        protected Map<LoopExitNode, Set<LocationIdentity>> processLoop(LoopBeginNode loop, Set<LocationIdentity> initialState) {
            LoopInfo<Set<LocationIdentity>> loopInfo = ReentrantNodeIterator.processLoop(this, loop, CollectionsFactory.newSet());
            Set<LocationIdentity> modifiedLocations = CollectionsFactory.newSet();
            for (Set<LocationIdentity> end : loopInfo.endStates.values()) {
                modifiedLocations.addAll(end);
            }
            for (Set<LocationIdentity> exit : loopInfo.exitStates.values()) {
                exit.addAll(modifiedLocations);
                exit.addAll(initialState);
            }
            assert checkNoImmutableLocations(modifiedLocations);
            modifiedInLoops.put(loop, modifiedLocations);
            return loopInfo.exitStates;
        }

    }

    public static class FloatingReadClosure extends NodeIteratorClosure<MemoryMapImpl> {

        private final Map<LoopBeginNode, Set<LocationIdentity>> modifiedInLoops;
        private boolean createFloatingReads;
        private boolean createMemoryMapNodes;

        public FloatingReadClosure(Map<LoopBeginNode, Set<LocationIdentity>> modifiedInLoops, boolean createFloatingReads, boolean createMemoryMapNodes) {
            this.modifiedInLoops = modifiedInLoops;
            this.createFloatingReads = createFloatingReads;
            this.createMemoryMapNodes = createMemoryMapNodes;
        }

        @Override
        protected MemoryMapImpl processNode(FixedNode node, MemoryMapImpl state) {
            if (node instanceof MemoryAccess) {
                processAccess((MemoryAccess) node, state);
            }

            if (createFloatingReads & node instanceof FloatableAccessNode) {
                processFloatable((FloatableAccessNode) node, state);
            } else if (node instanceof MemoryCheckpoint.Single) {
                processCheckpoint((MemoryCheckpoint.Single) node, state);
            } else if (node instanceof MemoryCheckpoint.Multi) {
                processCheckpoint((MemoryCheckpoint.Multi) node, state);
            }
            assert MemoryCheckpoint.TypeAssertion.correctType(node) : node;

            if (createMemoryMapNodes && node instanceof ReturnNode) {
                ((ReturnNode) node).setMemoryMap(node.graph().unique(new MemoryMapNode(state.lastMemorySnapshot)));
            }
            return state;
        }

        private static void processAccess(MemoryAccess access, MemoryMapImpl state) {
            LocationIdentity locationIdentity = access.getLocationIdentity();
            if (!locationIdentity.equals(LocationIdentity.ANY_LOCATION)) {
                MemoryNode lastLocationAccess = state.getLastLocationAccess(locationIdentity);
                access.setLastLocationAccess(lastLocationAccess);
            }
        }

        private static void processCheckpoint(MemoryCheckpoint.Single checkpoint, MemoryMapImpl state) {
            processIdentity(checkpoint.getLocationIdentity(), checkpoint, state);
        }

        private static void processCheckpoint(MemoryCheckpoint.Multi checkpoint, MemoryMapImpl state) {
            for (LocationIdentity identity : checkpoint.getLocationIdentities()) {
                processIdentity(identity, checkpoint, state);
            }
        }

        private static void processIdentity(LocationIdentity identity, MemoryCheckpoint checkpoint, MemoryMapImpl state) {
            if (identity.equals(ANY_LOCATION)) {
                state.lastMemorySnapshot.clear();
            }
            state.lastMemorySnapshot.put(identity, checkpoint);
        }

        private static void processFloatable(FloatableAccessNode accessNode, MemoryMapImpl state) {
            StructuredGraph graph = accessNode.graph();
            assert accessNode.getNullCheck() == false;
            LocationIdentity locationIdentity = accessNode.location().getLocationIdentity();
            if (accessNode.canFloat()) {
                MemoryNode lastLocationAccess = state.getLastLocationAccess(locationIdentity);
                FloatingAccessNode floatingNode = accessNode.asFloatingNode(lastLocationAccess);
                ValueAnchorNode anchor = null;
                GuardingNode guard = accessNode.getGuard();
                if (guard != null) {
                    anchor = graph.add(new ValueAnchorNode(guard.asNode()));
                    graph.addAfterFixed(accessNode, anchor);
                }
                graph.replaceFixedWithFloating(accessNode, floatingNode);
            }
        }

        @Override
        protected MemoryMapImpl merge(AbstractMergeNode merge, List<MemoryMapImpl> states) {
            return mergeMemoryMaps(merge, states);
        }

        @Override
        protected MemoryMapImpl afterSplit(AbstractBeginNode node, MemoryMapImpl oldState) {
            MemoryMapImpl result = new MemoryMapImpl(oldState);
            if (node.predecessor() instanceof InvokeWithExceptionNode) {
                /*
                 * InvokeWithException cannot be the lastLocationAccess for a FloatingReadNode.
                 * Since it is both the invoke and a control flow split, the scheduler cannot
                 * schedule anything immediately after the invoke. It can only schedule in the
                 * normal or exceptional successor - and we have to tell the scheduler here which
                 * side it needs to choose by putting in the location identity on both successors.
                 */
                InvokeWithExceptionNode invoke = (InvokeWithExceptionNode) node.predecessor();
                result.lastMemorySnapshot.put(invoke.getLocationIdentity(), (MemoryCheckpoint) node);
            }
            return result;
        }

        @Override
        protected Map<LoopExitNode, MemoryMapImpl> processLoop(LoopBeginNode loop, MemoryMapImpl initialState) {
            Set<LocationIdentity> modifiedLocations = modifiedInLoops.get(loop);
            if (modifiedLocations.contains(ANY_LOCATION)) {
                // create phis for all locations if ANY is modified in the loop
                modifiedLocations = CollectionsFactory.newSet(modifiedLocations);
                modifiedLocations.addAll(initialState.lastMemorySnapshot.keySet());
            }

            Map<LocationIdentity, MemoryPhiNode> phis = CollectionsFactory.newMap();

            for (LocationIdentity location : modifiedLocations) {
                MemoryPhiNode phi = loop.graph().addWithoutUnique(new MemoryPhiNode(loop, location));
                phi.addInput(ValueNodeUtil.asNode(initialState.getLastLocationAccess(location)));
                phis.put(location, phi);
            }
            for (Map.Entry<LocationIdentity, MemoryPhiNode> entry : phis.entrySet()) {
                initialState.lastMemorySnapshot.put(entry.getKey(), entry.getValue());
            }

            LoopInfo<MemoryMapImpl> loopInfo = ReentrantNodeIterator.processLoop(this, loop, initialState);

            for (Map.Entry<LoopEndNode, MemoryMapImpl> entry : loopInfo.endStates.entrySet()) {
                int endIndex = loop.phiPredecessorIndex(entry.getKey());
                for (Map.Entry<LocationIdentity, MemoryPhiNode> phiEntry : phis.entrySet()) {
                    LocationIdentity key = phiEntry.getKey();
                    PhiNode phi = phiEntry.getValue();
                    phi.initializeValueAt(endIndex, ValueNodeUtil.asNode(entry.getValue().getLastLocationAccess(key)));
                }
            }
            return loopInfo.exitStates;
        }
    }
}
