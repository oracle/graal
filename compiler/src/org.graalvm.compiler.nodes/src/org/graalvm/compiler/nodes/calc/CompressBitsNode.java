/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_2;

import org.graalvm.compiler.core.common.type.IntegerStamp;
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

import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

/**
 * Intrinsification for {@link Integer#compress}, {@link Long#compress}.
 */
@NodeInfo(cycles = CYCLES_2, size = SIZE_2)
public final class CompressBitsNode extends BinaryNode implements ArithmeticLIRLowerable {

    public static final NodeClass<CompressBitsNode> TYPE = NodeClass.create(CompressBitsNode.class);

    public CompressBitsNode(ValueNode value, ValueNode mask) {
        super(TYPE, computeStamp((IntegerStamp) value.stamp(NodeView.DEFAULT), (IntegerStamp) mask.stamp(NodeView.DEFAULT)), value, mask);
    }

    static final long INT_MASK = CodeUtil.mask(32);
    static final long LONG_MASK = CodeUtil.mask(64);

    public static Stamp computeStamp(IntegerStamp valueStamp, IntegerStamp maskStamp) {
        if (valueStamp.getStackKind() == JavaKind.Int) {
            if (maskStamp.upMask() == INT_MASK && valueStamp.canBeNegative()) {
                // compress result can be negative
                return IntegerStamp.create(32,
                                valueStamp.lowerBound(), // compress(value, INT_MASK)
                                CodeUtil.maxValue(32));
            }
            // compress result will always be positive
            return IntegerStamp.create(32,
                            Integer.compress((int) valueStamp.downMask(), (int) maskStamp.downMask()) & INT_MASK,
                            Integer.compress((int) valueStamp.upMask(), (int) maskStamp.upMask()) & INT_MASK,
                            0,
                            Integer.compress((int) INT_MASK, (int) maskStamp.upMask()) & INT_MASK);
        } else {
            GraalError.guarantee(valueStamp.getStackKind() == JavaKind.Long, "unexpected Java kind %s", valueStamp.getStackKind());
            if (maskStamp.upMask() == LONG_MASK && valueStamp.canBeNegative()) {
                // compress result can be negative
                return IntegerStamp.create(64,
                                valueStamp.lowerBound(), // compress(value, LONG_MASK)
                                CodeUtil.maxValue(64));
            }
            // compress result will always be positive
            return IntegerStamp.create(64,
                            Long.compress(valueStamp.downMask(), maskStamp.downMask()),
                            Long.compress(valueStamp.upMask(), maskStamp.upMask()),
                            0,
                            Long.compress(LONG_MASK, maskStamp.upMask()));
        }
    }

    @Override
    public Stamp foldStamp(Stamp valueStamp, Stamp maskStamp) {
        Stamp newStamp = computeStamp((IntegerStamp) valueStamp, (IntegerStamp) maskStamp);
        if (newStamp.join(stamp).equals(newStamp)) {
            return newStamp;
        }
        return stamp;
    }

    @Override
    public Node canonical(CanonicalizerTool tool, ValueNode value, ValueNode mask) {
        JavaKind kind = value.getStackKind();
        GraalError.guarantee(kind == JavaKind.Int || kind == JavaKind.Long, "unexpected Java kind %s", kind);

        if (mask.isConstant()) {
            JavaConstant maskAsConstant = mask.asJavaConstant();

            if (value.isConstant()) {
                JavaConstant valueAsConstant = value.asJavaConstant();
                if (kind == JavaKind.Int) {
                    return ConstantNode.forInt(Integer.compress(valueAsConstant.asInt(), maskAsConstant.asInt()));
                } else {
                    return ConstantNode.forLong(Long.compress(valueAsConstant.asLong(), maskAsConstant.asLong()));
                }
            } else {
                if (kind == JavaKind.Int) {
                    int maskValue = maskAsConstant.asInt();
                    if (maskValue == 0) {
                        // compress(x, 0) == 0
                        return ConstantNode.forInt(0);
                    } else if (maskValue == -1) {
                        // compress(x, -1) == x
                        return value;
                    }
                } else {
                    long maskValue = maskAsConstant.asLong();
                    if (maskValue == 0L) {
                        // compress(x, 0) == 0
                        return ConstantNode.forLong(0L);
                    } else if (maskValue == -1L) {
                        // compress(x, -1) == x
                        return value;
                    }
                }
            }
        }

        if (mask instanceof LeftShiftNode && ((LeftShiftNode) mask).getX().isConstant()) {
            if (kind == JavaKind.Int) {
                int maskX = ((LeftShiftNode) mask).getX().asJavaConstant().asInt();
                if (maskX == 1) {
                    // compress(x, 1 << n) == (x >> n & 1)
                    return AndNode.create(RightShiftNode.create(value, ((LeftShiftNode) mask).getY(), NodeView.DEFAULT), ConstantNode.forInt(1), NodeView.DEFAULT);
                } else if (maskX == -1) {
                    // compress(x, -1 << n) == x >>> n
                    return UnsignedRightShiftNode.create(value, ((LeftShiftNode) mask).getY(), NodeView.DEFAULT);
                }
            } else {
                long maskX = ((LeftShiftNode) mask).getX().asJavaConstant().asLong();
                if (maskX == 1L) {
                    // compress(x, 1 << n) == (x >> n & 1)
                    return AndNode.create(RightShiftNode.create(value, ((LeftShiftNode) mask).getY(), NodeView.DEFAULT), ConstantNode.forLong(1), NodeView.DEFAULT);
                } else if (maskX == -1L) {
                    // compress(x, -1 << n) == x >>> n
                    return UnsignedRightShiftNode.create(value, ((LeftShiftNode) mask).getY(), NodeView.DEFAULT);
                }
            }
        }
        // compress(expand(x, m), m) == x & compress(m, m)
        if (value instanceof ExpandBitsNode && ((ExpandBitsNode) value).getY() == mask) {
            return AndNode.create(new CompressBitsNode(mask, mask), value, NodeView.DEFAULT);
        }

        return this;
    }

    @Override
    public void generate(NodeLIRBuilderTool builder, ArithmeticLIRGeneratorTool gen) {
        builder.setResult(this, gen.emitIntegerCompress(builder.operand(getX()), builder.operand(getY())));
    }
}
