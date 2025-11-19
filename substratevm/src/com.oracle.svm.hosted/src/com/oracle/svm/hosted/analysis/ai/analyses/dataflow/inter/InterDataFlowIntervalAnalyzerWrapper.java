package com.oracle.svm.hosted.analysis.ai.analyses.dataflow.inter;

import com.oracle.svm.hosted.analysis.ai.analyzer.AnalyzerManager;
import com.oracle.svm.hosted.analysis.ai.analyzer.InterProceduralAnalyzer;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.policy.IteratorPolicy;
import com.oracle.svm.hosted.analysis.ai.domain.memory.AbstractMemory;
import com.oracle.svm.hosted.analysis.ai.interpreter.AbstractInterpreter;

/**
 * Helper to construct and register an inter-procedural dataflow analyzer using the interval memory domain.
 */
public final class InterDataFlowIntervalAnalyzerWrapper {

    private InterDataFlowIntervalAnalyzerWrapper() {}

    public static InterProceduralAnalyzer<AbstractMemory> build(AbstractInterpreter<AbstractMemory> interpreter) {
        return new InterProceduralAnalyzer.Builder<>(new AbstractMemory(), interpreter, new DataFlowIntervalAnalysisSummaryFactory())
                .iteratorPolicy(IteratorPolicy.DEFAULT_FORWARD_WTO)
                .build();
    }

    public static void register(AnalyzerManager manager, AbstractInterpreter<AbstractMemory> interpreter) {
        manager.registerAnalyzer(build(interpreter));
    }
}
