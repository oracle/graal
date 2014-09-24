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

@NodeInfo(shortName = "+")
public class AddNode extends BinaryArithmeticNode implements NarrowableArithmeticNode {

    public static AddNode create(ValueNode x, ValueNode y) {
        return USE_GENERATED_NODES ? new AddNodeGen(x, y) : new AddNode(x, y);
    }

    protected AddNode(ValueNode x, ValueNode y) {
        super(ArithmeticOpTable.forStamp(x.stamp()).getAdd(), x, y);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        ValueNode ret = super.canonical(tool, forX, forY);
        if (ret != this) {
            return ret;
        }

        if (forX.isConstant() && !forY.isConstant()) {
            return AddNode.create(forY, forX);
        }
        boolean associative = getOp().isAssociative();
        if (associative) {
            if (forX instanceof SubNode) {
                SubNode sub = (SubNode) forX;
                if (sub.getY() == forY) {
                    // (a - b) + b
                    return sub.getX();
                }
            }
            if (forY instanceof SubNode) {
                SubNode sub = (SubNode) forY;
                if (sub.getY() == forX) {
                    // b + (a - b)
                    return sub.getX();
                }
            }
        }
        if (forY.isConstant()) {
            Constant c = forY.asConstant();
            if (getOp().isNeutral(c)) {
                return forX;
            }
            if (associative) {
                // canonicalize expressions like "(a + 1) + 2"
                BinaryNode reassociated = reassociate(this, ValueNode.isConstantPredicate(), forX, forY);
                if (reassociated != this) {
                    return reassociated;
                }
            }
        }
        if (forX instanceof NegateNode) {
            return BinaryArithmeticNode.sub(forY, ((NegateNode) forX).getValue());
        } else if (forY instanceof NegateNode) {
            return BinaryArithmeticNode.sub(forX, ((NegateNode) forY).getValue());
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
