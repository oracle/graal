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
package jdk.graal.compiler.vector.nodes.op;

import java.util.Arrays;
import java.util.List;

import jdk.graal.compiler.vector.nodes.AbstractVectorNode;
import jdk.graal.compiler.vector.nodes.ShiftableVectorNode;
import jdk.graal.compiler.vector.nodes.SimplifiableVectorNode;
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

import jdk.graal.compiler.core.common.calc.CanonicalCondition;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.BinaryArithmeticNode;
import jdk.graal.compiler.nodes.calc.CompareNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.vm.ci.meta.ConstantReflectionProvider;

/**
 * Concatenate two vectors. Creating instances of this class, can potentially result in exponential
 * duplication of vector map/fold operations (see comments in VectorMaterializationPhase).
 */
@NodeInfo
public final class ConcatVectorNode extends AbstractVectorNode implements SimplifiableVectorNode, ShiftableVectorNode, VectorOperation, Canonicalizable {
    public static final NodeClass<ConcatVectorNode> TYPE = NodeClass.create(ConcatVectorNode.class);

    @Input ValueNode x;
    @Input ValueNode xLength;

    @Input ValueNode y;
    @Input ValueNode yLength;

    public ConcatVectorNode(ValueNode x, ValueNode xLength, ValueNode y, ValueNode yLength) {
        super(TYPE, x.stamp(NodeView.DEFAULT).meet(y.stamp(NodeView.DEFAULT)));
        this.x = x;
        this.xLength = xLength;
        this.y = y;
        this.yLength = yLength;
    }

    public ConcatVectorNode(VectorNode x, ValueNode xLength, VectorNode y, ValueNode yLength) {
        this((ValueNode) x, xLength, (ValueNode) y, yLength);
    }

    public VectorNode x() {
        return (VectorNode) x;
    }

    public ValueNode getXLength() {
        return xLength;
    }

    public VectorNode y() {
        return (VectorNode) y;
    }

    public ValueNode getYLength() {
        return yLength;
    }

    private void setX(VectorNode newX) {
        updateUsages(x, newX.asNode());
        x = newX.asNode();
    }

    private void setY(VectorNode newY) {
        updateUsages(y, newY.asNode());
        y = newY.asNode();
    }

    @Override
    public List<ValueNode> getVectorInputs() {
        return Arrays.asList(x, y);
    }

    @Override
    public VectorNode shift(ValueNode index, GuardingNode guard, FixedNode insertBefore, ConstantReflectionProvider constantReflection) {
        ValueNode zero = ConstantNode.forIntegerStamp(index.stamp(NodeView.DEFAULT), 0, graph());

        // xStart = Math.min(xLength, index);
        LogicNode startOutsideX = CompareNode.createCompareNode(graph(), CanonicalCondition.BT, xLength, index, constantReflection, NodeView.DEFAULT);
        ValueNode xStart = graph().unique(new ConditionalNode(startOutsideX, xLength, index));

        // xRest = x.length - xStart;
        ValueNode xRest = BinaryArithmeticNode.sub(graph(), xLength, xStart, NodeView.DEFAULT);

        // xShift = shift(x, xStart);
        VectorNode xShift = AbstractVectorNode.shift(x(), xStart, insertBefore, constantReflection);

        // yStart = startOutsideX ? index - x.length : 0
        ValueNode yStart = graph().unique(new ConditionalNode(startOutsideX, BinaryArithmeticNode.sub(graph(), index, xLength, NodeView.DEFAULT), zero));

        // yRest = y.length - yStart
        ValueNode yRest = BinaryArithmeticNode.sub(graph(), yLength, yStart, NodeView.DEFAULT);

        // yShift = shift(y, yStart);
        VectorNode yShift = AbstractVectorNode.shift(y(), yStart, insertBefore, constantReflection);

        return graph().unique(new ConcatVectorNode(xShift, xRest, yShift, yRest));
    }

    @Override
    public VectorNode simplify(VectorSimplifier simplifier) {
        VectorNode newX = simplifier.simplifyLengthHint(x(), xLength);
        if (xLength == simplifier.getLengthHint()) {
            return newX;
        } else {
            setX(newX);
            setY(simplifier.simplifyLengthHint(y(), yLength));
            return this;
        }
    }

    private static boolean isEmptyVectorLength(ValueNode length) {
        IntegerStamp lengthStamp = (IntegerStamp) length.stamp(NodeView.DEFAULT);
        return lengthStamp.upperBound() <= 0;
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        if (isEmptyVectorLength(xLength)) {
            return y;
        } else if (isEmptyVectorLength(yLength)) {
            return x;
        } else {
            return this;
        }
    }

    @Override
    public boolean inferStamp() {
        return updateStamp(x.stamp(NodeView.DEFAULT).meet(y.stamp(NodeView.DEFAULT)));
    }

    @NodeIntrinsic
    public static native BooleanVector concat(BooleanVector x, int xLength, BooleanVector y, int yLength);

    @NodeIntrinsic
    public static native ByteVector concat(ByteVector x, int xLength, ByteVector y, int yLength);

    @NodeIntrinsic
    public static native ShortVector concat(ShortVector x, int xLength, ShortVector y, int yLength);

    @NodeIntrinsic
    public static native CharVector concat(CharVector x, int xLength, CharVector y, int yLength);

    @NodeIntrinsic
    public static native IntVector concat(IntVector x, int xLength, IntVector y, int yLength);

    @NodeIntrinsic
    public static native LongVector concat(LongVector x, int xLength, LongVector y, int yLength);

    @NodeIntrinsic
    public static native FloatVector concat(FloatVector x, int xLength, FloatVector y, int yLength);

    @NodeIntrinsic
    public static native DoubleVector concat(DoubleVector x, int xLength, DoubleVector y, int yLength);

    @NodeIntrinsic
    public static native ObjectVector concat(ObjectVector x, int xLength, ObjectVector y, int yLength);
}
