package com.oracle.svm.hosted.analysis.ai.example.access.inter;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.analyzer.InterProceduralAnalyzer;
import com.oracle.svm.hosted.analysis.ai.analyzer.InterProceduralAnalyzer.Builder;
import com.oracle.svm.hosted.analysis.ai.domain.EnvironmentDomain;
import com.oracle.svm.hosted.analysis.ai.domain.IntInterval;
import com.oracle.svm.hosted.analysis.ai.example.access.AccessPathIntervalNodeInterpreter;
import jdk.graal.compiler.debug.DebugContext;

import java.io.IOException;

/**
 * This analyzer assigns intervals to accessPaths.
 */
public class AccessPathIntervalInterAnalyzer {

    private final InterProceduralAnalyzer<EnvironmentDomain<IntInterval>> analyzer;

    public AccessPathIntervalInterAnalyzer() {
        analyzer = new Builder<>(
                new EnvironmentDomain<>(new IntInterval()),
                new AccessPathIntervalNodeInterpreter(),
                new AccessPathIntervalSummaryFactory())
                .build();
    }

    public void run(AnalysisMethod method, DebugContext debug) throws IOException {
        analyzer.analyzeMethod(method, debug);
    }
}
