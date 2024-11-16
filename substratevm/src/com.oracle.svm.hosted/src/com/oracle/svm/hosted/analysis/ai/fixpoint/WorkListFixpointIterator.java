package com.oracle.svm.hosted.analysis.ai.fixpoint;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.policy.IteratorPolicy;
import com.oracle.svm.hosted.analysis.ai.transfer.TransferFunction;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.StructuredGraph;

import java.util.LinkedList;
import java.util.Queue;

/**
 * Represents a fixpoint iterator that uses a worklist to iterate over the graph
 *
 * @param <Domain> type of the derived AbstractDomain
 */
public class WorkListFixpointIterator<
        Domain extends AbstractDomain<Domain>>
        implements FixpointIterator<Domain> {
    private final StructuredGraph graph;
    private final TransferFunction<Domain> transferFunction;
    private final IteratorPolicy policy;
    private final DebugContext debug;
    private final Environment<Domain> environment;

    public WorkListFixpointIterator(StructuredGraph graph,
                                    TransferFunction<Domain> transferFunction,
                                    IteratorPolicy policy,
                                    Domain initialDomain,
                                    DebugContext debug) {
        this.graph = graph;
        this.transferFunction = transferFunction;
        this.policy = policy;
        this.environment = new Environment<>(initialDomain, graph.getNodeCount());
        this.debug = debug;
    }

    public Environment<Domain> iterateUntilFixpoint() {
        Queue<Node> workList = new LinkedList<>();
        for (Node node : graph.getNodes()) {
            workList.add(node);
        }

        while (!workList.isEmpty()) {
            Node node = workList.poll();
            Domain newDomain = transferFunction.analyzeNode(node, environment);
            updateNodeAfterVisit(node);
            debug.log("\t" + node);
            debug.log("\t" + newDomain.toString());

            for (Node successor : node.successors()) {
                if (stateChanged(successor, newDomain)) {
                    workList.add(successor);
                }
            }
        }
        return environment;
    }

    private boolean stateChanged(Node successor, Domain newDomain) {
        Domain oldDomain = environment.getDomain(successor);
        int visitedAmount = environment.getState(successor).getVisitedCount();
        Domain updatedDomain;

        if (visitedAmount < policy.maxJoinIterations()) {
            updatedDomain = oldDomain.join(newDomain);
            debug.log("\t" + "[JOIN] " + oldDomain + " ⊔ " + newDomain.toString() + " = " + updatedDomain.toString());
        } else {
            updatedDomain = oldDomain.widen(newDomain);
            debug.log("\t" + "[WIDEN] " + oldDomain + " ▿ " + newDomain.toString() + " = " + updatedDomain.toString());
        }

        if (!updatedDomain.equals(oldDomain)) {
            environment.setDomain(successor, updatedDomain);
            return true;
        }
        return false;
    }

    private void updateNodeAfterVisit(Node node) {
        var state = environment.getState(node);
        state.incrementVisitedCount();
        if (state.getVisitedCount() > policy.maxWidenIterations()) {
            throw new RuntimeException("Widening limit exceeded, either check the widening implementation or increase the limit in the policy");
        }
    }
}