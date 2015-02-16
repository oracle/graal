/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.replacements.nodes;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.HeapAccess.BarrierType;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;

/**
 * A special purpose store node that differs from {@link UnsafeStoreNode} in that it is not a
 * {@link StateSplit} and does not include a write barrier.
 */
@NodeInfo
public final class DirectObjectStoreNode extends FixedWithNextNode implements Lowerable {

    public static final NodeClass<DirectObjectStoreNode> TYPE = NodeClass.get(DirectObjectStoreNode.class);
    @Input ValueNode object;
    @Input ValueNode value;
    @Input ValueNode offset;
    protected final int displacement;
    protected final LocationIdentity locationIdentity;
    protected final Kind storeKind;

    public DirectObjectStoreNode(ValueNode object, int displacement, ValueNode offset, ValueNode value, LocationIdentity locationIdentity, Kind storeKind) {
        super(TYPE, StampFactory.forVoid());
        this.object = object;
        this.value = value;
        this.offset = offset;
        this.displacement = displacement;
        this.locationIdentity = locationIdentity;
        this.storeKind = storeKind;
    }

    @NodeIntrinsic
    public static native void storeObject(Object obj, @ConstantNodeParameter int displacement, long offset, Object value, @ConstantNodeParameter LocationIdentity locationIdentity,
                    @ConstantNodeParameter Kind storeKind);

    @NodeIntrinsic
    public static native void storeLong(Object obj, @ConstantNodeParameter int displacement, long offset, long value, @ConstantNodeParameter LocationIdentity locationIdenity,
                    @ConstantNodeParameter Kind storeKind);

    @Override
    public void lower(LoweringTool tool) {
        IndexedLocationNode location = graph().unique(new IndexedLocationNode(locationIdentity, displacement, offset, 1));
        JavaWriteNode write = graph().add(new JavaWriteNode(storeKind, object, value, location, BarrierType.NONE, storeKind == Kind.Object, false));
        graph().replaceFixedWithFixed(this, write);

        tool.getLowerer().lower(write, tool);
    }
}
