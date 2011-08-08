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
package com.oracle.max.graal.compiler.ir;

import com.oracle.max.graal.compiler.phases.CanonicalizerPhase.CanonicalizerOp;
import com.oracle.max.graal.compiler.phases.CanonicalizerPhase.NotifyReProcess;
import com.oracle.max.graal.graph.*;
import com.sun.cri.bytecode.*;
import com.sun.cri.ci.*;


public final class LeftShift extends Shift {
    private static final LeftShiftCanonicalizerOp CANONICALIZER = new LeftShiftCanonicalizerOp();

    public LeftShift(CiKind kind, Value x, Value y, Graph graph) {
        super(kind, kind == CiKind.Int ? Bytecodes.ISHL : Bytecodes.LSHL, x, y, graph);
    }

    @Override
    public String shortName() {
        return "<<";
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Op> T lookup(Class<T> clazz) {
        if (clazz == CanonicalizerOp.class) {
            return (T) CANONICALIZER;
        }
        return super.lookup(clazz);
    }

    private static class LeftShiftCanonicalizerOp implements CanonicalizerOp {
        @Override
        public Node canonical(Node node, NotifyReProcess reProcess) {
            LeftShift leftShift = (LeftShift) node;
            CiKind kind = leftShift.kind;
            Graph graph = leftShift.graph();
            Value value = leftShift.x();
            Value y = leftShift.y();
            if (y.isConstant()) {
                int amount = y.asConstant().asInt();
                int originalAmout = amount;
                int mask;
                if (kind == CiKind.Int) {
                    mask = 0x1f;
                } else {
                    assert kind == CiKind.Long;
                    mask = 0x3f;
                }
                amount &= mask;
                if (value.isConstant()) {
                    if (kind == CiKind.Int) {
                        return Constant.forInt(value.asConstant().asInt() << amount, graph);
                    } else {
                        assert kind == CiKind.Long;
                        return Constant.forLong(value.asConstant().asLong() << amount, graph);
                    }
                }
                if (amount == 0) {
                    return value;
                }
                if (value instanceof Shift) {
                    Shift other = (Shift) value;
                    if (other.y().isConstant()) {
                        int otherAmount = other.y().asConstant().asInt() & mask;
                        if (other instanceof LeftShift) {
                            int total = amount + otherAmount;
                            if (total != (total & mask)) {
                                return Constant.forInt(0, graph);
                            }
                            return new LeftShift(kind, other.x(), Constant.forInt(total, graph), graph);
                        } else if ((other instanceof RightShift || other instanceof UnsignedRightShift) && otherAmount == amount) {
                            if (kind == CiKind.Long) {
                                return new And(kind, other.x(), Constant.forLong(-1L << amount, graph), graph);
                            } else {
                                assert kind == CiKind.Int;
                                return new And(kind, other.x(), Constant.forInt(-1 << amount, graph), graph);
                            }
                        }
                    }
                }
                if (originalAmout != amount) {
                    return new LeftShift(kind, value, Constant.forInt(amount, graph), graph);
                }
            }
            return leftShift;
        }
    }
}
