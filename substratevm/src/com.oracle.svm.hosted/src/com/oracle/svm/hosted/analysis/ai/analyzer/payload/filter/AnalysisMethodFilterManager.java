package com.oracle.svm.hosted.analysis.ai.analyzer.payload.filter;

import com.oracle.graal.pointsto.meta.AnalysisMethod;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages a collection of {@link AnalysisMethodFilter} instances.
 */
public class AnalysisMethodFilterManager {

    private final List<AnalysisMethodFilter> filters;

    public AnalysisMethodFilterManager() {
        this.filters = new ArrayList<>();
    }

    public AnalysisMethodFilterManager(List<AnalysisMethodFilter> filters) {
        this.filters = filters;
    }

    /**
     * Adds a new filter to the manager.
     *
     * @param filter the filter to add
     */
    public AnalysisMethodFilterManager addFilter(AnalysisMethodFilter filter) {
        filters.add(filter);
        return this;
    }

    /**
     * Checks if the given method should be skipped based on the registered filters.
     *
     * @param method the method to check
     * @return true if the method should be skipped, false otherwise
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