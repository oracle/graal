/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.ByteOrder;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;

import jdk.vm.ci.meta.JavaKind;

/**
 * Produces the platform dependent first or second half of a long or double value as an int.
 */
@NodeInfo(cycles = NodeCycles.CYCLES_2)
public final class UnpackEndianHalfNode extends UnaryNode implements Lowerable {
    public static final NodeClass<UnpackEndianHalfNode> TYPE = NodeClass.create(UnpackEndianHalfNode.class);

    private final boolean firstHalf;

    protected UnpackEndianHalfNode(ValueNode value, boolean firstHalf) {
        super(TYPE, StampFactory.forKind(JavaKind.Int), value);
        assert value.getStackKind() == JavaKind.Double || value.getStackKind() == JavaKind.Long : "unexpected kind " + value.getStackKind();
        this.firstHalf = firstHalf;
    }

    @SuppressWarnings("unused")
    public static ValueNode create(ValueNode value, boolean firstHalf, NodeView view) {
        if (value.isConstant() && value.asConstant().isDefaultForKind()) {
            return ConstantNode.defaultForKind(JavaKind.Int);
        }
        return new UnpackEndianHalfNode(value, firstHalf);
    }

    public boolean isFirstHalf() {
        return firstHalf;
    }

    @Override
    public Node canonical(CanonicalizerTool tool, ValueNode forValue) {
        if (forValue.isDefaultConstant()) {
            return ConstantNode.defaultForKind(stamp.getStackKind());
        }
        return this;
    }

    public void lower(ByteOrder byteOrder) {
        ValueNode result = value;
        if (value.getStackKind() == JavaKind.Double) {
            result = graph().unique(new ReinterpretNode(JavaKind.Long, value));
        }
        if ((byteOrder == ByteOrder.BIG_ENDIAN) == firstHalf) {
            result = graph().unique(new UnsignedRightShiftNode(result, ConstantNode.forInt(32, graph())));
        }
        result = IntegerConvertNode.convert(result, StampFactory.forKind(JavaKind.Int), graph(), NodeView.DEFAULT);
        replaceAtUsagesAndDelete(result);
    }
}
