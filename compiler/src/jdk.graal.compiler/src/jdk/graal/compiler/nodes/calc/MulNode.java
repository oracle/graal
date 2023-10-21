/*
 * Copyright (c) 2011, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_2;

import jdk.graal.compiler.core.common.type.ArithmeticOpTable;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.BinaryOp;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.BinaryOp.Mul;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.Value;

@NodeInfo(shortName = "*", cycles = CYCLES_2)
public class MulNode extends BinaryArithmeticNode<Mul> implements NarrowableArithmeticNode, Canonicalizable.BinaryCommutative<ValueNode> {

    public static final NodeClass<MulNode> TYPE = NodeClass.create(MulNode.class);

    public MulNode(ValueNode x, ValueNode y) {
        this(TYPE, x, y);
    }

    protected MulNode(NodeClass<? extends MulNode> c, ValueNode x, ValueNode y) {
        super(c, getArithmeticOpTable(x).getMul(), x, y);
    }

    public static ValueNode create(ValueNode x, ValueNode y, NodeView view) {
        BinaryOp<Mul> op = ArithmeticOpTable.forStamp(x.stamp(view)).getMul();
        Stamp stamp = op.foldStamp(x.stamp(view), y.stamp(view));
        ConstantNode tryConstantFold = tryConstantFold(op, x, y, stamp, view);
        if (tryConstantFold != null) {
            return tryConstantFold;
        }
        return canonical(null, op, stamp, x, y, view);
    }

    @Override
    protected BinaryOp<Mul> getOp(ArithmeticOpTable table) {
        return table.getMul();
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        ValueNode ret = super.canonical(tool, forX, forY);
        if (ret != this) {
            return ret;
        }

        if (forX.isConstant() && !forY.isConstant()) {
            // we try to swap and canonicalize
            ValueNode improvement = canonical(tool, forY, forX);
            if (improvement != this) {
                return improvement;
            }
            // if this fails we only swap
            return new MulNode(forY, forX);
        }

        // convert "(-a)*(-b)" into "a*b"
        if (forX instanceof NegateNode && forY instanceof NegateNode) {
            return new MulNode(((NegateNode) forX).getValue(), ((NegateNode) forY).getValue()).maybeCommuteInputs();
        }

        BinaryOp<Mul> op = getOp(forX, forY);
        NodeView view = NodeView.from(tool);
        return canonical(this, op, stamp(view), forX, forY, view);
    }

    private static ValueNode canonical(MulNode self, BinaryOp<Mul> op, Stamp stamp, ValueNode forX, ValueNode forY, NodeView view) {
        if (forY.isConstant()) {
            Constant c = forY.asConstant();
            if (op.isNeutral(c)) {
                return forX;
            }

            if (op.isAssociative()) {
                // Canonicalize expressions like "(a * 2) * 4" => "(a * 8)"
                ValueNode reassociated = reassociateMatchedValues(self != null ? self : (MulNode) new MulNode(forX, forY).maybeCommuteInputs(), ValueNode.isConstantPredicate(), forX, forY, view);
                if (reassociated != self) {
                    return reassociated;
                }
            }
            if (c instanceof PrimitiveConstant && ((PrimitiveConstant) c).getJavaKind().isNumericInteger()) {
                long i = ((PrimitiveConstant) c).asLong();
                ValueNode result = canonical(stamp, forX, i, view);
                if (result != null) {
                    return result;
                }
            }
        }
        return self != null ? self : new MulNode(forX, forY).maybeCommuteInputs();
    }

    public static ValueNode canonical(Stamp stamp, ValueNode forX, long i, NodeView view) {
        if (i == 0) {
            return ConstantNode.forIntegerStamp(stamp, 0);
        } else if (i == 1) {
            return forX;
        } else if (i == -1) {
            return NegateNode.create(forX, view);
        } else if (i > 0) {
            if (CodeUtil.isPowerOf2(i)) {
                return new LeftShiftNode(forX, ConstantNode.forInt(CodeUtil.log2(i)));
            } else if (CodeUtil.isPowerOf2(i - 1)) {
                return AddNode.create(new LeftShiftNode(forX, ConstantNode.forInt(CodeUtil.log2(i - 1))), forX, view);
            } else if (CodeUtil.isPowerOf2(i + 1)) {
                return SubNode.create(new LeftShiftNode(forX, ConstantNode.forInt(CodeUtil.log2(i + 1))), forX, view);
            } else {
                int bitCount = Long.bitCount(i);
                long highestBitValue = Long.highestOneBit(i);
                if (bitCount == 2) {
                    // e.g., 0b1000_0010
                    long lowerBitValue = i - highestBitValue;
                    assert highestBitValue > 0 && lowerBitValue > 0;
                    ValueNode left = new LeftShiftNode(forX, ConstantNode.forInt(CodeUtil.log2(highestBitValue)));
                    ValueNode right = lowerBitValue == 1 ? forX : new LeftShiftNode(forX, ConstantNode.forInt(CodeUtil.log2(lowerBitValue)));
                    return AddNode.create(left, right, view);
                } else {
                    // e.g., 0b1111_1100
                    int shiftToRoundUpToPowerOf2 = CodeUtil.log2(highestBitValue) + 1;
                    long subValue = (1 << shiftToRoundUpToPowerOf2) - i;
                    if (CodeUtil.isPowerOf2(subValue) && shiftToRoundUpToPowerOf2 < ((IntegerStamp) stamp).getBits()) {
                        assert CodeUtil.log2(subValue) >= 1;
                        ValueNode left = new LeftShiftNode(forX, ConstantNode.forInt(shiftToRoundUpToPowerOf2));
                        ValueNode right = new LeftShiftNode(forX, ConstantNode.forInt(CodeUtil.log2(subValue)));
                        return SubNode.create(left, right, view);
                    }
                }
            }
        } else if (i < 0) {
            if (CodeUtil.isPowerOf2(-i)) {
                return NegateNode.create(LeftShiftNode.create(forX, ConstantNode.forInt(CodeUtil.log2(-i)), view), view);
            }
        }
        return null;
    }

    @Override
    public void generate(NodeLIRBuilderTool nodeValueMap, ArithmeticLIRGeneratorTool gen) {
        Value op1 = nodeValueMap.operand(getX());
        Value op2 = nodeValueMap.operand(getY());
        if (shouldSwapInputs(nodeValueMap)) {
            Value tmp = op1;
            op1 = op2;
            op2 = tmp;
        }
        nodeValueMap.setResult(this, gen.emitMul(op1, op2, false));
    }

    protected boolean isExact() {
        return false;
    }
}
