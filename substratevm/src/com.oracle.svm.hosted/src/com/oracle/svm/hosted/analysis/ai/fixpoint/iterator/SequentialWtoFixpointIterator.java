package com.oracle.svm.hosted.analysis.ai.fixpoint.iterator;

import com.oracle.svm.hosted.analysis.ai.analyzer.context.AnalysisContext;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import com.oracle.svm.hosted.analysis.ai.fixpoint.wto.WeakTopologicalOrdering;
import com.oracle.svm.hosted.analysis.ai.fixpoint.wto.WtoComponent;
import com.oracle.svm.hosted.analysis.ai.fixpoint.wto.WtoCycle;
import com.oracle.svm.hosted.analysis.ai.fixpoint.wto.WtoVertex;
import com.oracle.svm.hosted.analysis.ai.interpreter.TransferFunction;
import jdk.graal.compiler.graph.Node;
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

    public SequentialWtoFixpointIterator(AnalysisContext<Domain> context, TransferFunction<Domain> transferFunction) {
        super(context, transferFunction);
    }

    @Override
    public AbstractStateMap<Domain> iterateUntilFixpoint() {
        clear();
        WeakTopologicalOrdering weakTopologicalOrdering = new WeakTopologicalOrdering(cfgGraph);
        logger.logToFile("Weak Topological Ordering for given graph: ");
        for (WtoComponent component : weakTopologicalOrdering.getComponents()) {
            logger.logToFile(component.toString());
        }

        for (WtoComponent component : weakTopologicalOrdering.getComponents()) {
            analyzeComponent(component);
        }

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
        logger.logToFile("Analyzing vertex: " + vertex);
        Node node = vertex.block().getBeginNode();
        if (node == cfgGraph.graph.start()) {
            abstractStateMap.setPrecondition(node, initialDomain);
        }

        transferFunction.analyzeBlock(vertex.block(), abstractStateMap);
    }

    private void analyzeCycle(WtoCycle cycle) {
        logger.logToFile("Analyzing cycle: " + cycle);
        Node head = cycle.block().getBeginNode();
        boolean iterate = true;

        while (iterate) {
            /* Analyze the nodes inside outermost block */
            transferFunction.analyzeBlock(cycle.block(), abstractStateMap);

            /* Analyze all other nested WtoComponents */
            for (WtoComponent component : cycle.components()) {
                analyzeComponent(component);
            }

            /*
             * At this point we analyzed the body of the cycle,
             * we look at the head of the cycle by collecting invariants from predecessors
             * and checking if the pre-condition at the head of the cycle changed.
             */
            transferFunction.collectInvariantsFromCfgPredecessors(head, abstractStateMap);
            if (abstractStateMap.getPreCondition(head).leq(abstractStateMap.getPostCondition(head))) {
                iterate = false;
            } else {
                extrapolate(head);
            }
        }
    }
}