package com.oracle.svm.hosted.analysis.ai.checker;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.vm.ci.meta.ResolvedJavaMethod;

import java.util.ArrayList;
import java.util.List;

public final class CheckerManager {

    private final List<Checker<?>> checkers;

    public CheckerManager() {
        this.checkers = new ArrayList<>();
    }

    public void registerChecker(Checker<?> checker) {
        checkers.add(checker);
    }

    public <Domain extends AbstractDomain<Domain>> void runCheckers(
            StructuredGraph graph,
            AbstractStateMap<Domain> abstractStateMap) {

        ResolvedJavaMethod method = graph.method();
        AbstractInterpretationLogger logger = AbstractInterpretationLogger.getInstance();
        String methodName = method.getName();
        List<CheckerSummary> checkerSummaries = new ArrayList<>();
        logger.log("Running provided checkers on method: " + methodName, LoggerVerbosity.CHECKER);

        for (Checker<?> checker : checkers) {
            if (checker.isCompatibleWith(abstractStateMap)) {
                try {
                    @SuppressWarnings("unchecked")
                    Checker<Domain> typedChecker = (Checker<Domain>) checker;
                    List<CheckerResult> checkerResults = typedChecker.check(abstractStateMap, graph);
                    CheckerSummary summary = new CheckerSummary(checker, checkerResults);
                    checkerSummaries.add(summary);
                } catch (ClassCastException e) {
                    logger.log("Compatibility check error in " + checker.getDescription(),
                            LoggerVerbosity.CHECKER_ERR);
                }
            } else {
                logger.log("Skipping incompatible checker: " + checker.getDescription(),
                        LoggerVerbosity.CHECKER);
            }
        }

        for (CheckerSummary checkerSummary : checkerSummaries) {
            logCheckerSummary(checkerSummary);
        }
    }

    private void logCheckerSummary(CheckerSummary checkerSummary) {
        AbstractInterpretationLogger logger = AbstractInterpretationLogger.getInstance();
        List<CheckerResult> errors = checkerSummary.getErrors();
        List<CheckerResult> warnings = checkerSummary.getWarnings();
        logger.log(checkerSummary.getChecker().getDescription(), LoggerVerbosity.CHECKER);

        if (!errors.isEmpty()) {
            logger.log("Number of errors: " + errors.size(), LoggerVerbosity.CHECKER);
            for (CheckerResult error : errors) {
                logger.log(error.details(), LoggerVerbosity.CHECKER_ERR);
            }
        } else {
            logger.log("No errors reported", LoggerVerbosity.CHECKER);
        }

        if (!warnings.isEmpty()) {
            logger.log("Number of warnings: " + warnings.size(), LoggerVerbosity.CHECKER);
            for (CheckerResult warning : warnings) {
                logger.log(warning.details(), LoggerVerbosity.CHECKER_WARN);
            }
        } else {
            logger.log("No warnings reported", LoggerVerbosity.CHECKER);
        }

        if (errors.isEmpty() && warnings.isEmpty()) {
            logger.log("Everything is OK", LoggerVerbosity.CHECKER);
        }
    }
}
