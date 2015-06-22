/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.jvmci.meta.*;

import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.memory.address.*;

/**
 * An {@link FixedAccessNode} that can be converted to a {@link FloatingAccessNode}.
 */
@NodeInfo
public abstract class FloatableAccessNode extends FixedAccessNode {
    public static final NodeClass<FloatableAccessNode> TYPE = NodeClass.create(FloatableAccessNode.class);

    protected FloatableAccessNode(NodeClass<? extends FloatableAccessNode> c, AddressNode address, LocationIdentity location, Stamp stamp) {
        super(c, address, location, stamp);
    }

    protected FloatableAccessNode(NodeClass<? extends FloatableAccessNode> c, AddressNode address, LocationIdentity location, Stamp stamp, GuardingNode guard, BarrierType barrierType) {
        super(c, address, location, stamp, guard, barrierType, false, null);
    }

    protected FloatableAccessNode(NodeClass<? extends FloatableAccessNode> c, AddressNode address, LocationIdentity location, Stamp stamp, GuardingNode guard, BarrierType barrierType,
                    boolean nullCheck, FrameState stateBefore) {
        super(c, address, location, stamp, guard, barrierType, nullCheck, stateBefore);
    }

    public abstract FloatingAccessNode asFloatingNode(MemoryNode lastLocationAccess);

    protected boolean forceFixed;

    public void setForceFixed(boolean flag) {
        this.forceFixed = flag;
    }

    /**
     * AccessNodes can float only if their location identities are not ANY_LOCATION. Furthermore, in
     * case G1 is enabled any access (read) to the java.lang.ref.Reference.referent field which has
     * an attached write barrier with pre-semantics can not also float.
     */
    public boolean canFloat() {
        return !forceFixed && getLocationIdentity().isSingle() && getBarrierType() == BarrierType.NONE;
    }
}
