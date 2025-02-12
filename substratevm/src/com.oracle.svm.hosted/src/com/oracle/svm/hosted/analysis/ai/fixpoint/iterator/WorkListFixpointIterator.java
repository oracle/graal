package com.oracle.svm.hosted.analysis.ai.fixpoint.iterator;

import com.oracle.svm.hosted.analysis.ai.analyzer.payload.AnalysisPayload;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import com.oracle.svm.hosted.analysis.ai.interpreter.TransferFunction;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.StructuredGraph;

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

    private final StructuredGraph graph;

    public WorkListFixpointIterator(AnalysisPayload<Domain> payload) {
        super(payload);
        this.graph = cfgGraph.graph;
    }

    @Override
    public AbstractStateMap<Domain> iterateUntilFixpoint() {
        payload.getLogger().logToFile("WorkListFixpointIterator::iterateUntilFixpoint");

        /* We will be using nodes instead of blocks in this fixpoint iterator
         * because this fixpoint is used for demonstration, and this is closer to the pseudocode. */
        Queue<Node> worklist = new LinkedList<>();
        Set<Node> inWorklist = new HashSet<>(); /* nodes that are in the worklist */

        /* Initialize the worklist with control flow nodes */
        graph.getNodes().filter(FixedNode.class::isInstance).forEach(node -> {
            worklist.add(node);
            inWorklist.add(node);
        });

        TransferFunction<Domain> transferFunction = payload.getTransferFunction();

        while (!worklist.isEmpty()) {
            Node current = worklist.poll();
            inWorklist.remove(current);

            /* Analyze the node */
            transferFunction.analyzeNode(current, abstractStateMap, payload);

            /* We don't know if we are at a head of a cycle in this iterator, so we always extrapolate (join/widen) */
            extrapolate(current);

            // Add successors to the worklist if their precondition changes
            for (Node successor : current.cfgSuccessors()) {
                Domain oldPreCondition = abstractStateMap.getPreCondition(successor).copyOf();
                transferFunction.collectInvariantsFromPredecessors(successor, abstractStateMap);

                if (!oldPreCondition.leq(abstractStateMap.getPreCondition(successor))) {
                    if (!inWorklist.contains(successor)) {
                        worklist.add(successor);
                        inWorklist.add(successor);
                    }
                }
            }
        }

        payload.getCheckerManager().checkAll(abstractStateMap);
        return abstractStateMap;
    }

}