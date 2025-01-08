package com.oracle.svm.hosted.analysis.ai.util;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.InvokeInfo;
import com.oracle.graal.pointsto.util.AnalysisError;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraphBuilder;
import jdk.graal.compiler.nodes.cfg.HIRBlock;

public class GraphUtils {

    public static ControlFlowGraph getGraph(AnalysisMethod root, DebugContext debug) {
        StructuredGraph structuredGraph = root.decodeAnalyzedGraph(debug, null);
        if (structuredGraph == null) {
            return null;
        }
        return new ControlFlowGraphBuilder(structuredGraph).build();
    }

    public static AnalysisMethod getInvokeAnalysisMethod(AnalysisMethod root, Invoke invoke) {
        for (InvokeInfo invokeInfo : root.getInvokes()) {
            if (invoke.getTargetMethod().equals(invokeInfo.getTargetMethod())) {
                return invokeInfo.getTargetMethod();
            }
        }
        throw AnalysisError.interruptAnalysis("Invoke not found in method");
    }

    public static void printGraph(AnalysisMethod root, DebugContext debug) {
        debug.log("Printing the graph of AnalysisMethod " + root);
        ControlFlowGraph graph = getGraph(root, debug);
        if (graph == null) {
            throw AnalysisError.interruptAnalysis("ControlFlowGraph is null");
        }

        for (HIRBlock block : graph.getBlocks()) {
            debug.log("\t" + block);

            for (Node node : block.getNodes()) {
                debug.log("\t\t" + node);

                for (Node successor : node.successors()) {
                    debug.log("\t\t\t" + successor);
                }
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
}
