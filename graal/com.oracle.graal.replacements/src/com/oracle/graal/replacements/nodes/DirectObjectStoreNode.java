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
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.HeapAccess.BarrierType;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

/**
 * A special purpose store node that differs from {@link UnsafeStoreNode} in that it is not a
 * {@link StateSplit} and does not include a write barrier.
 */
public class DirectObjectStoreNode extends FixedWithNextNode implements Lowerable {

    @Input private ValueNode object;
    @Input private ValueNode value;
    @Input private ValueNode offset;
    private final int displacement;
    private final LocationIdentity locationIdentity;

    public DirectObjectStoreNode(ValueNode object, int displacement, ValueNode offset, ValueNode value, LocationIdentity locationIdentity) {
        super(StampFactory.forVoid());
        this.object = object;
        this.value = value;
        this.offset = offset;
        this.displacement = displacement;
        this.locationIdentity = locationIdentity;
    }

    @NodeIntrinsic
    public static native void storeObject(Object obj, @ConstantNodeParameter int displacement, long offset, Object value, @ConstantNodeParameter LocationIdentity locationIdentity);

    @NodeIntrinsic
    public static native void storeLong(Object obj, @ConstantNodeParameter int displacement, long offset, long value, @ConstantNodeParameter LocationIdentity locationIdenity);

    @Override
    public void lower(LoweringTool tool) {
        IndexedLocationNode location = IndexedLocationNode.create(locationIdentity, value.getKind(), displacement, offset, graph(), 1);
        WriteNode write = graph().add(new WriteNode(object, value, location, BarrierType.NONE, value.getKind() == Kind.Object));
        graph().replaceFixedWithFixed(this, write);
    }
}
