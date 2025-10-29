package com.oracle.svm.hosted.analysis.ai.fixpoint.context;

import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.GraphTraversalHelper;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.LoopEndNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Basic implementation of {@link IteratorContext} that tracks fixpoint iteration state
 * and provides context information to abstract interpreters.
 * <p>
 * This class is mutable and should be updated by the fixpoint iterator as it progresses.
 */
public class BasicIteratorContext implements IteratorContext {

    private final GraphTraversalHelper graphTraversalHelper;
    private final Map<Node, Integer> loopIterationCounts;
    private final Map<Node, Integer> nodeVisitCounts;
    private AbstractState<?> abstractState;
    private HIRBlock previousBlock;
    private HIRBlock currentBlock;
    private IteratorPhase currentPhase;
    private boolean hasConverged;
    private int globalIterationCount;

    public BasicIteratorContext(GraphTraversalHelper graphTraversalHelper) {
        this.graphTraversalHelper = graphTraversalHelper;
        this.loopIterationCounts = new HashMap<>();
        this.nodeVisitCounts = new HashMap<>();
        this.globalIterationCount = 0;
        this.currentPhase = IteratorPhase.ASCENDING;
        this.hasConverged = false;
        this.previousBlock = null;
        this.abstractState = null;
    }

    @Override
    public Node getPredecessor(Node node, int index) {
        if (graphTraversalHelper == null) {
            return null;
        }
        HIRBlock block = getBlockForNode(node);
        if (block == null || index >= graphTraversalHelper.getPredecessorCount(block)) {
            return null;
        }
        HIRBlock predBlock = graphTraversalHelper.getPredecessorAt(block, index);
        return graphTraversalHelper.getEndNode(predBlock);
    }

    @Override
    public List<Node> getPredecessors(Node node) {
        if (graphTraversalHelper == null) {
            return List.of();
        }
        HIRBlock block = getBlockForNode(node);
        if (block == null) {
            return List.of();
        }
        List<Node> predecessors = new ArrayList<>();
        for (int i = 0; i < graphTraversalHelper.getPredecessorCount(block); i++) {
            HIRBlock predBlock = graphTraversalHelper.getPredecessorAt(block, i);
            predecessors.add(graphTraversalHelper.getEndNode(predBlock));
        }
        return predecessors;
    }

    @Override
    public int getPredecessorCount(Node node) {
        HIRBlock block = getBlockForNode(node);
        return block != null ? graphTraversalHelper.getPredecessorCount(block) : 0;
    }

    @Override
    public boolean isLoopHeader(Node node) {
        return node instanceof LoopBeginNode;
    }

    @Override
    public boolean isBackEdge(Node source, Node target) {
        if (!(target instanceof LoopBeginNode)) {
            return false;
        }
        // A back-edge is from a LoopEndNode to a LoopBeginNode
        return source instanceof LoopEndNode loopEnd &&
                loopEnd.loopBegin() == target;
    }

    @Override
    public int getGlobalIterationCount() {
        return globalIterationCount;
    }

    @Override
    public int getLoopIterationCount(Node loopHeader) {
        if (!isLoopHeader(loopHeader)) {
            return 0;
        }
        return loopIterationCounts.getOrDefault(loopHeader, 0);
    }

    @Override
    public boolean hasConverged() {
        return hasConverged;
    }

    @Override
    public boolean isWideningPhase() {
        return currentPhase == IteratorPhase.WIDENING;
    }

    @Override
    public boolean isNarrowingPhase() {
        return currentPhase == IteratorPhase.NARROWING;
    }

    @Override
    public IteratorPhase getCurrentPhase() {
        return currentPhase;
    }

    @Override
    public HIRBlock getCurrentBlock() {
        return currentBlock;
    }

    @Override
    public HIRBlock getBlockForNode(Node node) {
        if (graphTraversalHelper == null) {
            return null;
        }
        // Search through all blocks to find the one containing this node
        ControlFlowGraph cfg = graphTraversalHelper.getCfgGraph();
        if (cfg == null) {
            return null;
        }
        for (HIRBlock block : cfg.getBlocks()) {
            for (Node blockNode : block.getNodes()) {
                if (blockNode == node) {
                    return block;
                }
            }
        }
        return null;
    }

    @Override
    public boolean isFirstVisit(Node node) {
        return nodeVisitCounts.getOrDefault(node, 0) == 0;
    }

    // === Mutation methods for the fixpoint iterator ===

    /**
     * Increment the global iteration counter.
     * Should be called by the fixpoint iterator at the start of each global iteration.
     */
    public void incrementGlobalIteration() {
        globalIterationCount++;
    }

    /**
     * Increment the loop iteration counter for a specific loop header.
     * Should be called when re-analyzing a loop.
     *
     * @param loopHeader The loop header node
     */
    public void incrementLoopIteration(Node loopHeader) {
        if (isLoopHeader(loopHeader)) {
            loopIterationCounts.merge(loopHeader, 1, Integer::sum);
        }
    }

    public int getNodeVisitCount(Node node) {
        return nodeVisitCounts.getOrDefault(node, 0);
    }

    public void incrementNodeVisitCount(Node node) {
        nodeVisitCounts.merge(node, 1, Integer::sum);
    }

    public void resetNodeVisitCount(Node node) {
        nodeVisitCounts.put(node, 0);
    }

    public void setCurrentBlock(HIRBlock block) {
        this.previousBlock = this.currentBlock;
        this.currentBlock = block;
        AbstractInterpretationLogger logger = AbstractInterpretationLogger.getInstance();
        logger.log(String.format("Context: previous block set to %s, current block set to %s",
                previousBlock, currentBlock), LoggerVerbosity.DEBUG);
    }

    /**
     * Update context when traversing an edge from source block to target block.
     * This ensures previousBlock is correctly set to the source when analyzing the target.
     *
     * @param sourceBlock The block we're coming from
     * @param targetBlock The block we're going to
     */
    public void setEdgeTraversal(HIRBlock sourceBlock, HIRBlock targetBlock) {
        this.previousBlock = sourceBlock;
        this.currentBlock = targetBlock;
        AbstractInterpretationLogger logger = AbstractInterpretationLogger.getInstance();
        logger.log(String.format("Edge traversal: %s -> %s", sourceBlock, targetBlock), LoggerVerbosity.DEBUG);
    }

    public void setPhase(IteratorPhase phase) {
        this.currentPhase = phase;
    }

    public void setConverged(boolean converged) {
        this.hasConverged = converged;
    }

    public void reset() {
        globalIterationCount = 0;
        loopIterationCounts.clear();
        nodeVisitCounts.clear();
        currentBlock = null;
        previousBlock = null;
        currentPhase = IteratorPhase.ASCENDING;
        hasConverged = false;
    }

    @Override
    public HIRBlock getPreviousBlock() {
        return previousBlock;
    }

    @Override
    public int getPreviousBlockIndex() {
        if (previousBlock == null || currentBlock == null) {
            AbstractInterpretationLogger logger = AbstractInterpretationLogger.getInstance();
            logger.log(String.format("getPreviousBlockIndex: previousBlock=%s, currentBlock=%s -> returning -1",
                    previousBlock, currentBlock), LoggerVerbosity.DEBUG);
            return -1;
        }

        AbstractInterpretationLogger logger = AbstractInterpretationLogger.getInstance();
        int predCount = graphTraversalHelper.getPredecessorCount(currentBlock);
        logger.log(String.format("getPreviousBlockIndex: looking for %s among %d predecessors of %s",
                previousBlock, predCount, currentBlock), LoggerVerbosity.DEBUG);

        for (int i = 0; i < predCount; i++) {
            HIRBlock pred = graphTraversalHelper.getPredecessorAt(currentBlock, i);
            logger.log(String.format("  Checking predecessor[%d]: %s (matches: %s)",
                    i, pred, pred == previousBlock), LoggerVerbosity.DEBUG);
            if (pred == previousBlock) {
                logger.log(String.format("  Found previousBlock at index %d", i), LoggerVerbosity.DEBUG);
                return i;
            }
        }

        logger.log(String.format("getPreviousBlockIndex: previousBlock %s not found among predecessors -> returning -1",
                previousBlock), LoggerVerbosity.DEBUG);
        return -1;
    }

    /**
     * Set the abstract state reference. Called by the fixpoint iterator.
     *
     * @param abstractState The abstract state
     */
    public void setAbstractState(AbstractState<?> abstractState) {
        this.abstractState = abstractState;
    }

    /**
     * Update the current block and remember the previous one.
     * This should be called by the fixpoint iterator when moving to a new block.
     *
     * @param newBlock The new block being analyzed
     */
    public void updateBlock(HIRBlock newBlock) {
        this.previousBlock = this.currentBlock;
        this.currentBlock = newBlock;
    }
}

