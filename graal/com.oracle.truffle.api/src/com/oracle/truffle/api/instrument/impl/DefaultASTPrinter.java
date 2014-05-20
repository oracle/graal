/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.instrument.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.nodes.NodeUtil.NodeClass;
import com.oracle.truffle.api.nodes.NodeUtil.NodeField;
import com.oracle.truffle.api.nodes.NodeUtil.NodeFieldKind;

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

    private static void printTree(PrintWriter p, Node node, int maxDepth, Node markNode, int level) {
        if (node == null) {
            p.print("null");
            return;
        }

        p.print(nodeName(node));

        String sep = "";
        p.print("(");

        final SourceSection src = node.getSourceSection();
        if (src != null) {
            if (!(src instanceof NullSourceSection)) {
                p.print(src.getSource().getName() + ":" + src.getStartLine());
            }
        }
        if (node instanceof PhylumTagged) {
            final PhylumTagged taggedNode = (PhylumTagged) node;
            p.print("[");
            String prefix = "";
            for (PhylumTag tag : taggedNode.getPhylumTags()) {
                p.print(prefix);
                prefix = ",";
                p.print(tag.toString());
            }
            p.print("]");
        }

        ArrayList<NodeField> childFields = new ArrayList<>();

        for (NodeField field : NodeClass.get(node.getClass()).getFields()) {
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
                for (NodeField field : childFields) {

                    Object value = field.loadValue(node);
                    if (value == null) {
                        printNewLine(p, level);
                        p.print(field.getName());
                        p.print(" = null ");
                    } else if (field.getKind() == NodeFieldKind.CHILD) {
                        final Node valueNode = (Node) value;
                        printNewLine(p, level, valueNode == markNode);
                        p.print(field.getName());
                        p.print(" = ");
                        printTree(p, valueNode, maxDepth, markNode, level + 1);
                    } else if (field.getKind() == NodeFieldKind.CHILDREN) {
                        printNewLine(p, level);
                        p.print(field.getName());
                        Node[] children = (Node[]) value;
                        p.print(" = [");
                        sep = "";
                        for (Node child : children) {
                            p.print(sep);
                            sep = ", ";
                            printTree(p, child, maxDepth, markNode, level + 1);
                        }
                        p.print("]");
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

    private static void printNewLine(PrintWriter p, int level, boolean mark) {
        p.println();
        for (int i = 0; i < level; i++) {
            if (mark && i == 0) {
                p.print(" -->");
            } else {
                p.print("    ");
            }
        }
    }

    private static void printNewLine(PrintWriter p, int level) {
        printNewLine(p, level, false);
    }

    private static String nodeName(Node node) {
        return node.getClass().getSimpleName();
    }

}
