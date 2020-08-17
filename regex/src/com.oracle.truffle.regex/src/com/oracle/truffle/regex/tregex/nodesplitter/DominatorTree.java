/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.nodesplitter;

import java.util.ArrayList;
import java.util.Arrays;

import com.oracle.truffle.regex.tregex.util.Exceptions;
import com.oracle.truffle.regex.util.CompilationFinalBitSet;

/**
 * Dominance algorithm as described in "A Simple, Fast Dominance Algorithm" by Keith D. Cooper,
 * Timothy J. Harvey, and Ken Kennedy. Used by {@link DFANodeSplit}.
 */
final class DominatorTree {

    private final Graph graph;
    private int nextPostOrderIndex;
    private final ArrayList<GraphNode> postOrder;
    private int[] doms;
    private final CompilationFinalBitSet flagTraversed;

    DominatorTree(Graph graph) {
        this.graph = graph;
        flagTraversed = new CompilationFinalBitSet(graph.size() + DFANodeSplit.EXTRA_INITIAL_CAPACITY);
        postOrder = new ArrayList<>(graph.size() + DFANodeSplit.EXTRA_INITIAL_CAPACITY);
        doms = new int[graph.size() + DFANodeSplit.EXTRA_INITIAL_CAPACITY];
    }

    private boolean isTraversed(GraphNode node) {
        return flagTraversed.get(node.getId());
    }

    private void setTraversed(GraphNode node) {
        flagTraversed.set(node.getId());
    }

    void createDomTree() {
        buildPostOrder();
        buildDominatorTree();
        initDomTreeDepth(graph.getStart(), 1);
    }

    GraphNode idom(GraphNode n) {
        return postOrder.get(doms[n.getPostOrderIndex()]);
    }

    boolean dom(GraphNode a, GraphNode b) {
        int dom = doms[b.getPostOrderIndex()];
        while (true) {
            if (a.getPostOrderIndex() == dom) {
                return true;
            }
            if (dom == doms[dom]) {
                return false;
            }
            dom = doms[dom];
        }
    }

    private boolean graphIsConsistent() {
        for (GraphNode n : graph.getNodes()) {
            for (GraphNode s : n.getSuccessors(graph)) {
                if (!s.hasPredecessor(n)) {
                    return false;
                }
            }
        }
        return true;
    }

    private void buildPostOrder() {
        assert graphIsConsistent();
        flagTraversed.clear();
        nextPostOrderIndex = 0;
        postOrder.clear();
        traversePostOrder(graph.getStart());
        assert allNodesTraversed();
    }

    private void traversePostOrder(GraphNode cur) {
        setTraversed(cur);
        for (GraphNode n : cur.getSuccessors(graph)) {
            if (!isTraversed(n)) {
                traversePostOrder(n);
            }
        }
        cur.setPostOrderIndex(nextPostOrderIndex++);
        postOrder.add(cur);
        assert postOrder.get(cur.getPostOrderIndex()) == cur;
    }

    private boolean allNodesTraversed() {
        for (GraphNode n : graph.getNodes()) {
            if (!isTraversed(n)) {
                return false;
            }
        }
        return true;
    }

    private void buildDominatorTree() {
        if (doms.length < graph.size()) {
            int newLength = doms.length * 2;
            while (newLength < graph.size()) {
                newLength *= 2;
            }
            doms = new int[newLength];
        }
        Arrays.fill(doms, -1);
        doms[graph.getStart().getPostOrderIndex()] = graph.getStart().getPostOrderIndex();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = postOrder.size() - 1; i >= 0; i--) {
                GraphNode b = postOrder.get(i);
                if (b == graph.getStart()) {
                    continue;
                }
                // find a predecessor that was already processed
                GraphNode selectedPredecessor = null;
                for (GraphNode p : b.getPredecessors()) {
                    if (p.getPostOrderIndex() > i) {
                        selectedPredecessor = p;
                        break;
                    }
                }
                if (selectedPredecessor == null) {
                    throw Exceptions.shouldNotReachHere();
                }
                int newIDom = selectedPredecessor.getPostOrderIndex();
                for (GraphNode p : b.getPredecessors()) {
                    if (p == selectedPredecessor) {
                        continue;
                    }
                    if (doms[p.getPostOrderIndex()] != -1) {
                        newIDom = intersect(p.getPostOrderIndex(), newIDom);
                    }
                }
                if (doms[b.getPostOrderIndex()] != newIDom) {
                    doms[b.getPostOrderIndex()] = newIDom;
                    changed = true;
                }
            }
        }
        for (GraphNode n : graph.getNodes()) {
            n.clearDomChildren();
        }
        for (int i = 0; i < graph.size(); i++) {
            GraphNode dominator = postOrder.get(doms[i]);
            GraphNode child = postOrder.get(i);
            if (dominator != child) {
                dominator.addDomChild(child);
            }
        }
    }

    private int intersect(int b1, int b2) {
        int finger1 = b1;
        int finger2 = b2;
        while (finger1 != finger2) {
            while (finger1 < finger2) {
                finger1 = doms[finger1];
            }
            while (finger2 < finger1) {
                finger2 = doms[finger2];
            }
        }
        return finger1;
    }

    private void initDomTreeDepth(GraphNode curNode, int depth) {
        curNode.setDomTreeDepth(depth);
        for (GraphNode child : curNode.getDomChildren(graph)) {
            initDomTreeDepth(child, depth + 1);
        }
    }
}
