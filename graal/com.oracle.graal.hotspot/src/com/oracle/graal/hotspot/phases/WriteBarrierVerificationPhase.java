/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.hotspot.phases;

import java.util.*;

import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.extended.WriteNode.WriteBarrierType;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.graph.*;

public class WriteBarrierVerificationPhase extends Phase {

    private class MemoryMap implements MergeableState<MemoryMap> {

        private IdentityHashMap<Object, LinkedList<LocationNode>> lastMemorySnapshot;
        private IdentityHashMap<Object, LinkedList<SerialWriteBarrier>> lastWriteBarrierSnapshot;

        public MemoryMap(MemoryMap memoryMap) {
            lastMemorySnapshot = new IdentityHashMap<>(memoryMap.lastMemorySnapshot);
            lastWriteBarrierSnapshot = new IdentityHashMap<>(memoryMap.lastWriteBarrierSnapshot);
        }

        public MemoryMap() {
            lastMemorySnapshot = new IdentityHashMap<>();
            lastWriteBarrierSnapshot = new IdentityHashMap<>();
        }

        @Override
        public String toString() {
            return "Map=" + lastMemorySnapshot.toString();
        }

        @Override
        public boolean merge(MergeNode merge, List<MemoryMap> withStates) {
            if (withStates.size() == 0) {
                return true;
            }

            for (MemoryMap other : withStates) {
                for (Object otherObject : other.lastMemorySnapshot.keySet()) {
                    LinkedList<LocationNode> currentLocations = lastMemorySnapshot.get(otherObject);
                    LinkedList<LocationNode> otherLocations = other.lastMemorySnapshot.get(otherObject);
                    if (otherLocations != null) {
                        if (currentLocations == null) {
                            currentLocations = new LinkedList<>();
                        }
                        for (LocationNode location : otherLocations) {
                            if (!currentLocations.contains(location)) {
                                currentLocations.add(location);
                            }
                        }
                    }
                }
                for (Object otherObject : other.lastWriteBarrierSnapshot.keySet()) {
                    LinkedList<SerialWriteBarrier> currentWriteBarriers = lastWriteBarrierSnapshot.get(otherObject);
                    LinkedList<SerialWriteBarrier> otherWriteBarriers = other.lastWriteBarrierSnapshot.get(otherObject);
                    if (otherWriteBarriers != null) {
                        if (currentWriteBarriers == null) {
                            currentWriteBarriers = new LinkedList<>();
                        }
                        for (SerialWriteBarrier barrier : otherWriteBarriers) {
                            if (!currentWriteBarriers.contains(barrier)) {
                                currentWriteBarriers.add(barrier);
                            }
                        }
                    }
                }
            }
            return true;
        }

        @Override
        public void loopBegin(LoopBeginNode loopBegin) {
        }

        @Override
        public void loopEnds(LoopBeginNode loopBegin, List<MemoryMap> loopEndStates) {
        }

        @Override
        public void afterSplit(BeginNode node) {
        }

        @Override
        public MemoryMap clone() {
            return new MemoryMap(this);
        }
    }

    @Override
    protected void run(StructuredGraph graph) {
        new PostOrderNodeIterator<MemoryMap>(graph.start(), new MemoryMap()) {

            @Override
            protected void node(FixedNode node) {
                processNode(node, state);
            }
        }.apply();
    }

    private static void processNode(FixedNode node, MemoryMap state) {
        if (node instanceof WriteNode) {
            processWriteNode((WriteNode) node, state);
        } else if (node instanceof CompareAndSwapNode) {
            processCASNode((CompareAndSwapNode) node, state);
        } else if (node instanceof SerialWriteBarrier) {
            processWriteBarrier((SerialWriteBarrier) node, state);
        } else if ((node instanceof DeoptimizingNode)) {
            if (((DeoptimizingNode) node).canDeoptimize()) {
                validateWriteBarriers(state);
                processSafepoint(state);
            }
        }
    }

    private static void processWriteNode(WriteNode node, MemoryMap state) {
        if (node.getWriteBarrierType() != WriteBarrierType.NONE) {
            LinkedList<LocationNode> locations = state.lastMemorySnapshot.get(node.object());
            if (locations == null) {
                locations = new LinkedList<>();
                locations.add(node.location());
                state.lastMemorySnapshot.put(node.object(), locations);
            } else if ((node.getWriteBarrierType() == WriteBarrierType.PRECISE) && !locations.contains(node.location())) {
                locations.add(node.location());
            }
        }
    }

    private static void processCASNode(CompareAndSwapNode node, MemoryMap state) {
        if (node.getWriteBarrierType() != WriteBarrierType.NONE) {
            LinkedList<LocationNode> locations = state.lastMemorySnapshot.get(node.object());
            if (locations == null) {
                locations = new LinkedList<>();
                locations.add(node.getLocation());
                state.lastMemorySnapshot.put(node.object(), locations);
            } else if ((node.getWriteBarrierType() == WriteBarrierType.PRECISE) && !locations.contains(node.getLocation())) {
                locations.add(node.getLocation());
            }
        }
    }

    private static void processWriteBarrier(SerialWriteBarrier currentBarrier, MemoryMap state) {
        LinkedList<SerialWriteBarrier> writeBarriers = state.lastWriteBarrierSnapshot.get(currentBarrier.getObject());
        if (writeBarriers == null) {
            writeBarriers = new LinkedList<>();
            writeBarriers.add(currentBarrier);
            state.lastWriteBarrierSnapshot.put(currentBarrier.getObject(), writeBarriers);
        } else if (currentBarrier.usePrecise()) {
            boolean found = false;
            for (SerialWriteBarrier barrier : writeBarriers) {
                if (barrier.getLocation() == currentBarrier.getLocation()) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                writeBarriers.add(currentBarrier);
            }
        }
    }

    private static void validateWriteBarriers(MemoryMap state) {
        Set<Object> objects = state.lastMemorySnapshot.keySet();
        for (Object write : objects) {
            LinkedList<SerialWriteBarrier> writeBarriers = state.lastWriteBarrierSnapshot.get(write);
            if (writeBarriers == null) {
                throw new GraalInternalError("Failed to find any write barrier at safepoint for written object");
            }
            /*
             * Check the first write barrier of the object to determine if it is precise or not. If
             * it is not, the validation for this object has passed (since we had a hit in the write
             * barrier hashmap), otherwise we have to ensure the presence of write barriers for
             * every written location.
             */
            final boolean precise = writeBarriers.getFirst().usePrecise();
            if (precise) {
                LinkedList<LocationNode> locations = state.lastMemorySnapshot.get(write);
                for (LocationNode location : locations) {
                    boolean found = false;
                    for (SerialWriteBarrier barrier : writeBarriers) {
                        if (location == barrier.getLocation()) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        throw new GraalInternalError("Failed to find write barrier at safepoint for precise written object");
                    }
                }
            }
        }
    }

    private static void processSafepoint(MemoryMap state) {
        state.lastMemorySnapshot.clear();
        state.lastWriteBarrierSnapshot.clear();
    }
}
