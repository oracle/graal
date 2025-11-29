package com.oracle.svm.hosted.analysis.ai.analyzer;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.analyzer.call.IntraAbsintInvokeHandler;
import com.oracle.svm.hosted.analysis.ai.analyzer.metadata.AnalysisContext;
import com.oracle.svm.hosted.analysis.ai.analyzer.mode.IntraAnalyzerMode;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.policy.IteratorPolicy;
import com.oracle.svm.hosted.analysis.ai.interpreter.AbstractInterpreter;

/**
 * An intra-procedural analyzer that performs an intra-procedural analysis on the given method.
 *
 * @param <Domain> the type of the abstract domain used for the analysis.
 */
public final class IntraProceduralAnalyzer<Domain extends AbstractDomain<Domain>> extends Analyzer<Domain> {

    private final IntraAnalyzerMode analyzerMode;
    private final AnalysisContext analysisContext;

    private IntraProceduralAnalyzer(Builder<Domain> builder) {
        super(builder);
        this.analyzerMode = builder.analyzerMode;
        this.analysisContext = new AnalysisContext(builder.iteratorPolicy, builder.checkerManager, builder.methodFilterManager);
    }

    public IntraAnalyzerMode getAnalyzerMode() {
        return analyzerMode;
    }

    @Override
    public void runAnalysis(AnalysisMethod method) {
        IntraAbsintInvokeHandler<Domain> callHandler = new IntraAbsintInvokeHandler<>(initialDomain, abstractInterpreter, analysisContext);
        callHandler.handleRootInvoke(method);
    }

    public static class Builder<Domain extends AbstractDomain<Domain>> extends Analyzer.Builder<Builder<Domain>, Domain> {

        private final IntraAnalyzerMode analyzerMode;

        public Builder(Domain initialDomain, AbstractInterpreter<Domain> abstractInterpreter, IntraAnalyzerMode analyzerMode) {
            super(initialDomain, abstractInterpreter);
            this.analyzerMode = analyzerMode;
        }

        public IntraProceduralAnalyzer<Domain> build() {
            return new IntraProceduralAnalyzer<>(this);
        }

        @Override
        protected Builder<Domain> self() {
            return this;
        }
    }
}
