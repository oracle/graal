package com.oracle.svm.hosted.analysis.ai.fixpoint.iterator;

import com.oracle.svm.hosted.analysis.ai.checker.CheckerManager;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import jdk.graal.compiler.graph.Node;

/**
 * Interface for a fixpoint iterator in abstract interpretation.
 * A fixpoint iterator is responsible for computing abstract states for all nodes in a control flow graph (CFG)
 * until a stable solution (fixpoint) is reached. The iteration process applies abstract transfer functions
 * repeatedly, propagating abstract states through the graph until further changes no longer occur.
 *
 * @param <Domain> abstract domain used in the abstract interpretation
 *
 */
public interface FixpointIterator<Domain extends AbstractDomain<Domain>> {

    /**
     * Runs the fixpoint iteration algorith with a given initial {@link Domain}.
     * This analysisMethod performs a fixpoint iteration algorithm and returns the
     * resulting {@link AbstractStateMap} after the iteration is completed.
     * This is a good place to run checkers from {@link CheckerManager}, because
     * we know the abstract context at every point in the analysisMethod.
     * NOTE:
     *      Currently, all implementations should call checkerManager.checkAll after the fixpoint is reached.
     *      This is not optimal when we have recursive methods.
     *
     * @return mapping of every IR node to it's abstract state after the fixpoint is reached
     */
    AbstractStateMap<Domain> iterateUntilFixpoint();

    /**
     * Returns the post condition of the given {@link Node}.
     *
     * @param node to the post condition of
     * @return the post condition of the node
     */
    Domain getPreCondition(Node node);

    /**
     * Returns the precondition of the given {@link Node}.
     *
     * @param node to the precondition of
     * @return the precondition of the node
     */
    Domain getPostCondition(Node node);

    /**
     * Returns the abstract state map of the iterator.
     *
     * @return the abstract state map of the iterator
     */
    AbstractStateMap<Domain> getAbstractStateMap();

    /**
     * Clears the internal state of the iterator,
     * so that the {@link FixpointIterator} can be reused.
     */
    void clear();
}
