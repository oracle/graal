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
import jdk.graal.compiler.core.common.type.ArithmeticOpTable;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.BinaryOp;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.BinaryOp.Min;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.LoweringProvider;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;

@NodeInfo(shortName = "Min")
public class MinNode extends MinMaxNode<Min> {

    public static final NodeClass<MinNode> TYPE = NodeClass.create(MinNode.class);

    protected MinNode(ValueNode x, ValueNode y) {
        super(TYPE, getArithmeticOpTable(x).getMin(), x, y);
    }

    @Override
    protected BinaryOp<Min> getOp(ArithmeticOpTable table) {
        return table.getMin();
    }

    public static ValueNode create(ValueNode x, ValueNode y, NodeView view) {
        BinaryOp<Min> op = ArithmeticOpTable.forStamp(x.stamp(view)).getMin();
        Stamp stamp = op.foldStamp(x.stamp(view), y.stamp(view));
        ConstantNode tryConstantFold = tryConstantFold(op, x, y, stamp, view);
        if (tryConstantFold != null) {
            return tryConstantFold;
        }
        return new MinNode(x, y).maybeCommuteInputs();
    }

    @Override
    public ValueNode asConditional(LoweringProvider lowerer) {
        if (!(stamp(NodeView.DEFAULT).isIntegerStamp())) {
            return null;
        }
        LogicNode condition = IntegerLessThanNode.create(maybeExtendForCompare(getX(), lowerer, Signedness.SIGNED), maybeExtendForCompare(getY(), lowerer, Signedness.SIGNED), NodeView.DEFAULT);
        return ConditionalNode.create(condition, getX(), getY(), NodeView.DEFAULT);
    }

    @Override
    public boolean isNarrowable(int resultBits) {
        if (!super.isNarrowable(resultBits)) {
            return false;
        }
        return super.isNarrowable(resultBits, Signedness.SIGNED);
    }

    @SuppressWarnings("unlikely-arg-type")
    public static boolean isSupported(Architecture arch) {
        return switch (arch) {
            case AMD64 amd64 -> amd64.getFeatures().contains(AMD64.CPUFeature.AVX);
            case AArch64 aarch64 -> true;
            default -> false;
        };
    }
}
