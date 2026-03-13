package com.oracle.svm.hosted.analysis.ai.analysis.methodfilter;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import jdk.graal.compiler.debug.MethodFilter;

public class SkipMicronautMethodFilter implements AnalysisMethodFilter{

    @Override
    public String getDescription() {
        return "Skip Micronaut Method Filter";
    }

    @Override
    public boolean shouldSkipMethod(AnalysisMethod method) {
        return method.getQualifiedName().startsWith("io.micronaut");
    }
}
