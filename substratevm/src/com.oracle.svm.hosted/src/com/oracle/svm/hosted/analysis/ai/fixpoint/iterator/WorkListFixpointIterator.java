package com.oracle.svm.hosted.analysis.ai.fixpoint.iterator;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.analyzer.metadata.AnalysisContext;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import com.oracle.svm.hosted.analysis.ai.interpreter.AbstractTransformer;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.FixedNode;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

/**
 * Represents the most basic type of fixpoint iterator that uses a worklist to iterate until a fixpoint is reached.
 * It may still be useful for some debugging purposes, like comparing the result from this iterator with other types of iterators.
 *
 * @param <Domain> the type of derived {@link AbstractDomain} the analysis is running on
 */
public final class WorkListFixpointIterator<Domain extends AbstractDomain<Domain>> extends FixpointIteratorBase<Domain> {

    public WorkListFixpointIterator(AnalysisMethod method,
                                    Domain initialDomain,
                                    AbstractTransformer<Domain> abstractTransformer,
                                    AnalysisContext analysisContext) {
        super(method, initialDomain, abstractTransformer, analysisContext);
    }

    @Override
    public AbstractState<Domain> doRunFixpointIteration() {
        logger.log("Starting WorkList fixpoint iteration of analysisMethod: " + analysisMethod, LoggerVerbosity.INFO);
        Queue<Node> worklist = new LinkedList<>();
        Set<Node> inWorklist = new HashSet<>(); /* nodes that are in the worklist */

        Iterable<Node> nodes = graphTraversalHelper.getNodes();
        for (Node node : nodes) {
            if (node instanceof FixedNode) {
                worklist.add(node);
                inWorklist.add(node);
            }
        }

        int iteration = 0;
        while (!worklist.isEmpty()) {
            if (iteration == 0 || worklist.size() == 1) {
                iteratorContext.incrementGlobalIteration();
                iteration++;
            }

            Node current = worklist.poll();
            inWorklist.remove(current);
            abstractTransformer.analyzeNode(current, abstractState, iteratorContext);
            extrapolate(current);

            /* Add successors to the worklist if their precondition changes */
            for (Node successor : graphTraversalHelper.getNodeCfgSuccessors(current)) {
                Domain oldPreCondition = abstractState.getPreCondition(successor).copyOf();
                abstractTransformer.analyzeEdge(current, successor, abstractState, iteratorContext);

                if (!oldPreCondition.leq(abstractState.getPreCondition(successor))) {
                    if (!inWorklist.contains(successor)) {
                        worklist.add(successor);
                        inWorklist.add(successor);
                    }
                }
            }
        }

        iteratorContext.setConverged(true);
        logger.log("Finished WorkList fixpoint iteration of analysisMethod: " + analysisMethod, LoggerVerbosity.INFO);
        return abstractState;
    }
}
