package com.oracle.svm.hosted.analysis.ai.analysis.context;

import com.oracle.svm.hosted.analysis.ai.checker.core.CheckerManager;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.policy.IteratorPolicy;
import com.oracle.svm.hosted.analysis.ai.analysis.methodfilter.AnalysisMethodFilterManager;
import com.oracle.svm.hosted.analysis.ai.summary.SummaryFactory;

/**
 * Central analysis-scoped configuration and services.
 * Single source of truth for iteration policy and managers that are
 * shared across methods and call graphs.
 */
public final class AnalysisContext<Domain extends AbstractDomain<Domain>> {

    private final IteratorPolicy iteratorPolicy;
    private final CheckerManager checkerManager;
    private final AnalysisMethodFilterManager methodFilterManager;
    private final SummaryFactory<Domain> summaryFactory; /* may be null for intra */
    private final MethodGraphCache methodGraphCache = new MethodGraphCache();

    public AnalysisContext(IteratorPolicy iteratorPolicy,
                           CheckerManager checkerManager,
                           AnalysisMethodFilterManager methodFilterManager) {
        this(iteratorPolicy, checkerManager, methodFilterManager, null);
    }

    public AnalysisContext(IteratorPolicy iteratorPolicy,
                           CheckerManager checkerManager,
                           AnalysisMethodFilterManager methodFilterManager,
                           SummaryFactory<Domain> summaryFactory) {
        this.iteratorPolicy = iteratorPolicy;
        this.checkerManager = checkerManager;
        this.methodFilterManager = methodFilterManager;
        this.summaryFactory = summaryFactory;
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

    public SummaryFactory<Domain> getSummaryFactory() {
        return summaryFactory;
    }

    public MethodGraphCache getMethodGraphCache() {
        return methodGraphCache;
    }
}
