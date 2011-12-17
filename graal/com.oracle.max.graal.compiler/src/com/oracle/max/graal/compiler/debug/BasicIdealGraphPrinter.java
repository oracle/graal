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
import java.util.Map.Entry;

/**
 * Elementary, generic generator of Ideal Graph Visualizer input for use in printers for specific data structures.
 */
public class BasicIdealGraphPrinter {

    /**
     * Edge between two nodes.
     */
    public static class Edge {
        final String from;
        final int fromIndex;
        final String to;
        final int toIndex;
        final String label;

        public Edge(String from, int fromIndex, String to, int toIndex, String label) {
            assert (from != null && to != null);
            this.from = from;
            this.fromIndex = fromIndex;
            this.to = to;
            this.toIndex = toIndex;
            this.label = label;
        }

        @Override
        public int hashCode() {
            int h = from.hashCode() ^ to.hashCode();
            h = 3 * h + fromIndex;
            h = 5 * h + toIndex;
            if (label != null) {
                h ^= label.hashCode();
            }
            return h;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj instanceof Edge) {
                Edge other = (Edge) obj;
                return from.equals(other.from)
                        && fromIndex == other.fromIndex
                        && to.equals(other.to)
                        && toIndex == other.toIndex
                        && (label == other.label || (label != null && label.equals(other.label)));
            }
            return false;
        }
    }

    private final PrintStream stream;

    /**
     * Creates a new {@link IdealGraphPrinter} that writes to the specified output stream.
     */
    public BasicIdealGraphPrinter(OutputStream stream) {
        this.stream = new PrintStream(stream);
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

    public void beginGroup() {
        stream.println("<group>");
    }

    public void beginMethod(String name, String shortName, int bci) {
        stream.printf(" <method name='%s' shortName='%s' bci='%d'>%n", escape(name), escape(shortName), bci);
    }

    public void beginBytecodes() {
        stream.println("  <bytecodes>\n<![CDATA[");
    }

    public void printBytecode(int bci, String mnemonic, int[] extra) {
        stream.print(bci);
        stream.print(' ');
        stream.print(mnemonic);
        if (extra != null) {
            for (int b : extra) {
                stream.print(' ');
                stream.print(b);
            }
        }
        stream.println();
    }

    public void endBytecodes() {
        stream.println("  ]]></bytecodes>");
    }

    public void endMethod() {
        stream.println(" </method>");
    }

    public void beginGraph(String title) {
        stream.printf(" <graph name='%s'>%n", escape(title));
    }

    public void beginProperties() {
        stream.print("<properties>");
    }

    public void printProperty(String name, String value) {
        stream.printf("<p name='%s'>%s</p>", escape(name), escape(value));
    }

    public void endProperties() {
        stream.print("</properties>");
    }

    public void printProperties(Map<String, String> properties) {
        beginProperties();
        for (Entry<String, String> entry : properties.entrySet()) {
            printProperty(entry.getKey(), entry.getValue());
        }
        endProperties();
    }

    public void beginNodes() {
        stream.println("  <nodes>");
    }

    public void beginNode(String id) {
        stream.printf("   <node id='%s'>", escape(id));
    }

    public void endNode() {
        stream.println("   </node>");
    }

    public void printNode(String id, Map<String, String> properties) {
        beginNode(id);
        if (properties != null) {
            printProperties(properties);
        }
        endNode();
    }

    public void endNodes() {
        stream.println("  </nodes>");
    }

    public void beginEdges() {
        stream.println("  <edges>");
    }

    public void printEdge(Edge edge) {
        stream.printf("   <edge from='%s' fromIndex='%d' to='%s' toIndex='%d' label='%s' />%n", escape(edge.from), edge.fromIndex, escape(edge.to), edge.toIndex, escape(edge.label));
    }

    public void endEdges() {
        stream.println("  </edges>");
    }

    public void beginControlFlow() {
        stream.println("  <controlFlow>");
    }

    public void beginBlock(String name) {
        stream.printf("   <block name='%s'>%n", escape(name));
    }

    public void beginSuccessors() {
        stream.println("    <successors>");
    }

    public void printSuccessor(String name) {
        stream.printf("     <successor name='%s'/>%n", escape(name));
    }

    public void endSuccessors() {
        stream.println("    </successors>");
    }

    public void beginBlockNodes() {
        stream.println("    <nodes>");
    }

    public void printBlockNode(String nodeId) {
        stream.printf("     <node id='%s'/>%n", escape(nodeId));
    }

    public void endBlockNodes() {
        stream.println("    </nodes>");
    }

    public void endBlock() {
        stream.println("   </block>");
    }

    public void endControlFlow() {
        stream.println("  </controlFlow>");
    }

    public void endGraph() {
        stream.println(" </graph>");
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
