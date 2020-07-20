/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;

import com.oracle.truffle.regex.tregex.automaton.StateIndex;
import com.oracle.truffle.regex.tregex.automaton.StateSet;
import com.oracle.truffle.regex.tregex.dfa.DFAGenerator;
import com.oracle.truffle.regex.tregex.nodes.dfa.DFAAbstractStateNode;
import com.oracle.truffle.regex.tregex.nodes.dfa.DFAInitialStateNode;
import com.oracle.truffle.regex.tregex.util.Exceptions;

/**
 * Abstract graph node wrapper with lots of extra fields used by the dominator tree algorithm in
 * {@link DominatorTree} and the node splitting algorithm implemented in {@link DFANodeSplit}.
 */
final class GraphNode implements Comparable<GraphNode> {

    private static final int[] NO_DOM_CHILDREN = new int[0];

    private DFAAbstractStateNode originalDfaNode;
    private DFAAbstractStateNode dfaNode;
    private boolean dfaNodeCopied = false;
    private final short[] successorSet;
    private final StateSet<DFANodeSplit, GraphNode> predecessorSet;
    private final StateSet<DFANodeSplit, GraphNode> backEdges;

    private int[] domChildren;
    private int nDomChildren;
    private int domTreeDepth;
    private int postOrderIndex;

    private GraphNode header;
    private int weight;

    private GraphNode copy;

    GraphNode(DFANodeSplit graph, DFAAbstractStateNode dfaNode, short[] successorSet) {
        this.dfaNode = dfaNode;
        this.successorSet = successorSet;
        predecessorSet = StateSet.create(graph);
        backEdges = StateSet.create(graph);
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
            throw Exceptions.shouldNotReachHere();
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

    public int getId() {
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

    void addPredecessor(GraphNode pre) {
        predecessorSet.add(pre);
    }

    void replacePredecessor(GraphNode pre) {
        predecessorSet.remove(pre);
        predecessorSet.add(pre.copy);
    }

    void removePredecessor(GraphNode pre) {
        predecessorSet.remove(pre);
    }

    void addDomChild(GraphNode child) {
        if (nDomChildren == domChildren.length) {
            if (domChildren == NO_DOM_CHILDREN) {
                domChildren = new int[10];
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
