package com.oracle.svm.hosted.analysis.ai.analyzer;

import com.oracle.svm.hosted.analysis.ai.fixpoint.FixpointIterator;
import jdk.graal.compiler.debug.DebugContext;

/**
 * Factory for creating analyzers
 */

public class AnalyzerFactory {

    public static InterProceduralAnalyzer createInterproceduralAnalyzer(FixpointIterator<?, ?> fixpointIterator, DebugContext debug) {
        return new InterProceduralAnalyzer(fixpointIterator, debug);
    }

    public static IntraProceduralAnalyzer createIntraproceduralAnalyzer(FixpointIterator<?, ?> fixpointIterator, DebugContext debug) {
        return new IntraProceduralAnalyzer(fixpointIterator, debug);
    }
}