/*
 * Copyright (c) 2011, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.graal.compiler.nodes.calc;

import jdk.graal.compiler.core.common.type.ArithmeticOpTable;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.ShiftOp;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.ShiftOp.Shr;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.code.CodeUtil;

@NodeInfo(shortName = ">>")
public final class RightShiftNode extends ShiftNode<Shr> {

    public static final NodeClass<RightShiftNode> TYPE = NodeClass.create(RightShiftNode.class);

    public RightShiftNode(ValueNode x, ValueNode y) {
        super(TYPE, BinaryArithmeticNode.getArithmeticOpTable(x).getShr(), x, y);
    }

    public static ValueNode create(ValueNode x, int y, NodeView view) {
        if (y == 0) {
            return x;
        }
        return create(x, ConstantNode.forInt(y), view);
    }

    public static ValueNode create(ValueNode x, ValueNode y, NodeView view) {
        ArithmeticOpTable.ShiftOp<Shr> op = ArithmeticOpTable.forStamp(x.stamp(view)).getShr();
        Stamp stamp = op.foldStamp(x.stamp(view), y.stamp(view));
        ValueNode value = canonical(op, stamp, x, y, view);
        if (value != null) {
            return value;
        }

        return canonical(null, op, stamp, x, y, view);
    }

    @Override
    protected ShiftOp<Shr> getOp(ArithmeticOpTable table) {
        return table.getShr();
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        NodeView view = NodeView.from(tool);
        ValueNode ret = super.canonical(tool, forX, forY);
        if (ret != this) {
            return ret;
        }

        return canonical(this, getArithmeticOp(), stamp(view), forX, forY, view);
    }

    private static ValueNode canonical(RightShiftNode rightShiftNode, ArithmeticOpTable.ShiftOp<Shr> op, Stamp stamp, ValueNode forX, ValueNode forY, NodeView view) {
        RightShiftNode self = rightShiftNode;
        if (forX.stamp(view) instanceof IntegerStamp && ((IntegerStamp) forX.stamp(view)).isPositive()) {
            return new UnsignedRightShiftNode(forX, forY);
        }

        Stamp xStampGeneric = forX.stamp(view);
        if (xStampGeneric instanceof IntegerStamp) {
            IntegerStamp xStamp = (IntegerStamp) xStampGeneric;
            if (xStamp.lowerBound() >= -1 && xStamp.upperBound() <= 0) {
                // Right shift by any amount does not change any bit.
                return forX;
            }
        }

        if (forY.isConstant() && op.isNeutral(forY.asConstant())) {
            return forX;
        }

        if (forY.isJavaConstant()) {
            int amount = forY.asJavaConstant().asInt();
            int originalAmout = amount;
            int mask = op.getShiftAmountMask(stamp);
            amount &= mask;
            if (amount == 0) {
                return forX;
            }

            if (xStampGeneric instanceof IntegerStamp) {
                IntegerStamp xStamp = (IntegerStamp) xStampGeneric;

                if (xStamp.lowerBound() >> amount == xStamp.upperBound() >> amount) {
                    // Right shift turns the result of the expression into a constant.
                    return ConstantNode.forIntegerBits(xStamp.getBits(), xStamp.lowerBound() >> amount);
                }
            }

            if (forX instanceof ShiftNode) {
                ShiftNode<?> other = (ShiftNode<?>) forX;
                if (other.getY().isConstant()) {
                    int otherAmount = other.getY().asJavaConstant().asInt() & mask;
                    if (other instanceof RightShiftNode) {
                        int total = amount + otherAmount;
                        if (total != (total & mask)) {
                            assert other.getX().stamp(view) instanceof IntegerStamp : Assertions.errorMessageContext("rightShiftNode", rightShiftNode, "forX", forX, "forY", forY, "other", other,
                                            "other.x", other.getX());
                            IntegerStamp istamp = (IntegerStamp) other.getX().stamp(view);

                            if (istamp.isPositive()) {
                                return ConstantNode.forIntegerBits(istamp.getBits(), 0);
                            }
                            if (istamp.isStrictlyNegative()) {
                                return ConstantNode.forIntegerBits(istamp.getBits(), -1L);
                            }

                            /*
                             * if we cannot replace both shifts with a constant, replace them by a
                             * full shift for this kind
                             */
                            assert total >= mask : Assertions.errorMessageContext(
                                            "rightShiftNode", rightShiftNode, "forX", forX, "forY", forY, "other", other,
                                            "other.x", other.getX(), "total", total, "mask", mask);
                            return new RightShiftNode(other.getX(), ConstantNode.forInt(mask));
                        }
                        return new RightShiftNode(other.getX(), ConstantNode.forInt(total));
                    }
                }
            }
            if (originalAmout != amount) {
                return new RightShiftNode(forX, ConstantNode.forInt(amount));
            }
        }
        if (self == null) {
            self = new RightShiftNode(forX, forY);
        }
        return self;
    }

    @Override
    public void generate(NodeLIRBuilderTool nodeValueMap, ArithmeticLIRGeneratorTool gen) {
        nodeValueMap.setResult(this, gen.emitShr(nodeValueMap.operand(getX()), nodeValueMap.operand(getY())));
    }

    @Override
    public boolean isNarrowable(int resultBits) {
        /*
         * Note that inserting a narrow before this node can change the input's stamp, as it can
         * cause a preceding (Zero|Sign)ExtendNode to be canonicalized away.
         *
         * Therefore, since the scalar shift on the underlying hardware will be on either a 32 or 64
         * bit operation, if resultBits < Integer.SIZE, the input to the shift cannot be narrowed.
         */
        if (resultBits >= Integer.SIZE && super.isNarrowable(resultBits)) {
            /*
             * For signed right shifts, the narrow can be done before the shift if the cut off bits
             * are all equal to the sign bit of the input. That's equivalent to the condition that
             * the input is in the signed range of the narrow type.
             */
            IntegerStamp inputStamp = (IntegerStamp) getX().stamp(NodeView.DEFAULT);
            return CodeUtil.minValue(resultBits) <= inputStamp.lowerBound() && inputStamp.upperBound() <= CodeUtil.maxValue(resultBits);
        } else {
            return false;
        }
    }
}
