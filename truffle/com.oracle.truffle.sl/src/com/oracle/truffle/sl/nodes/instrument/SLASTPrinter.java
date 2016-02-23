/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.sl.nodes.instrument;

import com.oracle.truffle.api.instrument.ASTPrinter;
import com.oracle.truffle.api.instrument.InstrumentationNode;
import com.oracle.truffle.api.instrument.WrapperNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeClass;
import com.oracle.truffle.api.nodes.NodeFieldAccessor;
import com.oracle.truffle.api.nodes.NodeFieldAccessor.NodeFieldKind;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.source.SourceSection;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;

/**
 * SLASTPrinter is used to print for SL's internal Truffle AST. This is used by
 * {@link SLDefaultVisualizer} to provide a means of displaying the internal Truffle AST
 */
public final class SLASTPrinter implements ASTPrinter {

    public SLASTPrinter() {
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

        for (NodeFieldAccessor field : NodeClass.get(node.getClass()).getFields()) {
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

    @Override
    public void printTree(PrintWriter p, Node node, int maxDepth, Node markNode) {
        printTree(p, node, maxDepth, markNode, 1);
        p.println();
        p.flush();
    }

    @Override
    public String printTreeToString(Node node, int maxDepth, Node markNode) {
        StringWriter out = new StringWriter();
        printTree(new PrintWriter(out), node, maxDepth, markNode);
        return out.toString();
    }

    @Override
    public String printTreeToString(Node node, int maxDepth) {
        return printTreeToString(node, maxDepth, null);
    }

    @Override
    public String printNodeWithInstrumentation(Node node) {
        if (node == null) {
            return "null";
        }
        final StringBuilder sb = new StringBuilder();
        sb.append(nodeName(node));
        sb.append("(");
        if (node instanceof InstrumentationNode) {
            sb.append(instrumentInfo((InstrumentationNode) node));
        }
        sb.append(sourceInfo(node));
        sb.append(NodeUtil.printSyntaxTags(node));
        sb.append(")");
        final Node parent = node.getParent();
        if (parent instanceof WrapperNode) {
            final WrapperNode wrapper = (WrapperNode) parent;
            sb.append(" Probed");
            sb.append(NodeUtil.printSyntaxTags(wrapper));
        }
        return sb.toString();
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
            if (src.getSource() == null) {
                return src.getShortDescription();
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
