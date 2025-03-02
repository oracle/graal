package com.oracle.svm.hosted.analysis.ai.util;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.InvokeInfo;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraphBuilder;
import jdk.graal.compiler.nodes.cfg.HIRBlock;

public final class GraphUtils {

    public static ControlFlowGraph getGraph(AnalysisMethod root, DebugContext debug) {
        StructuredGraph structuredGraph = root.decodeAnalyzedGraph(debug, null);
        if (structuredGraph == null) {
            throw AnalysisError.interruptAnalysis("unable to decode analyzed graph");
        }
        return new ControlFlowGraphBuilder(structuredGraph).build();
    }

    public static AnalysisMethod getInvokeAnalysisMethod(AnalysisMethod root, Invoke invoke) {
        for (InvokeInfo invokeInfo : root.getInvokes()) {
            // TODO: try comparing bci, && invokeInfo.getPosition().getBCI() == invoke.bci()
            if (invoke.getTargetMethod().equals(invokeInfo.getTargetMethod())) {
                return invokeInfo.getTargetMethod();
            }
        }
        throw AnalysisError.interruptAnalysis("Invoke not found in analysisMethod");
    }

    public static void printGraph(AnalysisMethod root, ControlFlowGraph graph) {
        if (graph == null) {
            throw AnalysisError.interruptAnalysis("ControlFlowGraph is null");
        }

        AbstractInterpretationLogger logger = AbstractInterpretationLogger.getInstance();
        logger.logToFile("Graph of AnalysisMethod: " + root);
        for (HIRBlock block : graph.getBlocks()) {
            logger.logToFile(block.toString());

            for (Node node : block.getNodes()) {
                logger.logToFile(node.toString());
                logger.logToFile("\tSuccessors: ");
                for (Node successor : node.successors()) {
                    logger.logToFile("\t\t" + successor.toString());
                }
                logger.logToFile("\tInputs: ");
                for (Node input : node.inputs()) {
                    logger.logToFile("\t\t" + input.toString());
                }
            }
        }

        logger.logToFile("The Invokes of the AnalysisMethod: " + root);
        for (InvokeInfo invoke : root.getInvokes()) {
            logger.logToFile("\tInvoke: " + invoke);
            for (AnalysisMethod callee : invoke.getOriginalCallees()) {
                logger.logToFile("\t\tCallee: " + callee);
            }
        }
    }

    public static void printInferredGraph(StructuredGraph graph, AnalysisMethod analysisMethod, AbstractStateMap<?> abstractStateMap) {
        AbstractInterpretationLogger logger = AbstractInterpretationLogger.getInstance();
        logger.logToFile("Computed postConditions of AnalysisMethod: " + analysisMethod);
        for (Node node : graph.getNodes()) {
            AbstractState<?> abstractState = abstractStateMap.getState(node);
            logger.logToFile(node + " -> " + abstractState.getPostCondition() + System.lineSeparator());
        }
    }
}
