package com.oracle.svm.hosted.analysis.ai.summary;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import jdk.graal.compiler.nodes.Invoke;

import java.util.List;

/**
 * Manages summaries for methods.
 *
 * @param summaryFactory
 * @param summaryCache
 * @param <Domain>       the type of derived {@link AbstractDomain} used in the analysis
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

    public Summary<Domain> getSummary(AnalysisMethod calleeMethod, Summary<Domain> summaryPrecondition) {
        return summaryCache.getSummary(calleeMethod, summaryPrecondition);
    }

    public boolean containsSummary(AnalysisMethod calleeMethod, Summary<Domain> summaryPrecondition) {
        return summaryCache.contains(calleeMethod, summaryPrecondition);
    }

    public void putSummary(AnalysisMethod calleeMethod, Summary<Domain> summary) {
        summaryCache.put(calleeMethod, summary);
    }

    public int getSummaryAmount(AnalysisMethod method) {
        return summaryCache.getMethodSummariesAmount(method);
    }

    public int getCacheCalls() {
        return summaryCache.getCacheCalls();
    }

    public int getCacheHits() {
        return summaryCache.getCacheHits();
    }

    public String getCacheStats() {
        return "Cache calls: " + getCacheCalls() + ", Cache hits: " + getCacheHits();
    }
}
