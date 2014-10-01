/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;

@NodeInfo(shortName = "*")
public class MulNode extends BinaryArithmeticNode implements NarrowableArithmeticNode {

    public static MulNode create(ValueNode x, ValueNode y) {
        return USE_GENERATED_NODES ? new MulNodeGen(x, y) : new MulNode(x, y);
    }

    protected MulNode(ValueNode x, ValueNode y) {
        super(ArithmeticOpTable.forStamp(x.stamp()).getMul(), x, y);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        ValueNode ret = super.canonical(tool, forX, forY);
        if (ret != this) {
            return ret;
        }

        if (forX.isConstant() && !forY.isConstant()) {
            return MulNode.create(forY, forX);
        }
        if (forY.isConstant()) {
            Constant c = forY.asConstant();
            if (getOp().isNeutral(c)) {
                return forX;
            }

            if (c.getKind().isNumericInteger()) {
                long i = c.asLong();
                boolean signFlip = false;
                if (i < 0) {
                    i = -i;
                    signFlip = true;
                }
                if (i > 0) {
                    ValueNode mulResult = null;
                    long bit1 = i & -i;
                    long bit2 = i - bit1;
                    bit2 = bit2 & -bit2;    // Extract 2nd bit
                    if (CodeUtil.isPowerOf2(i)) { //
                        mulResult = LeftShiftNode.create(forX, ConstantNode.forInt(CodeUtil.log2(i)));
                    } else if (bit2 + bit1 == i) { // We can work with two shifts and add
                        ValueNode shift1 = LeftShiftNode.create(forX, ConstantNode.forInt(CodeUtil.log2(bit1)));
                        ValueNode shift2 = LeftShiftNode.create(forX, ConstantNode.forInt(CodeUtil.log2(bit2)));
                        mulResult = AddNode.create(shift1, shift2);
                    } else if (CodeUtil.isPowerOf2(i + 1)) { // shift and subtract
                        ValueNode shift1 = LeftShiftNode.create(forX, ConstantNode.forInt(CodeUtil.log2(i + 1)));
                        mulResult = SubNode.create(shift1, forX);
                    }
                    if (mulResult != null) {
                        if (signFlip) {
                            return NegateNode.create(mulResult);
                        } else {
                            return mulResult;
                        }
                    }
                }
            }

            if (getOp().isAssociative()) {
                // canonicalize expressions like "(a * 1) * 2"
                return reassociate(this, ValueNode.isConstantPredicate(), forX, forY);
            }
        }
        return this;
    }

    @Override
    public void generate(NodeMappableLIRBuilder builder, ArithmeticLIRGenerator gen) {
        Value op1 = builder.operand(getX());
        Value op2 = builder.operand(getY());
        if (!getY().isConstant() && !BinaryArithmeticNode.livesLonger(this, getY(), builder)) {
            Value tmp = op1;
            op1 = op2;
            op2 = tmp;
        }
        builder.setResult(this, gen.emitMul(op1, op2));
    }
}
