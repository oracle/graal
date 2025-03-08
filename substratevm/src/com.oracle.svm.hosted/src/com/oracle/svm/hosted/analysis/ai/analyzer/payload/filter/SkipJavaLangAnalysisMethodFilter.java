package com.oracle.svm.hosted.analysis.ai.analyzer.payload.filter;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.util.AnalysisMethodUtil;

/**
 * Skip methods that are part of the java.lang package.
 */
public final class SkipJavaLangAnalysisMethodFilter implements AnalysisMethodFilter {

    @Override
    public boolean shouldSkipMethod(AnalysisMethod method) {
        return AnalysisMethodUtil.isJavaLangMethod(method);
    }
}
