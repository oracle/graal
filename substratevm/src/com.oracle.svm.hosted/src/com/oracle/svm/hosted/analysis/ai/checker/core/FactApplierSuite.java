package com.oracle.svm.hosted.analysis.ai.checker.core;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.hosted.analysis.ai.analysis.AbstractInterpretationServices;
import com.oracle.svm.hosted.analysis.ai.checker.appliers.*;
import com.oracle.svm.hosted.analysis.ai.exception.AbstractInterpretationException;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugOptions;
import jdk.graal.compiler.debug.MethodFilter;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.options.OptionValues;

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

    private static boolean shouldDumpToIGV(AnalysisMethod method, StructuredGraph graph) {
        if (!AbstractInterpretationLogger.getInstance().isGraphIgvDumpEnabled()) {
            return false;
        }
        if (graph == null || graph.getDebug() == null) {
            return false;
        }

        OptionValues currentOptions = HostedOptionValues.singleton();
        String filterString = DebugOptions.MethodFilter.getValue(currentOptions);

        if (filterString == null || filterString.isEmpty()) {
            return true;
        }

        MethodFilter filter = MethodFilter.parse(filterString);
        return filter.matches(method.wrapped);
    }

    /**
     * Executes the appliers in registration order and returns aggregate counters for stats.
     */
    public ApplierResult runAppliers(AnalysisMethod method, StructuredGraph graph, FactAggregator aggregator) {
        if (graph == null) {
            return ApplierResult.empty();
        }

        boolean shouldDump = shouldDumpToIGV(method, graph);
        String methodName = method.format("%H.%n");

        ApplierResult total = ApplierResult.empty();
        for (FactApplier applier : appliers) {
            if (!applier.shouldApply()) {
                continue;
            }

            ApplierResult r = applier.apply(method, graph, aggregator);
            total = total.plus(r);

            if (r.anyOptimizations() && !graph.verify()) {
                AbstractInterpretationException.graphVerifyFailed(applier.getDescription(), method);
            }

            if (shouldDump && r.anyOptimizations()) {
                dumpGraph(graph, applier.getDescription());
            }
        }

        return total;
    }

    private void dumpGraph(StructuredGraph graph, Object... args) {
        DebugContext debug = AbstractInterpretationServices.getInstance().getDebug();

        try (DebugContext.Scope _ = debug.scope("GraalAF", graph)) {
            debug.dump(DebugContext.BASIC_LEVEL, graph, "After Abstract Interpretation applier - %s", args);
        } catch (Throwable e) {
            AbstractInterpretationLogger.getInstance().log(
                "Failed to dump graph: " + e.getMessage(),
                com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity.CHECKER_WARN
            );
        }
    }
}
