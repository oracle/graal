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
import jdk.graal.compiler.vector.nodes.ShiftableVectorNode;
import jdk.graal.compiler.vector.nodes.SimdifyableVectorProducer;
import jdk.graal.compiler.vector.nodes.VectorNode;
import jdk.graal.compiler.vector.nodes.type.Vector.BooleanVector;
import jdk.graal.compiler.vector.nodes.type.Vector.ByteVector;
import jdk.graal.compiler.vector.nodes.type.Vector.CharVector;
import jdk.graal.compiler.vector.nodes.type.Vector.DoubleVector;
import jdk.graal.compiler.vector.nodes.type.Vector.FloatVector;
import jdk.graal.compiler.vector.nodes.type.Vector.IntVector;
import jdk.graal.compiler.vector.nodes.type.Vector.LongVector;
import jdk.graal.compiler.vector.nodes.type.Vector.ObjectVector;
import jdk.graal.compiler.vector.nodes.type.Vector.ShortVector;
import jdk.graal.compiler.vector.nodes.type.VectorStamp;

import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.loop.InductionVariable.Direction;
import jdk.graal.compiler.vector.nodes.simd.SimdBroadcastNode;
import jdk.vm.ci.meta.ConstantReflectionProvider;

/**
 * Create a vector filled with copies of the value {@link FillVectorNode#element}.
 */
@NodeInfo
public class FillVectorNode extends AbstractVectorNode implements ShiftableVectorNode, SimdifyableVectorProducer {
    public static final NodeClass<FillVectorNode> TYPE = NodeClass.create(FillVectorNode.class);

    @Input ValueNode element;

    public FillVectorNode(ValueNode element) {
        super(TYPE, new VectorStamp(element.stamp(NodeView.DEFAULT)));
        this.element = element;
    }

    public ValueNode getElement() {
        return element;
    }

    @Override
    public VectorNode shift(ValueNode index, GuardingNode guard, FixedNode insertBefore, ConstantReflectionProvider constantReflection) {
        return this;
    }

    @Override
    public ValueNode simdify(int length, Direction consumerDirection) {
        if (length == 1) {
            return element;
        } else {
            return graph().unique(new SimdBroadcastNode(element, length));
        }
    }

    @Override
    public boolean inferStamp() {
        return updateStamp(new VectorStamp(element.stamp(NodeView.DEFAULT)));
    }

    @NodeIntrinsic
    public static native BooleanVector fill(boolean element);

    @NodeIntrinsic
    public static native ByteVector fill(byte element);

    @NodeIntrinsic
    public static native ShortVector fill(short element);

    @NodeIntrinsic
    public static native CharVector fill(char element);

    @NodeIntrinsic
    public static native IntVector fill(int element);

    @NodeIntrinsic
    public static native LongVector fill(long element);

    @NodeIntrinsic
    public static native FloatVector fill(float element);

    @NodeIntrinsic
    public static native DoubleVector fill(double element);

    @NodeIntrinsic
    public static native ObjectVector fill(Object element);
}
