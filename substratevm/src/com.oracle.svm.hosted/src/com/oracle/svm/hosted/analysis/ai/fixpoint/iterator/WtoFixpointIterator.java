package com.oracle.svm.hosted.analysis.ai.fixpoint.iterator;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.analyzer.metadata.AnalyzerMetadata;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.context.IteratorPhase;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import com.oracle.svm.hosted.analysis.ai.fixpoint.wto.WeakTopologicalOrdering;
import com.oracle.svm.hosted.analysis.ai.fixpoint.wto.WtoComponent;
import com.oracle.svm.hosted.analysis.ai.fixpoint.wto.WtoCycle;
import com.oracle.svm.hosted.analysis.ai.fixpoint.wto.WtoVertex;
import com.oracle.svm.hosted.analysis.ai.interpreter.AbstractTransformer;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;
import jdk.graal.compiler.graph.Node;

/**
 * Represents a fixpoint iterator based on the Weak Topological Ordering (WTO) algorithm.
 * F. Bourdoncle. Efficient chaotic iteration strategies with widenings.
 * In Formal Methods in Programming and Their Applications, pp 128-141.
 * <a href="https://doi.org/10.1007/BFb0039704"></a>
 *
 * @param <Domain> type of the derived AbstractDomain
 */
public final class WtoFixpointIterator<Domain extends AbstractDomain<Domain>> extends FixpointIteratorBase<Domain> {

    private final WeakTopologicalOrdering weakTopologicalOrdering;

    public WtoFixpointIterator(AnalysisMethod method,
                               Domain initialDomain,
                               AbstractTransformer<Domain> abstractTransformer,
                               AnalyzerMetadata analyzerMetadata) {

        super(method, initialDomain, abstractTransformer, analyzerMetadata);
        if (analyzerMetadata.containsMethodWto(method)) {
            this.weakTopologicalOrdering = analyzerMetadata.getMethodWtoMap().get(method);
        } else {
            logger.log("Computing Weak Topological Ordering for " + method.getQualifiedName(), LoggerVerbosity.DEBUG);
            this.weakTopologicalOrdering = new WeakTopologicalOrdering(graphTraversalHelper);
            analyzerMetadata.addToMethodWtoMap(method, weakTopologicalOrdering);
        }
        logger.log("Weak Topological Ordering for " + method.getQualifiedName() + ": ", LoggerVerbosity.DEBUG);
        logger.log(weakTopologicalOrdering.toString(), LoggerVerbosity.DEBUG);
    }

    @Override
    public AbstractState<Domain> iterateUntilFixpoint() {
        logger.log("Starting WTO fixpoint iteration of analysisMethod: " + analysisMethod, LoggerVerbosity.INFO);
        iteratorContext.reset();

        for (WtoComponent component : weakTopologicalOrdering.getComponents()) {
            iteratorContext.incrementGlobalIteration();
            analyzeComponent(component);
        }

        iteratorContext.setConverged(true);
        logger.log("Finished WTO fixpoint iteration of analysisMethod: " + analysisMethod, LoggerVerbosity.INFO);
        logger.printLabelledGraph(analyzerMetadata.getMethodGraph().get(analysisMethod).graph, analysisMethod, abstractState);
        return abstractState;
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

        // Track node visits (used for isFirstVisit and widening decisions)
        iteratorContext.incrementNodeVisitCount(node);
        iteratorContext.setCurrentBlock(vertex.block());

        if (node == graphTraversalHelper.getGraphStart() && !abstractState.hasNode(node)) {
            abstractState.setPreCondition(node, initialDomain);
        }

        abstractTransformer.analyzeBlock(vertex.block(), abstractState, graphTraversalHelper, iteratorContext);
    }

    private void analyzeCycle(WtoCycle cycle) {
        logger.log("Analyzing cycle: " + cycle.toString(), LoggerVerbosity.DEBUG);
        boolean iterate = true;
        Node headBegin = cycle.head().getBeginNode();

        if (iteratorContext.isLoopHeader(headBegin)) {
            iteratorContext.incrementLoopIteration(headBegin);
        }

        iteratorContext.setPhase(IteratorPhase.WIDENING);
        while (iterate) {
            iteratorContext.setCurrentBlock(cycle.head());

            /* Analyze the nodes inside the outermost head */
            abstractTransformer.analyzeBlock(cycle.head(), abstractState, graphTraversalHelper, iteratorContext);

            /* Analyze all other nested WtoComponents */
            for (WtoComponent component : cycle.components()) {
                analyzeComponent(component);
            }

            /*
             * At this point we analyzed the body of the cycle,
             * we look at the head of the cycle by collecting invariants from predecessors
             * and checking if the pre-condition at the head of the cycle changed.
             */
            abstractTransformer.collectInvariantsFromCfgPredecessors(cycle.head(), abstractState, graphTraversalHelper, iteratorContext);

            if (abstractState.getPreCondition(headBegin).leq(abstractState.getPostCondition(headBegin))) {
                iterate = false;
            } else {
                extrapolate(headBegin);
                if (iteratorContext.isLoopHeader(headBegin)) {
                    iteratorContext.incrementLoopIteration(headBegin);
                }
            }
        }

        iteratorContext.setPhase(IteratorPhase.ASCENDING);
    }
}
