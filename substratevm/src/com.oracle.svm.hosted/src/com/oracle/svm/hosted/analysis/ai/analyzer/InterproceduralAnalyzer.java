package com.oracle.svm.hosted.analysis.ai.analyzer;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.fixpoint.FixpointIterator;
import jdk.graal.compiler.nodes.StructuredGraph;

/**
 * Analyzer for inter procedural analysis
 */

public class InterproceduralAnalyzer {

    private final FixpointIterator<?, ?> fixpointIterator;

    public InterproceduralAnalyzer(FixpointIterator<?, ?> fixpointIterator) {
        this.fixpointIterator = fixpointIterator;
    }

    public void analyze(AnalysisMethod method) {
        StructuredGraph graph = method.decodeAnalyzedGraph(null, null);
        if (graph != null) {
            fixpointIterator.analyze(graph);
        }
    }
}