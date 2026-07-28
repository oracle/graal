/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.core.common.type.ArithmeticOpTable;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.BinaryOp;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.BinaryOp.SUAdd;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.meta.Constant;

/**
 * Represents a saturating unsigned addition. The mathematical sum is clamped to the unsigned value
 * range instead of wrapping.
 */
@NodeInfo(shortName = "SUAdd")
public final class SaturatingUAddNode extends BinaryArithmeticNode<SUAdd> implements Canonicalizable.BinaryCommutative<ValueNode> {

    public static final NodeClass<SaturatingUAddNode> TYPE = NodeClass.create(SaturatingUAddNode.class);

    protected SaturatingUAddNode(ValueNode x, ValueNode y) {
        super(TYPE, getArithmeticOpTable(x).getSUAdd(), x, y);
    }

    public static ValueNode create(ValueNode x, ValueNode y, NodeView view) {
        BinaryOp<SUAdd> op = ArithmeticOpTable.forStamp(x.stamp(view)).getSUAdd();
        Stamp stamp = op.foldStamp(x.stamp(view), y.stamp(view));
        ConstantNode folded = tryConstantFold(op, x, y, stamp, view);
        if (folded != null) {
            return folded;
        }
        if (x.isConstant() && !y.isConstant()) {
            return canonical(null, op, y, x);
        }
        return canonical(null, op, x, y);
    }

    @Override
    protected BinaryOp<SUAdd> getOp(ArithmeticOpTable table) {
        return table.getSUAdd();
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        ValueNode ret = super.canonical(tool, forX, forY);
        if (ret != this) {
            return ret;
        }
        return canonical(this, getArithmeticOp(), forX, forY);
    }

    private static ValueNode canonical(SaturatingUAddNode self, BinaryOp<SUAdd> op, ValueNode x, ValueNode y) {
        if (y.isConstant()) {
            Constant c = y.asConstant();
            if (op.isNeutral(c)) {
                return x;
            }
        }
        return self != null ? self : new SaturatingUAddNode(x, y);
    }

    @Override
    public void generate(NodeLIRBuilderTool builder, ArithmeticLIRGeneratorTool gen) {
        builder.setResult(this, gen.emitSaturatingUnsignedAdd(builder.operand(getX()), builder.operand(getY())));
    }
}
