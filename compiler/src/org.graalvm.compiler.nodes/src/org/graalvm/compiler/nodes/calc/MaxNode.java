/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.calc;

import org.graalvm.compiler.core.common.NumUtil.Signedness;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.BinaryOp;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.BinaryOp.Max;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.LoweringProvider;

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
    public boolean isNarrowable(int resultBits) {
        if (!super.isNarrowable(resultBits)) {
            return false;
        }
        return super.isNarrowable(resultBits, Signedness.SIGNED);
    }
}
