package com.oracle.svm.hosted.analysis.ai.checker;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;

import java.util.ArrayList;
import java.util.List;

public final class CheckerManager {

    private final List<Checker> checkers;
    private final List<CheckerSummary> summaries;

    public CheckerManager() {
        this.checkers = new ArrayList<>();
        this.summaries = new ArrayList<>();
    }

    public CheckerManager(List<Checker> checkers) {
        this.checkers = checkers;
        this.summaries = new ArrayList<>(checkers.size());
    }

    /**
     * Registers a new checker that will be used in the analysis.
     *
     * @param checker the checker to register
     */
    public <T extends AbstractDomain<T>> void registerChecker(Checker checker) {
        checkers.add(checker);
    }

    /**
     * Run all compatible checkers for the given abstract state map
     */
    public List<CheckerResult> checkAll(AbstractStateMap<?> abstractStateMap) {
        List<CheckerResult> results = new ArrayList<>();

        for (int i = 0; i < checkers.size(); i++) {
            Checker checker = checkers.get(i);
            CheckerResult result = checker.check(abstractStateMap);
            results.add(result);
            summaries.get(i).addResult(result);
        }

        return results;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < checkers.size(); i++) {
            Checker checker = checkers.get(i);
            CheckerSummary summary = summaries.get(i);
            sb.append(checker.getDescription()).append(":\n");
            sb.append(summary.toString()).append("\n");
        }
        return sb.toString();
    }

    public String printCheckers() {
        StringBuilder sb = new StringBuilder();
        for (Checker checker : checkers) {
            sb.append(checker.getDescription()).append(":\n");
        }
        return sb.toString();
    }
}