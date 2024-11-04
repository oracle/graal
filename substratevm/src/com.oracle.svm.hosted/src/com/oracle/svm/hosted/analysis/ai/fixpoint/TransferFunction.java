package com.oracle.svm.hosted.analysis.ai.fixpoint;

import jdk.graal.compiler.graph.Node;

/**
 * Interface for transfer functions used in fixpoint analysis.
 */

public interface TransferFunction {
    void analyzeNode(Node node);
}
