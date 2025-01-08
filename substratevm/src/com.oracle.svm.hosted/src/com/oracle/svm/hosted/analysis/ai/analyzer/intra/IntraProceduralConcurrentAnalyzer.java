package com.oracle.svm.hosted.analysis.ai.analyzer.intra;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.analyzer.Analyzer;
import com.oracle.svm.hosted.analysis.ai.analyzer.context.IntraProceduralAnalysisContext;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.ConcurrentWpoFixpointIterator;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.policy.IteratorPolicy;
import com.oracle.svm.hosted.analysis.ai.interpreter.TransferFunction;
import com.oracle.svm.hosted.analysis.ai.interpreter.call.IntraProceduralCallInterpreter;
import com.oracle.svm.hosted.analysis.ai.interpreter.node.NodeInterpreter;
import jdk.graal.compiler.debug.DebugContext;

/**
 * Represents an intra-procedural concurrent analyzer
 * @param <Domain> the type of derived {@link AbstractDomain}
 */
public class IntraProceduralConcurrentAnalyzer<Domain extends AbstractDomain<Domain>> extends Analyzer<Domain> {

    public IntraProceduralConcurrentAnalyzer(AnalysisMethod root, DebugContext debug, IteratorPolicy iteratorPolicy) {
        super(root, debug, iteratorPolicy);
    }

    public IntraProceduralConcurrentAnalyzer(AnalysisMethod root, DebugContext debug) {
        super(root, debug, IteratorPolicy.DEFAULT_CONCURRENT);
    }

    @Override
    public void run(Domain initialDomain, NodeInterpreter<Domain> nodeInterpreter) {
        IteratorPolicy policy = IteratorPolicy.DEFAULT_CONCURRENT;
        IntraProceduralAnalysisContext<Domain> payload = new IntraProceduralAnalysisContext<>(initialDomain, policy, root, debug, nodeInterpreter, checkerManager);
        payload.getLogger().logHighlightedDebugInfo("Running intra-procedural concurrent analysis");
        TransferFunction<Domain> transferFunction = new TransferFunction<>(nodeInterpreter, new IntraProceduralCallInterpreter<>(payload), payload.getLogger());
        ConcurrentWpoFixpointIterator<Domain> iterator = new ConcurrentWpoFixpointIterator<>(payload, transferFunction);
        doRun(payload, iterator);
    }
}