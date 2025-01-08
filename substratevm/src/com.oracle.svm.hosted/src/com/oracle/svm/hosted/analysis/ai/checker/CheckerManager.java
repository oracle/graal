package com.oracle.svm.hosted.analysis.ai.checker;

import java.util.ArrayList;
import java.util.List;

public class CheckerManager {

    private final List<Checker> checkers = new ArrayList<>();

    public void registerChecker(Checker checker) {
        checkers.add(checker);
    }

    public List<Checker> getCheckers() {
        return checkers;
    }
}