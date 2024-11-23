package com.oracle.svm.hosted.analysis.ai.transfer;

import com.oracle.svm.hosted.analysis.ai.domain.IntInterval;
import com.oracle.svm.hosted.analysis.ai.fixpoint.Environment;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.BinaryArithmeticNode;
import jdk.graal.compiler.nodes.calc.IntegerEqualsNode;
import jdk.graal.compiler.nodes.calc.IntegerLessThanNode;
import jdk.graal.compiler.nodes.calc.MulNode;
import jdk.graal.compiler.nodes.calc.SubNode;
import jdk.graal.compiler.nodes.PhiNode;

/**
 * Basic implementation of a transfer function for integer interval domains.
 */
public record IntIntervalTransferFunction() implements TransferFunction<IntInterval> {

    @Override
    public IntInterval computePostCondition(Node node, Environment<IntInterval> environment) {
        if (node instanceof ConstantNode constantNode) {
            if (constantNode.isNullConstant()) {
                return new IntInterval();
            } else {
                return new IntInterval(constantNode.asJavaConstant().asInt());
            }
        }

        if (node instanceof BinaryArithmeticNode<?>) {
            return evaluateBinaryArithmeticNode((BinaryArithmeticNode<?>) node, environment);
        }

        if (node instanceof IntegerLessThanNode lessThanNode) {
            IntInterval conditionInterval = computePostCondition(lessThanNode.getY(), environment);
            return new IntInterval(Integer.MIN_VALUE, conditionInterval.getLowerBound() - 1);
        }

        if (node instanceof IntegerEqualsNode integerEqualsNode) {
            return computePostCondition(integerEqualsNode.getY(), environment);
        }

        var post = environment.getPostCondition(node);
        if (node instanceof IfNode ifNode) {
            var condition = computePostCondition(ifNode.condition(), environment);
            var trueBranch = ifNode.trueSuccessor();
            var falseBranch = ifNode.falseSuccessor();
            environment.getPostCondition(trueBranch).widenWith(condition.meet(post));

            var negatedCondition = IntInterval.getInversed(condition);
            environment.getPostCondition(falseBranch).widenWith(negatedCondition.meet(post));
        } else if (node instanceof PhiNode phiNode) {
            for (Node input : phiNode.values()) {
                post.joinWith(computePostCondition(input, environment));
            }
        } else if (node instanceof AbstractMergeNode || node instanceof FrameState) {
            for (Node input : node.inputs()) {
                post.joinWith(computePostCondition(input, environment));
            }
        }

        return post;
    }

    private IntInterval evaluateBinaryArithmeticNode(BinaryArithmeticNode<?> node, Environment<IntInterval> environment) {
        var result = environment.getPostCondition(node);
        var x = node.getX() instanceof PhiNode ? environment.getPostCondition(node.getX()) : computePostCondition(node.getX(), environment);
        var y = node.getY() instanceof PhiNode ? environment.getPostCondition(node.getY()) : computePostCondition(node.getY(), environment);

        return switch (node) {
            case AddNode addNode -> x.add(y);
            case SubNode subNode -> x.sub(y);
            case MulNode mulNode -> x.mul(y);
            default -> result;
        };
    }
}