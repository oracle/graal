package com.oracle.svm.hosted.analysis.ai.example.pentagon;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.analyzer.IntraProceduralAnalyzer;
import com.oracle.svm.hosted.analysis.ai.checker.example.PentagonDomainChecker;
import com.oracle.svm.hosted.analysis.ai.domain.access.AccessPath;
import com.oracle.svm.hosted.analysis.ai.domain.numerical.PentagonDomain;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.nodes.StructuredGraph;

import java.io.IOException;

public class IntraProceduralPentagonAnalyzer {

    private final IntraProceduralAnalyzer<PentagonDomain<AccessPath>> analyzer;

    /* NOTE: The ctor of this analyzer is different to others, since this is for demonstration purposes,
             We will be analyzing a single method (see the class name) and we want to mark some parts of
             the graph as unreachable, for optimization. Therefore, to make our life easier, we will pass
             the {@link StructuredGraph} to the constructor of the analyzer.
     */
    public IntraProceduralPentagonAnalyzer(StructuredGraph graph) {
        this.analyzer = new IntraProceduralAnalyzer.Builder<>(
                new PentagonDomain<>(),
                new PentagonNodeInterpreter())
                .registerChecker(new PentagonDomainChecker(graph))
                .build();
    }

    public void run(AnalysisMethod method, DebugContext debug) throws IOException {
        analyzer.analyzeMethod(method, debug);
    }
}
