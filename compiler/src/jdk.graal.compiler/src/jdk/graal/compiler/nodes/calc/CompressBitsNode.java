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

package jdk.graal.compiler.nodes.calc;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_2;

import jdk.graal.compiler.core.common.type.ArithmeticOpTable;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.BinaryOp;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.BinaryOp.Compress;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

/**
 * Intrinsification for {@code Integer.compress}, {@code Long.compress}.
 */
@NodeInfo(cycles = CYCLES_2, size = SIZE_2)
public final class CompressBitsNode extends BinaryArithmeticNode<Compress> {

    public static final NodeClass<CompressBitsNode> TYPE = NodeClass.create(CompressBitsNode.class);

    public CompressBitsNode(ValueNode value, ValueNode mask) {
        super(TYPE, getArithmeticOpTable(value).getCompress(), value, mask);
    }

    @Override
    protected BinaryOp<Compress> getOp(ArithmeticOpTable table) {
        return table.getCompress();
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode value, ValueNode mask) {
        ValueNode ret = super.canonical(tool, value, mask);
        if (ret != this) {
            return ret;
        }

        JavaKind kind = value.getStackKind();
        GraalError.guarantee(kind == JavaKind.Int || kind == JavaKind.Long, "unexpected Java kind %s", kind);

        if (mask.isConstant()) {
            JavaConstant maskAsConstant = mask.asJavaConstant();

            GraalError.guarantee(!value.isConstant(), "should have been folded in super.canonical");
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
            return AndNode.create(new CompressBitsNode(mask, mask), ((ExpandBitsNode) value).getX(), NodeView.DEFAULT);
        }

        return this;
    }

    @Override
    public void generate(NodeLIRBuilderTool builder, ArithmeticLIRGeneratorTool gen) {
        builder.setResult(this, gen.emitIntegerCompress(builder.operand(getX()), builder.operand(getY())));
    }
}
