package com.oracle.svm.hosted.analysis.ai.checker.facts;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import jdk.graal.compiler.graph.Node;

/**
 * Fact describing that a boolean condition node is always true/false.
 *
 * @param conditionKind e.g. "lt", "eq"
 * @param result        "always-true" or "always-false"
 */
public record ConditionFact(Node node, String conditionKind, String result) implements Fact {

    @Override
    public String kind() {
        return "condition";
    }

    @Override
    public String describe() {
        return conditionKind + " -> " + result;
    }
}

