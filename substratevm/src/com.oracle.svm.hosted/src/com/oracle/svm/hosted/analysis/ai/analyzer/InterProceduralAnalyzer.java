package com.oracle.svm.hosted.analysis.ai.analyzer;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.analyzer.call.InterAbsintInvokeHandler;
import com.oracle.svm.hosted.analysis.ai.analyzer.metadata.AnalysisContext;
import com.oracle.svm.hosted.analysis.ai.analyzer.metadata.CallStack;
import com.oracle.svm.hosted.analysis.ai.analyzer.mode.InterAnalyzerMode;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.interpreter.AbstractInterpreter;
import com.oracle.svm.hosted.analysis.ai.summary.SummaryFactory;
import com.oracle.svm.hosted.analysis.ai.summary.SummaryManager;

/**
 * An inter-procedural analyzer that performs an inter-procedural analysis on the given method.
 *
 * @param <Domain> the type of the abstract domain used for the analysis.
 */
public final class InterProceduralAnalyzer<Domain extends AbstractDomain<Domain>> extends Analyzer<Domain> {

    private final InterAnalyzerMode analyzerMode;
    private final AnalysisContext analysisContext;
    private final SummaryManager<Domain> summaryManager;
    private final CallStack callStack;

    private InterProceduralAnalyzer(Builder<Domain> builder) {
        super(builder);
        this.analyzerMode = builder.analyzerMode;
        this.summaryManager = new SummaryManager<>(builder.summaryFactory);
        this.callStack = new CallStack(builder.maxRecursionDepth);
        this.analysisContext = new AnalysisContext(builder.iteratorPolicy, builder.checkerManager, builder.methodFilterManager, builder.summaryFactory);
    }

    public InterAnalyzerMode getAnalyzerMode() {
        return analyzerMode;
    }

    @Override
    public void runAnalysis(AnalysisMethod method) {
        InterAbsintInvokeHandler<Domain> callHandler = new InterAbsintInvokeHandler<>(initialDomain, abstractInterpreter, analysisContext, callStack, summaryManager);
        callHandler.handleRootInvoke(method);
        checkerManager.runCheckersOnMethodSummaries(summaryManager.getSummaryRepository().getMethodSummaryMap(), analysisContext.getMethodGraphCache().getMethodGraphMap());
    }

    public static class Builder<Domain extends AbstractDomain<Domain>> extends Analyzer.Builder<Builder<Domain>, Domain> {
        private static final int DEFAULT_MAX_RECURSION_DEPTH = 64;
        private int maxRecursionDepth = DEFAULT_MAX_RECURSION_DEPTH;
        private final SummaryFactory<Domain> summaryFactory;
        private final InterAnalyzerMode analyzerMode;

        public Builder(Domain initialDomain,
                       AbstractInterpreter<Domain> abstractInterpreter,
                       SummaryFactory<Domain> summaryFactory,
                       InterAnalyzerMode analyzerMode) {
            super(initialDomain, abstractInterpreter);
            this.summaryFactory = summaryFactory;
            this.analyzerMode = analyzerMode;
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
