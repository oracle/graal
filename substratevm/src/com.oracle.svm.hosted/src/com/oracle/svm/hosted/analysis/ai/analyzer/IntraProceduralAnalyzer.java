package com.oracle.svm.hosted.analysis.ai.analyzer;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.analyzer.call.IntraProceduralCallHandler;
import com.oracle.svm.hosted.analysis.ai.analyzer.payload.IteratorPayload;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.interpreter.NodeInterpreter;
import jdk.graal.compiler.debug.DebugContext;

/**
 * An intra-procedural analyzer that performs an intra-procedural analysis on the given method.
 * @param <Domain> the type of the abstract domain used for the analysis.
 */
public final class IntraProceduralAnalyzer<Domain extends AbstractDomain<Domain>> extends Analyzer<Domain> {

    private IntraProceduralAnalyzer(Builder<Domain> builder) {
        super(builder);
    }

    @Override
    public void analyzeMethod(AnalysisMethod method, DebugContext debug) {
        IteratorPayload iteratorPayload = new IteratorPayload(iteratorPolicy);
        IntraProceduralCallHandler<Domain> callHandler = new IntraProceduralCallHandler<>(initialDomain, nodeInterpreter, checkerManager, methodFilterManager, iteratorPayload);
        callHandler.handleRootCall(method, debug);
    }

    public static class Builder<Domain extends AbstractDomain<Domain>> extends Analyzer.Builder<Builder<Domain>, Domain> {

        public Builder(Domain initialDomain, NodeInterpreter<Domain> nodeInterpreter) {
            super(initialDomain, nodeInterpreter);
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
