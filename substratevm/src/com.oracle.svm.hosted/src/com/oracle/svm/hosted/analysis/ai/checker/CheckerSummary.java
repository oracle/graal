package com.oracle.svm.hosted.analysis.ai.checker;

import java.util.List;

/**
 * Summarizes the results of a checker run.
 */
public class CheckerSummary {

    private final Checker checker;
    private List<CheckerResult> warnings;
    private List<CheckerResult> errors;

    public CheckerSummary(Checker checker) {
        this.checker = checker;
    }

    public void addWarning(CheckerResult warning) {
        warnings.add(warning);
    }

    public void addError(CheckerResult error) {
        errors.add(error);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Summary of Checker: ").append(checker.getClass().getSimpleName()).append("\n");
        for (var warning : warnings) {
            sb.append("WARNING: ").append(warning).append("\n");
        }
        for (var error : errors) {
            sb.append("ERROR: ").append(error).append("\n");
        }
        if (warnings.isEmpty() && errors.isEmpty()) {
            sb.append("Checker passed successfully");
        } else {
            sb.append("Checker failed");
        }

        return sb.toString();
    }
}
