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
public class IntegerMulNode extends IntegerArithmeticNode implements NarrowableArithmeticNode {

    public static IntegerMulNode create(ValueNode x, ValueNode y) {
        return USE_GENERATED_NODES ? new IntegerMulNodeGen(x, y) : new IntegerMulNode(x, y);
    }

    protected IntegerMulNode(ValueNode x, ValueNode y) {
        super(x.stamp().unrestricted(), x, y);
        assert x.stamp().isCompatible(y.stamp());
    }

    @Override
    public Constant evalConst(Constant... inputs) {
        assert inputs.length == 2;
        return Constant.forPrimitiveInt(PrimitiveStamp.getBits(stamp()), inputs[0].asLong() * inputs[1].asLong());
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        if (forX.isConstant() && !forY.isConstant()) {
            return IntegerMulNode.create(forY, forX);
        }
        if (forX.isConstant()) {
            return ConstantNode.forPrimitive(evalConst(forX.asConstant(), forY.asConstant()));
        } else if (forY.isConstant()) {
            long c = forY.asConstant().asLong();
            if (c == 1) {
                return forX;
            }
            if (c == 0) {
                return ConstantNode.forIntegerStamp(stamp(), 0);
            }
            long abs = Math.abs(c);
            if (abs > 0 && CodeUtil.isPowerOf2(abs)) {
                LeftShiftNode shift = LeftShiftNode.create(forX, ConstantNode.forInt(CodeUtil.log2(abs)));
                if (c < 0) {
                    return NegateNode.create(shift);
                } else {
                    return shift;
                }
            }
            // canonicalize expressions like "(a * 1) * 2"
            return BinaryNode.reassociate(this, ValueNode.isConstantPredicate(), forX, forY);
        }
        return this;
    }

    @Override
    public void generate(NodeMappableLIRBuilder builder, ArithmeticLIRGenerator gen) {
        Value op1 = builder.operand(getX());
        Value op2 = builder.operand(getY());
        if (!getY().isConstant() && !FloatAddNode.livesLonger(this, getY(), builder)) {
            Value op = op1;
            op1 = op2;
            op2 = op;
        }
        builder.setResult(this, gen.emitMul(op1, op2));
    }
}
