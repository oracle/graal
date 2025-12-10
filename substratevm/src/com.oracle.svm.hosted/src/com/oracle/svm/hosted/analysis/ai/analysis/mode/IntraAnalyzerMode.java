package com.oracle.svm.hosted.analysis.ai.analysis.mode;

import com.oracle.svm.hosted.analysis.ai.analysis.IntraProceduralAnalyzer;
import com.oracle.svm.hosted.analysis.ai.AbstractInterpretationEngine;

/**
 * Represents the possible modes of {@link IntraProceduralAnalyzer}.
 * NOTE: if users set the mode to {@link #ANALYZE_MAIN_ENTRYPOINT_ONLY} and the application does not have a single main entry point
 * the {@link AbstractInterpretationEngine} will default to using {@link #ANALYZE_ALL_INVOKED_METHODS} instead
 */
public enum IntraAnalyzerMode {
    ANALYZE_MAIN_ENTRYPOINT_ONLY, /* If there is a single main entry point to the application, it will only analyze it */
    ANALYZE_ALL_INVOKED_METHODS, /* We can analyze every simplyImplementationInvoked from points-to analysis */
}
