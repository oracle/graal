package com.oracle.svm.hosted.analysis.ai.checker.annotator;

import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.BeginNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.StartNode;
import jdk.graal.compiler.nodes.StructuredGraph;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Simple graph rewriter helpers driven by facts from checkers.
 */
public final class GraphRewrite {

    public static void foldIfTrue(StructuredGraph graph, IfNode ifNode) {
        var logger = AbstractInterpretationLogger.getInstance();
        try {
            BeginNode begin = graph.add(new BeginNode());
            ifNode.replaceAtPredecessor(begin);
            FixedNode trueNext = ifNode.trueSuccessor();
            begin.setNext(trueNext);
            ifNode.clearSuccessors();
            ifNode.safeDelete();
            logger.log("[REWRITE] Folded If to true branch: " + ifNode, LoggerVerbosity.INFO);
        } catch (Throwable t) {
            logger.log("[REWRITE] Failed to fold If: " + t.getMessage(), LoggerVerbosity.INFO);
        }
    }

    public static void foldIfFalse(StructuredGraph graph, IfNode ifNode) {
        var logger = AbstractInterpretationLogger.getInstance();
        try {
            BeginNode begin = graph.add(new BeginNode());
            ifNode.replaceAtPredecessor(begin);
            FixedNode falseNext = ifNode.falseSuccessor();
            begin.setNext(falseNext);
            ifNode.clearSuccessors();
            ifNode.safeDelete();
            logger.log("[REWRITE] Folded If to false branch: " + ifNode, LoggerVerbosity.INFO);
        } catch (Throwable t) {
            logger.log("[REWRITE] Failed to fold If (false): " + t.getMessage(), LoggerVerbosity.INFO);
        }
    }

    public static void markBoundsSafe(Node n) {
        AbstractInterpretationLogger.getInstance().log("[REWRITE] Bounds proven safe for: " + n, LoggerVerbosity.INFO);
    }

    /**
     * Remove unreachable fixed nodes by traversing successors from Start.
     */
    public static void sweepUnreachableFixed(StructuredGraph graph) {
        var logger = AbstractInterpretationLogger.getInstance();
        Set<Node> reachable = new HashSet<>();
        ArrayDeque<Node> work = new ArrayDeque<>();
        StartNode start = graph.start();
        if (start == null) return;
        work.add(start);
        reachable.add(start);
        while (!work.isEmpty()) {
            Node cur = work.removeFirst();
            for (Node s : cur.successors()) {
                if (s != null && reachable.add(s)) {
                    work.addLast(s);
                }
            }
        }
        // Collect unreachable fixed nodes (excluding Start)
        var it = graph.getNodes().iterator();
        while (it.hasNext()) {
            Node n = it.next();
            if (n instanceof FixedNode && !(n instanceof StartNode)) {
                if (!reachable.contains(n)) {
                    try {
                        n.safeDelete();
                        logger.log("[REWRITE] Deleted unreachable fixed node: " + n, LoggerVerbosity.INFO);
                    } catch (Throwable t) {
                        logger.log("[REWRITE] Failed to delete unreachable node: " + n + " due to " + t.getMessage(), LoggerVerbosity.DEBUG);
                    }
                }
            }
        }
    }
}
