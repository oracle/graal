package com.oracle.svm.hosted.analysis.ai.fixpoint.iterator;

import com.oracle.svm.hosted.analysis.ai.checker.Checker;
import com.oracle.svm.hosted.analysis.ai.checker.CheckerSummary;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.ControlSplitNode;
import jdk.graal.compiler.nodes.cfg.HIRBlock;

import java.util.List;

/**
 * API for abstract interpretation fixpoint iterators.
 *
 * @param <Domain> the type of the abstract domain
 *                                                                                                                                                 TODO in the future add support for different directions of iteration (bottom-up)
 */
public interface FixpointIterator<Domain extends AbstractDomain<Domain>> {

    /**
     * Runs the fixpoint iteration algorith with a given initial {@link Domain}.
     * <p>
     * This method performs a fixpoint iteration algorithm and returns the
     * resulting {@link AbstractStateMap}
     * after the iteration is completed.
     *
     * @return the environment after the fixpoint is reached
     */
    AbstractStateMap<Domain> iterateUntilFixpoint();

    /**
     * Returns the post condition of the given {@link Node}.
     *
     * @param node to the post condition of
     * @return the post condition of the node
     */
    public Domain getPreCondition(Node node);

    /**
     * Returns the precondition of the given {@link Node}.
     *
     * @param node to the precondition of
     * @return the precondition of the node
     */
    public Domain getPostCondition(Node node);

    /**
     * Clears the internal state of the iterator,
     * so that the {@link FixpointIterator} can be reused.
     */
    void clear();
}