package com.oracle.svm.hosted.analysis.ai.checker.applier;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.checker.core.FactAggregator;
import com.oracle.svm.hosted.analysis.ai.checker.core.facts.Fact;
import com.oracle.svm.hosted.analysis.ai.checker.core.facts.FactKind;
import com.oracle.svm.hosted.analysis.ai.checker.core.facts.IndexSafetyFact;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.java.LoadIndexedNode;
import jdk.graal.compiler.nodes.java.StoreIndexedNode;
import jdk.graal.compiler.nodes.calc.IntegerBelowNode;
import jdk.graal.compiler.nodes.calc.IntegerLessThanNode;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.phases.common.DeadCodeEliminationPhase;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Applies IndexSafetyFact instances by folding proven-safe bounds checks.
 */
public final class BoundsCheckEliminatorApplier implements FactApplier {

    @Override
    public Set<FactKind> getApplicableFactKinds() {
        return Set.of(FactKind.BOUNDS_SAFETY);
    }

    @Override
    public String getDescription() {
        return "BoundsCheckEliminator";
    }

    @Override
    public void apply(AnalysisMethod method, StructuredGraph graph, FactAggregator aggregator) {
        var logger = AbstractInterpretationLogger.getInstance();
        List<Fact> idxFacts = aggregator.factsOfKind(FactKind.BOUNDS_SAFETY);
        if (idxFacts.isEmpty()) {
            logger.log("[BoundsCheckEliminator] No index safety facts.", LoggerVerbosity.CHECKER);
            return;
        }
        for (Fact f : idxFacts.reversed()) {
            IndexSafetyFact isf = (IndexSafetyFact) f;
            if (!isf.isInBounds()) continue;
            Node access = isf.getArrayAccess();
            if (!(access instanceof LoadIndexedNode || access instanceof StoreIndexedNode)) continue;
            Node guardIf = findGuardingIf(access);
            if (!(guardIf instanceof IfNode ifn)) continue;
            logger.log("[GraphRewrite] Folding IfNode to true branch: " + ifn, LoggerVerbosity.CHECKER);
            graph.removeSplitPropagate(ifn, ifn.trueSuccessor());
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
