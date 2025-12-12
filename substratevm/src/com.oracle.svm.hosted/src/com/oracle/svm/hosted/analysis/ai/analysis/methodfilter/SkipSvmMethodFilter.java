package com.oracle.svm.hosted.analysis.ai.analysis.methodfilter;

import com.oracle.graal.pointsto.meta.AnalysisMethod;

public class SkipSvmMethodFilter implements AnalysisMethodFilter {

    @Override
    public String getDescription() {
        return "Skip substratevm methods";
    }

    @Override
    public boolean shouldSkipMethod(AnalysisMethod method) {
        return method.getQualifiedName().startsWith("com.oracle.svm");
    }
}
