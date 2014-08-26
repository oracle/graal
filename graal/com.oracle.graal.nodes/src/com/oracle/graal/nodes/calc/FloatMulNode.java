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

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;

@NodeInfo(shortName = "*")
public class FloatMulNode extends FloatArithmeticNode {

    public static FloatMulNode create(ValueNode x, ValueNode y, boolean isStrictFP) {
        return USE_GENERATED_NODES ? new FloatMulNodeGen(x, y, isStrictFP) : new FloatMulNode(x, y, isStrictFP);
    }

    public static Class<? extends FloatMulNode> getGenClass() {
        return USE_GENERATED_NODES ? FloatMulNodeGen.class : FloatMulNode.class;
    }

    protected FloatMulNode(ValueNode x, ValueNode y, boolean isStrictFP) {
        super(x.stamp().unrestricted(), x, y, isStrictFP);
    }

    public Constant evalConst(Constant... inputs) {
        assert inputs.length == 2;
        assert inputs[0].getKind() == inputs[1].getKind();
        if (inputs[0].getKind() == Kind.Float) {
            return Constant.forFloat(inputs[0].asFloat() * inputs[1].asFloat());
        } else {
            assert inputs[0].getKind() == Kind.Double;
            return Constant.forDouble(inputs[0].asDouble() * inputs[1].asDouble());
        }
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        if (forX.isConstant() && !forY.isConstant()) {
            return FloatMulNode.create(forY, forX, isStrictFP());
        }
        if (forX.isConstant()) {
            return ConstantNode.forPrimitive(evalConst(forX.asConstant(), forY.asConstant()));
        }
        if (forY.isConstant()) {
            @SuppressWarnings("hiding")
            Constant y = forY.asConstant();
            switch (y.getKind()) {
                case Float:
                    if (y.asFloat() == 1.0f) {
                        return forX;
                    }
                    break;
                case Double:
                    if (y.asDouble() == 1.0) {
                        return forX;
                    }
                    break;
                default:
                    throw GraalGraphInternalError.shouldNotReachHere();
            }
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
