package com.oracle.svm.hosted.analysis.ai.summary;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.interpreter.NodeInterpreter;
import jdk.graal.compiler.nodes.Invoke;

/**
 * Represents a summary of a method. It is used to avoid reanalyzing the method's body at every call site.
 * For instance when the analysis has already analyzed method foo(), we already know the effects of it, and it is not necessary to reanalyze it.
 * New summaries are created in the provided {@link SummaryFactory} and then checked for subsumption in {@link SummaryCache}.
 * When we are creating a summary, we only know the abstract context at the invocation site, actual + formal parameters and other info from {@link Invoke}
 * Therefore, {@link SummaryFactory} can only create incomplete summaries (summaries only with their pre-condition known). -> check {@link SummaryFactory} docs.
 * After the framework does the necessary fixpoint computation of the method body, we know everything to create a complete summary in {@code finalizeSummary}.
 *
 * @param <Domain> type of the derived {@link AbstractDomain} used in abstract interpretation
 */
public interface Summary<Domain extends AbstractDomain<Domain>> {

    /**
     * Gets the invokeNode of the method that this summary represents.
     * @return the invokeNode of the method
     */
    Invoke getInvoke();

    /**
     * Returns the pre-condition of the summary.
     * Pre-condition of a summary is the abstract context before the method body is executed.
     * NOTE:
     *      This doesn't have to be the same as the abstract context at the invocation point.
     *      {@link SummaryFactory} implementations can choose only the relevant information from the abstract context
     *      and create a summary pre-condition. This is done to reduce the size of the summary.
     *
     * @return the pre-condition of the summary
     */
    Domain getPreCondition();

    /**
     * Returns the post-condition of the summary.
     * NOTE:
     *      Same as in {@code getPreCondition}, this doesn't have to be the same as the abstract context at the return point.
     *      We can remove information that won't be needed in applySummary (in the caller abstract context).
     * @return the post-condition of the summary
     */
    Domain getPostCondition();

    /**
     * Checks if this summary covers the other summary.
     * Covering in this case means that this pre-condition summary is more general than another pre-condition summary.
     * We will be using this analysisMethod to check if we can reuse the summary from {@link SummaryCache},
     * meaning that one summary does not have a calculated post-condition yet, therefore implementations
     * should not take abstract post-conditions into account.
     * Implementations can choose to implement strict equivalence for better precision of the analysis
     * (at the cost of slower analysis, since we will be analysing methods way more frequently),
     * or for better performance, they can choose to implement relational comparison (< or >)
     *
     * @param other the other {@link Summary} to compare with
     * @return true if this summary subsumes other summary
     */
    boolean subsumes(Summary<Domain> other);

    /**
     * This method is called by the framework after the fixpoint computation of the analysisMethod body.
     * It should finalize the summary by correctly modifying the post-condition of the summary.
     *
     * @param calleePostCondition the post-condition of the analysisMethod body
     */
    void finalizeSummary(Domain calleePostCondition);

    /**
     * This method is called by the framework when applying the summary to the caller's abstract context.
     * It should return the abstract context after applying the summary.
     * NOTE:
     *      This method should not modify the caller's abstract context, but rather return a new one.
     *      This is done so that {@link NodeInterpreter} has more control over how to apply the summary,
     *      and this way we don't have to write interpretation logic in the summary.
     * @param callerPreCondition the abstract context we want to apply the summary to
     * @return the domain which is the result of applying the summary to the caller's abstract context
     */
    Domain applySummary(Domain callerPreCondition);
}
