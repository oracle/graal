package com.oracle.svm.hosted.analysis.ai;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.InvokeInfo;
import com.oracle.svm.hosted.ProgressReporter;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.StructuredGraph;
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
                doRun();
            }
        }
    }

    private void doRun() {
        var cfgGraph = getGraph(root);
        debug.log("Printing ControlFlowGraph nodes");
        for (Node node : cfgGraph.graph.getNodes()) {
            debug.log("\tNode: " + node);
            debug.log("\t\tPredecessors: " + node.cfgPredecessors());
            debug.log("\t\tSuccessors: " + node.cfgSuccessors());
            debug.log("\t\tInputs: " + node.inputs());
            debug.log("\t\t" + node.getNodeSourcePosition());
        }

        debug.log("Printing the invokes");
        for (InvokeInfo invoke : root.getInvokes()) {
            var method = invoke.getTargetMethod();
            var g2 = method.decodeAnalyzedGraph(debug, null);
            for (Node node : g2.getNodes()) {
                debug.log("\t" + node);
            }
            debug.log("Has invoke: " + invoke);
            for (AnalysisMethod callee : invoke.getOriginalCallees()) {
                debug.log("\tCallee: " + callee);
            }
        }


    }

    private ControlFlowGraph getGraph(AnalysisMethod method) {
        StructuredGraph structuredGraph = method.decodeAnalyzedGraph(debug, null);
        for (var node : structuredGraph.getNodes()) {
            debug.log("\t" + node);
        }
        return new ControlFlowGraphBuilder(structuredGraph).build();
    }
}
