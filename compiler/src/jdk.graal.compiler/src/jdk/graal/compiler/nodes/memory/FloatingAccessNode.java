/*
 * Copyright (c) 2011, 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.FloatingGuardedNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import org.graalvm.word.LocationIdentity;

@NodeInfo
public abstract class FloatingAccessNode extends FloatingGuardedNode implements AddressableMemoryAccess, GuardedMemoryAccess, OnHeapMemoryAccess {
    public static final NodeClass<FloatingAccessNode> TYPE = NodeClass.create(FloatingAccessNode.class);

    @Input(InputType.Association) protected AddressNode address;
    protected final LocationIdentity location;

    protected BarrierType barrierType;

    protected FloatingAccessNode(NodeClass<? extends FloatingAccessNode> c, AddressNode address, LocationIdentity location, Stamp stamp, GuardingNode guard, BarrierType barrierType) {
        super(c, stamp, guard);
        this.address = address;
        this.location = location;
        this.barrierType = barrierType;
    }

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

    @Override
    public BarrierType getBarrierType() {
        return barrierType;
    }

    @Override
    public boolean canNullCheck() {
        return true;
    }

    public abstract FixedAccessNode asFixedNode();
}
