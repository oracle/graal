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
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;

@NodeInfo(shortName = "+")
public class FloatAddNode extends FloatArithmeticNode {

    public FloatAddNode(ValueNode x, ValueNode y, boolean isStrictFP) {
        super(x.stamp().unrestricted(), x, y, isStrictFP);
    }

    public Constant evalConst(Constant... inputs) {
        assert inputs.length == 2;
        assert inputs[0].getKind() == inputs[1].getKind();
        if (inputs[0].getKind() == Kind.Float) {
            return Constant.forFloat(inputs[0].asFloat() + inputs[1].asFloat());
        } else {
            assert inputs[0].getKind() == Kind.Double;
            return Constant.forDouble(inputs[0].asDouble() + inputs[1].asDouble());
        }
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        if (forX.isConstant() && !forY.isConstant()) {
            return new FloatAddNode(forY, forX, isStrictFP());
        }
        if (forX.isConstant()) {
            return ConstantNode.forConstant(evalConst(forX.asConstant(), forY.asConstant()), null);
        }
        // Constant 0.0 can't be eliminated since it can affect the sign of the result.
        // Constant -0.0 is an additive identity.
        if (forY.isConstant()) {
            Constant y = forY.asConstant();
            switch (y.getKind()) {
                case Float:
                    // use Float.compare because -0.0f == 0.0f
                    if (Float.compare(y.asFloat(), -0.0f) == 0) {
                        return forX;
                    }
                    break;
                case Double:
                    // use Double.compare because -0.0 == 0.0
                    if (Double.compare(y.asDouble(), -0.0) == 0) {
                        return forX;
                    }
                    break;
                default:
                    throw GraalGraphInternalError.shouldNotReachHere();
            }
        }
        /*
         * JVM spec, Chapter 6, dsub/fsub bytecode: For double subtraction, it is always the case
         * that a-b produces the same result as a+(-b).
         */
        if (forX instanceof NegateNode) {
            return new FloatSubNode(forY, ((NegateNode) forX).getValue(), isStrictFP());
        }
        if (forY instanceof NegateNode) {
            return new FloatSubNode(forX, ((NegateNode) forY).getValue(), isStrictFP());
        }
        return this;
    }

    @Override
    public void generate(NodeMappableLIRBuilder builder, ArithmeticLIRGenerator gen) {
        Value op1 = builder.operand(getX());
        Value op2 = builder.operand(getY());
        if (!getY().isConstant() && !livesLonger(this, getY(), builder)) {
            Value op = op1;
            op1 = op2;
            op2 = op;
        }
        builder.setResult(this, gen.emitAdd(op1, op2));
    }

    public static boolean livesLonger(ValueNode after, ValueNode value, NodeMappableLIRBuilder builder) {
        for (Node usage : value.usages()) {
            if (usage != after && usage instanceof ValueNode && builder.hasOperand(((ValueNode) usage))) {
                return true;
            }
        }
        return false;
    }
}
