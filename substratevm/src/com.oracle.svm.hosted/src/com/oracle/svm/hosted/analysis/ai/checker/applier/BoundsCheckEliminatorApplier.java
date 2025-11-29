package com.oracle.svm.hosted.analysis.ai.checker.applier;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.checker.core.ApplierResult;
import com.oracle.svm.hosted.analysis.ai.checker.core.FactAggregator;
import com.oracle.svm.hosted.analysis.ai.checker.core.NodeUtil;
import com.oracle.svm.hosted.analysis.ai.checker.core.facts.Fact;
import com.oracle.svm.hosted.analysis.ai.checker.core.facts.FactKind;
import com.oracle.svm.hosted.analysis.ai.checker.core.facts.SafeBoundsAccessFact;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.calc.IntegerBelowNode;
import jdk.graal.compiler.nodes.calc.IntegerLessThanNode;
import jdk.graal.compiler.nodes.java.LoadIndexedNode;
import jdk.graal.compiler.nodes.java.StoreIndexedNode;

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
    public ApplierResult apply(AnalysisMethod method, StructuredGraph graph, FactAggregator aggregator) {
        List<Fact> idxFacts = aggregator.factsOfKind(FactKind.BOUNDS_SAFETY);
        if (idxFacts.isEmpty()) {
            return ApplierResult.empty();
        }

        int folded = 0;
        for (Fact f : idxFacts.reversed()) {
            SafeBoundsAccessFact isf = (SafeBoundsAccessFact) f;
            if (!isf.isInBounds()) continue;
            Node access = isf.getArrayAccess();
            if (!(access instanceof LoadIndexedNode || access instanceof StoreIndexedNode)) continue;

            IfNode guardIf = NodeUtil.findGuardingIf(access);
            if (guardIf == null) {
                continue;
            }

            if (guardIf.condition() instanceof IntegerLessThanNode || guardIf.condition() instanceof IntegerBelowNode) {
                graph.removeSplitPropagate(guardIf, guardIf.trueSuccessor());
                folded++;
            }
        }
        return ApplierResult.boundsEliminated(folded);
    }
}
