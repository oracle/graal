package com.oracle.svm.hosted.analysis.ai.example.leaks.set.inter;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.analyzer.InterProceduralAnalyzer;
import com.oracle.svm.hosted.analysis.ai.analyzer.payload.filter.SkipJavaLangAnalysisMethodFilter;
import com.oracle.svm.hosted.analysis.ai.domain.SetDomain;
import com.oracle.svm.hosted.analysis.ai.example.leaks.set.LeaksIdSetNodeInterpreter;
import com.oracle.svm.hosted.analysis.ai.example.leaks.set.ResourceId;
import jdk.graal.compiler.debug.DebugContext;

public class LeaksIdSetInterAnalyzer {

    private final InterProceduralAnalyzer<SetDomain<ResourceId>> analyzer;

    public LeaksIdSetInterAnalyzer() {
        analyzer = new InterProceduralAnalyzer.Builder<>(
                new SetDomain<>(),
                new LeaksIdSetNodeInterpreter(),
                new LeaksIdSetSummaryFactory())
                .addMethodFilter(new SkipJavaLangAnalysisMethodFilter())
                .build();
    }

    public void run(AnalysisMethod method, DebugContext debug) {
        analyzer.analyzeMethod(method, debug);
    }
}
