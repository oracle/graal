package com.oracle.svm.hosted.analysis.ai.checker.applier;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.checker.core.FactAggregator;
import com.oracle.svm.hosted.analysis.ai.checker.core.facts.FactKind;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;
import jdk.graal.compiler.nodes.StructuredGraph;

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
        return "Cleanup/DCE";
    }

    @Override
    public void apply(AnalysisMethod method, StructuredGraph graph, FactAggregator aggregator) {
        GraphRewrite.sweepUnreachableFixed(graph);
        boolean ok = graph.verify();
        AbstractInterpretationLogger.getInstance()
                .log("[Cleanup] verify=" + ok + ", nodes=" + graph.getNodeCount(), LoggerVerbosity.CHECKER);
    }
}

