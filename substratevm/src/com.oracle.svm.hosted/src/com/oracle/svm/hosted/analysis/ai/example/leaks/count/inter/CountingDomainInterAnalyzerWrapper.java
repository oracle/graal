package com.oracle.svm.hosted.analysis.ai.example.leaks.count.inter;

import com.oracle.svm.hosted.analysis.ai.analyzer.Analyzer;
import com.oracle.svm.hosted.analysis.ai.analyzer.InterProceduralAnalyzer;
import com.oracle.svm.hosted.analysis.ai.domain.CountDomain;
import com.oracle.svm.hosted.analysis.ai.example.leaks.count.LeaksCountingDomainNodeInterpreter;

/**
 * Example of a simple inter-procedural leaks analysis domain
 * that counts the number of FileInputStream objects opened in a analysisMethod.
 */
public class CountingDomainInterAnalyzerWrapper {

    private final InterProceduralAnalyzer<CountDomain> analyzer;

    public CountingDomainInterAnalyzerWrapper() {
        analyzer = new InterProceduralAnalyzer.Builder<>(
                new CountDomain(1024),
                new LeaksCountingDomainNodeInterpreter(),
                new LeakCountingSummaryFactory())
                .build();
    }

    public Analyzer<CountDomain> getAnalyzer() {
        return analyzer;
    }
}
