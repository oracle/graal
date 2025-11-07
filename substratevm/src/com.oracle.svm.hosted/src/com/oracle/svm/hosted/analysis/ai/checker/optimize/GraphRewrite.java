package com.oracle.svm.hosted.analysis.ai.checker.optimize;

import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.StartNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.graph.NodeFlood;
import jdk.graal.compiler.nodes.AbstractEndNode;
import jdk.graal.compiler.nodes.GuardNode;

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
     * Sweep unreachable fixed nodes by walking from StartNode and killing any fixed node not reached.
     */
    public static void sweepUnreachableFixed(StructuredGraph graph) {
        StartNode start = graph.start();
        if (start == null) {
            AbstractInterpretationLogger.getInstance().log("[GraphRewrite] No StartNode found; skip sweep.", LoggerVerbosity.CHECKER);
            return;
        }

        NodeFlood flood = graph.createNodeFlood();
        flood.add(start);

        Node.EdgeVisitor visitor = new Node.EdgeVisitor() {
            @Override
            public Node apply(Node n, Node succOrInput) {
                if (succOrInput != null && succOrInput.isAlive()) {
                    flood.add(succOrInput);
                }
                return succOrInput;
            }
        };

        for (Node cur : flood) {
            if (cur instanceof AbstractEndNode end) {
                flood.add(end.merge());
            } else {
                cur.applySuccessors(visitor);
                cur.applyInputs(visitor);
            }
        }

        boolean changed = false;
        for (GuardNode guard : graph.getNodes(GuardNode.TYPE)) {
            if (flood.isMarked(guard.getAnchor().asNode())) {
                flood.add(guard);
                changed = true;
            }
        }
        if (changed) {
            for (Node cur : flood) {
                if (cur instanceof AbstractEndNode end) {
                    flood.add(end.merge());
                } else {
                    cur.applySuccessors(visitor);
                    cur.applyInputs(visitor);
                }
            }
        }

        Node.EdgeVisitor removeUsageVisitor = new Node.EdgeVisitor() {
            @Override
            public Node apply(Node n, Node input) {
                if (input != null && input.isAlive() && flood.isMarked(input)) {
                    input.removeUsage(n);
                }
                return input;
            }
        };

        var snapshot = graph.getNodes().snapshot();
        for (Node node : snapshot) {
            if (!flood.isMarked(node)) {
                node.markDeleted();
                node.applyInputs(removeUsageVisitor);
                AbstractInterpretationLogger.getInstance().log("[GraphRewrite] Removed unreachable node: " + node, LoggerVerbosity.CHECKER);
            }
        }
    }
}
