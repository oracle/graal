package com.oracle.svm.hosted.analysis.ai.example.leaks.set.intra;

import com.oracle.svm.hosted.analysis.ai.analyzer.Analyzer;
import com.oracle.svm.hosted.analysis.ai.analyzer.IntraProceduralAnalyzer;
import com.oracle.svm.hosted.analysis.ai.checker.example.ResourceLeaksChecker;
import com.oracle.svm.hosted.analysis.ai.domain.SetDomain;
import com.oracle.svm.hosted.analysis.ai.example.leaks.set.LeaksIdSetNodeInterpreter;
import com.oracle.svm.hosted.analysis.ai.example.leaks.set.ResourceId;

public class LeaksIdSetIntraAnalyzerWrapper {

    private final IntraProceduralAnalyzer<SetDomain<ResourceId>> analyzer;

    public LeaksIdSetIntraAnalyzerWrapper() {
        analyzer = new IntraProceduralAnalyzer.Builder<>(
                new SetDomain<>(),
                new LeaksIdSetNodeInterpreter())
                .registerChecker(new ResourceLeaksChecker())
                .build();
    }

    public Analyzer<SetDomain<ResourceId>> getAnalyzer() {
        return analyzer;
    }
}

