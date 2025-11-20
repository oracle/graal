package com.oracle.svm.hosted.analysis.ai.interpreter;

import com.oracle.svm.hosted.analysis.ai.analyzer.call.InvokeCallBack;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.context.IteratorContext;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.GraphTraversalHelper;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.StartNode;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.cfg.HIRBlock;

/**
 * Represents the transfer functions used in abstract interpretation.
 * This class is responsible for applying abstract operations corresponding to the semantics
 * of Graal IR nodes and edges and transforming the abstract state during an analysis.
 *
 * @param <Domain> type of the derived {@link AbstractDomain} used in the analysis.
 */
public record AbstractTransformer<Domain extends AbstractDomain<Domain>>(
        AbstractInterpreter<Domain> abstractInterpreter, InvokeCallBack<Domain> invokeCallBack) {

    /**
     * Performs semantic transformation of the given {@link Node} with iterator context.
     * For efficiency, it modifies the post-condition of {@param node}.
     *
     * @param node          to analyze
     * @param abstractState current abstract state during fixpoint iteration
     * @param context       context information from the fixpoint iterator
     */
    public void analyzeNode(Node node, AbstractState<Domain> abstractState, IteratorContext context) {
        abstractInterpreter.execNode(node, abstractState, invokeCallBack, context);
    }

    /**
     * Performs semantic transformation of an edge between two {@link Node}s with iterator context.
     * For efficiency, it modifies the pre-condition of the {@param target} Node.
     *
     * @param source        the node from which the edge originates
     * @param target        the node to which the edge goes
     * @param abstractState abstract state during fixpoint iteration
     * @param context       context information from the fixpoint iterator
     */
    public void analyzeEdge(Node source, Node target, AbstractState<Domain> abstractState, IteratorContext context) {
        abstractInterpreter.execEdge(source, target, abstractState, context);
    }

    /**
     * Collects invariants from all CFG predecessors of the given {@link Node}.
     *
     * @param node          the node whose predecessors to analyze
     * @param abstractState abstract state during fixpoint iteration
     * @param context       context information from the fixpoint iterator
     */
    public void collectInvariantsFromCfgPredecessors(Node node, AbstractState<Domain> abstractState, IteratorContext context) {
        AbstractInterpretationLogger logger = AbstractInterpretationLogger.getInstance();
        GraphTraversalHelper graphTraversalHelper = context.getGraphTraversalHelper();
        HIRBlock currentBlock = context.getBlockForNode(node);

        int predCount = graphTraversalHelper.getPredecessorCount(currentBlock);
        logger.log("Collecting invariants from " + predCount + " CFG predecessors for node: " + node,
                LoggerVerbosity.DEBUG);

        for (int i = 0; i < predCount; i++) {
            HIRBlock predBlock = graphTraversalHelper.getPredecessorAt(currentBlock, i);
            Node predecessor = graphTraversalHelper.getEndNode(predBlock);
            context.setEdgeTraversal(predBlock, currentBlock);
            logger.log("  Processing edge: " + predBlock + " -> " + currentBlock, LoggerVerbosity.DEBUG);
            if (predecessor != null) {
                analyzeEdge(predecessor, node, abstractState, context);
            }
        }
    }

    /**
     * Performs semantic transformation of given {@link HIRBlock} with given iterator context.
     * @param block the block to analyze
     * @param abstractState the current abstract state before analyzing this block
     * @param context the current iterator context before analyzing this block
     */
    public void analyzeBlock(HIRBlock block, AbstractState<Domain> abstractState, IteratorContext context) {
        AbstractInterpretationLogger logger = AbstractInterpretationLogger.getInstance();
        logger.log("Analyzing block: " + block, LoggerVerbosity.DEBUG);

        Node previousNode = null;
        Node first = block.getBeginNode();

        if (context.shouldCollectPredecessors(first)) {
            collectInvariantsFromCfgPredecessors(first, abstractState, context);
        }

        boolean skippingBlock = shouldSkipBlock(first, abstractState.getPreCondition(first));
        logger.log("SkippingBLock: " + skippingBlock, LoggerVerbosity.DEBUG);

        for (Node node : block.getNodes()) {
            if (previousNode != null) {
                Domain prevPost = abstractState.getPostCondition(previousNode);
                /* Inside a block the abstract state flows trivially */
                abstractState.setPreCondition(node, prevPost);
            }

            Domain pre = abstractState.getPreCondition(node);
            if (skippingBlock) {
                abstractState.setPostCondition(node, pre);
                logger.log("  Skipping unreachable node: " + node, LoggerVerbosity.DEBUG);
                previousNode = node;
                continue;
            }

            analyzeNode(node, abstractState, context);
            previousNode = node;
        }
    }

    private boolean shouldSkipBlock(Node first, Domain preCondition) {
        var logger = AbstractInterpretationLogger.getInstance();
        return preCondition.isBot() && !(first instanceof StartNode);
    }
}
