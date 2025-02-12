package com.oracle.svm.hosted.analysis.ai.summary;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import jdk.graal.compiler.nodes.Invoke;

import java.util.List;

/**
 * Represents a factory for creating {@link Summary} instances.
 * This is used to create summaries for method calls during analysis.
 *
 * @param <Domain> type of the derived {@link AbstractDomain} used in the analysis
 */
public interface SummaryFactory<Domain extends AbstractDomain<Domain>> {

    /**
     * Creates a {@link Summary} from the abstract context at a given call site.
     * The created summary will be then used for lookup in {@link SummaryCache}, more specifically,
     * the framework will check if this summary is subsumed by any of the summaries in the cache.
     * When calling {@code createSummary} we don't know what the post-condition of the summary will be yet,
     * so implementation can either leave the post-condition empty or set it to some default value (TOP value of the domain most of the time).
     * NOTE: It should only be necessary to have the abstract context at the call site + arguments to create a summary
     *       Creation of a summary can include:
     *       Taking only a part of the abstract context, that is relevant for the method call.
     *       Renaming the formal arguments to actual arguments, etc.
     *
     * @param invoke    the invocation we are creating the summary for
     * @param abstractState the abstract context we entered {@code invokeNode} with
     * @param actualArguments the actual arguments of the method call
     * @return a {@link Summary} containing only the pre-condition of the summary
     */
    Summary<Domain> createSummary(Invoke invoke, AbstractState<Domain> abstractState, List<Domain> actualArguments);
}
