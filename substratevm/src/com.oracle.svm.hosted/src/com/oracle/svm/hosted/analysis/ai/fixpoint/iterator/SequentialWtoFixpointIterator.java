package com.oracle.svm.hosted.analysis.ai.fixpoint.iterator;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.analyzer.payload.IteratorPayload;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import com.oracle.svm.hosted.analysis.ai.fixpoint.wto.WeakTopologicalOrdering;
import com.oracle.svm.hosted.analysis.ai.fixpoint.wto.WtoComponent;
import com.oracle.svm.hosted.analysis.ai.fixpoint.wto.WtoCycle;
import com.oracle.svm.hosted.analysis.ai.fixpoint.wto.WtoVertex;
import com.oracle.svm.hosted.analysis.ai.interpreter.TransferFunction;
import com.oracle.svm.hosted.analysis.ai.util.GraphUtil;
import com.oracle.svm.hosted.analysis.ai.util.LoggerVerbosity;
import jdk.graal.compiler.debug.DebugContext;
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

    private final WeakTopologicalOrdering weakTopologicalOrdering;

    public SequentialWtoFixpointIterator(AnalysisMethod method,
                                         DebugContext debug,
                                         Domain initialDomain,
                                         TransferFunction<Domain> transferFunction,
                                         IteratorPayload iteratorPayload) {

        super(method, debug, initialDomain, transferFunction, iteratorPayload);
        if (iteratorPayload.containsMethodWto(method)) {
            this.weakTopologicalOrdering = iteratorPayload.getMethodWtoMap().get(method);
        } else {
            logger.log("Computing Weak Topological Ordering for " + method.getQualifiedName(), LoggerVerbosity.DEBUG);
            this.weakTopologicalOrdering = new WeakTopologicalOrdering(graphTraversalHelper);
            iteratorPayload.addToMethodWtoMap(method, weakTopologicalOrdering);
        }
        logger.log("Weak Topological Ordering for " + method.getQualifiedName() + ": ", LoggerVerbosity.DEBUG);
        logger.log(weakTopologicalOrdering.toString(), LoggerVerbosity.DEBUG);
    }

    @Override
    public AbstractStateMap<Domain> iterateUntilFixpoint() {
        logger.log("Starting sequential WTO fixpoint iteration", LoggerVerbosity.DEBUG);
        for (WtoComponent component : weakTopologicalOrdering.getComponents()) {
            analyzeComponent(component);
        }

        logger.log("Sequential WTO fixpoint iteration finished", LoggerVerbosity.DEBUG);
        GraphUtil.printInferredGraph(iteratorPayload.getMethodGraph().get(analysisMethod).graph, analysisMethod, abstractStateMap);
        return abstractStateMap;
    }

    private void analyzeComponent(WtoComponent component) {
        logger.log("Analyzing component: " + component.toString(), LoggerVerbosity.DEBUG);
        if (component instanceof WtoVertex vertex) {
            analyzeVertex(vertex);
        } else if (component instanceof WtoCycle cycle) {
            analyzeCycle(cycle);
        }
    }

    private void analyzeVertex(WtoVertex vertex) {
        logger.log("Analyzing vertex: " + vertex.toString(), LoggerVerbosity.DEBUG);
        Node node = graphTraversalHelper.getBeginNode(vertex.block());
        if (node == graphTraversalHelper.getGraphStart()) {
            abstractStateMap.setPreCondition(node, initialDomain);
        }
        transferFunction.analyzeBlock(vertex.block(), abstractStateMap, graphTraversalHelper);
    }

    private void analyzeCycle(WtoCycle cycle) {
        logger.log("Analyzing cycle: " + cycle.toString(), LoggerVerbosity.DEBUG);
        Node head = graphTraversalHelper.getBeginNode(cycle.block());
        boolean iterate = true;

        while (iterate) {
            /* Analyze the nodes inside outermost block */
            transferFunction.analyzeBlock(cycle.block(), abstractStateMap, graphTraversalHelper);

            /* Analyze all other nested WtoComponents */
            for (WtoComponent component : cycle.components()) {
                analyzeComponent(component);
            }

            /*
             * At this point we analyzed the body of the cycle,
             * we look at the head of the cycle by collecting invariants from predecessors
             * and checking if the pre-condition at the head of the cycle changed.
             */
            transferFunction.collectInvariantsFromPredecessors(head, abstractStateMap, graphTraversalHelper);
            if (abstractStateMap.getPreCondition(head).leq(abstractStateMap.getPostCondition(head))) {
                iterate = false;
            } else {
                extrapolate(head);
            }
        }
    }
}
