package com.oracle.svm.hosted.analysis.ai.checker.applier;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.checker.core.FactAggregator;
import com.oracle.svm.hosted.analysis.ai.checker.core.facts.ConstantFact;
import com.oracle.svm.hosted.analysis.ai.checker.core.facts.Fact;
import com.oracle.svm.hosted.analysis.ai.checker.core.facts.FactKind;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.graph.Node;

import java.util.List;
import java.util.Set;

/**
 * Applies ConstantFact by replacing usages with ConstantNodes when beneficial.
 * Conservative and idempotent.
 */
public final class ConstantPropagationApplier implements FactApplier{

    @Override
    public Set<FactKind> getApplicableFactKinds() {
        return Set.of(FactKind.CONSTANT);
    }

    @Override
    public String getDescription() {
        return "ConstantPropagation";
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
            if (!node.isAlive()) {
                continue;
            }
            if (node instanceof ConstantNode) {
                continue; // already constant
            }
            // Narrow long to int for Java int constants (interval analysis used numeric ints)
            int intVal = (int) value;
            ConstantNode cnode = ConstantNode.forInt(intVal); // graph association handled by insertion via replaceAtUsages
            node.replaceAtUsages(cnode);
            replaced++;
        }
        if (replaced > 0) {
            logger.log("[ConstantPropagation] Replaced usages with constants: " + replaced, LoggerVerbosity.CHECKER);
        }
    }
}
