package com.oracle.svm.hosted.analysis.ai.checker.applier;

import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.StructuredGraph;

/**
 * Simple graph rewriter helpers driven by facts from checkers.
 */
public final class GraphRewrite {

    public static void foldIfTrue(StructuredGraph graph, IfNode ifNode) {
        AbstractBeginNode falseSuccessor = ifNode.falseSuccessor();
        var logger = AbstractInterpretationLogger.getInstance();
        graph.removeSplitPropagate(ifNode, ifNode.trueSuccessor());
    }

    public static void foldIfFalse(StructuredGraph graph, IfNode ifNode) {
        AbstractBeginNode trueSuccessor = ifNode.trueSuccessor();
        AbstractInterpretationLogger.getInstance().log("[GraphRewrite] Folding IfNode to false branch: " + ifNode, LoggerVerbosity.CHECKER);
        graph.removeSplitPropagate(ifNode, ifNode.falseSuccessor());
    }
}
