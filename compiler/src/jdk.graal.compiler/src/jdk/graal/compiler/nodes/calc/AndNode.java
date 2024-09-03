/*
 * Copyright (c) 2011, 2023, Oracle and/or its affiliates. All rights reserved.
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
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.BinaryOp;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.BinaryOp.And;
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
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.PrimitiveConstant;

@NodeInfo(shortName = "&")
public final class AndNode extends BinaryArithmeticNode<And> implements NarrowableArithmeticNode, Canonicalizable.BinaryCommutative<ValueNode> {

    public static final NodeClass<AndNode> TYPE = NodeClass.create(AndNode.class);

    public AndNode(ValueNode x, ValueNode y) {
        super(TYPE, getArithmeticOpTable(x).getAnd(), x, y);
    }

    public static ValueNode create(ValueNode x, ValueNode y, NodeView view) {
        BinaryOp<And> op = ArithmeticOpTable.forStamp(x.stamp(view)).getAnd();
        Stamp stamp = op.foldStamp(x.stamp(view), y.stamp(view));
        ConstantNode tryConstantFold = tryConstantFold(op, x, y, stamp, view);
        if (tryConstantFold != null) {
            return tryConstantFold;
        }
        return canonical(null, op, x, y, view);
    }

    @Override
    protected BinaryOp<And> getOp(ArithmeticOpTable table) {
        return table.getAnd();
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        ValueNode ret = super.canonical(tool, forX, forY);
        if (ret != this) {
            return ret;
        }

        NodeView view = NodeView.from(tool);
        return canonical(this, getOp(forX, forY), forX, forY, view);
    }

    /**
     * Given a value which is an input to an {@code AndNode} and an IntegerStamp for the other input
     * determine if the input can be simplified by folding away an {@code AddNode} or an
     * {@code OrNode}.
     *
     * @param usingAndInput the {@code AndNode} {@code ValueNode} input
     * @param usingAndOtherStamp the stamp of the other input
     * @return A {@code ValueNode} to use as a replacement input in place of the
     *         {@code usingAndInput} input, null otherwise
     */
    public static ValueNode eliminateRedundantBinaryArithmeticOp(ValueNode usingAndInput, IntegerStamp usingAndOtherStamp) {
        if (usingAndOtherStamp.isUnrestricted()) {
            return null;
        }
        if (!(usingAndInput instanceof BinaryArithmeticNode<?>)) {
            return null;
        }
        BinaryArithmeticNode<?> opNode = (BinaryArithmeticNode<?>) usingAndInput;
        ValueNode opX = opNode.getX();
        ValueNode opY = opNode.getY();
        IntegerStamp stampX = (IntegerStamp) opX.stamp(NodeView.DEFAULT);
        IntegerStamp stampY = (IntegerStamp) opY.stamp(NodeView.DEFAULT);
        if (usingAndInput instanceof OrNode) {
            // An OrNode strictly adds information to a bit pattern - it cannot remove set bits.
            // We can fold an operand away when that operand does not contribute any bits to the
            // masked result - check must be set against masked maybe set bits
            if (!stampY.isUnrestricted() && (stampY.mayBeSet() & usingAndOtherStamp.mayBeSet()) == 0) {
                return opX;
            }
            if (!stampX.isUnrestricted() && (stampX.mayBeSet() & usingAndOtherStamp.mayBeSet()) == 0) {
                return opY;
            }
        } else if (usingAndInput instanceof AddNode) {
            // like an OrNode above, an AddNode is adding information in a fixed set of
            // bit positions, modulo carrying across columns. So if we know:
            // 1) the operand has ones only where we know zeros must be in the and result
            // 2) bit carrys can't mess up the pattern (eg bits are packed at the bottom)
            // then we can simply fold the add away because it adds no information
            long mightBeOne = usingAndOtherStamp.mayBeSet();
            // here we check all the bits that might be set are packed and contiguous at the bottom
            // of the stamp - number of leading zeros + bitCount == number of bits
            if (Long.numberOfLeadingZeros(mightBeOne) + Long.bitCount(mightBeOne) != 64) {
                return null;
            }

            if (mightBeOne != 0) {
                // check if the operand stamp has any ones in the range of possible ones
                // if there are no ones we know there is no possibility of a carry and
                // no information added so we can fold away the operation
                //
                // eg given a ((x << 2) + 15) & 3
                // we know that the bits in the result of the and that might be one are the lowest
                // two bits
                //
                // we know that x << 2 has no bits set in the lowest two bits so we don't need to
                // add x << 2 to 15 to know what the result of the & 3 is - we can just do 15 & 3
                if (!stampY.isUnrestricted() && (stampY.mayBeSet() & mightBeOne) == 0) {
                    return opX;
                }
                if (!stampX.isUnrestricted() && (stampX.mayBeSet() & mightBeOne) == 0) {
                    return opY;
                }
            }
        }
        return null;
    }

    private static ValueNode canonical(AndNode self, BinaryOp<And> op, ValueNode forX, ValueNode forY, NodeView view) {
        if (GraphUtil.unproxify(forX) == GraphUtil.unproxify(forY)) {
            return forX;
        }
        if (forX.isConstant() && !forY.isConstant()) {
            return new AndNode(forY, forX);
        }

        Stamp rawXStamp = forX.stamp(view);
        Stamp rawYStamp = forY.stamp(view);
        if (rawXStamp instanceof IntegerStamp && rawYStamp instanceof IntegerStamp) {
            IntegerStamp xStamp = (IntegerStamp) rawXStamp;
            IntegerStamp yStamp = (IntegerStamp) rawYStamp;
            if (((~xStamp.mustBeSet()) & yStamp.mayBeSet()) == 0) {
                return forY;
            } else if (((~yStamp.mustBeSet()) & xStamp.mayBeSet()) == 0) {
                return forX;
            }
            ValueNode newLHS = eliminateRedundantBinaryArithmeticOp(forX, yStamp);
            if (newLHS != null) {
                return new AndNode(newLHS, forY);
            }
            ValueNode newRHS = eliminateRedundantBinaryArithmeticOp(forY, xStamp);
            if (newRHS != null) {
                return new AndNode(forX, newRHS);
            }
        }

        if (forY.isConstant()) {
            Constant c = forY.asConstant();
            if (op.isNeutral(c)) {
                return forX;
            }

            if (c instanceof PrimitiveConstant && ((PrimitiveConstant) c).getJavaKind().isNumericInteger()) {
                long rawY = ((PrimitiveConstant) c).asLong();
                if (forX instanceof SignExtendNode) {
                    SignExtendNode ext = (SignExtendNode) forX;
                    if (rawY == ((1L << ext.getInputBits()) - 1)) {
                        return new ZeroExtendNode(ext.getValue(), ext.getResultBits());
                    }
                }
            }

            return reassociateMatchedValues(self != null ? self : (AndNode) new AndNode(forX, forY).maybeCommuteInputs(), ValueNode.isConstantPredicate(), forX, forY, view);
        }
        if (forX instanceof NotNode && forY instanceof NotNode) {
            // ~x & ~y |-> ~(x | y)
            return new NotNode(OrNode.create(((NotNode) forX).getValue(), ((NotNode) forY).getValue(), view));
        }
        if (forY instanceof NotNode && ((NotNode) forY).getValue() == forX && rawXStamp instanceof IntegerStamp) {
            // x & ~x |-> 0
            return ConstantNode.forIntegerStamp(rawXStamp, 0L);
        }
        return self != null ? self : new AndNode(forX, forY).maybeCommuteInputs();
    }

    @Override
    public void generate(NodeLIRBuilderTool nodeValueMap, ArithmeticLIRGeneratorTool gen) {
        nodeValueMap.setResult(this, gen.emitAnd(nodeValueMap.operand(getX()), nodeValueMap.operand(getY())));
    }
}
