/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.compiler.common.type.ArithmeticOpTable.BinaryOp;
import com.oracle.graal.compiler.common.type.ArithmeticOpTable.BinaryOp.Sub;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.jvmci.meta.*;

@NodeInfo(shortName = "-")
public class SubNode extends BinaryArithmeticNode<Sub> implements NarrowableArithmeticNode {

    public static final NodeClass<SubNode> TYPE = NodeClass.create(SubNode.class);

    public SubNode(ValueNode x, ValueNode y) {
        this(TYPE, x, y);
    }

    protected SubNode(NodeClass<? extends SubNode> c, ValueNode x, ValueNode y) {
        super(c, ArithmeticOpTable::getSub, x, y);
    }

    public static ValueNode create(ValueNode x, ValueNode y) {
        BinaryOp<Sub> op = ArithmeticOpTable.forStamp(x.stamp()).getSub();
        Stamp stamp = op.foldStamp(x.stamp(), y.stamp());
        ConstantNode tryConstantFold = tryConstantFold(op, x, y, stamp);
        if (tryConstantFold != null) {
            return tryConstantFold;
        } else {
            return new SubNode(x, y);
        }
    }

    @SuppressWarnings("hiding")
    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        ValueNode ret = super.canonical(tool, forX, forY);
        if (ret != this) {
            return ret;
        }

        BinaryOp<Sub> op = getOp(forX, forY);
        if (GraphUtil.unproxify(forX) == GraphUtil.unproxify(forY)) {
            Constant zero = op.getZero(forX.stamp());
            if (zero != null) {
                return ConstantNode.forPrimitive(stamp(), zero);
            }
        }
        boolean associative = op.isAssociative();
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
                    return new NegateNode(x.getY());
                }
            }
            if (forY instanceof AddNode) {
                AddNode y = (AddNode) forY;
                if (y.getX() == forX) {
                    // a - (a + b)
                    return new NegateNode(y.getY());
                }
                if (y.getY() == forX) {
                    // b - (a + b)
                    return new NegateNode(y.getX());
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
            if (op.isNeutral(c)) {
                return forX;
            }
            if (associative) {
                BinaryNode reassociated = reassociate(this, ValueNode.isConstantPredicate(), forX, forY);
                if (reassociated != this) {
                    return reassociated;
                }
            }
            if (c instanceof PrimitiveConstant && ((PrimitiveConstant) c).getKind().isNumericInteger()) {
                long i = ((PrimitiveConstant) c).asLong();
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
                return new NegateNode(forY);
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
        builder.setResult(this, gen.emitSub(builder.operand(getX()), builder.operand(getY()), false));
    }
}
