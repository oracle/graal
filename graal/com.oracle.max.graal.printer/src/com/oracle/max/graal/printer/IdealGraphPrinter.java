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
package com.oracle.max.graal.printer;

import java.io.*;
import java.util.*;
import java.util.Map.Entry;

import com.oracle.max.cri.ri.*;
import com.oracle.max.graal.compiler.schedule.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.graph.Node.Verbosity;
import com.oracle.max.graal.graph.NodeClass.NodeClassIterator;
import com.oracle.max.graal.graph.NodeClass.Position;
import com.oracle.max.graal.java.*;
import com.oracle.max.graal.nodes.*;

/**
 * Generates a representation of {@link Graph Graphs} that can be visualized and inspected with the <a
 * href="http://kenai.com/projects/igv">Ideal Graph Visualizer</a>.
 */
class IdealGraphPrinter extends BasicIdealGraphPrinter {
    /**
     * Creates a new {@link IdealGraphPrinter} that writes to the specified output stream.
     */
    public IdealGraphPrinter(OutputStream stream) {
        super(stream);
    }

    /**
     * Starts a new group of graphs with the given name, short name and method byte code index (BCI) as properties.
     */
    public void beginGroup(String name, String shortName, RiResolvedMethod method, int bci) {
        beginGroup();
        beginProperties();
        printProperty("name", name);
        endProperties();
        beginMethod(name, shortName, bci);
        if (method != null) {
            beginBytecodes();
            BytecodeStream bytecodes = new BytecodeStream(method.code());
            while (bytecodes.currentBC() != Bytecodes.END) {
                int startBCI = bytecodes.currentBCI();
                String mnemonic = Bytecodes.nameOf(bytecodes.currentBC());
                int[] extra = null;
                if (bytecodes.nextBCI() > startBCI + 1) {
                    extra = new int[bytecodes.nextBCI() - (startBCI + 1)];
                    for (int i = 0; i < extra.length; i++) {
                        extra[i] = bytecodes.readUByte(startBCI + 1 + i);
                    }
                }
                printBytecode(startBCI, mnemonic, extra);
                bytecodes.next();
            }
            endBytecodes();
        }
        endMethod();
    }

    public void print(Graph graph, String title) {
        print(graph, title, null);
    }

    /**
     * Prints an entire {@link Graph} with the specified title, optionally using short names for nodes.
     */
    public void print(Graph graph, String title, IdentifyBlocksPhase predefinedSchedule) {
        beginGraph(title);
        Set<Node> noBlockNodes = new HashSet<>();
        IdentifyBlocksPhase schedule = predefinedSchedule;
        if (schedule == null) {
            try {
                schedule = new IdentifyBlocksPhase(true);
                schedule.apply((StructuredGraph) graph);
            } catch (Throwable t) {
                // nothing to do here...
            }
        }

        beginNodes();
        List<Edge> edges = printNodes(graph, schedule == null ? null : schedule.getNodeToBlock(), noBlockNodes);
        endNodes();

        beginEdges();
        for (Edge edge : edges) {
            printEdge(edge);
        }
        endEdges();

        if (schedule != null) {
            beginControlFlow();
            for (Block block : schedule.getBlocks()) {
                printBlock(graph, block, schedule.getNodeToBlock());
            }
            printNoBlock(noBlockNodes);
            endControlFlow();
        }

        endGraph();
        flush();
    }

    private List<Edge> printNodes(Graph graph, NodeMap<Block> nodeToBlock, Set<Node> noBlockNodes) {
        ArrayList<Edge> edges = new ArrayList<>();

        NodeMap<Set<Entry<String, Integer>>> colors = graph.createNodeMap();
        NodeMap<Set<Entry<String, String>>> colorsToString = graph.createNodeMap();
        NodeMap<Set<String>> bits = graph.createNodeMap();

        for (Node node : graph.getNodes()) {

            beginNode(node.toString(Verbosity.Id));
            beginProperties();
            printProperty("idx", node.toString(Verbosity.Id));

            Map<Object, Object> props = node.getDebugProperties();
            if (!props.containsKey("name") || props.get("name").toString().trim().length() == 0) {
                String name = node.toString(Verbosity.Name);
                printProperty("name", name);
            }
            printProperty("class", node.getClass().getSimpleName());
            Block block = nodeToBlock == null ? null : nodeToBlock.get(node);
            if (block != null) {
                printProperty("block", Integer.toString(block.blockID()));
                if (!(node instanceof PhiNode || node instanceof FrameState || node instanceof LocalNode) && !block.getInstructions().contains(node)) {
                    printProperty("notInOwnBlock", "true");
                }
            } else {
                printProperty("block", "noBlock");
                noBlockNodes.add(node);
            }

            Set<Entry<String, Integer>> nodeColors = colors.get(node);
            if (nodeColors != null) {
                for (Entry<String, Integer> color : nodeColors) {
                    String name = color.getKey();
                    Integer value = color.getValue();
                    printProperty(name, Integer.toString(value));
                }
            }
            Set<Entry<String, String>> nodeColorStrings = colorsToString.get(node);
            if (nodeColorStrings != null) {
                for (Entry<String, String> color : nodeColorStrings) {
                    String name = color.getKey();
                    String value = color.getValue();
                    printProperty(name, value);
                }
            }
            Set<String> nodeBits = bits.get(node);
            if (nodeBits != null) {
                for (String bit : nodeBits) {
                    printProperty(bit, "true");
                }
            }

            for (Entry<Object, Object> entry : props.entrySet()) {
                String key = entry.getKey().toString();
                String value = entry.getValue() == null ? "null" : entry.getValue().toString();
                printProperty(key, value);
            }

            endProperties();
            endNode();

            // successors
            int fromIndex = 0;
            NodeClassIterator succIter = node.successors().iterator();
            while (succIter.hasNext()) {
                Position position = succIter.nextPosition();
                Node successor = node.getNodeClass().get(node, position);
                if (successor != null) {
                    edges.add(new Edge(node.toString(Verbosity.Id), fromIndex, successor.toString(Verbosity.Id), 0, node.getNodeClass().getName(position)));
                }
                fromIndex++;
            }

            // inputs
            int toIndex = 1;
            NodeClassIterator inputIter = node.inputs().iterator();
            while (inputIter.hasNext()) {
                Position position = inputIter.nextPosition();
                Node input = node.getNodeClass().get(node, position);
                if (input != null) {
                    edges.add(new Edge(input.toString(Verbosity.Id), input.successors().explicitCount(), node.toString(Verbosity.Id), toIndex, node.getNodeClass().getName(position)));
                }
                toIndex++;
            }
        }

        return edges;
    }

    private void printBlock(Graph graph, Block block, NodeMap<Block> nodeToBlock) {
        beginBlock(Integer.toString(block.blockID()));
        beginSuccessors();
        for (Block sux : block.getSuccessors()) {
            if (sux != null) {
                printSuccessor(Integer.toString(sux.blockID()));
            }
        }
        endSuccessors();
        beginBlockNodes();

        Set<Node> nodes = new HashSet<>(block.getInstructions());

        if (nodeToBlock != null) {
            for (Node n : graph.getNodes()) {
                Block blk = nodeToBlock.get(n);
                if (blk == block) {
                    nodes.add(n);
                }
            }
        }

        if (nodes.size() > 0) {
            // if this is the first block: add all locals to this block
            if (block.getInstructions().size() > 0 && block.getInstructions().get(0) == ((StructuredGraph) graph).start()) {
                for (Node node : graph.getNodes()) {
                    if (node instanceof LocalNode) {
                        nodes.add(node);
                    }
                }
            }

            // add all framestates and phis to their blocks
            for (Node node : block.getInstructions()) {
                if (node instanceof StateSplit && ((StateSplit) node).stateAfter() != null) {
                    nodes.add(((StateSplit) node).stateAfter());
                }
                if (node instanceof MergeNode) {
                    for (PhiNode phi : ((MergeNode) node).phis()) {
                        nodes.add(phi);
                    }
                }
            }

            for (Node node : nodes) {
                printBlockNode(node.toString(Verbosity.Id));
            }
        }
        endBlockNodes();
        endBlock();
    }

    private void printNoBlock(Set<Node> noBlockNodes) {
        if (!noBlockNodes.isEmpty()) {
            beginBlock("noBlock");
            beginBlockNodes();
            for (Node node : noBlockNodes) {
                printBlockNode(node.toString(Verbosity.Id));
            }
            endBlockNodes();
            endBlock();
        }
    }
}
