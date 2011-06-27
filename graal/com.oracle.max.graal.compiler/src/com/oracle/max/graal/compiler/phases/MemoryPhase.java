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
import com.oracle.max.graal.compiler.schedule.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;

public class MemoryPhase extends Phase {

    public static class MemoryMap {

        private final Block block;
        private HashMap<Object, Node> locationToWrite;
        private HashMap<Object, List<Node>> locationToReads;
        private Node lastReadWriteMerge;
        private Node lastWriteMerge;
        private int mergeOperations;

        public MemoryMap(Block b, MemoryMap memoryMap) {
            this(b);
            for (Entry<Object, Node> e : memoryMap.locationToWrite.entrySet()) {
                locationToWrite.put(e.getKey(), e.getValue());
            }
//            for (Entry<Object, List<Node>> e : memoryMap.locationToReads.entrySet()) {
//                locationToReads.put(e.getKey(), new ArrayList<Node>(e.getValue()));
//            }
            lastReadWriteMerge = memoryMap.lastReadWriteMerge;
            lastWriteMerge = memoryMap.lastWriteMerge;
        }

        public MemoryMap(Block b) {
            block = b;
            locationToWrite = new HashMap<Object, Node>();
            locationToReads = new HashMap<Object, List<Node>>();
            if (GraalOptions.TraceMemoryMaps) {
                TTY.println("Creating new memory map for block B" + b.blockID());
            }
            StartNode startNode = b.firstNode().graph().start();
            if (b.firstNode() == startNode) {
                WriteMemoryCheckpointNode checkpoint = new WriteMemoryCheckpointNode(startNode.graph());
                checkpoint.setNext((FixedNode) startNode.start());
                startNode.setStart(checkpoint);
                lastReadWriteMerge = checkpoint;
                lastWriteMerge = checkpoint;
            }
        }

        public void mergeWith(MemoryMap memoryMap) {
            if (GraalOptions.TraceMemoryMaps) {
                TTY.println("Merging with memory map of block B" + memoryMap.block.blockID());
            }

            lastReadWriteMerge = mergeNodes(lastReadWriteMerge, memoryMap.lastReadWriteMerge);
            lastWriteMerge = mergeNodes(lastWriteMerge, memoryMap.lastWriteMerge);

            List<Object> toRemove = new ArrayList<Object>();
            for (Entry<Object, Node> e : locationToWrite.entrySet()) {
                if (memoryMap.locationToWrite.containsKey(e.getKey())) {
                    if (GraalOptions.TraceMemoryMaps) {
                        TTY.println("Merging entries for location " + e.getKey());
                    }
                    locationToWrite.put(e.getKey(), mergeNodes(e.getValue(), memoryMap.locationToWrite.get(e.getKey())));
                } else {
                    toRemove.add(e.getKey());
                }
            }

            for (Object o : toRemove) {
                locationToWrite.remove(o);
            }

//            for (Entry<Object, List<Node>> e : memoryMap.locationToReads.entrySet()) {
//                for (Node n : e.getValue()) {
//                    addRead(n, e.getKey());
//                }
//            }

            mergeOperations++;
        }

        private Node mergeNodes(Node original, Node newValue) {
            if (original == newValue) {
                // Nothing to merge.
                if (GraalOptions.TraceMemoryMaps) {
                    TTY.println("Nothing to merge both nodes are " + original.id());
                }
                return original;
            }
            Merge m = (Merge) block.firstNode();
            if (original instanceof Phi && ((Phi) original).merge() == m) {
                ((Phi) original).addInput(newValue);
                return original;
            } else {
                Phi phi = new Phi(CiKind.Illegal, m, m.graph());
                phi.makeDead(); // Phi does not produce a value, it is only a memory phi.
                for (int i = 0; i < mergeOperations + 1; ++i) {
                    phi.addInput(original);
                }
                phi.addInput(newValue);
                return phi;
            }
        }

        public void createWriteMemoryMerge(AbstractMemoryCheckpointNode memMerge) {
            if (GraalOptions.TraceMemoryMaps) {
                TTY.println("Creating write memory checkpoint at node " + memMerge.id());
            }

            // Merge in all writes.
            for (Entry<Object, Node> writeEntry : locationToWrite.entrySet()) {
                memMerge.mergedNodes().add(writeEntry.getValue());

                // Register the merge point as a read such that subsequent writes to this location will depend on it (but subsequent reads do not).
//                addRead(memMerge, writeEntry.getKey());
            }
            lastWriteMerge = memMerge;
        }

        public void createReadWriteMemoryCheckpoint(AbstractMemoryCheckpointNode memMerge) {
            if (GraalOptions.TraceMemoryMaps) {
                TTY.println("Creating readwrite memory checkpoint at node " + memMerge.id());
            }

            // Merge in all writes.
            for (Entry<Object, Node> writeEntry : locationToWrite.entrySet()) {
                memMerge.mergedNodes().add(writeEntry.getValue());
            }
            locationToWrite.clear();

            // Merge in all reads.
//            for (Entry<Object, List<Node>> readEntry : locationToReads.entrySet()) {
//                memMerge.mergedNodes().addAll(readEntry.getValue());
//            }
            locationToReads.clear();
            lastWriteMerge = memMerge;
            lastReadWriteMerge = memMerge;
        }

        public void registerWrite(WriteNode node) {
            Object location = node.location().locationIdentity();
            if (GraalOptions.TraceMemoryMaps) {
                TTY.println("Register write to " + location + " at node " + node.id());
            }

            boolean connectionAdded = false;
//            if (locationToReads.containsKey(location)) {
//                for (Node prevRead : locationToReads.get(location)) {
//                    node.inputs().variablePart().add(prevRead);
//                    connectionAdded = true;
//                }
//            }

            if (!connectionAdded) {
                if (locationToWrite.containsKey(location)) {
                    Node prevWrite = locationToWrite.get(location);
                    node.inputs().variablePart().add(prevWrite);
                    connectionAdded = true;
                }
            }

            node.inputs().variablePart().add(lastWriteMerge);

            locationToWrite.put(location, node);
            locationToReads.remove(location);
        }

        public void registerRead(ReadNode node) {
            Object location = node.location().locationIdentity();
            if (GraalOptions.TraceMemoryMaps) {
                TTY.println("Register read to " + location + " at node " + node.id());
            }

            boolean connectionAdded = false;
            if (locationToWrite.containsKey(location)) {
                Node prevWrite = locationToWrite.get(location);
                node.inputs().variablePart().add(prevWrite);
                connectionAdded = true;
            }

            if (!connectionAdded) {
                node.inputs().variablePart().add(lastReadWriteMerge);
            }

            //addRead(node, location);
        }

        private void addRead(Node node, Object location) {
            if (!locationToReads.containsKey(location)) {
                locationToReads.put(location, new ArrayList<Node>());
            }
            locationToReads.get(location).add(node);
        }
    }

    @Override
    protected void run(final Graph graph) {
        final IdentifyBlocksPhase s = new IdentifyBlocksPhase(false);
        s.apply(graph);

        List<Block> blocks = s.getBlocks();
        MemoryMap[] memoryMaps = new MemoryMap[blocks.size()];
        for (final Block b : blocks) {
            process(b, memoryMaps);
        }
    }

    private void process(final Block b, MemoryMap[] memoryMaps) {
        // Visit every block at most once.
        if (memoryMaps[b.blockID()] != null) {
            return;
        }

        // Process predecessors before this block.
        for (Block pred : b.getPredecessors()) {
            process(pred, memoryMaps);
        }

        // Create initial memory map for the block.
        MemoryMap map = null;
        if (b.getPredecessors().size() == 0) {
            map = new MemoryMap(b);
        } else {
            map = new MemoryMap(b, memoryMaps[b.getPredecessors().get(0).blockID()]);
            for (int i = 1; i < b.getPredecessors().size(); ++i) {
                map.mergeWith(memoryMaps[b.getPredecessors().get(i).blockID()]);
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
    }
}
