package com.oracle.svm.hosted.analysis.ai.analyzer.payload.filter;

import com.oracle.graal.pointsto.meta.AnalysisMethod;

public class SkipEverythingAnalysisMethodFilter implements AnalysisMethodFilter {

    @Override
    public boolean shouldSkipMethod(AnalysisMethod method) {
        return true;
    }
}
