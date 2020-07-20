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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.regex.tregex.TRegexOptions;
import com.oracle.truffle.regex.tregex.automaton.StateIndex;
import com.oracle.truffle.regex.tregex.automaton.StateSet;
import com.oracle.truffle.regex.tregex.buffer.ShortArrayBuffer;
import com.oracle.truffle.regex.tregex.dfa.DFAGenerator;
import com.oracle.truffle.regex.tregex.nodes.dfa.DFAAbstractStateNode;
import com.oracle.truffle.regex.tregex.nodes.dfa.DFAInitialStateNode;
import com.oracle.truffle.regex.util.CompilationFinalBitSet;

/**
 * Implementation of a node splitting algorithm presented by Sebastian Unger and Frank Mueller in
 * "Handling Irreducible Loops: Optimized Node Splitting vs. DJ-Graphs" (2001) and "Transforming
 * Irreducible Regions of Control Flow into Reducible Regions by Optimized Node Splitting" (1998).
 */
public final class DFANodeSplit implements StateIndex<GraphNode> {

    public static final int EXTRA_INITIAL_CAPACITY = 20;

    private final DFAGenerator dfaGenerator;
    private final Graph graph;
    private final DominatorTree domTree;
    private final CompilationFinalBitSet flagDone;
    private final CompilationFinalBitSet flagActive;
    private short nextId;

    private DFANodeSplit(DFAGenerator dfaGenerator, DFAAbstractStateNode[] dfa) {
        this.dfaGenerator = dfaGenerator;
        graph = new Graph(dfa.length + EXTRA_INITIAL_CAPACITY);
        CompilationFinalBitSet successorBitSet = new CompilationFinalBitSet(dfa.length);
        ShortArrayBuffer successorBuffer = new ShortArrayBuffer();
        for (DFAAbstractStateNode n : dfa) {
            for (int i = 0; i < n.getSuccessors().length; i++) {
                if (n.getSuccessors()[i] == -1) {
                    assert n instanceof DFAInitialStateNode;
                } else {
                    if (!successorBitSet.get(n.getSuccessors()[i])) {
                        successorBuffer.add((short) i);
                    }
                    successorBitSet.set(n.getSuccessors()[i]);
                }
            }
            GraphNode graphNode = new GraphNode(this, n, successorBuffer.toArray());
            successorBitSet.clear();
            successorBuffer.clear();
            graph.addGraphNode(graphNode);
        }
        nextId = (short) graph.size();
        flagDone = new CompilationFinalBitSet(graph.size() + EXTRA_INITIAL_CAPACITY);
        flagActive = new CompilationFinalBitSet(graph.size() + EXTRA_INITIAL_CAPACITY);
        for (GraphNode graphNode : graph.getNodes()) {
            for (GraphNode successor : graphNode.getSuccessors(this)) {
                successor.addPredecessor(graphNode);
            }
        }
        graph.setStart(graph.getNodes().get(0));
        domTree = new DominatorTree(graph);
    }

    private boolean isDone(GraphNode node) {
        return flagDone.get(node.getId());
    }

    private void setDone(GraphNode node) {
        flagDone.set(node.getId());
    }

    private void clearDone(GraphNode node) {
        flagDone.clear(node.getId());
    }

    private boolean isActive(GraphNode node) {
        return flagActive.get(node.getId());
    }

    private void setActive(GraphNode node) {
        flagActive.set(node.getId());
    }

    private void clearActive(GraphNode node) {
        flagActive.clear(node.getId());
    }

    public void addGraphNode(GraphNode graphNode) {
        graph.addGraphNode(graphNode);
    }

    public static DFAAbstractStateNode[] createReducibleGraph(DFAAbstractStateNode[] nodes) throws DFANodeSplitBailoutException {
        return new DFANodeSplit(null, nodes).process();
    }

    public static DFAAbstractStateNode[] createReducibleGraphAndUpdateDFAGen(DFAGenerator dfaGen, DFAAbstractStateNode[] nodes) throws DFANodeSplitBailoutException {
        return new DFANodeSplit(dfaGen, nodes).process();
    }

    @Override
    public int getNumberOfStates() {
        return graph.getNumberOfStates();
    }

    @Override
    public int getId(GraphNode state) {
        return state.getId();
    }

    @Override
    public GraphNode getState(int id) {
        return graph.getState(id);
    }

    private DFAAbstractStateNode[] process() throws DFANodeSplitBailoutException {
        domTree.createDomTree();
        searchBackEdges(graph.getStart());
        markUndone();
        splitLoops(graph.getStart(), Collections.emptySet());
        DFAAbstractStateNode[] ret = new DFAAbstractStateNode[graph.size()];
        for (GraphNode node : graph.getNodes()) {
            ret[node.getDfaNode().getId()] = node.getDfaNode();
        }
        if (dfaGenerator != null) {
            updateDFAGenerator();
        }
        return ret;
    }

    /**
     * Register all changes made in the DFA generator. This is necessary for gathering debug
     * information only, never do this in production!
     */
    private void updateDFAGenerator() {
        dfaGenerator.nodeSplitSetNewDFASize(graph.size());
        for (GraphNode node : graph.getNodes()) {
            node.registerDuplicate(dfaGenerator);
        }
        for (GraphNode node : graph.getNodes()) {
            node.updateSuccessors(dfaGenerator);
        }
    }

    private boolean splitLoops(GraphNode topNode, Set<GraphNode> set) throws DFANodeSplitBailoutException {
        boolean crossEdge = false;
        for (GraphNode child : topNode.getDomChildren(this)) {
            if (set.isEmpty() || set.contains(child)) {
                if (splitLoops(child, set)) {
                    crossEdge = true;
                }
            }
        }
        if (crossEdge) {
            handleIrChildren(topNode, set);
        }
        for (GraphNode pred : topNode.getPredecessors()) {
            if (pred.isBackEdge(topNode) && !domTree.dom(topNode, pred)) {
                return true;
            }
        }
        return false;
    }

    private void handleIrChildren(GraphNode topNode, Set<GraphNode> set) throws DFANodeSplitBailoutException {
        ArrayDeque<GraphNode> dfsList = new ArrayDeque<>();
        ArrayList<Set<GraphNode>> sccList = new ArrayList<>();
        for (GraphNode child : topNode.getDomChildren(this)) {
            if (!isDone(child) && (set.isEmpty() || set.contains(child))) {
                scc1(dfsList, child, set, topNode.getDomTreeDepth());
            }
        }
        for (GraphNode n : dfsList) {
            if (isDone(n)) {
                Set<GraphNode> scc = StateSet.create(this);
                scc2(scc, n, topNode.getDomTreeDepth());
                sccList.add(scc);
            }
        }
        for (Set<GraphNode> scc : sccList) {
            if (scc.size() > 1) {
                handleScc(topNode, scc);
            }
        }
    }

    private void scc1(ArrayDeque<GraphNode> dfsList, GraphNode curNode, Set<GraphNode> set, int level) {
        setDone(curNode);
        for (GraphNode child : curNode.getSuccessors(this)) {
            if (!isDone(child) && child.getDomTreeDepth() > level && (set.isEmpty() || set.contains(child))) {
                scc1(dfsList, child, set, level);
            }
        }
        dfsList.push(curNode);
    }

    private void scc2(Set<GraphNode> scc, GraphNode curNode, int level) {
        clearDone(curNode);
        for (GraphNode pred : curNode.getPredecessors()) {
            if (isDone(pred) && pred.getDomTreeDepth() > level) {
                scc2(scc, pred, level);
            }
        }
        scc.add(curNode);
    }

    private void handleScc(GraphNode topNode, Set<GraphNode> scc) throws DFANodeSplitBailoutException {
        StateSet<DFANodeSplit, GraphNode> msed = StateSet.create(this);
        for (GraphNode n : scc) {
            if (n.getDomTreeDepth() == topNode.getDomTreeDepth() + 1) {
                n.setWeightAndHeaders(this, n, scc);
                msed.add(n);
            }
        }
        if (msed.size() <= 1) {
            return;
        }
        splitSCC(chooseNode(msed), scc);

        domTree.createDomTree();
        markUndone();
        searchBackEdges(graph.getStart());
        markUndone();
        for (GraphNode tmp : findTopNodes(scc)) {
            splitLoops(tmp, scc);
        }
    }

    private void splitSCC(GraphNode headerNode, Set<GraphNode> scc) throws DFANodeSplitBailoutException {
        for (GraphNode n : scc) {
            if (n.getHeader() != headerNode) {
                if (nextId == TRegexOptions.TRegexMaxDFASizeAfterNodeSplitting) {
                    CompilerDirectives.transferToInterpreter();
                    throw new DFANodeSplitBailoutException();
                }
                n.createCopy(this, nextId++);
            }
        }
        for (GraphNode cur : scc) {
            if (cur.getHeader() != headerNode) {
                for (GraphNode suc : cur.getSuccessors(this)) {
                    if (suc.getCopy() == null) {
                        suc.addPredecessor(cur.getCopy());
                    } else {
                        cur.getCopy().replaceSuccessor(suc);
                        suc.getCopy().replacePredecessor(cur);
                    }
                }
                Iterator<GraphNode> curPredecessors = cur.getPredecessors().iterator();
                while (curPredecessors.hasNext()) {
                    GraphNode pred = curPredecessors.next();
                    if (pred.getCopy() == null) {
                        if (scc.contains(pred)) {
                            pred.replaceSuccessor(cur);
                            curPredecessors.remove();
                        } else {
                            cur.getCopy().removePredecessor(pred);
                        }
                    }
                }
            }
        }
        for (GraphNode g : new ArrayList<>(scc)) {
            if (g.getHeader() != headerNode) {
                scc.add(g.getCopy());
                g.clearCopy();
            }
        }
    }

    private Set<GraphNode> findTopNodes(Set<GraphNode> scc) {
        Set<GraphNode> tops = StateSet.create(this);
        for (GraphNode tmp : scc) {
            GraphNode top = domTree.idom(tmp);
            while (scc.contains(top)) {
                top = domTree.idom(top);
            }
            tops.add(top);
        }
        return tops;
    }

    private static GraphNode chooseNode(Set<GraphNode> msed) {
        int maxWeight = 0;
        GraphNode maxNode = null;
        for (GraphNode n : msed) {
            if (n.getWeight() > maxWeight) {
                maxWeight = n.getWeight();
                maxNode = n;
            }
        }
        return maxNode;
    }

    private void searchBackEdges(GraphNode cnode) {
        setDone(cnode);
        setActive(cnode);
        cnode.clearBackEdges();
        for (GraphNode child : cnode.getSuccessors(this)) {
            if (isActive(child)) {
                cnode.markBackEdge(child);
            } else if (!isDone(child)) {
                searchBackEdges(child);
            }
        }
        clearActive(cnode);
    }

    private void markUndone() {
        flagDone.clear();
        flagActive.clear();
    }
}
