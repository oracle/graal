/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.replacements.arraycopy;

import static jdk.internal.jvmci.meta.LocationIdentity.*;
import jdk.internal.jvmci.meta.*;

import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.memory.*;
import com.oracle.graal.nodes.spi.*;

@NodeInfo
public class ArrayCopyUnrollNode extends ArrayRangeWriteNode implements MemoryCheckpoint.Single, Lowerable, MemoryAccess {

    public static final NodeClass<ArrayCopyUnrollNode> TYPE = NodeClass.create(ArrayCopyUnrollNode.class);

    @Input protected ValueNode src;
    @Input protected ValueNode srcPos;
    @Input protected ValueNode dest;
    @Input protected ValueNode destPos;
    @Input protected ValueNode length;

    private Kind elementKind;

    private int unrolledLength;

    @OptionalInput(InputType.Memory) private MemoryNode lastLocationAccess;

    public ArrayCopyUnrollNode(ValueNode src, ValueNode srcPos, ValueNode dest, ValueNode destPos, ValueNode length, int unrolledLength, Kind elementKind) {
        super(TYPE, StampFactory.forKind(Kind.Void));
        this.src = src;
        this.srcPos = srcPos;
        this.dest = dest;
        this.destPos = destPos;
        this.length = length;
        this.unrolledLength = unrolledLength;
        assert elementKind != null && elementKind != Kind.Illegal;
        this.elementKind = elementKind;
    }

    public ValueNode getSource() {
        return src;
    }

    public ValueNode getSourcePosition() {
        return srcPos;
    }

    public ValueNode getDestination() {
        return dest;
    }

    public ValueNode getDestinationPosition() {
        return destPos;
    }

    @Override
    public ValueNode getLength() {
        return length;
    }

    @Override
    public ValueNode getArray() {
        return dest;
    }

    @Override
    public ValueNode getIndex() {
        return destPos;
    }

    @Override
    public boolean isObjectArray() {
        return elementKind == Kind.Object;
    }

    @Override
    public boolean isInitialization() {
        return false;
    }

    @NodeIntrinsic
    public static native void arraycopy(Object nonNullSrc, int srcPos, Object nonNullDest, int destPos, int length, @ConstantNodeParameter int unrolledLength, @ConstantNodeParameter Kind elementKind);

    public int getUnrollLength() {
        return unrolledLength;
    }

    public Kind getElementKind() {
        return elementKind;
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        if (elementKind != null) {
            return NamedLocationIdentity.getArrayLocation(elementKind);
        }
        return any();
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    public MemoryNode getLastLocationAccess() {
        return lastLocationAccess;
    }

    public void setLastLocationAccess(MemoryNode lla) {
        updateUsagesInterface(lastLocationAccess, lla);
        lastLocationAccess = lla;
    }
}
