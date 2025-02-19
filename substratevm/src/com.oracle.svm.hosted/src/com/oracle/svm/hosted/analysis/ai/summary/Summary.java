package com.oracle.svm.hosted.analysis.ai.summary;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
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
     * It should finalize the summary by setting the post-condition of the summary.
     *
     * @param postCondition the post-condition of the analysisMethod body
     */
    void finalizeSummary(Domain postCondition);

    /**
     * This analysisMethod ensures that the analysis is inter-procedural.
     * Applies the summary of the callee to the current abstract state of the caller (at the invocation site).
     * This can be done in multiple steps, like converting the actual arguments to the callee's formal arguments,
     * converting the summary into an abstract domain, etc.
     * NOTE: this will be only called after {@code finalizeSummary} is called
     *
     * @param invoke of the invokeNode
     * @param invokeNode the invoke node that we are applying the summary to
     * @param callerStateMap the state map of the caller
     */
    void applySummary(Invoke invoke, Node invokeNode, AbstractStateMap<Domain> callerStateMap);

    /**
     * All summaries should be able to be represented as a string.
     * @return a string representation of the summary
     */
    String toString();
}