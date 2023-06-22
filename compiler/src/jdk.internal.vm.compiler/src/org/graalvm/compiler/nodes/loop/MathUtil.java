/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package org.graalvm.compiler.nodes.loop;

import java.util.function.BiFunction;

import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.BinaryArithmeticNode;
import org.graalvm.compiler.nodes.calc.FixedBinaryNode;
import org.graalvm.compiler.nodes.calc.MulNode;
import org.graalvm.compiler.nodes.calc.SignedDivNode;
import org.graalvm.compiler.nodes.calc.SubNode;
import org.graalvm.compiler.nodes.calc.UnsignedDivNode;
import org.graalvm.compiler.nodes.extended.GuardingNode;

/**
 * Utility methods to perform integer math with some obvious constant folding first.
 */
public class MathUtil {
    private static boolean isConstantOne(ValueNode v1) {
        return v1.isConstant() && v1.stamp(NodeView.DEFAULT) instanceof IntegerStamp && v1.asJavaConstant().asLong() == 1;
    }

    private static boolean isConstantZero(ValueNode v1) {
        return v1.isConstant() && v1.stamp(NodeView.DEFAULT) instanceof IntegerStamp && v1.asJavaConstant().asLong() == 0;
    }

    public static ValueNode add(StructuredGraph graph, ValueNode v1, ValueNode v2) {
        return add(graph, v1, v2, true);
    }

    public static ValueNode add(StructuredGraph graph, ValueNode v1, ValueNode v2, boolean gvn) {
        if (isConstantZero(v1)) {
            return v2;
        }
        if (isConstantZero(v2)) {
            return v1;
        }
        if (gvn) {
            return BinaryArithmeticNode.add(graph, v1, v2, NodeView.DEFAULT);
        } else {
            return graph.addWithoutUniqueWithInputs(new AddNode(v1, v2));
        }
    }

    public static ValueNode mul(StructuredGraph graph, ValueNode v1, ValueNode v2) {
        return mul(graph, v1, v2, true);
    }

    public static ValueNode mul(StructuredGraph graph, ValueNode v1, ValueNode v2, boolean gvn) {
        if (isConstantOne(v1)) {
            return v2;
        }
        if (isConstantOne(v2)) {
            return v1;
        }
        if (gvn) {
            return BinaryArithmeticNode.mul(graph, v1, v2, NodeView.DEFAULT);
        } else {
            return graph.addWithoutUniqueWithInputs(new MulNode(v1, v2));
        }
    }

    public static ValueNode sub(StructuredGraph graph, ValueNode v1, ValueNode v2) {
        return sub(graph, v1, v2, true);
    }

    public static ValueNode sub(StructuredGraph graph, ValueNode v1, ValueNode v2, boolean gvn) {
        if (isConstantZero(v2)) {
            return v1;
        }
        if (gvn) {
            return BinaryArithmeticNode.sub(graph, v1, v2, NodeView.DEFAULT);
        } else {
            return graph.addWithoutUniqueWithInputs(new SubNode(v1, v2));
        }
    }

    public static ValueNode divBefore(StructuredGraph graph, FixedNode before, ValueNode dividend, ValueNode divisor, GuardingNode zeroCheck) {
        return fixedDivBefore(graph, before, dividend, divisor, (dend, sor) -> SignedDivNode.create(dend, sor, zeroCheck, NodeView.DEFAULT));
    }

    public static ValueNode unsignedDivBefore(StructuredGraph graph, FixedNode before, ValueNode dividend, ValueNode divisor, GuardingNode zeroCheck) {
        return fixedDivBefore(graph, before, dividend, divisor, (dend, sor) -> UnsignedDivNode.create(dend, sor, zeroCheck, NodeView.DEFAULT));
    }

    private static ValueNode fixedDivBefore(StructuredGraph graph, FixedNode before, ValueNode dividend, ValueNode divisor, BiFunction<ValueNode, ValueNode, ValueNode> createDiv) {
        if (isConstantOne(divisor)) {
            return dividend;
        }
        ValueNode div = createDiv.apply(dividend, divisor);
        if (div instanceof FixedBinaryNode) {
            FixedBinaryNode fixedDiv = (FixedBinaryNode) div;
            if (before.predecessor() instanceof FixedBinaryNode) {
                FixedBinaryNode binaryPredecessor = (FixedBinaryNode) before.predecessor();
                if (fixedDiv.dataFlowEquals(binaryPredecessor)) {
                    if (fixedDiv.isAlive()) {
                        fixedDiv.safeDelete();
                    }
                    return binaryPredecessor;
                }
            }
            graph.addBeforeFixed(before, graph.addOrUniqueWithInputs(fixedDiv));
        }
        return div;
    }
}
