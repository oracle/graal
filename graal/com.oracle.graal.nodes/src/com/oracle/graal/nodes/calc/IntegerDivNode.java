/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.calc;

import com.oracle.graal.api.code.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

@NodeInfo(shortName = "/")
public class IntegerDivNode extends FixedBinaryNode implements Canonicalizable, Lowerable, LIRLowerable {

    public IntegerDivNode(Stamp stamp, ValueNode x, ValueNode y) {
        super(stamp, x, y);
    }

    @Override
    public boolean inferStamp() {
        return updateStamp(StampTool.div(x().stamp(), y().stamp()));
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (x().isConstant() && y().isConstant()) {
            long y = y().asConstant().asLong();
            if (y == 0) {
                return this; // this will trap, can not canonicalize
            }
            return ConstantNode.forIntegerStamp(stamp(), x().asConstant().asLong() / y, graph());
        } else if (y().isConstant()) {
            long c = y().asConstant().asLong();
            if (c == 1) {
                return x();
            }
            if (c == -1) {
                return graph().unique(new NegateNode(x()));
            }
            long abs = Math.abs(c);
            if (CodeUtil.isPowerOf2(abs) && x().stamp() instanceof IntegerStamp) {
                Stamp unrestricted = stamp().unrestricted();
                ValueNode dividend = x();
                IntegerStamp stampX = (IntegerStamp) x().stamp();
                int log2 = CodeUtil.log2(abs);
                // no rounding if dividend is positive or if its low bits are always 0
                if (stampX.canBeNegative() || (stampX.upMask() & (abs - 1)) != 0) {
                    int bits = PrimitiveStamp.getBits(stamp());
                    RightShiftNode sign = graph().unique(new RightShiftNode(unrestricted, x(), ConstantNode.forInt(bits - 1, graph())));
                    UnsignedRightShiftNode round = graph().unique(new UnsignedRightShiftNode(unrestricted, sign, ConstantNode.forInt(bits - log2, graph())));
                    dividend = IntegerArithmeticNode.add(graph(), dividend, round);
                }
                RightShiftNode shift = graph().unique(new RightShiftNode(unrestricted, dividend, ConstantNode.forInt(log2, graph())));
                if (c < 0) {
                    return graph().unique(new NegateNode(shift));
                }
                return shift;
            }
        }

        // Convert the expression ((a - a % b) / b) into (a / b).
        if (x() instanceof IntegerSubNode) {
            IntegerSubNode integerSubNode = (IntegerSubNode) x();
            if (integerSubNode.y() instanceof IntegerRemNode) {
                IntegerRemNode integerRemNode = (IntegerRemNode) integerSubNode.y();
                if (integerSubNode.stamp().isCompatible(this.stamp()) && integerRemNode.stamp().isCompatible(this.stamp()) && integerSubNode.x() == integerRemNode.x() &&
                                this.y() == integerRemNode.y()) {
                    return graph().add(new IntegerDivNode(stamp(), integerSubNode.x(), this.y()));
                }
            }
        }

        if (next() instanceof IntegerDivNode) {
            NodeClass nodeClass = NodeClass.get(this.getClass());
            if (next().getClass() == this.getClass() && nodeClass.inputsEqual(this, next()) && nodeClass.valueEqual(this, next())) {
                return next();
            }
        }

        return this;
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        gen.setResult(this, gen.getLIRGeneratorTool().emitDiv(gen.operand(x()), gen.operand(y()), this));
    }

    @Override
    public boolean canDeoptimize() {
        return !(y().stamp() instanceof IntegerStamp) || ((IntegerStamp) y().stamp()).contains(0);
    }
}
