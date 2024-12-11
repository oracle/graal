package com.oracle.svm.hosted.analysis.ai.analyzer.intra;

import com.oracle.svm.hosted.analysis.ai.analyzer.Analyzer;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.ConcurrentWpoFixpointIterator;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.policy.IteratorPolicy;
import com.oracle.svm.hosted.analysis.ai.interpreter.call.IntraProceduralCallInterpreter;
import com.oracle.svm.hosted.analysis.ai.interpreter.node.NodeInterpreter;
import com.oracle.svm.hosted.analysis.ai.interpreter.TransferFunction;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;

public class IntraProceduralConcurrentAnalyzer<
        Domain extends AbstractDomain<Domain>>
        extends Analyzer<Domain> {

    private final DebugContext debug;

    public IntraProceduralConcurrentAnalyzer(DebugContext debug) {
        this.debug = debug;
    }

    @Override
    public void run(ControlFlowGraph graph, Domain initialDomain, NodeInterpreter<Domain> nodeInterpreter) {
        var policy = IteratorPolicy.DEFAULT_CONCURRENT;
        var transferFunction = new TransferFunction<>(nodeInterpreter, new IntraProceduralCallInterpreter<>(), initialDomain, policy, debug);
        var iterator = new ConcurrentWpoFixpointIterator<>(graph, policy, transferFunction, initialDomain, debug);
        debug.log("Starting intra procedural concurrent analysis");
        var stateMap = iterator.iterateUntilFixpoint();
        debug.log("Analysis finished");
        debug.log(stateMap.toString());
    }
}
