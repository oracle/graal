package com.oracle.svm.hosted.analysis.ai.example.backward;

import com.oracle.svm.hosted.analysis.ai.analyzer.Analyzer;
import com.oracle.svm.hosted.analysis.ai.analyzer.IntraProceduralAnalyzer;
import com.oracle.svm.hosted.analysis.ai.analyzer.payload.filter.SkipJavaLangAnalysisMethodFilter;
import com.oracle.svm.hosted.analysis.ai.domain.EmptyDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.policy.IteratorPolicy;

/**
 * This class demonstrates a backward analysis using the IntraProceduralAnalyzer.
 * It uses an empty domain and an empty domain interpreter for the analysis.
 */
public class BackwardAnalyzerWrapper {

    private final IntraProceduralAnalyzer<EmptyDomain> analyzer;

    public BackwardAnalyzerWrapper() {
        analyzer = new IntraProceduralAnalyzer.Builder<>(
                new EmptyDomain(),
                new EmptyDomainInterpreter())
                .iteratorPolicy(IteratorPolicy.DEFAULT_BACKWARD_WORKLIST)
                .addMethodFilter(new SkipJavaLangAnalysisMethodFilter())
                .build();
    }

    public Analyzer<EmptyDomain> getAnalyzer() {
        return analyzer;
    }
}
