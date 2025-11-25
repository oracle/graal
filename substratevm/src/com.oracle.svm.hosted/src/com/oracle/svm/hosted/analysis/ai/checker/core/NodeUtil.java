package com.oracle.svm.hosted.analysis.ai.checker.core;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.extended.BytecodeExceptionNode;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

public class NodeUtil {
    /**
     * Used to find the nearest enclosing {@link IfNode} of a given node
     */
    public static IfNode findGuardingIf(Node node) {
        Set<Node> seen = new HashSet<>();
        ArrayDeque<Node> work = new ArrayDeque<>();
        work.add(node);
        while (!work.isEmpty()) {
            Node cur = work.poll();
            if (!seen.add(cur)) continue;
            for (var pred : cur.cfgPredecessors()) {
                if (pred instanceof IfNode ifn) {
                    return ifn;
                }
                work.add(pred);
            }
        }
        return null;
    }

    // FIXME: this probably needs a better heuristic
    public static boolean leadsToByteCodeException(IfNode guardingIf) {
        Node falseBegin = guardingIf.falseSuccessor();
        if (falseBegin == null) return false;

        var successors = falseBegin.successors();
        if (successors.count() != 1) {
            return false;
        }
        return successors.first() instanceof BytecodeExceptionNode;
    }
}
