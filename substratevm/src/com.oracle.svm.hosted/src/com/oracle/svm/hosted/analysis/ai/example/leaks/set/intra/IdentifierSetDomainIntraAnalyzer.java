package com.oracle.svm.hosted.analysis.ai.example.leaks.set.intra;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.analyzer.intra.IntraProceduralSequentialAnalyzer;
import com.oracle.svm.hosted.analysis.ai.domain.SetDomain;
import com.oracle.svm.hosted.analysis.ai.example.leaks.set.IdentifierSetDomainNodeInterpreter;
import com.oracle.svm.hosted.analysis.ai.interpreter.NodeInterpreter;
import jdk.graal.compiler.debug.DebugContext;

public class IdentifierSetDomainIntraAnalyzer {
    private final IntraProceduralSequentialAnalyzer<SetDomain<String>> analyzer;
    private final NodeInterpreter<SetDomain<String>> nodeInterpreter;

    public IdentifierSetDomainIntraAnalyzer(AnalysisMethod root, DebugContext debug) {
        analyzer = new IntraProceduralSequentialAnalyzer<>(root, debug);
        nodeInterpreter = new IdentifierSetDomainNodeInterpreter();
    }

    public void run() {
        SetDomain<String> initialDomain = new SetDomain<>();
        analyzer.run(initialDomain, nodeInterpreter);
    }
}