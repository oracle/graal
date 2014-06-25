/*
 * Copyright (c) 2011, 2011, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

@NodeInfo(shortName = "+")
public class IntegerAddNode extends IntegerArithmeticNode implements Canonicalizable, NarrowableArithmeticNode {

    public IntegerAddNode(Stamp stamp, ValueNode x, ValueNode y) {
        super(stamp, x, y);
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
    public Node canonical(CanonicalizerTool tool) {
        if (getX().isConstant() && !getY().isConstant()) {
            return graph().unique(new IntegerAddNode(stamp(), getY(), getX()));
        }
        if (getX() instanceof IntegerSubNode) {
            IntegerSubNode sub = (IntegerSubNode) getX();
            if (sub.getY() == getY()) {
                // (a - b) + b
                return sub.getX();
            }
        }
        if (getY() instanceof IntegerSubNode) {
            IntegerSubNode sub = (IntegerSubNode) getY();
            if (sub.getY() == getX()) {
                // b + (a - b)
                return sub.getX();
            }
        }
        if (getX().isConstant()) {
            return ConstantNode.forPrimitive(evalConst(getX().asConstant(), getY().asConstant()), graph());
        } else if (getY().isConstant()) {
            long c = getY().asConstant().asLong();
            if (c == 0) {
                return getX();
            }
            // canonicalize expressions like "(a + 1) + 2"
            BinaryNode reassociated = BinaryNode.reassociate(this, ValueNode.isConstantPredicate());
            if (reassociated != this) {
                return reassociated;
            }
        }
        if (getX() instanceof NegateNode) {
            return IntegerArithmeticNode.sub(graph(), getY(), ((NegateNode) getX()).getValue());
        } else if (getY() instanceof NegateNode) {
            return IntegerArithmeticNode.sub(graph(), getX(), ((NegateNode) getY()).getValue());
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
