/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.truffle.api.instrument.impl;

import java.io.*;
import java.util.*;

import com.oracle.truffle.api.instrument.*;
import com.oracle.truffle.api.instrument.ProbeNode.WrapperNode;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.nodes.NodeFieldAccessor.NodeFieldKind;
import com.oracle.truffle.api.source.*;

/**
 * A language-agnostic for printing out various pieces of a Truffle AST.
 */
public class DefaultASTPrinter implements ASTPrinter {

    public DefaultASTPrinter() {
    }

    public void printTree(PrintWriter p, Node node, int maxDepth, Node markNode) {
        printTree(p, node, maxDepth, markNode, 1);
        p.println();
        p.flush();
    }

    public String printTreeToString(Node node, int maxDepth, Node markNode) {
        StringWriter out = new StringWriter();
        printTree(new PrintWriter(out), node, maxDepth, markNode);
        return out.toString();
    }

    public String printTreeToString(Node node, int maxDepth) {
        return printTreeToString(node, maxDepth, null);
    }

    public String printNodeWithInstrumentation(Node node) {
        final StringBuilder sb = new StringBuilder();
        if (node == null) {
            sb.append("null");
        } else {
            sb.append(nodeName(node));
            sb.append("(");
            if (node instanceof InstrumentationNode) {
                sb.append(instrumentInfo((InstrumentationNode) node));
            }
            sb.append(sourceInfo(node));

            sb.append(NodeUtil.printSyntaxTags(node));
            sb.append(")");
        }
        final Node parent = node.getParent();
        if (parent instanceof WrapperNode) {
            final WrapperNode wrapper = (WrapperNode) parent;
            sb.append(" Probed");
            sb.append(NodeUtil.printSyntaxTags(wrapper));
        }
        return sb.toString();
    }

    protected void printTree(PrintWriter p, Node node, int maxDepth, Node markNode, int level) {
        if (node == null) {
            p.print("null");
            return;
        }

        p.print(nodeName(node));

        p.print("(");

        if (node instanceof InstrumentationNode) {
            p.print(instrumentInfo((InstrumentationNode) node));
        }

        p.print(sourceInfo(node));

        p.print(NodeUtil.printSyntaxTags(node));

        ArrayList<NodeFieldAccessor> childFields = new ArrayList<>();

        for (NodeFieldAccessor field : NodeClass.get(node).getFields()) {
            if (field.getKind() == NodeFieldKind.CHILD || field.getKind() == NodeFieldKind.CHILDREN) {
                childFields.add(field);
            } else if (field.getKind() == NodeFieldKind.DATA) {
                // p.print(sep);
                // sep = ", ";
                //
                // final String fieldName = field.getName();
                // switch (fieldName) {
                //
                // }
                // p.print(fieldName);
                // p.print(" = ");
                // p.print(field.loadValue(node));
            }
        }
        p.print(")");

        if (level <= maxDepth) {

            if (childFields.size() != 0) {
                p.print(" {");
                for (NodeFieldAccessor field : childFields) {

                    Object value = field.loadValue(node);
                    if (value == null) {
                        printNewLine(p, level);
                        p.print(field.getName());
                        p.print(" = null ");
                    } else if (field.getKind() == NodeFieldKind.CHILD) {
                        printChild(p, maxDepth, markNode, level, field, value);
                    } else if (field.getKind() == NodeFieldKind.CHILDREN) {
                        printChildren(p, maxDepth, markNode, level, field, value);
                    } else {
                        printNewLine(p, level);
                        p.print(field.getName());
                    }
                }
                printNewLine(p, level - 1);
                p.print("}");
            }
        }
    }

    protected void printChildren(PrintWriter p, int maxDepth, Node markNode, int level, NodeFieldAccessor field, Object value) {
        printNewLine(p, level);
        p.print(field.getName());
        Node[] children = (Node[]) value;
        p.print(" = [");
        String sep = "";
        for (Node child : children) {
            p.print(sep);
            sep = ", ";
            printTree(p, child, maxDepth, markNode, level + 1);
        }
        p.print("]");
    }

    protected void printChild(PrintWriter p, int maxDepth, Node markNode, int level, NodeFieldAccessor field, Object value) {
        final Node valueNode = (Node) value;
        printNewLine(p, level, valueNode == markNode);
        p.print(field.getName());
        p.print(" = ");
        printTree(p, valueNode, maxDepth, markNode, level + 1);
    }

    protected static void printNewLine(PrintWriter p, int level, boolean mark) {
        p.println();
        for (int i = 0; i < level; i++) {
            if (mark && i == 0) {
                p.print(" -->");
            } else {
                p.print("    ");
            }
        }
    }

    protected static void printNewLine(PrintWriter p, int level) {
        printNewLine(p, level, false);
    }

    protected static String nodeName(Node node) {
        return node.getClass().getSimpleName();
    }

    protected static String sourceInfo(Node node) {
        final SourceSection src = node.getSourceSection();
        if (src != null) {
            if (src instanceof NullSourceSection) {
                final NullSourceSection nullSection = (NullSourceSection) src;
                return nullSection.getShortDescription();
            } else {
                return src.getSource().getName() + ":" + src.getStartLine();
            }
        }
        return "";
    }

    protected static String instrumentInfo(InstrumentationNode node) {
        final String info = node.instrumentationInfo();
        return info == null ? "" : info;
    }

}
