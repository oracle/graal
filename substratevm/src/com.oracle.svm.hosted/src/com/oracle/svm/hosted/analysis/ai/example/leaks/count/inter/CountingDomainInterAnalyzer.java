package com.oracle.svm.hosted.analysis.ai.example.leaks.count.inter;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.analyzer.InterProceduralAnalyzer;
import com.oracle.svm.hosted.analysis.ai.domain.CountDomain;
import com.oracle.svm.hosted.analysis.ai.example.leaks.count.LeaksCountingDomainNodeInterpreter;
import jdk.graal.compiler.debug.DebugContext;

import java.io.IOException;

/**
 * Example of a simple inter-procedural leaks analysis domain
 * that counts the number of FileInputStream objects opened in a analysisMethod.
 */
public class CountingDomainInterAnalyzer {

    private final InterProceduralAnalyzer<CountDomain> analyzer;

    public CountingDomainInterAnalyzer() {
        analyzer = new InterProceduralAnalyzer.Builder<>(
                new CountDomain(1024),
                new LeaksCountingDomainNodeInterpreter(),
                new LeakCountingSummaryFactory())
                .build();
    }

    public void run(AnalysisMethod root, DebugContext debug) throws IOException {
        analyzer.analyzeMethod(root, debug);
    }
}
