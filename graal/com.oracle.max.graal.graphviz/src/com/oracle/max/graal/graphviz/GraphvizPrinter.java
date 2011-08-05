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
package com.oracle.max.graal.graphviz;

import java.awt.Color;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.HashSet;

import com.oracle.max.graal.graph.Graph;
import com.oracle.max.graal.graph.Node;
import com.oracle.max.graal.graph.NodeInputsIterable;
import com.oracle.max.graal.graph.NodeSuccessorsIterable;

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
    private final HashSet<Class<?>> omittedClasses = new HashSet<Class<?>>();
    private final HashMap<Class<?>, String> classColors = new HashMap<Class<?>, String>();

    /**
     * Creates a new {@link GraphvizPrinter} that writes to the specified output stream.
     */
    public GraphvizPrinter(OutputStream out) {
        this.out = new PrintStream(out);
    }

    public void addOmittedClass(Class<?> clazz) {
        omittedClasses.add(clazz);
    }

    public void addClassColor(Class<?> clazz, String color) {
        classColors.put(clazz, color);
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
        if (omittedClasses.contains(node.getClass())) {
            return;
        }
        int id = node.id();
        String name = "n" + id;
        NodeInputsIterable inputs = node.inputs();
        NodeSuccessorsIterable successors = node.successors();

        String color = classColors.get(node.getClass());

        if (shortNames) {
            printNode(name, node.id(), excapeLabel(node.shortName()), color, inputs.explicitCount(), successors.explicitCount());
        } else {
            printNode(name, node.id(), excapeLabel(node.toString()), color, inputs.explicitCount(), successors.explicitCount());
        }

        int i = 0;
        for (Node successor : successors) {
            if (successor != Node.Null && !omittedClasses.contains(successor.getClass())) {
                printControlEdge(id, i, successor.id());
            }
            i++;
        }

        i = 0;
        for (Node input : inputs) {
            if (input != Node.Null && !omittedClasses.contains(input.getClass())) {
                if (node.getClass().getSimpleName().equals("FrameState") && input.getClass().getSimpleName().equals("Local")) {
                    continue;
                }
                printDataEdge(id, i, input.id());
            }
            i++;
        }
    }

    private void printNode(String name, Number number, String label, String color, int ninputs, int nsuccessors) {
        int minWidth = Math.min(1 + label.length() / 3, 10);
        minWidth = Math.max(minWidth, Math.max(ninputs + 1, nsuccessors + 1));
        out.println(name + "  [shape=plaintext,");
        out.println("   label=< <TABLE BORDER=\"0\" CELLSPACING=\"0\"><TR>");

        printPort("predecessors", "rosybrown1");

        for (int i = 1; i < minWidth - ninputs; i++) {
            printEmptyPort();
        }

        for (int i = 0; i < ninputs; i++) {
            printPort("in" + i, "lightgrey");
        }

        if (number != null) {
            label = "<FONT POINT-SIZE=\"8\">" + number + "</FONT> " + label;
        }

        out.println("    </TR><TR><TD BORDER=\"1\" COLSPAN=\"" + minWidth + "\" BGCOLOR=\"" + (color != null ? color : NODE_BGCOLOR_STRING) + "\">" + label + "</TD></TR><TR>");

        for (int i = 0; i < nsuccessors; i++) {
            printPort("succ" + i, "rosybrown1");
        }

        for (int i = 1; i < minWidth - nsuccessors; i++) {
            printEmptyPort();
        }

        printPort("usages", "lightgrey");

        out.println("    </TR></TABLE>>]; ");
    }

    private static String excapeLabel(String label) {
        label = label.replace("&", "&amp;");
        label = label.replace("<", "&lt;");
        label = label.replace(">", "&gt;");
        label = label.replace("\"", "&quot;");
        return label;
    }

    private void printPort(String name, String color) {
        out.print("    <TD CELLPADDING=\"0\" WIDTH=\"15\"><TABLE BORDER=\"0\" CELLSPACING=\"2\" CELLPADDING=\"0\"><TR><TD WIDTH=\"15\" HEIGHT=\"5\" PORT=\"");
        out.print(name);
        out.print("\" BGCOLOR=\"");
        out.print(color);
        out.println("\"></TD></TR></TABLE></TD>");
    }

    private void printEmptyPort() {
        out.print("    <TD CELLPADDING=\"0\" WIDTH=\"15\"><TABLE BORDER=\"0\" CELLSPACING=\"2\" CELLPADDING=\"0\"><TR><TD WIDTH=\"15\" HEIGHT=\"5\"></TD></TR></TABLE></TD>");
    }

    private void printControlEdge(int from, int fromPort, int to) {
        out.println("n" + from + ":succ" + fromPort + ":s -> n" + to + ":predecessors:n [color=red, weight=2];");
    }

    private void printDataEdge(int from, int fromPort, int to) {
        out.println("n" + to + ":usages:s -> n" + from + ":in" + fromPort + ":n [color=black,dir=back];");
    }

}
