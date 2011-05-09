/*
 * Copyright (c) 2011, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.graph.vis;

import java.awt.Color;
import java.io.OutputStream;
import java.io.PrintStream;

import com.oracle.graal.graph.Graph;
import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.Node.NodeArray;

/**
 * Generates a representation of {@link Node Nodes} or entire {@link Graph Graphs} in the DOT language that can be
 * visualized with <a href="http://www.graphviz.org/">Graphviz</a>.
 */
public class GraphvizPrinter {

    public static final Color NODE_BGCOLOR = Color.WHITE;
    private static final String NODE_BGCOLOR_STRING = formatColorString(NODE_BGCOLOR);

    private static String formatColorString(Color c) {
        return String.format("#%02x%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha());
    }

    private final PrintStream out;

    /**
     * Creates a new {@link GraphvizPrinter} that writes to the specified output stream.
     */
    public GraphvizPrinter(OutputStream out) {
        this.out = new PrintStream(out);
    }

    /**
     * Opens a graph with the specified title (label). Call this before printing any nodes, but not more than once
     * without calling {@link #end()} first.
     * 
     * @param title
     *            The graph's label.
     */
    public void begin(String title) {
        out.println("digraph g {");
        if (title != null) {
            out.println("  label=\"" + title + "\";");
        }
    }

    /**
     * Closes the graph. No nodes should be printed afterwards. Another graph can be opened with {@link #begin(String)},
     * but many Graphviz output plugins ignore additional graphs in their input.
     */
    public void end() {
        out.println("}");
    }

    /**
     * Prints all nodes and edges in the specified graph.
     */
    public void print(Graph graph, boolean shortNames) {
        // graph.getNodes() returns all the graph's nodes, not just "roots"
        for (Node n : graph.getNodes()) {
            printNode(n, shortNames);
        }
    }

    /**
     * Prints a single node and edges for all its inputs and successors.
     */
    public void printNode(Node node, boolean shortNames) {
        int id = node.id();
        String name = "n" + id;
        NodeArray inputs = node.inputs();
        NodeArray successors = node.successors();

        if (shortNames) {
            printNode(name, node.shortName(), inputs.size(), successors.size());
        } else {
            printNode(name, node.toString(), inputs.size(), successors.size());
        }

        for (int i = 0; i < successors.size(); ++i) {
            Node successor = successors.get(i);
            if (successor != Node.Null) {
                printControlEdge(id, i, successor.id());
            }
        }

        for (int i = 0; i < inputs.size(); ++i) {
            Node input = inputs.get(i);
            if (input != Node.Null) {
                if (node.getClass().getSimpleName().equals("FrameState") && input.getClass().getSimpleName().equals("Local")) {
                    continue;
                }
                printDataEdge(id, i, input.id());
            }
        }
    }

    private void printNode(String name, String label, int ninputs, int nsuccessors) {
        out.println(name + "  [shape=plaintext,");
        out.println("   label=< <TABLE BORDER=\"0\" CELLSPACING=\"0\"><TR><TD CELLPADDING=\"0\">");
        out.println("    <TABLE BORDER=\"0\" CELLSPACING=\"2\" CELLPADDING=\"0\"><TR>");
        out.println("    <TD WIDTH=\"15\" HEIGHT=\"5\" PORT=\"predecessors\" BGCOLOR=\"rosybrown1\"></TD></TR></TABLE>");
        out.println("    </TD><TD COLSPAN=\"2\" CELLPADDING=\"0\" ALIGN=\"RIGHT\"><TABLE BORDER=\"0\" CELLSPACING=\"2\" CELLPADDING=\"0\"><TR>");

        if ((ninputs == 1 && nsuccessors == 1) || (ninputs == 0 && nsuccessors == 0)) {
            out.println("    <TD WIDTH=\"15\" HEIGHT=\"5\"></TD>");
        }

        for (int i = 0; i < nsuccessors - ninputs; i++) {
            out.println("    <TD WIDTH=\"15\" HEIGHT=\"5\"></TD>");
        }

        for (int i = 0; i < ninputs; i++) {
            out.println("    <TD WIDTH=\"15\" HEIGHT=\"5\" PORT=\"in" + i + "\" BGCOLOR=\"lightgrey\"></TD>");
        }

        label = label.replace("&", "&amp;");
        label = label.replace("<", "&lt;");
        label = label.replace(">", "&gt;");
        out.println("    </TR></TABLE></TD></TR><TR><TD BORDER=\"1\" COLSPAN=\"3\" BGCOLOR=\"" + NODE_BGCOLOR_STRING + "\">" + label + "</TD></TR>");
        out.println("    <TR><TD COLSPAN=\"2\" CELLPADDING=\"0\" ALIGN=\"RIGHT\"><TABLE BORDER=\"0\" CELLSPACING=\"2\" CELLPADDING=\"0\"><TR>");

        for (int i = 0; i < nsuccessors; i++) {
            out.println("    <TD WIDTH=\"15\" HEIGHT=\"5\" PORT=\"succ" + i + "\" BGCOLOR=\"rosybrown1\"></TD>");
        }

        for (int i = 0; i < ninputs - nsuccessors; i++) {
            out.println("    <TD WIDTH=\"15\" HEIGHT=\"5\"></TD>");
        }

        if ((ninputs == 1 && nsuccessors == 1) || (ninputs == 0 && nsuccessors == 0)) {
            out.println("    <TD WIDTH=\"15\" HEIGHT=\"5\"></TD>");
        }

        out.println("    </TR></TABLE></TD><TD CELLPADDING=\"0\"><TABLE BORDER=\"0\" CELLSPACING=\"2\" CELLPADDING=\"0\"><TR>");
        out.println("    <TD WIDTH=\"15\" HEIGHT=\"5\" PORT=\"usages\" BGCOLOR=\"lightgrey\"></TD></TR></TABLE></TD></TR></TABLE>>]; ");
    }

    private void printControlEdge(int from, int fromPort, int to) {
        out.println("n" + from + ":succ" + fromPort + " -> n" + to + ":predecessors:n [color=red];");
    }

    private void printDataEdge(int from, int fromPort, int to) {
        out.println("n" + to + ":usages -> n" + from + ":in" + fromPort + ":n [color=black,dir=back];");
    }

}
