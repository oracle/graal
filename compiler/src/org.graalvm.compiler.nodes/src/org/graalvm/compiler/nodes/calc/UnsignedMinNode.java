/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.BinaryOp.UMin;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.LoweringProvider;

@NodeInfo(shortName = "UnsignedMin")
public class UnsignedMinNode extends MinMaxNode<UMin> {

    public static final NodeClass<UnsignedMinNode> TYPE = NodeClass.create(UnsignedMinNode.class);

    protected UnsignedMinNode(ValueNode x, ValueNode y) {
        super(TYPE, getArithmeticOpTable(x).getUMin(), x, y);
    }

    @Override
    protected BinaryOp<UMin> getOp(ArithmeticOpTable table) {
        return table.getUMin();
    }

    public static ValueNode create(ValueNode x, ValueNode y, NodeView view) {
        BinaryOp<UMin> op = ArithmeticOpTable.forStamp(x.stamp(view)).getUMin();
        Stamp stamp = op.foldStamp(x.stamp(view), y.stamp(view));
        ConstantNode tryConstantFold = tryConstantFold(op, x, y, stamp, view);
        if (tryConstantFold != null) {
            return tryConstantFold;
        }
        return new UnsignedMinNode(x, y).maybeCommuteInputs();
    }

    @Override
    public ValueNode asConditional(LoweringProvider lowerer) {
        if (!(stamp(NodeView.DEFAULT).isIntegerStamp())) {
            return null;
        }
        LogicNode condition = IntegerBelowNode.create(maybeExtendForCompare(getX(), lowerer, Signedness.UNSIGNED), maybeExtendForCompare(getY(), lowerer, Signedness.UNSIGNED), NodeView.DEFAULT);
        return ConditionalNode.create(condition, getX(), getY(), NodeView.DEFAULT);
    }

    @Override
    public boolean isNarrowable(int resultBits) {
        if (!super.isNarrowable(resultBits)) {
            return false;
        }
        return super.isNarrowable(resultBits, Signedness.UNSIGNED);
    }
}
