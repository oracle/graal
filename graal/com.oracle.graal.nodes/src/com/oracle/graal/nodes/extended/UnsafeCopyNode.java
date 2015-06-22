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
package com.oracle.graal.nodes.extended;

import jdk.internal.jvmci.meta.*;

import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;

/**
 * Copy a value at a location specified as an offset relative to a source object to another location
 * specified as an offset relative to destination object. No null checks are performed.
 *
 * This node must be replaced during processing of node intrinsics with an {@link UnsafeLoadNode}
 * and {@link UnsafeStoreNode} pair.
 */
@NodeInfo
public final class UnsafeCopyNode extends FixedWithNextNode implements StateSplit {

    public static final NodeClass<UnsafeCopyNode> TYPE = NodeClass.create(UnsafeCopyNode.class);
    @Input ValueNode sourceObject;
    @Input ValueNode destinationObject;
    @Input ValueNode sourceOffset;
    @Input ValueNode destinationOffset;
    protected final Kind accessKind;
    protected final LocationIdentity locationIdentity;
    @OptionalInput(InputType.State) FrameState stateAfter;

    public UnsafeCopyNode(ValueNode sourceObject, ValueNode sourceOffset, ValueNode destinationObject, ValueNode destinationOffset, Kind accessKind, LocationIdentity locationIdentity) {
        this(sourceObject, sourceOffset, destinationObject, destinationOffset, accessKind, locationIdentity, null);
    }

    public UnsafeCopyNode(ValueNode sourceObject, ValueNode sourceOffset, ValueNode destinationObject, ValueNode destinationOffset, Kind accessKind, LocationIdentity locationIdentity,
                    FrameState stateAfter) {
        super(TYPE, StampFactory.forVoid());
        this.sourceObject = sourceObject;
        this.sourceOffset = sourceOffset;
        this.destinationObject = destinationObject;
        this.destinationOffset = destinationOffset;
        this.accessKind = accessKind;
        this.locationIdentity = locationIdentity;
        this.stateAfter = stateAfter;
        assert accessKind != Kind.Void && accessKind != Kind.Illegal;
    }

    public ValueNode sourceObject() {
        return sourceObject;
    }

    public ValueNode destinationObject() {
        return destinationObject;
    }

    public LocationIdentity getLocationIdentity() {
        return locationIdentity;
    }

    public ValueNode sourceOffset() {
        return sourceOffset;
    }

    public ValueNode destinationOffset() {
        return destinationOffset;
    }

    public Kind accessKind() {
        return accessKind;
    }

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

    public FrameState getState() {
        return stateAfter;
    }

    @NodeIntrinsic
    public static native void copy(Object srcObject, long srcOffset, Object destObject, long destOffset, @ConstantNodeParameter Kind kind, @ConstantNodeParameter LocationIdentity locationIdentity);
}
