package com.oracle.svm.hosted.analysis.ai.analyzer.context.filter;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.util.AnalysisMethodUtils;

/**
 * Skip methods that are part of the java.lang package.
 */
public class SkipJavaLangMethodFilter implements MethodFilter {

    @Override
    public boolean shouldSkip(AnalysisMethod method) {
        return AnalysisMethodUtils.isJavaLangMethod(method);
    }
}
