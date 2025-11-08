package com.oracle.svm.hosted.analysis.ai.checker.core.facts;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.IfNode;

public record ConditionTruthFact(IfNode ifNode, Node condition, Truth truth) implements Fact {
    public enum Truth {ALWAYS_TRUE, ALWAYS_FALSE}

    @Override
    public FactKind kind() {
        return FactKind.CONDITION_TRUTH;
    }

    @Override
    public String describe() {
        return "IfNode=" + ifNode + ", cond=" + condition + ", truth=" + truth;
    }

    @Override
    public Node node() {
        return ifNode;
    }
}

