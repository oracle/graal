package com.oracle.svm.hosted.analysis.ai.checker.core.facts;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.IfNode;

public record ConditionTruthFact(IfNode ifNode, Node condition,
                                 com.oracle.svm.hosted.analysis.ai.checker.core.facts.ConditionTruthFact.Truth truth) implements Fact {
    public enum Truth {ALWAYS_TRUE, ALWAYS_FALSE}

    @Override
    public String kind() {
        return "Condition";
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

