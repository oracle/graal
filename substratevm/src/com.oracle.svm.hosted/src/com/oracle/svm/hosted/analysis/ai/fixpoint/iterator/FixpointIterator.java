package com.oracle.svm.hosted.analysis.ai.fixpoint.iterator;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;

// TODO: I don't understand, why the does the FixpointIterator have to be instantiated
//  in oreder to run the fixpoint computation ?
//  iterateUntilFixpoint should receive at least the initial abstract state ?

/**
 * API for a fixpoint iterator in abstract interpretation.
 * A fixpoint iterator is responsible for computing abstract states (pre-condition and post-condition pairs)
 * for each node in the Graal IR by iteratively applying transfer functions
 * until a stable solution (fixpoint) is reached.
 *
 * @param <Domain> the abstract domain used in the analysis
 */
public interface FixpointIterator<Domain extends AbstractDomain<Domain>> {

    /**
     * This method performs the fixpoint computation and returns the
     * resulting {@link AbstractState} after the fixpoint is reached.
     *
     * @return the abstract state after the fixpoint is reached
     */
    AbstractState<Domain> iterateUntilFixpoint();

    AbstractState<Domain> getAbstractState();

    /**
     * Clears the internal state of the iterator,
     * so that the {@link FixpointIterator} can be reused.
     */
    void clear();
}
