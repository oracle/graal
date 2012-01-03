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
import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.graphbuilder.*;
import com.oracle.max.graal.compiler.schedule.*;
import com.oracle.max.graal.compiler.util.*;
import com.oracle.max.graal.compiler.util.LoopUtil.Loop;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.graph.Node.Verbosity;
import com.oracle.max.graal.graph.NodeClass.NodeClassIterator;
import com.oracle.max.graal.graph.NodeClass.Position;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.loop.*;
import com.oracle.max.graal.printer.BasicIdealGraphPrinter.*;

/**
 * Generates a representation of {@link Graph Graphs} that can be visualized and inspected with the <a
 * href="http://kenai.com/projects/igv">Ideal Graph Visualizer</a>.
 */
class IdealGraphPrinter {

    private final BasicIdealGraphPrinter printer;
    private final HashSet<Class<?>> omittedClasses = new HashSet<>();
    private final Set<Node> noBlockNodes = new HashSet<>();

    /**
     * Creates a new {@link IdealGraphPrinter} that writes to the specified output stream.
     */
    public IdealGraphPrinter(OutputStream stream) {
        this.printer = new BasicIdealGraphPrinter(stream);
    }

    /**
     * Adds a node class that is omitted in the output.
     */
    public void addOmittedClass(Class<?> clazz) {
        omittedClasses.add(clazz);
    }

    /**
     * Flushes any buffered output.
     */
    public void flush() {
        printer.flush();
    }

    /**
     * Starts a new graph document.
     */
    public void begin() {
        printer.begin();
    }

    /**
     * Starts a new group of graphs with the given name, short name and method byte code index (BCI) as properties.
     */
    public void beginGroup(String name, String shortName, RiResolvedMethod method, int bci, String origin) {
        printer.beginGroup();
        printer.beginProperties();
        printer.printProperty("name", name);
        printer.printProperty("origin", origin);
        printer.endProperties();
        printer.beginMethod(name, shortName, bci);
        if (GraalOptions.PrintIdealGraphBytecodes && method != null) {
            printer.beginBytecodes();
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
                printer.printBytecode(startBCI, mnemonic, extra);
                bytecodes.next();
            }
            printer.endBytecodes();
        }
        printer.endMethod();
    }

    /**
     * Ends the current group.
     */
    public void endGroup() {
        printer.endGroup();
    }

    /**
     * Finishes the graph document and flushes the output stream.
     */
    public void end() {
        printer.end();
    }

    public void print(Graph graph, String title, boolean shortNames) {
        print(graph, title, shortNames, null);
    }

    /**
     * Prints an entire {@link Graph} with the specified title, optionally using short names for nodes.
     */
    public void print(Graph graph, String title, boolean shortNames, IdentifyBlocksPhase predefinedSchedule) {
        printer.beginGraph(title);
        noBlockNodes.clear();
        IdentifyBlocksPhase schedule = predefinedSchedule;
        if (schedule == null) {
            try {
                schedule = new IdentifyBlocksPhase(true);
                schedule.apply((StructuredGraph) graph, false);
            } catch (Throwable t) {
                // nothing to do here...
            }
        }
        List<Loop> loops = null;
        try {
            loops = LoopUtil.computeLoops((StructuredGraph) graph);
            // loop.nodes() does some more calculations which may fail, so execute this here as well (result is cached)
            if (loops != null) {
                for (Loop loop : loops) {
                    loop.nodes();
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
            loops = null;
        }

        printer.beginNodes();
        List<Edge> edges = printNodes(graph, shortNames, schedule == null ? null : schedule.getNodeToBlock(), loops);
        printer.endNodes();

        printer.beginEdges();
        for (Edge edge : edges) {
            printer.printEdge(edge);
        }
        printer.endEdges();

        if (schedule != null) {
            printer.beginControlFlow();
            for (Block block : schedule.getBlocks()) {
                printBlock(graph, block, schedule.getNodeToBlock());
            }
            printNoBlock();
            printer.endControlFlow();
        }

        printer.endGraph();
        flush();
    }

    private List<Edge> printNodes(Graph graph, boolean shortNames, NodeMap<Block> nodeToBlock, List<Loop> loops) {
        ArrayList<Edge> edges = new ArrayList<>();
        NodeBitMap loopExits = graph.createNodeBitMap();
        if (loops != null) {
            for (Loop loop : loops) {
                loopExits.setUnion(loop.exits());
            }
        }

        NodeMap<Set<Entry<String, Integer>>> colors = graph.createNodeMap();
        NodeMap<Set<Entry<String, String>>> colorsToString = graph.createNodeMap();
        NodeMap<Set<String>> bits = graph.createNodeMap();
// TODO This code was never reachable, since there was no code putting a NodeMap or NodeBitMap into the debugObjects.
// If you need to reactivate this code, put the mapping from names to values into a helper object and register it in the new debugObjects array.
//
//        if (debugObjects != null) {
//            for (Entry<String, Object> entry : debugObjects.entrySet()) {
//                String name = entry.getKey();
//                Object obj = entry.getValue();
//                if (obj instanceof NodeMap) {
//                    Map<Object, Integer> colorNumbers = new HashMap<Object, Integer>();
//                    int nextColor = 0;
//                    NodeMap<?> map = (NodeMap<?>) obj;
//                    for (Entry<Node, ?> mapEntry : map.entries()) {
//                        Node node = mapEntry.getKey();
//                        Object color = mapEntry.getValue();
//                        Integer colorNumber = colorNumbers.get(color);
//                        if (colorNumber == null) {
//                            colorNumber = nextColor++;
//                            colorNumbers.put(color, colorNumber);
//                        }
//                        Set<Entry<String, Integer>> nodeColors = colors.get(node);
//                        if (nodeColors == null) {
//                            nodeColors = new HashSet<Entry<String, Integer>>();
//                            colors.put(node, nodeColors);
//                        }
//                        nodeColors.add(new SimpleImmutableEntry<String, Integer>(name + "Color", colorNumber));
//                        Set<Entry<String, String>> nodeColorStrings = colorsToString.get(node);
//                        if (nodeColorStrings == null) {
//                            nodeColorStrings = new HashSet<Entry<String, String>>();
//                            colorsToString.put(node, nodeColorStrings);
//                        }
//                        nodeColorStrings.add(new SimpleImmutableEntry<String, String>(name, color.toString()));
//                    }
//                } else if (obj instanceof NodeBitMap) {
//                    NodeBitMap bitmap = (NodeBitMap) obj;
//                    for (Node node : bitmap) {
//                        Set<String> nodeBits = bits.get(node);
//                        if (nodeBits == null) {
//                            nodeBits = new HashSet<String>();
//                            bits.put(node, nodeBits);
//                        }
//                        nodeBits.add(name);
//                    }
//                }
//            }
//        }

        for (Node node : graph.getNodes()) {
            if (omittedClasses.contains(node.getClass())) {
                continue;
            }

            printer.beginNode(node.toString(Verbosity.Id));
            printer.beginProperties();
            printer.printProperty("idx", node.toString(Verbosity.Id));

            Map<Object, Object> props = node.getDebugProperties();
            if (!props.containsKey("name") || props.get("name").toString().trim().length() == 0) {
                String name;
                if (shortNames) {
                    name = node.toString(Verbosity.Name);
                } else {
                    name = node.toString();
                }
                printer.printProperty("name", name);
            }
            printer.printProperty("class", node.getClass().getSimpleName());
            Block block = nodeToBlock == null ? null : nodeToBlock.get(node);
            if (block != null) {
                printer.printProperty("block", Integer.toString(block.blockID()));
                if (!(node instanceof PhiNode || node instanceof FrameState || node instanceof LocalNode || node instanceof InductionVariableNode) && !block.getInstructions().contains(node)) {
                    printer.printProperty("notInOwnBlock", "true");
                }
            } else {
                printer.printProperty("block", "noBlock");
                noBlockNodes.add(node);
            }
            if (loopExits.isMarked(node)) {
                printer.printProperty("loopExit", "true");
            }
            StringBuilder sb = new StringBuilder();
            if (loops != null) {
                for (Loop loop : loops) {
                    if (loop.nodes().isMarked(node)) {
                        if (sb.length() > 0) {
                            sb.append(", ");
                        }
                        sb.append(loop.loopBegin().toString(Verbosity.Id));
                    }
                }
            }
            if (sb.length() > 0) {
                printer.printProperty("loops", sb.toString());
            }

            Set<Entry<String, Integer>> nodeColors = colors.get(node);
            if (nodeColors != null) {
                for (Entry<String, Integer> color : nodeColors) {
                    String name = color.getKey();
                    Integer value = color.getValue();
                    printer.printProperty(name, Integer.toString(value));
                }
            }
            Set<Entry<String, String>> nodeColorStrings = colorsToString.get(node);
            if (nodeColorStrings != null) {
                for (Entry<String, String> color : nodeColorStrings) {
                    String name = color.getKey();
                    String value = color.getValue();
                    printer.printProperty(name, value);
                }
            }
            Set<String> nodeBits = bits.get(node);
            if (nodeBits != null) {
                for (String bit : nodeBits) {
                    printer.printProperty(bit, "true");
                }
            }

            for (Entry<Object, Object> entry : props.entrySet()) {
                String key = entry.getKey().toString();
                String value = entry.getValue() == null ? "null" : entry.getValue().toString();
                printer.printProperty(key, value);
            }

            printer.endProperties();
            printer.endNode();

            // successors
            int fromIndex = 0;
            NodeClassIterator succIter = node.successors().iterator();
            while (succIter.hasNext()) {
                Position position = succIter.nextPosition();
                Node successor = node.getNodeClass().get(node, position);
                if (successor != null && !omittedClasses.contains(successor.getClass())) {
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
                if (input != null && !omittedClasses.contains(input.getClass())) {
                    edges.add(new Edge(input.toString(Verbosity.Id), input.successors().explicitCount(), node.toString(Verbosity.Id), toIndex, node.getNodeClass().getName(position)));
                }
                toIndex++;
            }
        }

        return edges;
    }

    private void printBlock(Graph graph, Block block, NodeMap<Block> nodeToBlock) {
        printer.beginBlock(Integer.toString(block.blockID()));
        printer.beginSuccessors();
        for (Block sux : block.getSuccessors()) {
            if (sux != null) {
                printer.printSuccessor(Integer.toString(sux.blockID()));
            }
        }
        printer.endSuccessors();
        printer.beginBlockNodes();

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
                    if (node instanceof LoopBeginNode) {
                        for (InductionVariableNode iv : ((LoopBeginNode) node).inductionVariables()) {
                            nodes.add(iv);
                        }
                    }
                }
            }

            for (Node node : nodes) {
                if (!omittedClasses.contains(node.getClass())) {
                    printer.printBlockNode(node.toString(Verbosity.Id));
                }
            }
        }
        printer.endBlockNodes();
        printer.endBlock();
    }

    private void printNoBlock() {
        if (!noBlockNodes.isEmpty()) {
            printer.beginBlock("noBlock");
            printer.beginBlockNodes();
            for (Node node : noBlockNodes) {
                printer.printBlockNode(node.toString(Verbosity.Id));
            }
            printer.endBlockNodes();
            printer.endBlock();
        }
    }

}
