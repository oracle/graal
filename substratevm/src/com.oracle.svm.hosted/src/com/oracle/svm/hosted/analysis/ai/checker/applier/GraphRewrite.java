package com.oracle.svm.hosted.analysis.ai.checker.applier;

import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.phases.common.DeadCodeEliminationPhase;

/**
 * Simple graph rewriter helpers driven by facts from checkers.
 */
public final class GraphRewrite {

    public static void foldIfTrue(StructuredGraph graph, IfNode ifNode) {
        AbstractBeginNode falseSuccessor = ifNode.falseSuccessor();
        var logger = AbstractInterpretationLogger.getInstance();
        logger.log("[GraphRewrite] Folding IfNode to true branch: " + ifNode, LoggerVerbosity.CHECKER);
        graph.removeSplit(ifNode, ifNode.trueSuccessor());
        GraphUtil.killCFG(falseSuccessor);
    }

    public static void foldIfFalse(StructuredGraph graph, IfNode ifNode) {
        AbstractBeginNode trueSuccessor = ifNode.trueSuccessor();
        AbstractInterpretationLogger.getInstance().log("[GraphRewrite] Folding IfNode to false branch: " + ifNode, LoggerVerbosity.CHECKER);
        graph.removeSplit(ifNode, ifNode.falseSuccessor());
        GraphUtil.killCFG(trueSuccessor);
    }

    /**
     * Sweep unreachable nodes using Graal's DCE instead of custom floods to ensure consistency.
     */
    public static void sweepUnreachableFixed(StructuredGraph graph) {
        new DeadCodeEliminationPhase().run(graph);
    }
}
