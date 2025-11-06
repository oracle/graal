package com.oracle.svm.hosted.analysis.ai.checker.core;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.checker.core.facts.Fact;
import jdk.vm.ci.meta.ResolvedJavaMethod;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A global registry that maps methods to the facts computed by checkers for their graphs.
 * This allows later Graal phases to retrieve and apply the facts to StructuredGraphs.
 */
public final class FactsRegistry {
    private static final ConcurrentHashMap<String, FactAggregator> byKey = new ConcurrentHashMap<>();

    private FactsRegistry() {}

    public static String keyFor(AnalysisMethod m) {
        return String.format("%H.%n(%p)");
    }

    public static String keyFor(ResolvedJavaMethod m) {
        return m.format("%H.%n(%p)");
    }

    public static void putFacts(AnalysisMethod m, List<Fact> facts) {
        byKey.compute(keyFor(m), (k, old) -> {
            FactAggregator agg = old != null ? old : new FactAggregator();
            agg.addAll(facts);
            return agg;
        });
    }

    public static void putAggregator(AnalysisMethod m, FactAggregator aggregator) {
        byKey.put(keyFor(m), aggregator);
    }

    public static FactAggregator getFacts(ResolvedJavaMethod m) {
        return byKey.get(keyFor(m));
    }

    public static void clear() {
        byKey.clear();
    }
}

