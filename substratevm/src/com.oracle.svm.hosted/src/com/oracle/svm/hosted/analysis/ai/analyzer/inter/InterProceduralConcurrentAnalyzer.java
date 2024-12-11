package com.oracle.svm.hosted.analysis.ai.analyzer.inter;

import com.oracle.svm.hosted.analysis.ai.analyzer.Analyzer;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.ConcurrentWpoFixpointIterator;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.policy.IteratorPolicy;
import com.oracle.svm.hosted.analysis.ai.interpreter.call.InterProceduralCallInterpreter;
import com.oracle.svm.hosted.analysis.ai.interpreter.node.NodeInterpreter;
import com.oracle.svm.hosted.analysis.ai.interpreter.TransferFunction;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;

public class InterProceduralConcurrentAnalyzer<
        Domain extends AbstractDomain<Domain>>
        extends Analyzer<Domain> {

    private final DebugContext debug;

    public InterProceduralConcurrentAnalyzer(DebugContext debug) {
        this.debug = debug;
    }

    @Override
    public void run(ControlFlowGraph graph, Domain initialDomain, NodeInterpreter<Domain> nodeInterpreter) {
        var policy = IteratorPolicy.DEFAULT_CONCURRENT;
        var transferFunction = new TransferFunction<>(nodeInterpreter, new InterProceduralCallInterpreter<>(), initialDomain, policy, debug);
        var iterator = new ConcurrentWpoFixpointIterator<>(graph, policy, transferFunction, initialDomain, debug);
        debug.log("Starting inter procedural concurrent analysis");
        var stateMap = iterator.iterateUntilFixpoint();
        debug.log("Analysis finished");
        debug.log(stateMap.toString());
    }
}
