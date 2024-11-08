package com.oracle.svm.hosted.analysis.ai.analyzer;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.fixpoint.FixpointIterator;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.nodes.StructuredGraph;

/**
 * Analyzer for inter procedural analysis
 */

public class InterProceduralAnalyzer {
    private final FixpointIterator<?, ?> fixpointIterator;
    private final DebugContext debug;

    public InterProceduralAnalyzer(FixpointIterator<?, ?> fixpointIterator, DebugContext debug) {
        this.fixpointIterator = fixpointIterator;
        this.debug = debug;
    }

    public void analyze(AnalysisMethod method) {
        StructuredGraph graph = method.decodeAnalyzedGraph(null, null);
        if (graph != null) {
            fixpointIterator.analyze(graph, debug);
        }
    }
}