/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.java;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;

/**
 * Represents the lowered version of an atomic compare-and-swap operation{@code CompareAndSwapNode}.
 */
@NodeInfo(allowedUsageTypes = {InputType.Value, InputType.Memory})
public class LoweredCompareAndSwapNode extends FixedAccessNode implements StateSplit, LIRLowerable, MemoryCheckpoint.Single {

    @Input private ValueNode expectedValue;
    @Input private ValueNode newValue;
    @OptionalInput(InputType.State) private FrameState stateAfter;

    public FrameState stateAfter() {
        return stateAfter;
    }

    public void setStateAfter(FrameState x) {
        assert x == null || x.isAlive() : "frame state must be in a graph";
        updateUsages(stateAfter, x);
        stateAfter = x;
    }

    public boolean hasSideEffect() {
        return true;
    }

    public ValueNode getExpectedValue() {
        return expectedValue;
    }

    public ValueNode getNewValue() {
        return newValue;
    }

    public LoweredCompareAndSwapNode(ValueNode object, LocationNode location, ValueNode expectedValue, ValueNode newValue, BarrierType barrierType) {
        super(object, location, StampFactory.forKind(Kind.Boolean.getStackKind()), barrierType);
        assert expectedValue.getKind() == newValue.getKind();
        this.expectedValue = expectedValue;
        this.newValue = newValue;
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        return location().getLocationIdentity();
    }

    public boolean canNullCheck() {
        return false;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        assert getNewValue().stamp().isCompatible(getExpectedValue().stamp());
        Value address = location().generateAddress(gen, gen.getLIRGeneratorTool(), gen.operand(object()));
        Value result = gen.getLIRGeneratorTool().emitCompareAndSwap(address, gen.operand(getExpectedValue()), gen.operand(getNewValue()), Constant.INT_1, Constant.INT_0);
        gen.setResult(this, result);
    }
}
