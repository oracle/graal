package com.oracle.svm.hosted.analysis.ai.summary;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.Invoke;

/**
 * Represents a factory for creating {@link Summary} instances.
 * This is used to create summaries for analysisMethod calls during analysis.
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
     *       Taking only a part of the abstract context, that is relevant for the analysisMethod call.
     *       Renaming the formal arguments to actual arguments, etc.
     *
     * @param invoke contains information about the invocation
     * @param invokeNode the node where the invocation happens
     * @param callerStateMap the abstract context of the caller at the invocation point
     *
     * @return a {@link Summary} containing only the pre-condition of the summary
     */
    Summary<Domain> createSummary(Invoke invoke, Node invokeNode, AbstractStateMap<Domain> callerStateMap);
}
