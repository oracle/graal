package com.oracle.svm.hosted.analysis.ai.example.access.intra;

import com.oracle.svm.hosted.analysis.ai.analyzer.Analyzer;
import com.oracle.svm.hosted.analysis.ai.analyzer.IntraProceduralAnalyzer;
import com.oracle.svm.hosted.analysis.ai.domain.access.AccessPathMap;
import com.oracle.svm.hosted.analysis.ai.domain.numerical.IntInterval;
import com.oracle.svm.hosted.analysis.ai.example.access.AccessPathIntervalNodeInterpreter;

public class AccessPathIntervalIntraAnalyzerWrapper {

    private final IntraProceduralAnalyzer<AccessPathMap<IntInterval>> analyzer;

    public AccessPathIntervalIntraAnalyzerWrapper() {
        analyzer = new IntraProceduralAnalyzer.Builder<>(
                new AccessPathMap<>(new IntInterval()),
                new AccessPathIntervalNodeInterpreter())
                .build();
    }

    public Analyzer<AccessPathMap<IntInterval>> getAnalyzer() {
        return analyzer;
    }
}
