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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

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

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

/**
 * Intrinsification for {@link Integer#expand}, {@link Long#expand}.
 */
@NodeInfo(cycles = CYCLES_2, size = SIZE_2)
public final class ExpandBitsNode extends BinaryNode implements ArithmeticLIRLowerable {

    public static final NodeClass<ExpandBitsNode> TYPE = NodeClass.create(ExpandBitsNode.class);

    public ExpandBitsNode(ValueNode value, ValueNode mask) {
        super(TYPE, computeStamp((IntegerStamp) mask.stamp(NodeView.DEFAULT)), value, mask);
    }

    public static Stamp computeStamp(IntegerStamp maskStamp) {
        return IntegerStamp.stampForMask(maskStamp.getBits(), 0, maskStamp.upMask());
    }

    @Override
    public Stamp foldStamp(Stamp valueStamp, Stamp maskStamp) {
        Stamp newStamp = computeStamp((IntegerStamp) valueStamp);
        if (newStamp.join(stamp).equals(newStamp)) {
            return newStamp;
        }
        return stamp;
    }

    private static int integerExpand(int i, int mask) {
        try {
            Method expand = Integer.class.getDeclaredMethod("expand", int.class, int.class);
            return (Integer) expand.invoke(null, i, mask);
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            throw GraalError.shouldNotReachHere(e, "Integer.expand is introduced in Java 19");
        }
    }

    private static long longExpand(long i, long mask) {
        try {
            Method expand = Long.class.getDeclaredMethod("expand", long.class, long.class);
            return (Long) expand.invoke(null, i, mask);
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            throw GraalError.shouldNotReachHere(e, "Long.expand is introduced in Java 19");
        }
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
                    return ConstantNode.forInt(integerExpand(valueAsConstant.asInt(), maskAsConstant.asInt()));
                } else {
                    return ConstantNode.forLong(longExpand(valueAsConstant.asLong(), maskAsConstant.asLong()));
                }
            } else {
                if (kind == JavaKind.Int) {
                    int maskValue = maskAsConstant.asInt();
                    if (maskValue == 0) {
                        // expand(x, 0) == 0
                        return mask;
                    } else if (maskValue == -1) {
                        // expand(x, -1) == x
                        return value;
                    }
                } else {
                    long maskValue = maskAsConstant.asLong();
                    if (maskValue == 0L) {
                        // expand(x, 0) == 0
                        return mask;
                    } else if (maskValue == -1L) {
                        // expand(x, -1) == x
                        return value;
                    }
                }
            }
        }
        // expand(-1, x) == x
        if (value.isConstant()) {
            if (kind == JavaKind.Int) {
                if (value.asJavaConstant().asInt() == -1) {
                    return mask;
                }
            } else {
                if (value.asJavaConstant().asLong() == -1L) {
                    return mask;
                }
            }
        }

        if (mask instanceof LeftShiftNode && ((LeftShiftNode) mask).getX().isConstant()) {
            if (kind == JavaKind.Int) {
                int maskX = ((LeftShiftNode) mask).getX().asJavaConstant().asInt();
                if (maskX == 1) {
                    // expand(x, 1 << n) == (x & 1) << n
                    return LeftShiftNode.create(AndNode.create(value, ConstantNode.forInt(1), NodeView.DEFAULT), ((LeftShiftNode) mask).getY(), NodeView.DEFAULT);
                } else if (maskX == -1) {
                    // expand(x, -1 << n) == x << n
                    return LeftShiftNode.create(value, ((LeftShiftNode) mask).getY(), NodeView.DEFAULT);
                }
            } else {
                long maskX = ((LeftShiftNode) mask).getX().asJavaConstant().asLong();
                if (maskX == 1L) {
                    // expand(x, 1 << n) == (x & 1) << n
                    return LeftShiftNode.create(AndNode.create(value, ConstantNode.forLong(1), NodeView.DEFAULT), ((LeftShiftNode) mask).getY(), NodeView.DEFAULT);
                } else if (maskX == -1L) {
                    // expand(x, -1 << n) == x << n
                    return LeftShiftNode.create(value, ((LeftShiftNode) mask).getY(), NodeView.DEFAULT);
                }
            }
        }
        // expand(compress(x, m), m) == x & m
        if (value instanceof CompressBitsNode && ((CompressBitsNode) value).getY() == mask) {
            return AndNode.create(value, mask, NodeView.DEFAULT);
        }

        return this;
    }

    @Override
    public void generate(NodeLIRBuilderTool builder, ArithmeticLIRGeneratorTool gen) {
        builder.setResult(this, gen.emitIntegerExpand(builder.operand(getX()), builder.operand(getY())));
    }
}
