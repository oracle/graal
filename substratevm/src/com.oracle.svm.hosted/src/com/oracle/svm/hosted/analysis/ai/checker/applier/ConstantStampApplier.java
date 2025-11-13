package com.oracle.svm.hosted.analysis.ai.checker.applier;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.checker.core.FactAggregator;
import com.oracle.svm.hosted.analysis.ai.checker.core.facts.ConstantFact;
import com.oracle.svm.hosted.analysis.ai.checker.core.facts.Fact;
import com.oracle.svm.hosted.analysis.ai.checker.core.facts.FactKind;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.util.GraphUtil;

import java.util.List;
import java.util.Set;

/**
 * Applies ConstantFact by replacing usages with constants.
 * Creates constants with the proper stamp in the same graph and avoids producing null inputs.
 */
public final class ConstantStampApplier implements FactApplier {

    @Override
    public Set<FactKind> getApplicableFactKinds() {
        return Set.of(FactKind.CONSTANT);
    }

    @Override
    public String getDescription() {
        return "ConstantStamper";
    }

    @Override
    public void apply(AnalysisMethod method, StructuredGraph graph, FactAggregator aggregator) {
        var logger = AbstractInterpretationLogger.getInstance();
        List<Fact> facts = aggregator.factsOfKind(FactKind.CONSTANT);
        if (facts.isEmpty()) {
            return;
        }

        int replaced = 0;
        for (Fact f : facts) {
            if (!(f instanceof ConstantFact(Node node, long value))) {
                continue;
            }

            if (!(node instanceof ValueNode vn)) {
                continue;
            }

            if (!vn.isAlive()) {
                continue;
            }

            if (vn instanceof ConstantNode) {
                continue;
            }

            Stamp stamp = vn.stamp(NodeView.DEFAULT);
            if (!(stamp instanceof IntegerStamp intStamp)) {
                continue;
            }

            long loMask = intStamp.getBits() >= 32 ? 0xFFFF_FFFFL : ((1L << intStamp.getBits()) - 1L);
            int intVal = (int) value;
            ConstantNode cnode = ConstantNode.forIntegerStamp(stamp, intVal, graph);
            if (cnode.graph() == null) {
                graph.addOrUnique(cnode);
            }

            vn.replaceAtUsages(cnode);
            if (!(vn instanceof FixedNode) && vn.hasNoUsages() && vn.isAlive()) {
                GraphUtil.killWithUnusedFloatingInputs(vn);
            }

            replaced++;
        }

        if (replaced > 0) {
            logger.log("[ConstantPropagation] Replaced usages with constants: " + replaced, LoggerVerbosity.CHECKER);
        }
    }
}
