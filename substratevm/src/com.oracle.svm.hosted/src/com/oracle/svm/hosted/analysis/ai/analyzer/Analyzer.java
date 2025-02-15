package com.oracle.svm.hosted.analysis.ai.analyzer;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.analyzer.payload.AnalysisPayload;
import com.oracle.svm.hosted.analysis.ai.analyzer.payload.IteratorPayload;
import com.oracle.svm.hosted.analysis.ai.analyzer.payload.filter.AnalysisMethodFilterManager;
import com.oracle.svm.hosted.analysis.ai.checker.CheckerManager;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.policy.IteratorPolicy;
import com.oracle.svm.hosted.analysis.ai.interpreter.NodeInterpreter;
import com.oracle.svm.hosted.analysis.ai.interpreter.TransferFunction;
import com.oracle.svm.hosted.analysis.ai.interpreter.call.CallHandler;
import jdk.graal.compiler.debug.DebugContext;

public abstract class Analyzer<Domain extends AbstractDomain<Domain>> {

    protected final Domain initialDomain;
    protected final NodeInterpreter<Domain> nodeInterpreter;
    protected final IteratorPolicy iteratorPolicy;
    protected final CheckerManager checkerManager;
    protected final AnalysisMethodFilterManager methodFilterManager;

    protected Analyzer(Builder<Domain> builder) {
        this.initialDomain = builder.initialDomain;
        this.nodeInterpreter = builder.nodeInterpreter;
        this.iteratorPolicy = builder.iteratorPolicy;
        this.checkerManager = builder.checkerManager;
        this.methodFilterManager = builder.methodFilterManager;
    }

    protected AnalysisPayload<Domain> createPayload(AnalysisMethod method, DebugContext debug, CallHandler<Domain> callHandler) {
        TransferFunction<Domain> transferFunction = new TransferFunction<>(nodeInterpreter, callHandler);
        IteratorPayload iteratorPayload = new IteratorPayload(iteratorPolicy);
        return new AnalysisPayload<>(initialDomain, iteratorPayload, method, debug, transferFunction, checkerManager, methodFilterManager);
    }

    public abstract void analyzeMethod(AnalysisMethod method, DebugContext debug);

    public static class Builder<Domain extends AbstractDomain<Domain>> {
        protected final Domain initialDomain;
        protected final NodeInterpreter<Domain> nodeInterpreter;
        protected IteratorPolicy iteratorPolicy = IteratorPolicy.DEFAULT_SEQUENTIAL;
        protected CheckerManager checkerManager = new CheckerManager();
        protected AnalysisMethodFilterManager methodFilterManager = new AnalysisMethodFilterManager();

        public Builder(Domain initialDomain, NodeInterpreter<Domain> nodeInterpreter) {
            this.initialDomain = initialDomain;
            this.nodeInterpreter = nodeInterpreter;
        }

        public Builder<Domain> iteratorPolicy(IteratorPolicy iteratorPolicy) {
            this.iteratorPolicy = iteratorPolicy;
            return this;
        }

        public Builder<Domain> checkerManager(CheckerManager checkerManager) {
            this.checkerManager = checkerManager;
            return this;
        }

        public Builder<Domain> methodFilterManager(AnalysisMethodFilterManager methodFilterManager) {
            this.methodFilterManager = methodFilterManager;
            return this;
        }
    }
}