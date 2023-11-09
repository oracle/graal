/*
 * Copyright (c) 2011, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.memory;

import static jdk.graal.compiler.nodeinfo.InputType.Memory;

import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.IterableNodeType;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.ImplicitNullCheckNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import org.graalvm.word.LocationIdentity;

/**
 * Accesses a value at a memory address specified by an {@linkplain #address address}. The access
 * does not include a null check on the object.
 */
@NodeInfo
public abstract class FixedAccessNode extends ImplicitNullCheckNode implements AddressableMemoryAccess, GuardedMemoryAccess, OnHeapMemoryAccess, IterableNodeType {
    public static final NodeClass<FixedAccessNode> TYPE = NodeClass.create(FixedAccessNode.class);

    @OptionalInput(InputType.Guard) protected GuardingNode guard;
    @Input(InputType.Association) AddressNode address;
    @OptionalInput(Memory) MemoryKill lastLocationAccess;
    protected final LocationIdentity location;

    /*
     * Indicates whether this access also serves as an implicit null check for the address. This
     * value can change throughout the node's lifetime and is dependent both on the known qualities
     * of the address and the access's barriers.
     */
    protected boolean usedAsNullCheck;
    protected BarrierType barrierType;

    @Override
    public AddressNode getAddress() {
        return address;
    }

    @Override
    public void setAddress(AddressNode address) {
        updateUsages(this.address, address);
        this.address = address;
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        return location;
    }

    public boolean getUsedAsNullCheck() {
        return usedAsNullCheck;
    }

    public void setUsedAsNullCheck(boolean check) {
        this.usedAsNullCheck = check;
    }

    protected FixedAccessNode(NodeClass<? extends FixedAccessNode> c, AddressNode address, LocationIdentity location, Stamp stamp) {
        this(c, address, location, stamp, BarrierType.NONE);
    }

    protected FixedAccessNode(NodeClass<? extends FixedAccessNode> c, AddressNode address, LocationIdentity location, Stamp stamp, BarrierType barrierType) {
        this(c, address, location, stamp, null, barrierType, false, null);
    }

    protected FixedAccessNode(NodeClass<? extends FixedAccessNode> c, AddressNode address, LocationIdentity location, Stamp stamp, GuardingNode guard, BarrierType barrierType, boolean usedAsNullCheck,
                    FrameState stateBefore) {
        super(c, stamp, stateBefore);
        this.address = address;
        this.location = location;
        this.guard = guard;
        this.barrierType = barrierType;
        this.usedAsNullCheck = usedAsNullCheck;
    }

    @Override
    public boolean canDeoptimize() {
        return usedAsNullCheck;
    }

    @Override
    public GuardingNode getGuard() {
        return guard;
    }

    @Override
    public void setGuard(GuardingNode guard) {
        updateUsagesInterface(this.guard, guard);
        this.guard = guard;
    }

    @Override
    public MemoryKill getLastLocationAccess() {
        return lastLocationAccess;
    }

    @Override
    public void setLastLocationAccess(MemoryKill lla) {
        updateUsagesInterface(lastLocationAccess, lla);
        lastLocationAccess = lla;
    }

    @Override
    public BarrierType getBarrierType() {
        return barrierType;
    }
}
