/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.nodesplitter;

import com.oracle.truffle.regex.tregex.automaton.IndexedState;
import com.oracle.truffle.regex.tregex.automaton.StateIndex;
import com.oracle.truffle.regex.tregex.automaton.StateSet;
import com.oracle.truffle.regex.tregex.automaton.StateSetBackingSortedArray;
import com.oracle.truffle.regex.tregex.dfa.DFAGenerator;
import com.oracle.truffle.regex.tregex.nodes.DFAAbstractStateNode;
import com.oracle.truffle.regex.tregex.nodes.DFAInitialStateNode;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;

/**
 * Abstract graph node wrapper with lots of extra fields used by the dominator tree algorithm in
 * {@link DominatorTree} and the node splitting algorithm implemented in {@link DFANodeSplit}.
 */
final class GraphNode implements Comparable<GraphNode>, IndexedState {

    private static final short[] NO_DOM_CHILDREN = new short[0];

    private DFAAbstractStateNode originalDfaNode;
    private DFAAbstractStateNode dfaNode;
    private boolean dfaNodeCopied = false;
    private final short[] successorSet;
    private final StateSet<GraphNode> predecessorSet;
    private final StateSet<GraphNode> backEdges;

    private short[] domChildren;
    private int nDomChildren;
    private int domTreeDepth;
    private int postOrderIndex;

    private GraphNode header;
    private int weight;

    private GraphNode copy;

    GraphNode(DFANodeSplit graph, DFAAbstractStateNode dfaNode, short[] successorSet) {
        this.dfaNode = dfaNode;
        this.successorSet = successorSet;
        predecessorSet = new StateSet<>(graph, new StateSetBackingSortedArray());
        backEdges = new StateSet<>(graph);
        domChildren = NO_DOM_CHILDREN;
    }

    private GraphNode(GraphNode cpy, short dfaNodeId) {
        this.originalDfaNode = cpy.dfaNode;
        this.dfaNode = cpy.dfaNode.createNodeSplitCopy(dfaNodeId);
        this.dfaNodeCopied = true;
        this.successorSet = cpy.successorSet;
        this.predecessorSet = cpy.predecessorSet.copy();
        this.backEdges = cpy.backEdges.copy();
        this.domChildren = cpy.domChildren == NO_DOM_CHILDREN ? NO_DOM_CHILDREN : Arrays.copyOf(cpy.domChildren, cpy.domChildren.length);
        this.nDomChildren = cpy.nDomChildren;
        this.header = cpy.header;
        this.postOrderIndex = cpy.postOrderIndex;
        this.domTreeDepth = cpy.domTreeDepth;
        this.weight = cpy.weight;
    }

    void createCopy(DFANodeSplit graph, short dfaNodeId) {
        if (getId() == 0) {
            assert dfaNode instanceof DFAInitialStateNode;
            throw new UnsupportedOperationException();
        }
        this.copy = new GraphNode(this, dfaNodeId);
        graph.addGraphNode(copy);
    }

    public DFAAbstractStateNode getDfaNode() {
        return dfaNode;
    }

    @Override
    public int compareTo(GraphNode o) {
        return getId() - o.getId();
    }

    @Override
    public short getId() {
        return dfaNode.getId();
    }

    void markBackEdge(GraphNode node) {
        backEdges.add(node);
    }

    boolean isBackEdge(GraphNode node) {
        return backEdges.contains(node);
    }

    void clearBackEdges() {
        backEdges.clear();
    }

    private int nodeWeight() {
        return dfaNode.getSuccessors().length;
    }

    void setWeightAndHeaders(StateIndex<GraphNode> index, GraphNode headerNode, Set<GraphNode> scc) {
        weight = nodeWeight();
        for (GraphNode child : getDomChildren(index)) {
            if (scc.contains(child)) {
                child.setWeightAndHeaders(index, headerNode, scc);
                weight += child.weight;
            }
        }
        header = headerNode;
    }

    void replaceSuccessor(GraphNode suc) {
        if (!dfaNodeCopied) {
            dfaNode = dfaNode.createNodeSplitCopy(dfaNode.getId());
            dfaNodeCopied = true;
        }
        short[] dfaSuccessors = dfaNode.getSuccessors();
        for (int i = 0; i < dfaSuccessors.length; i++) {
            if (dfaSuccessors[i] == suc.dfaNode.getId()) {
                dfaSuccessors[i] = suc.copy.dfaNode.getId();
            }
        }
    }

    boolean hasPredecessor(GraphNode pre) {
        return predecessorSet.contains(pre);
    }

    void addPredecessorUnsorted(GraphNode pre) {
        predecessorSet.addBatch(pre);
    }

    void sortPredecessors() {
        predecessorSet.addBatchFinish();
    }

    void addPredecessor(GraphNode pre) {
        predecessorSet.add(pre);
    }

    void replacePredecessor(GraphNode pre) {
        predecessorSet.replace(pre, pre.copy);
    }

    void removePredecessor(GraphNode pre) {
        predecessorSet.remove(pre);
    }

    void addDomChild(GraphNode child) {
        if (nDomChildren == domChildren.length) {
            if (domChildren == NO_DOM_CHILDREN) {
                domChildren = new short[10];
            } else {
                domChildren = Arrays.copyOf(domChildren, domChildren.length * 2);
            }
        }
        domChildren[nDomChildren++] = child.getId();
    }

    void clearDomChildren() {
        nDomChildren = 0;
    }

    public int getPostOrderIndex() {
        return postOrderIndex;
    }

    public void setPostOrderIndex(int postOrderIndex) {
        this.postOrderIndex = postOrderIndex;
    }

    Iterable<GraphNode> getSuccessors(StateIndex<GraphNode> index) {
        return () -> new Iterator<GraphNode>() {

            private int i = 0;

            @Override
            public boolean hasNext() {
                return i < successorSet.length;
            }

            @Override
            public GraphNode next() {
                return index.getState(dfaNode.getSuccessors()[successorSet[i++]]);
            }
        };
    }

    Iterable<GraphNode> getPredecessors() {
        return predecessorSet;
    }

    Iterable<GraphNode> getDomChildren(StateIndex<GraphNode> index) {
        return () -> new Iterator<GraphNode>() {

            private int i = 0;

            @Override
            public boolean hasNext() {
                return i < nDomChildren;
            }

            @Override
            public GraphNode next() {
                return index.getState(domChildren[i++]);
            }
        };
    }

    public int getDomTreeDepth() {
        return domTreeDepth;
    }

    public void setDomTreeDepth(int domTreeDepth) {
        this.domTreeDepth = domTreeDepth;
    }

    public GraphNode getHeader() {
        return header;
    }

    public int getWeight() {
        return weight;
    }

    public GraphNode getCopy() {
        return copy;
    }

    public void clearCopy() {
        this.copy = null;
    }

    public void registerDuplicate(DFAGenerator dfaGenerator) {
        if (originalDfaNode != null) {
            dfaGenerator.nodeSplitRegisterDuplicateState(originalDfaNode.getId(), dfaNode.getId());
        }
    }

    public void updateSuccessors(DFAGenerator dfaGenerator) {
        if (dfaNodeCopied) {
            dfaGenerator.nodeSplitUpdateSuccessors(dfaNode.getId(), dfaNode.getSuccessors());
        }
    }
}
