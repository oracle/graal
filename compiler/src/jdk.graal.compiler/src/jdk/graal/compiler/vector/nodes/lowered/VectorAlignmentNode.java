/*
 * Copyright (c) 2014, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.nodes.lowered;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_UNKNOWN;

import jdk.graal.compiler.vector.nodes.consumer.LowerableVectorConsumer;
import jdk.graal.compiler.vector.nodes.consumer.VectorConsumer;
import jdk.graal.compiler.vector.nodes.lowered.PartialVectorConsumerNode.Consumer;
import jdk.graal.compiler.vector.nodes.lowered.iterator.VectorConsumerIterator;

import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.calc.IntegerConvertNode;
import jdk.vm.ci.meta.JavaKind;

/**
 * Computes the number of elements that need to be processed in the pre-loop in order for the access
 * to vector elements to be aligned.
 */
@NodeInfo(cycles = CYCLES_UNKNOWN, size = SIZE_UNKNOWN)
public final class VectorAlignmentNode extends FloatingNode {
    public static final NodeClass<VectorAlignmentNode> TYPE = NodeClass.create(VectorAlignmentNode.class);

    @Input(InputType.Association) ValueNode consumer;
    @Input ValueNode iterator;
    @Input ValueNode alignment;

    public VectorAlignmentNode(ValueNode consumer, ValueNode iterator, ValueNode alignment) {
        super(TYPE, IntegerStamp.create(JavaKind.Long.getBitCount(), 0L, ((IntegerStamp) alignment.stamp(NodeView.DEFAULT)).upperBound() - 1));
        this.consumer = consumer;
        this.iterator = iterator;
        this.alignment = alignment;
    }

    public VectorConsumer getConsumer() {
        return (VectorConsumer) consumer;
    }

    public ValueNode getIterator() {
        return iterator;
    }

    public void lower(VectorLoweringTool tool) {
        assert consumer instanceof LowerableVectorConsumer : "vector consumer " + consumer + " is not lowerable";
        assert alignment.isConstant() : "vector alignment node " + this + " has non-constant alignment";
        VectorConsumerIterator it = tool.getIterator(iterator, (LowerableVectorConsumer) consumer);
        ValueNode alignCount = it.getAlignCount(getConsumer(), alignment.asJavaConstant().asInt());
        replaceAtUsagesAndDelete(IntegerConvertNode.convert(alignCount, stamp(NodeView.DEFAULT), graph(), NodeView.DEFAULT));
    }

    @NodeIntrinsic
    public static native long getAlignCount(Consumer consumer, VectorConsumerIterator iterator, int alignment);
}
