package com.oracle.svm.hosted.analysis.ai.example.leaks.set.inter;

import com.oracle.svm.hosted.analysis.ai.analyzer.Analyzer;
import com.oracle.svm.hosted.analysis.ai.analyzer.InterProceduralAnalyzer;
import com.oracle.svm.hosted.analysis.ai.analyzer.payload.filter.SkipJavaLangAnalysisMethodFilter;
import com.oracle.svm.hosted.analysis.ai.checker.ResourceLeaksChecker;
import com.oracle.svm.hosted.analysis.ai.domain.SetDomain;
import com.oracle.svm.hosted.analysis.ai.example.leaks.set.LeaksIdSetAbstractInterpreter;
import com.oracle.svm.hosted.analysis.ai.example.leaks.set.ResourceId;

public class LeaksIdSetInterAnalyzerWrapper {

    private final InterProceduralAnalyzer<SetDomain<ResourceId>> analyzer;

    public LeaksIdSetInterAnalyzerWrapper() {
        analyzer = new InterProceduralAnalyzer.Builder<>(
                new SetDomain<>(),
                new LeaksIdSetAbstractInterpreter(),
                new LeaksIdSetSummaryFactory())
                .addMethodFilter(new SkipJavaLangAnalysisMethodFilter())
                .registerChecker(new ResourceLeaksChecker())
                .build();
    }

    public Analyzer<SetDomain<ResourceId>> getAnalyzer() {
        return analyzer;
    }
}
