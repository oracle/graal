package com.oracle.svm.hosted.analysis.ai.analyzer.example.leaks.count.inter;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.analyzer.context.filter.SkipJavaLangMethodFilter;
import com.oracle.svm.hosted.analysis.ai.analyzer.example.leaks.count.LeaksCountingDomainNodeInterpreter;
import com.oracle.svm.hosted.analysis.ai.analyzer.inter.InterProceduralSequentialAnalyzer;
import com.oracle.svm.hosted.analysis.ai.domain.CountingDomain;
import com.oracle.svm.hosted.analysis.ai.interpreter.NodeInterpreter;
import com.oracle.svm.hosted.analysis.ai.summary.SummarySupplier;
import jdk.graal.compiler.debug.DebugContext;

/**
 * Example of a simple inter procedural leaks analysis domain
 * that counts the number of FileInputStream objects opened in a method.
 */
public class CountingDomainInterAnalyzer {
    private final InterProceduralSequentialAnalyzer<CountingDomain> analyzer;
    private final NodeInterpreter<CountingDomain> nodeInterpreter;

    public CountingDomainInterAnalyzer(AnalysisMethod root, DebugContext debug) {
        SummarySupplier<CountingDomain> supplier = new LeakCountingSummarySupplier();
        analyzer = new InterProceduralSequentialAnalyzer<>(root, debug, supplier, new SkipJavaLangMethodFilter());
        nodeInterpreter = new LeaksCountingDomainNodeInterpreter();
    }

    public void run() {
        CountingDomain initialDomain = new CountingDomain();
        analyzer.run(initialDomain, nodeInterpreter);
    }
}
