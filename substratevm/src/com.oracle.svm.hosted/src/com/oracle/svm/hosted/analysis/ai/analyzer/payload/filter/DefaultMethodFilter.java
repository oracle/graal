package com.oracle.svm.hosted.analysis.ai.analyzer.payload.filter;

import com.oracle.graal.pointsto.meta.AnalysisMethod;

/**
 * Default method filter that does not skip any methods.
 */
public class DefaultMethodFilter implements MethodFilter {

    @Override
    public boolean shouldSkip(AnalysisMethod method) {
        return false;
    }
}