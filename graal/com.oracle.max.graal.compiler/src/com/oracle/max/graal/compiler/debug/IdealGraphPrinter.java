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
package com.oracle.max.graal.compiler.debug;

import java.io.*;
import java.util.*;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Map.Entry;

import com.oracle.max.graal.compiler.ir.*;
import com.oracle.max.graal.compiler.schedule.*;
import com.oracle.max.graal.compiler.util.*;
import com.oracle.max.graal.compiler.util.LoopUtil.Loop;
import com.oracle.max.graal.compiler.value.*;
import com.oracle.max.graal.graph.*;

/**
 * Generates a representation of {@link Graph Graphs} that can be visualized and inspected with the <a
 * href="http://kenai.com/projects/igv">Ideal Graph Visualizer</a>.
 */
public class IdealGraphPrinter {

    private static class Edge {
        final int from;
        final int to;
        final int fromIndex;
        final int toIndex;

        Edge(int from, int fromIndex, int to, int toIndex) {
            this.from = from;
            this.fromIndex = fromIndex;
            this.to = to;
            this.toIndex = toIndex;
        }
    }

    private final HashSet<Class<?>> omittedClasses = new HashSet<Class<?>>();
    private final PrintStream stream;
    private final Set<Node> noBlockNodes = new HashSet<Node>();

    /**
     * Creates a new {@link IdealGraphPrinter} that writes to the specified output stream.
     */
    public IdealGraphPrinter(OutputStream stream) {
        this.stream = new PrintStream(stream);
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
        stream.flush();
    }

    /**
     * Starts a new graph document.
     */
    public void begin() {
        stream.println("<graphDocument>");
    }

    /**
     * Starts a new group of graphs with the given name, short name and method byte code index (BCI) as properties.
     */
    public void beginGroup(String name, String shortName, int bci) {
        stream.println("<group>");
        stream.printf(" <properties><p name='name'>%s</p><p name='origin'>Graal</p></properties>%n", escape(name));
        stream.printf(" <method name='%s' shortName='%s' bci='%d'/>%n", escape(name), escape(shortName), bci);
    }

    /**
     * Ends the current group.
     */
    public void endGroup() {
        stream.println("</group>");
    }

    /**
     * Finishes the graph document and flushes the output stream.
     */
    public void end() {
        stream.println("</graphDocument>");
        flush();
    }

    public void print(Graph graph, String title, boolean shortNames) {
        print(graph, title, shortNames, Collections.<String, Object>emptyMap());
    }

    /**
     * Prints an entire {@link Graph} with the specified title, optionally using short names for nodes.
     */
    public void print(Graph graph, String title, boolean shortNames, Map<String, Object> debugObjects) {
        stream.printf(" <graph name='%s'>%n", escape(title));
        noBlockNodes.clear();
        IdentifyBlocksPhase schedule = null;
        if (debugObjects != null) {
            for (Object obj : debugObjects.values()) {
                if (obj instanceof IdentifyBlocksPhase) {
                    schedule = (IdentifyBlocksPhase) obj;
                }
            }
        }
        if (schedule == null) {
            try {
                schedule = new IdentifyBlocksPhase(true, false);
                schedule.apply(graph, false, false);
            } catch (Throwable t) {
                // nothing to do here...
                //t.printStackTrace();
            }
        }
        List<Loop> loops = null;
        try {
            loops = LoopUtil.computeLoops(graph);
        } catch (Throwable t) {
            t.printStackTrace();
        }

        stream.println("  <nodes>");
        List<Edge> edges = printNodes(graph, shortNames, schedule == null ? null : schedule.getNodeToBlock(), loops, debugObjects);
        stream.println("  </nodes>");

        stream.println("  <edges>");
        for (Edge edge : edges) {
            printEdge(edge);
        }
        stream.println("  </edges>");

        if (schedule != null) {
            stream.println("  <controlFlow>");
            for (Block block : schedule.getBlocks()) {
                printBlock(graph, block, schedule.getNodeToBlock());
            }
            printNoBlock();
            stream.println("  </controlFlow>");
        }

        stream.println(" </graph>");
        flush();
    }

    private List<Edge> printNodes(Graph graph, boolean shortNames, NodeMap<Block> nodeToBlock, List<Loop> loops, Map<String, Object> debugObjects) {
        ArrayList<Edge> edges = new ArrayList<Edge>();
        NodeBitMap loopExits = graph.createNodeBitMap();
        if (loops != null) {
            for (Loop loop : loops) {
                loopExits.setUnion(loop.exits());
            }
        }

        Map<Node, Set<Entry<String, Integer>>> colors = new HashMap<Node, Set<Entry<String, Integer>>>();
        Map<Node, Set<Entry<String, String>>> colorsToString = new HashMap<Node, Set<Entry<String, String>>>();
        Map<Node, Set<String>> bits = new HashMap<Node, Set<String>>();
        if (debugObjects != null) {
            for (Entry<String, Object> entry : debugObjects.entrySet()) {
                String name = entry.getKey();
                Object obj = entry.getValue();
                if (obj instanceof NodeMap) {
                    Map<Object, Integer> colorNumbers = new HashMap<Object, Integer>();
                    int nextColor = 0;
                    NodeMap<?> map = (NodeMap<?>) obj;
                    for (Entry<Node, ?> mapEntry : map.entries()) {
                        Node node = mapEntry.getKey();
                        Object color = mapEntry.getValue();
                        Integer colorNumber = colorNumbers.get(color);
                        if (colorNumber == null) {
                            colorNumber = nextColor++;
                            colorNumbers.put(color, colorNumber);
                        }
                        Set<Entry<String, Integer>> nodeColors = colors.get(node);
                        if (nodeColors == null) {
                            nodeColors = new HashSet<Entry<String, Integer>>();
                            colors.put(node, nodeColors);
                        }
                        nodeColors.add(new SimpleImmutableEntry<String, Integer>(name + "Color", colorNumber));
                        Set<Entry<String, String>> nodeColorStrings = colorsToString.get(node);
                        if (nodeColorStrings == null) {
                            nodeColorStrings = new HashSet<Entry<String, String>>();
                            colorsToString.put(node, nodeColorStrings);
                        }
                        nodeColorStrings.add(new SimpleImmutableEntry<String, String>(name, color.toString()));
                    }
                } else if (obj instanceof NodeBitMap) {
                    NodeBitMap bitmap = (NodeBitMap) obj;
                    for (Node node : bitmap) {
                        Set<String> nodeBits = bits.get(node);
                        if (nodeBits == null) {
                            nodeBits = new HashSet<String>();
                            bits.put(node, nodeBits);
                        }
                        nodeBits.add(name);
                    }
                }
            }
        }

        for (Node node : graph.getNodes()) {
            if (omittedClasses.contains(node.getClass())) {
                continue;
            }

            stream.printf("   <node id='%d'><properties>%n", node.id());
            stream.printf("    <p name='idx'>%d</p>%n", node.id());

            Map<Object, Object> props = node.getDebugProperties();
            if (!props.containsKey("name") || props.get("name").toString().trim().length() == 0) {
                String name;
                if (shortNames) {
                    name = node.shortName();
                } else {
                    name = node.toString();
                }
                stream.printf("    <p name='name'>%s</p>%n", escape(name));
            }
            stream.printf("    <p name='class'>%s</p>%n", escape(node.getClass().getSimpleName()));
            Block block = nodeToBlock == null ? null : nodeToBlock.get(node);
            if (block != null) {
                stream.printf("    <p name='block'>%d</p>%n", block.blockID());
                if (!(node instanceof Phi || node instanceof FrameState || node instanceof Local || node instanceof LoopCounter) && !block.getInstructions().contains(node)) {
                    stream.println("    <p name='notInOwnBlock'>true</p>");
                }
            } else {
                stream.println("    <p name='block'>noBlock</p>");
                noBlockNodes.add(node);
            }
            if (loopExits.isMarked(node)) {
                stream.println("    <p name='loopExit'>true</p>");
            }
            StringBuilder sb = new StringBuilder();
            if (loops != null) {
                for (Loop loop : loops) {
                    if (loop.nodes().isMarked(node)) {
                        if (sb.length() > 0) {
                            sb.append(", ");
                        }
                        sb.append(loop.loopBegin().id());
                    }
                }
            }
            if (sb.length() > 0) {
                stream.printf("    <p name='loops'>%s</p>%n", sb);
            }

            Set<Entry<String, Integer>> nodeColors = colors.get(node);
            if (nodeColors != null) {
                for (Entry<String, Integer> color : nodeColors) {
                    String name = color.getKey();
                    Integer value = color.getValue();
                    stream.printf("    <p name='%s'>%d</p>%n", name, value);
                }
            }
            Set<Entry<String, String>> nodeColorStrings = colorsToString.get(node);
            if (nodeColorStrings != null) {
                for (Entry<String, String> color : nodeColorStrings) {
                    String name = color.getKey();
                    String value = color.getValue();
                    stream.printf("    <p name='%s'>%s</p>%n", name, value);
                }
            }
            Set<String> nodeBits = bits.get(node);
            if (nodeBits != null) {
                for (String bit : nodeBits) {
                    stream.print("    <p name='");
                    stream.print(bit);
                    stream.println("'>true</p>");
                }
            }

            for (Entry<Object, Object> entry : props.entrySet()) {
                String key = entry.getKey().toString();
                String value = entry.getValue() == null ? "null" : entry.getValue().toString();
                stream.print("    <p name='");
                stream.print(escape(key));
                stream.print("'>");
                stream.print(escape(value));
                stream.println("</p>");
            }

            stream.println("   </properties></node>");

            // successors
            int fromIndex = 0;
            for (Node successor : node.successors()) {
                if (successor != Node.Null && !omittedClasses.contains(successor.getClass())) {
                    edges.add(new Edge(node.id(), fromIndex, successor.id(), 0));
                }
                fromIndex++;
            }

            // inputs
            int toIndex = 1;
            for (Node input : node.inputs()) {
                if (input != Node.Null && !omittedClasses.contains(input.getClass())) {
                    edges.add(new Edge(input.id(), input.successors().size(), node.id(), toIndex));
                }
                toIndex++;
            }
        }

        return edges;
    }

    private void printEdge(Edge edge) {
        stream.printf("   <edge from='%d' fromIndex='%d' to='%d' toIndex='%d'/>%n", edge.from, edge.fromIndex, edge.to, edge.toIndex);
    }

    private void printBlock(Graph graph, Block block, NodeMap<Block> nodeToBlock) {
        stream.printf("   <block name='%d'>%n", block.blockID());
        stream.println("    <successors>");
        for (Block sux : block.getSuccessors()) {
            if (sux != null) {
                stream.printf("     <successor name='%d'/>%n", sux.blockID());
            }
        }
        stream.println("    </successors>");
        stream.println("    <nodes>");

        Set<Node> nodes = new HashSet<Node>(block.getInstructions());

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
            if (block.getInstructions().size() > 0  && block.getInstructions().get(0) == graph.start()) {
                for (Node node : graph.getNodes()) {
                    if (node instanceof Local) {
                        nodes.add(node);
                    }
                }
            }

            // add all framestates and phis to their blocks
            for (Node node : block.getInstructions()) {
                if (node instanceof StateSplit && ((StateSplit) node).stateAfter() != null) {
                    nodes.add(((StateSplit) node).stateAfter());
                }
                if (node instanceof Merge) {
                    for (Node usage : node.usages()) {
                        if (usage instanceof Phi || usage instanceof LoopCounter) {
                            nodes.add(usage);
                        }
                    }
                }
            }

            for (Node node : nodes) {
                if (!omittedClasses.contains(node.getClass())) {
                    stream.printf("     <node id='%d'/>%n", node.id());
                }
            }
        }
        stream.println("    </nodes>");
        stream.printf("   </block>%n", block.blockID());
    }

    private void printNoBlock() {
        if (!noBlockNodes.isEmpty()) {
            stream.printf("   <block name='noBlock'>%n");
            stream.printf("    <nodes>%n");
            for (Node node : noBlockNodes) {
                stream.printf("     <node id='%d'/>%n", node.id());
            }
            stream.printf("    </nodes>%n");
            stream.printf("   </block>%n");
        }
    }

    private String escape(String s) {
        StringBuilder str = null;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&':
                case '<':
                case '>':
                case '"':
                case '\'':
                    if (str == null) {
                        str = new StringBuilder();
                        str.append(s, 0, i);
                    }
                    switch(c) {
                        case '&':
                            str.append("&amp;");
                            break;
                        case '<':
                            str.append("&lt;");
                            break;
                        case '>':
                            str.append("&gt;");
                            break;
                        case '"':
                            str.append("&quot;");
                            break;
                        case '\'':
                            str.append("&apos;");
                            break;
                        default:
                            assert false;
                    }
                    break;
                default:
                    if (str != null) {
                        str.append(c);
                    }
                    break;
            }
        }
        if (str == null) {
            return s;
        } else {
            return str.toString();
        }
    }
}
