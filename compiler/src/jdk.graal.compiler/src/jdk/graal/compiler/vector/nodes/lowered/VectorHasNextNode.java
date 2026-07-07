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

import static jdk.graal.compiler.nodeinfo.InputType.Association;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_UNKNOWN;

import jdk.graal.compiler.vector.nodes.consumer.LowerableVectorConsumer;
import jdk.graal.compiler.vector.nodes.consumer.VectorConsumer;
import jdk.graal.compiler.vector.nodes.lowered.PartialVectorConsumerNode.Consumer;
import jdk.graal.compiler.vector.nodes.lowered.iterator.VectorConsumerIterator;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.vm.ci.meta.JavaKind;

/**
 * Check if there are more elements to be consumed by a vector consumer. The optional {@link #limit}
 * parameter allows specifying a custom maximum number of elements to be consumed. This limit will
 * be clamped to the length of the vector consumer, so there is no need for an explicit bounds
 * check.
 */
//@formatter:off
@NodeInfo(cycles = CYCLES_UNKNOWN,
   cyclesRationale = "We cannot argue about vector nodes statically.",
   size = SIZE_UNKNOWN,
   sizeRationale = "We cannot argue about vector nodes statically.")
//@formatter:on
public final class VectorHasNextNode extends LogicNode {
    public static final NodeClass<VectorHasNextNode> TYPE = NodeClass.create(VectorHasNextNode.class);

    @Input(Association) ValueNode consumer;
    @Input ValueNode iterator;
    @Input ValueNode length;
    @OptionalInput ValueNode limit;

    public VectorHasNextNode(ValueNode consumer, ValueNode iterator, ValueNode length, ValueNode limit) {
        super(TYPE);
        this.consumer = consumer;
        this.iterator = iterator;
        this.length = length;
        this.limit = limit;
    }

    public VectorConsumer getConsumer() {
        return (VectorConsumer) consumer;
    }

    public ValueNode getConsumerOrProxy() {
        assert consumer instanceof VectorConsumer || consumer instanceof VectorConsumerProxyNode : consumer;
        return consumer;
    }

    public void lower(VectorLoweringTool tool) {
        assert consumer instanceof LowerableVectorConsumer : "vector consumer " + consumer + " is not lowerable";
        assert length.isConstant() : "vector hasNext " + this + " has non-constant length";
        VectorConsumerIterator it = tool.getIterator(iterator, (LowerableVectorConsumer) consumer);
        replaceAtUsagesAndDelete(it.hasNext(getConsumer(), length.asJavaConstant().asInt(), limit, tool.getConstantReflection()));
    }

    public int constantLength() {
        GraalError.guarantee(length.isJavaConstant(), "length must be a constant after snippet expansion is complete");
        return length.asJavaConstant().asInt();
    }

    @NodeIntrinsic(value = HasNextConditionalNode.class)
    public static native boolean hasNext(Consumer consumer, VectorConsumerIterator iterator, int length);

    @NodeIntrinsic(value = HasNextConditionalNode.class)
    public static native boolean hasNext(Consumer consumer, VectorConsumerIterator iterator, int length, long limit);

    @NodeInfo(cycles = CYCLES_UNKNOWN, size = SIZE_UNKNOWN)
    static final class HasNextConditionalNode extends FloatingNode implements Canonicalizable {

        public static final NodeClass<HasNextConditionalNode> TYPE = NodeClass.create(HasNextConditionalNode.class);
        @Input ValueNode consumer;
        @Input ValueNode iterator;
        @Input ValueNode length;
        @OptionalInput ValueNode limit;

        protected HasNextConditionalNode(ValueNode consumer, ValueNode iterator, ValueNode length) {
            this(consumer, iterator, length, null);
        }

        protected HasNextConditionalNode(ValueNode consumer, ValueNode iterator, ValueNode length, ValueNode limit) {
            super(TYPE, StampFactory.forKind(JavaKind.Boolean));
            this.consumer = consumer;
            this.iterator = iterator;
            this.length = length;
            this.limit = limit;
        }

        @Override
        public ValueNode canonical(CanonicalizerTool tool) {
            LogicNode condition = new VectorHasNextNode(consumer, iterator, length, limit);
            return new ConditionalNode(condition, ConstantNode.forInt(1), ConstantNode.forInt(0));
        }
    }
}
