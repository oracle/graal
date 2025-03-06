package com.oracle.svm.hosted.analysis.ai.summary;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import jdk.graal.compiler.nodes.Invoke;

import java.util.List;

/**
 * Manages summaries for functions.
 * @param summaryFactory
 * @param summaryCache
 * @param <Domain> the type of derived {@link AbstractDomain} used in the analysis
 */
public record SummaryManager<Domain extends AbstractDomain<Domain>>(SummaryFactory<Domain> summaryFactory,
                                                                    SummaryCache<Domain> summaryCache) {

    public SummaryManager(SummaryFactory<Domain> summaryFactory) {
        this(summaryFactory, new SummaryCache<>());
    }

    public Summary<Domain> createSummary(Invoke invoke,
                                         Domain callSitePreCondition,
                                         List<Domain> domainArguments) {
        return summaryFactory.createSummary(invoke, callSitePreCondition, domainArguments);
    }

    /**
     * Gets a summary from the summary cache.
     *
     * @param calleeName the name of the callee
     * @param summaryPrecondition the precondition of the callee
     * @return the summary for targetName with given summaryPrecondition
     */
    public Summary<Domain> getSummary(String calleeName, Summary<Domain> summaryPrecondition) {
        return summaryCache.getSummary(calleeName, summaryPrecondition);
    }

    /**
     * Checks if the summary cache contains a summary for the given target name and precondition.
     *
     * @param calleeName the name of the callee
     * @param summaryPrecondition the precondition of the callee
     * @return true if the cache contains the summary, false otherwise
     */
    public boolean containsSummaryForResolvedJavaName(String calleeName, Summary<Domain> summaryPrecondition) {
        return summaryCache.contains(calleeName, summaryPrecondition);
    }

    /**
     * Puts a summary into the summary cache.
     *
     * @param calleeName the name of the callee
     * @param summary the summary to put
     */
    public void putSummary(String calleeName, Summary<Domain> summary) {
        summaryCache.put(calleeName, summary);
    }
}
