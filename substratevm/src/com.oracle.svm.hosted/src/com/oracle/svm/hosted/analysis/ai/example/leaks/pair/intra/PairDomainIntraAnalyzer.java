package com.oracle.svm.hosted.analysis.ai.example.leaks.pair.intra;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.analyzer.IntraProceduralAnalyzer;
import com.oracle.svm.hosted.analysis.ai.domain.BooleanOrDomain;
import com.oracle.svm.hosted.analysis.ai.domain.CountDomain;
import com.oracle.svm.hosted.analysis.ai.domain.PairDomain;
import com.oracle.svm.hosted.analysis.ai.example.leaks.pair.LeaksPairDomainNodeInterpreter;
import jdk.graal.compiler.debug.DebugContext;

public class PairDomainIntraAnalyzer {

    private final IntraProceduralAnalyzer<PairDomain<CountDomain, BooleanOrDomain>> analyzer;

    public PairDomainIntraAnalyzer() {
        analyzer = new IntraProceduralAnalyzer.Builder<>(
                new PairDomain<>(new CountDomain(0), new BooleanOrDomain(false)),
                new LeaksPairDomainNodeInterpreter())
                .build();
    }

    public void run(AnalysisMethod method, DebugContext debug) {
        /* CountDomain is default constructed with 0 and BooleanOrDomain is default constructed with false,
         * but we pass these arguments explicitly to constructors to be more explicit in this example */
        analyzer.analyzeMethod(method, debug);
    }
}
