/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.nodeinfo.InputType.Memory;
import static com.oracle.graal.nodeinfo.InputType.State;
import static com.oracle.graal.nodeinfo.InputType.Value;
import static com.oracle.graal.nodeinfo.NodeCycles.CYCLES_30;
import static com.oracle.graal.nodeinfo.NodeSize.SIZE_8;

import com.oracle.graal.compiler.common.LIRKind;
import com.oracle.graal.compiler.common.LocationIdentity;
import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.lir.gen.LIRGeneratorTool;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.FrameState;
import com.oracle.graal.nodes.StateSplit;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.memory.FixedAccessNode;
import com.oracle.graal.nodes.memory.MemoryCheckpoint;
import com.oracle.graal.nodes.memory.address.AddressNode;
import com.oracle.graal.nodes.spi.LIRLowerable;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;

/**
 * Represents the lowered version of an atomic compare-and-swap operation{@code CompareAndSwapNode}.
 */
@NodeInfo(allowedUsageTypes = {Value, Memory}, cycles = CYCLES_30, size = SIZE_8)
public final class LoweredCompareAndSwapNode extends FixedAccessNode implements StateSplit, LIRLowerable, MemoryCheckpoint.Single {

    public static final NodeClass<LoweredCompareAndSwapNode> TYPE = NodeClass.create(LoweredCompareAndSwapNode.class);
    @Input ValueNode expectedValue;
    @Input ValueNode newValue;
    @OptionalInput(State) FrameState stateAfter;

    @Override
    public FrameState stateAfter() {
        return stateAfter;
    }

    @Override
    public void setStateAfter(FrameState x) {
        assert x == null || x.isAlive() : "frame state must be in a graph";
        updateUsages(stateAfter, x);
        stateAfter = x;
    }

    @Override
    public boolean hasSideEffect() {
        return true;
    }

    public ValueNode getExpectedValue() {
        return expectedValue;
    }

    public ValueNode getNewValue() {
        return newValue;
    }

    public LoweredCompareAndSwapNode(AddressNode address, LocationIdentity location, ValueNode expectedValue, ValueNode newValue, BarrierType barrierType) {
        super(TYPE, address, location, StampFactory.forKind(JavaKind.Boolean.getStackKind()), barrierType);
        assert expectedValue.getStackKind() == newValue.getStackKind();
        this.expectedValue = expectedValue;
        this.newValue = newValue;
    }

    @Override
    public boolean canNullCheck() {
        return false;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        assert getNewValue().stamp().isCompatible(getExpectedValue().stamp());
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();

        LIRKind resultKind = tool.getLIRKind(stamp());
        Value trueResult = tool.emitConstant(resultKind, JavaConstant.TRUE);
        Value falseResult = tool.emitConstant(resultKind, JavaConstant.FALSE);
        Value result = tool.emitCompareAndSwap(gen.operand(getAddress()), gen.operand(getExpectedValue()), gen.operand(getNewValue()), trueResult, falseResult);

        gen.setResult(this, result);
    }
}
