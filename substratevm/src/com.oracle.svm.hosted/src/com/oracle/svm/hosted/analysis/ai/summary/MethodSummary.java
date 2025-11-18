package com.oracle.svm.hosted.analysis.ai.summary;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;

import java.util.HashMap;
import java.util.Map;

/**
 * Holds all context-sensitive summaries for a single method.
 */
public final class MethodSummary<Domain extends AbstractDomain<Domain>> {

    private final AnalysisMethod method;
    private final Map<ContextKey, ContextSummary<Domain>> contexts = new HashMap<>();

    public MethodSummary(AnalysisMethod method) {
        this.method = method;
    }

    public AnalysisMethod getMethod() {
        return method;
    }

    public ContextSummary<Domain> getOrCreate(ContextKey key, Domain entryState) {
        return contexts.computeIfAbsent(key, k -> new ContextSummary<>(k, entryState.copyOf()));
    }

    public ContextSummary<Domain> get(ContextKey key) {
        return contexts.get(key);
    }

    public Domain getReturnValue(ContextKey key) {
        ContextSummary<Domain> cs = contexts.get(key);
        return cs == null ? null : cs.getReturnValue();
    }

    public Map<ContextKey, ContextSummary<Domain>> getAllContexts() {
        return contexts;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("MethodSummary{" + method + "}\n");
        contexts.forEach((k, v) -> sb.append(k).append(" -> ").append(v).append('\n'));
        return sb.toString();
    }
}
