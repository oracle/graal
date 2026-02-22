package com.oracle.svm.hosted.analysis.ai.checker.appliers;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.checker.core.ApplierResult;
import com.oracle.svm.hosted.analysis.ai.checker.core.FactAggregator;
import com.oracle.svm.hosted.analysis.ai.checker.facts.ConstantFact;
import com.oracle.svm.hosted.analysis.ai.checker.facts.Fact;
import com.oracle.svm.hosted.analysis.ai.checker.facts.FactKind;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.InvokeNode;
import jdk.graal.compiler.nodes.InvokeWithExceptionNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.vm.ci.meta.JavaKind;

import java.util.List;
import java.util.Set;

/**
 * This is an applier that folds invokes to the constant values that they return
 */
public final class InvokeConstantFoldingApplier extends BaseApplier {

    @Override
    public Set<FactKind> getApplicableFactKinds() {
        return Set.of(FactKind.CONSTANT);
    }

    @Override
    public String getDescription() {
        return "InvokeConstantFolding";
    }

    @Override
    public ApplierResult apply(AnalysisMethod method, StructuredGraph graph, FactAggregator aggregator) {
        List<Fact> facts = aggregator.factsOfKind(FactKind.CONSTANT);
        if (facts.isEmpty()) {
            return ApplierResult.empty();
        }

        int folded = 0;
        for (Fact f : facts) {
            if (!(f instanceof ConstantFact(Node node, long value))) {
                continue;
            }

            if (!(node instanceof Invoke invoke) || !invoke.isAlive()) {
                continue;
            }

            Node invokeAsNode = invoke.asNode();
            if (invokeAsNode == null) {
                continue;
            }

            JavaKind retKind = JavaKind.Int;
            if (invoke.callTarget() != null && invoke.callTarget().targetMethod() != null) {
                var sig = invoke.callTarget().targetMethod().getSignature();
                if (sig != null) {
                    retKind = sig.getReturnKind();
                }
            }

            if (!retKind.isNumericInteger()) {
                continue;
            }

            if (!fitsIntoKind(retKind, value)) {
                continue;
            }

            folded++;
            if (shouldApply()) {
                ConstantNode constantNode = createIntegerConstant(graph, retKind, value);
                invokeAsNode.replaceAtUsages(constantNode);
                if (invokeAsNode instanceof InvokeNode invokeNode) {
                    FixedNode next = invokeNode.next();
                    invokeNode.replaceAtPredecessor(next);
                    graph.removeFixed(invokeNode);
                } else if (invokeAsNode instanceof InvokeWithExceptionNode invokeWithException) {
                    graph.removeSplitPropagate(invokeWithException, invokeWithException.getPrimarySuccessor());
                }
            }
        }

        return ApplierResult.builder()
                .appliedFacts(folded)
                .invokesReplacedWithConstants(folded)
                .build();
    }

    private static ConstantNode createIntegerConstant(StructuredGraph graph, JavaKind kind, long value) {
        ConstantNode cn = switch (kind) {
            case Byte -> ConstantNode.forInt((byte) value, graph);
            case Short -> ConstantNode.forInt((short) value, graph);
            case Char -> ConstantNode.forInt((char) value, graph);
            case Long -> ConstantNode.forLong(value, graph);
            default -> ConstantNode.forInt((int) value, graph);
        };
        if (cn.graph() == null) {
            graph.add(cn);
        }
        return cn;
    }

    private static boolean fitsIntoKind(JavaKind kind, long v) {
        return switch (kind) {
            case Byte -> v >= Byte.MIN_VALUE && v <= Byte.MAX_VALUE;
            case Short -> v >= Short.MIN_VALUE && v <= Short.MAX_VALUE;
            case Char -> v >= Character.MIN_VALUE && v <= Character.MAX_VALUE;
            case Int -> v >= Integer.MIN_VALUE && v <= Integer.MAX_VALUE;
            case Long -> true;
            default -> false;
        };
    }
}
