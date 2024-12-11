package com.oracle.svm.hosted.analysis.ai.interpreter;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.policy.IteratorPolicy;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import com.oracle.svm.hosted.analysis.ai.fixpoint.summary.FixpointCache;
import com.oracle.svm.hosted.analysis.ai.interpreter.call.CallInterpreter;
import com.oracle.svm.hosted.analysis.ai.interpreter.node.NodeInterpreter;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.ControlSplitNode;
import jdk.graal.compiler.nodes.InvokeNode;
import jdk.graal.compiler.nodes.cfg.HIRBlock;

/**
 * Transfer function for abstract interpretation.
 * This class is responsible for performing semantic transformation from original semantics
 * into an abstract domain semantics.
 *
 * @param <Domain> type of the derived {@link AbstractDomain}
 */
public final class TransferFunction<Domain extends AbstractDomain<Domain>> {

    private final NodeInterpreter<Domain> nodeInterpreter;
    private final CallInterpreter<Domain> callInterpreter;
    private final Domain initialDomain;
    private final IteratorPolicy policy;
    private final DebugContext debug;


    public TransferFunction(NodeInterpreter<Domain> nodeInterpreter, CallInterpreter<Domain> callInterpreter, Domain initialDomain, IteratorPolicy policy, DebugContext debug) {
        this.nodeInterpreter = nodeInterpreter;
        this.callInterpreter = callInterpreter;
        this.initialDomain = initialDomain;
        this.policy = policy;
        this.debug = debug;
    }

    /**
     * Perform semantic transformation of the given {@link Node},
     * NOTE: This method should modify the postCondition of node directly to avoid creating copies.
     *
     * @param node             to analyze
     * @param abstractStateMap current state of the analysis
     */
    public void analyzeNode(Node node, AbstractStateMap<Domain> abstractStateMap, FixpointCache<Domain> fixpointCache) {
        debug.log("Analyzing node: " + node);
        collectInvariantsFromPredecessors(node, abstractStateMap);
        if (node instanceof InvokeNode invokeNode) {
            callInterpreter.execInvoke(invokeNode, abstractStateMap, fixpointCache, this, initialDomain, policy, debug);
        } else {
            nodeInterpreter.execNode(node, abstractStateMap);
        }
    }

    /**
     * Perform semantic transformation of an edge between two {@link Node}s.
     * This is done because some nodes, like {@link ControlSplitNode},
     * have multiple outgoing edges and the destination node can't just simply join with the abstract domain of the source node.
     * NOTE: This method should modify the precondition of the destination node directly, to avoid creating copies.
     *
     * @param sourceNode       the node from which the edge originates
     * @param destinationNode  the node to which the edge goes
     * @param abstractStateMap current state of the analysis
     */
    public void analyzeEdge(Node sourceNode, Node destinationNode, AbstractStateMap<Domain> abstractStateMap) {
        nodeInterpreter.execEdge(sourceNode, destinationNode, abstractStateMap);
    }

    /**
     * Collect invariants from predecessors of the given node.
     * NOTE: This method should modify the precondition of the destination node directly, to avoid creating copies.
     *
     * @param destinationNode  the node to which the edge goes
     * @param abstractStateMap current state of the analysis
     */
    public void collectInvariantsFromPredecessors(Node destinationNode, AbstractStateMap<Domain> abstractStateMap) {
        for (Node predecessor : destinationNode.cfgPredecessors()) {
            analyzeEdge(predecessor, destinationNode, abstractStateMap);
        }
    }

    public void analyzeEdge(HIRBlock sourceBlock, HIRBlock destinationBlock, AbstractStateMap<Domain> abstractStateMap) {
        for (Node sourceNode : sourceBlock.getNodes()) {
            for (Node destinationNode : destinationBlock.getNodes()) {
                analyzeEdge(sourceNode, destinationNode, abstractStateMap);
            }
        }
    }

    public void analyzeBlock(HIRBlock block, AbstractStateMap<Domain> abstractStateMap, FixpointCache<Domain> fixpointCache) {
        for (Node node : block.getNodes()) {
            analyzeNode(node, abstractStateMap, fixpointCache);
        }
    }
}
