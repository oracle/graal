package com.oracle.svm.hosted.analysis.ai;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.InvokeInfo;
import com.oracle.svm.hosted.ProgressReporter;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.StructuredGraph;

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

    /**
     * TODO this is where the actual analyses will start
     */
    private void doRun() {
        debug.log("Printing the graph");
        var rootGraph = getGraph(root);
        for (Node node : rootGraph.getNodes()) {
            debug.log("\t" + node);
        }

        debug.log("Printing the invokes");
        for (InvokeInfo invoke : root.getInvokes()) {
            debug.log("Has invoke: " + invoke);
            for (AnalysisMethod callee : invoke.getOriginalCallees()) {
                debug.log("\tCallee: " + callee);
            }
        }
    }

    private StructuredGraph getGraph(AnalysisMethod method) {
        return method.decodeAnalyzedGraph(debug, null);
    }
}
