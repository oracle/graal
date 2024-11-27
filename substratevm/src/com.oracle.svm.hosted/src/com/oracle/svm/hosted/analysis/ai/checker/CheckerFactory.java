package com.oracle.svm.hosted.analysis.ai.checker;

public class CheckerFactory {

    public static Checker createChecker(CheckerName checkerName) {
        return switch (checkerName) {
            case DivisionByZero -> new DivisionByZeroChecker();
        };
    }
}
