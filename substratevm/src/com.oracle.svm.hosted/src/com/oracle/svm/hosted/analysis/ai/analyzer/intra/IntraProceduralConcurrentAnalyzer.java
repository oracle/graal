package com.oracle.svm.hosted.analysis.ai.analyzer.intra;

import com.oracle.svm.hosted.analysis.ai.analyzer.Analyzer;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.ConcurrentWpoFixpointIterator;
import com.oracle.svm.hosted.analysis.ai.interpreter.IntraProceduralCallInterpreter;
import com.oracle.svm.hosted.analysis.ai.interpreter.NodeInterpreter;
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
        var transferFunction = new TransferFunction<>(nodeInterpreter, new IntraProceduralCallInterpreter());
        var iterator = new ConcurrentWpoFixpointIterator<>(graph, transferFunction, initialDomain, debug);
        debug.log("Starting analysis");
        var stateMap = iterator.iterateUntilFixpoint();
        debug.log("Analysis finished");
    }
}
