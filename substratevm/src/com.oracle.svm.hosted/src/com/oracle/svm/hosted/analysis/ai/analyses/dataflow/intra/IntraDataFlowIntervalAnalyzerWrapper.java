package com.oracle.svm.hosted.analysis.ai.analyses.dataflow.intra;

import com.oracle.svm.hosted.analysis.ai.analyses.dataflow.DataFlowIntervalAbstractInterpreter;
import com.oracle.svm.hosted.analysis.ai.analyzer.Analyzer;
import com.oracle.svm.hosted.analysis.ai.analyzer.IntraProceduralAnalyzer;
import com.oracle.svm.hosted.analysis.ai.analyzer.payload.filter.SkipJavaLangAnalysisMethodFilter;
import com.oracle.svm.hosted.analysis.ai.domain.memory.AbsMemory;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.policy.IteratorPolicy;

public class IntraDataFlowIntervalAnalyzerWrapper {
    private final IntraProceduralAnalyzer<AbsMemory> analyzer;

    public IntraDataFlowIntervalAnalyzerWrapper() {
        analyzer = new IntraProceduralAnalyzer.Builder<>(
                new AbsMemory(),
                new DataFlowIntervalAbstractInterpreter())
                .iteratorPolicy(IteratorPolicy.DEFAULT_FORWARD_WTO)
                .addMethodFilter(new SkipJavaLangAnalysisMethodFilter())
                .build();
    }

    public Analyzer<AbsMemory> getAnalyzer() {
        return analyzer;
    }
}
