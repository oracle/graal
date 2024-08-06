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
package jdk.graal.compiler.nodes.java;

import static jdk.graal.compiler.nodeinfo.InputType.Memory;
import static jdk.graal.compiler.nodeinfo.InputType.State;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_8;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_2;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StateSplit;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.memory.FixedAccessNode;
import jdk.graal.compiler.nodes.memory.LIRLowerableAccess;
import jdk.graal.compiler.nodes.memory.SingleMemoryKill;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.Value;

/**
 * Represents the lowered version of an atomic read-and-add operation like
 * {@code sun.misc.Unsafe.getAndAddInt(Object, long, int)}.
 */
@NodeInfo(allowedUsageTypes = {Memory}, cycles = CYCLES_8, size = SIZE_2)
public final class LoweredAtomicReadAndAddNode extends FixedAccessNode implements StateSplit, LIRLowerableAccess, SingleMemoryKill {

    public static final NodeClass<LoweredAtomicReadAndAddNode> TYPE = NodeClass.create(LoweredAtomicReadAndAddNode.class);
    @Input ValueNode delta;
    @OptionalInput(State) FrameState stateAfter;

    public LoweredAtomicReadAndAddNode(AddressNode address, LocationIdentity location, ValueNode delta) {
        super(TYPE, address, location, delta.stamp(NodeView.DEFAULT).unrestricted(), BarrierType.NONE);
        this.delta = delta;
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
        LIRKind accessKind = gen.getLIRGeneratorTool().getLIRKind(getAccessStamp(NodeView.DEFAULT));
        Value result = gen.getLIRGeneratorTool().emitAtomicReadAndAdd(accessKind, gen.operand(getAddress()), gen.operand(delta()));
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
