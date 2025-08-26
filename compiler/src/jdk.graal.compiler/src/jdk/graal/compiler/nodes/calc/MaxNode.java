/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Arm Limited. All rights reserved.
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

import jdk.graal.compiler.core.common.NumUtil.Signedness;
import jdk.graal.compiler.core.common.calc.Condition;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.BinaryOp;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.BinaryOp.Max;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.LoweringProvider;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;

@NodeInfo(shortName = "Max")
public class MaxNode extends MinMaxNode<Max> {

    public static final NodeClass<MaxNode> TYPE = NodeClass.create(MaxNode.class);

    protected MaxNode(ValueNode x, ValueNode y) {
        super(TYPE, getArithmeticOpTable(x).getMax(), x, y);
    }

    @Override
    protected BinaryOp<Max> getOp(ArithmeticOpTable table) {
        return table.getMax();
    }

    public static ValueNode create(ValueNode x, ValueNode y, NodeView view) {
        BinaryOp<Max> op = ArithmeticOpTable.forStamp(x.stamp(view)).getMax();
        Stamp stamp = op.foldStamp(x.stamp(view), y.stamp(view));
        ConstantNode tryConstantFold = tryConstantFold(op, x, y, stamp, view);
        if (tryConstantFold != null) {
            return tryConstantFold;
        }
        return new MaxNode(x, y).maybeCommuteInputs();
    }

    @Override
    public ValueNode asConditional(LoweringProvider lowerer) {
        if (!(stamp(NodeView.DEFAULT).isIntegerStamp())) {
            return null;
        }
        LogicNode condition = IntegerLessThanNode.create(maybeExtendForCompare(getX(), lowerer, Signedness.SIGNED), maybeExtendForCompare(getY(), lowerer, Signedness.SIGNED), NodeView.DEFAULT);
        return ConditionalNode.create(condition, getY(), getX(), NodeView.DEFAULT);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        ValueNode ret = super.canonical(tool, forX, forY);
        if (ret != this) {
            return ret;
        }
        if (stamp(NodeView.DEFAULT) instanceof IntegerStamp) {
            IntegerStamp stampX = (IntegerStamp) forX.stamp(NodeView.from(tool));
            IntegerStamp stampY = (IntegerStamp) forY.stamp(NodeView.from(tool));
            if (IntegerStamp.sameSign(stampX, stampY)) {
                return UnsignedMaxNode.create(forX, forY, NodeView.from(tool));
            }
        }
        return this;
    }

    @Override
    public Signedness signedness() {
        return Signedness.SIGNED;
    }

    @Override
    protected Condition conditionCodeForEqualsUsage(ValueNode other) {
        if (other == getX()) {
            // max(x, y) == x --> x >= y
            return Condition.GE;
        } else if (other == getY()) {
            // max(x, y) == y --> x <= y
            return Condition.LE;
        } else {
            return null;
        }
    }

    /**
     * Returns {@code true} if the given architecture has support for emitting a simple branchless
     * instruction sequence implementing a max operation on floating-point values.
     */
    @SuppressWarnings("unlikely-arg-type")
    public static boolean floatingPointSupportAvailable(Architecture arch) {
        return switch (arch) {
            case AMD64 amd64 -> amd64.getFeatures().contains(AMD64.CPUFeature.AVX);
            case AArch64 aarch64 -> true;
            default -> false;
        };
    }
}
