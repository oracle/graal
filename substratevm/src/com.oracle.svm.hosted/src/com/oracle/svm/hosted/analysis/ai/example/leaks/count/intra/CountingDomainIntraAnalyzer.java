package com.oracle.svm.hosted.analysis.ai.example.leaks.count.intra;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.analyzer.intra.IntraProceduralSequentialAnalyzer;
import com.oracle.svm.hosted.analysis.ai.domain.CountingDomain;
import com.oracle.svm.hosted.analysis.ai.example.leaks.count.LeaksCountingDomainNodeInterpreter;
import com.oracle.svm.hosted.analysis.ai.interpreter.NodeInterpreter;
import jdk.graal.compiler.debug.DebugContext;

/**
 * Example of a simple intra procedural leaks analysis domain that
 * counts the number of FileInputStream objects opened in a method.
 */
public class CountingDomainIntraAnalyzer {
    private final IntraProceduralSequentialAnalyzer<CountingDomain> analyzer;
    private final NodeInterpreter<CountingDomain> nodeInterpreter;

    public CountingDomainIntraAnalyzer(AnalysisMethod root, DebugContext debug) {
        analyzer = new IntraProceduralSequentialAnalyzer<>(root, debug);
        nodeInterpreter = new LeaksCountingDomainNodeInterpreter();
    }

    public void run() {
        CountingDomain initialDomain = new CountingDomain();
        analyzer.run(initialDomain, nodeInterpreter);
    }
}
