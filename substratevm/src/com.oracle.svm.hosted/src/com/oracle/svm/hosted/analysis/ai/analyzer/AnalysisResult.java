package com.oracle.svm.hosted.analysis.ai.analyzer;

/**
 * Represents the result of an analysis.
 */
public enum AnalysisResult {

    /* Analysis successfully finished */
    OK,
    /* Some internal error occurred ( RuntimeException, IOException ) */
    ANALYSIS_FAILED,
    /* The analysisMethod should be skipped according to the provided analysisMethod filters */
    IN_SKIP_LIST,
    /* Two methods calling each other recursively -> I have no solution for this */
    MUTUAL_RECURSION_CYCLE,
    /* The AnalysisMethod of an Invoke was not found in the current DebugContext */
    UNKNOWN_METHOD,
    /* The recursion limit was reached */
    RECURSION_LIMIT_OVERFLOW,
}
