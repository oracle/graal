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

import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.compiler.common.type.ArithmeticOpTable.BinaryOp;
import com.oracle.graal.compiler.common.type.ArithmeticOpTable.BinaryOp.Div;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.jvmci.code.*;
import com.oracle.jvmci.meta.*;

@NodeInfo(shortName = "/")
public final class DivNode extends BinaryArithmeticNode<Div> {

    public static final NodeClass<DivNode> TYPE = NodeClass.create(DivNode.class);

    public DivNode(ValueNode x, ValueNode y) {
        super(TYPE, ArithmeticOpTable::getDiv, x, y);
    }

    public static ValueNode create(ValueNode x, ValueNode y) {
        BinaryOp<Div> op = ArithmeticOpTable.forStamp(x.stamp()).getDiv();
        Stamp stamp = op.foldStamp(x.stamp(), y.stamp());
        ConstantNode tryConstantFold = tryConstantFold(op, x, y, stamp);
        if (tryConstantFold != null) {
            return tryConstantFold;
        } else {
            return new DivNode(x, y);
        }
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        ValueNode ret = super.canonical(tool, forX, forY);
        if (ret != this) {
            return ret;
        }

        if (forY.isConstant()) {
            Constant c = forY.asConstant();
            if (getOp(forX, forY).isNeutral(c)) {
                return forX;
            }
            if (c instanceof PrimitiveConstant && ((PrimitiveConstant) c).getKind().isNumericInteger()) {
                long i = ((PrimitiveConstant) c).asLong();
                boolean signFlip = false;
                if (i < 0) {
                    i = -i;
                    signFlip = true;
                }
                ValueNode divResult = null;
                if (CodeUtil.isPowerOf2(i)) {
                    divResult = new RightShiftNode(forX, ConstantNode.forInt(CodeUtil.log2(i)));
                }
                if (divResult != null) {
                    if (signFlip) {
                        return new NegateNode(divResult);
                    } else {
                        return divResult;
                    }
                }
            }
        }
        return this;
    }

    @Override
    public void generate(NodeMappableLIRBuilder builder, ArithmeticLIRGenerator gen) {
        builder.setResult(this, gen.emitDiv(builder.operand(getX()), builder.operand(getY()), null));
    }
}
