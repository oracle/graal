package com.oracle.svm.hosted.analysis.ai.checker.core;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.checker.applier.*;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;
import com.oracle.svm.hosted.analysis.ai.util.AbstractInterpretationServices;
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
     * Executes the appliers in registration order and returns aggregate counters for stats.
     */
    public ApplierResult runAppliers(AnalysisMethod method, StructuredGraph graph, FactAggregator aggregator) {
        var logger = AbstractInterpretationLogger.getInstance();
        if (graph == null) {
            logger.log("[FactApplierSuite] Null graph; skipping appliers", LoggerVerbosity.CHECKER_WARN);
            return ApplierResult.empty();
        }

        ApplierResult total = ApplierResult.empty();

        if (logger.isGraphDumpEnabled() && graph.getDebug().isDumpEnabledForMethod()) {
            try (var session = new AbstractInterpretationLogger.IGVDumpSession(graph.getDebug(), graph, "FactApplierScope")) {
                session.dumpBeforeSuite("running provided (" + appliers.size() + ") appliers");

                for (FactApplier applier : appliers) {

                    logger.log("[FactApplier] Applying: " + applier.getDescription(), LoggerVerbosity.CHECKER);
                    ApplierResult r = applier.apply(method, graph, aggregator);
                    total = total.plus(r);

                    if (!graph.verify()) {
                        logger.log("[FactApplier] Graph verification failed after " + applier.getDescription(), LoggerVerbosity.CHECKER_WARN);
                    }
                    session.dumpApplierSubphase(applier.getDescription());
                }

                return total;
            } catch (Throwable e) {
                logger.log("[FactApplier] IGV dump failed" + e.getMessage(), LoggerVerbosity.CHECKER_WARN);
                throw new RuntimeException(e);
            }
        }

        /* Otherwise perform fact applying without dumping */
        try {
            for (FactApplier applier : appliers) {
                logger.log("[FactApplier] Applying: " + applier.getDescription(), LoggerVerbosity.CHECKER);
                ApplierResult r = applier.apply(method, graph, aggregator);
                total = total.plus(r);
                if (!graph.verify()) {
                    logger.log("[FactApplier] Graph verification failed after " + applier.getDescription(), LoggerVerbosity.CHECKER_WARN);
                }
            }
        } catch (Throwable e) {
            logger.log("[FactApplier] IGV dump failed" + e.getMessage(), LoggerVerbosity.CHECKER_WARN);
            throw new RuntimeException(e);
        }

        return total;
    }
}