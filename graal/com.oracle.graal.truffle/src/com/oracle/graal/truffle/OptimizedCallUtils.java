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

import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.nodes.NodeUtil.NodeCountFilter;

class OptimizedCallUtils {

    public static int countNodes(OptimizedCallTarget target, NodeCountFilter filter, boolean inlined) {
        NodeCountVisitorImpl nodeCount = new NodeCountVisitorImpl(filter, inlined);
        target.getRootNode().accept(nodeCount);
        return nodeCount.nodeCount;
    }

    public static int countCalls(OptimizedCallTarget target) {
        return countNodes(target, new NodeCountFilter() {
            public boolean isCounted(Node node) {
                return node instanceof CallNode;
            }
        }, true);
    }

    public static int countCallsInlined(OptimizedCallTarget target) {
        return countNodes(target, new NodeCountFilter() {
            public boolean isCounted(Node node) {
                return (node instanceof OptimizedCallNode) && ((OptimizedCallNode) node).isInlined();
            }
        }, true);
    }

    public static int countNonTrivialNodes(final OptimizedCallTarget target, final boolean inlined) {
        return countNodes(target, new NodeCountFilter() {
            public boolean isCounted(Node node) {
                NodeCost cost = node.getCost();
                if (cost != null && cost != NodeCost.NONE && cost != NodeCost.UNINITIALIZED) {
                    return true;
                }
                return false;
            }
        }, inlined);
    }

    private static final class NodeCountVisitorImpl implements NodeVisitor {

        private final NodeCountFilter filter;
        private int nodeCount;
        private boolean followInlined;

        private NodeCountVisitorImpl(NodeCountFilter filter, boolean followInlined) {
            this.filter = filter;
            this.followInlined = followInlined;
        }

        public boolean visit(Node node) {
            if (filter == null || filter.isCounted(node)) {
                nodeCount++;
            }
            if (followInlined) {
                if (node instanceof OptimizedCallNode) {
                    OptimizedCallNode ocn = (OptimizedCallNode) node;
                    if (ocn.isInlined()) {
                        ocn.getCurrentRootNode().accept(this);
                    }
                }
            }
            return true;
        }

    }

}
