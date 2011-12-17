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
package com.oracle.max.graal.nodes.calc;

import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.spi.*;
import com.sun.cri.ci.*;

@NodeInfo(shortName = "<<")
public final class LeftShiftNode extends ShiftNode implements Canonicalizable, LIRLowerable {

    public LeftShiftNode(CiKind kind, ValueNode x, ValueNode y) {
        super(kind, x, y);
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (y().isConstant()) {
            int amount = y().asConstant().asInt();
            int originalAmout = amount;
            int mask;
            if (kind() == CiKind.Int) {
                mask = 0x1f;
            } else {
                assert kind() == CiKind.Long;
                mask = 0x3f;
            }
            amount &= mask;
            if (x().isConstant()) {
                if (kind() == CiKind.Int) {
                    return ConstantNode.forInt(x().asConstant().asInt() << amount, graph());
                } else {
                    assert kind() == CiKind.Long;
                    return ConstantNode.forLong(x().asConstant().asLong() << amount, graph());
                }
            }
            if (amount == 0) {
                return x();
            }
            if (x() instanceof ShiftNode) {
                ShiftNode other = (ShiftNode) x();
                if (other.y().isConstant()) {
                    int otherAmount = other.y().asConstant().asInt() & mask;
                    if (other instanceof LeftShiftNode) {
                        int total = amount + otherAmount;
                        if (total != (total & mask)) {
                            return ConstantNode.forInt(0, graph());
                        }
                        return graph().unique(new LeftShiftNode(kind(), other.x(), ConstantNode.forInt(total, graph())));
                    } else if ((other instanceof RightShiftNode || other instanceof UnsignedRightShiftNode) && otherAmount == amount) {
                        if (kind() == CiKind.Long) {
                            return graph().unique(new AndNode(kind(), other.x(), ConstantNode.forLong(-1L << amount, graph())));
                        } else {
                            assert kind() == CiKind.Int;
                            return graph().unique(new AndNode(kind(), other.x(), ConstantNode.forInt(-1 << amount, graph())));
                        }
                    }
                }
            }
            if (originalAmout != amount) {
                return graph().unique(new LeftShiftNode(kind(), x(), ConstantNode.forInt(amount, graph())));
            }
        }
        return this;
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
        gen.setResult(this, gen.emitShl(gen.operand(x()), gen.operand(y())));
    }
}
