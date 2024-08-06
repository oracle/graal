/*
 * Copyright (c) 2009, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.calc;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_1;

import jdk.graal.compiler.core.common.type.ArithmeticOpTable;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.UnaryOp;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.UnaryOp.Abs;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.ArithmeticLIRLowerable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.code.CodeUtil;

/**
 * Absolute value.
 */
@NodeInfo(cycles = CYCLES_2, size = SIZE_1)
public final class AbsNode extends UnaryArithmeticNode<Abs> implements ArithmeticLIRLowerable, NarrowableArithmeticNode {
    public static final NodeClass<AbsNode> TYPE = NodeClass.create(AbsNode.class);

    public AbsNode(ValueNode x) {
        super(TYPE, BinaryArithmeticNode.getArithmeticOpTable(x).getAbs(), x);
    }

    public static ValueNode create(ValueNode value, NodeView view) {
        ValueNode synonym = findSynonym(value, view);
        if (synonym != null) {
            return synonym;
        }
        return new AbsNode(value);
    }

    protected static ValueNode findSynonym(ValueNode forValue, NodeView view) {
        ArithmeticOpTable.UnaryOp<Abs> absOp = ArithmeticOpTable.forStamp(forValue.stamp(view)).getAbs();
        ValueNode synonym = UnaryArithmeticNode.findSynonym(forValue, absOp);
        if (synonym != null) {
            return synonym;
        }
        if (forValue instanceof AbsNode) {
            return forValue;
        }
        if (forValue.stamp(view) instanceof IntegerStamp && ((IntegerStamp) forValue.stamp(view)).isPositive()) {
            // The value always positive so nothing to do
            return forValue;
        }
        // abs(-x) => abs(x)
        if (forValue instanceof NegateNode) {
            NegateNode negate = (NegateNode) forValue;
            return AbsNode.create(negate.getValue(), view);
        }
        return null;
    }

    @Override
    protected UnaryOp<Abs> getOp(ArithmeticOpTable table) {
        return table.getAbs();
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        ValueNode ret = super.canonical(tool, forValue);
        if (ret != this) {
            return ret;
        }
        ValueNode synonym = findSynonym(forValue, NodeView.from(tool));
        if (synonym != null) {
            return synonym;
        }
        return this;
    }

    @Override
    public boolean isNarrowable(int resultBits) {
        if (NarrowableArithmeticNode.super.isNarrowable(resultBits)) {
            /*
             * Abs(Narrow(x)) is only equivalent to Narrow(Abs(x)) if the cut off bits are all equal
             * to the sign bit of the input. That's equivalent to the condition that the input is in
             * the signed range of the narrow type.
             */
            IntegerStamp inputStamp = (IntegerStamp) getValue().stamp(NodeView.DEFAULT);
            return CodeUtil.minValue(resultBits) <= inputStamp.lowerBound() && inputStamp.upperBound() <= CodeUtil.maxValue(resultBits);
        } else {
            return false;
        }
    }

    @Override
    public void generate(NodeLIRBuilderTool nodeValueMap, ArithmeticLIRGeneratorTool gen) {
        nodeValueMap.setResult(this, gen.emitMathAbs(nodeValueMap.operand(getValue())));
    }
}
