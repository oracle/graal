package com.oracle.svm.hosted.analysis.ai.fixpoint.summary;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import jdk.graal.compiler.nodes.InvokeNode;

/**
 * Represents a description of behavior of a function.
 * It is used to avoid reanalyzing the function's body at every call site.
 *
 * @param <Domain> type of the derived {@link AbstractDomain}
 */
public interface FunctionSummary<Domain extends AbstractDomain<Domain>> {

    /**
     * Get the pre-condition of the function.
     * This is the abstract context we entered the function with
     *
     * @return the pre-condition of the function
     */
    Domain getPreCondition();

    /**
     * Get the post condition of the function.
     * This is the result of abstract interpretation on the function's body.
     *
     * @return the post condition of the function
     */
    Domain getPostCondition();

    /**
     * Set the post condition of the function.
     * This is used to store the result of abstract interpretation on the function's body.
     *
     * @param postCondition the post condition to set
     */
    void setPostCondition(Domain postCondition);

    /**
     * Checks if this summary covers the other summary.
     * Covers means that the precondition of this summary is more general than the precondition of the other summary.
     * We will be using this function to check if we can reuse the summary from {@link FixpointCache},
     * meaning that one summary does not have a calculated post-condition yet, therefore implementations
     * should not take abstract post-conditions into account.
     * Implementations can choose to implement strict equivalence for better precision of the analysis
     * (at the cost of slower analysis, since we will be analysing functions way more frequently),
     * or for better performance, they can choose to implement relational comparison (< or >)
     *
     * @param other the other {@link FunctionSummary} to compare with
     * @return true if this summary subsumes other summary
     */
    boolean subsumes(FunctionSummary<Domain> other);

    /**
     * Creates a {@link FunctionSummary} containing only the precondition of the summary.
     * This is used to check if {@link FixpointCache} contains a summary for a given call site and given abstract context.
     * NOTE: We will need to update the summary with the abstract post condition after we interpret the function's body.
     *
     * @param invokeNode    the call site we are creating the summary for
     * @param abstractState the abstract context we entered {@code callTargetNode} with
     * @return a {@link FunctionSummary} containing only the precondition of the summary
     */
    FunctionSummary<Domain> createSummaryPrecondition(InvokeNode invokeNode, AbstractState<Domain> abstractState);
}