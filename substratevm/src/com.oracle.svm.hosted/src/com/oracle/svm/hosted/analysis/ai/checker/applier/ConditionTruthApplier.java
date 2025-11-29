package com.oracle.svm.hosted.analysis.ai.checker.applier;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.checker.core.ApplierResult;
import com.oracle.svm.hosted.analysis.ai.checker.core.FactAggregator;
import com.oracle.svm.hosted.analysis.ai.checker.core.facts.ConditionTruthFact;
import com.oracle.svm.hosted.analysis.ai.checker.core.facts.Fact;
import com.oracle.svm.hosted.analysis.ai.checker.core.facts.FactKind;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.StructuredGraph;

import java.util.List;
import java.util.Set;

/**
 * Applies ConditionTruthFact by folding always-true/false branches.
 */
public final class ConditionTruthApplier implements FactApplier {

    @Override
    public Set<FactKind> getApplicableFactKinds() {
        return Set.of(FactKind.CONDITION_TRUTH);
    }

    @Override
    public String getDescription() {
        return "ConditionTruthFolding";
    }

    @Override
    public ApplierResult apply(AnalysisMethod method, StructuredGraph graph, FactAggregator aggregator) {
        List<Fact> facts = aggregator.factsOfKind(FactKind.CONDITION_TRUTH);
        if (facts.isEmpty()) {
            return ApplierResult.empty();
        }

        int trueFolded = 0;
        int falseFolded = 0;

        for (Fact f : facts) {
            if (!(f instanceof ConditionTruthFact tf)) {
                continue;
            }
            IfNode ifn = tf.ifNode();
            if (ifn == null || !ifn.isAlive()) {
                continue;
            }
            switch (tf.truth()) {
                case ALWAYS_TRUE -> {
                    GraphRewrite.foldIfTrue(graph, ifn);
                    trueFolded++;
                }
                case ALWAYS_FALSE -> {
                    GraphRewrite.foldIfFalse(graph, ifn);
                    falseFolded++;
                }
                default -> {
                    // uncertain -> no action
                }
            }
        }

        return ApplierResult.builder()
                .appliedFacts(trueFolded + falseFolded)
                .branchesFoldedTrue(trueFolded)
                .branchesFoldedFalse(falseFolded)
                .build();
    }
}
