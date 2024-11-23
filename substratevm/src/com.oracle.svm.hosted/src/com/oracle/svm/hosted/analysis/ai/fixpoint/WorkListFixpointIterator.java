package com.oracle.svm.hosted.analysis.ai.fixpoint;

import com.oracle.svm.hosted.analysis.ai.checker.rule.CheckerRule;
import com.oracle.svm.hosted.analysis.ai.checker.rule.CheckerRuleResult;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.transfer.TransferFunction;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.AbstractEndNode;
import jdk.graal.compiler.nodes.ControlSplitNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.StructuredGraph;

import java.util.LinkedList;
import java.util.Queue;

/**
 * Represents a very basic sequential inter procedural fixpoint iterator
 * that uses a work list to iterate over the graph
 *
 * @param <Domain> type of the derived AbstractDomain
 */
public class WorkListFixpointIterator<
        Domain extends AbstractDomain<Domain>>
        implements FixpointIterator<Domain> {

    private final StructuredGraph graph;
    private final IteratorPolicy policy;
    private final TransferFunction<Domain> transferFunction;
    private final CheckerRule<Domain> rule;
    private final DebugContext debug;
    private final Environment<Domain> environment;

    public WorkListFixpointIterator(StructuredGraph graph,
                                    IteratorPolicy policy,
                                    TransferFunction<Domain> transferFunction,
                                    CheckerRule<Domain> rule,
                                    Domain initialDomain,
                                    DebugContext debug) {
        this.graph = graph;
        this.policy = policy;
        this.transferFunction = transferFunction;
        this.rule = rule;
        this.environment = new Environment<>(initialDomain, graph.getNodeCount());
        this.debug = debug;
    }

    public WorkListFixpointIterator(StructuredGraph graph,
                                    TransferFunction<Domain> transferFunction,
                                    CheckerRule<Domain> rule,
                                    Domain initialDomain,
                                    DebugContext debug) {
        this.graph = graph;
        this.policy = IteratorPolicy.DEFAULT;
        this.transferFunction = transferFunction;
        this.rule = rule;
        this.environment = new Environment<>(initialDomain, graph.getNodeCount());
        this.debug = debug;
    }

    public Environment<Domain> iterateUntilFixpoint() {
        Queue<Node> workList = new LinkedList<>();
        // We will only iterate over controlFlowNodes -> FixedNodes // TODO: consult this
        graph.getNodes().filter(FixedNode.class::isInstance).forEach(workList::add);

        while (!workList.isEmpty()) {
            try {
                var node = workList.poll();
                debug.log("\t" + "Analyzing node: " + node);
                mergeAllIncomingNodes(node, environment);
                debug.log("\t" + "Domain postcondition: " + environment.getPostCondition(node));
                debug.log("\t" + "Domain precondition: " + environment.getPreCondition(node));
                var postCondition = transferFunction.computePostCondition(node, environment);
                debug.log("\t" + "Domain postcondition: " + environment.getPostCondition(node));
                debug.log("\t" + "Domain precondition: " + environment.getPreCondition(node));
                debug.log("\t" + "computed precondition: " + postCondition);
                if (!postCondition.equals(environment.getPreCondition(node))) {
                    updateNodeState(node);
                    debug.log("\t" + "node has changed " + node);
                    addSuccessorsToWorkList(node, workList);
                }

                var ruleResult = rule.evaluateRule(node, environment.getPostCondition(node));
                if (ruleResult == CheckerRuleResult.ERROR) {
                    debug.log("\t", "Encountering ERROR, stopping the analysis");
                    return environment;
                }
            } catch (Exception e) {
                debug.log("\t" + e.getMessage());
                return environment;
            }
        }
        return environment;
    }

    private void updateNodeState(Node node) {
        var state = environment.getState(node);
        state.incrementVisitedCount();
        int visitedAmount = environment.getState(node).getVisitedCount();
        if (visitedAmount < policy.maxJoinIterations()) {
            environment.getPreCondition(node).joinWith(environment.getPostCondition(node));
        } else {
            environment.getPreCondition(node).widenWith(environment.getPostCondition(node));
        }

        environment.clearPostCondition(node);

        if (state.getVisitedCount() > policy.maxWidenIterations()) {
            throw new RuntimeException("Exceeded maxWidenIterations!" +
                    " Consider increasing the limit, or refactor your widening operator");
        }
    }

    /**
     * Collect all the abstract domains from CFG predecessors, modifying the domain of {@code node}
     *
     * @param node        the node we want to merge incoming domains for
     * @param environment the current environment
     */
    private void mergeAllIncomingNodes(Node node, Environment<Domain> environment) {
        for (Node pred : node.cfgPredecessors()) {
            if (pred instanceof ControlSplitNode) {
                continue;
            }
            environment.getPostCondition(node).joinWith(environment.getPreCondition(pred));
        }
    }

    private void addSuccessorsToWorkList(Node node, Queue<Node> workList) {
        if (node instanceof AbstractEndNode) {
            for (var pred : node.inputs()) {
                workList.add(pred);
            }
            return;
        }

        for (var succ : node.cfgSuccessors()) {
            workList.add(succ);
        }
    }
}