package com.oracle.svm.hosted.analysis.ai.analyzer.payload.filter;

import com.oracle.graal.pointsto.meta.AnalysisMethod;

/**
 * Represents a analysisMethod filter that can be used to filter methods during analysis.
 * Used in some cases to skip methods that are not relevant for the analysis.
 * For example when analyzing a specific package, the filter can be used to skip methods that are not in the package.
 */
public interface AnalysisMethodFilter {

    /**
     * Checks if the given analysisMethod should be analyzed or skipped.
     *
     * @param method the analysisMethod to check
     * @return true if the analysisMethod should be analyzed, false otherwise
     */
    boolean shouldSkipMethod(AnalysisMethod method);
}
