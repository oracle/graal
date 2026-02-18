package com.oracle.svm.hosted.analysis.ai.checker.appliers;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.checker.core.ApplierResult;
import com.oracle.svm.hosted.analysis.ai.checker.core.FactAggregator;
import com.oracle.svm.hosted.analysis.ai.checker.facts.FactKind;
import com.oracle.svm.hosted.analysis.ai.analysis.AbstractInterpretationServices;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.DeadCodeEliminationPhase;

import java.util.Set;

/**
 * Performs a final sweep and verification after applying fact-driven rewrites.
 */
public final class CleanupApplier implements FactApplier {

    @Override
    public Set<FactKind> getApplicableFactKinds() {
        return Set.of();
    }

    @Override
    public String getDescription() {
        return "Cleanup";
    }

    @Override
    public boolean shouldApply() {
        return true;
    }

    @Override
    public ApplierResult apply(AnalysisMethod method, StructuredGraph graph, FactAggregator aggregator) {
        CanonicalizerPhase.create().apply(graph, AbstractInterpretationServices.getInstance().getInflation().getProviders(method), false);
        new DeadCodeEliminationPhase().apply(graph, false);
        return ApplierResult.empty();
    }
}
