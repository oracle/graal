package com.oracle.svm.hosted.analysis.ai.summary;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import jdk.graal.compiler.nodes.Invoke;

import java.util.List;

/**
 * Represents a supplier of {@link Summary} objects.
 * This is used to create summaries for method calls during analysis.
 *
 * @param <Domain> type of the derived {@link AbstractDomain}
 */
public interface SummarySupplier<Domain extends AbstractDomain<Domain>> {

    /**
     * Creates a {@link Summary} containing only the pre-condition of the summary.
     * NOTE: It should only be necessary to have the callers state + arguments to create  a pre-condition summary
     * This is used to check if {@link SummaryCache} contains a summary for a given call site and given abstract context.
     *
     * @param invoke    the invocation we are creating the summary for
     * @param abstractState the abstract context we entered {@code invokeNode} with
     * @param actualArguments the actual arguments of the method call
     * @return a {@link Summary} containing only the precondition of the summary
     */
    Summary<Domain> get(Invoke invoke, AbstractState<Domain> abstractState, List<Domain> actualArguments);

    /**
     * Creates a complete {@link Summary} containing both the precondition and post-condition of the summary.
     * NOTE: This should be done once we have reached the fixpoint of the method body
     *
     * @param preConditionSummary the precondition summary to be modified
     * @param abstractState the abstract context representing the computed post-condition for a method body
     * @return a complete {@link Summary} containing both the precondition and post-condition of the summary
     */
    Summary<Domain> createCompleteSummary(Summary<Domain> preConditionSummary, AbstractState<Domain> abstractState);
}
