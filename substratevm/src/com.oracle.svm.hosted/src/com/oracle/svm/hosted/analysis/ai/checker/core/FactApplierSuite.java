package com.oracle.svm.hosted.analysis.ai.checker.core;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.checker.applier.CleanupApplier;
import com.oracle.svm.hosted.analysis.ai.checker.applier.FactApplier;
import com.oracle.svm.hosted.analysis.ai.checker.applier.FactApplierRegistry;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.nodes.StructuredGraph;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a suite of {@link FactApplier} instances to be run in sequence.
 */
public final class FactApplierSuite {

    private final List<FactApplier> appliers = new ArrayList<>();

    public FactApplierSuite(List<FactApplier> appliers) {
        if (appliers != null) {
            this.appliers.addAll(appliers);
        }
    }

    public FactApplierSuite register(FactApplier applier) {
        if (applier != null) {
            appliers.add(applier);
        }
        return this;
    }

    /**
     * Builds a suite by querying the global {@link FactApplierRegistry} for appliers relevant to
     * the facts present in the aggregator. Optionally appends a cleanup applier.
     */
    public static FactApplierSuite fromRegistry(FactAggregator aggregator, boolean appendCleanup) {
        List<FactApplier> relevant = FactApplierRegistry.getRelevantAppliers(aggregator);
        FactApplierSuite suite = new FactApplierSuite(relevant);
        if (appendCleanup) {
            suite.register(new CleanupApplier());
        }
        return suite;
    }

    /**
     * Executes the appliers in registration order directly on the provided graph's debug context.
     * Avoids creating a separate IGV session that could cause graph copies; we want in-place
     * mutations to persist for later phases.
     */
    public void runAppliers(AnalysisMethod method, StructuredGraph graph, FactAggregator aggregator) {
        var logger = AbstractInterpretationLogger.getInstance();
        if (graph == null) {
            logger.log("[FactApplierSuite] Null graph; skipping appliers", LoggerVerbosity.CHECKER_WARN);
            return;
        }
        DebugContext debug = graph.getDebug();
        int idx = 0;
        for (FactApplier applier : appliers) {
            try (DebugContext.Scope _ = debug.scope("FactApplier:" + applier.getDescription(), graph)) {
                logger.log("[FactApplier] Applying: " + applier.getDescription(), LoggerVerbosity.CHECKER);
                applier.apply(method, graph, aggregator);
                if (debug.isDumpEnabled(DebugContext.INFO_LEVEL)) {
                    debug.dump(DebugContext.INFO_LEVEL, graph, "After applier %d: %s".formatted(++idx, applier.getDescription()));
                }

                if (!graph.verify()) {
                    logger.log("[FactApplier] Graph verification failed after " + applier.getDescription(),
                            LoggerVerbosity.CHECKER_WARN);
                }
                logger.exportGraphToJson(graph, method, "after-fact-applier-%d-%s".formatted(idx, applier.getDescription()));
            } catch (Throwable t) {
                logger.log("[FactApplier] Failed in " + applier.getDescription() + ": " + t.getMessage(), LoggerVerbosity.CHECKER_WARN);
            }
        }
        if (debug.isDumpEnabled(DebugContext.BASIC_LEVEL)) {
            debug.dump(DebugContext.BASIC_LEVEL, graph, "After all fact appliers");
        }
    }
}