/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.nodes.debug;

import java.io.*;
import java.util.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.nodes.NodeUtil.NodeClass;
import com.oracle.truffle.api.nodes.NodeUtil.NodeField;
import com.oracle.truffle.api.nodes.NodeUtil.NodeFieldKind;
import com.oracle.truffle.api.nodes.instrument.*;
import com.oracle.truffle.ruby.nodes.*;
import com.oracle.truffle.ruby.nodes.call.CallNode;
import com.oracle.truffle.ruby.nodes.literal.*;
import com.oracle.truffle.ruby.nodes.methods.*;

/**
 * Printers for Truffle-internal AST information.
 */
public final class RubyASTPrinter implements ASTPrinter {

    public RubyASTPrinter() {
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
            } else if (src instanceof CoreSourceSection) {
                final CoreSourceSection coreSection = (CoreSourceSection) src;
                p.print("core=\"" + (coreSection == null ? "?" : coreSection.toString()) + "\"");
            }
        }
        if (node instanceof PhylumMarked) {
            final PhylumMarked markedNode = (PhylumMarked) node;
            String prefix = "";
            for (NodePhylum phylum : markedNode.getPhylumMarks()) {
                p.print(prefix);
                prefix = ",";
                p.print(phylum.toString());
            }

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
        String nodeVal = null;
        if (node instanceof CallNode) {
            final CallNode callNode = (CallNode) node;
            nodeVal = callNode.getName();

        } else if (node instanceof FixnumLiteralNode) {
            final FixnumLiteralNode fixnum = (FixnumLiteralNode) node;
            nodeVal = Integer.toString(fixnum.getValue());
        } else if (node instanceof MethodDefinitionNode) {
            final MethodDefinitionNode defNode = (MethodDefinitionNode) node;
            nodeVal = defNode.getName();
        }
        String result = node.getClass().getSimpleName();
        if (nodeVal != null) {
            result = result + "[\"" + nodeVal + "\"]";
        }
        return result;
    }

}
