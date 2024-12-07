package com.oracle.svm.hosted.analysis.ai.analyzer.intra;

import com.oracle.svm.hosted.analysis.ai.analyzer.Analyzer;
import com.oracle.svm.hosted.analysis.ai.checker.Checker;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.IteratorPolicy;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.SequentialWtoFixpointIterator;
import com.oracle.svm.hosted.analysis.ai.interpreter.IntraProceduralCallInterpreter;
import com.oracle.svm.hosted.analysis.ai.interpreter.NodeInterpreter;
import com.oracle.svm.hosted.analysis.ai.interpreter.TransferFunction;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an intra-procedural sequential analyzer
 *
 * @param <Domain> type of the derived {@link AbstractDomain}
 */
public class IntraProceduralSequentialAnalyzer<Domain extends AbstractDomain<Domain>> extends Analyzer<Domain> {

    private final DebugContext debug;

    public IntraProceduralSequentialAnalyzer(DebugContext debug) {
        this.debug = debug;
    }

    @Override
    public void run(ControlFlowGraph graph, Domain initialDomain, NodeInterpreter<Domain> nodeInterpreter) {
        var transferFunction = new TransferFunction<>(nodeInterpreter, new IntraProceduralCallInterpreter());
        var iterator = new SequentialWtoFixpointIterator<>(graph, transferFunction, initialDomain, debug);
        var stateMap = iterator.iterateUntilFixpoint();
        debug.log("Analysis finished");
        debug.log(stateMap.toString());
        debug.log("Performing checks");
    }
}
