/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.LocationIdentity;

import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.FixedWithNextNode;
import com.oracle.graal.nodes.StateSplit;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.calc.AddNode;
import com.oracle.graal.nodes.extended.JavaWriteNode;
import com.oracle.graal.nodes.extended.UnsafeStoreNode;
import com.oracle.graal.nodes.memory.HeapAccess.BarrierType;
import com.oracle.graal.nodes.memory.address.AddressNode;
import com.oracle.graal.nodes.memory.address.OffsetAddressNode;
import com.oracle.graal.nodes.spi.Lowerable;
import com.oracle.graal.nodes.spi.LoweringTool;

/**
 * A special purpose store node that differs from {@link UnsafeStoreNode} in that it is not a
 * {@link StateSplit} and does not include a write barrier. Note that contrary to the sound of the
 * name this node can be used for storing any kind.
 */
@NodeInfo
public final class DirectObjectStoreNode extends FixedWithNextNode implements Lowerable {

    public static final NodeClass<DirectObjectStoreNode> TYPE = NodeClass.create(DirectObjectStoreNode.class);
    @Input ValueNode object;
    @Input ValueNode value;
    @Input ValueNode offset;
    protected final int displacement;
    protected final LocationIdentity locationIdentity;
    protected final JavaKind storeKind;

    public DirectObjectStoreNode(ValueNode object, int displacement, ValueNode offset, ValueNode value, LocationIdentity locationIdentity, JavaKind storeKind) {
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
                    @ConstantNodeParameter JavaKind storeKind);

    @NodeIntrinsic
    public static native void storeBoolean(Object obj, @ConstantNodeParameter int displacement, long offset, boolean value, @ConstantNodeParameter LocationIdentity locationIdenity,
                    @ConstantNodeParameter JavaKind storeKind);

    @NodeIntrinsic
    public static native void storeByte(Object obj, @ConstantNodeParameter int displacement, long offset, byte value, @ConstantNodeParameter LocationIdentity locationIdenity,
                    @ConstantNodeParameter JavaKind storeKind);

    @NodeIntrinsic
    public static native void storeChar(Object obj, @ConstantNodeParameter int displacement, long offset, char value, @ConstantNodeParameter LocationIdentity locationIdenity,
                    @ConstantNodeParameter JavaKind storeKind);

    @NodeIntrinsic
    public static native void storeShort(Object obj, @ConstantNodeParameter int displacement, long offset, short value, @ConstantNodeParameter LocationIdentity locationIdenity,
                    @ConstantNodeParameter JavaKind storeKind);

    @NodeIntrinsic
    public static native void storeInt(Object obj, @ConstantNodeParameter int displacement, long offset, int value, @ConstantNodeParameter LocationIdentity locationIdenity,
                    @ConstantNodeParameter JavaKind storeKind);

    @NodeIntrinsic
    public static native void storeLong(Object obj, @ConstantNodeParameter int displacement, long offset, long value, @ConstantNodeParameter LocationIdentity locationIdenity,
                    @ConstantNodeParameter JavaKind storeKind);

    @NodeIntrinsic
    public static native void storeFloat(Object obj, @ConstantNodeParameter int displacement, long offset, float value, @ConstantNodeParameter LocationIdentity locationIdenity,
                    @ConstantNodeParameter JavaKind storeKind);

    @NodeIntrinsic
    public static native void storeDouble(Object obj, @ConstantNodeParameter int displacement, long offset, double value, @ConstantNodeParameter LocationIdentity locationIdenity,
                    @ConstantNodeParameter JavaKind storeKind);

    @Override
    public void lower(LoweringTool tool) {
        ValueNode off = graph().unique(new AddNode(offset, ConstantNode.forIntegerStamp(offset.stamp(), displacement, graph())));
        AddressNode address = graph().unique(new OffsetAddressNode(object, off));
        JavaWriteNode write = graph().add(new JavaWriteNode(storeKind, address, locationIdentity, value, BarrierType.NONE, storeKind == JavaKind.Object, false));
        graph().replaceFixedWithFixed(this, write);

        tool.getLowerer().lower(write, tool);
    }
}
