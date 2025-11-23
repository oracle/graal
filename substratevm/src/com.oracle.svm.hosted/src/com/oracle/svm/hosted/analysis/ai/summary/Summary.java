package com.oracle.svm.hosted.analysis.ai.summary;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;

/**
 * Represents a summary of a method. It is used to avoid reanalyzing the method's body at every call site.
 * For instance when the analysis has already analyzed method foo(), we already know the effects of it, and it is not necessary to reanalyze it.
 * New summaries are created in the provided {@link SummaryFactory} and then checked for subsumption in {@link SummaryCache}.
 * When we are creating a summary, we only know the abstract context at the invocation site, actual + formal parameters
 * Therefore, {@link SummaryFactory} can only create incomplete summaries (summaries only with their pre-condition known).
 * After the framework does the necessary fixpoint computation of the method body, we know everything to create a complete summary in {@code finalizeSummary}.
 *
 * @param <Domain> type of the derived {@link AbstractDomain} used in abstract interpretation
 */
public interface Summary<Domain extends AbstractDomain<Domain>> {

    /**
     * Returns the pre-condition of the summary, which is created by a corresponding {@link SummaryFactory}.
     * Pre-condition of a method summary is the relevant abstract context at the entry point of the method .
     * {@SummaryFactory} Implementations can choose to keep only the relevant information from the
     * caller abstract context and create a compact pre-condition for the callee.
     */
    Domain getPreCondition();

    /**
     * Returns the post-condition of the summary.
     * This is the abstract context at method exit (join of all normal returns).
     * Implementations may omit storing information not needed by callers when applying the summary.
     */
    Domain getPostCondition();

    /**
     * Checks if this summary covers (subsumes) the other summary's pre-condition.
     *
     * @param other the other {@link Summary} to compare with
     * @return true if this summary subsumes other summary
     */
    boolean subsumesSummary(Summary<Domain> other);

    /**
     * This method is supposed to modify the post-condition of a summary
     * Called after the callee fixpoint finishes; implementations should populate post-condition accordingly.
     * Must handle BOT/TOP callee states conservatively.
     */
    void finalizeSummary(AbstractState<Domain> calleeAbstractState);

    /**
     * Apply this summary back to the caller abstract context,
     * this is necessary to propagate analysis results back to the caller.
     *
     * @param domain caller domain
     * @return resulting domain
     */
    Domain applySummary(Domain domain);
}