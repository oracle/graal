/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.memory;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;

@NodeInfo(allowedUsageTypes = {InputType.Memory})
public abstract class AbstractWriteNode extends FixedAccessNode implements StateSplit, MemoryCheckpoint.Single, MemoryAccess, GuardingNode {

    public static final NodeClass<AbstractWriteNode> TYPE = NodeClass.create(AbstractWriteNode.class);
    @Input ValueNode value;
    @OptionalInput(InputType.State) FrameState stateAfter;
    @OptionalInput(InputType.Memory) Node lastLocationAccess;

    protected final boolean initialization;

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

    public ValueNode value() {
        return value;
    }

    /**
     * Returns whether this write is the initialization of the written location. If it is true, the
     * old value of the memory location is either uninitialized or zero. If it is false, the memory
     * location is guaranteed to contain a valid value or zero.
     */
    public boolean isInitialization() {
        return initialization;
    }

    protected AbstractWriteNode(NodeClass<? extends AbstractWriteNode> c, ValueNode object, ValueNode value, ValueNode location, BarrierType barrierType) {
        this(c, object, value, location, barrierType, false);
    }

    protected AbstractWriteNode(NodeClass<? extends AbstractWriteNode> c, ValueNode object, ValueNode value, ValueNode location, BarrierType barrierType, boolean initialization) {
        super(c, object, location, StampFactory.forVoid(), barrierType);
        this.value = value;
        this.initialization = initialization;
    }

    protected AbstractWriteNode(NodeClass<? extends AbstractWriteNode> c, ValueNode object, ValueNode value, ValueNode location, BarrierType barrierType, GuardingNode guard, boolean initialization) {
        super(c, object, location, StampFactory.forVoid(), guard, barrierType, false, null);
        this.value = value;
        this.initialization = initialization;
    }

    @Override
    public boolean isAllowedUsageType(InputType type) {
        return (type == InputType.Guard && getNullCheck()) ? true : super.isAllowedUsageType(type);
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        return location().getLocationIdentity();
    }

    public MemoryNode getLastLocationAccess() {
        return (MemoryNode) lastLocationAccess;
    }

    public void setLastLocationAccess(MemoryNode lla) {
        Node newLla = ValueNodeUtil.asNode(lla);
        updateUsages(lastLocationAccess, newLla);
        lastLocationAccess = newLla;
    }
}
