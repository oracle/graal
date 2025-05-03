package com.oracle.svm.hosted.analysis.ai.fixpoint.iterator;

import com.oracle.svm.hosted.analysis.ai.checker.CheckerManager;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;

/**
 * Interface for a fixpoint iterator in abstract interpretation.
 * A fixpoint iterator is responsible for computing abstract states for all nodes in a control flow graph (CFG)
 * until a stable solution (fixpoint) is reached. The iteration process applies abstract transfer functions
 * repeatedly, propagating abstract states through the graph until further changes no longer occur.
 *
 * @param <Domain> abstract domain used in the abstract interpretation
 */
public interface FixpointIterator<Domain extends AbstractDomain<Domain>> {

    /**
     * Runs the fixpoint iteration algorith with a given initial {@link Domain}.
     * This analysisMethod performs a fixpoint iteration algorithm and returns the
     * resulting {@link AbstractState} after the iteration is completed.
     * This is a good place to run checkers from {@link CheckerManager}, because
     * we know the abstract context at every point in the analysisMethod.
     * NOTE:
     * Currently, all implementations should call checkerManager.checkAll after the fixpoint is reached.
     * This is not optimal when we have recursive methods.
     *
     * @return mapping of every IR node to it's abstract state after the fixpoint is reached
     */
    AbstractState<Domain> iterateUntilFixpoint();

    /**
     * Gets the {@link ControlFlowGraph} on which the fixpoint iteration is performed.
     *
     * @return the control flow graph of the method
     */
    ControlFlowGraph getControlFlowGraph();

    /**
     * Gets the abstract state of the iterator.
     *
     * @return the abstract state of the iterator
     */
    AbstractState<Domain> getAbstractState();

    /**
     * Clears the internal state of the iterator,
     * so that the {@link FixpointIterator} can be reused.
     */
    void clear();
}
