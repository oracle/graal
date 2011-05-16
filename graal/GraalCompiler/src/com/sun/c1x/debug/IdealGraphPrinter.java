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
package com.sun.c1x.debug;

import java.io.*;
import java.util.*;

import com.oracle.graal.graph.*;

/**
 * Generates a representation of {@link Graph Graphs} that can be visualized and inspected with the <a
 * href="http://kenai.com/projects/igv">Ideal Graph Visualizer</a>.
 */
public class IdealGraphPrinter {

    private static class Edge {
        final int from;
        final int to;
        final int index;

        Edge(int from, int to, int index) {
            this.from = from;
            this.to = to;
            this.index = index;
        }
    }

    private final HashSet<Class<?>> omittedClasses = new HashSet<Class<?>>();
    private final PrintStream stream;

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
     * Starts a new graph document containing a single group of graphs with the given name, short name and byte code
     * index (BCI) as properties.
     */
    public void begin(String name, String shortName, int bci) {
        stream.println("<graphDocument><group>");
        stream.printf(" <properties><p name='name'>%s</p></properties>%n", escape(name));
        stream.printf(" <method name='%s' shortName='%s' bci='%d'/>%n", escape(name), escape(shortName), bci);
    }

    /**
     * Finishes the graph document.
     */
    public void end() {
        stream.println("</group></graphDocument>");
        stream.flush();
    }

    /**
     * Prints an entire {@link Graph} with the specified title, optionally using short names for nodes.
     */
    public void print(Graph graph, String title, boolean shortNames) {
        stream.printf(" <graph name='%s'>%n", escape(title));

        stream.println("  <nodes>");
        List<Edge> edges = printNodes(graph.getNodes(), shortNames);
        stream.println("  </nodes>");

        stream.println("  <edges>");
        for (Edge edge : edges) {
            printEdge(edge);
        }
        stream.println("  </edges>");

        stream.println(" </graph>");
    }

    private List<Edge> printNodes(Collection<Node> nodes, boolean shortNames) {
        ArrayList<Edge> edges = new ArrayList<Edge>();

        for (Node node : nodes) {
            if (node == Node.Null || omittedClasses.contains(node)) {
                continue;
            }

            String name;
            if (shortNames) {
                name = node.shortName();
            } else {
                name = node.toString();
            }

            stream.printf("   <node id='%d'><properties>", node.id());
            stream.printf("<p name='idx'>%d</p>", node.id());
            stream.printf("<p name='name'>%s</p>", escape(name));
            stream.println("</properties></node>");

            int index = 0;
            for (Node predecessor : node.predecessors()) {
                if (predecessor != Node.Null && !omittedClasses.contains(predecessor.getClass())) {
                    edges.add(new Edge(predecessor.id(), node.id(), index));
                }
                index++;
            }
            for (Node input : node.inputs()) {
                if (input != Node.Null && !omittedClasses.contains(input.getClass())) {
                    edges.add(new Edge(input.id(), node.id(), index));
                }
                index++;
            }
        }

        return edges;
    }

    private void printEdge(Edge edge) {
        stream.printf("   <edge from='%d' to='%d' index='%d'/>%n", edge.from, edge.to, edge.index);
    }

    private String escape(String s) {
        s = s.replace("&", "&amp;");
        s = s.replace("<", "&lt;");
        s = s.replace(">", "&gt;");
        s = s.replace("\"", "&quot;");
        s = s.replace("'", "&apos;");
        return s;
    }
}
