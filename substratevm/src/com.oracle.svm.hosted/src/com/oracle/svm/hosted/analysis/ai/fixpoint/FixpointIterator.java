package com.oracle.svm.hosted.analysis.ai.fixpoint;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.transfer.TransferFunction;
import com.oracle.svm.hosted.analysis.ai.transfer.policy.IteratorPolicy;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.truffle.phases.inlining.CallNode;

import java.util.Queue;
import java.util.LinkedList;

/**
 * A fixpoint iterator for dataflow analysis.
 * The analysis is performed on a graph where each node is associated with a domain element.
 *
 * @param <Domain> type of the derived abstract domain
 */

public class FixpointIterator<Domain extends AbstractDomain<Domain>> {
    private final StructuredGraph graph;
    private final TransferFunction<Domain> transferFunction;
    private final IteratorPolicy policy;
    private final DebugContext debug;
    private final Environment<Domain> environment;

    public FixpointIterator(StructuredGraph graph,
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

    public Domain analyze() {
        Queue<Node> workList = new LinkedList<>();
        for (Node node : graph.getNodes()) {
            workList.add(node);
        }

        Domain newDomain = null;
        while (!workList.isEmpty()) {
            Node node = workList.poll();
            if (shouldSkipNode(node)) {
                continue;
            }

            newDomain = transferFunction.analyzeNode(node, environment);
            updateNodeAfterVisit(node);
            debug.log("\t" + node);
            debug.log("\t" + newDomain.toString());

            for (Node successor : node.successors()) {
                if (stateChanged(successor, newDomain)) {
                    workList.add(successor);
                }
            }
        }
        return newDomain;
    }

    private boolean stateChanged(Node successor, Domain newDomain) {
        Domain oldDomain = environment.getDomain(successor);
        int visitedAmount = environment.getState(successor).getVisitedCount();
        Domain updatedDomain;

        if (visitedAmount < policy.getMaxJoinIterations()) {
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
        if (state.getVisitedCount() > policy.getMaxWidenIterations()) {
            throw new RuntimeException("Widening limit exceeded, either check the widening implementation or increase the limit in the policy");
        }
    }

    private boolean shouldSkipNode(Node node) {
        return policy.shouldSkipCallNodes() && node instanceof CallNode;
    }
}