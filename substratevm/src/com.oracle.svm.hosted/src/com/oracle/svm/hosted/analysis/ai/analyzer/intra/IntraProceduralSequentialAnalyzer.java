package com.oracle.svm.hosted.analysis.ai.analyzer.intra;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.analyzer.Analyzer;
import com.oracle.svm.hosted.analysis.ai.analyzer.payload.AnalysisPayload;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.SequentialWtoFixpointIterator;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import com.oracle.svm.hosted.analysis.ai.interpreter.NodeInterpreter;
import com.oracle.svm.hosted.analysis.ai.interpreter.call.IntraProceduralCallHandler;
import jdk.graal.compiler.debug.DebugContext;

public final class IntraProceduralSequentialAnalyzer<Domain extends AbstractDomain<Domain>> extends Analyzer<Domain> {

    private IntraProceduralSequentialAnalyzer(Builder<Domain> builder) {
        super(builder);
    }

    @Override
    public void analyzeMethod(AnalysisMethod method, DebugContext debug) {
        IntraProceduralCallHandler<Domain> callHandler = new IntraProceduralCallHandler<>();
        AnalysisPayload<Domain> payload = createPayload(method, debug, callHandler);
        payload.getLogger().logHighlightedDebugInfo("Running intra-procedural sequential analysis");
        SequentialWtoFixpointIterator<Domain> iterator = new SequentialWtoFixpointIterator<>(payload);
        payload.getLogger().logDebugInfo("IntraProceduralSequentialAnalyzer: Running fixpoint iteration");
        AbstractStateMap<Domain> stateMap = iterator.iterateUntilFixpoint();
        payload.getLogger().logToFile("Abstract state map after analysis: ");
        payload.getLogger().logToFile(stateMap.toString());
        payload.getLogger().logHighlightedDebugInfo("Analysis finished, you can see the analysis output at " + payload.getLogger().fileName());
    }

    public static class Builder<Domain extends AbstractDomain<Domain>> extends Analyzer.Builder<Domain> {

        public Builder(Domain initialDomain, NodeInterpreter<Domain> nodeInterpreter) {
            super(initialDomain, nodeInterpreter);
        }

        public IntraProceduralSequentialAnalyzer<Domain> build() {
            return new IntraProceduralSequentialAnalyzer<>(this);
        }
    }
}