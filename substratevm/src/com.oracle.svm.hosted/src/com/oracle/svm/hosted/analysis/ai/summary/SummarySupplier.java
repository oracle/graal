package com.oracle.svm.hosted.analysis.ai.summary;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import jdk.graal.compiler.nodes.Invoke;

public interface SummarySupplier<Domain extends AbstractDomain<Domain>> {

    /**
     * Creates a {@link Summary} containing only the precondition of the summary.
     * This is used to check if {@link SummaryCache} contains a summary for a given call site and given abstract context.
     *
     * @param invoke    the invocation we are creating the summary for
     * @param abstractState the abstract context we entered {@code invokeNode} with
     * @return a {@link Summary} containing only the precondition of the summary
     */
    Summary<Domain> get(Invoke invoke, AbstractState<Domain> abstractState);

    /**
     * Creates a complete {@link Summary} containing both the precondition and post-condition of the summary.
     *
     * @param preconditionSummary the precondition summary to be modified
     * @param abstractState the abstract context representing the computed post-condition for a method body
     * @return a complete {@link Summary} containing both the precondition and post-condition of the summary
     */
    Summary<Domain> createCompleteSummary(Summary<Domain> preconditionSummary, AbstractState<Domain> abstractState);
}
