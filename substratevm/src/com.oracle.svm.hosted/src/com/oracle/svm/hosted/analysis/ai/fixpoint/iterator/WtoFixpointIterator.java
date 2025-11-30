package com.oracle.svm.hosted.analysis.ai.fixpoint.iterator;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.analyzer.metadata.AnalysisContext;
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
                               AnalysisContext analysisContext) {

        super(method, initialDomain, abstractTransformer, analysisContext);
        var cache = analysisContext.getMethodGraphCache();
        if (cache.containsMethodWto(method)) {
            this.weakTopologicalOrdering = cache.getMethodWtoMap().get(method);
        } else {
            logger.log("Computing Weak Topological Ordering for " + method.getQualifiedName(), LoggerVerbosity.DEBUG);
            logger.log("Using AnalysisDirection:" + analysisContext.getIteratorPolicy().direction(), LoggerVerbosity.INFO);

            this.weakTopologicalOrdering = new WeakTopologicalOrdering(graphTraversalHelper);
            cache.addToMethodWtoMap(method, weakTopologicalOrdering);
        }
        logger.log("Weak Topological Ordering for " + method.getQualifiedName() + ": ", LoggerVerbosity.DEBUG);
        logger.log(weakTopologicalOrdering.toString(), LoggerVerbosity.DEBUG);
    }

    @Override
    public AbstractState<Domain> doRunFixpointIteration() {
        logger.log("Starting WTO fixpoint iteration of method: " + analysisMethod.getName(), LoggerVerbosity.INFO);
        iteratorContext.reset();
        assert graph != null;

        for (WtoComponent component : weakTopologicalOrdering.getComponents()) {
            iteratorContext.incrementGlobalIteration();
            analyzeComponent(component);
        }

        iteratorContext.setConverged(true);
        logger.log("Finished WTO fixpoint iteration of method: " + analysisMethod.getName(), LoggerVerbosity.INFO);
        return abstractState;
    }

    private void analyzeComponent(WtoComponent component) {
        logger.log("Analyzing component: " + component, LoggerVerbosity.DEBUG);
        if (component instanceof WtoVertex vertex) {
            analyzeVertex(vertex);
        } else if (component instanceof WtoCycle cycle) {
            analyzeCycle(cycle);
        }
    }

    private void analyzeVertex(WtoVertex vertex) {
        logger.log("Analyzing vertex: " + vertex, LoggerVerbosity.DEBUG);
        Node node = graphTraversalHelper.getBeginNode(vertex.block());

        /* Track node visits (used for isFirstVisit and widening decisions) */
        iteratorContext.incrementNodeVisitCount(node);
        if (node == graphTraversalHelper.getGraphStart() && !abstractState.hasNode(node)) {
            abstractState.setPreCondition(node, initialDomain);
        }
        iteratorContext.setCurrentBlock(vertex.block());
        abstractTransformer.analyzeBlock(vertex.block(), abstractState, iteratorContext);
    }

    private void analyzeCycle(WtoCycle cycle) {
        logger.log("Analyzing cycle: " + cycle, LoggerVerbosity.DEBUG);
        boolean iterate = true;
        Node headBegin = cycle.head().getBeginNode();

        if (iteratorContext.isLoopHeader(headBegin)) {
            iteratorContext.incrementLoopIteration(headBegin);
        }

        iteratorContext.setPhase(IteratorPhase.WIDENING);

        while (iterate) {
            int visitCount = iteratorContext.getNodeVisitCount(headBegin);
            logger.log("Loop iteration (visit count: " + visitCount + ") for cycle: " + cycle, LoggerVerbosity.DEBUG);

            // Ensure a pre-condition exists
            Domain currentPre = abstractState.getPreCondition(headBegin);
            if (currentPre == null) {
                abstractState.setPreCondition(headBegin, initialDomain.copyOf());
                currentPre = abstractState.getPreCondition(headBegin);
            }
            Domain oldPreCondition = currentPre.copyOf();

            iteratorContext.setCurrentBlock(cycle.head());
            abstractTransformer.analyzeBlock(cycle.head(), abstractState, iteratorContext);

            // Analyze nested components inside the cycle body (excluding head already handled)
            for (WtoComponent component : cycle.components()) {
                analyzeComponent(component);
            }

            Domain postCondition = abstractState.getPostCondition(headBegin);
            if (postCondition == null) {
                // Defensive: if transformer failed to set a post condition, treat as TOP and converge
                logger.log("Post-condition missing for loop head " + headBegin + ", forcing convergence", LoggerVerbosity.INFO);
                abstractState.setPostCondition(headBegin, oldPreCondition.copyOf());
                break;
            }

            logger.log("Visit " + visitCount + ": old pre = " + oldPreCondition + ", post = " + postCondition, LoggerVerbosity.DEBUG);

            // Convergence criteria:
            // 1. Exact equality (standard)
            // 2. postCondition <= oldPreCondition (domain order) after widening: stable over-approximation
            boolean equal = oldPreCondition.equals(postCondition);
            boolean dominated = postCondition.leq(oldPreCondition);
            if (equal || dominated) {
                logger.log("Loop converged (" + (equal ? "equal" : "post ⊑ oldPre") + ") after " + (visitCount + 1) + " visits", LoggerVerbosity.DEBUG);
                iterate = false;
            } else {
                logger.log("No convergence yet (post !<= oldPre). Extrapolating.", LoggerVerbosity.DEBUG);
                extrapolate(headBegin);
                if (iteratorContext.isLoopHeader(headBegin)) {
                    iteratorContext.incrementLoopIteration(headBegin);
                }
            }
        }

        iteratorContext.setPhase(IteratorPhase.ASCENDING);
    }
}
