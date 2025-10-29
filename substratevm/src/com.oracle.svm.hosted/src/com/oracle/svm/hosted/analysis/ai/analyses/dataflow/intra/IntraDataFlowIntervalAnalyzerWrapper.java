package com.oracle.svm.hosted.analysis.ai.analyses.dataflow.intra;

import com.oracle.svm.hosted.analysis.ai.analyses.dataflow.DataFlowIntervalAbstractInterpreter;
import com.oracle.svm.hosted.analysis.ai.analyzer.Analyzer;
import com.oracle.svm.hosted.analysis.ai.analyzer.IntraProceduralAnalyzer;
import com.oracle.svm.hosted.analysis.ai.analyzer.metadata.filter.SkipJavaLangAnalysisMethodFilter;
import com.oracle.svm.hosted.analysis.ai.domain.memory.AbstractMemory;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.policy.IteratorPolicy;

public class IntraDataFlowIntervalAnalyzerWrapper {
    private final IntraProceduralAnalyzer<AbstractMemory> analyzer;

    public IntraDataFlowIntervalAnalyzerWrapper() {
        analyzer = new IntraProceduralAnalyzer.Builder<>(
                new AbstractMemory(),
                new DataFlowIntervalAbstractInterpreter())
                .iteratorPolicy(IteratorPolicy.DEFAULT_FORWARD_WTO)
                .addMethodFilter(new SkipJavaLangAnalysisMethodFilter())
                .build();
    }

    public Analyzer<AbstractMemory> getAnalyzer() {
        return analyzer;
    }
}
