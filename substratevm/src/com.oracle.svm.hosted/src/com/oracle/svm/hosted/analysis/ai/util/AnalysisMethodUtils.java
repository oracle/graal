package com.oracle.svm.hosted.analysis.ai.util;

import com.oracle.graal.pointsto.meta.AnalysisMethod;

public final class AnalysisMethodUtils {
    public static boolean isJavaLangMethod(AnalysisMethod analysisMethod) {
        return analysisMethod.getQualifiedName().startsWith("java") || analysisMethod.getQualifiedName().startsWith("jdk");
    }
}
