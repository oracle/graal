package com.oracle.svm.hosted.analysis.ai.summary;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;

/**
 * Per-context summary for an {@link AnalysisMethod}.
 * <p>
 * A context is identified by a {@link ContextKey} and typically represents one
 * abstract invocation context (e.g., a call string, receiver type, etc.).
 */
public final class ContextSummary<Domain extends AbstractDomain<Domain>> {

    private final ContextKey contextKey;
    private Summary<Domain> summary;

    public ContextSummary(ContextKey contextKey, Summary<Domain> summary) {
        this.contextKey = contextKey;
        this.summary = summary;
    }

    public ContextKey contextKey() {
        return contextKey;
    }

    public Summary<Domain> summary() {
        return summary;
    }

    public void setSummary(Summary<Domain> otherSummary) {
        this.summary = otherSummary;
    }
}
