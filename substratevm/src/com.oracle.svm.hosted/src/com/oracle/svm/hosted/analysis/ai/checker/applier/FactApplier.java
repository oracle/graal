package com.oracle.svm.hosted.analysis.ai.checker.applier;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.checker.core.ApplierResult;
import com.oracle.svm.hosted.analysis.ai.checker.core.FactAggregator;
import com.oracle.svm.hosted.analysis.ai.checker.core.facts.FactKind;
import jdk.graal.compiler.nodes.StructuredGraph;

import java.util.Set;

/**
 * Applies a specific kind of fact to a StructuredGraph. FactAppliers are run
 * after all checkers have produced facts and those facts have been aggregated.
 * They must preserve graph invariants and avoid unsafe partial deletions.
 */
public interface FactApplier {

    /**
     * @return the kinds of facts this applier can handle.
     */
    Set<FactKind> getApplicableFactKinds();

    /**
     * @return a human-readable description of this applier.
     */
    String getDescription();

    /**
     * Apply graph rewrites driven by facts in the aggregator. Appliers should be idempotent
     * and resilient to partially optimized graphs.
     *
     * @return per-applier counters for statistics aggregation.
     */
    ApplierResult apply(AnalysisMethod method, StructuredGraph graph, FactAggregator aggregator);
}
