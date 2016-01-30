/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.igv;

import java.io.PrintStream;
import java.util.Map;
import java.util.Map.Entry;

public class IdealGraphPrinterBase {

    private PrintStream stream;

    public IdealGraphPrinterBase(PrintStream stream) {
        this.stream = stream;
    }

    protected static class Edge {

        final String from;
        final int fromIndex;
        final String to;
        final int toIndex;
        final String label;

        public Edge(String from, int fromIndex, String to, int toIndex, String label) {
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
                return from.equals(other.from) && fromIndex == other.fromIndex && to.equals(other.to) && toIndex == other.toIndex &&
                                (label == other.label || (label != null && label.equals(other.label)));
            }
            return false;
        }
    }

    protected void printEdge(Edge edge) {
        stream.printf("   <edge from='%s' fromIndex='%d' to='%s' toIndex='%d' label='%s' />%n", escape(edge.from), edge.fromIndex, escape(edge.to), edge.toIndex, escape(edge.label));
    }

    protected void endEdges() {
        stream.println("  </edges>");
    }

    protected void beginBlock(String name) {
        stream.printf("   <block name='%s'>%n", escape(name));
    }

    protected void beginSuccessors() {
        stream.println("    <successors>");
    }

    protected void printSuccessor(String name) {
        stream.printf("     <successor name='%s'/>%n", escape(name));
    }

    protected void endSuccessors() {
        stream.println("    </successors>");
    }

    protected void beginProperties() {
        stream.print("<properties>");
    }

    protected void printProperty(String name, String value) {
        stream.printf("<p name='%s'>%s</p>", escape(name), escape(value));
    }

    protected void endProperties() {
        stream.print("</properties>");
    }

    protected void printProperties(Map<String, String> properties) {
        beginProperties();
        for (Entry<String, String> entry : properties.entrySet()) {
            printProperty(entry.getKey(), entry.getValue());
        }
        endProperties();
    }

    protected void beginNodes() {
        stream.println("  <nodes>");
    }

    protected void endNodes() {
        stream.println("  </nodes>");
    }

    protected void beginNode(String id) {
        stream.printf("   <node id='%s'>", escape(id));
    }

    protected void endNode() {
        stream.println("   </node>");
    }

    protected void beginGraphDocument() {
        stream.println("<graphDocument>");
    }

    protected void beginEdges() {
        stream.println("  <edges>");
    }

    protected void endGraphDocument() {
        stream.println("</graphDocument>");
    }

    protected void beginGraph(String title) {
        stream.printf(" <graph name='%s'>%n", escape(title));
    }

    protected void endGraph() {
        stream.println(" </graph>");
    }

    protected void beginGroup() {
        stream.println("<group>");
    }

    public void endGroup() {
        stream.println("</group>");
    }

    private static String escape(String s) {
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
                    switch (c) {
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
                case '\u0000':
                case '\u0001':
                case '\u0002':
                case '\u0003':
                case '\u0004':
                case '\u0005':
                case '\u0006':
                case '\u0007':
                case '\u0008':
                case '\u000b':
                case '\u000c':
                case '\u000e':
                case '\u000f':
                case '\u0010':
                case '\u0011':
                case '\u0012':
                case '\u0013':
                case '\u0014':
                case '\u0015':
                case '\u0016':
                case '\u0017':
                case '\u0018':
                case '\u0019':
                case '\u001a':
                case '\u001b':
                case '\u001c':
                case '\u001d':
                case '\u001e':
                case '\u001f':
                    if (str == null) {
                        str = new StringBuilder();
                        str.append(s, 0, i);
                    }
                    str.append("'0x").append(Integer.toHexString(c));
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
