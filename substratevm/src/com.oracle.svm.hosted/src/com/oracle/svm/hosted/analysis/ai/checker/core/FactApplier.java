package com.oracle.svm.hosted.analysis.ai.checker.core;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import jdk.graal.compiler.nodes.StructuredGraph;

/**
 * Applies a specific kind of facts to a StructuredGraph. FactAppliers are run
 * after all checkers have produced facts and those facts have been aggregated.
 * They must preserve graph invariants and avoid unsafe partial deletions.
 */
public interface FactApplier {
    /**
     * @return a human-readable description of this applier.
     */
    String getDescription();

    /**
     * Apply graph rewrites driven by facts in the aggregator. Appliers should be idempotent
     * and resilient to partially optimized graphs.
     */
    void apply(AnalysisMethod method, StructuredGraph graph, FactAggregator aggregator);
}

