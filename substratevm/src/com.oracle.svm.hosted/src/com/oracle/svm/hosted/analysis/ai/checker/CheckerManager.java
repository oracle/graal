package com.oracle.svm.hosted.analysis.ai.checker;

import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import com.oracle.svm.hosted.analysis.ai.util.AbstractInterpretationLogger;

import java.util.ArrayList;
import java.util.List;

public final class CheckerManager {

    private final List<Checker> checkers;

    public CheckerManager() {
        this.checkers = new ArrayList<>();
    }

    public CheckerManager(List<Checker> checkers) {
        this.checkers = checkers;
    }

    /**
     * Registers a new checker that will be used in the analysis.
     *
     * @param checker the checker to register
     */
    public void registerChecker(Checker checker) {
        checkers.add(checker);
    }

    /**
     * Executes all registered checkers on the given abstract state map and collects the summary of results.
     *
     * @param abstractStateMap the abstract state map to be checked, representing the state after the fixpoint iteration
     */
    public void checkAll(String methodName, AbstractStateMap<?> abstractStateMap) {
        AbstractInterpretationLogger logger = AbstractInterpretationLogger.getInstance();
        logger.logHighlightedDebugInfo("Running checks on method: " + methodName);
        logUsedCheckers();
        List<CheckerSummary> checkerSummaries = new ArrayList<>();
        for (Checker checker : checkers) {
            List<CheckerResult> checkerResults = checker.check(abstractStateMap);
            CheckerSummary summary = new CheckerSummary(checkerResults);
            checkerSummaries.add(summary);
            printCheckResult(checker, summary);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Checker checker : checkers) {
            sb.append(checker.getDescription());
        }
        return sb.toString();
    }

    private void printCheckResult(Checker checker, CheckerSummary summary) {
        AbstractInterpretationLogger logger = AbstractInterpretationLogger.getInstance();
        logger.logDebugInfo("Checker: " + checker.getDescription());

        List<CheckerResult> errors = summary.getErrors();
        List<CheckerResult> warnings = summary.getWarnings();

        if (!errors.isEmpty()) {
            logger.logDebugInfo("Number of errors: " + errors.size());
            for (CheckerResult error : errors) {
                logger.logDebugError("Error: " + error);
            }
        } else {
            logger.logDebugInfo("No errors reported");
        }

        if (!warnings.isEmpty()) {
            logger.logDebugInfo("Number of warnings: " + warnings.size());
            for (CheckerResult warning : warnings) {
                logger.logDebugInfo("Warning: " + warning);
            }
        } else {
            logger.logDebugInfo("No warnings reported");
        }

        if (errors.isEmpty() && warnings.isEmpty()) {
            logger.logDebugInfo("Everything is OK");
        }
    }

    private void logUsedCheckers() {
        AbstractInterpretationLogger logger = AbstractInterpretationLogger.getInstance();
        if (checkers.isEmpty()) {
            logger.logHighlightedDebugInfo("No checkers were provided in the current analysis");
            return;
        }

        logger.logHighlightedDebugInfo("Using the following checkers: ");
        for (Checker checker : checkers) {
            logger.logDebugInfo(checker.getDescription());
        }
    }
}
