package com.oracle.svm.hosted.analysis.ai.checker;

import java.util.LinkedList;
import java.util.List;

/**
 * Summarizes the results of a single checker.
 * Contains a list of warnings and errors.
 */
public final class CheckerSummary {

    private final List<CheckerResult> warnings;
    private final List<CheckerResult> errors;

    public CheckerSummary(List<CheckerResult> checkerResults) {
        this.warnings = new LinkedList<>();
        this.errors = new LinkedList<>();
        for (var result : checkerResults) {
            addResult(result);
        }
    }

    public CheckerSummary() {
        this.warnings = new LinkedList<>();
        this.errors = new LinkedList<>();
    }

    public List<CheckerResult> getWarnings() {
        return warnings;
    }

    public List<CheckerResult> getErrors() {
        return errors;
    }

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
