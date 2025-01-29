package com.oracle.svm.hosted.analysis.ai.example.leaks.pair;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.analyzer.context.filter.SkipJavaLangMethodFilter;
import com.oracle.svm.hosted.analysis.ai.analyzer.inter.InterProceduralSequentialAnalyzer;
import com.oracle.svm.hosted.analysis.ai.domain.BooleanOrDomain;
import com.oracle.svm.hosted.analysis.ai.domain.CountingDomain;
import com.oracle.svm.hosted.analysis.ai.domain.PairDomain;
import com.oracle.svm.hosted.analysis.ai.interpreter.NodeInterpreter;
import com.oracle.svm.hosted.analysis.ai.summary.SummarySupplier;
import jdk.graal.compiler.debug.DebugContext;

/**
 * This analyzer uses a domain that is a pair of two other domains: CountingDomain and BooleanOrDomain.
 * The CountingDomain represents the amount of opened FileInputStream objects in the analyzed method.
 * The BooleanOrDomain represents whether a FileInputStream object has escaped to the caller (a resource was not closed)
 */
public class PairDomainInterAnalyzer {
    private final InterProceduralSequentialAnalyzer<PairDomain<CountingDomain, BooleanOrDomain>> analyzer;
    private final NodeInterpreter<PairDomain<CountingDomain, BooleanOrDomain>> nodeInterpreter;

    public PairDomainInterAnalyzer(AnalysisMethod root, DebugContext debug) {
        SummarySupplier<PairDomain<CountingDomain, BooleanOrDomain>> summarySupplier = new LeakPairSummarySupplier();
        analyzer = new InterProceduralSequentialAnalyzer<>(root, debug, summarySupplier, new SkipJavaLangMethodFilter());
        nodeInterpreter = new LeaksPairDomainNodeInterpreter();
    }

    public void run() {
        /* CountingDomain is default constructed with 0 and BooleanOrDomain is default constructed with false,
         * but we pass these arguments explicitly to constructors to be more explicit in this example */
        PairDomain<CountingDomain, BooleanOrDomain> initialDomain = new PairDomain<>(new CountingDomain(0), new BooleanOrDomain(false));
        analyzer.run(initialDomain, nodeInterpreter);
    }
}
