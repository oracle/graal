package com.oracle.svm.hosted.analysis.ai.analysis.mode;

import com.oracle.svm.hosted.analysis.ai.AbstractInterpretationEngine;
import com.oracle.svm.hosted.analysis.ai.analysis.InterProceduralAnalyzer;

/**
 * Represents the possible modes of {@link InterProceduralAnalyzer}.
 * NOTE: if users set the mode to {@link #ANALYZE_FROM_MAIN_ENTRYPOINT} and the application does not have a single main entry point
 * the {@link AbstractInterpretationEngine} will default to using {@link #ANALYZE_FROM_ALL_ROOTS} instead
 */
public enum InterAnalyzerMode {
    ANALYZE_FROM_MAIN_ENTRYPOINT, /* If there is a single main entry point to the application, analyze from this method only */
    ANALYZE_FROM_ALL_ROOTS /* Analyze all call graph roots from the points-to analysis */
}
