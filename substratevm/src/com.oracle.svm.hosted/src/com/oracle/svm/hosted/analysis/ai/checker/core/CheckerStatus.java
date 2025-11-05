package com.oracle.svm.hosted.analysis.ai.checker.core;

/**
 * Represents the result of a check performed by a {@link Checker}.
 */
public enum CheckerStatus {
    OK,
    INFO,
    WARNING,
    ERROR;

    @Override
    public String toString() {
        return switch (this) {
            case OK -> "Ok";
            case INFO -> "Info";
            case WARNING -> "Warning";
            case ERROR -> "Error";
        };
    }
}
