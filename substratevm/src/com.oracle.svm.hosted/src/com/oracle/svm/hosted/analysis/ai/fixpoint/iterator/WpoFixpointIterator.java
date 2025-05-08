package com.oracle.svm.hosted.analysis.ai.fixpoint.iterator;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.analyzer.payload.IteratorPayload;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import com.oracle.svm.hosted.analysis.ai.fixpoint.wpo.WeakPartialOrdering;
import com.oracle.svm.hosted.analysis.ai.fixpoint.wpo.WpoVertex;
import com.oracle.svm.hosted.analysis.ai.interpreter.AbstractTransformers;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;
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
 * WARNING: You are now entering a dangerous class, which is currently experimental, and most likely needs to be fixed
 * <p>
 * Represents a deterministic concurrent fixpoint algorithm using weak partial ordering
 * of a {@link ControlFlowGraph}.
 * Implemented based on:
 * Sung Kook Kim, Arnaud J. Venet, and Aditya V. Thakur.
 * Deterministic Parallel Fixpoint Computation.
 * <a href="https://dl.acm.org/ft_gateway.cfm?id=3371082"></a>
 *
 * @param <Domain> type of the derived {@link AbstractDomain}
 */
public final class WpoFixpointIterator<
        Domain extends AbstractDomain<Domain>>
        extends FixpointIteratorBase<Domain> {

    private final WeakPartialOrdering weakPartialOrdering;
    private final HIRBlock entry;
    private final Map<HIRBlock, WorkNode> nodeToWork = new ConcurrentHashMap<>();

    public WpoFixpointIterator(AnalysisMethod method,
                               Domain initialDomain,
                               AbstractTransformers<Domain> abstractTransformers,
                               IteratorPayload iteratorPayload) {
        super(method, initialDomain, abstractTransformers, iteratorPayload);
        if (iteratorPayload.containsMethodWpo(method)) {
            this.weakPartialOrdering = iteratorPayload.getMethodWpoMap().get(method);
        } else {
            this.weakPartialOrdering = new WeakPartialOrdering(graphTraversalHelper);
            iteratorPayload.addToMethodWpoMap(method, weakPartialOrdering);
        }

        this.entry = graphTraversalHelper.getEntryBlock();
    }

    @Override
    public AbstractState<Domain> iterateUntilFixpoint() {
        logger.log("Starting concurrent WPO fixpoint iteration of analysisMethod: " + analysisMethod, LoggerVerbosity.INFO);
        buildWorkNodes();
        runAnalysis();
        logger.log("Finished concurrent WPO fixpoint iteration of analysisMethod: " + analysisMethod, LoggerVerbosity.INFO);
        logger.printLabelledGraph(iteratorPayload.getMethodGraph().get(analysisMethod).graph, analysisMethod, abstractState);
        return abstractState;
    }

    private void buildWorkNodes() {
        logger.log("Building work nodes for WPO", LoggerVerbosity.DEBUG);
        for (int idx = 0; idx < weakPartialOrdering.size(); idx++) {
            WpoVertex.Kind kind = weakPartialOrdering.getKind(idx);
            HIRBlock node = weakPartialOrdering.getNode(idx);
            WorkNode workNode = new WorkNode(kind, node, idx);
            nodeToWork.put(node, workNode);
        }

        for (int idx = 0; idx < weakPartialOrdering.size(); idx++) {
            WorkNode workNode = nodeToWork.get(weakPartialOrdering.getNode(idx));

            for (int successor : weakPartialOrdering.getSuccessors(idx)) {
                workNode.addSuccessor(nodeToWork.get(weakPartialOrdering.getNode(successor)));
            }

            if (workNode.kind == WpoVertex.Kind.Exit) {
                workNode.setHead(nodeToWork.get(weakPartialOrdering.getNode(weakPartialOrdering.getHeadOfExit(idx))));
            } else {
                for (int i = 0; i < workNode.node.getPredecessorCount(); ++i) {
                    HIRBlock pred = workNode.node.getPredecessorAt(i);
                    workNode.addPredecessor(nodeToWork.get(pred));
                }
            }
        }
        logger.log("Finished building work nodes for WPO", LoggerVerbosity.DEBUG);
        logger.log("The WPO of " + analysisMethod.getQualifiedName() + ": ", LoggerVerbosity.DEBUG);
        logger.log(weakPartialOrdering.toString(), LoggerVerbosity.DEBUG);
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
        if (abstractState != null) {
            abstractState.clear();
        }
    }

    private class WorkNode {

        private final WpoVertex.Kind kind;
        private final HIRBlock node;
        private final int index;
        private final AtomicInteger refCount;
        private final List<WorkNode> successors = new ArrayList<>();
        private final List<WorkNode> predecessors = new ArrayList<>();
        private WorkNode head;

        WorkNode(WpoVertex.Kind kind, HIRBlock node, int index) {
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
            abstractTransformers.analyzeBlock(node, abstractState, graphTraversalHelper);
            refCount.set(weakPartialOrdering.getNumPredecessorsReducible(index));
            return successors;
        }

        List<WorkNode> updateHead() {
            if (refCount.get() == weakPartialOrdering.getNumPredecessors(index)) {
                for (WorkNode pred : predecessors) {
                    if (!weakPartialOrdering.isBackEdge(node, pred.node)) {
                        abstractTransformers.analyzeEdge(pred.node, node, abstractState);
                    }
                }
            }

            abstractTransformers.analyzeBlock(node, abstractState, graphTraversalHelper);
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
            Domain oldPre = abstractState.getPreCondition(node.getBeginNode()).copyOf();
            abstractTransformers.collectInvariantsFromCfgPredecessors(node, abstractState, graphTraversalHelper);
            extrapolate(node.getBeginNode());

            if (oldPre.leq(abstractState.getPreCondition(node.getBeginNode()))) {
                abstractState.resetCount(node.getBeginNode());
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
