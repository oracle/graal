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
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.ShiftOp.UShr;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.PrimitiveStamp;
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
import jdk.vm.ci.meta.JavaKind;

@NodeInfo(shortName = ">>>")
public final class UnsignedRightShiftNode extends ShiftNode<UShr> {

    public static final NodeClass<UnsignedRightShiftNode> TYPE = NodeClass.create(UnsignedRightShiftNode.class);

    public UnsignedRightShiftNode(ValueNode x, ValueNode y) {
        super(TYPE, BinaryArithmeticNode.getArithmeticOpTable(x).getUShr(), x, y);
    }

    public static ValueNode create(ValueNode x, ValueNode y, NodeView view) {
        ArithmeticOpTable.ShiftOp<UShr> op = ArithmeticOpTable.forStamp(x.stamp(view)).getUShr();
        Stamp stamp = op.foldStamp(x.stamp(view), (IntegerStamp) y.stamp(view));
        ValueNode value = ShiftNode.canonical(op, stamp, x, y, view);
        if (value != null) {
            return value;
        }

        return canonical(null, op, stamp, x, y, view);
    }

    @Override
    protected ShiftOp<UShr> getOp(ArithmeticOpTable table) {
        return table.getUShr();
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        NodeView view = NodeView.from(tool);
        ValueNode ret = super.canonical(tool, forX, forY);
        if (ret != this) {
            return ret;
        }

        return canonical(this, this.getArithmeticOp(), this.stamp(view), forX, forY, view);
    }

    @SuppressWarnings("unused")
    private static ValueNode canonical(UnsignedRightShiftNode node, ArithmeticOpTable.ShiftOp<UShr> op, Stamp stamp, ValueNode forX, ValueNode forY, NodeView view) {
        if (forY.isConstant()) {
            int amount = forY.asJavaConstant().asInt();
            int originalAmout = amount;
            int mask = op.getShiftAmountMask(stamp);
            amount &= mask;
            if (amount == 0) {
                return forX;
            }

            Stamp xStampGeneric = forX.stamp(view);
            if (xStampGeneric instanceof IntegerStamp) {
                IntegerStamp xStamp = (IntegerStamp) xStampGeneric;
                long xMask = CodeUtil.mask(xStamp.getBits());
                long xLowerBound = xStamp.lowerBound() & xMask;
                long xUpperBound = xStamp.upperBound() & xMask;

                if (xLowerBound >>> amount == xUpperBound >>> amount) {
                    // The result of the shift is constant.
                    return ConstantNode.forIntegerBits(PrimitiveStamp.getBits(stamp), xLowerBound >>> amount);
                }

                if (amount == xStamp.getBits() - 1 && xStamp.lowerBound() == -1 && xStamp.upperBound() == 0) {
                    // Shift is equivalent to a negation, i.e., turns -1 into 1 and keeps 0 at 0.
                    return NegateNode.create(forX, view);
                }
            }

            if (forX instanceof ShiftNode) {
                ShiftNode<?> other = (ShiftNode<?>) forX;
                if (other.getY().isConstant()) {
                    int otherAmount = other.getY().asJavaConstant().asInt() & mask;
                    if (other instanceof UnsignedRightShiftNode) {
                        int total = amount + otherAmount;
                        if (total != (total & mask)) {
                            return ConstantNode.forIntegerBits(PrimitiveStamp.getBits(stamp), 0);
                        }
                        return new UnsignedRightShiftNode(other.getX(), ConstantNode.forInt(total));
                    } else if (other instanceof LeftShiftNode && otherAmount == amount) {
                        if (stamp.getStackKind() == JavaKind.Long) {
                            return new AndNode(other.getX(), ConstantNode.forLong(-1L >>> amount));
                        } else {
                            assert stamp.getStackKind() == JavaKind.Int : Assertions.errorMessage(node, op, stamp, forX, forY);
                            return new AndNode(other.getX(), ConstantNode.forInt(-1 >>> amount));
                        }
                    }
                }
            }
            if (originalAmout != amount) {
                return new UnsignedRightShiftNode(forX, ConstantNode.forInt(amount));
            }
        }

        if (node != null) {
            return node;
        }
        return new UnsignedRightShiftNode(forX, forY);
    }

    @Override
    public void generate(NodeLIRBuilderTool nodeValueMap, ArithmeticLIRGeneratorTool gen) {
        nodeValueMap.setResult(this, gen.emitUShr(nodeValueMap.operand(getX()), nodeValueMap.operand(getY())));
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
             * For unsigned right shifts, the narrow can be done before the shift if the cut off
             * bits are all zero.
             */
            IntegerStamp inputStamp = (IntegerStamp) getX().stamp(NodeView.DEFAULT);
            return (inputStamp.mayBeSet() & ~(CodeUtil.mask(resultBits))) == 0;
        } else {
            return false;
        }
    }
}
