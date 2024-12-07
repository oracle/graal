package com.oracle.svm.hosted.analysis.ai;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.InvokeInfo;
import com.oracle.svm.hosted.ProgressReporter;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraphBuilder;

public class AbstractInterpretationDriver {

    private final DebugContext debug;
    private final AnalysisMethod root;

    public AbstractInterpretationDriver(DebugContext debug, AnalysisMethod root) {
        this.debug = debug;
        this.root = root;
    }

    @SuppressWarnings("try")
    public void run() {
        try (ProgressReporter.ReporterClosable c = ProgressReporter.singleton().printAbstractInterpretation()) {
            /*
             * Make a new scope for logging, run with -H:Log=AbstractInterpretation to activate it
             */
            try (var scope = debug.scope("AbstractInterpretation")) {
                debug.log("Starting the analysis");
                doRun();
                debug.log("Analysis finished");
            }
        }
    }

    private void doRun() {
        var cfgGraph = getGraph(root);
        debug.log("Printing the control flow graph");
        for (var block : cfgGraph.getBlocks()) {
            debug.log("Block: " + block);
            for (var node : block.getNodes()) {
                debug.log("\tNode: " + node);
                debug.log("\t\tPredecessors: " + node.cfgPredecessors());
                debug.log("\t\tSuccessors: " + node.cfgSuccessors());
                debug.log("\t\tInputs: " + node.inputs());
                debug.log("\t\t" + node.getNodeSourcePosition());
            }

            int count = block.getSuccessorCount();
            debug.log("\tSuccessors: ");
            for (int i = 0; i < count; i++) {
                debug.log("\t\t" + block.getSuccessorAt(i));
            }

            count = block.getPredecessorCount();
            debug.log("\tPredecessors: ");
            for (int i = 0; i < count; i++) {
                debug.log("\t\t" + block.getPredecessorAt(i));
            }
        }

        debug.log("Printing the invokes");
        for (InvokeInfo invoke : root.getInvokes()) {
            debug.log("Has invoke: " + invoke);
            for (AnalysisMethod callee : invoke.getOriginalCallees()) {
                debug.log("\tCallee: " + callee);
            }
        }

    }

    private ControlFlowGraph getGraph(AnalysisMethod method) {
        var structuredGraph = method.decodeAnalyzedGraph(debug, null);
        return new ControlFlowGraphBuilder(structuredGraph).build();
    }
}
