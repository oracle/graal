package com.oracle.svm.hosted.analysis.ai.example.access.intra;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.analyzer.IntraProceduralAnalyzer;
import com.oracle.svm.hosted.analysis.ai.domain.EnvironmentDomain;
import com.oracle.svm.hosted.analysis.ai.domain.IntInterval;
import com.oracle.svm.hosted.analysis.ai.example.access.AccessPathIntervalNodeInterpreter;
import jdk.graal.compiler.debug.DebugContext;

import java.io.IOException;

public class AccessPathIntervalIntraAnalyzer {

    private final IntraProceduralAnalyzer<EnvironmentDomain<IntInterval>> analyzer;

    public AccessPathIntervalIntraAnalyzer() {
        analyzer = new IntraProceduralAnalyzer.Builder<>(
                new EnvironmentDomain<>(new IntInterval()),
                new AccessPathIntervalNodeInterpreter())
                .build();
    }

    public void run(AnalysisMethod method, DebugContext debug) throws IOException {
        analyzer.analyzeMethod(method, debug);
    }
}
