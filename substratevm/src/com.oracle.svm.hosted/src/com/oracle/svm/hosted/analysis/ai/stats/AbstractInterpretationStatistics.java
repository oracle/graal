package com.oracle.svm.hosted.analysis.ai.stats;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.analysis.AbstractInterpretationServices;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Aggregates high-level statistics for an abstract interpretation run and any checker/applier
 * optimizations that followed.
 */
public class AbstractInterpretationStatistics {
    // TODO: maybe we should think about how to print the method filter statistics perhaps
    private final EnumMap<OptimizationKind, Integer> globalOptCounters = new EnumMap<>(OptimizationKind.class);
    private final ConcurrentHashMap<AnalysisMethod, MethodStats> methodStats = new ConcurrentHashMap<>();

    public static final class MethodStats {
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
            for (OptimizationKind k : OptimizationKind.values()) {
                int v = get(k);
                if (v > 0) {
                    sj.add(k.label() + "=" + v);
                }
            }
            if (sj.length() == 2) {
                sj.add("no-optimizations");
            }
            return sj.toString();
        }
    }

    public AbstractInterpretationStatistics() {
        for (OptimizationKind k : OptimizationKind.values()) {
            globalOptCounters.put(k, 0);
        }
    }

    private MethodStats statsFor(AnalysisMethod method) {
        Objects.requireNonNull(method, "method");
        return methodStats.computeIfAbsent(method, m -> new MethodStats());
    }

    private void addMethodOpt(AnalysisMethod method, OptimizationKind kind, int count) {
        if (count <= 0) return;
        MethodStats ms = statsFor(method);
        ms.add(kind, count);
        globalOptCounters.merge(kind, count, Integer::sum);
    }

    public void addMethodBoundsEliminated(AnalysisMethod method, int count) {
        addMethodOpt(method, OptimizationKind.BOUNDS_CHECK_ELIMINATED, count);
    }

    public void addMethodConstantsStamped(AnalysisMethod method, int count) {
        addMethodOpt(method, OptimizationKind.CONSTANT_STAMP_TIGHTENED, count);
    }

    public void addMethodConstantsPropagated(AnalysisMethod method, int count) {
        addMethodOpt(method, OptimizationKind.CONSTANT_PROPAGATED, count);
    }

    public void addMethodBranchesFoldedTrue(AnalysisMethod method, int count) {
        addMethodOpt(method, OptimizationKind.BRANCH_FOLDED_TRUE, count);
    }

    public void addMethodBranchesFoldedFalse(AnalysisMethod method, int count) {
        addMethodOpt(method, OptimizationKind.BRANCH_FOLDED_FALSE, count);
    }

    public void addMethodInvokesReplaced(AnalysisMethod method, int count) {
        addMethodOpt(method, OptimizationKind.INVOKE_REPLACED_WITH_CONSTANT, count);
    }

    public void merge(AbstractInterpretationStatistics other) {
        if (other == null) return;
        for (var e : other.globalOptCounters.entrySet()) {
            globalOptCounters.merge(e.getKey(), e.getValue(), Integer::sum);
        }
        for (Map.Entry<AnalysisMethod, MethodStats> e : other.methodStats.entrySet()) {
            MethodStats dst = statsFor(e.getKey());
            MethodStats src = e.getValue();
            for (OptimizationKind k : OptimizationKind.values()) {
                int v = src.get(k);
                if (v > 0) {
                    dst.add(k, v);
                }
            }
        }
    }

    private int getNumOfOptimizedMethods() {
        return Math.toIntExact(methodStats.entrySet().stream().filter(entry -> entry.getValue().anyOptimization()).count());
    }

    @Override
    public String toString() {
        StringJoiner sj = new StringJoiner(", ", "[AI Stats] ", "");
        sj.add("methodsAnalyzed=" + methodStats.size())
                .add("methodsOptimized=" + getNumOfOptimizedMethods());
        for (OptimizationKind k : OptimizationKind.values()) {
            int v = globalOptCounters.getOrDefault(k, 0);
            if (v > 0) {
                sj.add(k.label() + "=" + v);
            }
        }
        return sj.toString();
    }

    public String toMultilineReport() {
        StringBuilder sb = new StringBuilder(512);
        sb.append("Methods analyzed: ").append(AbstractInterpretationServices.getInstance().getTouchedMethods().size()).append('\n');
        sb.append("Methods optimized: ").append(getNumOfOptimizedMethods()).append('\n');

        sb.append("Optimizations performed: ");
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
                .sorted((o1, o2) -> Integer.compare(totalOptimizations(o2.getValue()), totalOptimizations(o1.getValue())))
                .limit(10)
                .forEach(entry -> sb.append("  ")
                        .append(entry.getKey().wrapped.format("%H.%n(%p)"))
                        .append(" ")
                        .append(entry.getValue())
                        .append('\n'));
        return sb.toString();
    }

    private static int totalOptimizations(MethodStats ms) {
        int sum = 0;
        for (OptimizationKind k : OptimizationKind.values()) {
            sum += ms.get(k);
        }
        return sum;
    }
}
