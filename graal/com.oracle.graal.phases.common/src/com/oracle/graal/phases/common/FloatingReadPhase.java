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

import java.util.*;

import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.PhiNode.PhiType;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.graph.*;
import com.oracle.graal.phases.graph.ReentrantNodeIterator.LoopInfo;
import com.oracle.graal.phases.graph.ReentrantNodeIterator.NodeIteratorClosure;

public class FloatingReadPhase extends Phase {

    private static class MemoryMap {

        private IdentityHashMap<Object, ValueNode> lastMemorySnapshot;

        public MemoryMap(MemoryMap memoryMap) {
            lastMemorySnapshot = new IdentityHashMap<>(memoryMap.lastMemorySnapshot);
        }

        public MemoryMap(StartNode start) {
            this();
            lastMemorySnapshot.put(LocationNode.ANY_LOCATION, start);
        }

        public MemoryMap() {
            lastMemorySnapshot = new IdentityHashMap<>();
        }

        private ValueNode getLastLocationAccess(Object locationIdentity) {
            ValueNode lastLocationAccess;
            if (locationIdentity == LocationNode.FINAL_LOCATION) {
                return null;
            } else {
                lastLocationAccess = lastMemorySnapshot.get(locationIdentity);
                if (lastLocationAccess == null) {
                    lastLocationAccess = lastMemorySnapshot.get(LocationNode.ANY_LOCATION);
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

    private final Map<LoopBeginNode, Set<Object>> modifiedInLoops = new IdentityHashMap<>();

    @Override
    protected void run(StructuredGraph graph) {
        ReentrantNodeIterator.apply(new CollectMemoryCheckpointsClosure(), graph.start(), new HashSet<>(), null);
        ReentrantNodeIterator.apply(new FloatingReadClosure(), graph.start(), new MemoryMap(graph.start()), null);
    }

    private class CollectMemoryCheckpointsClosure extends NodeIteratorClosure<Set<Object>> {

        @Override
        protected void processNode(FixedNode node, Set<Object> currentState) {
            if (node instanceof MemoryCheckpoint) {
                for (Object identity : ((MemoryCheckpoint) node).getLocationIdentities()) {
                    currentState.add(identity);
                }
            }
        }

        @Override
        protected Set<Object> merge(MergeNode merge, List<Set<Object>> states) {
            Set<Object> result = new HashSet<>();
            for (Set<Object> other : states) {
                result.addAll(other);
            }
            return result;
        }

        @Override
        protected Set<Object> afterSplit(BeginNode node, Set<Object> oldState) {
            return new HashSet<>(oldState);
        }

        @Override
        protected Map<LoopExitNode, Set<Object>> processLoop(LoopBeginNode loop, Set<Object> initialState) {
            LoopInfo<Set<Object>> loopInfo = ReentrantNodeIterator.processLoop(this, loop, new HashSet<>());
            Set<Object> modifiedLocations = new HashSet<>();
            for (Set<Object> end : loopInfo.endStates.values()) {
                modifiedLocations.addAll(end);
            }
            for (Set<Object> exit : loopInfo.exitStates.values()) {
                exit.addAll(modifiedLocations);
                exit.addAll(initialState);
            }
            assert !modifiedLocations.contains(LocationNode.FINAL_LOCATION);
            modifiedInLoops.put(loop, modifiedLocations);
            return loopInfo.exitStates;
        }

    }

    private class FloatingReadClosure extends NodeIteratorClosure<MemoryMap> {

        @Override
        protected void processNode(FixedNode node, MemoryMap state) {
            if (node instanceof FloatableAccessNode) {
                processFloatable((FloatableAccessNode) node, state);
            } else if (node instanceof MemoryCheckpoint) {
                processCheckpoint((MemoryCheckpoint) node, state);
            }
        }

        private void processCheckpoint(MemoryCheckpoint checkpoint, MemoryMap state) {
            for (Object identity : checkpoint.getLocationIdentities()) {
                if (identity == LocationNode.ANY_LOCATION) {
                    state.lastMemorySnapshot.clear();
                }
                state.lastMemorySnapshot.put(identity, (ValueNode) checkpoint);
            }
        }

        private void processFloatable(FloatableAccessNode accessNode, MemoryMap state) {
            StructuredGraph graph = (StructuredGraph) accessNode.graph();
            assert accessNode.getNullCheck() == false;
            Object locationIdentity = accessNode.location().locationIdentity();
            if (locationIdentity != LocationNode.UNKNOWN_LOCATION) {
                ValueNode lastLocationAccess = state.getLastLocationAccess(locationIdentity);
                FloatingAccessNode floatingNode = accessNode.asFloatingNode(lastLocationAccess);
                floatingNode.setNullCheck(accessNode.getNullCheck());
                ValueAnchorNode anchor = null;
                for (GuardNode guard : accessNode.dependencies().filter(GuardNode.class)) {
                    if (anchor == null) {
                        anchor = graph.add(new ValueAnchorNode());
                        graph.addAfterFixed(accessNode, anchor);
                    }
                    anchor.addAnchoredNode(guard);
                }
                graph.replaceFixedWithFloating(accessNode, floatingNode);
            }
        }

        @Override
        protected MemoryMap merge(MergeNode merge, List<MemoryMap> states) {
            MemoryMap newState = new MemoryMap();

            Set<Object> keys = new HashSet<>();
            for (MemoryMap other : states) {
                keys.addAll(other.lastMemorySnapshot.keySet());
            }
            assert !keys.contains(LocationNode.FINAL_LOCATION);

            for (Object key : keys) {
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
                            PhiNode phi = merge.graph().add(new PhiNode(PhiType.Memory, merge));
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
        protected MemoryMap afterSplit(BeginNode node, MemoryMap oldState) {
            MemoryMap result = new MemoryMap(oldState);
            if (node.predecessor() instanceof InvokeWithExceptionNode) {
                /*
                 * InvokeWithException cannot be the lastLocationAccess for a FloatingReadNode.
                 * Since it is both the invoke and a control flow split, the scheduler cannot
                 * schedule anything immediately the invoke. It can only schedule in the normal or
                 * exceptional successor - and we have to tell the scheduler here which side it
                 * needs to choose by putting in the location identity on both successors.
                 */
                InvokeWithExceptionNode checkpoint = (InvokeWithExceptionNode) node.predecessor();
                for (Object identity : checkpoint.getLocationIdentities()) {
                    result.lastMemorySnapshot.put(identity, node);
                }
            }
            return result;
        }

        @Override
        protected Map<LoopExitNode, MemoryMap> processLoop(LoopBeginNode loop, MemoryMap initialState) {
            Set<Object> modifiedLocations = modifiedInLoops.get(loop);
            if (modifiedLocations.contains(LocationNode.ANY_LOCATION)) {
                // create phis for all locations if ANY is modified in the loop
                modifiedLocations = new HashSet<>(modifiedLocations);
                modifiedLocations.addAll(initialState.lastMemorySnapshot.keySet());
            }

            Map<Object, PhiNode> phis = new HashMap<>();
            for (Object location : modifiedLocations) {
                PhiNode phi = loop.graph().add(new PhiNode(PhiType.Memory, loop));
                phi.addInput(initialState.getLastLocationAccess(location));
                phis.put(location, phi);
                initialState.lastMemorySnapshot.put(location, phi);
            }

            LoopInfo<MemoryMap> loopInfo = ReentrantNodeIterator.processLoop(this, loop, initialState);

            for (Map.Entry<LoopEndNode, MemoryMap> entry : loopInfo.endStates.entrySet()) {
                int endIndex = loop.phiPredecessorIndex(entry.getKey());
                for (Map.Entry<Object, PhiNode> phiEntry : phis.entrySet()) {
                    Object key = phiEntry.getKey();
                    PhiNode phi = phiEntry.getValue();
                    phi.initializeValueAt(endIndex, entry.getValue().getLastLocationAccess(key));
                }
            }
            for (Map.Entry<LoopExitNode, MemoryMap> entry : loopInfo.exitStates.entrySet()) {
                LoopExitNode exit = entry.getKey();
                MemoryMap state = entry.getValue();
                for (Object location : modifiedLocations) {
                    ValueNode lastAccessAtExit = state.lastMemorySnapshot.get(location);
                    if (lastAccessAtExit != null) {
                        state.lastMemorySnapshot.put(location, loop.graph().add(new ProxyNode(lastAccessAtExit, exit, PhiType.Memory)));
                    }
                }
            }
            return loopInfo.exitStates;
        }
    }
}
