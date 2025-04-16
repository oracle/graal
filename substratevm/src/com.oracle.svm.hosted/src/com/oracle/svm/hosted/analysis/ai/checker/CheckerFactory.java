package com.oracle.svm.hosted.analysis.ai.checker;

import com.oracle.svm.hosted.analysis.ai.checker.example.DivisionByZeroChecker;

/**
 * Factory for creating checkers.
 * Whenever a new checker is implemented, it should be added here for easier creation.
 */
public final class CheckerFactory {

    public static Checker createChecker(CheckerName checkerName) {
        return switch (checkerName) {
            case DivisionByZero -> new DivisionByZeroChecker();
        };
    }
}
