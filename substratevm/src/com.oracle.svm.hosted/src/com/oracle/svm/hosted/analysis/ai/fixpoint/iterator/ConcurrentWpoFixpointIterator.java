package com.oracle.svm.hosted.analysis.ai.fixpoint.iterator;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.policy.IteratorPolicy;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import com.oracle.svm.hosted.analysis.ai.fixpoint.summary.FixpointCache;
import com.oracle.svm.hosted.analysis.ai.fixpoint.wpo.WeakPartialOrdering;
import com.oracle.svm.hosted.analysis.ai.fixpoint.wpo.WpoNode;
import com.oracle.svm.hosted.analysis.ai.interpreter.TransferFunction;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represents a deterministic concurrent fixpoint algorithm using weak partial ordering
 * of a {@link ControlFlowGraph}.
 * Implemented based on:
 * Sung Kook Kim, Arnaud J. Venet, and Aditya V. Thakur.
 * Deterministic Parallel Fixpoint Computation.
 * https://dl.acm.org/ft_gateway.cfm?id=3371082
 *
 * @param <Domain> type of the derived {@link AbstractDomain}
 */
public final class ConcurrentWpoFixpointIterator<
        Domain extends AbstractDomain<Domain>>
        extends FixpointIteratorBase<Domain> {

    private final WeakPartialOrdering weakPartialOrdering;
    private final HIRBlock entry;
    private final Map<HIRBlock, WorkNode> nodeToWork = new ConcurrentHashMap<>();

    public ConcurrentWpoFixpointIterator(ControlFlowGraph cfgGraph,
                                         com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.policy.IteratorPolicy policy,
                                         TransferFunction<Domain> transferFunction,
                                         Domain initialDomain,
                                         DebugContext debug) {
        super(cfgGraph, policy, transferFunction, initialDomain, debug);
        this.weakPartialOrdering = new WeakPartialOrdering(cfgGraph);
        this.entry = cfgGraph.getStartBlock();
    }

    public ConcurrentWpoFixpointIterator(ControlFlowGraph cfgGraph,
                                         IteratorPolicy policy,
                                         TransferFunction<Domain> transferFunction,
                                         Domain initialDomain,
                                         AbstractStateMap<Domain> abstractStateMap,
                                         FixpointCache<Domain> fixpointCache,
                                         DebugContext debug) {
        super(cfgGraph, policy, transferFunction, initialDomain, abstractStateMap, fixpointCache, debug);
        this.weakPartialOrdering = new WeakPartialOrdering(cfgGraph);
        this.entry = cfgGraph.getStartBlock();
    }

    @Override
    public AbstractStateMap<Domain> iterateUntilFixpoint() {
        clear();
        buildWorkNodes();
        runAnalysis();
        return abstractStateMap;
    }

    private void buildWorkNodes() {
        for (int idx = 0; idx < weakPartialOrdering.size(); idx++) {
            WpoNode.Kind kind = weakPartialOrdering.getKind(idx);
            HIRBlock node = weakPartialOrdering.getNode(idx);

            WorkNode workNode = new WorkNode(kind, node, idx);
            nodeToWork.put(node, workNode);
        }

        for (int idx = 0; idx < weakPartialOrdering.size(); idx++) {
            WorkNode workNode = nodeToWork.get(weakPartialOrdering.getNode(idx));

            for (int succ : weakPartialOrdering.getSuccessors(idx)) {
                workNode.addSuccessor(nodeToWork.get(weakPartialOrdering.getNode(succ)));
            }

            if (workNode.kind == WpoNode.Kind.Exit) {
                workNode.setHead(nodeToWork.get(weakPartialOrdering.getNode(weakPartialOrdering.getHeadOfExit(idx))));
            } else {
                for (int i = 0; i < workNode.node.getPredecessorCount(); ++i) {
                    HIRBlock pred = workNode.node.getPredecessorAt(i);
                    workNode.addPredecessor(nodeToWork.get(pred));
                }
            }
        }
    }

    private void runAnalysis() {
        Queue<WorkNode> workQueue = new ConcurrentLinkedQueue<>();
        workQueue.add(nodeToWork.get(entry));

        while (!workQueue.isEmpty()) {
            WorkNode workNode = workQueue.poll();
            List<WorkNode> successors = workNode.update();

            for (WorkNode successor : successors) {
                if (successor.decrementRefCount() == 0) {
                    workQueue.add(successor);
                }
            }
        }

    }

    @Override
    public void clear() {
        nodeToWork.clear();
        if (abstractStateMap != null) {
            abstractStateMap.clear();
        }
    }

    private class WorkNode {

        private final WpoNode.Kind kind;
        private final HIRBlock node;
        private final int index;
        private final AtomicInteger refCount;
        private final List<WorkNode> successors = new ArrayList<>();
        private final List<WorkNode> predecessors = new ArrayList<>();
        private WorkNode head;

        WorkNode(WpoNode.Kind kind, HIRBlock node, int index) {
            this.kind = kind;
            this.node = node;
            this.index = index;
            this.refCount = new AtomicInteger(weakPartialOrdering.getNumPredecessors(index));
        }

        void addSuccessor(WorkNode workNode) {
            successors.add(workNode);
        }

        void addPredecessor(WorkNode workNode) {
            predecessors.add(workNode);
        }

        void setHead(WorkNode head) {
            this.head = head;
        }

        List<WorkNode> update() {
            return switch (kind) {
                case Plain -> updatePlain();
                case Head -> updateHead();
                case Exit -> updateExit();
            };
        }

        List<WorkNode> updatePlain() {
            transferFunction.analyzeBlock(node, abstractStateMap, fixpointCache);
            refCount.set(weakPartialOrdering.getNumPredecessorsReducible(index));
            return successors;
        }

        List<WorkNode> updateHead() {
            if (refCount.get() == weakPartialOrdering.getNumPredecessors(index)) {
                for (WorkNode pred : predecessors) {
                    if (!weakPartialOrdering.isBackEdge(node, pred.node)) {
                        transferFunction.analyzeEdge(pred.node, node, abstractStateMap);
                    }
                }
            }

            transferFunction.analyzeBlock(node, abstractStateMap, fixpointCache);
            return successors;
        }

        List<WorkNode> updateExit() {
            boolean converged = head.updateHeadBackEdge();
            refCount.set(weakPartialOrdering.getNumPredecessorsReducible(index));
            if (converged) {
                handleIrreducible();
                return successors;
            } else {
                return head.updateHead();
            }
        }

        boolean updateHeadBackEdge() {
            Domain oldPre = abstractStateMap.getPreCondition(node.getBeginNode()).copyOf();
            transferFunction.collectInvariantsFromPredecessors(node.getBeginNode(), abstractStateMap);
            extrapolate(node.getBeginNode());

            if (oldPre.leq(abstractStateMap.getPreCondition(node.getBeginNode()))) {
                abstractStateMap.resetCount(node.getBeginNode());
                return true;
            }

            return false;
        }

        void handleIrreducible() {
            for (Map.Entry<Integer, Integer> entry : weakPartialOrdering.getIrreducibles(index).entrySet()) {
                nodeToWork.get(weakPartialOrdering.getNode(entry.getKey())).refCount.addAndGet(entry.getValue());
            }
        }

        int decrementRefCount() {
            return refCount.decrementAndGet();
        }
    }
}