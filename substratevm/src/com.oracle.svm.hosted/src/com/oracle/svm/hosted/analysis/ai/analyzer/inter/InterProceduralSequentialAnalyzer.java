package com.oracle.svm.hosted.analysis.ai.analyzer.inter;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.analyzer.Analyzer;
import com.oracle.svm.hosted.analysis.ai.analyzer.payload.AnalysisPayload;
import com.oracle.svm.hosted.analysis.ai.analyzer.payload.IteratorPayload;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.ConcurrentWpoFixpointIterator;
import com.oracle.svm.hosted.analysis.ai.interpreter.NodeInterpreter;
import com.oracle.svm.hosted.analysis.ai.interpreter.call.InterProceduralCallHandler;
import com.oracle.svm.hosted.analysis.ai.summary.SummaryFactory;
import jdk.graal.compiler.debug.DebugContext;

public final class InterProceduralSequentialAnalyzer<Domain extends AbstractDomain<Domain>> extends Analyzer<Domain> {

    private final SummaryFactory<Domain> summaryFactory;
    private final int maxRecursionDepth;

    private InterProceduralSequentialAnalyzer(Builder<Domain> builder) {
        super(builder);
        this.summaryFactory = builder.summaryFactory;
        this.maxRecursionDepth = builder.maxRecursionDepth;
    }

    @Override
    public void analyzeMethod(AnalysisMethod method, DebugContext debug) {
        InterProceduralCallHandler<Domain> callHandler = new InterProceduralCallHandler<>(summaryFactory, new IteratorPayload(iteratorPolicy), maxRecursionDepth);
        AnalysisPayload<Domain> payload = createPayload(method, debug, callHandler);
        payload.getLogger().logHighlightedDebugInfo("Running inter-procedural analysis");
        ConcurrentWpoFixpointIterator<Domain> iterator = new ConcurrentWpoFixpointIterator<>(payload);
        iterator.iterateUntilFixpoint();
        payload.getLogger().logHighlightedDebugInfo("The computed summaries are: ");
        payload.getLogger().logDebugInfo(callHandler.getSummaryCache().toString());
    }

    public static class Builder<Domain extends AbstractDomain<Domain>> extends Analyzer.Builder<Domain> {
        private final SummaryFactory<Domain> summaryFactory;
        private int maxRecursionDepth = 10;

        public Builder(Domain initialDomain, NodeInterpreter<Domain> nodeInterpreter, SummaryFactory<Domain> summaryFactory) {
            super(initialDomain, nodeInterpreter);
            this.summaryFactory = summaryFactory;
        }

        public Builder<Domain> maxRecursionDepth(int maxRecursionDepth) {
            this.maxRecursionDepth = maxRecursionDepth;
            return this;
        }

        public InterProceduralSequentialAnalyzer<Domain> build() {
            return new InterProceduralSequentialAnalyzer<>(this);
        }
    }
}