package com.oracle.svm.hosted.analysis.ai.analyzer;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.analyzer.call.IntraProceduralInvokeHandler;
import com.oracle.svm.hosted.analysis.ai.analyzer.payload.IteratorPayload;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.interpreter.AbstractInterpreter;

/**
 * An intra-procedural analyzer that performs an intra-procedural analysis on the given method.
 *
 * @param <Domain> the type of the abstract domain used for the analysis.
 */
public final class IntraProceduralAnalyzer<Domain extends AbstractDomain<Domain>> extends Analyzer<Domain> {

    private IntraProceduralAnalyzer(Builder<Domain> builder) {
        super(builder);
    }

    /**
     * The {@code runAnalysis} method is responsible for executing the analysis on the given method.
     *
     * @param method the method to be analyzed.
     */
    @Override
    public void runAnalysis(AnalysisMethod method) {
        IteratorPayload iteratorPayload = new IteratorPayload(iteratorPolicy);
        IntraProceduralInvokeHandler<Domain> callHandler = new IntraProceduralInvokeHandler<>(initialDomain, abstractInterpreter, checkerManager, methodFilterManager, iteratorPayload);
        callHandler.handleRootInvoke(method);
    }

    public static class Builder<Domain extends AbstractDomain<Domain>> extends Analyzer.Builder<Builder<Domain>, Domain> {

        public Builder(Domain initialDomain, AbstractInterpreter<Domain> abstractInterpreter) {
            super(initialDomain, abstractInterpreter);
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
