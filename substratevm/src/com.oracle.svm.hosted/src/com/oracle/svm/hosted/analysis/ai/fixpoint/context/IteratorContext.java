package com.oracle.svm.hosted.analysis.ai.fixpoint.context;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.GraphTraversalHelper;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.cfg.HIRBlock;

import java.util.List;
import java.util.Optional;

/**
 * Provides context information from the fixpoint iterator to the abstract interpreter.
 * This allows interpreters to make more precise decisions based on the state of the
 * fixpoint computation (e.g., which iteration, which predecessor, whether to widen, etc.).
 * <p>
 * The context is optional - interpreters should handle null context gracefully
 * and fall back to conservative behavior.
 */
public interface IteratorContext {

    /**
     * Get the CFG predecessor at the given index for a merge/loop node.
     * This is used for flow-sensitive phi node evaluation.
     *
     * @param node  The merge or loop node (PhiNode owner)
     * @param index The index of the predecessor
     * @return The predecessor node at the given index, or null if not available
     */
    Node getPredecessor(Node node, int index);

    /**
     * Get all CFG predecessors of a node in order.
     * The order corresponds to the order of phi inputs.
     *
     * @param node The merge or loop node
     * @return List of predecessor nodes, possibly empty
     */
    List<Node> getPredecessors(Node node);

    /**
     * Get the number of CFG predecessors for a merge/loop node.
     *
     * @param node The merge or loop node
     * @return The number of predecessors
     */
    int getPredecessorCount(Node node);

    /**
     * Check if the given node is a loop header (loop begin node).
     *
     * @param node The node to check
     * @return true if the node is a loop header
     */
    boolean isLoopHeader(Node node);

    /**
     * Check if the edge from source to target is a loop back-edge.
     * Back-edges are where widening is typically applied.
     *
     * @param source The source node of the edge
     * @param target The target node of the edge
     * @return true if this is a back-edge
     */
    boolean isBackEdge(Node source, Node target);

    /**
     * Get the current global iteration count of the fixpoint computation.
     * Iteration 0 is the initial pass.
     *
     * @return The current iteration number
     */
    int getGlobalIterationCount();

    /**
     * Get the iteration count for a specific loop header.
     * This counts how many times the loop has been re-analyzed.
     *
     * @param loopHeader The loop header node
     * @return The number of iterations for this loop, or 0 if not a loop header
     */
    int getLoopIterationCount(Node loopHeader);

    /**
     * Check if the analysis has stabilized (reached fixpoint).
     *
     * @return true if no changes occurred in the last iteration
     */
    boolean hasConverged();

    /**
     * Check if we're in the widening phase.
     * During widening, growing intervals are extrapolated to infinity.
     *
     * @return true if in widening phase
     */
    boolean isWideningPhase();

    /**
     * Check if we're in the narrowing phase.
     * During narrowing, intervals are refined downward using meet.
     *
     * @return true if in narrowing phase
     */
    boolean isNarrowingPhase();

    /**
     * Get the current analysis phase name (for logging/debugging).
     *
     * @return A string describing the current phase (e.g., "ascending", "widening", "narrowing")
     */
    IteratorPhase getCurrentPhase();

    /**
     * Get the current basic block being analyzed.
     *
     * @return The current HIRBlock, or null if not available
     */
    HIRBlock getCurrentBlock();

    /**
     * Get the block containing the given node.
     *
     * @param node The node to look up
     * @return The HIRBlock containing the node, or null if not available
     */
    HIRBlock getBlockForNode(Node node);

    /**
     * Check if this is the first visit to the given node.
     *
     * @param node The node to check
     * @return true if this is the first visit
     */
    boolean isFirstVisit(Node node);


    /**
     * Get the basic block analyzed prior to the current one.
     *
     * @return The previous HIRBlock, or null if not available
     */
    HIRBlock getPreviousBlock();

    int getPreviousBlockIndex();

    /**
     * Get the GraphTraversalHelper associated with this iterator context.
     */
    GraphTraversalHelper getGraphTraversalHelper();

    int getNodeVisitCount(Node node);

    void setEdgeTraversal(HIRBlock sourceBlock, HIRBlock targetBlock);

    /**
     * Get a compact call-context signature (e.g., k-CFA call string) attached to this iterator
     * instance by the invoke handler.
     */
    String getCallContextSignature();

    void setCallContextSignature(String signature);

    /**
     * Decide whether the iterator wants the transformer to collect/merge CFG predecessors
     * into the given node's pre-condition. This allows the iterator to centralize
     * policy about seeding/extrapolation/widening (e.g. skip merging when a loop header
     * already has an extrapolated pre-condition).
     *
     * @param node the node for which to decide a predecessor collection
     * @return true if predecessors should be collected and merged, false to skip
     */
    default boolean shouldCollectPredecessors(Node node) {
        if (node == null) {
            return true;
        }
        if (isLoopHeader(node)) {
            int visitCount = getNodeVisitCount(node);
            return visitCount == 0;
        }
        return true;
    }

    AnalysisMethod getCurrentAnalysisMethod();
}
