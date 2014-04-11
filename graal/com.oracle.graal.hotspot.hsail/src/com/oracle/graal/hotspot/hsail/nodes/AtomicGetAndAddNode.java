/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.hsail.nodes;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

/**
 * Represents an atomic get-and-add operation. The result is the get value (before the delta is
 * added)
 */
public class AtomicGetAndAddNode extends AbstractStateSplit implements Lowerable, MemoryCheckpoint.Single {

    @Input private ValueNode base;
    @Input private ValueNode offset;
    @Input private ValueNode delta;
    @Input private LocationIdentity locationIdentity;

    public ValueNode base() {
        return base;
    }

    public ValueNode offset() {
        return offset;
    }

    public ValueNode delta() {
        return delta;
    }

    @SuppressWarnings("unused")
    public AtomicGetAndAddNode(ValueNode base, ValueNode offset, ValueNode location /* ignored */, ValueNode delta) {
        super(StampFactory.forKind(Kind.Long.getStackKind()));
        this.base = base;
        this.offset = offset;
        this.delta = delta;
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        return locationIdentity;
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    @NodeIntrinsic
    public native static long atomicGetAndAdd(long base, int offset, LocationIdentity locationIdentity, int delta);

    public MemoryCheckpoint asMemoryCheckpoint() {
        return this;
    }

    public MemoryPhiNode asMemoryPhi() {
        return null;
    }

}
