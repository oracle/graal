package com.oracle.svm.hosted.analysis.ai.analyzer.metadata;

import com.oracle.svm.hosted.analysis.ai.checker.CheckerManager;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.policy.IteratorPolicy;
import com.oracle.svm.hosted.analysis.ai.analyzer.metadata.filter.AnalysisMethodFilterManager;
import com.oracle.svm.hosted.analysis.ai.summary.SummaryFactory;

/**
 * Central analysis-scoped configuration and services.
 * Single source of truth for iteration policy and managers that are
 * shared across methods and call graphs.
 */
public final class AnalysisContext {
    private final IteratorPolicy iteratorPolicy;
    private final CheckerManager checkerManager;
    private final AnalysisMethodFilterManager methodFilterManager;
    private final SummaryFactory<?> summaryFactory; /* may be null for intra */
    private final Integer maxRecursionDepth; /* may be null for intra */
    private final MethodGraphCache methodGraphCache = new MethodGraphCache();

    public AnalysisContext(IteratorPolicy iteratorPolicy,
                           CheckerManager checkerManager,
                           AnalysisMethodFilterManager methodFilterManager) {
        this(iteratorPolicy, checkerManager, methodFilterManager, null, null);
    }

    public AnalysisContext(IteratorPolicy iteratorPolicy,
                           CheckerManager checkerManager,
                           AnalysisMethodFilterManager methodFilterManager,
                           SummaryFactory<?> summaryFactory,
                           Integer maxRecursionDepth) {
        this.iteratorPolicy = iteratorPolicy;
        this.checkerManager = checkerManager;
        this.methodFilterManager = methodFilterManager;
        this.summaryFactory = summaryFactory;
        this.maxRecursionDepth = maxRecursionDepth;
    }

    public IteratorPolicy getIteratorPolicy() {
        return iteratorPolicy;
    }

    public CheckerManager getCheckerManager() {
        return checkerManager;
    }

    public AnalysisMethodFilterManager getMethodFilterManager() {
        return methodFilterManager;
    }

    public SummaryFactory<?> getSummaryFactory() {
        return summaryFactory;
    }

    public Integer getMaxRecursionDepth() {
        return maxRecursionDepth;
    }

    public MethodGraphCache getMethodGraphCache() {
        return methodGraphCache;
    }
}
