package com.oracle.svm.hosted.analysis.ai.stats;

import com.oracle.graal.pointsto.meta.AnalysisMethod;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Aggregates high-level statistics for an abstract interpretation run and any checker/applier
 * optimizations that followed.
 */
public class AbstractInterpretationStatistics {
    private int methodsOptimized; /* at least one applier changed the graph */

    private int factsFound;       /* total facts produced by all checkers */
    private int factsApplied;

    /** Global counters keyed by optimization kind. */
    private final EnumMap<OptimizationKind, Integer> globalOptCounters = new EnumMap<>(OptimizationKind.class);

    private final Map<AnalysisMethod, MethodStats> methodStats = new LinkedHashMap<>();

    public static final class MethodStats {
        public int factsFound;
        public int factsApplied;
        public final EnumMap<OptimizationKind, Integer> optCounters = new EnumMap<>(OptimizationKind.class);

        public boolean anyOptimization() {
            return optCounters.values().stream().anyMatch(v -> v != null && v > 0);
        }

        public int get(OptimizationKind kind) {
            return optCounters.getOrDefault(kind, 0);
        }

        public void add(OptimizationKind kind, int delta) {
            if (delta <= 0) return;
            optCounters.merge(kind, delta, Integer::sum);
        }

        @Override
        public String toString() {
            StringJoiner sj = new StringJoiner(", ", "{", "}");
            sj.add("factsFound=" + factsFound)
                    .add("factsApplied=" + factsApplied);
            for (OptimizationKind k : OptimizationKind.values()) {
                int v = get(k);
                if (v > 0) {
                    sj.add(k.label() + "=" + v);
                }
            }
            return sj.toString();
        }
    }

    public AbstractInterpretationStatistics() {
        for (OptimizationKind k : OptimizationKind.values()) {
            globalOptCounters.put(k, 0);
        }
    }

    public AbstractInterpretationStatistics incMethodsOptimized() {
        methodsOptimized++;
        return this;
    }

    public AbstractInterpretationStatistics addFactsFound(int count) {
        if (count > 0) factsFound += count;
        return this;
    }

    public AbstractInterpretationStatistics addFactsApplied(int count) {
        if (count > 0) factsApplied += count;
        return this;
    }

    private MethodStats statsFor(AnalysisMethod method) {
        Objects.requireNonNull(method, "method");
        return methodStats.computeIfAbsent(method, m -> new MethodStats());
    }

    public AbstractInterpretationStatistics addMethodFactsFound(AnalysisMethod method, int count) {
        if (count <= 0) return this;
        statsFor(method).factsFound += count;
        addFactsFound(count);
        return this;
    }

    public AbstractInterpretationStatistics addMethodFactsApplied(AnalysisMethod method, int count) {
        if (count <= 0) return this;
        statsFor(method).factsApplied += count;
        addFactsApplied(count);
        return this;
    }

    private AbstractInterpretationStatistics addMethodOpt(AnalysisMethod method, OptimizationKind kind, int count) {
        if (count <= 0) return this;
        MethodStats ms = statsFor(method);
        ms.add(kind, count);
        globalOptCounters.merge(kind, count, Integer::sum);
        return this;
    }

    public AbstractInterpretationStatistics addMethodBoundsEliminated(AnalysisMethod method, int count) {
        return addMethodOpt(method, OptimizationKind.BOUNDS_CHECK_ELIMINATED, count);
    }

    public AbstractInterpretationStatistics addMethodConstantsStamped(AnalysisMethod method, int count) {
        return addMethodOpt(method, OptimizationKind.CONSTANT_STAMP_TIGHTENED, count);
    }

    public AbstractInterpretationStatistics addMethodConstantsPropagated(AnalysisMethod method, int count) {
        return addMethodOpt(method, OptimizationKind.CONSTANT_PROPAGATED, count);
    }

    public AbstractInterpretationStatistics addMethodBranchesFoldedTrue(AnalysisMethod method, int count) {
        return addMethodOpt(method, OptimizationKind.BRANCH_FOLDED_TRUE, count);
    }

    public AbstractInterpretationStatistics addMethodBranchesFoldedFalse(AnalysisMethod method, int count) {
        return addMethodOpt(method, OptimizationKind.BRANCH_FOLDED_FALSE, count);
    }

    public AbstractInterpretationStatistics addMethodInvokesReplaced(AnalysisMethod method, int count) {
        return addMethodOpt(method, OptimizationKind.INVOKE_REPLACED_WITH_CONSTANT, count);
    }

    public AbstractInterpretationStatistics finalizeMethod(AnalysisMethod method) {
        MethodStats ms = statsFor(method);
        if (ms.anyOptimization()) {
            incMethodsOptimized();
        }
        return this;
    }

    public Map<AnalysisMethod, MethodStats> getMethodStats() {
        return Collections.unmodifiableMap(methodStats);
    }

    public AbstractInterpretationStatistics merge(AbstractInterpretationStatistics other) {
        if (other == null) return this;
        methodsOptimized += other.methodsOptimized;
        factsFound += other.factsFound;
        factsApplied += other.factsApplied;
        for (var e : other.globalOptCounters.entrySet()) {
            globalOptCounters.merge(e.getKey(), e.getValue(), Integer::sum);
        }
        for (Map.Entry<AnalysisMethod, MethodStats> e : other.methodStats.entrySet()) {
            MethodStats dst = statsFor(e.getKey());
            MethodStats src = e.getValue();
            dst.factsFound += src.factsFound;
            dst.factsApplied += src.factsApplied;
            for (OptimizationKind k : OptimizationKind.values()) {
                int v = src.get(k);
                if (v > 0) {
                    dst.add(k, v);
                }
            }
        }
        return this;
    }

    @Override
    public String toString() {
        StringJoiner sj = new StringJoiner(", ", "[AI Stats] ", "");
        sj.add("methodsAnalyzed=" + methodStats.size())
                .add("methodsOptimized=" + methodsOptimized)
                .add("factsFound=" + factsFound)
                .add("factsApplied=" + factsApplied);
        for (OptimizationKind k : OptimizationKind.values()) {
            int v = globalOptCounters.getOrDefault(k, 0);
            if (v > 0) {
                sj.add(k.label() + "=" + v);
            }
        }
        return sj.toString();
    }

    public String toMultilineReport() {
        StringBuilder sb = new StringBuilder(256);
        sb.append("Methods analyzed: ").append(methodStats.size()).append('\n');
        sb.append("Methods optimized: ").append(methodsOptimized).append('\n');
        sb.append("Facts found/applied: ").append(factsFound).append(" / ").append(factsApplied).append('\n');

        sb.append("Optimizations (global): ");
        boolean first = true;
        for (OptimizationKind k : OptimizationKind.values()) {
            int v = globalOptCounters.getOrDefault(k, 0);
            if (v <= 0) continue;
            if (!first) sb.append(", ");
            sb.append(k.label()).append("=").append(v);
            first = false;
        }
        if (first) {
            sb.append("none");
        }
        sb.append('\n');

        sb.append("\nMost-optimized methods:\n");
        methodStats.entrySet().stream()
                .filter(entry -> entry.getValue().anyOptimization())
                .sorted(MethodStatsFactsAppliedComparator::compare)
                .limit(10)
                .forEach(entry -> sb.append("  ")
                        .append(entry.getKey().format("%H.%n(%P)"))
                        .append(" ")
                        .append(entry.getValue())
                        .append('\n'));
        return sb.toString();
    }

    private static final class MethodStatsFactsAppliedComparator {
        public static int compare(Map.Entry<AnalysisMethod, MethodStats> o1, Map.Entry<AnalysisMethod, MethodStats> o2) {
            return o1.getValue().factsApplied - o2.getValue().factsApplied;
        }

    }
}
