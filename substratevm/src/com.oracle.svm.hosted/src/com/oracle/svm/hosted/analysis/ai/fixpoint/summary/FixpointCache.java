package com.oracle.svm.hosted.analysis.ai.fixpoint.summary;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a mapping of `function contexts` to their summaries.
 *
 * @param <Domain> type of the derived {@link AbstractDomain}
 */
public final class FixpointCache<Domain extends AbstractDomain<Domain>> {
    private final Map<String, FunctionSummary<Domain>> cache = new HashMap<>();

    public boolean contains(String context) {
        return cache.containsKey(context);
    }

    public void put(String key, FunctionSummary<Domain> summary) {
        cache.put(key, summary);
    }

    public FunctionSummary<Domain> get(String key) {
        return cache.get(key);
    }
}