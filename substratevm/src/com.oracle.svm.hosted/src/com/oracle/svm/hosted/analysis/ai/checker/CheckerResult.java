package com.oracle.svm.hosted.analysis.ai.checker;

/*
 * Represents the result of a check performed by a checker.
 * If the Checker has additional information to provide, it can be included in the details field.
 */
public record CheckerResult(CheckerStatus result, String details) {

    @Override
    public String toString() {
        return result.toString() + (details != null && !details.isEmpty() ? ": " + details : "");
    }
}