/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.loop;

import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.util.*;

public class DerivedScaledInductionVariable extends InductionVariable {

    private InductionVariable base;
    private ValueNode scale;
    private ValueNode value;

    public DerivedScaledInductionVariable(LoopEx loop, InductionVariable base, ValueNode scale, ValueNode value) {
        super(loop);
        this.base = base;
        this.scale = scale;
        this.value = value;
    }

    public DerivedScaledInductionVariable(LoopEx loop, InductionVariable base, NegateNode value) {
        super(loop);
        this.base = base;
        this.scale = ConstantNode.forInt(-1, value.graph());
        this.value = value;
    }

    @Override
    public StructuredGraph graph() {
        return base.graph();
    }

    @Override
    public Direction direction() {
        Stamp stamp = scale.stamp();
        if (stamp instanceof IntegerStamp) {
            IntegerStamp integerStamp = (IntegerStamp) stamp;
            if (integerStamp.isStrictlyPositive()) {
                return base.direction();
            } else if (integerStamp.isStrictlyNegative()) {
                return base.direction().opposite();
            }
        }
        return null;
    }

    @Override
    public ValueNode valueNode() {
        return value;
    }

    @Override
    public ValueNode initNode() {
        return IntegerArithmeticNode.mul(graph(), base.initNode(), scale);
    }

    @Override
    public ValueNode strideNode() {
        return IntegerArithmeticNode.mul(graph(), base.strideNode(), scale);
    }

    @Override
    public boolean isConstantInit() {
        return scale.isConstant() && base.isConstantInit();
    }

    @Override
    public boolean isConstantStride() {
        return scale.isConstant() && base.isConstantStride();
    }

    @Override
    public long constantInit() {
        return base.constantInit() * scale.asConstant().asLong();
    }

    @Override
    public long constantStride() {
        return base.constantStride() * scale.asConstant().asLong();
    }

    @Override
    public ValueNode extremumNode(boolean assumePositiveTripCount, Stamp stamp) {
        return IntegerArithmeticNode.mul(graph(), base.extremumNode(assumePositiveTripCount, stamp), IntegerConvertNode.convert(scale, stamp, graph()));
    }

    @Override
    public ValueNode exitValueNode() {
        return IntegerArithmeticNode.mul(graph(), base.exitValueNode(), scale);
    }

    @Override
    public boolean isConstantExtremum() {
        return scale.isConstant() && base.isConstantExtremum();
    }

    @Override
    public long constantExtremum() {
        return base.constantExtremum() * scale.asConstant().asLong();
    }

    @Override
    public void deleteUnusedNodes() {
        if (scale.isAlive() && scale.usages().isEmpty()) {
            GraphUtil.killWithUnusedFloatingInputs(scale);
        }
    }
}
