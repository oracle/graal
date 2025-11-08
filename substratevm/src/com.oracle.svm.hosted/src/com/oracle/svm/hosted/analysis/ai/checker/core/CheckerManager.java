package com.oracle.svm.hosted.analysis.ai.checker.core;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.checker.core.facts.Fact;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;
import jdk.graal.compiler.nodes.StructuredGraph;

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

    @SuppressWarnings("unchecked")
    public <Domain extends AbstractDomain<Domain>> void runCheckers(AnalysisMethod method, AbstractState<Domain> abstractState) {
        AbstractInterpretationLogger logger = AbstractInterpretationLogger.getInstance();
        logger.log("Running provided checkers on method: " + method.getName(), LoggerVerbosity.CHECKER);
        List<Fact> allFacts = new ArrayList<>();

        for (var checker : checkers) {
            if (!checker.isCompatibleWith(abstractState)) {
                continue;
            }
            var typedChecker = (Checker<Domain>) checker;
            List<Fact> facts = typedChecker.produceFacts(method, abstractState);

            if (facts != null && !facts.isEmpty()) {
                allFacts.addAll(facts);
            }
        }

        logger.logFacts(allFacts);
        StructuredGraph graph = abstractState.getCfgGraph().graph;
        FactAggregator aggregator = FactAggregator.aggregate(allFacts);
        FactApplierSuite applierSuite = FactApplierSuite.fromRegistry(aggregator, true);
        try {
            applierSuite.runAppliers(method, graph, aggregator);
        } catch (Exception e) {
            logger.log("IGV dump failed: " + e.getMessage(), LoggerVerbosity.CHECKER_ERR);
        } catch (Throwable e) {
            return; // Avoid breaking the analysis pipeline
        }
    }
}
