package com.oracle.svm.hosted.analysis.ai.checker.applier;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.checker.core.FactAggregator;
import com.oracle.svm.hosted.analysis.ai.checker.core.facts.ConstantFact;
import com.oracle.svm.hosted.analysis.ai.checker.core.facts.Fact;
import com.oracle.svm.hosted.analysis.ai.checker.core.facts.FactKind;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;
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
public final class ConstantStampApplier implements FactApplier {

    @Override
    public Set<FactKind> getApplicableFactKinds() {
        return Set.of(FactKind.CONSTANT);
    }

    @Override
    public String getDescription() {
        return "ConstantStamp";
    }

    @Override
    public void apply(AnalysisMethod method, StructuredGraph graph, FactAggregator aggregator) {
        var logger = AbstractInterpretationLogger.getInstance();
        List<Fact> facts = aggregator.factsOfKind(FactKind.CONSTANT);
        if (facts.isEmpty()) {
            return;
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
                vn.setStamp(improved);
                tightened++;
            }
        }

        if (tightened > 0) {
            logger.log("[ConstantStamp] Tightened stamps: " + tightened, LoggerVerbosity.CHECKER);
        }
    }
}
