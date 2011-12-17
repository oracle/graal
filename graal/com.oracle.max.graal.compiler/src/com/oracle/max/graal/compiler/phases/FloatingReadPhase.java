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

import com.oracle.max.criutils.*;
import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.loop.*;
import com.oracle.max.graal.compiler.schedule.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.PhiNode.*;
import com.oracle.max.graal.nodes.extended.*;
import com.sun.cri.ci.*;

public class FloatingReadPhase extends Phase {

    private static class MemoryMap {
        private Block block;
        private IdentityHashMap<Object, Node> map;
        private IdentityHashMap<Object, Node> loopEntryMap;
        private int mergeOperationCount;

        public MemoryMap(Block block) {
            this.block = block;
            map = new IdentityHashMap<Object, Node>();
        }

        public MemoryMap(Block block, MemoryMap other) {
            this(block);
            map.putAll(other.map);
        }

        public void mergeLoopEntryWith(MemoryMap otherMemoryMap, LoopBeginNode begin) {
            for (Object keyInOther : otherMemoryMap.map.keySet()) {
                assert loopEntryMap.containsKey(keyInOther) || map.get(keyInOther) == otherMemoryMap.map.get(keyInOther) : keyInOther + ", " + map.get(keyInOther) + " vs " + otherMemoryMap.map.get(keyInOther) + " " + begin;
            }

            for (Map.Entry<Object, Node> entry : loopEntryMap.entrySet()) {
                PhiNode phiNode = (PhiNode) entry.getValue();
                assert phiNode.valueCount() == 1;

                Node other;
                Object key = entry.getKey();
                if (otherMemoryMap.map.containsKey(key)) {
                    other = otherMemoryMap.map.get(key);
                } else {
                    other = otherMemoryMap.map.get(LocationNode.ANY_LOCATION);
                }

                phiNode.addInput((ValueNode) other);
            }
        }

        public void mergeWith(MemoryMap otherMemoryMap, Block b) {
            if (GraalOptions.TraceMemoryMaps) {
                TTY.println("merging block " + otherMemoryMap.block + " into block " + block);
            }
            IdentityHashMap<Object, Node> otherMap = otherMemoryMap.map;

            for (Map.Entry<Object, Node> entry : map.entrySet()) {
                if (otherMap.containsKey(entry.getKey())) {
                    mergeNodes(entry.getKey(), entry.getValue(), otherMap.get(entry.getKey()), b);
                } else {
                    mergeNodes(entry.getKey(), entry.getValue(), otherMap.get(LocationNode.ANY_LOCATION), b);
                }
            }

            Node anyLocationNode = map.get(LocationNode.ANY_LOCATION);
            for (Map.Entry<Object, Node> entry : otherMap.entrySet()) {
                if (!map.containsKey(entry.getKey())) {
                    Node current = anyLocationNode;
                    if (anyLocationNode instanceof PhiNode) {
                        PhiNode phiNode = (PhiNode) anyLocationNode;
                        if (phiNode.merge() == block.firstNode()) {
                            PhiNode phiCopy = (PhiNode) phiNode.copyWithInputs();
                            phiCopy.removeInput(phiCopy.valueCount() - 1);
                            current = phiCopy;
                            map.put(entry.getKey(), current);
                        }
                    }
                    mergeNodes(entry.getKey(), current, entry.getValue(), b);
                }
            }

            mergeOperationCount++;
        }

        private void mergeNodes(Object location, Node original, Node newValue, Block block) {
            if (original == newValue) {
                // Nothing to merge.
                if (GraalOptions.TraceMemoryMaps) {
                    TTY.println("Nothing to merge both nodes are " + original);
                }
                return;
            }
            MergeNode m = (MergeNode) block.firstNode();
            if (m.isPhiAtMerge(original)) {
                PhiNode phi = (PhiNode) original;
                phi.addInput((ValueNode) newValue);
                if (GraalOptions.TraceMemoryMaps) {
                    TTY.println("Add new input to " + original + ": " + newValue);
                }
                assert phi.valueCount() <= phi.merge().endCount() : phi.merge();
            } else {
                assert m != null;
                PhiNode phi = m.graph().unique(new PhiNode(CiKind.Illegal, m, PhiType.Memory));
                for (int i = 0; i < mergeOperationCount + 1; ++i) {
                    phi.addInput((ValueNode) original);
                }
                phi.addInput((ValueNode) newValue);
                if (GraalOptions.TraceMemoryMaps) {
                    TTY.println("Creating new " + phi + " merge=" + phi.merge() + ", mergeOperationCount=" + mergeOperationCount + ", newValue=" + newValue + ", location=" + location);
                }
                assert phi.valueCount() <= phi.merge().endCount() + ((phi.merge() instanceof LoopBeginNode) ? 1 : 0) : phi.merge() + "/" + phi.valueCount() + "/" + phi.merge().endCount() + "/" + mergeOperationCount;
                assert m.usages().contains(phi);
                assert phi.merge().usages().contains(phi);
                for (Node input : phi.inputs()) {
                    assert input.usages().contains(phi);
                }
                map.put(location, phi);
            }
        }

        public void processCheckpoint(MemoryCheckpoint checkpoint) {
            map.clear();
            map.put(LocationNode.ANY_LOCATION, (Node) checkpoint);
        }

        public void processWrite(WriteNode writeNode) {
            map.put(writeNode.location().locationIdentity(), writeNode);
        }

        public void processRead(ReadNode readNode) {
            FixedNode next = readNode.next();
            readNode.setNext(null);
            readNode.replaceAtPredecessors(next);

            if (GraalOptions.TraceMemoryMaps) {
                TTY.println("Register read to node " + readNode);
            }

            if (readNode.location().locationIdentity() != LocationNode.FINAL_LOCATION) {
                // Create dependency on previous node that creates the memory state for this location.
                readNode.addDependency(getLocationForRead(readNode));
            }
        }

        private Node getLocationForRead(ReadNode readNode) {
            Object locationIdentity = readNode.location().locationIdentity();
            Node result = map.get(locationIdentity);
            if (result == null) {
                result = map.get(LocationNode.ANY_LOCATION);
            }
            return result;
        }

        public void createLoopEntryMemoryMap(Set<Object> modifiedLocations, Loop loop) {

            loopEntryMap = new IdentityHashMap<Object, Node>();

            for (Object modifiedLocation : modifiedLocations) {
                Node other;
                if (map.containsKey(modifiedLocation)) {
                    other = map.get(modifiedLocation);
                } else {
                    other = map.get(LocationNode.ANY_LOCATION);
                }
                createLoopEntryPhi(modifiedLocation, other, loop, loopEntryMap);
            }

            if (modifiedLocations.contains(LocationNode.ANY_LOCATION)) {
                for (Map.Entry<Object, Node> entry : map.entrySet()) {
                    if (!modifiedLocations.contains(entry.getKey())) {
                        createLoopEntryPhi(entry.getKey(), entry.getValue(), loop, loopEntryMap);
                    }
                }
            }
        }

        private void createLoopEntryPhi(Object modifiedLocation, Node other, Loop loop, IdentityHashMap<Object, Node> loopEntryMap) {
            PhiNode phi = other.graph().unique(new PhiNode(CiKind.Illegal, loop.loopBegin(), PhiType.Memory));
            phi.addInput((ValueNode) other);
            map.put(modifiedLocation, phi);
            loopEntryMap.put(modifiedLocation, phi);
        }


        public IdentityHashMap<Object, Node> getLoopEntryMap() {
            return loopEntryMap;
        }
    }

    @Override
    protected void run(StructuredGraph graph) {

        // Add start node write checkpoint.
        addStartCheckpoint(graph);

        LoopInfo loopInfo = LoopUtil.computeLoopInfo(graph);

        HashMap<Loop, Set<Object>> modifiedValues = new HashMap<Loop, Set<Object>>();
        // Initialize modified values to empty hash set.
        for (Loop loop : loopInfo.loops()) {
            modifiedValues.put(loop, new HashSet<Object>());
        }

        // Get modified values in loops.
        for (Node n : graph.getNodes()) {
            Loop loop = loopInfo.loop(n);
            if (loop != null) {
                if (n instanceof WriteNode) {
                    WriteNode writeNode = (WriteNode) n;
                    traceWrite(loop, writeNode.location().locationIdentity(), modifiedValues);
                } else if (n instanceof MemoryCheckpoint) {
                    traceMemoryCheckpoint(loop, modifiedValues);
                }
            }
        }

        // Propagate values to parent loops.
        for (Loop loop : loopInfo.rootLoops()) {
            propagateFromChildren(loop, modifiedValues);
        }

        if (GraalOptions.TraceMemoryMaps) {
            print(graph, loopInfo, modifiedValues);
        }

        // Identify blocks.
        final IdentifyBlocksPhase s = new IdentifyBlocksPhase(false);
        s.apply(graph, context);
        List<Block> blocks = s.getBlocks();

        // Process blocks (predecessors first).
        MemoryMap[] memoryMaps = new MemoryMap[blocks.size()];
        for (final Block b : blocks) {
            processBlock(b, memoryMaps, s.getNodeToBlock(), modifiedValues, loopInfo);
        }
    }

    private void addStartCheckpoint(StructuredGraph graph) {
        BeginNode entryPoint = graph.start();
        FixedNode next = entryPoint.next();
        if (!(next instanceof MemoryCheckpoint)) {
            WriteMemoryCheckpointNode checkpoint = graph.add(new WriteMemoryCheckpointNode());
            entryPoint.setNext(checkpoint);
            checkpoint.setNext(next);
        }
    }

    private void processBlock(Block b, MemoryMap[] memoryMaps, NodeMap<Block> nodeToBlock, HashMap<Loop, Set<Object>> modifiedValues, LoopInfo loopInfo) {

        // Visit every block at most once.
        if (memoryMaps[b.blockID()] != null) {
            return;
        }

        // Process predecessors before this block.
        for (Block pred : b.getPredecessors()) {
            processBlock(pred, memoryMaps, nodeToBlock, modifiedValues, loopInfo);
        }


        // Create initial memory map for the block.
        MemoryMap map = null;
        if (b.getPredecessors().size() == 0) {
            map = new MemoryMap(b);
        } else {
            map = new MemoryMap(b, memoryMaps[b.getPredecessors().get(0).blockID()]);
            if (b.isLoopHeader()) {
                assert b.getPredecessors().size() == 1;
                Loop loop = loopInfo.loop(b.firstNode());
                map.createLoopEntryMemoryMap(modifiedValues.get(loop), loop);
            } else {
                for (int i = 1; i < b.getPredecessors().size(); ++i) {
                    assert b.firstNode() instanceof MergeNode : b.firstNode();
                    Block block = b.getPredecessors().get(i);
                    map.mergeWith(memoryMaps[block.blockID()], b);
                }
            }
        }
        memoryMaps[b.blockID()] = map;

        // Process instructions of this block.
        for (Node n : b.getInstructions()) {
            if (n instanceof ReadNode) {
                ReadNode readNode = (ReadNode) n;
                map.processRead(readNode);
            } else if (n instanceof WriteNode) {
                WriteNode writeNode = (WriteNode) n;
                map.processWrite(writeNode);
            } else if (n instanceof MemoryCheckpoint) {
                MemoryCheckpoint checkpoint = (MemoryCheckpoint) n;
                map.processCheckpoint(checkpoint);
            }
        }


        if (b.lastNode() instanceof LoopEndNode) {
            LoopEndNode end = (LoopEndNode) b.lastNode();
            LoopBeginNode begin = end.loopBegin();
            Block beginBlock = nodeToBlock.get(begin);
            MemoryMap memoryMap = memoryMaps[beginBlock.blockID()];
            assert memoryMap != null : beginBlock.name();
            assert memoryMap.getLoopEntryMap() != null;
            memoryMap.mergeLoopEntryWith(map, begin);
        }
    }

    private void traceMemoryCheckpoint(Loop loop, HashMap<Loop, Set<Object>> modifiedValues) {
        modifiedValues.get(loop).add(LocationNode.ANY_LOCATION);
    }

    private void propagateFromChildren(Loop loop, HashMap<Loop, Set<Object>> modifiedValues) {
        for (Loop child : loop.children()) {
            propagateFromChildren(child, modifiedValues);
            modifiedValues.get(loop).addAll(modifiedValues.get(child));
        }
    }

    private void traceWrite(Loop loop, Object locationIdentity, HashMap<Loop, Set<Object>> modifiedValues) {
        modifiedValues.get(loop).add(locationIdentity);
    }

    private void print(StructuredGraph graph, LoopInfo loopInfo, HashMap<Loop, Set<Object>> modifiedValues) {
        TTY.println();
        TTY.println("Loops:");
        for (Loop loop : loopInfo.loops()) {
            TTY.print(loop + " modified values: " + modifiedValues.get(loop));
            TTY.println();
        }
    }

    private void mark(LoopBeginNode begin, LoopBeginNode outer, NodeMap<LoopBeginNode> nodeToLoop) {

        if (nodeToLoop.get(begin) != null) {
            // Loop already processed.
            return;
        }
        nodeToLoop.set(begin, begin);

        NodeFlood workCFG = begin.graph().createNodeFlood();
        workCFG.add(begin.loopEnd());
        for (Node n : workCFG) {
            if (n == begin) {
                // Stop at loop begin.
                continue;
            }
            markNode(n, begin, outer, nodeToLoop);

            for (Node pred : n.cfgPredecessors()) {
                workCFG.add(pred);
            }
        }
    }

    private void markNode(Node n, LoopBeginNode begin, LoopBeginNode outer, NodeMap<LoopBeginNode> nodeToLoop) {
        LoopBeginNode oldMark = nodeToLoop.get(n);
        if (oldMark == null || oldMark == outer) {

            // We have an inner loop, start marking it.
            if (n instanceof LoopBeginNode) {
                mark((LoopBeginNode) n, begin, nodeToLoop);
            }

            nodeToLoop.set(n, begin);
        }
    }
}
