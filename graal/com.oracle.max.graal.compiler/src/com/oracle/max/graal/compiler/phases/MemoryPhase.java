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
package com.oracle.max.graal.compiler.phases;

import java.util.*;
import java.util.Map.Entry;

import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.debug.*;
import com.oracle.max.graal.compiler.ir.*;
import com.oracle.max.graal.compiler.ir.Phi.*;
import com.oracle.max.graal.compiler.schedule.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.graph.collections.*;
import com.sun.cri.ci.*;

public class MemoryPhase extends Phase {

    public static class MemoryMap {

        private final Block block;
        private HashMap<Object, Node> locationForWrite;
        private HashMap<Object, Node> locationForRead;
        private Node mergeForWrite;
        private Node mergeForRead;
        private int mergeOperationCount;
        private Node loopCheckPoint;
        private MemoryMap loopEntryMap;

        public MemoryMap(Block b, MemoryMap memoryMap) {
            this(b);
            if (b.firstNode() instanceof LoopBegin) {
                loopCheckPoint = new WriteMemoryCheckpointNode(b.firstNode().graph());
                mergeForWrite = loopCheckPoint;
                mergeForRead = loopCheckPoint;
                this.loopEntryMap = memoryMap;
            } else {
                memoryMap.locationForWrite.putAll(memoryMap.locationForWrite);
                memoryMap.locationForRead.putAll(memoryMap.locationForRead);
                mergeForWrite = memoryMap.mergeForWrite;
                mergeForRead = memoryMap.mergeForRead;
            }
        }

        public MemoryMap(Block b) {
            if (GraalOptions.TraceMemoryMaps) {
                TTY.println("Creating new memory map for block B" + b.blockID());
            }

            block = b;
            locationForWrite = new HashMap<Object, Node>();
            locationForRead = new HashMap<Object, Node>();
            StartNode startNode = b.firstNode().graph().start();
            if (b.firstNode() == startNode) {
                WriteMemoryCheckpointNode checkpoint = new WriteMemoryCheckpointNode(startNode.graph());
                checkpoint.setNext((FixedNode) startNode.next());
                startNode.setNext(checkpoint);
                mergeForWrite = checkpoint;
                mergeForRead = checkpoint;
            }
        }

        public Node getLoopCheckPoint() {
            return loopCheckPoint;
        }

        public MemoryMap getLoopEntryMap() {
            return loopEntryMap;
        }

        public void resetMergeOperationCount() {
            mergeOperationCount = 0;
        }

        public void mergeWith(MemoryMap memoryMap, Block block) {
            if (GraalOptions.TraceMemoryMaps) {
                TTY.println("Merging with memory map of block B" + memoryMap.block.blockID());
            }
            mergeForWrite = mergeNodes(mergeForWrite, memoryMap.mergeForWrite, locationForWrite, memoryMap.locationForWrite, block);
            mergeForRead = mergeNodes(mergeForRead, memoryMap.mergeForRead, locationForRead, memoryMap.locationForRead, block);
            mergeOperationCount++;
        }

        private Node mergeNodes(Node mergeLeft, Node mergeRight, HashMap<Object, Node> locationLeft, HashMap<Object, Node> locationRight, Block block) {
            if (GraalOptions.TraceMemoryMaps) {
                TTY.println("Merging main merge nodes: " + mergeLeft.id() + " and " + mergeRight.id());
            }

            for (Entry<Object, Node> e : locationRight.entrySet()) {
                if (!locationLeft.containsKey(e.getKey())) {
                    // Only available in right map => create correct node for left map.
                    if (GraalOptions.TraceMemoryMaps) {
                        TTY.println("Only right map " + e.getKey());
                    }
                    Node leftNode = mergeLeft;
                    if (leftNode instanceof Phi && ((Phi) leftNode).merge() == block.firstNode()) {
                        leftNode = leftNode.copyWithEdges();
                    }
                    locationLeft.put(e.getKey(), leftNode);
                }
            }

            for (Entry<Object, Node> e : locationLeft.entrySet()) {
                if (locationRight.containsKey(e.getKey())) {
                    // Available in both maps.
                    if (GraalOptions.TraceMemoryMaps) {
                        TTY.println("Merging entries for location " + e.getKey());
                    }
                    locationLeft.put(e.getKey(), mergeNodes(e.getValue(), locationRight.get(e.getKey()), block));
                } else {
                    // Only available in left map.
                    if (GraalOptions.TraceMemoryMaps) {
                        TTY.println("Only available in left map " + e.getKey());
                    }
                    locationLeft.put(e.getKey(), mergeNodes(e.getValue(), mergeRight, block));
                }
            }

            return mergeNodes(mergeLeft, mergeRight, block);
        }

        private Node mergeNodes(Node original, Node newValue, Block block) {
            if (original == newValue) {
                // Nothing to merge.
                if (GraalOptions.TraceMemoryMaps) {
                    TTY.println("Nothing to merge both nodes are " + original.id());
                }
                return original;
            }
            Merge m = (Merge) block.firstNode();
            if (original instanceof Phi && ((Phi) original).merge() == m) {
                Phi phi = (Phi) original;
                phi.addInput(newValue);
                if (GraalOptions.TraceMemoryMaps) {
                    TTY.println("Add new input to phi " + original.id());
                }
                assert phi.valueCount() <= phi.merge().endCount();
                return original;
            } else {
                Phi phi = new Phi(CiKind.Illegal, m, PhiType.Memory, m.graph());
                for (int i = 0; i < mergeOperationCount + 1; ++i) {
                    phi.addInput(original);
                }
                phi.addInput(newValue);
                if (GraalOptions.TraceMemoryMaps) {
                    TTY.println("Creating new phi " + phi.id());
                }
                assert phi.valueCount() <= phi.merge().endCount() + ((phi.merge() instanceof LoopBegin) ? 1 : 0) : phi.merge() + "/" + phi.valueCount() + "/" + phi.merge().endCount() + "/" + mergeOperationCount;
                return phi;
            }
        }

        public void createWriteMemoryMerge(AbstractMemoryCheckpointNode memMerge) {
            if (GraalOptions.TraceMemoryMaps) {
                TTY.println("Creating write memory checkpoint at node " + memMerge.id());
            }

            // Merge in all writes.
            for (Entry<Object, Node> writeEntry : locationForWrite.entrySet()) {
                memMerge.mergedNodes().add(writeEntry.getValue());
            }
            locationForWrite.clear();
            mergeForWrite = memMerge;
        }

        public void createReadWriteMemoryCheckpoint(AbstractMemoryCheckpointNode memMerge) {
            if (GraalOptions.TraceMemoryMaps) {
                TTY.println("Creating readwrite memory checkpoint at node " + memMerge.id());
            }
            createWriteMemoryMerge(memMerge);
            locationForRead.clear();
            mergeForRead = memMerge;
        }

        public void registerWrite(WriteNode node) {
            Object location = node.location().locationIdentity();
            if (GraalOptions.TraceMemoryMaps) {
                TTY.println("Register write to " + location + " at node " + node.id());
            }

            // Create dependency on previous write to same location.
            node.addDependency(getLocationForWrite(node));

            locationForWrite.put(location, node);
            locationForRead.put(location, node);
        }

        public Node getLocationForWrite(WriteNode node) {
            Object location = node.location().locationIdentity();
            if (locationForWrite.containsKey(location)) {
                Node prevWrite = locationForWrite.get(location);
                return prevWrite;
            } else {
                return mergeForWrite;
            }
        }

        public void registerRead(ReadNode node) {
            if (GraalOptions.TraceMemoryMaps) {
                TTY.println("Register read to node " + node.id());
            }

            // Create dependency on previous node that creates the memory state for this location.
            node.addDependency(getLocationForRead(node));
        }

        public Node getLocationForRead(ReadNode node) {
            Object location = node.location().locationIdentity();
            if (locationForRead.containsKey(location)) {
                return locationForRead.get(location);
            }
            return mergeForRead;
        }

        public void replaceCheckPoint(Node loopCheckPoint) {
            for (Node n : loopCheckPoint.usages().snapshot()) {
                replaceCheckPoint(loopCheckPoint, n);
            }
        }

        private void replaceCheckPoint(Node loopCheckPoint, Node n) {
            if (n instanceof ReadNode) {
                n.replaceFirstInput(loopCheckPoint, getLocationForRead((ReadNode) n));
            } else if (n instanceof WriteNode) {
                n.replaceFirstInput(loopCheckPoint, getLocationForWrite((WriteNode) n));
            } else if (n instanceof WriteMemoryCheckpointNode) {
                n.replaceFirstInput(loopCheckPoint, mergeForWrite);
            } else {
                n.replaceFirstInput(loopCheckPoint, mergeForRead);
            }
        }
    }

    @Override
    protected void run(final Graph graph) {
        final IdentifyBlocksPhase s = new IdentifyBlocksPhase(false);
        s.apply(graph);

        List<Block> blocks = s.getBlocks();
        MemoryMap[] memoryMaps = new MemoryMap[blocks.size()];
        for (final Block b : blocks) {
            process(b, memoryMaps, s.getNodeToBlock());
        }
    }

    private void process(final Block b, MemoryMap[] memoryMaps, NodeMap<Block> nodeMap) {
        // Visit every block at most once.
        if (memoryMaps[b.blockID()] != null) {
            return;
        }

        // Process predecessors before this block.
        for (Block pred : b.getPredecessors()) {
            process(pred, memoryMaps, nodeMap);
        }

        // Create initial memory map for the block.
        MemoryMap map = null;
        if (b.getPredecessors().size() == 0) {
            map = new MemoryMap(b);
        } else {
            map = new MemoryMap(b, memoryMaps[b.getPredecessors().get(0).blockID()]);
            for (int i = 1; i < b.getPredecessors().size(); ++i) {
                assert b.firstNode() instanceof Merge : b.firstNode();
                Block block = b.getPredecessors().get(i);
                map.mergeWith(memoryMaps[block.blockID()], b);
            }
        }

        // Create the floating memory checkpoint instructions.
        for (final Node n : b.getInstructions()) {
            if (n instanceof ReadNode) {
                ReadNode readNode = (ReadNode) n;
                readNode.replaceAtPredecessors(readNode.next());
                readNode.setNext(null);
                map.registerRead(readNode);
            } else if (n instanceof WriteNode) {
                WriteNode writeNode = (WriteNode) n;
                WriteMemoryCheckpointNode checkpoint = new WriteMemoryCheckpointNode(writeNode.graph());
                checkpoint.setStateAfter(writeNode.stateAfter());
                writeNode.setStateAfter(null);
                checkpoint.setNext(writeNode.next());
                writeNode.setNext(null);
                writeNode.replaceAtPredecessors(checkpoint);
                map.registerWrite(writeNode);
                map.createWriteMemoryMerge(checkpoint);
            } else if (n instanceof AbstractMemoryCheckpointNode) {
                map.createReadWriteMemoryCheckpoint((AbstractMemoryCheckpointNode) n);
            }
        }

        memoryMaps[b.blockID()] = map;
        if (b.lastNode() instanceof LoopEnd) {
            LoopEnd end = (LoopEnd) b.lastNode();
            LoopBegin begin = end.loopBegin();
            Block beginBlock = nodeMap.get(begin);
            MemoryMap memoryMap = memoryMaps[beginBlock.blockID()];
            assert memoryMap != null : beginBlock.name();
            assert memoryMap.getLoopEntryMap() != null;
            memoryMap.getLoopEntryMap().resetMergeOperationCount();
            memoryMap.getLoopEntryMap().mergeWith(map, beginBlock);
            Node loopCheckPoint = memoryMap.getLoopCheckPoint();
            memoryMap.getLoopEntryMap().replaceCheckPoint(loopCheckPoint);
        }
    }
}
