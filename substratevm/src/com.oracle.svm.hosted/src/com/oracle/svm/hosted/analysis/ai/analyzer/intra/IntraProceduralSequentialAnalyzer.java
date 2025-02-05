package com.oracle.svm.hosted.analysis.ai.analyzer.intra;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.analyzer.Analyzer;
import com.oracle.svm.hosted.analysis.ai.analyzer.payload.IntraProceduralAnalysisPayload;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.SequentialWtoFixpointIterator;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.policy.IteratorPolicy;
import com.oracle.svm.hosted.analysis.ai.interpreter.NodeInterpreter;
import com.oracle.svm.hosted.analysis.ai.interpreter.TransferFunction;
import com.oracle.svm.hosted.analysis.ai.interpreter.call.IntraProceduralCallInterpreter;
import jdk.graal.compiler.debug.DebugContext;

/**
 * Represents an intra-procedural sequential analyzer
 * @param <Domain> the type of derived {@link AbstractDomain}
 */
public class IntraProceduralSequentialAnalyzer<Domain extends AbstractDomain<Domain>> extends Analyzer<Domain> {

    public IntraProceduralSequentialAnalyzer(AnalysisMethod root, DebugContext debug, IteratorPolicy iteratorPolicy) {
        super(root, debug, iteratorPolicy);
    }

    public IntraProceduralSequentialAnalyzer(AnalysisMethod root, DebugContext debug) {
        super(root, debug, IteratorPolicy.DEFAULT_SEQUENTIAL);
    }

    @Override
    public void run(Domain initialDomain, NodeInterpreter<Domain> nodeInterpreter) {
        IteratorPolicy policy = IteratorPolicy.DEFAULT_SEQUENTIAL;
        IntraProceduralAnalysisPayload<Domain> payload = new IntraProceduralAnalysisPayload<>(initialDomain, policy, root, debug, nodeInterpreter, checkerManager);
        payload.getLogger().logHighlightedDebugInfo("Running intra-procedural sequential analysis");
        TransferFunction<Domain> transferFunction = new TransferFunction<>(nodeInterpreter, new IntraProceduralCallInterpreter<>(), payload);
        SequentialWtoFixpointIterator<Domain> iterator = new SequentialWtoFixpointIterator<>(payload, transferFunction);
        doRun(payload, iterator);
    }
}