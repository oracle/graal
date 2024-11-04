package com.oracle.svm.hosted.analysis.ai.analyzer;

import com.oracle.svm.hosted.analysis.ai.fixpoint.FixpointIterator;
import jdk.graal.compiler.nodes.StructuredGraph;

/**
 * Analyzer for intra procedural analysis
 */

public class IntraproceduralAnalyzer {

    private final FixpointIterator<?, ?> fixpointIterator;

    public IntraproceduralAnalyzer(FixpointIterator<?, ?> fixpointIterator) {
        this.fixpointIterator = fixpointIterator;
    }

    public void analyze(StructuredGraph graph) {
        if (graph != null) {
            fixpointIterator.analyze(graph);
        }
    }
}