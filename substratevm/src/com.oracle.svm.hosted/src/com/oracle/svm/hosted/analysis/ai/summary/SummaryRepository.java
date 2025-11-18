package com.oracle.svm.hosted.analysis.ai.summary;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;

import java.util.HashMap;
import java.util.Map;

/**
 * Repository of method summaries per analysis method.
 */
public final class SummaryRepository<Domain extends AbstractDomain<Domain>> {

    private final Map<AnalysisMethod, MethodSummary<Domain>> methods = new HashMap<>();

    public MethodSummary<Domain> getOrCreate(AnalysisMethod method) {
        return methods.computeIfAbsent(method, MethodSummary::new);
    }

    public MethodSummary<Domain> get(AnalysisMethod method) {
        return methods.get(method);
    }
}

