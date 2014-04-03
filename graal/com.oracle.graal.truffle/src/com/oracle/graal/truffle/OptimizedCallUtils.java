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

class OptimizedCallUtils {

    public abstract static class InlinedCallVisitor implements NodeVisitor {

        private TruffleCallPath currentPath;
        private final TruffleInliningResult inliningDecision;

        public InlinedCallVisitor(TruffleInliningResult inliningDecision, TruffleCallPath initialPath) {
            this.inliningDecision = inliningDecision;
            this.currentPath = initialPath;
        }

        public final TruffleInliningResult getInliningDecision() {
            return inliningDecision;
        }

        public final boolean visit(Node node) {
            if (node instanceof OptimizedCallNode) {
                OptimizedCallNode callNode = ((OptimizedCallNode) node);
                this.currentPath = new TruffleCallPath(this.currentPath, callNode);
                try {
                    boolean result = visit(currentPath, node);
                    TruffleInliningResult decision = inliningDecision;
                    if (decision != null && decision.isInlined(currentPath)) {
                        callNode.getCurrentRootNode().accept(this);
                    }
                    return result;
                } finally {
                    this.currentPath = this.currentPath.getParent();
                }
            } else {
                return visit(currentPath, node);
            }
        }

        public abstract boolean visit(TruffleCallPath path, Node node);

    }

    public static int countNodes(TruffleInliningResult decision, TruffleCallPath path, InlinedNodeCountFilter filter) {
        InlinedNodeCountVisitor nodeCount = new InlinedNodeCountVisitor(decision, path, filter);
        path.getCallTarget().getRootNode().accept(nodeCount);
        return nodeCount.nodeCount;
    }

    public static int countCalls(TruffleInliningResult decision, TruffleCallPath path) {
        InlinedNodeCountVisitor nodeCount = new InlinedNodeCountVisitor(decision, path, new InlinedNodeCountFilter() {
            public boolean isCounted(TruffleCallPath p, Node node) {
                return node instanceof CallNode;
            }
        });
        path.getCallTarget().getRootNode().accept(nodeCount);
        return nodeCount.nodeCount;
    }

    public interface InlinedNodeCountFilter {

        boolean isCounted(TruffleCallPath path, Node node);
    }

    private static final class InlinedNodeCountVisitor extends InlinedCallVisitor {

        private final InlinedNodeCountFilter filter;
        int nodeCount;

        private InlinedNodeCountVisitor(TruffleInliningResult decision, TruffleCallPath initialPath, InlinedNodeCountFilter filter) {
            super(decision, initialPath);
            this.filter = filter;
        }

        @Override
        public boolean visit(TruffleCallPath path, Node node) {
            if (filter == null || filter.isCounted(path, node)) {
                nodeCount++;
            }
            return true;
        }

    }

    static int countNonTrivialNodes(TruffleInliningResult state, TruffleCallPath path) {
        return countNodes(state, path, new InlinedNodeCountFilter() {

            public boolean isCounted(TruffleCallPath p, Node node) {
                NodeCost cost = node.getCost();
                if (cost != null && cost != NodeCost.NONE && cost != NodeCost.UNINITIALIZED) {
                    return true;
                }
                return false;
            }
        });
    }

}
