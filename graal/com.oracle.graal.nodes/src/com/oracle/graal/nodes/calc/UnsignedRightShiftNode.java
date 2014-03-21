/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

@NodeInfo(shortName = ">>>")
public final class UnsignedRightShiftNode extends ShiftNode implements Canonicalizable {

    public UnsignedRightShiftNode(Stamp stamp, ValueNode x, ValueNode y) {
        super(stamp, x, y);
    }

    @Override
    public boolean inferStamp() {
        return updateStamp(StampTool.unsignedRightShift(x().stamp(), y().stamp()));
    }

    @Override
    public Constant evalConst(Constant... inputs) {
        assert inputs.length == 2;
        if (getKind() == Kind.Int) {
            return Constant.forInt(inputs[0].asInt() >>> inputs[1].asInt());
        } else {
            assert getKind() == Kind.Long;
            return Constant.forLong(inputs[0].asLong() >>> inputs[1].asLong());
        }
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (x().isConstant() && y().isConstant()) {
            return ConstantNode.forPrimitive(evalConst(x().asConstant(), y().asConstant()), graph());
        } else if (y().isConstant()) {
            int amount = y().asConstant().asInt();
            int originalAmout = amount;
            int mask = getShiftAmountMask();
            amount &= mask;
            if (amount == 0) {
                return x();
            }
            if (x() instanceof ShiftNode) {
                ShiftNode other = (ShiftNode) x();
                if (other.y().isConstant()) {
                    int otherAmount = other.y().asConstant().asInt() & mask;
                    if (other instanceof UnsignedRightShiftNode) {
                        int total = amount + otherAmount;
                        if (total != (total & mask)) {
                            return ConstantNode.forIntegerKind(getKind(), 0, graph());
                        }
                        return graph().unique(new UnsignedRightShiftNode(stamp(), other.x(), ConstantNode.forInt(total, graph())));
                    } else if (other instanceof LeftShiftNode && otherAmount == amount) {
                        if (getKind() == Kind.Long) {
                            return graph().unique(new AndNode(stamp(), other.x(), ConstantNode.forLong(-1L >>> amount, graph())));
                        } else {
                            assert getKind() == Kind.Int;
                            return graph().unique(new AndNode(stamp(), other.x(), ConstantNode.forInt(-1 >>> amount, graph())));
                        }
                    }
                }
            }
            if (originalAmout != amount) {
                return graph().unique(new UnsignedRightShiftNode(stamp(), x(), ConstantNode.forInt(amount, graph())));
            }
        }
        return this;
    }

    @Override
    public void generate(ArithmeticLIRGenerator gen) {
        gen.setResult(this, gen.emitUShr(gen.operand(x()), gen.operand(y())));
    }
}
