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

package jdk.compiler.graal.nodes.calc;

import static jdk.compiler.graal.nodeinfo.NodeCycles.CYCLES_2;
import static jdk.compiler.graal.nodeinfo.NodeSize.SIZE_2;

import jdk.compiler.graal.core.common.type.ArithmeticOpTable;
import jdk.compiler.graal.core.common.type.ArithmeticOpTable.BinaryOp;
import jdk.compiler.graal.core.common.type.ArithmeticOpTable.BinaryOp.Expand;
import jdk.compiler.graal.debug.GraalError;
import jdk.compiler.graal.graph.NodeClass;
import jdk.compiler.graal.lir.gen.ArithmeticLIRGeneratorTool;
import jdk.compiler.graal.nodeinfo.NodeInfo;
import jdk.compiler.graal.nodes.ConstantNode;
import jdk.compiler.graal.nodes.NodeView;
import jdk.compiler.graal.nodes.ValueNode;
import jdk.compiler.graal.nodes.spi.CanonicalizerTool;
import jdk.compiler.graal.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

/**
 * Intrinsification for {@code Integer.expand}, {@code Long.expand}.
 */
@NodeInfo(cycles = CYCLES_2, size = SIZE_2)
public final class ExpandBitsNode extends BinaryArithmeticNode<Expand> {

    public static final NodeClass<ExpandBitsNode> TYPE = NodeClass.create(ExpandBitsNode.class);

    public ExpandBitsNode(ValueNode value, ValueNode mask) {
        super(TYPE, getArithmeticOpTable(value).getExpand(), value, mask);
    }

    @Override
    protected BinaryOp<Expand> getOp(ArithmeticOpTable table) {
        return table.getExpand();
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
            return AndNode.create(((CompressBitsNode) value).getX(), mask, NodeView.DEFAULT);
        }

        return this;
    }

    @Override
    public void generate(NodeLIRBuilderTool builder, ArithmeticLIRGeneratorTool gen) {
        builder.setResult(this, gen.emitIntegerExpand(builder.operand(getX()), builder.operand(getY())));
    }
}
