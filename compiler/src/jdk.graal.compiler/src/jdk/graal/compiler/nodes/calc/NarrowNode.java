/*
 * Copyright (c) 2014, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_1;

import jdk.graal.compiler.core.common.calc.CanonicalCondition;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.IntegerConvertOp;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.IntegerConvertOp.Narrow;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.PrimitiveStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.code.CodeUtil;

/**
 * The {@code NarrowNode} converts an integer to a narrower integer.
 */
@NodeInfo(cycles = CYCLES_1)
public final class NarrowNode extends IntegerConvertNode<Narrow> {

    public static final NodeClass<NarrowNode> TYPE = NodeClass.create(NarrowNode.class);

    public NarrowNode(ValueNode input, int resultBits) {
        this(input, PrimitiveStamp.getBits(input.stamp(NodeView.DEFAULT)), resultBits);
        assert 0 < resultBits && resultBits <= PrimitiveStamp.getBits(input.stamp(NodeView.DEFAULT)) : resultBits;
    }

    public NarrowNode(ValueNode input, int inputBits, int resultBits) {
        super(TYPE, BinaryArithmeticNode.getArithmeticOpTable(input).getNarrow(), inputBits, resultBits, input);
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
                long valueMayBeSet = integerStamp.mayBeSet();
                if ((valueMayBeSet & CodeUtil.mask(bits)) == valueMayBeSet) {
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
            /*
             * We may use signed stamps to represent unsigned integers. This narrow preserves order
             * if it is signed lossless and is being compared to another narrow that is signed
             * lossless, or if it is unsigned lossless and the other narrow is also unsigned
             * lossless. We don't have access to the other narrow here, so we must make a
             * conservative choice. We can rely on the fact that the same computation will be
             * performed on the other narrow, so it will make the same choice.
             *
             * Most Java values are signed, so we expect the signed case to be more relevant for
             * equals comparison. In contrast, the unsigned case should be more relevant for
             * unsigned less than comparisons.
             */
            case EQ:
                return isSignedLossless();
            case BT:
                return isUnsignedLossless();
            default:
                throw GraalError.shouldNotReachHere("Unsupported canonical condition."); // ExcludeFromJacocoGeneratedReport
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
                    return new ZeroExtendNode(other.getValue(), other.getInputBits(), getResultBits());
                }
            }
        } else if (forValue instanceof AndNode) {
            AndNode andNode = (AndNode) forValue;
            Stamp xStamp = andNode.getX().stamp(view);
            Stamp yStamp = andNode.getY().stamp(view);
            if (xStamp instanceof IntegerStamp && yStamp instanceof IntegerStamp) {
                long relevantMask = CodeUtil.mask(this.getResultBits());
                if ((relevantMask & ((IntegerStamp) yStamp).mustBeSet()) == relevantMask) {
                    return create(andNode.getX(), this.getResultBits(), view);
                } else if ((relevantMask & ((IntegerStamp) xStamp).mustBeSet()) == relevantMask) {
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
