package com.oracle.svm.hosted.analysis.ai.analyzer;

/**
 * Represents the result of an analysis.
 */
public enum AnalysisResult {

    OK, /* Analysis successfully finished */
    ANALYSIS_FAILED, /* Some internal error occurred ( RuntimeException, IOException ) */
    IN_SKIP_LIST, /* The method should be skipped according to the provided method filters */
    MUTUAL_RECURSION_CYCLE, /* Two methods calling each other recursively -> I have no solution for this */
    UNKNOWN_METHOD, /* The AnalysisMethod of an Invoke was not found in the current DebugContext */
    RECURSION_LIMIT_OVERFLOW, /* The recursion limit was reached */
}