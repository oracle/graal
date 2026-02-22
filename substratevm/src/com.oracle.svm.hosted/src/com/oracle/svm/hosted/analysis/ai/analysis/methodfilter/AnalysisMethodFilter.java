package com.oracle.svm.hosted.analysis.ai.analysis.methodfilter;

import com.oracle.graal.pointsto.meta.AnalysisMethod;

/**
 * Represents an {@link AnalysisMethod} filter that can be used to filter methods during analysis.
 * Used in some cases to skip methods that are not relevant for the analysis.
 * For example, when analyzing a specific package, the filter can be used to skip methods that are not in the package.
 */
public interface AnalysisMethodFilter {

    /**
     * Description of the method filter
     * @return String description of the method filter
     */
    String getDescription();

    /**
     * Checks if the given analysisMethod be skipped during the analysis.
     *
     * @param method the method to check
     * @return true if the method should be analyzed, false otherwise
     */
    boolean shouldSkipMethod(AnalysisMethod method);
}
