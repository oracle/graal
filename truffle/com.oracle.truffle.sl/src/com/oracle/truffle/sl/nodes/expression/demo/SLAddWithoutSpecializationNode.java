/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
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
        super(null);
        this.leftNode = leftNode;
        this.rightNode = rightNode;
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
