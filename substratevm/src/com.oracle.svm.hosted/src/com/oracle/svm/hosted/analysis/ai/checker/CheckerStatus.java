package com.oracle.svm.hosted.analysis.ai.checker;

/**
 * Represents the result of a check performed by a {@link Checker}.
 */
public enum CheckerStatus {
    WARNING,
    ERROR;

    @Override
    public String toString() {
        return switch (this) {
            case WARNING -> "Warning";
            case ERROR -> "Error";
        };
    }
}
