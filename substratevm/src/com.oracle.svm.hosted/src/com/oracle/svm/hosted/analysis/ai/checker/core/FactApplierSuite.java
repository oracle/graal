package com.oracle.svm.hosted.analysis.ai.checker.core;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.checker.appliers.*;
import com.oracle.svm.hosted.analysis.ai.exception.AbstractInterpretationException;
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
            return ApplierResult.empty();
        }

        if (logger.isGraphIgvDumpEnabled()) {
            try (var session = new AbstractInterpretationLogger.IGVDumpSession(graph.getDebug(), graph, "FactApplierScope")) {
                session.dumpBeforeSuite("running provided (" + appliers.size() + ") appliers");
                return runAppliersInternal(method, graph, aggregator, session);
            } catch (AbstractInterpretationException e) {
                throw new RuntimeException(e);
            } catch (Throwable e) {
                logger.log("[FactApplier] IGV dump failed" + e.getMessage(), LoggerVerbosity.CHECKER_WARN);
            }
        }

        return runAppliersInternal(method, graph, aggregator, null);
    }

    /**
     * Shared core for executing all registered appliers, optionally emitting IGV subphase dumps.
     */
    private ApplierResult runAppliersInternal(AnalysisMethod method,
                                              StructuredGraph graph,
                                              FactAggregator aggregator,
                                              AbstractInterpretationLogger.IGVDumpSession session) {
        ApplierResult total = ApplierResult.empty();
        for (FactApplier applier : appliers) {
            // FIXME: we maybe want to dry-run the appliers so that we see what could have been applied
            if (!applier.shouldApply()) {
                continue;
            }

            ApplierResult r = applier.apply(method, graph, aggregator);
            total = total.plus(r);

            if (r.anyOptimizations() && !graph.verify()) {
                AbstractInterpretationException.graphVerifyFailed(applier.getDescription(), method);
            }

            if (session != null && applier.shouldApply()) {
                session.dumpApplierSubphase(applier.getDescription());
            }
        }
        return total;
    }
}