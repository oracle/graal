package com.oracle.svm.hosted.analysis.ai.checker.core.facts;

import jdk.graal.compiler.graph.Node;

/**
 * A fact that records a constant value for a particular node.
 */
public record ConstantFact(Node node, long value) implements Fact {

    @Override
    public FactKind kind() {
        return FactKind.CONSTANT;
    }

    @Override
    public String describe() {
        return node.toString() + " has constant value: " + Long.toString(value);
    }
}

