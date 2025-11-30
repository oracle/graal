package com.oracle.svm.hosted.analysis.ai.checker.core;

import com.oracle.graal.pointsto.flow.AnalysisParsedGraph;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.checker.core.facts.Fact;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;
import com.oracle.svm.hosted.analysis.ai.summary.MethodSummary;
import com.oracle.svm.hosted.analysis.ai.util.AbstractInterpretationServices;
import jdk.graal.compiler.nodes.EncodedGraph;
import jdk.graal.compiler.nodes.GraphEncoder;
import jdk.graal.compiler.nodes.StructuredGraph;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CheckerManager {

    private final List<Checker<?>> checkers = new ArrayList<>();

    public void registerChecker(Checker<?> checker) {
        checkers.add(checker);
    }

    @SuppressWarnings("unchecked")
    public <Domain extends AbstractDomain<Domain>> void runCheckersOnSingleMethod(AnalysisMethod method, AbstractState<Domain> abstractState, StructuredGraph graph) {
        AbstractInterpretationLogger logger = AbstractInterpretationLogger.getInstance();
        var stats = AbstractInterpretationServices.getInstance().getStats();
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

        /* Apply the facts to the analysisMethod */
        FactAggregator aggregator = FactAggregator.aggregate(allFacts);
        FactApplierSuite applierSuite = FactApplierSuite.fromRegistry(aggregator, true);
        ApplierResult result = applierSuite.runAppliers(method, graph, aggregator);

        /* Update stats */
        stats.addMethodBoundsEliminated(method, result.boundsChecksEliminated());
        stats.addMethodBranchesFoldedTrue(method, result.branchesFoldedTrue());
        stats.addMethodBranchesFoldedFalse(method, result.branchesFoldedFalse());
        stats.addMethodConstantsStamped(method, result.constantsStamped());
        stats.addMethodConstantsPropagated(method, result.constantsPropagated());
        stats.addMethodInvokesReplaced(method, result.invokesReplacedWithConstants());

        applyAbstractInterpretationResults(method, graph);
    }

    private void applyAbstractInterpretationResults(AnalysisMethod method, StructuredGraph graph) {
        EncodedGraph encoded = GraphEncoder.encodeSingleGraph(graph, AnalysisParsedGraph.HOST_ARCHITECTURE);
        method.setAnalyzedGraph(encoded);
    }

    public <Domain extends AbstractDomain<Domain>> void runCheckersOnMethodSummaries(Map<AnalysisMethod, MethodSummary<Domain>> methodSummaryMap,
                                                                                     Map<AnalysisMethod, StructuredGraph> methodGraphMap) {
        for (var entry : methodSummaryMap.entrySet()) {
            AnalysisMethod method = entry.getKey();
            MethodSummary<Domain> methodSummary = entry.getValue();
            AbstractState<Domain> abstractState = methodSummary.getStateAcrossAllContexts();
            runCheckersOnSingleMethod(method, abstractState, methodGraphMap.get(method));
        }
    }
}
