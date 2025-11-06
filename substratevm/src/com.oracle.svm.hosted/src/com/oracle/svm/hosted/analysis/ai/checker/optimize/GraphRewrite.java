package com.oracle.svm.hosted.analysis.ai.checker.optimize;

import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.StartNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.util.GraphUtil;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Simple graph rewriter helpers driven by facts from checkers.
 */
public final class GraphRewrite {

    public static void foldIfTrue(StructuredGraph graph, IfNode ifNode) {
        AbstractBeginNode falseSuccessor = ifNode.falseSuccessor();
        graph.removeSplit(ifNode, ifNode.trueSuccessor());
        GraphUtil.killCFG(falseSuccessor);
    }

    public static void foldIfFalse(StructuredGraph graph, IfNode ifNode) {
        AbstractBeginNode trueSuccessor = ifNode.trueSuccessor();
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
        // Mark reachable fixed nodes
        Set<FixedNode> reachable = new HashSet<>();
        ArrayDeque<FixedNode> work = new ArrayDeque<>();
        work.add(start);
        while (!work.isEmpty()) {
            FixedNode cur = work.poll();
            if (!reachable.add(cur)) continue;
            for (var s : cur.successors()) {
                if (s instanceof FixedNode fn) {
                    work.add(fn);
                }
            }
        }
        // Kill unreachable fixed nodes
        var snapshot = graph.getNodes().filter(FixedNode.class).snapshot();
        for (FixedNode fn : snapshot) {
            if (!reachable.contains(fn)) {
                GraphUtil.killCFG(fn);
            }
        }
    }
}
