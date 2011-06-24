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
import com.sun.cri.ri.*;

public class LoweringPhase extends Phase {

    public static final LoweringOp DELEGATE_TO_RUNTIME = new LoweringOp() {
        @Override
        public void lower(Node n, CiLoweringTool tool) {
            tool.getRuntime().lower(n, tool);
        }
    };

    private final RiRuntime runtime;
    private Graph currentGraph;

    public LoweringPhase(RiRuntime runtime) {
        this.runtime = runtime;
    }

    public static class MemoryMap {

        private final Block block;
        private HashMap<Object, Node> locationToWrite;
        private HashMap<Object, List<Node>> locationToReads;

        public MemoryMap(Block b, MemoryMap memoryMap) {
            this(b);
        }

        public MemoryMap(Block b) {
            block = b;
            locationToWrite = new HashMap<Object, Node>();
            locationToReads = new HashMap<Object, List<Node>>();
            if (GraalOptions.TraceMemoryMaps) {
                TTY.println("Creating new memory map for block B" + b.blockID());
            }
        }

        public void mergeWith(MemoryMap memoryMap) {
            if (GraalOptions.TraceMemoryMaps) {
                TTY.println("Merging with memory map of block B" + memoryMap.block.blockID());
            }
        }

        public void createMemoryMerge(AbstractMemoryMergeNode memMerge) {
            if (GraalOptions.TraceMemoryMaps) {
                TTY.println("Creating memory merge at node " + memMerge.id());
            }

            for (Entry<Object, Node> writeEntry : locationToWrite.entrySet()) {
                memMerge.mergedNodes().add(writeEntry.getValue());
            }

            TTY.println("entrySet size" + locationToReads.entrySet());
            for (Entry<Object, List<Node>> readEntry : locationToReads.entrySet()) {
                TTY.println(readEntry.getValue().toString());
                memMerge.mergedNodes().addAll(readEntry.getValue());
            }

            locationToReads.clear();
            locationToWrite.clear();
        }

        public void registerWrite(Object location, Node node) {
            if (GraalOptions.TraceMemoryMaps) {
                TTY.println("Register write to " + location + " at node " + node.id());
            }

            if (locationToWrite.containsKey(location)) {
                Node prevWrite = locationToWrite.get(location);
                node.inputs().add(prevWrite);
            }

            if (locationToReads.containsKey(location)) {
                for (Node prevRead : locationToReads.get(location)) {
                    node.inputs().add(prevRead);
                }
            }
            locationToWrite.put(location, node);
            locationToReads.remove(location);
        }

        public void registerRead(Object location, Node node) {
            if (GraalOptions.TraceMemoryMaps) {
                TTY.println("Register read to " + location + " at node " + node.id());
            }

            if (locationToWrite.containsKey(location)) {
                Node prevWrite = locationToWrite.get(location);
                node.inputs().add(prevWrite);
            }

            if (!locationToReads.containsKey(location)) {
                locationToReads.put(location, new ArrayList<Node>());
            }
            locationToReads.get(location).add(node);
            TTY.println("entrySet size" + locationToReads.entrySet());
        }

        public Node getMemoryState(Object location) {
            return null;
        }
    }

    @Override
    protected void run(final Graph graph) {
        this.currentGraph = graph;
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
            for (int i=1; i<b.getPredecessors().size(); ++i) {
                map.mergeWith(memoryMaps[b.getPredecessors().get(0).blockID()]);
            }
        }
        final MemoryMap finalMap = map;
        memoryMaps[b.blockID()] = finalMap;

        final CiLoweringTool loweringTool = new CiLoweringTool() {

            @Override
            public Node getGuardAnchor() {
                return b.createAnchor();
            }

            @Override
            public RiRuntime getRuntime() {
                return runtime;
            }

            @Override
            public Node createGuard(Node condition) {
                Anchor anchor = (Anchor) getGuardAnchor();
                for (GuardNode guard : anchor.happensAfterGuards()) {
                    if (guard.node().valueEqual(condition)) {
                        condition.delete();
                        return guard;
                    }
                }
                GuardNode newGuard = new GuardNode(currentGraph);
                newGuard.setAnchor(anchor);
                newGuard.setNode((BooleanNode) condition);
                return newGuard;
            }

            @Override
            public void createMemoryMerge(Node node) {
                assert node instanceof AbstractMemoryMergeNode;
                AbstractMemoryMergeNode memMerge = (AbstractMemoryMergeNode) node;
                assert memMerge.mergedNodes().size() == 0;
                finalMap.createMemoryMerge(memMerge);
            }

            @Override
            public void registerRead(Object location, Node node) {
                finalMap.registerRead(location, node);
            }

            @Override
            public void registerWrite(Object location, Node node) {
                finalMap.registerWrite(location, node);
            }

            @Override
            public Node getMemoryState(Object location) {
                return finalMap.getMemoryState(location);
            }
        };

        // Lower the instructions of this block.
        for (final Node n : b.getInstructions()) {
            if (n instanceof FixedNode) {
                LoweringOp op = n.lookup(LoweringOp.class);
                if (op != null) {
                    op.lower(n, loweringTool);
                } else {
                    // This memory merge node is not lowered => create a memory merge nevertheless.
                    if (n instanceof AbstractMemoryMergeNode) {
                        loweringTool.createMemoryMerge(n);
                    }
                }
            }
        }
    }

    public interface LoweringOp extends Op {
        void lower(Node n, CiLoweringTool tool);
    }
}
