package com.oracle.svm.hosted.analysis.ai.summary;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import jdk.graal.compiler.nodes.Invoke;
import jdk.vm.ci.meta.ResolvedJavaMethod;

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
                                         Domain callerPreCondition,
                                         List<Domain> domainArguments) {
        return summaryFactory.createSummary(invoke, callerPreCondition, domainArguments);
    }

    /**
     * Gets a summary from the summary cache.
     *
     * @param calleeMethod the method we are searching summary for
     * @param summaryPrecondition the precondition of the callee
     * @return the summary for targetName with given summaryPrecondition
     */
    public Summary<Domain> getSummary(ResolvedJavaMethod calleeMethod, Summary<Domain> summaryPrecondition) {
        return summaryCache.getSummary(calleeMethod, summaryPrecondition);
    }

    /**
     * Checks if the summary cache contains a summary for the given target name and precondition.
     *
     * @param calleeMethod the method we are searching summary for
     * @param summaryPrecondition the precondition of the callee of the {@param calleeMethod}
     * @return true if the cache contains the summary, false otherwise
     */
    public boolean containsSummary(ResolvedJavaMethod calleeMethod, Summary<Domain> summaryPrecondition) {
        return summaryCache.contains(calleeMethod, summaryPrecondition);
    }

    /**
     * Puts a summary into the summary cache.
     *
     * @param calleeMethod the method we are putting the summary for
     * @param summary the summary to put
     */
    public void putSummary(ResolvedJavaMethod calleeMethod, Summary<Domain> summary) {
        summaryCache.put(calleeMethod, summary);
    }

    public int getSummaryAmount(ResolvedJavaMethod method) {
        return summaryCache.getMethodSummariesAmount(method);
    }

    public int getCacheCalls() {
        return summaryCache.getCacheCalls();
    }

    public int getCacheHits() {
        return summaryCache.getCacheHits();
    }
}
