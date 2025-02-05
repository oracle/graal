package com.oracle.svm.hosted.analysis.ai.example.leaks.set.inter;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.analyzer.inter.InterProceduralSequentialAnalyzer;
import com.oracle.svm.hosted.analysis.ai.analyzer.payload.filter.SkipJavaLangMethodFilter;
import com.oracle.svm.hosted.analysis.ai.domain.SetDomain;
import com.oracle.svm.hosted.analysis.ai.example.leaks.set.IdentifierSetDomainNodeInterpreter;
import com.oracle.svm.hosted.analysis.ai.interpreter.NodeInterpreter;
import com.oracle.svm.hosted.analysis.ai.summary.SummarySupplier;
import jdk.graal.compiler.debug.DebugContext;

/**
 * This analyzer uses a set of identifiers to track the opened resources ( in this case instances of {@link java.io.FileInputStream}.
 * The main idea is to globally track the set of opened resource.
 * This is done by binding each opened FileInputStream with a unique identifier
 */
public class IdentifierSetDomainInterAnalyzer {

    private final InterProceduralSequentialAnalyzer<SetDomain<String>> analyzer;
    private final NodeInterpreter<SetDomain<String>> nodeInterpreter;

    public IdentifierSetDomainInterAnalyzer(AnalysisMethod root, DebugContext debug) {
        SummarySupplier<SetDomain<String>> summarySupplier = new IdentifierSetDomainSummarySupplier();
        analyzer = new InterProceduralSequentialAnalyzer<>(root, debug, summarySupplier, new SkipJavaLangMethodFilter());
        nodeInterpreter = new IdentifierSetDomainNodeInterpreter();
    }

    public void run() {
        SetDomain<String> initialDomain = new SetDomain<>();
        analyzer.run(initialDomain, nodeInterpreter);
    }
}
