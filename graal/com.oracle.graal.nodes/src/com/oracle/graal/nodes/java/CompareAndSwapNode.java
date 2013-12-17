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
package com.oracle.graal.nodes.java;

import static com.oracle.graal.graph.UnsafeAccess.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

/**
 * Represents an atomic compare-and-swap operation The result is a boolean that contains whether the
 * value matched the expected value.
 */
public class CompareAndSwapNode extends AbstractMemoryCheckpoint implements Lowerable, MemoryCheckpoint.Single, Canonicalizable {

    @Input private ValueNode object;
    @Input private ValueNode offset;
    @Input private ValueNode expected;
    @Input private ValueNode newValue;
    private final int displacement;
    private final LocationIdentity locationIdentity;

    public ValueNode object() {
        return object;
    }

    public ValueNode offset() {
        return offset;
    }

    public ValueNode expected() {
        return expected;
    }

    public ValueNode newValue() {
        return newValue;
    }

    public int displacement() {
        return displacement;
    }

    public CompareAndSwapNode(ValueNode object, int displacement, ValueNode offset, ValueNode expected, ValueNode newValue) {
        this(object, displacement, offset, expected, newValue, LocationIdentity.ANY_LOCATION);
    }

    public CompareAndSwapNode(ValueNode object, int displacement, ValueNode offset, ValueNode expected, ValueNode newValue, LocationIdentity locationIdentity) {
        super(StampFactory.forKind(Kind.Boolean.getStackKind()));
        assert expected.kind() == newValue.kind();
        this.object = object;
        this.offset = offset;
        this.expected = expected;
        this.newValue = newValue;
        this.displacement = displacement;
        this.locationIdentity = locationIdentity;
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        return locationIdentity;
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (getLocationIdentity() == LocationIdentity.ANY_LOCATION) {
            Constant offsetConstant = offset().asConstant();
            if (offsetConstant != null) {
                ResolvedJavaType receiverType = ObjectStamp.typeOrNull(object());
                if (receiverType != null) {
                    long constantOffset = offsetConstant.asLong();
                    ResolvedJavaField field = receiverType.findInstanceFieldWithOffset(constantOffset);
                    if (field != null && expected().kind() == field.getKind() && newValue().kind() == field.getKind()) {
                        return graph().add(new CompareAndSwapNode(object, displacement, offset, expected, newValue, field));
                    }
                }
            }
        }
        return this;
    }

    // specialized on value type until boxing/unboxing is sorted out in intrinsification
    @NodeIntrinsic
    public static boolean compareAndSwap(Object object, @ConstantNodeParameter int displacement, long offset, Object expected, Object newValue) {
        return unsafe.compareAndSwapObject(object, displacement + offset, expected, newValue);
    }

    @NodeIntrinsic
    public static boolean compareAndSwap(Object object, @ConstantNodeParameter int displacement, long offset, long expected, long newValue) {
        return unsafe.compareAndSwapLong(object, displacement + offset, expected, newValue);
    }

    @NodeIntrinsic
    public static boolean compareAndSwap(Object object, @ConstantNodeParameter int displacement, long offset, int expected, int newValue) {
        return unsafe.compareAndSwapInt(object, displacement + offset, expected, newValue);
    }
}
