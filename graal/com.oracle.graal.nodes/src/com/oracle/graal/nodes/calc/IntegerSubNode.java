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
import com.oracle.graal.nodes.util.*;

@NodeInfo(shortName = "-")
public class IntegerSubNode extends IntegerArithmeticNode implements NarrowableArithmeticNode {

    public static IntegerSubNode create(ValueNode x, ValueNode y) {
        return new IntegerSubNodeGen(x, y);
    }

    public static Class<? extends IntegerSubNode> getGenClass() {
        return IntegerSubNodeGen.class;
    }

    protected IntegerSubNode(ValueNode x, ValueNode y) {
        super(StampTool.sub(x.stamp(), y.stamp()), x, y);
    }

    @Override
    public boolean inferStamp() {
        return updateStamp(StampTool.sub(getX().stamp(), getY().stamp()));
    }

    @Override
    public Constant evalConst(Constant... inputs) {
        assert inputs.length == 2;
        return Constant.forPrimitiveInt(PrimitiveStamp.getBits(stamp()), inputs[0].asLong() - inputs[1].asLong());
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        if (GraphUtil.unproxify(forX) == GraphUtil.unproxify(forY)) {
            return ConstantNode.forIntegerStamp(stamp(), 0);
        }
        if (forX instanceof IntegerAddNode) {
            IntegerAddNode x = (IntegerAddNode) forX;
            if (x.getY() == forY) {
                // (a + b) - b
                return x.getX();
            }
            if (x.getX() == forY) {
                // (a + b) - a
                return x.getY();
            }
        } else if (forX instanceof IntegerSubNode) {
            IntegerSubNode x = (IntegerSubNode) forX;
            if (x.getX() == forY) {
                // (a - b) - a
                return NegateNode.create(x.getY());
            }
        }
        if (forY instanceof IntegerAddNode) {
            IntegerAddNode y = (IntegerAddNode) forY;
            if (y.getX() == forX) {
                // a - (a + b)
                return NegateNode.create(y.getY());
            }
            if (y.getY() == forX) {
                // b - (a + b)
                return NegateNode.create(y.getX());
            }
        } else if (forY instanceof IntegerSubNode) {
            IntegerSubNode y = (IntegerSubNode) forY;
            if (y.getX() == forX) {
                // a - (a - b)
                return y.getY();
            }
        }
        if (forX.isConstant() && forY.isConstant()) {
            return ConstantNode.forPrimitive(evalConst(forX.asConstant(), forY.asConstant()));
        } else if (forY.isConstant()) {
            long c = forY.asConstant().asLong();
            if (c == 0) {
                return forX;
            }
            BinaryNode reassociated = BinaryNode.reassociate(this, ValueNode.isConstantPredicate(), forX, forY);
            if (reassociated != this) {
                return reassociated;
            }
            if (c < 0 || ((IntegerStamp) StampFactory.forKind(forY.getKind())).contains(-c)) {
                // Adding a negative is more friendly to the backend since adds are
                // commutative, so prefer add when it fits.
                return IntegerArithmeticNode.add(forX, ConstantNode.forIntegerStamp(stamp(), -c));
            }
        } else if (forX.isConstant()) {
            long c = forX.asConstant().asLong();
            if (c == 0) {
                return NegateNode.create(forY);
            }
            return BinaryNode.reassociate(this, ValueNode.isConstantPredicate(), forX, forY);
        }
        if (forY instanceof NegateNode) {
            return IntegerArithmeticNode.add(forX, ((NegateNode) forY).getValue());
        }
        return this;
    }

    @Override
    public void generate(NodeMappableLIRBuilder builder, ArithmeticLIRGenerator gen) {
        builder.setResult(this, gen.emitSub(builder.operand(getX()), builder.operand(getY())));
    }
}
