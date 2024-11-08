package com.oracle.svm.hosted.analysis.ai.analyzer;

import com.oracle.svm.hosted.analysis.ai.fixpoint.FixpointIterator;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.nodes.StructuredGraph;

/**
 * Analyzer for intra procedural analysis
 */

public class IntraProceduralAnalyzer {
    private final FixpointIterator<?, ?> fixpointIterator;
    private final DebugContext debug;

    public IntraProceduralAnalyzer(FixpointIterator<?, ?> fixpointIterator, DebugContext debug) {
        this.fixpointIterator = fixpointIterator;
        this.debug = debug;
    }

    public void analyze(StructuredGraph graph) {
        if (graph != null) {
            fixpointIterator.analyze(graph, debug);
        }
    }
}