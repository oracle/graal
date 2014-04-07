/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.extended;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.type.*;

public abstract class AbstractWriteNode extends FixedAccessNode implements StateSplit, MemoryCheckpoint.Single, MemoryAccess, GuardingNode {

    @Input private ValueNode value;
    @Input(InputType.State) private FrameState stateAfter;
    @Input(InputType.Memory) private Node lastLocationAccess;

    private final boolean initialization;

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

    public AbstractWriteNode(ValueNode object, ValueNode value, ValueNode location, BarrierType barrierType, boolean compressible) {
        this(object, value, location, barrierType, compressible, false);
    }

    public AbstractWriteNode(ValueNode object, ValueNode value, ValueNode location, BarrierType barrierType, boolean compressible, boolean initialization) {
        super(object, location, StampFactory.forVoid(), barrierType, compressible);
        this.value = value;
        this.initialization = initialization;
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

    public MemoryCheckpoint asMemoryCheckpoint() {
        return this;
    }

    public MemoryPhiNode asMemoryPhi() {
        return null;
    }
}
