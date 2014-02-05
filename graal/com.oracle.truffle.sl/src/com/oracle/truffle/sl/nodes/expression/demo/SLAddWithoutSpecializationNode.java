package com.oracle.truffle.sl.nodes.expression.demo;

import java.math.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.sl.nodes.*;
import com.oracle.truffle.sl.nodes.expression.*;

/**
 * This is an example how the add operation would be implemented without specializations and without
 * the Truffle DSL. Do not write such code in your language! See {@link SLAddNode} how the add
 * operation is implemented correctly.
 */
public class SLAddWithoutSpecializationNode extends SLExpressionNode {

    @Child private SLExpressionNode leftNode;
    @Child private SLExpressionNode rightNode;

    public SLAddWithoutSpecializationNode(SLExpressionNode leftNode, SLExpressionNode rightNode) {
        this.leftNode = adoptChild(leftNode);
        this.rightNode = adoptChild(rightNode);
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        /* Evaluate the child nodes. */
        Object left = leftNode.executeGeneric(frame);
        Object right = rightNode.executeGeneric(frame);

        if (left instanceof Long && right instanceof Long) {
            /* Fast path of the arbitrary-precision arithmetic. We need to check for overflows */
            try {
                return ExactMath.addExact((Long) left, (Long) right);
            } catch (ArithmeticException ex) {
                /* Fall through to BigInteger case. */
            }
        }

        /* Implicit type conversions. */
        if (left instanceof Long) {
            left = BigInteger.valueOf((Long) left);
        }
        if (right instanceof Long) {
            right = BigInteger.valueOf((Long) right);
        }
        if (left instanceof BigInteger && right instanceof BigInteger) {
            /* Slow path of the arbitrary-precision arithmetic. */
            return ((BigInteger) left).add((BigInteger) right);
        }

        /* String concatenation if either the left or the right operand is a String. */
        if (left instanceof String || right instanceof String) {
            return left.toString() + right.toString();
        }

        /* Type error. */
        throw new UnsupportedSpecializationException(this, new Node[]{leftNode, rightNode}, new Object[]{left, right});
    }
}
