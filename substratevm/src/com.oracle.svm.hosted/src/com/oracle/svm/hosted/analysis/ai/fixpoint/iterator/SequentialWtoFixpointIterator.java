package com.oracle.svm.hosted.analysis.ai.fixpoint.iterator;

import com.oracle.svm.hosted.analysis.ai.analyzer.payload.AnalysisPayload;
import com.oracle.svm.hosted.analysis.ai.analyzer.payload.InterProceduralAnalysisPayload;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import com.oracle.svm.hosted.analysis.ai.fixpoint.wto.WeakTopologicalOrdering;
import com.oracle.svm.hosted.analysis.ai.fixpoint.wto.WtoComponent;
import com.oracle.svm.hosted.analysis.ai.fixpoint.wto.WtoCycle;
import com.oracle.svm.hosted.analysis.ai.fixpoint.wto.WtoVertex;
import com.oracle.svm.hosted.analysis.ai.interpreter.TransferFunction;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;

/**
 * Represents a fixpoint iterator that iterates over {@link HIRBlock}s
 * according to the Weak Topological Ordering (WTO).
 * Implemented based on:
 * F. Bourdoncle. Efficient chaotic iteration strategies with widenings.
 * In Formal Methods in Programming and Their Applications, pp 128-141.
 *
 * @param <Domain> type of the derived AbstractDomain
 */
public final class SequentialWtoFixpointIterator<Domain extends AbstractDomain<Domain>> extends FixpointIteratorBase<Domain> {

    private final WeakTopologicalOrdering weakTopologicalOrdering;

    public SequentialWtoFixpointIterator(AnalysisPayload<Domain> payload) {
        super(payload);
        this.weakTopologicalOrdering = new WeakTopologicalOrdering(cfgGraph);

        if (payload instanceof InterProceduralAnalysisPayload<Domain> interPayload) {
            interPayload.putMethodGraph(payload.getRoot(), cfgGraph);
            interPayload.putMethodWto(payload.getRoot(), weakTopologicalOrdering);
        }
    }

    /* This ctor is used when we have the graph + WTO for a method ready (we analyzed it in the past) */
    public SequentialWtoFixpointIterator(AnalysisPayload<Domain> payload,
                                         ControlFlowGraph cfgGraph,
                                         WeakTopologicalOrdering weakTopologicalOrdering) {
        super(payload, cfgGraph);
        this.weakTopologicalOrdering = weakTopologicalOrdering;
    }

    @Override
    public AbstractStateMap<Domain> iterateUntilFixpoint() {
        payload.getLogger().logToFile("SequentialWtoFixpointIterator::iterateUntilFixpoint");
        for (WtoComponent component : weakTopologicalOrdering.getComponents()) {
            analyzeComponent(component);
        }

        payload.getCheckerManager().checkAll(abstractStateMap);
        return abstractStateMap;
    }

    private void analyzeComponent(WtoComponent component) {
        if (component instanceof WtoVertex vertex) {
            analyzeVertex(vertex);
        } else if (component instanceof WtoCycle cycle) {
            analyzeCycle(cycle);
        }
    }

    private void analyzeVertex(WtoVertex vertex) {
        payload.getLogger().logToFile("Analyzing vertex: " + vertex);
        Node node = vertex.block().getBeginNode();
        if (node == cfgGraph.graph.start()) {
            abstractStateMap.setPreCondition(node, payload.getInitialDomain());
        }
        TransferFunction<Domain> transferFunction = payload.getTransferFunction();
        transferFunction.analyzeBlock(vertex.block(), abstractStateMap, payload);
    }

    private void analyzeCycle(WtoCycle cycle) {
        payload.getLogger().logToFile("Analyzing cycle: " + cycle);
        Node head = cycle.block().getBeginNode();
        TransferFunction<Domain> transferFunction = payload.getTransferFunction();
        boolean iterate = true;

        while (iterate) {
            /* Analyze the nodes inside outermost block */
            transferFunction.analyzeBlock(cycle.block(), abstractStateMap, payload);

            /* Analyze all other nested WtoComponents */
            for (WtoComponent component : cycle.components()) {
                analyzeComponent(component);
            }

            /*
             * At this point we analyzed the body of the cycle,
             * we look at the head of the cycle by collecting invariants from predecessors
             * and checking if the pre-condition at the head of the cycle changed.
             */
            transferFunction.collectInvariantsFromPredecessors(head, abstractStateMap);
            if (abstractStateMap.getPreCondition(head).leq(abstractStateMap.getPostCondition(head))) {
                iterate = false;
            } else {
                extrapolate(head);
            }
        }
    }
}