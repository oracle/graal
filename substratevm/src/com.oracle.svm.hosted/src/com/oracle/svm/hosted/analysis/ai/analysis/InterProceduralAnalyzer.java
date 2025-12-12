package com.oracle.svm.hosted.analysis.ai.analysis;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.analysis.invokehandle.InterAbsintInvokeHandler;
import com.oracle.svm.hosted.analysis.ai.analysis.context.AnalysisContext;
import com.oracle.svm.hosted.analysis.ai.analysis.context.CallStack;
import com.oracle.svm.hosted.analysis.ai.analysis.mode.InterAnalyzerMode;
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
    private final int maxRecursionDepth;
    private final int maxCallStackDepth;

    private InterProceduralAnalyzer(Builder<Domain> builder) {
        super(builder);
        this.analyzerMode = builder.analyzerMode;
        this.summaryManager = new SummaryManager<>(builder.summaryFactory);
        this.maxRecursionDepth = builder.maxRecursionDepth;
        this.maxCallStackDepth = builder.maxCallStackDepth;
        this.analysisContext = new AnalysisContext(builder.iteratorPolicy, builder.checkerManager, builder.methodFilterManager, builder.summaryFactory);
    }

    public InterAnalyzerMode getAnalyzerMode() {
        return analyzerMode;
    }

    public AnalysisContext getAnalysisContext() {
        return analysisContext;
    }

    public SummaryManager<Domain> getSummaryManager() {
        return summaryManager;
    }

    @Override
    public void runAnalysis(AnalysisMethod method) {
        if (method == null) {
            return;
        }
        InterAbsintInvokeHandler<Domain> callHandler = new InterAbsintInvokeHandler<>(initialDomain, abstractInterpreter, analysisContext, new CallStack(maxRecursionDepth, maxCallStackDepth), summaryManager);
        callHandler.handleRootInvoke(method);
    }

    public static class Builder<Domain extends AbstractDomain<Domain>> extends Analyzer.Builder<Builder<Domain>, Domain> {
        private static final int DEFAULT_MAX_RECURSION_DEPTH = 16;
        private static final int DEFAULT_MAX_CALLSTACK_DEPTH = 128;
        private int maxRecursionDepth = DEFAULT_MAX_RECURSION_DEPTH;
        private int maxCallStackDepth = DEFAULT_MAX_CALLSTACK_DEPTH;
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

        public Builder<Domain> maxCallStackDepth(int maxCallStackDepth) {
            this.maxCallStackDepth = maxCallStackDepth;
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
