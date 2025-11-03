package com.oracle.svm.hosted.analysis.ai.checker;

/**
 * Represents the result of a check performed by a {@link Checker}.
 */
public enum CheckerStatus {
    INFO,
    WARNING,
    ERROR;

    @Override
    public String toString() {
        return switch (this) {
            case INFO -> "Info";
            case WARNING -> "Warning";
            case ERROR -> "Error";
        };
    }
}
