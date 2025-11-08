package com.oracle.svm.hosted.analysis.ai.checker.core;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.checker.applier.FactApplier;
import com.oracle.svm.hosted.analysis.ai.checker.applier.FactApplierRegistry;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;
import jdk.graal.compiler.nodes.StructuredGraph;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a suite of {@link FactApplier} instances to be run in sequence.
 */
public final class FactApplierSuite {
    private final List<FactApplier> appliers = new ArrayList<>();

    public FactApplierSuite() {
    }

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
     * Default suite containing the standard appliers available in this project.
     */
    public static FactApplierSuite defaultSuite() {
        return new FactApplierSuite()
                .register(new com.oracle.svm.hosted.analysis.ai.checker.applier.ConstantPropagationApplier())
                .register(new com.oracle.svm.hosted.analysis.ai.checker.applier.ConditionTruthApplier())
                .register(new com.oracle.svm.hosted.analysis.ai.checker.applier.BoundsCheckEliminatorApplier())
                .register(new com.oracle.svm.hosted.analysis.ai.checker.applier.CleanupApplier());
    }

    /**
     * Builds a suite by querying the global {@link FactApplierRegistry} for appliers relevant to
     * the facts present in the aggregator. Optionally appends a cleanup applier.
     */
    public static FactApplierSuite fromRegistry(FactAggregator aggregator, boolean appendCleanup) {
        List<FactApplier> relevant = FactApplierRegistry.getRelevantAppliers(aggregator);
        FactApplierSuite suite = new FactApplierSuite(relevant);
        if (appendCleanup) {
            suite.register(new com.oracle.svm.hosted.analysis.ai.checker.applier.CleanupApplier());
        }
        return suite;
    }

    /**
     * Executes the appliers in registration order. Dumps are delegated to the logger via a single
     * IGV dump session so all sub-dumps are grouped under one top-level scope.
     */
    public void runAppliers(AnalysisMethod method, StructuredGraph graph, FactAggregator aggregator) throws Throwable {
        var logger = AbstractInterpretationLogger.getInstance();
        var session = AbstractInterpretationLogger.openIGVDumpSession(method, graph, "abstract interpretation");
        if (session != null) {
            try (AbstractInterpretationLogger.IGVDumpSession s = session) {
                s.dumpBeforeSuite("abstract interpretation checkers", appliers.size());
                for (FactApplier applier : appliers) {
                    try {
                        logger.log("[FactApplier] Applying: " + applier.getDescription(), LoggerVerbosity.CHECKER);
                        applier.apply(method, graph, aggregator);
                    } catch (Throwable t) {
                        logger.log("[FactApplier] Failed in " + applier.getDescription() + ": " + t.getMessage(), LoggerVerbosity.CHECKER_WARN);
                    }
                    s.dumpApplierSubphase(applier.getDescription());
                }
                s.dumpAfterSuite("abstract interpretation checkers");
            }
        } else {
            // No debug context available: just run appliers
            for (FactApplier applier : appliers) {
                try {
                    logger.log("[FactApplier] Applying (no IGV): " + applier.getDescription(), LoggerVerbosity.CHECKER);
                    applier.apply(method, graph, aggregator);
                } catch (Throwable t) {
                    logger.log("[FactApplier] Failed (no IGV) in " + applier.getDescription() + ": " + t.getMessage(), LoggerVerbosity.CHECKER_WARN);
                }
            }
        }
    }
}