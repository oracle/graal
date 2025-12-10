package com.oracle.svm.hosted.analysis.ai.checker.facts;

import jdk.graal.compiler.graph.Node;

/**
 * Represents a "Fact" that a checker was able to infer during abstract interpretation.
 */
public interface Fact {

    FactKind kind();

    String describe();

    Node node();
}

