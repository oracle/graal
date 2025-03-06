package com.oracle.svm.hosted.analysis.ai.summary;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.Invoke;

/**
 * Represents a summary of a analysisMethod, used to avoid reanalyzing the analysisMethod's body at every call site.
 * New summaries are created in the provided {@link SummaryFactory} and then checked for subsumption in {@link SummaryCache}.
 * When we are creating a summary, we only know the abstract context at the invocation site, actual + formal parameters and other info from {@link Invoke}
 * Therefore, {@link SummaryFactory} can only create incomplete summaries (summaries only with their pre-condition known). -> check {@link SummaryFactory} docs.
 * After the framework does the necessary fixpoint computation of the analysisMethod body, we know everything to create a complete summary in {@code finalizeSummary}.
 * Calling {@code finalizeSummary} should create the summary post-condition.
 * And for this reason, {@link Summary} implementations should remember both pre-condition and post-condition in their internal structures.
 *
 * @param <Domain> type of the derived {@link AbstractDomain} used in abstract interpretation
 */
public interface Summary<Domain extends AbstractDomain<Domain>> {

    /**
     * Gets the invokeNode of the analysisMethod that this summary represents.
     * @return the invokeNode of the analysisMethod
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
     * This analysisMethod is called by the framework after the fixpoint computation of the analysisMethod body.
     * It should finalize the summary by correctly modifying the post-condition of the summary.
     *
     * @param calleePostCondition the post-condition of the analysisMethod body
     */
    void finalizeSummary(Domain calleePostCondition);

    /**
     * This analysisMethod ensures that the analysis is inter-procedural.
     * Applies the summary of the callee to the current abstract state of the caller (at the invocation site).
     * This can be done in multiple steps, like converting the actual arguments to the callee's formal arguments,
     * converting the summary into an abstract domain, etc.
     * NOTE: this will be only called after {@code finalizeSummary} is called
     *
     * @param invoke of the invokeNode
     * @param invokeNode the invoke node that we are applying the summary to
     * @param callerPreCondition the abstract context of the caller at the invocation site
     */
    Domain applySummary(Invoke invoke, Node invokeNode, Domain callerPreCondition);
}
