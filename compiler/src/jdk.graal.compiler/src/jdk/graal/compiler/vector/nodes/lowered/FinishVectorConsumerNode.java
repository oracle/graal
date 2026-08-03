/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.vector.nodes.lowered.PartialVectorConsumerNode.Consumer;
import jdk.graal.compiler.vector.nodes.lowered.iterator.VectorConsumerIterator;

import jdk.graal.compiler.graph.IterableNodeType;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ValueNode;

/**
 * Do the final work of a vector consumer, after iterating over the whole vector.
 */
//@formatter:off
@NodeInfo(cycles = CYCLES_UNKNOWN,
   cyclesRationale = "We cannot argue about vector nodes statically.",
   size = SIZE_UNKNOWN,
   sizeRationale = "We cannot argue about vector nodes statically.")
//@formatter:on
public final class FinishVectorConsumerNode extends CommitVectorConsumerNode implements IterableNodeType {
    public static final NodeClass<FinishVectorConsumerNode> TYPE = NodeClass.create(FinishVectorConsumerNode.class);

    /**
     * The number of elements (iterations of the original scalar loop's body) actually processed by
     * this consumer. This may be less than the consumer's length. In that case, other code (the
     * vector post loop) must process the remaining iterations.
     */
    @OptionalInput ValueNode consumedElements;

    public FinishVectorConsumerNode(ValueNode consumer, ValueNode iterator) {
        this(consumer, iterator, null);
    }

    public FinishVectorConsumerNode(ValueNode consumer, ValueNode iterator, ValueNode consumedElements) {
        super(TYPE, consumer, iterator);
        this.consumedElements = consumedElements;
    }

    public ValueNode consumedElements() {
        return consumedElements;
    }

    @Override
    protected void cleanup(VectorConsumerIterator it) {
        it.finishConsumer(this);
    }

    @NodeIntrinsic
    public static native Object finish(Consumer consumer, VectorConsumerIterator iterator);

    @NodeIntrinsic
    public static native Object finish(Consumer consumer, VectorConsumerIterator iterator, long consumedElements);
}
