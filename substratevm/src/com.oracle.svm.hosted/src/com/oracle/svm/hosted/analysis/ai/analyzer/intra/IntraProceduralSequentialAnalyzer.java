package com.oracle.svm.hosted.analysis.ai.analyzer.intra;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.analyzer.Analyzer;
import com.oracle.svm.hosted.analysis.ai.analyzer.context.IntraProceduralAnalysisContext;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.SequentialWtoFixpointIterator;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.policy.IteratorPolicy;
import com.oracle.svm.hosted.analysis.ai.interpreter.TransferFunction;
import com.oracle.svm.hosted.analysis.ai.interpreter.call.IntraProceduralCallInterpreter;
import com.oracle.svm.hosted.analysis.ai.interpreter.node.NodeInterpreter;
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
        IntraProceduralAnalysisContext<Domain> payload = new IntraProceduralAnalysisContext<>(initialDomain, policy, root, debug, nodeInterpreter, checkerManager);
        payload.getLogger().logHighlightedDebugInfo("Running intra-procedural sequential analysis");
        TransferFunction<Domain> transferFunction = new TransferFunction<>(nodeInterpreter, new IntraProceduralCallInterpreter<>(payload), payload.getLogger());
        SequentialWtoFixpointIterator<Domain> iterator = new SequentialWtoFixpointIterator<>(payload, transferFunction);
        doRun(payload, iterator);
    }
}