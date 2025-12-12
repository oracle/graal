package com.oracle.svm.hosted.analysis.ai.analysis.methodfilter;

import com.oracle.graal.pointsto.meta.AnalysisMethod;

/**
 * Skip methods that are part of the java.lang package.
 */
public final class SkipJavaLangAnalysisMethodFilter implements AnalysisMethodFilter {

    @Override
    public String getDescription() {
        return "Skip jdk/java methods";
    }

    @Override
    public boolean shouldSkipMethod(AnalysisMethod method) {
        return method.getQualifiedName().startsWith("java") ||
                method.getQualifiedName().startsWith("jdk") ||
                method.getQualifiedName().startsWith("sun");
    }
}
