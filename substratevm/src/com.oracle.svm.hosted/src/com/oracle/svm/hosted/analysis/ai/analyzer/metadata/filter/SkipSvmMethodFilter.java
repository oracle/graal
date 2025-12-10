package com.oracle.svm.hosted.analysis.ai.analyzer.metadata.filter;

import com.oracle.graal.pointsto.meta.AnalysisMethod;

public class SkipSvmMethodFilter implements AnalysisMethodFilter {

    @Override
    public boolean shouldSkipMethod(AnalysisMethod method) {
        return method.getQualifiedName().startsWith("com.oracle.svm");
    }
}
