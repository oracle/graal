package com.oracle.svm.hosted.analysis.ai;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.ProgressReporter;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.nodes.Invoke;
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
        printGraph(cfgGraph);
    }

    private void printGraph(ControlFlowGraph cfg) {
        debug.log("Printing ControlFlowGraph");
        for (var block : cfg.getBlocks()) {
            debug.log("\t" + block);
            for (var node : block.getNodes()) {
                debug.log("\t\t" + node);
                /* Warning, this won't work with call graph cycles, fix this using summaries */
                if (node instanceof Invoke invokeNode) {
                    debug.log("\t\t\t" + invokeNode);
                    debug.log("\t\t\t" + invokeNode.callTarget().targetName());
                    var callTargetCfg = new ControlFlowGraphBuilder(invokeNode.callTarget().graph()).build();
                    printGraph(callTargetCfg);
                } else {
                    for (var succ : node.cfgSuccessors()) {
                        debug.log("\t\t\t" + succ);
                    }
                    for (var input : node.inputs()) {
                        debug.log("\t\t\t" + input);
                    }
                }
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
