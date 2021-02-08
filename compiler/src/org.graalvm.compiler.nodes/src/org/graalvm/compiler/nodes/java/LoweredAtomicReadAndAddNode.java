/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.java;

import static org.graalvm.compiler.nodeinfo.InputType.Memory;
import static org.graalvm.compiler.nodeinfo.InputType.State;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_8;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_2;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StateSplit;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.memory.FixedAccessNode;
import org.graalvm.compiler.nodes.memory.LIRLowerableAccess;
import org.graalvm.compiler.nodes.memory.SingleMemoryKill;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

/**
 * Represents the lowered version of an atomic read-and-add operation like
 * {@link sun.misc.Unsafe#getAndAddInt(Object, long, int)}.
 */
@NodeInfo(allowedUsageTypes = {Memory}, cycles = CYCLES_8, size = SIZE_2)
public final class LoweredAtomicReadAndAddNode extends FixedAccessNode implements StateSplit, LIRLowerableAccess, SingleMemoryKill {

    public static final NodeClass<LoweredAtomicReadAndAddNode> TYPE = NodeClass.create(LoweredAtomicReadAndAddNode.class);
    @Input ValueNode delta;
    @OptionalInput(State) FrameState stateAfter;
    private final ValueKind<?> valueKind;

    public LoweredAtomicReadAndAddNode(AddressNode address, LocationIdentity location, ValueNode delta, ValueKind<?> valueKind, BarrierType barrierType) {
        super(TYPE, address, location, delta.stamp(NodeView.DEFAULT).unrestricted(), barrierType);
        this.delta = delta;
        this.valueKind = valueKind;
    }

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

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        Value result = gen.getLIRGeneratorTool().emitAtomicReadAndAdd(gen.operand(getAddress()), valueKind, gen.operand(delta()));
        gen.setResult(this, result);
    }

    @Override
    public boolean canNullCheck() {
        return false;
    }

    public ValueNode delta() {
        return delta;
    }

    @Override
    public Stamp getAccessStamp(NodeView view) {
        return stamp(view);
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        return getLocationIdentity();
    }
}
