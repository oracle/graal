package com.oracle.svm.hosted.analysis.ai.summary;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;

import java.util.HashMap;
import java.util.Map;

/**
 * Holds all context-sensitive summaries for a single method.
 */
public final class MethodSummary<Domain extends AbstractDomain<Domain>> {

    private final AnalysisMethod method;
    private final Map<ContextKey, ContextSummary<Domain>> contexts = new HashMap<>();
    private AbstractState<Domain> stateAcrossAllContexts = null;

    public MethodSummary(AnalysisMethod method) {
        this.method = method;
    }

    public AnalysisMethod getMethod() {
        return method;
    }

    public ContextSummary<Domain> getOrCreate(ContextKey key, Summary<Domain> preConditionSummary) {
        return contexts.computeIfAbsent(key, k -> new ContextSummary<>(k, preConditionSummary));
    }

    public ContextSummary<Domain> get(ContextKey key) {
        return contexts.get(key);
    }

    public Map<ContextKey, ContextSummary<Domain>> getAllContexts() {
        return contexts;
    }

    public AbstractState<Domain> getStateAcrossAllContexts() {
        return stateAcrossAllContexts;
    }

    public void joinWithContextState(AbstractState<Domain> other) {
        if (stateAcrossAllContexts == null) {
            stateAcrossAllContexts = other;
            return;
        }
        stateAcrossAllContexts.joinWith(other);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("MethodSummary{" + method + "}\n");
        contexts.forEach((k, v) -> sb.append(k).append(" -> ").append(v).append('\n'));
        return sb.toString();
    }
}
