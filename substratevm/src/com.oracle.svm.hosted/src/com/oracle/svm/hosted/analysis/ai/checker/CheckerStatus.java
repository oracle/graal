package com.oracle.svm.hosted.analysis.ai.checker;

/**
 * Represents the result of a check performed by a {@link Checker}.
 */
public enum CheckerStatus {
    OK,
    WARNING,
    UNKNOWN,
    ERROR;

    @Override
    public String toString() {
        return switch (this) {
            case OK -> "OK";
            case WARNING -> "Warning";
            case UNKNOWN -> "Unknown";
            case ERROR -> "Error";
        };
    }
}
