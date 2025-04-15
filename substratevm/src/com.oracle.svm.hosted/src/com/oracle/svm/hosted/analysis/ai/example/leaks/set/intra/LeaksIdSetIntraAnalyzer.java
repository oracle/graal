package com.oracle.svm.hosted.analysis.ai.example.leaks.set.intra;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.analyzer.IntraProceduralAnalyzer;
import com.oracle.svm.hosted.analysis.ai.domain.SetDomain;
import com.oracle.svm.hosted.analysis.ai.example.leaks.set.LeaksIdSetNodeInterpreter;
import com.oracle.svm.hosted.analysis.ai.example.leaks.set.ResourceId;
import jdk.graal.compiler.debug.DebugContext;

public class LeaksIdSetIntraAnalyzer {

    private final IntraProceduralAnalyzer<SetDomain<ResourceId>> analyzer;

    public LeaksIdSetIntraAnalyzer() {
        analyzer = new IntraProceduralAnalyzer.Builder<>(
                new SetDomain<>(),
                new LeaksIdSetNodeInterpreter())
                .build();
    }

    public void run(AnalysisMethod method, DebugContext debug) {
        analyzer.analyzeMethod(method, debug);
    }
}
