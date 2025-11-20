package com.oracle.svm.hosted.analysis.ai.analyzer;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.analyzer.call.InterAbsintInvokeHandler;
import com.oracle.svm.hosted.analysis.ai.analyzer.metadata.AnalysisContext;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.interpreter.AbstractInterpreter;
import com.oracle.svm.hosted.analysis.ai.summary.SummaryFactory;

/**
 * An inter-procedural analyzer that performs an inter-procedural analysis on the given method.
 *
 * @param <Domain> the type of the abstract domain used for the analysis.
 */
public final class InterProceduralAnalyzer<Domain extends AbstractDomain<Domain>> extends Analyzer<Domain> {

    private final SummaryFactory<Domain> summaryFactory;
    private final int maxRecursionDepth;

    private InterProceduralAnalyzer(Builder<Domain> builder) {
        super(builder);
        this.summaryFactory = builder.summaryFactory;
        this.maxRecursionDepth = builder.maxRecursionDepth;
    }

    @Override
    public void runAnalysis(AnalysisMethod method) {
        AnalysisContext analysisContext = new AnalysisContext(iteratorPolicy, checkerManager, methodFilterManager, summaryFactory);
        InterAbsintInvokeHandler<Domain> callHandler = new InterAbsintInvokeHandler<>(initialDomain, abstractInterpreter, analysisContext, summaryFactory, maxRecursionDepth);
        callHandler.handleRootInvoke(method);
    }

    public static class Builder<Domain extends AbstractDomain<Domain>> extends Analyzer.Builder<Builder<Domain>, Domain> {
        private static final int DEFAULT_MAX_RECURSION_DEPTH = 64;
        private final SummaryFactory<Domain> summaryFactory;
        private int maxRecursionDepth = DEFAULT_MAX_RECURSION_DEPTH;

        public Builder(Domain initialDomain, AbstractInterpreter<Domain> abstractInterpreter, SummaryFactory<Domain> summaryFactory) {
            super(initialDomain, abstractInterpreter);
            this.summaryFactory = summaryFactory;
        }

        public Builder<Domain> maxRecursionDepth(int maxRecursionDepth) {
            this.maxRecursionDepth = maxRecursionDepth;
            return self();
        }

        public InterProceduralAnalyzer<Domain> build() {
            return new InterProceduralAnalyzer<>(this);
        }

        @Override
        protected Builder<Domain> self() {
            return this;
        }
    }
}
