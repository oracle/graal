package com.oracle.svm.hosted.analysis.ai.checker.facts;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.nodes.NodeView;
import jdk.vm.ci.meta.JavaKind;

/**
 * A fact that records a constant value for a particular node.
 */
public record ConstantFact(Node node, long value) implements Fact {

    @Override
    public FactKind kind() {
        return FactKind.CONSTANT;
    }

    @Override
    public String describe() {
        return node + " has constant value: " + value;
    }

    /**
     * @return JavaKind of the node if it is a {@link ValueNode}, otherwise null.
     */
    public JavaKind kindOfValueNode() {
        if (node instanceof ValueNode vn) {
            return vn.getStackKind();
        }
        return null;
    }

    /**
     * Builds an exact integer {@link Stamp} [value, value] compatible with the node, or null if not integer.
     */
    public Stamp exactIntegerStampOrNull() {
        if (!(node instanceof ValueNode vn)) {
            return null;
        }
        var stamp = vn.stamp(NodeView.DEFAULT);
        if (stamp instanceof IntegerStamp is) {
            JavaKind kind = vn.getStackKind();
            return StampFactory.forInteger(kind, value, value);
        }
        return null;
    }
}
