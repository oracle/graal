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
import com.oracle.graal.nodes.util.*;

@NodeInfo(shortName = "-")
public class SubNode extends BinaryArithmeticNode implements NarrowableArithmeticNode {

    public static SubNode create(ValueNode x, ValueNode y) {
        return USE_GENERATED_NODES ? new SubNodeGen(x, y) : new SubNode(x, y);
    }

    protected SubNode(ValueNode x, ValueNode y) {
        super(ArithmeticOpTable.forStamp(x.stamp()).getSub(), x, y);
    }

    @SuppressWarnings("hiding")
    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        ValueNode ret = super.canonical(tool, forX, forY);
        if (ret != this) {
            return ret;
        }

        if (GraphUtil.unproxify(forX) == GraphUtil.unproxify(forY)) {
            Constant zero = getOp().getZero(forX.stamp());
            if (zero != null) {
                return ConstantNode.forPrimitive(stamp(), zero);
            }
        }
        boolean associative = getOp().isAssociative();
        if (associative) {
            if (forX instanceof AddNode) {
                AddNode x = (AddNode) forX;
                if (x.getY() == forY) {
                    // (a + b) - b
                    return x.getX();
                }
                if (x.getX() == forY) {
                    // (a + b) - a
                    return x.getY();
                }
            } else if (forX instanceof SubNode) {
                SubNode x = (SubNode) forX;
                if (x.getX() == forY) {
                    // (a - b) - a
                    return NegateNode.create(x.getY());
                }
            }
            if (forY instanceof AddNode) {
                AddNode y = (AddNode) forY;
                if (y.getX() == forX) {
                    // a - (a + b)
                    return NegateNode.create(y.getY());
                }
                if (y.getY() == forX) {
                    // b - (a + b)
                    return NegateNode.create(y.getX());
                }
            } else if (forY instanceof SubNode) {
                SubNode y = (SubNode) forY;
                if (y.getX() == forX) {
                    // a - (a - b)
                    return y.getY();
                }
            }
        }
        if (forY.isConstant()) {
            Constant c = forY.asConstant();
            if (getOp().isNeutral(c)) {
                return forX;
            }
            if (associative) {
                BinaryNode reassociated = reassociate(this, ValueNode.isConstantPredicate(), forX, forY);
                if (reassociated != this) {
                    return reassociated;
                }
            }
            if (c.getKind().isNumericInteger()) {
                long i = c.asLong();
                if (i < 0 || ((IntegerStamp) StampFactory.forKind(forY.getKind())).contains(-i)) {
                    // Adding a negative is more friendly to the backend since adds are
                    // commutative, so prefer add when it fits.
                    return BinaryArithmeticNode.add(forX, ConstantNode.forIntegerStamp(stamp(), -i));
                }
            }
        } else if (forX.isConstant()) {
            Constant c = forX.asConstant();
            if (ArithmeticOpTable.forStamp(stamp()).getAdd().isNeutral(c)) {
                /*
                 * Note that for floating point numbers, + and - have different neutral elements. We
                 * have to test for the neutral element of +, because we are doing this
                 * transformation: 0 - x == (-x) + 0 == -x.
                 */
                return NegateNode.create(forY);
            }
            if (associative) {
                return reassociate(this, ValueNode.isConstantPredicate(), forX, forY);
            }
        }
        if (forY instanceof NegateNode) {
            return BinaryArithmeticNode.add(forX, ((NegateNode) forY).getValue());
        }
        return this;
    }

    @Override
    public void generate(NodeMappableLIRBuilder builder, ArithmeticLIRGenerator gen) {
        builder.setResult(this, gen.emitSub(builder.operand(getX()), builder.operand(getY())));
    }
}
