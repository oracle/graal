package com.oracle.svm.hosted.analysis.ai.checker.core;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.checker.core.facts.Fact;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;
import com.oracle.svm.hosted.analysis.ai.checker.annotator.AssertInjector;
import com.oracle.svm.hosted.analysis.ai.checker.annotator.RewriterOrchestrator;

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

    public <Domain extends AbstractDomain<Domain>> void runCheckers(AnalysisMethod method, AbstractState<Domain> abstractState) {
        AbstractInterpretationLogger logger = AbstractInterpretationLogger.getInstance();
        List<CheckerSummary> checkerSummaries = new ArrayList<>();
        List<Fact> allFacts = new ArrayList<>();
        logger.log("Running provided checkers on method: " + method.getName(), LoggerVerbosity.CHECKER);

        for (var checker : checkers) {
            if (checker.isCompatibleWith(abstractState)) {
                try {
                    @SuppressWarnings("unchecked")
                    Checker<Domain> typedChecker = (Checker<Domain>) checker;
                    List<CheckerResult> checkerResults = typedChecker.check(method, abstractState);
                    CheckerSummary summary = new CheckerSummary(checker, checkerResults);
                    checkerSummaries.add(summary);

                    List<Fact> facts = typedChecker.produceFacts(method, abstractState);
                    if (facts != null && !facts.isEmpty()) {
                        allFacts.addAll(facts);
                    }
                } catch (ClassCastException e) {
                    logger.log("Compatibility check error in " + checker.getDescription(),
                            LoggerVerbosity.CHECKER_ERR);
                }
            } else {
                logger.log("Skipping incompatible checker: " + checker.getDescription(),
                        LoggerVerbosity.CHECKER);
            }
        }

        logger.logFacts(allFacts);
        FactAggregator aggregator = FactAggregator.aggregate(allFacts);
        RewriterOrchestrator.apply(method, abstractState.getCfgGraph().graph, aggregator);
        AssertInjector.injectAnchors(abstractState.getCfgGraph().graph, allFacts);
        try {
            AbstractInterpretationLogger.dumpGraph(method, abstractState.getCfgGraph().graph, "GraalAF");
        } catch (Exception e) {
            logger.log("IGV dump failed: " + e.getMessage(), LoggerVerbosity.CHECKER_ERR);
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
