/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle;

import java.io.*;

import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.nodes.NodeUtil.NodeClass;
import com.oracle.truffle.api.nodes.NodeUtil.NodeCountFilter;
import com.oracle.truffle.api.nodes.NodeUtil.NodeField;

class OptimizedCallUtils {

    public static int countCalls(OptimizedCallTarget target) {
        return NodeUtil.countNodes(target.getRootNode(), new NodeCountFilter() {
            public boolean isCounted(Node node) {
                return node instanceof DirectCallNode;
            }
        }, true);
    }

    public static int countCallsInlined(OptimizedCallTarget target) {
        return NodeUtil.countNodes(target.getRootNode(), new NodeCountFilter() {
            public boolean isCounted(Node node) {
                return (node instanceof OptimizedDirectCallNode) && ((OptimizedDirectCallNode) node).isInlined();
            }
        }, true);
    }

    public static int countNonTrivialNodes(final OptimizedCallTarget target, final boolean inlined) {
        return NodeUtil.countNodes(target.getRootNode(), new NodeCountFilter() {
            public boolean isCounted(Node node) {
                NodeCost cost = node.getCost();
                if (cost != null && cost != NodeCost.NONE && cost != NodeCost.UNINITIALIZED) {
                    return true;
                }
                return false;
            }
        }, inlined);
    }

    public static void printCompactTree(OutputStream out, Node node) {
        printCompactTree(new PrintWriter(out), null, node, 1);
    }

    private static void printCompactTree(PrintWriter p, Node parent, Node node, int level) {
        if (node == null) {
            return;
        }
        for (int i = 0; i < level; i++) {
            p.print("  ");
        }
        if (parent == null) {
            p.println(node.getClass().getSimpleName());
        } else {
            String fieldName = "unknownField";
            NodeField[] fields = NodeClass.get(parent.getClass()).getFields();
            for (NodeField field : fields) {
                Object value = field.loadValue(parent);
                if (value == node) {
                    fieldName = field.getName();
                    break;
                } else if (value instanceof Node[]) {
                    int index = 0;
                    for (Node arrayNode : (Node[]) value) {
                        if (arrayNode == node) {
                            fieldName = field.getName() + "[" + index + "]";
                            break;
                        }
                        index++;
                    }
                }
            }
            p.print(fieldName);
            p.print(" = ");
            p.println(node.getClass().getSimpleName());
        }

        for (Node child : node.getChildren()) {
            printCompactTree(p, node, child, level + 1);
        }
        if (node instanceof OptimizedDirectCallNode) {
            OptimizedDirectCallNode callNode = (OptimizedDirectCallNode) node;
            if (callNode.isInlined()) {
                printCompactTree(p, node, callNode.getCurrentRootNode(), level + 1);
            }
        }
        p.flush();
    }

}
