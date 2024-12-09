package com.oracle.svm.hosted.analysis.ai.fixpoint.cache;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import jdk.graal.compiler.nodes.CallTargetNode;

/**
 * Represents a description of behavior of a method.
 * It is used to avoid reanalyzing the method's body at every call site.
 *
 * @param <Domain> type of the derived {@link AbstractDomain}
 */
public interface FunctionSummary<Domain extends AbstractDomain<Domain>> {

    /**
     * Get the post condition of the method.
     * This is the result of abstract interpretation on the method's body.
     *
     * @return the post condition of the method
     */
    Domain getPostCondition();

    /**
     * Set the post condition of the method.
     * This is used to store the result of abstract interpretation on the method's body.
     *
     * @param postCondition the post condition to set
     */
    void setPostCondition(Domain postCondition);

    /**
     * Checks if this summary is equal to other summary.
     * We will be using this method to check if we can reuse the summary from {@link FixpointCache},
     * meaning that one summary does not have a calculated post-condition yet, therefore implementations
     * should not take abstract post-conditions into account.
     * Implementations can choose to implement strict equivalence for better precision of the analysis
     * (at the cost of slower analysis, since we will be analysing methods way more frequently),
     * or for better performance, they can choose to implement relational comparison (< or >)
     *
     * @param other the other {@link FunctionSummary} to compare with
     * @return true if this summary is equal to other summary
     */
    boolean isEqual(FunctionSummary<Domain> other);

    /**
     * Creates a {@link FunctionSummary} containing only the precondition of the summary.
     * This is used to check if {@link FixpointCache} contains a summary for a given call site and given abstract context.
     * NOTE: We will need to update the summary with the abstract post condition after we interpret the method's body.
     *
     * @param callTargetNode the call site
     * @param abstractState  the abstract context we entered {@callTargetNode} with
     * @return a {@link FunctionSummary} containing only the precondition of the summary
     */
    FunctionSummary<Domain> createSummaryPrecondition(CallTargetNode callTargetNode, AbstractState<Domain> abstractState);
}