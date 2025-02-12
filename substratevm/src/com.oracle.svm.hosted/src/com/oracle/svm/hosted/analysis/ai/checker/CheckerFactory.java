package com.oracle.svm.hosted.analysis.ai.checker;

/**
 * Factory for creating checkers.
 * Whenever a new checker is added, it should be added here for easier creation.
 */
public final class CheckerFactory {

    public static Checker createChecker(CheckerName checkerName) {
        return switch (checkerName) {
            case DivisionByZero -> new DivisionByZeroChecker();
        };
    }
}
