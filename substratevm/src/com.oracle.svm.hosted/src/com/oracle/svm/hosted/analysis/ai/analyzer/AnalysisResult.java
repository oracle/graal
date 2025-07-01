package com.oracle.svm.hosted.analysis.ai.analyzer;

/**
 * Represents the result of an analysis.
 */
public enum AnalysisResult {

    OK("Analysis successfully finished"),
    ANALYSIS_FAILED("Some internal error occurred"),
    IN_SKIP_LIST("The method should be skipped according to the provided method filters"),
    MUTUAL_RECURSION_CYCLE("There is a sequence of calls that cannot be resolved due to mutual recursion"),
    UNKNOWN_METHOD("The method of an Invoke was not found in the current DebugContext"),
    RECURSION_LIMIT_OVERFLOW("The recursion limit was reached");

    private final String description;

    AnalysisResult(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return description;
    }
}
