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
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

@NodeInfo(shortName = "+")
public class IntegerAddNode extends IntegerArithmeticNode implements NarrowableArithmeticNode {

    public static IntegerAddNode create(ValueNode x, ValueNode y) {
        return new IntegerAddNodeGen(x, y);
    }

    public static Class<? extends IntegerAddNode> getGenClass() {
        return IntegerAddNodeGen.class;
    }

    protected IntegerAddNode(ValueNode x, ValueNode y) {
        super(StampTool.add(x.stamp(), y.stamp()), x, y);
    }

    @Override
    public boolean inferStamp() {
        return updateStamp(StampTool.add(getX().stamp(), getY().stamp()));
    }

    @Override
    public Constant evalConst(Constant... inputs) {
        assert inputs.length == 2;
        return Constant.forPrimitiveInt(PrimitiveStamp.getBits(stamp()), inputs[0].asLong() + inputs[1].asLong());
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        if (forX.isConstant() && !forY.isConstant()) {
            return IntegerAddNode.create(forY, forX);
        }
        if (forX instanceof IntegerSubNode) {
            IntegerSubNode sub = (IntegerSubNode) forX;
            if (sub.getY() == forY) {
                // (a - b) + b
                return sub.getX();
            }
        }
        if (forY instanceof IntegerSubNode) {
            IntegerSubNode sub = (IntegerSubNode) forY;
            if (sub.getY() == forX) {
                // b + (a - b)
                return sub.getX();
            }
        }
        if (forX.isConstant()) {
            return ConstantNode.forPrimitive(evalConst(forX.asConstant(), forY.asConstant()));
        } else if (forY.isConstant()) {
            long c = forY.asConstant().asLong();
            if (c == 0) {
                return forX;
            }
            // canonicalize expressions like "(a + 1) + 2"
            BinaryNode reassociated = BinaryNode.reassociate(this, ValueNode.isConstantPredicate(), forX, forY);
            if (reassociated != this) {
                return reassociated;
            }
        }
        if (forX instanceof NegateNode) {
            return IntegerArithmeticNode.sub(forY, ((NegateNode) forX).getValue());
        } else if (forY instanceof NegateNode) {
            return IntegerArithmeticNode.sub(forX, ((NegateNode) forY).getValue());
        }
        return this;
    }

    @Override
    public void generate(NodeMappableLIRBuilder builder, ArithmeticLIRGenerator gen) {
        Value op1 = builder.operand(getX());
        assert op1 != null : getX() + ", this=" + this;
        Value op2 = builder.operand(getY());
        if (!getY().isConstant() && !FloatAddNode.livesLonger(this, getY(), builder)) {
            Value op = op1;
            op1 = op2;
            op2 = op;
        }
        builder.setResult(this, gen.emitAdd(op1, op2));
    }
}
