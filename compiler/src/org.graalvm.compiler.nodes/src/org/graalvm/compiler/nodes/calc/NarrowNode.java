/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.calc;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_1;
import static org.graalvm.compiler.nodes.calc.BinaryArithmeticNode.getArithmeticOpTable;

import org.graalvm.compiler.core.common.calc.CanonicalCondition;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.IntegerConvertOp;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.IntegerConvertOp.Narrow;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.PrimitiveStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.CanonicalizerTool;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.code.CodeUtil;

/**
 * The {@code NarrowNode} converts an integer to a narrower integer.
 */
@NodeInfo(cycles = CYCLES_1)
public final class NarrowNode extends IntegerConvertNode<Narrow> {

    public static final NodeClass<NarrowNode> TYPE = NodeClass.create(NarrowNode.class);

    public NarrowNode(ValueNode input, int resultBits) {
        this(input, PrimitiveStamp.getBits(input.stamp(NodeView.DEFAULT)), resultBits);
        assert 0 < resultBits && resultBits <= PrimitiveStamp.getBits(input.stamp(NodeView.DEFAULT));
    }

    public NarrowNode(ValueNode input, int inputBits, int resultBits) {
        super(TYPE, getArithmeticOpTable(input).getNarrow(), inputBits, resultBits, input);
    }

    public static ValueNode create(ValueNode input, int resultBits, NodeView view) {
        return create(input, PrimitiveStamp.getBits(input.stamp(view)), resultBits, view);
    }

    public static ValueNode create(ValueNode input, int inputBits, int resultBits, NodeView view) {
        IntegerConvertOp<Narrow> signExtend = ArithmeticOpTable.forStamp(input.stamp(view)).getNarrow();
        ValueNode synonym = findSynonym(signExtend, input, inputBits, resultBits, signExtend.foldStamp(inputBits, resultBits, input.stamp(view)));
        if (synonym != null) {
            return synonym;
        } else {
            return new NarrowNode(input, inputBits, resultBits);
        }
    }

    @Override
    protected IntegerConvertOp<Narrow> getOp(ArithmeticOpTable table) {
        return table.getNarrow();
    }

    @Override
    protected IntegerConvertOp<?> getReverseOp(ArithmeticOpTable table) {
        assert isSignedLossless() || isUnsignedLossless();
        return isSignedLossless() ? table.getSignExtend() : table.getZeroExtend();
    }

    @Override
    public boolean isLossless() {
        // This is conservative as we don't know which compare operator is being used.
        return isSignedLossless() && isUnsignedLossless();
    }

    private boolean isSignedLossless() {
        Stamp valueStamp = value.stamp(NodeView.DEFAULT);
        int bits = getResultBits();
        if (bits > 0 && valueStamp instanceof IntegerStamp) {
            IntegerStamp integerStamp = (IntegerStamp) valueStamp;
            long bitsRangeMin = CodeUtil.minValue(bits);
            long bitsRangeMax = CodeUtil.maxValue(bits);
            if (bitsRangeMin <= integerStamp.lowerBound() && integerStamp.upperBound() <= bitsRangeMax) {
                // all signed values fit
                return true;
            }
        }
        return false;
    }

    private boolean isUnsignedLossless() {
        Stamp valueStamp = value.stamp(NodeView.DEFAULT);
        int bits = getResultBits();
        if (bits > 0 && valueStamp instanceof IntegerStamp) {
            IntegerStamp integerStamp = (IntegerStamp) valueStamp;
            if (integerStamp.isPositive()) {
                long valueUpMask = integerStamp.upMask();
                if ((valueUpMask & CodeUtil.mask(bits)) == valueUpMask) {
                    // value is unsigned and fits
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean preservesOrder(CanonicalCondition cond) {
        switch (cond) {
            case LT:
                return isSignedLossless();
            case EQ:
            case BT:
                // We may use signed stamps to represent unsigned integers.
                return isSignedLossless() || isUnsignedLossless();
            default:
                throw GraalError.shouldNotReachHere("Unsupported canonical condition.");
        }
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        NodeView view = NodeView.from(tool);
        ValueNode ret = super.canonical(tool, forValue);
        if (ret != this) {
            return ret;
        }

        if (forValue instanceof NarrowNode) {
            // zzzzzzzz yyyyxxxx -(narrow)-> yyyyxxxx -(narrow)-> xxxx
            // ==> zzzzzzzz yyyyxxxx -(narrow)-> xxxx
            NarrowNode other = (NarrowNode) forValue;
            return new NarrowNode(other.getValue(), other.getInputBits(), getResultBits());
        } else if (forValue instanceof IntegerConvertNode) {
            // SignExtendNode or ZeroExtendNode
            IntegerConvertNode<?> other = (IntegerConvertNode<?>) forValue;
            if (other.getValue().hasExactlyOneUsage() && other.hasMoreThanOneUsage()) {
                // Do not perform if this will introduce a new live value.
                // If the original value's usage count is > 1, there is already another user.
                // If the convert's usage count is <=1, it will be dead code eliminated.
                return this;
            }
            if (getResultBits() == other.getInputBits()) {
                // xxxx -(extend)-> yyyy xxxx -(narrow)-> xxxx
                // ==> no-op
                return other.getValue();
            } else if (getResultBits() < other.getInputBits()) {
                // yyyyxxxx -(extend)-> zzzzzzzz yyyyxxxx -(narrow)-> xxxx
                // ==> yyyyxxxx -(narrow)-> xxxx
                return new NarrowNode(other.getValue(), other.getInputBits(), getResultBits());
            } else {
                if (other instanceof SignExtendNode) {
                    // sxxx -(sign-extend)-> ssssssss sssssxxx -(narrow)-> sssssxxx
                    // ==> sxxx -(sign-extend)-> sssssxxx
                    return SignExtendNode.create(other.getValue(), other.getInputBits(), getResultBits(), view);
                } else if (other instanceof ZeroExtendNode) {
                    // xxxx -(zero-extend)-> 00000000 0000xxxx -(narrow)-> 0000xxxx
                    // ==> xxxx -(zero-extend)-> 0000xxxx
                    return new ZeroExtendNode(other.getValue(), other.getInputBits(), getResultBits(), ((ZeroExtendNode) other).isInputAlwaysPositive());
                }
            }
        } else if (forValue instanceof AndNode) {
            AndNode andNode = (AndNode) forValue;
            Stamp xStamp = andNode.getX().stamp(view);
            Stamp yStamp = andNode.getY().stamp(view);
            if (xStamp instanceof IntegerStamp && yStamp instanceof IntegerStamp) {
                long relevantMask = CodeUtil.mask(this.getResultBits());
                if ((relevantMask & ((IntegerStamp) yStamp).downMask()) == relevantMask) {
                    return create(andNode.getX(), this.getResultBits(), view);
                } else if ((relevantMask & ((IntegerStamp) xStamp).downMask()) == relevantMask) {
                    return create(andNode.getY(), this.getResultBits(), view);
                }
            }
        }

        return this;
    }

    @Override
    public void generate(NodeLIRBuilderTool nodeValueMap, ArithmeticLIRGeneratorTool gen) {
        nodeValueMap.setResult(this, gen.emitNarrow(nodeValueMap.operand(getValue()), getResultBits()));
    }

    @Override
    public boolean mayNullCheckSkipConversion() {
        return false;
    }
}
