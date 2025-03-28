package com.oracle.svm.hosted.analysis.ai.checker;

import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import com.oracle.svm.hosted.analysis.ai.util.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.util.LoggerVerbosity;
import jdk.vm.ci.meta.ResolvedJavaMethod;

import java.util.ArrayList;
import java.util.List;

public final class CheckerManager {

    private final List<Checker> checkers;

    public CheckerManager() {
        this.checkers = new ArrayList<>();
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
     * @param method           the method to be checked
     * @param abstractStateMap representing the abstract state after the fixpoint iteration on {@param method}
     */
    public void runCheckers(ResolvedJavaMethod method, AbstractStateMap<?> abstractStateMap) {
        AbstractInterpretationLogger logger = AbstractInterpretationLogger.getInstance();
        String methodName = method.getName();
        if (checkers.isEmpty()) {
            logger.log("Running checks on method: " + methodName + " -> no checkers were provided in the current analysis", LoggerVerbosity.CHECKER);
            return;
        }

        logger.log("Running checks on method: " + methodName, LoggerVerbosity.CHECKER);
        List<CheckerSummary> checkerSummaries = new ArrayList<>();
        for (Checker checker : checkers) {
            List<CheckerResult> checkerResults = checker.check(abstractStateMap);
            CheckerSummary summary = new CheckerSummary(checker, checkerResults);
            checkerSummaries.add(summary);
        }

        for (CheckerSummary checkerSummary : checkerSummaries) {
            logCheckerSummary(checkerSummary);
        }
    }

    private void logCheckerSummary(CheckerSummary checkerSummary) {
        AbstractInterpretationLogger logger = AbstractInterpretationLogger.getInstance();
        logger.log(checkerSummary.getChecker().getDescription(), LoggerVerbosity.CHECKER);

        List<CheckerResult> errors = checkerSummary.getErrors();
        List<CheckerResult> warnings = checkerSummary.getWarnings();
        if (!errors.isEmpty()) {
            logger.log("Number of errors: " + errors.size(), LoggerVerbosity.CHECKER);
            for (CheckerResult error : errors) {
                logger.log("Error: " + error, LoggerVerbosity.CHECKER);
            }
        } else {
            logger.log("No errors reported", LoggerVerbosity.CHECKER);
        }

        if (!warnings.isEmpty()) {
            logger.log("Number of warnings: " + warnings.size(), LoggerVerbosity.CHECKER);
            for (CheckerResult warning : warnings) {
                logger.log("Warning: " + warning, LoggerVerbosity.CHECKER);
            }
        } else {
            logger.log("No warnings reported", LoggerVerbosity.CHECKER);
        }

        if (errors.isEmpty() && warnings.isEmpty()) {
            logger.log("Everything is OK", LoggerVerbosity.CHECKER);
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
}
