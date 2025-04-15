package com.oracle.svm.hosted.analysis.ai.analyzer;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.analyzer.call.InterProceduralCallHandler;
import com.oracle.svm.hosted.analysis.ai.analyzer.payload.IteratorPayload;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.interpreter.NodeInterpreter;
import com.oracle.svm.hosted.analysis.ai.summary.SummaryFactory;
import com.oracle.svm.hosted.analysis.ai.util.InterAnalysisConstants;
import jdk.graal.compiler.debug.DebugContext;

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
    public void analyzeMethod(AnalysisMethod method, DebugContext debug) {
        IteratorPayload iteratorPayload = new IteratorPayload(iteratorPolicy);
        InterProceduralCallHandler<Domain> callHandler = new InterProceduralCallHandler<>(initialDomain, nodeInterpreter, checkerManager, methodFilterManager, iteratorPayload, summaryFactory, maxRecursionDepth);
        callHandler.handleRootCall(method, debug);
    }

    public static class Builder<Domain extends AbstractDomain<Domain>> extends Analyzer.Builder<Builder<Domain>, Domain> {

        private final SummaryFactory<Domain> summaryFactory;
        private int maxRecursionDepth = InterAnalysisConstants.MAX_RECURSION_DEPTH;

        public Builder(Domain initialDomain, NodeInterpreter<Domain> nodeInterpreter, SummaryFactory<Domain> summaryFactory) {
            super(initialDomain, nodeInterpreter);
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
