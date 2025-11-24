package com.oracle.svm.hosted.analysis.ai.summary;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;

/**
 * Per-context summary for an {@link AnalysisMethod}.
 * <p>
 * A context is identified by a {@link ContextKey} and typically represents one
 * abstract invocation context (e.g., a call string, receiver type, etc.).
 * @param contextKey  The identity of the context this summary belongs to.
 */
public record ContextSummary<Domain extends AbstractDomain<Domain>>(ContextKey contextKey, Summary<Domain> summary) {

}
