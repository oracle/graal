package com.oracle.svm.hosted.analysis.ai.example.access.inter;

import com.oracle.svm.hosted.analysis.ai.analyzer.Analyzer;
import com.oracle.svm.hosted.analysis.ai.analyzer.InterProceduralAnalyzer;
import com.oracle.svm.hosted.analysis.ai.analyzer.InterProceduralAnalyzer.Builder;
import com.oracle.svm.hosted.analysis.ai.analyzer.payload.filter.SkipJavaLangAnalysisMethodFilter;
import com.oracle.svm.hosted.analysis.ai.domain.access.AccessPathMap;
import com.oracle.svm.hosted.analysis.ai.domain.numerical.IntInterval;
import com.oracle.svm.hosted.analysis.ai.example.access.AccessPathIntervalAbstractInterpreter;

/**
 * This analyzer assigns intervals to accessPaths.
 */
public class AccessPathIntervalInterAnalyzerWrapper {

    private final InterProceduralAnalyzer<AccessPathMap<IntInterval>> analyzer;

    public AccessPathIntervalInterAnalyzerWrapper() {
        analyzer = new Builder<>(
                new AccessPathMap<>(new IntInterval()),
                new AccessPathIntervalAbstractInterpreter(),
                new AccessPathIntervalSummaryFactory())
                .addMethodFilter(new SkipJavaLangAnalysisMethodFilter())
                .build();
    }

    public Analyzer<AccessPathMap<IntInterval>> getAnalyzer() {
        return analyzer;
    }
}
