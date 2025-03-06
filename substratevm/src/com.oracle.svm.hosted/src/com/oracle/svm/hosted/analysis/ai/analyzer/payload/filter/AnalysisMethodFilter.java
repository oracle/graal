package com.oracle.svm.hosted.analysis.ai.analyzer.payload.filter;

import com.oracle.graal.pointsto.meta.AnalysisMethod;

/**
 * Represents a analysisMethod filter that can be used to filter methods during analysis.
 * Used in some cases to skip methods that are not relevant for the analysis.
 * For example when analyzing a specific package, the filter can be used to skip methods that are not in the package.
 */
public interface AnalysisMethodFilter {

    boolean shouldSkipMethod(AnalysisMethod method);
}
