package com.oracle.svm.hosted.analysis.ai.checker.optimize;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.analyzer.metadata.AnalysisContext;
import com.oracle.svm.hosted.analysis.ai.checker.core.FactAggregator;
import com.oracle.svm.hosted.analysis.ai.checker.core.facts.Fact;
import com.oracle.svm.hosted.analysis.ai.checker.core.facts.IndexSafetyFact;
import com.oracle.svm.hosted.analysis.ai.checker.core.facts.ConditionTruthFact;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.LoopExitNode;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.ValueProxy;
import jdk.graal.compiler.nodes.java.LoadIndexedNode;
import jdk.graal.compiler.nodes.java.StoreIndexedNode;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Applies graph rewrites based on aggregated facts in a fixed, safe order, directly on the live graph.
 */
public final class RewriterOrchestrator {

    public static void apply(AnalysisMethod method, StructuredGraph graph, FactAggregator aggregator) {
        var logger = AbstractInterpretationLogger.getInstance();
        eliminateBoundsChecks(aggregator, graph);
//        GraphRewrite.sweepUnreachableFixed(graph);
    }

    private static void foldBranches(StructuredGraph graph, FactAggregator aggregator) {
        for (Fact f : aggregator.factsOfKind("Condition")) {
            ConditionTruthFact cf = (ConditionTruthFact) f;
            IfNode ifn = cf.ifNode();
            if (isLikelyLoopGuard(ifn)) {
                continue;
            }
            if (cf.truth() == ConditionTruthFact.Truth.ALWAYS_TRUE) {
                GraphRewrite.foldIfTrue(graph, ifn);
            } else if (cf.truth() == ConditionTruthFact.Truth.ALWAYS_FALSE) {
                GraphRewrite.foldIfFalse(graph, ifn);
            }
        }
    }

    private static boolean isLikelyLoopGuard(IfNode ifn) {
        // Heuristic 1: the condition uses a phi at a LoopBegin
        ValueNode cond = ifn.condition();
        if (usesLoopHeaderPhi(cond)) {
            return true;
        }
        // Heuristic 2: one successor quickly reaches a LoopExit
        return successorReachesLoopExit(ifn.trueSuccessor()) || successorReachesLoopExit(ifn.falseSuccessor());
    }

    private static boolean usesLoopHeaderPhi(ValueNode v) {
        Set<ValueNode> seen = new HashSet<>();
        ArrayDeque<ValueNode> work = new ArrayDeque<>();
        work.add(v);
        while (!work.isEmpty()) {
            ValueNode cur = work.poll();
            if (!seen.add(cur)) continue;
            if (cur instanceof PhiNode phi) {
                AbstractMergeNode m = phi.merge();
                if (m instanceof LoopBeginNode) {
                    return true;
                }
            }
            if (cur instanceof ValueProxy vp) {
                work.add(vp.getOriginalNode());
            }
            for (var in : cur.inputs()) {
                if (in instanceof ValueNode vn) {
                    work.add(vn);
                }
            }
        }
        return false;
    }

    private static boolean successorReachesLoopExit(Node begin) {
        int steps = 0;
        Node cur = begin;
        Set<Node> seen = new HashSet<>();
        // FIXME: limit to 16 steps to avoid infinite loops, but this is a heuristic, think of a better way
        while (cur != null && steps < 16 && seen.add(cur)) {
            if (cur instanceof LoopExitNode) return true;
            var nexts = new ArrayDeque<FixedNode>();
            for (var s : cur.successors()) {
                if (s instanceof FixedNode fn) nexts.add(fn);
            }
            if (nexts.isEmpty()) break;
            cur = nexts.poll();
            steps++;
        }
        return false;
    }

    private static void eliminateBoundsChecks(FactAggregator aggregator, StructuredGraph graph) {
        List<Fact> idxFacts = aggregator.factsOfKind("IndexSafety");
        for (Fact f : idxFacts) {
            IndexSafetyFact isf = (IndexSafetyFact) f;
            var n = isf.getArrayAccess();
            if (!isf.isInBounds()) continue;
            if (n instanceof LoadIndexedNode || n instanceof StoreIndexedNode) {
                Node guardIf = findGuardingIf(n);
                if (guardIf == null) continue;
                if (!(guardIf instanceof IfNode ifn)) continue;
                GraphRewrite.foldIfTrue(graph, ifn);
            }
        }
    }

    private static Node findGuardingIf(Node n) {
        Set<Node> seen = new HashSet<>();
        ArrayDeque<Node> work = new ArrayDeque<>();
        work.add(n);
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
}
