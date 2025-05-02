package com.oracle.svm.hosted.analysis.ai.util;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.InvokeInfo;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraphBuilder;
import jdk.graal.compiler.nodes.cfg.HIRBlock;

public final class GraphUtil {

    private GraphUtil() {
    }

    public static ControlFlowGraph getGraph(AnalysisMethod root, DebugContext debug) {
        StructuredGraph structuredGraph = root.decodeAnalyzedGraph(debug, null);
        if (structuredGraph == null) {
            throw AnalysisError.interruptAnalysis("Unable to get graph for analysisMethod: " + root);
        }
        return new ControlFlowGraphBuilder(structuredGraph).build();
    }

    public static AnalysisMethod getInvokeAnalysisMethod(AnalysisMethod root, Invoke invoke) {
        for (InvokeInfo invokeInfo : root.getInvokes()) {
            if (invoke.getTargetMethod().equals(invokeInfo.getTargetMethod())) {
                return invokeInfo.getTargetMethod();
            }
        }
        throw AnalysisError.interruptAnalysis(invoke + " not found in: " + root);
    }

    public static void printGraph(AnalysisMethod root, ControlFlowGraph graph) {
        if (graph == null) {
            throw AnalysisError.interruptAnalysis("ControlFlowGraph is null");
        }

        AbstractInterpretationLogger logger = AbstractInterpretationLogger.getInstance();
        logger.log("Graph of AnalysisMethod: " + root, LoggerVerbosity.DEBUG);
        for (HIRBlock block : graph.getBlocks()) {
            logger.log(block.toString(), LoggerVerbosity.DEBUG);

            for (Node node : block.getNodes()) {
                logger.log(node.toString(), LoggerVerbosity.DEBUG);
                logger.log("\tSuccessors: ", LoggerVerbosity.DEBUG);
                for (Node successor : node.successors()) {
                    logger.log("\t\t" + successor.toString(), LoggerVerbosity.DEBUG);
                }
                logger.log("\tInputs: ", LoggerVerbosity.DEBUG);
                for (Node input : node.inputs()) {
                    logger.log("\t\t" + input.toString(), LoggerVerbosity.DEBUG);
                }
            }
        }

        logger.log("The Invokes of the AnalysisMethod: " + root, LoggerVerbosity.DEBUG);
        for (InvokeInfo invoke : root.getInvokes()) {
            logger.log("\tInvoke: " + invoke, LoggerVerbosity.DEBUG);
            for (AnalysisMethod callee : invoke.getOriginalCallees()) {
                logger.log("\t\tCallee: " + callee, LoggerVerbosity.DEBUG);
            }
        }
    }

    public static void printLabelledGraph(StructuredGraph graph, AnalysisMethod analysisMethod, AbstractStateMap<?> abstractStateMap) {
        AbstractInterpretationLogger logger = AbstractInterpretationLogger.getInstance();
        logger.log("Computed post conditions of method: " + analysisMethod, LoggerVerbosity.INFO);
        for (Node node : graph.getNodes()) {
            AbstractState<?> abstractState = abstractStateMap.getState(node);
            logger.log(node + " -> " + abstractState.getPostCondition(), LoggerVerbosity.INFO);
        }
    }
}
