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
package org.graalvm.compiler.nodes.calc;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_1;

import org.graalvm.compiler.core.common.type.FloatStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.ArithmeticLIRLowerable;
import org.graalvm.compiler.nodes.spi.CanonicalizerTool;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaKind;

@NodeInfo(cycles = CYCLES_2, size = SIZE_1)
public final class CopySignNode extends BinaryNode implements ArithmeticLIRLowerable {
    public static final NodeClass<CopySignNode> TYPE = NodeClass.create(CopySignNode.class);

    public CopySignNode(ValueNode magnitude, ValueNode sign) {
        super(TYPE, computeStamp(magnitude.stamp(NodeView.DEFAULT), sign.stamp(NodeView.DEFAULT)), magnitude, sign);
    }

    public static Stamp computeStamp(Stamp stampX, Stamp stampY) {
        FloatStamp floatStampX = (FloatStamp) stampX;
        FloatStamp floatStampY = (FloatStamp) stampY;
        if (floatStampX.isNaN()) {
            return stampX;
        }
        if (floatStampY.isNonNaN()) {
            if (floatStampY.lowerBound() > 0) {
                if (floatStampX.lowerBound() > 0) {
                    return floatStampX;
                }
                if (floatStampX.upperBound() < 0) {
                    return new FloatStamp(floatStampX.getBits(), -floatStampX.upperBound(), -floatStampX.lowerBound(), floatStampX.isNonNaN());
                }
                return new FloatStamp(floatStampX.getBits(), Math.min(-floatStampX.lowerBound(), floatStampX.upperBound()),
                                Math.max(-floatStampX.lowerBound(), floatStampX.upperBound()), floatStampX.isNonNaN());
            }
            if (floatStampY.upperBound() < 0) {
                if (floatStampX.upperBound() < 0) {
                    return floatStampX;
                }
                if (floatStampX.lowerBound() > 0) {
                    return new FloatStamp(floatStampX.getBits(), -floatStampX.upperBound(), -floatStampX.lowerBound(), floatStampX.isNonNaN());
                }
                return new FloatStamp(floatStampX.getBits(), Math.min(floatStampX.lowerBound(), -floatStampX.upperBound()),
                                Math.max(floatStampX.lowerBound(), -floatStampX.upperBound()), floatStampX.isNonNaN());
            }
        }
        return new FloatStamp(floatStampX.getBits(), Math.min(floatStampX.lowerBound(), -floatStampX.upperBound()), Math.max(-floatStampX.lowerBound(), floatStampX.upperBound()),
                        floatStampX.isNonNaN());
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
                throw GraalError.shouldNotReachHere();
        }
        return this;
    }

    @Override
    public void generate(NodeLIRBuilderTool nodeValueMap, ArithmeticLIRGeneratorTool gen) {
        nodeValueMap.setResult(this, gen.emitMathCopySign(nodeValueMap.operand(getX()), nodeValueMap.operand(getY())));
    }
}
