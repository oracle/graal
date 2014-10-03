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
import com.oracle.graal.nodes.util.*;

@NodeInfo(shortName = "&")
public class AndNode extends BinaryArithmeticNode implements NarrowableArithmeticNode {

    public static AndNode create(ValueNode x, ValueNode y) {
        return USE_GENERATED_NODES ? new AndNodeGen(x, y) : new AndNode(x, y);
    }

    protected AndNode(ValueNode x, ValueNode y) {
        super(ArithmeticOpTable.forStamp(x.stamp()).getAnd(), x, y);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        ValueNode ret = super.canonical(tool, forX, forY);
        if (ret != this) {
            return ret;
        }

        if (GraphUtil.unproxify(forX) == GraphUtil.unproxify(forY)) {
            return forX;
        }
        if (forX.isConstant() && !forY.isConstant()) {
            return AndNode.create(forY, forX);
        }
        if (forY.isConstant()) {
            Constant c = forY.asConstant();
            if (getOp().isNeutral(c)) {
                return forX;
            }

            if (c.getKind().isNumericInteger()) {
                long rawY = c.asLong();
                long mask = CodeUtil.mask(PrimitiveStamp.getBits(stamp()));
                if ((rawY & mask) == 0) {
                    return ConstantNode.forIntegerStamp(stamp(), 0);
                }
                if (forX instanceof SignExtendNode) {
                    SignExtendNode ext = (SignExtendNode) forX;
                    if (rawY == ((1L << ext.getInputBits()) - 1)) {
                        return ZeroExtendNode.create(ext.getValue(), ext.getResultBits());
                    }
                }
                IntegerStamp xStamp = (IntegerStamp) forX.stamp();
                if (((xStamp.upMask() | xStamp.downMask()) & ~rawY) == 0) {
                    // No bits are set which are outside the mask, so the mask will have no effect.
                    return forX;
                }
            }

            return reassociate(this, ValueNode.isConstantPredicate(), forX, forY);
        }
        return this;
    }

    @Override
    public void generate(NodeMappableLIRBuilder builder, ArithmeticLIRGenerator gen) {
        builder.setResult(this, gen.emitAnd(builder.operand(getX()), builder.operand(getY())));
    }
}
