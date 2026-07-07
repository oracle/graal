/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
import jdk.graal.compiler.vector.nodes.lowered.iterator.VectorConsumerIterator;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.vm.ci.meta.JavaKind;

/**
 * Do part of the work of a vector consumer.
 */
//@formatter:off
@NodeInfo(cycles = CYCLES_UNKNOWN,
          cyclesRationale = "We cannot argue about vector nodes statically.",
          size = SIZE_UNKNOWN,
          sizeRationale = "We cannot argue about vector nodes statically.")
//@formatter:on
public final class PartialVectorConsumerNode extends FixedWithNextNode implements VectorIteratorNode, Canonicalizable {
    public static final NodeClass<PartialVectorConsumerNode> TYPE = NodeClass.create(PartialVectorConsumerNode.class);

    @Input(Association) ValueNode consumer;
    @Input ValueNode iterator;
    @Input ValueNode stepLength;

    protected VectorConsumerIterator loweredIterator;

    public PartialVectorConsumerNode(VectorConsumer consumer, ValueNode iterator, ValueNode stepLength) {
        this(consumer.asNode(), iterator, stepLength);
    }

    public PartialVectorConsumerNode(ValueNode consumer, ValueNode iterator, ValueNode stepLength) {
        super(TYPE, StampFactory.empty(JavaKind.Object));
        this.consumer = consumer;
        this.iterator = iterator;
        this.stepLength = stepLength;
    }

    public VectorConsumer getConsumer() {
        return (VectorConsumer) consumer;
    }

    public ValueNode getIterator() {
        return iterator;
    }

    @Override
    public int getStepLength() {
        GraalError.guarantee(stepLength.isJavaConstant(), "step length was not reduced to a constant");
        int value = stepLength.asJavaConstant().asInt();
        assert NumUtil.assertNonNegativeInt(value);
        return value;
    }

    @Override
    @SuppressWarnings("try")
    public VectorConsumerIterator lower(VectorLoweringTool tool) {
        if (loweredIterator == null) {
            try (DebugCloseable position = getConsumer().asNode().withNodeSourcePosition()) {
                VectorConsumerIterator it = tool.getIterator(getIterator(), (LowerableVectorConsumer) getConsumer());
                loweredIterator = it.next(getConsumer(), getStepLength(), this, tool.getConstantReflection());
            }
            clearConsumer();
            clearIterator();
        }
        return loweredIterator;
    }

    private void clearIterator() {
        updateUsages(iterator, null);
        iterator = null;
    }

    private void clearConsumer() {
        updateUsages(consumer, null);
        consumer = null;
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        if (tool.allUsagesAvailable() && usages().isEmpty()) {
            return null;
        } else {
            return this;
        }
    }

    public interface Consumer {
    }

    @NodeIntrinsic
    public static native VectorConsumerIterator consume(Consumer consumer, VectorConsumerIterator accumulator, int stepLength);

}
