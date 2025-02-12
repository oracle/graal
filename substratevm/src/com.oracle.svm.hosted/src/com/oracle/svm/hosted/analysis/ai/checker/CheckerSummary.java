package com.oracle.svm.hosted.analysis.ai.checker;

import java.util.List;

/**
 * Summarizes the results of a single checker.
 * Contains a list of warnings and errors.
 */
public final class CheckerSummary {

    private List<CheckerResult> warnings;
    private List<CheckerResult> errors;

    public void addResult(CheckerResult result) {
        switch (result.result()) {
            case WARNING -> addWarning(result);
            case ERROR -> addError(result);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
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

    private void addWarning(CheckerResult warning) {
        warnings.add(warning);
    }

    private void addError(CheckerResult error) {
        errors.add(error);
    }
}
