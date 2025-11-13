package com.oracle.svm.hosted.analysis.ai.checker.core;

import com.oracle.graal.pointsto.flow.AnalysisParsedGraph;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.checker.core.facts.Fact;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;
import jdk.graal.compiler.nodes.EncodedGraph;
import jdk.graal.compiler.nodes.GraphEncoder;
import jdk.graal.compiler.nodes.StructuredGraph;

import java.util.ArrayList;
import java.util.List;

public final class CheckerManager {

    private final List<Checker<?>> checkers;
    private final boolean persistRewrites;

    public CheckerManager() {
        this(true);
    }

    public CheckerManager(boolean persistRewrites) {
        this.checkers = new ArrayList<>();
        this.persistRewrites = persistRewrites;
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
        StructuredGraph graph = abstractState.getGraph();
        FactAggregator aggregator = FactAggregator.aggregate(allFacts);
        FactApplierSuite applierSuite = FactApplierSuite.fromRegistry(aggregator, true);
        applierSuite.runAppliers(method, graph, aggregator);
        if (persistRewrites) {
            applyAbstractInterpretationResults(method, graph);
            method.setAnalyzedGraph(GraphEncoder.encodeSingleGraph(graph, AnalysisParsedGraph.HOST_ARCHITECTURE));
        } else {
            logger.log("[CheckerManager] Skipping persistence of rewrites (persistRewrites=false)", LoggerVerbosity.CHECKER_WARN);
        }
    }

    private void applyAbstractInterpretationResults(AnalysisMethod method, StructuredGraph graph) {
        EncodedGraph encoded = GraphEncoder.encodeSingleGraph(graph, AnalysisParsedGraph.HOST_ARCHITECTURE);
        method.setAnalyzedGraph(encoded);
    }
}
