package com.oracle.svm.hosted.analysis.ai.interpreter;

import com.oracle.svm.hosted.analysis.ai.analyzer.call.InvokeCallBack;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.GraphTraversalHelper;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.cfg.HIRBlock;

/**
 * Represents the transfer functions used in abstract interpretation.
 * This class is responsible for applying abstract operations corresponding to the semantics
 * of GraalIR nodes and edges, effectively transforming the abstract state as the analysis progresses.
 * It uses an {@link AbstractInterpreter} to define the specific abstract semantics for each node type
 * and an {@link InvokeCallBack} to handle method invocations.
 *
 * @param <Domain> The type of the abstract domain used in the analysis.
 */
public record AbstractTransformers<Domain extends AbstractDomain<Domain>>(
        AbstractInterpreter<Domain> abstractInterpreter,
        InvokeCallBack<Domain> analyzeDependencyCallback) {

    /**
     * Performs semantic transformation of the given {@link Node}, while modifying the post-condition of {@code node}
     *
     * @param node          to analyze
     * @param abstractState current abstract state during fixpoint iteration
     */
    public void analyzeNode(Node node, AbstractState<Domain> abstractState) {
        if (abstractState.getState(node).isRestrictedFromExecution()) {
            return;
        }
        abstractInterpreter.execNode(node, abstractState, analyzeDependencyCallback);
    }

    /**
     * Performs semantic transformation of an edge between two {@link Node}s. while modifying the pre-condition of {@code target}
     *
     * @param source        the node from which the edge originates
     * @param target        the node to which the edge goes
     * @param abstractState abstract state during fixpoint iteration
     */
    public void analyzeEdge(Node source, Node target, AbstractState<Domain> abstractState) {
        if (abstractState.getState(source).isRestrictedFromExecution()) {
            abstractState.getState(target).markRestrictedFromExecution();
            return;
        }

        abstractInterpreter.execEdge(source, target, abstractState);
    }

    /**
     * Collect post-conditions from the CFG predecessors of the given {@link HIRBlock}.
     * NOTE: This analysisMethod should modify the precondition of the destination node directly, to avoid creating copies.
     *
     * @param block         the {@link HIRBlock} into which we are merging invariants
     * @param abstractState abstract state during fixpoint iteration
     */
    public void collectInvariantsFromCfgPredecessors(HIRBlock block,
                                                     AbstractState<Domain> abstractState,
                                                     GraphTraversalHelper graphTraversalHelper) {
        Node blockBeginNode = graphTraversalHelper.getBeginNode(block);
        for (int i = 0; i < graphTraversalHelper.getPredecessorCount(block); i++) {
            HIRBlock predecessor = block.getPredecessorAt(i);
            Node predecessorEndNode = graphTraversalHelper.getEndNode(predecessor);
            analyzeEdge(predecessorEndNode, blockBeginNode, abstractState);
        }
    }

    /**
     * Performs semantic transformation of an edge between two {@link HIRBlock}s.
     *
     * @param sourceBlock      the head from which the edge originates
     * @param destinationBlock the head to which the edge goes
     * @param abstractState    abstract state during fixpoint iteration
     */
    public void analyzeEdge(HIRBlock sourceBlock, HIRBlock destinationBlock, AbstractState<Domain> abstractState) {
        for (Node sourceNode : sourceBlock.getNodes()) {
            for (Node destinationNode : destinationBlock.getNodes()) {
                analyzeEdge(sourceNode, destinationNode, abstractState);
            }
        }
    }

    /**
     * Performs semantic transformation of the given {@link HIRBlock}.
     *
     * @param block         the head to analyze
     * @param abstractState abstract state during fixpoint iteration
     */
    public void analyzeBlock(HIRBlock block,
                             AbstractState<Domain> abstractState,
                             GraphTraversalHelper graphTraversalHelper) {
        AbstractInterpretationLogger logger = AbstractInterpretationLogger.getInstance();
        logger.log("Analyzing block: " + block, LoggerVerbosity.DEBUG);
        for (Node node : block.getNodes()) {
            analyzeEdge(graphTraversalHelper.getNodePredecessor(node), node, abstractState);
            analyzeNode(node, abstractState);
        }
    }
}
