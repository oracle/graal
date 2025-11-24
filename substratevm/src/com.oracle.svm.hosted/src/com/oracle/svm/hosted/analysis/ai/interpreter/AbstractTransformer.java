package com.oracle.svm.hosted.analysis.ai.interpreter;

import com.oracle.svm.hosted.analysis.ai.analyzer.call.InvokeCallBack;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.context.IteratorContext;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.GraphTraversalHelper;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.NodeState;
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
        logger.log("Collecting invariants predecessors for node: " + node, LoggerVerbosity.DEBUG);
        GraphTraversalHelper graphTraversalHelper = context.getGraphTraversalHelper();
        HIRBlock currentBlock = context.getBlockForNode(node);

        /* FIXME: This implementation is kinda retarded, but there is no smarter way to do it, because we don't expect
           implementations of execEdge to set the mark to NORMAL,
           One way we could fix it is by making execEdge return a NodeMark, but that seems like a really bad idea, since
           it makes the implementations a lot more annoying, whereas now execEdge implementation can choose their
           internal logic when they set a target of an edge unreachable, set the mark and we will read it here,
           and then set it back to normal, and evaluate it after
         */

        /* Node is reachable if there is least one edge that reaches this node, or if it is a StartNode in the StructuredGraph*/
        int predecessorCount = graphTraversalHelper.getPredecessorCount(currentBlock);
        NodeState.NodeMark nodeMark = predecessorCount == 0 ? NodeState.NodeMark.NORMAL : NodeState.NodeMark.UNREACHABLE;
        NodeState<Domain> nodeState = abstractState.getNodeState(node);

        for (int i = 0; i < graphTraversalHelper.getPredecessorCount(currentBlock); i++) {
            HIRBlock predBlock = graphTraversalHelper.getPredecessorAt(currentBlock, i);
            Node predecessor = graphTraversalHelper.getEndNode(predBlock);
            context.setEdgeTraversal(predBlock, currentBlock);
            logger.log("  Processing edge: " + predBlock + " -> " + currentBlock, LoggerVerbosity.DEBUG);

            if (predecessor == null || abstractState.getNodeState(predecessor).isUnreachable()) {
                continue;
            }

            nodeState.setMark(NodeState.NodeMark.NORMAL);
            analyzeEdge(predecessor, node, abstractState, context);
            if (nodeState.getMark() == NodeState.NodeMark.NORMAL) {
                nodeMark = NodeState.NodeMark.NORMAL;
            }
        }

        nodeState.setMark(nodeMark);
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

        Node blockBeginNode = block.getBeginNode();
        if (context.shouldCollectPredecessors(blockBeginNode)) {
            collectInvariantsFromCfgPredecessors(blockBeginNode, abstractState, context);
        }

        boolean blockUnreachable = abstractState.getNodeState(blockBeginNode).isUnreachable();
        logger.log("SkippingBlock: " + blockUnreachable, LoggerVerbosity.DEBUG);

        Node previousNode = null;
        for (Node node : block.getNodes()) {
            if (previousNode != null) {
                Domain prevPost = abstractState.getPostCondition(previousNode);
                /* Inside a block the abstract state flows trivially */
                abstractState.setPreCondition(node, prevPost);
            }

            Domain pre = abstractState.getPreCondition(node);
            if (blockUnreachable) {
                abstractState.setPostCondition(node, pre);
                logger.log("  Skipping unreachable node: " + node, LoggerVerbosity.DEBUG);
                abstractState.getNodeState(node).setMark(NodeState.NodeMark.UNREACHABLE);
                previousNode = node;
                continue;
            }

            analyzeNode(node, abstractState, context);
            previousNode = node;
        }
    }
}
