package com.oracle.svm.hosted.analysis.ai.analyzer;

import com.oracle.svm.hosted.analysis.ai.fixpoint.FixpointIterator;

/**
 * Factory for creating analyzers
 */

public class AnalyzerFactory {

    public static InterproceduralAnalyzer createInterproceduralAnalyzer(FixpointIterator<?, ?> fixpointIterator) {
        return new InterproceduralAnalyzer(fixpointIterator);
    }

    public static IntraproceduralAnalyzer createIntraproceduralAnalyzer(FixpointIterator<?, ?> fixpointIterator) {
        return new IntraproceduralAnalyzer(fixpointIterator);
    }
}