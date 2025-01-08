package com.oracle.svm.hosted.analysis.ai.summary;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;

/**
 * Represents a description of behavior of a function.
 * It is used to avoid reanalyzing the function's body at every call site.
 *
 * @param <Domain> type of the derived {@link AbstractDomain} used in abstract interpretation
 */
public interface Summary<Domain extends AbstractDomain<Domain>> {

    /**
     * Get the abstract context the method was invoked with
     *
     * @return the pre-condition of the summary
     */
    Domain preCondition();

    /**
     * Gets the {@code Domain} from the internal state of the summary
     *
     */
    Domain postCondition();

    /**
     * Checks if this summary covers the other summary.
     * Covering in this case means that the precondition of this summary is more general than the precondition of the other summary.
     * We will be using this function to check if we can reuse the summary from {@link SummaryCache},
     * meaning that one summary does not have a calculated post-condition yet, therefore implementations
     * should not take abstract post-conditions into account.
     * Implementations can choose to implement strict equivalence for better precision of the analysis
     * (at the cost of slower analysis, since we will be analysing functions way more frequently),
     * or for better performance, they can choose to implement relational comparison (< or >)
     *
     * @param other the other {@link Summary} to compare with
     * @return true if this summary subsumes other summary
     */
    boolean subsumes(Summary<Domain> other);
}