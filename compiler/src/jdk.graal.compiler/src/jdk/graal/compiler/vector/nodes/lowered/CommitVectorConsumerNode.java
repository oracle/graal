/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.nodeinfo.InputType.Association;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_UNKNOWN;

import jdk.graal.compiler.vector.nodes.consumer.LowerableVectorConsumer;
import jdk.graal.compiler.vector.nodes.consumer.VectorConsumer;
import jdk.graal.compiler.vector.nodes.lowered.PartialVectorConsumerNode.Consumer;
import jdk.graal.compiler.vector.nodes.lowered.iterator.VectorConsumerIterator;

import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;

/**
 * Responsible for lowering part of a vector consumer's work into a loop. For a
 * {@link VectorConsumerIterator} to produce a single lowered chunk of work, the corresponding
 * {@link PartialVectorConsumerNode} must be a (transitive) usage of a
 * {@link CommitVectorConsumerNode}.
 */
//@formatter:off
@NodeInfo(cycles = CYCLES_UNKNOWN,
   cyclesRationale = "We cannot argue about vector nodes statically.",
   size = SIZE_UNKNOWN,
   sizeRationale = "We cannot argue about vector nodes statically.")
//@formatter:on
public class CommitVectorConsumerNode extends FixedWithNextNode {
    public static final NodeClass<CommitVectorConsumerNode> TYPE = NodeClass.create(CommitVectorConsumerNode.class);

    @Input(Association) ValueNode consumer;
    @Input ValueNode iterator;

    public CommitVectorConsumerNode(ValueNode consumer, ValueNode iterator) {
        this(TYPE, consumer, iterator);
    }

    protected CommitVectorConsumerNode(NodeClass<? extends CommitVectorConsumerNode> nodeClass, ValueNode consumer, ValueNode iterator) {
        super(nodeClass, consumer.stamp(NodeView.DEFAULT));
        this.consumer = consumer;
        this.iterator = iterator;
    }

    public VectorConsumer getConsumer() {
        return (VectorConsumer) consumer;
    }

    public void setConsumer(VectorConsumer consumer) {
        ValueNode consumerValue = consumer == null ? null : consumer.asNode();
        updateUsages(this.consumer, consumerValue);
        this.consumer = consumerValue;
    }

    public ValueNode getIterator() {
        return iterator;
    }

    public void setIterator(ValueNode iterator) {
        updateUsages(this.iterator, iterator);
        this.iterator = iterator;
    }

    @Override
    public boolean inferStamp() {
        return updateStamp(consumer.stamp(NodeView.DEFAULT));
    }

    @Override
    public boolean isAllowedUsageType(InputType type) {
        return consumer.isAllowedUsageType(type);
    }

    public void lower(VectorLoweringTool tool) {
        assert consumer instanceof LowerableVectorConsumer : "vector consumer " + consumer + " is not lowerable";
        VectorConsumerIterator it = tool.getIterator(iterator, (LowerableVectorConsumer) consumer);
        tool.constructGraph((LowerableVectorConsumer) consumer);
        cleanup(it);
    }

    @SuppressWarnings("unused")
    protected void cleanup(VectorConsumerIterator it) {
        graph().removeFixed(this);
    }

    @NodeIntrinsic
    public static native Object commit(Consumer consumer, VectorConsumerIterator iterator);
}
