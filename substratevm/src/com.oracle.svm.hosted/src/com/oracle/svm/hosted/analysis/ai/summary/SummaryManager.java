package com.oracle.svm.hosted.analysis.ai.summary;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import jdk.graal.compiler.nodes.Invoke;

/**
 * Manages summaries for functions.
 * @param summaryFactory
 * @param summaryCache
 * @param <Domain> the type of derived {@link AbstractDomain} used in the analysis
 */
public record SummaryManager<Domain extends AbstractDomain<Domain>>(SummaryFactory<Domain> summaryFactory,
                                                                    SummaryCache<Domain> summaryCache) {

    /**
     * Creates a summary using the summary factory.
     *
     * @param invoke the invocation we are creating the summary for
     * @param callerStateMap the abstract context we entered {@code invokeNode} with
     * @return a {@link Summary} containing only the pre-condition of the summary
     */
    public Summary<Domain> createSummary(Invoke invoke, AbstractState<Domain> callerStateMap) {
        return summaryFactory.createSummary(invoke, callerStateMap);
    }

    /**
     * Creates an empty summary using the summary factory.
     *
     * @return an empty {@link Summary} instance for the specified {@code Domain}
     */
    public Summary<Domain> createEmptySummary() {
        return summaryFactory.createEmptySummary();
    }

    /**
     * Gets a summary from the summary cache.
     *
     * @param targetName the name of the function
     * @param summaryPrecondition the precondition of the function
     * @return the summary for targetName with given summaryPrecondition
     */
    public Summary<Domain> getSummary(String targetName, Summary<Domain> summaryPrecondition) {
        return summaryCache.getSummary(targetName, summaryPrecondition);
    }

    /**
     * Checks if the summary cache contains a summary for the given target name and precondition.
     *
     * @param targetName the name of the function
     * @param summaryPrecondition the precondition of the function
     * @return true if the cache contains the summary, false otherwise
     */
    public boolean containsSummary(String targetName, Summary<Domain> summaryPrecondition) {
        return summaryCache.contains(targetName, summaryPrecondition);
    }

    /**
     * Puts a summary into the summary cache.
     *
     * @param targetName the name of the function
     * @param summary the summary to put
     */
    public void putSummary(String targetName, Summary<Domain> summary) {
        summaryCache.put(targetName, summary);
    }
}