/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
import static org.graalvm.word.LocationIdentity.any;

import org.graalvm.compiler.core.common.memory.MemoryOrderMode;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StateSplit;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.memory.FixedAccessNode;
import org.graalvm.compiler.nodes.memory.LIRLowerableAccess;
import org.graalvm.compiler.nodes.memory.OrderedMemoryAccess;
import org.graalvm.compiler.nodes.memory.SingleMemoryKill;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.word.LocationIdentity;

/**
 * Low-level atomic compare-and-swap operation.
 */
@NodeInfo(allowedUsageTypes = {InputType.Value, Memory})
public abstract class AbstractCompareAndSwapNode extends FixedAccessNode implements OrderedMemoryAccess, StateSplit, LIRLowerableAccess, SingleMemoryKill {
    public static final NodeClass<AbstractCompareAndSwapNode> TYPE = NodeClass.create(AbstractCompareAndSwapNode.class);
    @Input ValueNode expectedValue;
    @Input ValueNode newValue;
    @OptionalInput(State) FrameState stateAfter;
    protected final MemoryOrderMode memoryOrder;

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

    @Override
    public final MemoryOrderMode getMemoryOrder() {
        return memoryOrder;
    }

    public AbstractCompareAndSwapNode(NodeClass<? extends AbstractCompareAndSwapNode> c, AddressNode address, LocationIdentity location, ValueNode expectedValue, ValueNode newValue,
                    BarrierType barrierType, Stamp stamp, MemoryOrderMode memoryOrder) {
        super(c, address, location, stamp, barrierType);
        assert expectedValue.getStackKind() == newValue.getStackKind();
        this.expectedValue = expectedValue;
        this.newValue = newValue;
        this.memoryOrder = memoryOrder;
    }

    @Override
    public boolean canNullCheck() {
        return false;
    }

    @Override
    public Stamp getAccessStamp(NodeView view) {
        return expectedValue.stamp(view).meet(newValue.stamp(view)).unrestricted();
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        return any();
    }
}
