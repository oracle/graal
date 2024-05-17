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
package org.graalvm.compiler.nodes.loop;

import static org.graalvm.compiler.nodes.loop.MathUtil.mul;

import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.IntegerConvertNode;
import org.graalvm.compiler.nodes.calc.NegateNode;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.phases.common.util.LoopUtility;

public class DerivedScaledInductionVariable extends DerivedInductionVariable {

    protected final ValueNode scale;
    protected final ValueNode value;

    public DerivedScaledInductionVariable(LoopEx loop, InductionVariable base, ValueNode scale, ValueNode value) {
        super(loop, base);
        this.scale = scale;
        this.value = value;
    }

    public DerivedScaledInductionVariable(LoopEx loop, InductionVariable base, NegateNode value) {
        super(loop, base);
        this.scale = ConstantNode.forIntegerStamp(value.stamp(NodeView.DEFAULT), -1, value.graph());
        this.value = value;
    }

    public ValueNode getScale() {
        return scale;
    }

    @Override
    public ValueNode valueNode() {
        return value;
    }

    @Override
    public Direction direction() {
        Direction baseDirection = base.direction();
        if (baseDirection == null) {
            return null;
        }
        Stamp stamp = scale.stamp(NodeView.DEFAULT);
        if (stamp instanceof IntegerStamp) {
            IntegerStamp integerStamp = (IntegerStamp) stamp;
            if (integerStamp.isStrictlyPositive()) {
                return baseDirection;
            } else if (integerStamp.isStrictlyNegative()) {
                return baseDirection.opposite();
            }
        }
        return null;
    }

    @Override
    public ValueNode initNode() {
        return mul(graph(), base.initNode(), scale);
    }

    @Override
    public ValueNode strideNode() {
        return mul(graph(), base.strideNode(), scale);
    }

    @Override
    public boolean isConstantInit() {
        try {
            if (scale.isConstant() && base.isConstantInit()) {
                constantInitSafe();
                return true;
            }
        } catch (ArithmeticException e) {
            // fall through to return false
        }
        return false;
    }

    @Override
    public boolean isConstantStride() {
        try {
            if (scale.isConstant() && base.isConstantStride()) {
                constantStrideSafe();
                return true;
            }
        } catch (ArithmeticException e) {
            // fall through to return false
        }
        return false;
    }

    @Override
    public long constantInit() {
        return constantInitSafe();
    }

    private long constantInitSafe() throws ArithmeticException {
        return opSafe(base.constantInit(), scale.asJavaConstant().asLong());
    }

    @Override
    public long constantStride() {
        return constantStrideSafe();
    }

    private long constantStrideSafe() throws ArithmeticException {
        return opSafe(base.constantStride(), scale.asJavaConstant().asLong());
    }

    private long opSafe(long a, long b) {
        // we can use scale bits here because all operands (init, scale, stride and extremum) have
        // by construction equal bit sizes
        return LoopUtility.multiplyExact(IntegerStamp.getBits(scale.stamp(NodeView.DEFAULT)), a, b);
    }

    @Override
    public boolean isConstantExtremum() {
        try {
            if (scale.isConstant() && base.isConstantExtremum()) {
                constantExtremumSafe();
                return true;
            }
        } catch (ArithmeticException e) {
            // fall through to return false
        }
        return false;
    }

    @Override
    public long constantExtremum() {
        return constantExtremumSafe();
    }

    private long constantExtremumSafe() throws ArithmeticException {
        return opSafe(base.constantExtremum(), scale.asJavaConstant().asLong());
    }

    @Override
    public ValueNode extremumNode(boolean assumeLoopEntered, Stamp stamp) {
        return mul(graph(), base.extremumNode(assumeLoopEntered, stamp), IntegerConvertNode.convert(scale, stamp, graph(), NodeView.DEFAULT));
    }

    @Override
    public ValueNode extremumNode(boolean assumeLoopEntered, Stamp stamp, ValueNode maxTripCount) {
        return mul(graph(), base.extremumNode(assumeLoopEntered, stamp, maxTripCount), IntegerConvertNode.convert(scale, stamp, graph(), NodeView.DEFAULT));
    }

    @Override
    public ValueNode exitValueNode() {
        return mul(graph(), base.exitValueNode(), scale);
    }

    @Override
    public void deleteUnusedNodes() {
        GraphUtil.tryKillUnused(scale);
    }

    @Override
    public boolean isConstantScale(InductionVariable ref) {
        return super.isConstantScale(ref) || (scale.isConstant() && base.isConstantScale(ref));
    }

    @Override
    public long constantScale(InductionVariable ref) {
        assert isConstantScale(ref);
        if (super.isConstantScale(ref)) {
            return super.constantScale(ref);
        }
        return scale.asJavaConstant().asLong() * base.constantScale(ref);
    }

    @Override
    public boolean offsetIsZero(InductionVariable ref) {
        if (super.offsetIsZero(ref)) {
            return true;
        }
        return base.offsetIsZero(ref);
    }

    @Override
    public ValueNode offsetNode(InductionVariable ref) {
        assert !offsetIsZero(ref);
        return null;
    }

    @Override
    public String toString(IVToStringVerbosity verbosity) {
        if (verbosity == IVToStringVerbosity.FULL) {
            return String.format("DerivedScaleInductionVariable base (%s) %s %s", base, value.getNodeClass().shortName(), scale);
        } else {
            return String.format("(%s) %s %s", base, value.getNodeClass().shortName(), scale);
        }
    }

    @Override
    public ValueNode copyValue(InductionVariable newBase, boolean gvn) {
        return MathUtil.mul(graph(), newBase.valueNode(), scale, gvn);
    }

    @Override
    public ValueNode copyValue(InductionVariable newBase) {
        return copyValue(newBase, true);
    }

    @Override
    public InductionVariable copy(InductionVariable newBase, ValueNode newValue) {
        return new DerivedScaledInductionVariable(loop, newBase, scale, newValue);
    }

    @Override
    public ValueNode entryTripValue() {
        return mul(graph(), base.entryTripValue(), scale);
    }
}
