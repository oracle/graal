/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.compiler.graal.nodes.calc;

import static jdk.compiler.graal.nodeinfo.NodeCycles.CYCLES_2;
import static jdk.compiler.graal.nodeinfo.NodeSize.SIZE_1;

import jdk.compiler.graal.core.common.type.FloatStamp;
import jdk.compiler.graal.core.common.type.Stamp;
import jdk.compiler.graal.debug.GraalError;
import jdk.compiler.graal.graph.Node;
import jdk.compiler.graal.graph.NodeClass;
import jdk.compiler.graal.lir.gen.ArithmeticLIRGeneratorTool;
import jdk.compiler.graal.nodeinfo.NodeInfo;
import jdk.compiler.graal.nodes.ConstantNode;
import jdk.compiler.graal.nodes.NodeView;
import jdk.compiler.graal.nodes.ValueNode;
import jdk.compiler.graal.nodes.spi.ArithmeticLIRLowerable;
import jdk.compiler.graal.nodes.spi.CanonicalizerTool;
import jdk.compiler.graal.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaKind;

@NodeInfo(cycles = CYCLES_2, size = SIZE_1)
public final class CopySignNode extends BinaryNode implements ArithmeticLIRLowerable {
    public static final NodeClass<CopySignNode> TYPE = NodeClass.create(CopySignNode.class);

    public CopySignNode(ValueNode magnitude, ValueNode sign) {
        super(TYPE, computeStamp(magnitude.stamp(NodeView.DEFAULT), sign.stamp(NodeView.DEFAULT)), magnitude, sign);
    }

    public static Stamp computeStamp(Stamp magnitude, Stamp sign) {
        FloatStamp magnitudeStamp = (FloatStamp) magnitude;
        FloatStamp signStamp = (FloatStamp) sign;
        if (magnitudeStamp.isNaN()) {
            return magnitude;
        }
        if (signStamp.isNonNaN()) {
            if (signStamp.lowerBound() > 0) {
                // the end result will be non-negative
                if (magnitudeStamp.lowerBound() > 0) {
                    // We know the entire range is above 0: leave it unchanged.
                    return magnitudeStamp;
                }
                if (magnitudeStamp.upperBound() < 0) {
                    // We know that the entire range is below 0
                    // flip [lower, upper] to [-upper, -lower]
                    return new FloatStamp(magnitudeStamp.getBits(), -magnitudeStamp.upperBound(), -magnitudeStamp.lowerBound(), magnitudeStamp.isNonNaN());
                }
                // We know lowerBound <= 0 and upperBound >= 0:
                // the new range is [0, Math.max(-lower, upper)]
                return new FloatStamp(magnitudeStamp.getBits(), 0,
                                Math.max(-magnitudeStamp.lowerBound(), magnitudeStamp.upperBound()), magnitudeStamp.isNonNaN());
            }
            if (signStamp.upperBound() < 0) {
                // the result will be non-positive
                if (magnitudeStamp.upperBound() < 0) {
                    // We know the entire range is below 0: leave it unchanged.
                    return magnitudeStamp;
                }
                if (magnitudeStamp.lowerBound() > 0) {
                    // We know that the entire range is above 0
                    // flip [lower, upper] to [-upper,-lower]
                    return new FloatStamp(magnitudeStamp.getBits(), -magnitudeStamp.upperBound(), -magnitudeStamp.lowerBound(), magnitudeStamp.isNonNaN());
                }
                // We know lowerBound <= 0 and upperBound >= 0
                // the new range is [Math.min(lower, -upper), 0]
                return new FloatStamp(magnitudeStamp.getBits(), Math.min(magnitudeStamp.lowerBound(), -magnitudeStamp.upperBound()),
                                0, magnitudeStamp.isNonNaN());
            }
        }
        /*
         * We have no information on whether the range will be flipped or not. Hence, we have to
         * expand the result to be the union of [lower, upper] and [-upper, -lower].
         */
        return new FloatStamp(magnitudeStamp.getBits(), Math.min(magnitudeStamp.lowerBound(), -magnitudeStamp.upperBound()), Math.max(-magnitudeStamp.lowerBound(), magnitudeStamp.upperBound()),
                        magnitudeStamp.isNonNaN());
    }

    @Override
    public Stamp foldStamp(Stamp stampX, Stamp stampY) {
        return computeStamp(stampX, stampY);
    }

    private static Node canonicalHelper(ValueNode forX, float yValue) {
        if (forX.isJavaConstant()) {
            return ConstantNode.forFloat(Math.copySign(forX.asJavaConstant().asFloat(), yValue));
        } else {
            ValueNode result = OrNode.create(
                            AndNode.create(ReinterpretNode.create(JavaKind.Int, forX, NodeView.DEFAULT),
                                            ConstantNode.forInt(0x7FFFFFFF),
                                            NodeView.DEFAULT),
                            ConstantNode.forInt(Float.floatToIntBits(yValue) & 0x80000000),
                            NodeView.DEFAULT);
            return ReinterpretNode.create(JavaKind.Float, result, NodeView.DEFAULT);
        }
    }

    private static Node canonicalHelper(ValueNode forX, double yValue) {
        if (forX.isJavaConstant()) {
            return ConstantNode.forDouble(Math.copySign(forX.asJavaConstant().asDouble(), yValue));
        } else {
            ValueNode result = OrNode.create(
                            AndNode.create(ReinterpretNode.create(JavaKind.Long, forX, NodeView.DEFAULT),
                                            ConstantNode.forLong(0x7FFFFFFF_FFFFFFFFL),
                                            NodeView.DEFAULT),
                            ConstantNode.forLong(Double.doubleToLongBits(yValue) & 0x80000000_00000000L),
                            NodeView.DEFAULT);
            return ReinterpretNode.create(JavaKind.Double, result, NodeView.DEFAULT);
        }
    }

    @Override
    public Node canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        FloatStamp floatStampY = (FloatStamp) forY.stamp(NodeView.DEFAULT);
        switch (forX.getStackKind()) {
            case Float:
                if (forY.isJavaConstant()) {
                    return canonicalHelper(forX, forY.asJavaConstant().asFloat());
                }
                if (floatStampY.isNonNaN()) {
                    if (floatStampY.lowerBound() > 0) {
                        // always positive
                        return canonicalHelper(forX, 1.0F);
                    } else if (floatStampY.upperBound() < 0) {
                        // always negative
                        return canonicalHelper(forX, -1.0F);
                    }
                }
                break;
            case Double:
                if (forY.isJavaConstant()) {
                    return canonicalHelper(forX, forY.asJavaConstant().asDouble());
                }
                if (floatStampY.isNonNaN()) {
                    if (floatStampY.lowerBound() > 0) {
                        // always positive
                        return canonicalHelper(forX, 1.0D);
                    } else if (floatStampY.upperBound() < 0) {
                        // always negative
                        return canonicalHelper(forX, -1.0D);
                    }
                }
                break;
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(forX.getStackKind()); // ExcludeFromJacocoGeneratedReport
        }
        return this;
    }

    @Override
    public void generate(NodeLIRBuilderTool nodeValueMap, ArithmeticLIRGeneratorTool gen) {
        nodeValueMap.setResult(this, gen.emitMathCopySign(nodeValueMap.operand(getX()), nodeValueMap.operand(getY())));
    }
}
