/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.loop;

import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.util.UnsignedLong;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.BinaryArithmeticNode;
import jdk.graal.compiler.nodes.calc.IntegerConvertNode;
import jdk.graal.compiler.nodes.calc.NegateNode;
import jdk.graal.compiler.nodes.calc.SubNode;

public class BasicInductionVariable extends InductionVariable {

    protected final ValuePhiNode phi;
    protected final ValueNode init;
    protected ValueNode rawStride;
    protected BinaryArithmeticNode<?> op;

    public BasicInductionVariable(Loop loop, ValuePhiNode phi, ValueNode init, ValueNode rawStride, BinaryArithmeticNode<?> op) {
        super(loop);
        this.phi = phi;
        this.init = init;
        this.rawStride = rawStride;
        this.op = op;
    }

    @Override
    public InductionVariable duplicate() {
        return new BasicInductionVariable(loop, phi, init, rawStride, (BinaryArithmeticNode<?>) op.copyWithInputs(true));
    }

    @Override
    public InductionVariable duplicateWithNewInit(ValueNode newInit) {
        return new BasicInductionVariable(loop, phi, newInit, rawStride, (BinaryArithmeticNode<?>) op.copyWithInputs(true));
    }

    @Override
    public StructuredGraph graph() {
        return phi.graph();
    }

    public BinaryArithmeticNode<?> getOp() {
        return op;
    }

    public void setOp(BinaryArithmeticNode<?> newOp) {
        rawStride = newOp.getY();
        op = newOp;
    }

    @Override
    public Direction direction() {
        Stamp stamp = rawStride.stamp(NodeView.DEFAULT);
        if (stamp instanceof IntegerStamp) {
            IntegerStamp integerStamp = (IntegerStamp) stamp;
            Direction dir = null;
            if (integerStamp.isStrictlyPositive()) {
                dir = Direction.Up;
            } else if (integerStamp.isStrictlyNegative()) {
                dir = Direction.Down;
            }
            if (dir != null) {
                if (op instanceof AddNode) {
                    return dir;
                } else {
                    assert op instanceof SubNode : Assertions.errorMessage(op);
                    return dir.opposite();
                }
            }
        }
        return null;
    }

    @Override
    public ValuePhiNode valueNode() {
        return phi;
    }

    @Override
    public ValueNode initNode() {
        return init;
    }

    public ValueNode rawStride() {
        return rawStride;
    }

    @Override
    public ValueNode strideNode() {
        if (op instanceof AddNode) {
            return rawStride;
        }
        if (op instanceof SubNode) {
            return graph().addOrUniqueWithInputs(NegateNode.create(rawStride, NodeView.DEFAULT));
        }
        throw GraalError.shouldNotReachHereUnexpectedValue(op); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public boolean isConstantInit() {
        return init.isConstant();
    }

    @Override
    public boolean isConstantStride() {
        return rawStride.isConstant();
    }

    @Override
    public long constantInit() {
        return init.asJavaConstant().asLong();
    }

    @Override
    public long constantStride() {
        if (op instanceof AddNode) {
            return rawStride.asJavaConstant().asLong();
        }
        if (op instanceof SubNode) {
            return -rawStride.asJavaConstant().asLong();
        }
        throw GraalError.shouldNotReachHereUnexpectedValue(op); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public ValueNode extremumNode(boolean assumeLoopEntered, Stamp stamp) {
        return extremumNode(assumeLoopEntered, stamp, loop.counted.maxTripCountNode(assumeLoopEntered));
    }

    @Override
    public ValueNode extremumNode(boolean assumeLoopEntered, Stamp stamp, ValueNode maxTripCount) {
        Stamp fromStamp = phi.stamp(NodeView.DEFAULT);
        StructuredGraph graph = graph();
        ValueNode stride = strideNode();
        ValueNode initNode = this.initNode();
        if (!fromStamp.isCompatible(stamp)) {
            stride = IntegerConvertNode.convert(stride, stamp, graph(), NodeView.DEFAULT);
            initNode = IntegerConvertNode.convert(initNode, stamp, graph(), NodeView.DEFAULT);
        }
        ValueNode effectiveTripCount = maxTripCount;
        if (!effectiveTripCount.stamp(NodeView.DEFAULT).isCompatible(stamp)) {
            effectiveTripCount = IntegerConvertNode.convert(effectiveTripCount, stamp, graph(), NodeView.DEFAULT);
        }
        ValueNode tripCountMinus1 = MathUtil.sub(graph, effectiveTripCount, ConstantNode.forIntegerStamp(stamp, 1, graph));
        ValueNode stripTimesTripCount = MathUtil.mul(graph, stride, tripCountMinus1);
        ValueNode extremum = MathUtil.add(graph, stripTimesTripCount, initNode);
        return extremum;
    }

    @Override
    public ValueNode exitValueNode() {
        Stamp stamp = phi.stamp(NodeView.DEFAULT);
        if (loop.counted().isInverted()) {
            return extremumNode(false, stamp);
        }
        ValueNode maxTripCount = loop.counted().maxTripCountNode();
        if (!maxTripCount.stamp(NodeView.DEFAULT).isCompatible(stamp)) {
            maxTripCount = IntegerConvertNode.convert(maxTripCount, stamp, graph(), NodeView.DEFAULT);
        }
        return MathUtil.add(graph(), MathUtil.mul(graph(), strideNode(), maxTripCount), initNode());
    }

    @Override
    public boolean isConstantExtremum() {
        return isConstantInit() && isConstantStride() && loop.counted().isConstantMaxTripCount();
    }

    @Override
    public long constantExtremum() {
        UnsignedLong tripCount = loop.counted().constantMaxTripCount();
        if (tripCount.isLessThan(1)) {
            return constantInit();
        }
        return tripCount.minus(1).wrappingTimes(constantStride()).wrappingPlus(constantInit()).asLong();
    }

    @Override
    public void deleteUnusedNodes() {
    }

    @Override
    public String toString(IVToStringVerbosity verbosity) {
        if (verbosity == IVToStringVerbosity.FULL) {
            return String.format("BasicInductionVariable %s %s %s %s", initNode(), phi, op.getNodeClass().shortName(), strideNode());
        } else {
            return String.format("%s %s %s %s", initNode(), phi, op.getNodeClass().shortName(), strideNode());
        }
    }

    @Override
    public ValueNode entryTripValue() {
        return init;
    }
}
