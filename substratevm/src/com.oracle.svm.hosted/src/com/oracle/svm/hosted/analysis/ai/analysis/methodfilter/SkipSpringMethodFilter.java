package com.oracle.svm.hosted.analysis.ai.analysis.methodfilter;

import com.oracle.graal.pointsto.meta.AnalysisMethod;

public class SkipSpringMethodFilter implements AnalysisMethodFilter {
    @Override
    public String getDescription() {
        return "Skip Spring Methods";
    }

    @Override
    public boolean shouldSkipMethod(AnalysisMethod method) {
        return method.getQualifiedName().startsWith("org.springframework");
    }
}
