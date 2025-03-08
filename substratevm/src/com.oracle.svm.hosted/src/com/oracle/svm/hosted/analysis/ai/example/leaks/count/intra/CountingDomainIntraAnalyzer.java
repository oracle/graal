package com.oracle.svm.hosted.analysis.ai.example.leaks.count.intra;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.analyzer.IntraProceduralAnalyzer;
import com.oracle.svm.hosted.analysis.ai.domain.CountDomain;
import com.oracle.svm.hosted.analysis.ai.example.leaks.count.LeaksCountingDomainNodeInterpreter;
import jdk.graal.compiler.debug.DebugContext;

/**
 * Example of a simple intra-procedural leaks analysis domain that
 * counts the number of FileInputStream objects opened in a analysisMethod.
 */
public class CountingDomainIntraAnalyzer {

    private final IntraProceduralAnalyzer<CountDomain> analyzer;

    public CountingDomainIntraAnalyzer() {
        analyzer = new IntraProceduralAnalyzer.Builder<>(
                new CountDomain(1024),
                new LeaksCountingDomainNodeInterpreter())
                .build();
    }

    public void run(AnalysisMethod method, DebugContext debug) {
        analyzer.analyzeMethod(method, debug);
    }
}
