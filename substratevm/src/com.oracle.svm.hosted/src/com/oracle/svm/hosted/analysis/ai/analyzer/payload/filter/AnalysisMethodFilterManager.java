package com.oracle.svm.hosted.analysis.ai.analyzer.payload.filter;

import com.oracle.graal.pointsto.meta.AnalysisMethod;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages a collection of {@link AnalysisMethodFilter} instances.
 */
public final class AnalysisMethodFilterManager {

    private final List<AnalysisMethodFilter> filters;

    public AnalysisMethodFilterManager() {
        this.filters = new ArrayList<>();
    }

    /**
     * Adds a new filter to the manager.
     *
     * @param filter the filter to add
     */
    public void addMethodFilter(AnalysisMethodFilter filter) {
        filters.add(filter);
    }

    /**
     * Checks if the given analysisMethod should be skipped based on the registered filters.
     *
     * @param method the analysisMethod to check
     * @return true if the analysisMethod should be skipped, false otherwise
     */
    public boolean shouldSkipMethod(AnalysisMethod method) {
        for (AnalysisMethodFilter filter : filters) {
            if (filter.shouldSkipMethod(method)) {
                return true;
            }
        }
        return false;
    }
}
