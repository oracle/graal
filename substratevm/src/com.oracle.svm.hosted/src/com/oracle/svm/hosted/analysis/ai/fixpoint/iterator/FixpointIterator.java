package com.oracle.svm.hosted.analysis.ai.fixpoint.iterator;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.context.IteratorContext;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import com.oracle.svm.hosted.analysis.ai.util.AbstractInterpretationServices;

/**
 * API for a fixpoint iterator in abstract interpretation.
 * A fixpoint iterator is responsible for computing abstract states (pre-condition and post-condition pairs)
 * for each node in the Graal IR by iteratively applying transfer functions until a stable solution
 * (fixpoint) is reached.
 *
 * @param <Domain> the abstract domain used in the analysis
 */
public interface FixpointIterator<Domain extends AbstractDomain<Domain>> {

    /**
     * Performs the fixpoint computation and returns the resulting {@link AbstractState}
     * after the fixpoint is reached.
     *
     * @return the abstract state after the fixpoint is reached
     */
    AbstractState<Domain> doRunFixpointIteration();

    default AbstractState<Domain> runFixpointIteration() {
        AbstractInterpretationServices.getInstance().markMethodTouched(getIteratorContext().getCurrentAnalysisMethod());
        return doRunFixpointIteration();
    }

    /**
     * Seed the entry pre-condition and run the fixpoint.
     * Equivalent to getAbstractState().setStartNodeState(initialEntryPre) followed by iterateUntilFixpoint().
     */
    default AbstractState<Domain> runFixpointIteration(Domain initialEntryPre) {
        getAbstractState().setStartNodeState(initialEntryPre);
        return runFixpointIteration();
    }

    /**
     * Returns the mutable abstract state managed by this iterator.
     */
    AbstractState<Domain> getAbstractState();

    /**
     * Clears the internal state of the iterator so it can be reused.
     */
    void clear();

    /**
     * Returns the iterator context that provides CFG and analysis-phase information to interpreters.
     */
    IteratorContext getIteratorContext();
}
