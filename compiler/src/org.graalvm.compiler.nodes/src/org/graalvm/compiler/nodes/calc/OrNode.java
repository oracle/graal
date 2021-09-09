/*
 * Copyright (c) 2011, 2020, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.core.common.type.ArithmeticOpTable;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.BinaryOp;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.BinaryOp.Or;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodes.spi.Canonicalizable.BinaryCommutative;
import org.graalvm.compiler.nodes.spi.CanonicalizerTool;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.nodes.util.GraphUtil;

import jdk.vm.ci.meta.Constant;

@NodeInfo(shortName = "|")
public final class OrNode extends BinaryArithmeticNode<Or> implements BinaryCommutative<ValueNode>, NarrowableArithmeticNode {

    public static final NodeClass<OrNode> TYPE = NodeClass.create(OrNode.class);

    public OrNode(ValueNode x, ValueNode y) {
        super(TYPE, getArithmeticOpTable(x).getOr(), x, y);
    }

    private OrNode(ValueNode x, ValueNode y, Stamp forcedStamp) {
        super(TYPE, forcedStamp, x, y);
    }

    /**
     * Create a new XorNode with a forced stamp, without eager folding. This should only be used in
     * snippet code, where native-image may assign wrong stamps during graph generation.
     */
    public static ValueNode createForSnippet(ValueNode x, ValueNode y, Stamp forcedStamp) {
        return new OrNode(x, y, forcedStamp);
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
            if (((~xStamp.downMask()) & yStamp.upMask()) == 0) {
                return forX;
            } else if (((~yStamp.downMask()) & xStamp.upMask()) == 0) {
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
            return new NotNode(AndNode.create(((NotNode) forX).getValue(), ((NotNode) forY).getValue(), view));
        }
        return self != null ? self : new OrNode(forX, forY).maybeCommuteInputs();
    }

    @Override
    public void generate(NodeLIRBuilderTool nodeValueMap, ArithmeticLIRGeneratorTool gen) {
        nodeValueMap.setResult(this, gen.emitOr(nodeValueMap.operand(getX()), nodeValueMap.operand(getY())));
    }
}
