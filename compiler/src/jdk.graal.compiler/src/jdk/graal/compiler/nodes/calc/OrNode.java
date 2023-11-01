/*
 * Copyright (c) 2011, 2023, Oracle and/or its affiliates. All rights reserved.
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
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.BinaryOp.Or;
import jdk.graal.compiler.core.common.type.IntegerStamp;
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
import jdk.graal.compiler.nodes.util.GraphUtil;

import jdk.vm.ci.meta.Constant;

@NodeInfo(shortName = "|")
public final class OrNode extends BinaryArithmeticNode<Or> implements Canonicalizable.BinaryCommutative<ValueNode>, NarrowableArithmeticNode {

    public static final NodeClass<OrNode> TYPE = NodeClass.create(OrNode.class);

    public OrNode(ValueNode x, ValueNode y) {
        super(TYPE, getArithmeticOpTable(x).getOr(), x, y);
    }

    public static ValueNode create(ValueNode x, ValueNode y, NodeView view) {
        BinaryOp<Or> op = ArithmeticOpTable.forStamp(x.stamp(view)).getOr();
        Stamp stamp = op.foldStamp(x.stamp(view), y.stamp(view));
        ConstantNode tryConstantFold = tryConstantFold(op, x, y, stamp, view);
        if (tryConstantFold != null) {
            return tryConstantFold;
        }
        return canonical(null, op, x, y, view);
    }

    @Override
    protected BinaryOp<Or> getOp(ArithmeticOpTable table) {
        return table.getOr();
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        NodeView view = NodeView.from(tool);
        ValueNode ret = super.canonical(tool, forX, forY);
        if (ret != this) {
            return ret;
        }

        return canonical(this, getOp(forX, forY), forX, forY, view);
    }

    private static ValueNode canonical(OrNode self, BinaryOp<Or> op, ValueNode forX, ValueNode forY, NodeView view) {
        if (GraphUtil.unproxify(forX) == GraphUtil.unproxify(forY)) {
            return forX;
        }
        if (forX.isConstant() && !forY.isConstant()) {
            return new OrNode(forY, forX);
        }

        Stamp rawXStamp = forX.stamp(view);
        Stamp rawYStamp = forY.stamp(view);
        if (rawXStamp instanceof IntegerStamp && rawYStamp instanceof IntegerStamp) {
            IntegerStamp xStamp = (IntegerStamp) rawXStamp;
            IntegerStamp yStamp = (IntegerStamp) rawYStamp;
            if (((~xStamp.mustBeSet()) & yStamp.mayBeSet()) == 0) {
                return forX;
            } else if (((~yStamp.mustBeSet()) & xStamp.mayBeSet()) == 0) {
                return forY;
            }
        }

        if (forY.isConstant()) {
            Constant c = forY.asConstant();
            if (op.isNeutral(c)) {
                return forX;
            }
            return reassociateMatchedValues(self != null ? self : (OrNode) new OrNode(forX, forY).maybeCommuteInputs(), ValueNode.isConstantPredicate(), forX, forY, view);
        }

        if (forX instanceof NotNode && forY instanceof NotNode) {
            // ~x | ~y |-> ~(x & y)
            return new NotNode(AndNode.create(((NotNode) forX).getValue(), ((NotNode) forY).getValue(), view));
        }
        if (forY instanceof NotNode && ((NotNode) forY).getValue() == forX) {
            // x | ~x |-> -1
            return ConstantNode.forIntegerStamp(rawXStamp, -1L);
        }
        return self != null ? self : new OrNode(forX, forY).maybeCommuteInputs();
    }

    @Override
    public void generate(NodeLIRBuilderTool nodeValueMap, ArithmeticLIRGeneratorTool gen) {
        nodeValueMap.setResult(this, gen.emitOr(nodeValueMap.operand(getX()), nodeValueMap.operand(getY())));
    }
}
