package com.oracle.svm.hosted.analysis.ai.checker.core.facts;

import jdk.graal.compiler.graph.Node;

/**
 * Represents a "Fact" that a checker was able to infer during abstract interpretation.
 */
public interface Fact {

    String kind();

    String describe();

    Node node();
}

