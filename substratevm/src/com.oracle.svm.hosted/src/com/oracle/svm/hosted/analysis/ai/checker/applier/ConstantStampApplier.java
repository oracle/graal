package com.oracle.svm.hosted.analysis.ai.checker.applier;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.checker.core.ApplierResult;
import com.oracle.svm.hosted.analysis.ai.checker.core.FactAggregator;
import com.oracle.svm.hosted.analysis.ai.checker.core.facts.ConstantFact;
import com.oracle.svm.hosted.analysis.ai.checker.core.facts.Fact;
import com.oracle.svm.hosted.analysis.ai.checker.core.facts.FactKind;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;

import java.util.List;
import java.util.Set;

/**
 * Applies ConstantFact by tightening the stamp of the corresponding ValueNode
 * to an exact integer interval [v, v].
 */
public final class ConstantStampApplier extends BaseApplier {

    @Override
    public Set<FactKind> getApplicableFactKinds() {
        return Set.of(FactKind.CONSTANT);
    }

    @Override
    public String getDescription() {
        return "ConstantStamp";
    }

    @Override
    public boolean shouldApply() {
        return false;
    }

    @Override
    public ApplierResult apply(AnalysisMethod method, StructuredGraph graph, FactAggregator aggregator) {
        List<Fact> facts = aggregator.factsOfKind(FactKind.CONSTANT);
        if (facts.isEmpty()) {
            return ApplierResult.empty();
        }

        int tightened = 0;
        for (Fact f : facts) {
            if (!(f instanceof ConstantFact cf)) {
                continue;
            }

            Node node = cf.node();
            if (!(node instanceof ValueNode vn) || !vn.isAlive()) {
                continue;
            }

            Stamp exact = cf.exactIntegerStampOrNull();
            if (exact == null) {
                continue;
            }
            Stamp current = vn.stamp(NodeView.DEFAULT);
            Stamp improved = current.tryImproveWith(exact);
            if (improved != null && !improved.equals(current)) {
                tightened++;
                if (shouldApply()) {
                    vn.setStamp(improved);
                }
            }
        }

        return ApplierResult.constantsStamped(tightened);
    }
}
