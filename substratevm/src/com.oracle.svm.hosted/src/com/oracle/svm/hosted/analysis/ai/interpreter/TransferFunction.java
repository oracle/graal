package com.oracle.svm.hosted.analysis.ai.interpreter;

import com.oracle.svm.hosted.analysis.ai.analyzer.call.InvokeCallBack;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.ControlSplitNode;
import jdk.graal.compiler.nodes.cfg.HIRBlock;

/**
 * Transfer function for abstract interpretation.
 * This class is responsible for performing semantic transformation from original semantics
 * into an abstract domain semantics.
 *
 * @param <Domain> type of the derived {@link AbstractDomain} used in the analysis
 */
public record TransferFunction<Domain extends AbstractDomain<Domain>>(NodeInterpreter<Domain> nodeInterpreter,
                                                                      InvokeCallBack<Domain> analyzeDependencyCallback) {

    /**
     * Perform semantic transformation of the given {@link Node},
     *
     * @param node             to analyze
     * @param abstractStateMap current state of the analysis
     *
     * @return the {@link AbstractDomain} after analyzing the node
     */
    public Domain analyzeNode(Node node, AbstractStateMap<Domain> abstractStateMap) {
        Domain result = nodeInterpreter.execNode(node, abstractStateMap, analyzeDependencyCallback);
        abstractStateMap.incrementVisitCount(node);
        return result;
    }

    /**
     * Perform semantic transformation of an edge between two {@link Node}s.
     * This is done because some nodes, like {@link ControlSplitNode},
     * have multiple outgoing edges and the destination node can't just simply join with the abstract domain of the source node.
     * NOTE: This analysisMethod should modify the precondition of the destination node directly, to avoid creating copies.
     *
     * @param sourceNode       the node from which the edge originates
     * @param destinationNode  the node to which the edge goes
     * @param abstractStateMap current state of the analysis
     */
    public void analyzeEdge(Node sourceNode, Node destinationNode, AbstractStateMap<Domain> abstractStateMap) {
        nodeInterpreter.execEdge(sourceNode, destinationNode, abstractStateMap);
    }

    /**
     * Collect invariants from CFG predecessors of the given node (joining incoming states).
     * NOTE: This analysisMethod should modify the precondition of the destination node directly, to avoid creating copies.
     *
     * @param destinationNode  the node to which the edge goes
     * @param abstractStateMap current state of the analysis
     */
    public void collectInvariantsFromPredecessors(Node destinationNode, AbstractStateMap<Domain> abstractStateMap) {
        for (Node predecessor : destinationNode.cfgPredecessors()) {
            analyzeEdge(predecessor, destinationNode, abstractStateMap);
        }
    }

    /**
     * Perform semantic transformation of an edge between two {@link HIRBlock}s.
     *
     * @param sourceBlock the block from which the edge originates
     * @param destinationBlock the block to which the edge goes
     * @param abstractStateMap current state of the analysis
     */
    public void analyzeEdge(HIRBlock sourceBlock, HIRBlock destinationBlock, AbstractStateMap<Domain> abstractStateMap) {
        for (Node sourceNode : sourceBlock.getNodes()) {
            for (Node destinationNode : destinationBlock.getNodes()) {
                analyzeEdge(sourceNode, destinationNode, abstractStateMap);
            }
        }
    }

    /**
     * Perform semantic transformation of the given {@link HIRBlock}.
     *
     * @param block the block to analyze
     * @param abstractStateMap current state of the analysis
     */
    public void analyzeBlock(HIRBlock block, AbstractStateMap<Domain> abstractStateMap) {
        for (Node node : block.getNodes()) {
            collectInvariantsFromPredecessors(node, abstractStateMap);
            analyzeNode(node, abstractStateMap);
        }
    }
}
