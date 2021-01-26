/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.extended;

import static org.graalvm.compiler.nodeinfo.InputType.Guard;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_2;

import org.graalvm.compiler.core.common.type.AbstractPointerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.CompressionNode;
import org.graalvm.compiler.nodes.ImplicitNullCheckNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaConstant;

@NodeInfo(allowedUsageTypes = Guard, cycles = CYCLES_2, size = SIZE_2)
public final class NullCheckNode extends ImplicitNullCheckNode implements LIRLowerable, GuardingNode {

    public static final NodeClass<NullCheckNode> TYPE = NodeClass.create(NullCheckNode.class);
    @Input ValueNode object;

    public NullCheckNode(ValueNode object) {
        this(object, null, null);
    }

    public NullCheckNode(ValueNode object, JavaConstant deoptReasonAndAction, JavaConstant deoptSpeculation) {
        super(TYPE, StampFactory.forVoid());
        this.object = object;
        assert (deoptReasonAndAction == null) == (deoptSpeculation == null);
        this.deoptReasonAndAction = deoptReasonAndAction;
        this.deoptSpeculation = deoptSpeculation;
    }

    public static NullCheckNode create(ValueNode object, JavaConstant deoptReasonAndAction, JavaConstant deoptSpeculation) {
        // Try to uncompress a compressed object before applying null check.
        NullCheckNode nullCheck = tryUseUncompressedNullCheck(object);
        if (nullCheck != null) {
            return nullCheck;
        }
        return new NullCheckNode(object, deoptReasonAndAction, deoptSpeculation);
    }

    private static NullCheckNode tryUseUncompressedNullCheck(ValueNode value) {
        Stamp stamp = value.stamp(NodeView.DEFAULT);
        // Do nothing if the value is not a compressed pointer.
        if (!(stamp instanceof AbstractPointerStamp) || !((AbstractPointerStamp) stamp).isCompressed()) {
            return null;
        }

        // Copy an uncompressing node from one of the existed uncompressing usages.
        CompressionNode uncompressed = null;
        for (Node usage : value.usages()) {
            if (usage instanceof CompressionNode) {
                if (((CompressionNode) usage).getOp() == CompressionNode.CompressionOp.Uncompress) {
                    uncompressed = (CompressionNode) usage;
                    break;
                }
            }
        }
        // Do nothing if there is not an uncompressing usage. The uncompressing operation will be
        // handled at back-end.
        if (uncompressed == null) {
            return null;
        }

        assert uncompressed.getValue().equals(value);
        Graph graph = value.graph();
        CompressionNode compression = (CompressionNode) uncompressed.copyWithInputs(false);
        compression = graph.addOrUniqueWithInputs(compression);
        OffsetAddressNode address = graph.addOrUniqueWithInputs(OffsetAddressNode.create(compression));
        return new NullCheckNode(address);
    }

    public ValueNode getObject() {
        return object;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        generator.getLIRGeneratorTool().emitNullCheck(generator.operand(object), generator.state(this));
    }

    @Override
    public boolean canDeoptimize() {
        return true;
    }

    @NodeIntrinsic
    public static native void nullCheck(Object object);
}
