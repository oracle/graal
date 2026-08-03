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
package jdk.graal.compiler.vector.nodes.producer;

import jdk.graal.compiler.vector.nodes.AbstractVectorNode;
import jdk.graal.compiler.vector.nodes.LowerableVectorNode;
import jdk.graal.compiler.vector.nodes.ShiftableVectorNode;
import jdk.graal.compiler.vector.nodes.VectorNode;
import jdk.graal.compiler.vector.nodes.lowered.iterator.SequenceVectorIterator;
import jdk.graal.compiler.vector.nodes.lowered.iterator.VectorIterator;
import jdk.graal.compiler.vector.nodes.type.VectorStamp;

import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.BinaryArithmeticNode;
import jdk.graal.compiler.nodes.calc.IntegerConvertNode;
import jdk.graal.compiler.nodes.extended.AnchoringNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.loop.InductionVariable.Direction;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.ConstantReflectionProvider;

/**
 * Create a vector of the form v[i] = initial + i * stride.
 */
@NodeInfo
public final class SequenceVectorNode extends AbstractVectorNode implements ShiftableVectorNode, LowerableVectorNode {
    public static final NodeClass<SequenceVectorNode> TYPE = NodeClass.create(SequenceVectorNode.class);

    @Input ValueNode initial;
    @Input ValueNode stride;
    final Direction direction;

    private SequenceVectorNode(VectorStamp stamp, ValueNode initial, ValueNode stride, Direction direction) {
        super(TYPE, stamp);
        assert initial.stamp(NodeView.DEFAULT).isCompatible(stride.stamp(NodeView.DEFAULT)) : initial.stamp(NodeView.DEFAULT) + " vs " + stride.stamp(NodeView.DEFAULT);
        this.initial = initial;
        this.stride = stride;
        this.direction = direction;
    }

    public SequenceVectorNode(ValueNode inital, ValueNode stride, Direction direction) {
        this(new VectorStamp(inital.stamp(NodeView.DEFAULT).unrestricted()), inital, stride, direction);
    }

    public ValueNode getInitial() {
        return initial;
    }

    public ValueNode getStride() {
        return stride;
    }

    public Direction direction() {
        return direction;
    }

    @Override
    public VectorNode shift(ValueNode index, GuardingNode guard, FixedNode insertBefore, ConstantReflectionProvider constantReflection) {
        // The index is derived from the loop counter, which is usually int. But this sequence might
        // be long, so make sure types match.
        ValueNode convertedIndex = IntegerConvertNode.convert(index, initial.stamp(NodeView.DEFAULT), NodeView.DEFAULT);
        ValueNode newInitial = BinaryArithmeticNode.add(graph(), initial, BinaryArithmeticNode.mul(graph(), convertedIndex, stride, NodeView.DEFAULT), NodeView.DEFAULT);
        return graph().unique(new SequenceVectorNode(getVectorStamp(), newInitial, stride, direction));
    }

    @Override
    public VectorIterator createInitialIterator(AnchoringNode anchor, TargetDescription target) {
        return SequenceVectorIterator.createInitialIterator(this);
    }

    @Override
    public VectorIterator createPhiIterator(AbstractMergeNode merge, AnchoringNode anchor, TargetDescription target) {
        return SequenceVectorIterator.createPhiIterator(this, merge);
    }
}
