package com.oracle.svm.hosted.analysis.ai.example.leaks.pair.inter;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.analyzer.InterProceduralAnalyzer;
import com.oracle.svm.hosted.analysis.ai.domain.BooleanOrDomain;
import com.oracle.svm.hosted.analysis.ai.domain.CountDomain;
import com.oracle.svm.hosted.analysis.ai.domain.PairDomain;
import com.oracle.svm.hosted.analysis.ai.example.leaks.pair.LeaksPairDomainNodeInterpreter;
import jdk.graal.compiler.debug.DebugContext;

/**
 * This analyzer uses a domain that is a pair of two other domains: CountDomain and BooleanOrDomain.
 * The CountDomain represents the amount of opened FileInputStream objects in the analyzed analysisMethod.
 * The BooleanOrDomain represents whether a FileInputStream object has escaped to the caller (a resource was not closed)
 */
public class PairDomainInterAnalyzer {

    private final InterProceduralAnalyzer<PairDomain<CountDomain, BooleanOrDomain>> analyzer;

    public PairDomainInterAnalyzer() {
        analyzer = new InterProceduralAnalyzer.Builder<>(
                new PairDomain<>(new CountDomain(1024), new BooleanOrDomain(false)),
                new LeaksPairDomainNodeInterpreter(),
                new LeakPairSummaryFactory())
                .build();
    }

    public void run(AnalysisMethod root, DebugContext debug) {
        analyzer.analyzeMethod(root, debug);
    }
}
