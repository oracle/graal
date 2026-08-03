/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.extended.AnchoringNode;
import jdk.graal.compiler.vector.nodes.LowerableVectorNode;
import jdk.graal.compiler.vector.nodes.VectorNode;
import jdk.graal.compiler.vector.nodes.consumer.LowerableVectorConsumer;
import jdk.graal.compiler.vector.nodes.consumer.VectorConsumer;
import jdk.graal.compiler.vector.nodes.lowered.PartialVectorConsumerNode.Consumer;
import jdk.graal.compiler.vector.nodes.lowered.iterator.DefaultVectorIterator;
import jdk.graal.compiler.vector.nodes.lowered.iterator.VectorConsumerIterator;
import jdk.graal.compiler.vector.nodes.lowered.iterator.VectorIterator;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaKind;

//@formatter:off
@NodeInfo(cycles = CYCLES_UNKNOWN,
     cyclesRationale = "We cannot argue about vector nodes statically.",
     size = SIZE_UNKNOWN,
     sizeRationale = "We cannot argue about vector nodes statically.")
//@formatter:on
public final class VectorInitialIteratorNode extends FloatingNode implements VectorIteratorNode {

    public static final NodeClass<VectorInitialIteratorNode> TYPE = NodeClass.create(VectorInitialIteratorNode.class);
    @Input(Association) ValueNode consumer;

    protected VectorConsumerIterator loweredIterator;

    public VectorInitialIteratorNode(VectorConsumer consumer) {
        this(consumer.asNode());
    }

    public VectorInitialIteratorNode(ValueNode consumer) {
        super(TYPE, StampFactory.empty(JavaKind.Object));
        this.consumer = consumer;
    }

    @Override
    public VectorConsumerIterator lower(VectorLoweringTool tool) {
        if (loweredIterator == null) {
            assert consumer instanceof LowerableVectorConsumer : "vector consumer " + consumer + " not lowerable";
            loweredIterator = ((LowerableVectorConsumer) consumer).createInitialIterator(tool.getTarget());
            clearInputs();
        }
        return loweredIterator;
    }

    @Override
    public int getStepLength() {
        return 0;
    }

    public static VectorIterator createInitialIterator(VectorNode vector, AnchoringNode anchor, TargetDescription target) {
        if (vector instanceof LowerableVectorNode) {
            return ((LowerableVectorNode) vector).createInitialIterator(anchor, target);
        } else {
            return new DefaultVectorIterator();
        }
    }

    public static VectorIterator createPhiIterator(AbstractMergeNode merge, VectorNode vector, AnchoringNode anchor, TargetDescription target) {
        if (vector instanceof LowerableVectorNode) {
            return ((LowerableVectorNode) vector).createPhiIterator(merge, anchor, target);
        } else {
            return new DefaultVectorIterator();
        }
    }

    @NodeIntrinsic
    public static native VectorConsumerIterator iterator(Consumer consumer);
}
