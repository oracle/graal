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
     * Returns the pre-condition of the summary.
     * Pre-condition of a summary is the abstract context before the method body is executed.
     * Implementations can choose only the relevant information from the abstract context and create a compact pre-condition.
     */
    Domain getPreCondition();

    /**
     * Returns the post-condition of the summary.
     * This is the abstract context at method exit (join of all normal returns). Implementations may elide
     * information that is not needed by callers when applying the summary.
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
     * Called after the callee fixpoint finishes; implementations should populate post-condition accordingly.
     * Must handle BOT/TOP callee states conservatively.
     */
    void finalizeSummary(AbstractState<Domain> calleeAbstractState);

    /**
     * Apply this summary to a caller domain (optional convenience).
     *
     * @param domain caller domain
     * @return resulting domain
     */
    Domain applySummary(Domain domain);

    /**
     * Checks if the summary is complete.
     *
     * @return true if the summary is complete
     */
    boolean isComplete();

    /**
     * Sets the post-condition of the summary.
     *
     * @param postCondition the post-condition to set
     */
    void setPostCondition(Domain postCondition);
}