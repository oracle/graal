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
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;

@NodeInfo(shortName = "*")
public class IntegerMulNode extends IntegerArithmeticNode implements Canonicalizable {

    public IntegerMulNode(Kind kind, ValueNode x, ValueNode y) {
        super(kind, x, y);
    }

    @Override
    public Constant evalConst(Constant... inputs) {
        assert inputs.length == 2;
        return Constant.forIntegerKind(kind(), inputs[0].asLong() * inputs[1].asLong(), null);
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (x().isConstant() && !y().isConstant()) {
            return graph().unique(new IntegerMulNode(kind(), y(), x()));
        }
        if (x().isConstant()) {
            return ConstantNode.forPrimitive(evalConst(x().asConstant(), y().asConstant()), graph());
        } else if (y().isConstant()) {
            long c = y().asConstant().asLong();
            if (c == 1) {
                return x();
            }
            if (c == 0) {
                return ConstantNode.defaultForKind(kind(), graph());
            }
            long abs = Math.abs(c);
            if (abs > 0 && CodeUtil.isPowerOf2(abs)) {
                LeftShiftNode shift = graph().unique(new LeftShiftNode(kind(), x(), ConstantNode.forInt(CodeUtil.log2(abs), graph())));
                if (c < 0) {
                    return graph().unique(new NegateNode(shift));
                } else {
                    return shift;
                }
            }
            // canonicalize expressions like "(a * 1) * 2"
            return BinaryNode.reassociate(this, ValueNode.isConstantPredicate());
        }
        return this;
    }

    @Override
    public void generate(ArithmeticLIRGenerator gen) {
        Value op1 = gen.operand(x());
        Value op2 = gen.operand(y());
        if (!y().isConstant() && !FloatAddNode.livesLonger(this, y(), gen)) {
            Value op = op1;
            op1 = op2;
            op2 = op;
        }
        gen.setResult(this, gen.emitMul(op1, op2));
    }
}
