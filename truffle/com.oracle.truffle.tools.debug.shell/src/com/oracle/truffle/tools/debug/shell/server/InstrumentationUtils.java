/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.debug.shell.server;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;

import com.oracle.truffle.api.instrumentation.InstrumentableFactory.WrapperNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeClass;
import com.oracle.truffle.api.source.SourceSection;

/**
 * A language-agnostic for printing out various pieces of a Truffle AST.
 */
@SuppressWarnings("deprecation")
final class InstrumentationUtils {

    private InstrumentationUtils() {
    }

    /**
     * Language-agnostic textual display of AST structure, extensible for per-language subclasses.
     */
    static class ASTPrinter {

        /**
         * Prints a textual AST display, one line per node, with nesting.
         *
         * @param p
         * @param node the root node of the display.
         * @param maxDepth the maximum number of levels to print below the root
         * @param markNode a node to mark with a textual arrow prefix, if present.
         */
        public void printTree(PrintWriter p, Node node, int maxDepth, Node markNode) {
            printTree(p, node, maxDepth, markNode, 1);
            p.println();
            p.flush();
        }

        /**
         * Creates a textual AST display, one line per node, with nesting.
         *
         * @param node the root node of the display.
         * @param maxDepth the maximum number of levels to print below the root
         * @param markNode a node to mark with a textual arrow prefix, if present.
         */
        public String displayAST(Node node, int maxDepth, Node markNode) {
            StringWriter out = new StringWriter();
            printTree(new PrintWriter(out), node, maxDepth, markNode);
            return out.toString();
        }

        /**
         * Creates a textual AST display, one line per node, with nesting.
         *
         * @param node the root node of the display.
         * @param maxDepth the maximum number of levels to print below the root
         */
        public String displayAST(Node node, int maxDepth) {
            return displayAST(node, maxDepth, null);
        }

        /**
         * Creates a textual display describing a single (non-wrapper) node, including
         * instrumentation status: if Probed, and any tags.
         */
        public String displayNodeWithInstrumentation(Node node) {
            if (node == null) {
                return "null";
            }
            final StringBuilder sb = new StringBuilder();
            sb.append(displayNodeName(node));
            sb.append("(");
            sb.append(displayTags(node));
            if (node.getParent() instanceof WrapperNode) {
                sb.append(",Probed");
            }
            sb.append(") ");
            sb.append(displaySourceInfo(node));
            return sb.toString();
        }

        /**
         * Returns a string listing the tags, if any, associated with a node.
         * <ul>
         * <li>"[tag1, tag2, ...]" if tags have been applied;</li>
         * <li>"[]" if the node supports tags, but none are present; and</li>
         * <li>"" if the node does not support tags.</li>
         * </ul>
         */
        protected String displayTags(final Object node) {
            if ((node instanceof Node) && ((Node) node).getSourceSection() != null) {
                return ((Node) node).getSourceSection().toString();
            }
            return "";
        }

        protected void printTree(PrintWriter p, Node node, int maxDepth, Node markNode, int level) {
            if (node == null) {
                p.print("null");
                return;
            }
            if (node instanceof WrapperNode) {
                p.print("WRAPPER(" + node.getClass().getSimpleName() + ")");
            } else {
                p.print(displayNodeWithInstrumentation(node));
            }
            if (level <= maxDepth) {
                ArrayList<com.oracle.truffle.api.nodes.NodeFieldAccessor> childFields = new ArrayList<>();
                for (com.oracle.truffle.api.nodes.NodeFieldAccessor field : NodeClass.get(node).getFields()) {
                    if (field.getKind() == com.oracle.truffle.api.nodes.NodeFieldAccessor.NodeFieldKind.CHILD ||
                                    field.getKind() == com.oracle.truffle.api.nodes.NodeFieldAccessor.NodeFieldKind.CHILDREN) {
                        childFields.add(field);
                    }
                }
                if (childFields.size() != 0) {
                    p.print(" {");
                    for (com.oracle.truffle.api.nodes.NodeFieldAccessor field : childFields) {

                        Object value = field.loadValue(node);
                        if (value == null) {
                            printNewLine(p, level);
                            p.print(field.getName());
                            p.print(" = null ");
                        } else if (field.getKind() == com.oracle.truffle.api.nodes.NodeFieldAccessor.NodeFieldKind.CHILD) {
                            printChild(p, maxDepth, markNode, level, field, value);
                        } else if (field.getKind() == com.oracle.truffle.api.nodes.NodeFieldAccessor.NodeFieldKind.CHILDREN) {
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

        protected void printChildren(PrintWriter p, int maxDepth, Node markNode, int level, com.oracle.truffle.api.nodes.NodeFieldAccessor field, Object value) {
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

        protected void printChild(PrintWriter p, int maxDepth, Node markNode, int level, com.oracle.truffle.api.nodes.NodeFieldAccessor field, Object value) {
            final Node valueNode = (Node) value;
            printNewLine(p, level, valueNode == markNode);
            p.print(field.getName());
            p.print(" = ");
            printTree(p, valueNode, maxDepth, markNode, level + 1);
        }

        protected void printNewLine(PrintWriter p, int level, boolean mark) {
            p.println();
            for (int i = 0; i < level; i++) {
                if (mark && i == 0) {
                    p.print(" -->");
                } else {
                    p.print("    ");
                }
            }
        }

        protected void printNewLine(PrintWriter p, int level) {
            printNewLine(p, level, false);
        }

        protected String displayNodeName(Node node) {
            return node.getClass().getSimpleName();
        }

        protected String displaySourceInfo(Node node) {
            final SourceSection src = node.getSourceSection();
            if (src != null) {
                if (src.getSource() == null) {
                    return getShortDescription(src);
                } else {
                    return src.getSource().getName() + ":" + src.getStartLine();
                }
            }
            return "";
        }
    }

    static String getShortDescription(SourceSection sourceSection) {
        StringBuilder b = new StringBuilder();
        b.append(sourceSection.getSource().getName());
        b.append(":");
        if (sourceSection.getStartLine() == sourceSection.getEndLine()) {
            b.append(sourceSection.getStartLine());
        } else {
            b.append(sourceSection.getStartLine()).append("-").append(sourceSection.getEndLine());
        }
        return b.toString();
    }

    /**
     * Language-agnostic textual display of source location information.
     */
    static class LocationPrinter {

        public String displaySourceLocation(Node node) {

            if (node == null) {
                return "<unknown>";
            }
            SourceSection section = node.getSourceSection();
            boolean estimated = false;
            if (section == null) {
                section = node.getEncapsulatingSourceSection();
                estimated = true;
            }
            if (section == null) {
                return "<unknown source location>";
            }
            return getShortDescription(section) + (estimated ? "~" : "");
        }
    }
}
