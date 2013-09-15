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

import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.PhiNode.PhiType;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.graph.*;
import com.oracle.graal.phases.graph.ReentrantNodeIterator.LoopInfo;
import com.oracle.graal.phases.graph.ReentrantNodeIterator.NodeIteratorClosure;

public class FloatingReadPhase extends Phase {

    private static class MemoryMap {

        private IdentityHashMap<LocationIdentity, ValueNode> lastMemorySnapshot;

        public MemoryMap(MemoryMap memoryMap) {
            lastMemorySnapshot = new IdentityHashMap<>(memoryMap.lastMemorySnapshot);
        }

        public MemoryMap(StartNode start) {
            this();
            lastMemorySnapshot.put(ANY_LOCATION, start);
        }

        public MemoryMap() {
            lastMemorySnapshot = new IdentityHashMap<>();
        }

        private ValueNode getLastLocationAccess(LocationIdentity locationIdentity) {
            ValueNode lastLocationAccess;
            if (locationIdentity == FINAL_LOCATION) {
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
        public String toString() {
            return "Map=" + lastMemorySnapshot.toString();
        }
    }

    @Override
    protected void run(StructuredGraph graph) {
        Map<LoopBeginNode, Set<LocationIdentity>> modifiedInLoops = new IdentityHashMap<>();
        ReentrantNodeIterator.apply(new CollectMemoryCheckpointsClosure(modifiedInLoops), graph.start(), new HashSet<LocationIdentity>(), null);
        ReentrantNodeIterator.apply(new FloatingReadClosure(modifiedInLoops), graph.start(), new MemoryMap(graph.start()), null);
    }

    private static class CollectMemoryCheckpointsClosure extends NodeIteratorClosure<Set<LocationIdentity>> {

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
        protected Set<LocationIdentity> merge(MergeNode merge, List<Set<LocationIdentity>> states) {
            Set<LocationIdentity> result = new HashSet<>();
            for (Set<LocationIdentity> other : states) {
                result.addAll(other);
            }
            return result;
        }

        @Override
        protected Set<LocationIdentity> afterSplit(AbstractBeginNode node, Set<LocationIdentity> oldState) {
            return new HashSet<>(oldState);
        }

        @Override
        protected Map<LoopExitNode, Set<LocationIdentity>> processLoop(LoopBeginNode loop, Set<LocationIdentity> initialState) {
            LoopInfo<Set<LocationIdentity>> loopInfo = ReentrantNodeIterator.processLoop(this, loop, new HashSet<LocationIdentity>());
            Set<LocationIdentity> modifiedLocations = new HashSet<>();
            for (Set<LocationIdentity> end : loopInfo.endStates.values()) {
                modifiedLocations.addAll(end);
            }
            for (Set<LocationIdentity> exit : loopInfo.exitStates.values()) {
                exit.addAll(modifiedLocations);
                exit.addAll(initialState);
            }
            assert !modifiedLocations.contains(FINAL_LOCATION);
            modifiedInLoops.put(loop, modifiedLocations);
            return loopInfo.exitStates;
        }

    }

    private static class FloatingReadClosure extends NodeIteratorClosure<MemoryMap> {

        private final Map<LoopBeginNode, Set<LocationIdentity>> modifiedInLoops;

        public FloatingReadClosure(Map<LoopBeginNode, Set<LocationIdentity>> modifiedInLoops) {
            this.modifiedInLoops = modifiedInLoops;
        }

        @Override
        protected MemoryMap processNode(FixedNode node, MemoryMap state) {
            if (node instanceof FloatableAccessNode) {
                processFloatable((FloatableAccessNode) node, state);
            } else if (node instanceof MemoryCheckpoint.Single) {
                processCheckpoint((MemoryCheckpoint.Single) node, state);
            } else if (node instanceof MemoryCheckpoint.Multi) {
                processCheckpoint((MemoryCheckpoint.Multi) node, state);
            }
            assert MemoryCheckpoint.TypeAssertion.correctType(node) : node;
            return state;
        }

        private static void processCheckpoint(MemoryCheckpoint.Single checkpoint, MemoryMap state) {
            LocationIdentity identity = checkpoint.getLocationIdentity();
            if (identity == ANY_LOCATION) {
                state.lastMemorySnapshot.clear();
            }
            state.lastMemorySnapshot.put(identity, (ValueNode) checkpoint);
        }

        private static void processCheckpoint(MemoryCheckpoint.Multi checkpoint, MemoryMap state) {
            for (LocationIdentity identity : checkpoint.getLocationIdentities()) {
                if (identity == ANY_LOCATION) {
                    state.lastMemorySnapshot.clear();
                }
                state.lastMemorySnapshot.put(identity, (ValueNode) checkpoint);
            }
        }

        private static void processFloatable(FloatableAccessNode accessNode, MemoryMap state) {
            StructuredGraph graph = accessNode.graph();
            assert accessNode.getNullCheck() == false;
            LocationIdentity locationIdentity = accessNode.location().getLocationIdentity();
            if (accessNode.canFloat()) {
                ValueNode lastLocationAccess = state.getLastLocationAccess(locationIdentity);
                FloatingAccessNode floatingNode = accessNode.asFloatingNode(lastLocationAccess);
                floatingNode.setNullCheck(accessNode.getNullCheck());
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
        protected MemoryMap merge(MergeNode merge, List<MemoryMap> states) {
            MemoryMap newState = new MemoryMap();

            Set<LocationIdentity> keys = new HashSet<>();
            for (MemoryMap other : states) {
                keys.addAll(other.lastMemorySnapshot.keySet());
            }
            assert !keys.contains(FINAL_LOCATION);

            for (LocationIdentity key : keys) {
                int mergedStatesCount = 0;
                boolean isPhi = false;
                ValueNode merged = null;
                for (MemoryMap state : states) {
                    ValueNode last = state.getLastLocationAccess(key);
                    if (isPhi) {
                        ((PhiNode) merged).addInput(last);
                    } else {
                        if (merged == last) {
                            // nothing to do
                        } else if (merged == null) {
                            merged = last;
                        } else {
                            PhiNode phi = merge.graph().addWithoutUnique(new PhiNode(PhiType.Memory, merge, key));
                            for (int j = 0; j < mergedStatesCount; j++) {
                                phi.addInput(merged);
                            }
                            phi.addInput(last);
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

        @Override
        protected MemoryMap afterSplit(AbstractBeginNode node, MemoryMap oldState) {
            MemoryMap result = new MemoryMap(oldState);
            if (node.predecessor() instanceof InvokeWithExceptionNode) {
                /*
                 * InvokeWithException cannot be the lastLocationAccess for a FloatingReadNode.
                 * Since it is both the invoke and a control flow split, the scheduler cannot
                 * schedule anything immediately after the invoke. It can only schedule in the
                 * normal or exceptional successor - and we have to tell the scheduler here which
                 * side it needs to choose by putting in the location identity on both successors.
                 */
                InvokeWithExceptionNode invoke = (InvokeWithExceptionNode) node.predecessor();
                result.lastMemorySnapshot.put(invoke.getLocationIdentity(), node);
            }
            return result;
        }

        @Override
        protected Map<LoopExitNode, MemoryMap> processLoop(LoopBeginNode loop, MemoryMap initialState) {
            Set<LocationIdentity> modifiedLocations = modifiedInLoops.get(loop);
            if (modifiedLocations.contains(ANY_LOCATION)) {
                // create phis for all locations if ANY is modified in the loop
                modifiedLocations = new HashSet<>(modifiedLocations);
                modifiedLocations.addAll(initialState.lastMemorySnapshot.keySet());
            }

            Map<LocationIdentity, PhiNode> phis = new HashMap<>();
            for (LocationIdentity location : modifiedLocations) {
                PhiNode phi = loop.graph().addWithoutUnique(new PhiNode(PhiType.Memory, loop, location));
                phi.addInput(initialState.getLastLocationAccess(location));
                phis.put(location, phi);
                initialState.lastMemorySnapshot.put(location, phi);
            }

            LoopInfo<MemoryMap> loopInfo = ReentrantNodeIterator.processLoop(this, loop, initialState);

            for (Map.Entry<LoopEndNode, MemoryMap> entry : loopInfo.endStates.entrySet()) {
                int endIndex = loop.phiPredecessorIndex(entry.getKey());
                for (Map.Entry<LocationIdentity, PhiNode> phiEntry : phis.entrySet()) {
                    LocationIdentity key = phiEntry.getKey();
                    PhiNode phi = phiEntry.getValue();
                    phi.initializeValueAt(endIndex, entry.getValue().getLastLocationAccess(key));
                }
            }
            for (Map.Entry<LoopExitNode, MemoryMap> entry : loopInfo.exitStates.entrySet()) {
                LoopExitNode exit = entry.getKey();
                MemoryMap state = entry.getValue();
                for (LocationIdentity location : modifiedLocations) {
                    ValueNode lastAccessAtExit = state.lastMemorySnapshot.get(location);
                    if (lastAccessAtExit != null) {
                        state.lastMemorySnapshot.put(location, ProxyNode.forMemory(lastAccessAtExit, exit, location, loop.graph()));
                    }
                }
            }
            return loopInfo.exitStates;
        }
    }
}
