package com.oracle.svm.hosted.analysis.ai.fixpoint.iterator;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.analyzer.payload.IteratorPayload;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import com.oracle.svm.hosted.analysis.ai.interpreter.AbstractTransformers;
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
                                    AbstractTransformers<Domain> abstractTransformers,
                                    IteratorPayload iteratorPayload) {
        super(method, initialDomain, abstractTransformers, iteratorPayload);
    }

    @Override
    public AbstractState<Domain> iterateUntilFixpoint() {
        logger.log("Starting WorkList fixpoint iteration of analysisMethod: " + analysisMethod, LoggerVerbosity.INFO);
        Queue<Node> worklist = new LinkedList<>();
        Set<Node> inWorklist = new HashSet<>(); /* nodes that are in the worklist */

        /* Initialize the worklist with control flow nodes */
        Iterable<Node> nodes = graphTraversalHelper.getNodes();
        for (Node node : nodes) {
            if (node instanceof FixedNode) {
                worklist.add(node);
                inWorklist.add(node);
            }
        }

        while (!worklist.isEmpty()) {
            Node current = worklist.poll();
            inWorklist.remove(current);
            abstractTransformers.analyzeNode(current, abstractState);

            /* We don't know if we are at a head of a cycle in this iterator, so we always extrapolate (join/widen) */
            extrapolate(current);

            // Add successors to the worklist if their precondition changes
            for (Node successor : graphTraversalHelper.getNodeCfgSuccessors(current)) {
                Domain oldPreCondition = abstractState.getPreCondition(successor).copyOf();
                abstractTransformers.analyzeEdge(current, successor, abstractState);

                if (!oldPreCondition.leq(abstractState.getPreCondition(successor))) {
                    if (!inWorklist.contains(successor)) {
                        worklist.add(successor);
                        inWorklist.add(successor);
                    }
                }
            }
        }

        logger.log("Finished WorkList fixpoint iteration of analysisMethod: " + analysisMethod, LoggerVerbosity.INFO);
        logger.printLabelledGraph(iteratorPayload.getMethodGraph().get(analysisMethod).graph, analysisMethod, abstractState);
        return abstractState;
    }
}
