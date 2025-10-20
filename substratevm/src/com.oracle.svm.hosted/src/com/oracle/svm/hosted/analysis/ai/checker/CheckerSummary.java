package com.oracle.svm.hosted.analysis.ai.checker;

import java.util.LinkedList;
import java.util.List;

/**
 * Summarizes the results of a single checker.
 * Contains a list of warnings and errors.
 */
public final class CheckerSummary {

    private final Checker<?> checker;
    private final List<CheckerResult> warnings;
    private final List<CheckerResult> errors;

    public CheckerSummary(Checker<?> checker, List<CheckerResult> checkerResults) {
        this.checker = checker;
        this.warnings = new LinkedList<>();
        this.errors = new LinkedList<>();
        for (var result : checkerResults) {
            addResult(result);
        }
    }

    public Checker<?> getChecker() {
        return checker;
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

    private void addWarning(CheckerResult warning) {
        warnings.add(warning);
    }

    private void addError(CheckerResult error) {
        errors.add(error);
    }
}
