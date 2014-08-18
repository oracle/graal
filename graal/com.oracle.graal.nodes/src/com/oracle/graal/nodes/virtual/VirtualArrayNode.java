/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.virtual;

import sun.misc.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;

@NodeInfo(nameTemplate = "VirtualArray {p#componentType/s}[{p#length}]")
public class VirtualArrayNode extends VirtualObjectNode implements ArrayLengthProvider {

    private final ResolvedJavaType componentType;
    private final int length;

    public static VirtualArrayNode create(ResolvedJavaType componentType, int length) {
        return new VirtualArrayNodeGen(componentType, length);
    }

    VirtualArrayNode(ResolvedJavaType componentType, int length) {
        super(componentType.getArrayClass(), true);
        this.componentType = componentType;
        this.length = length;
    }

    @Override
    public ResolvedJavaType type() {
        return componentType.getArrayClass();
    }

    public ResolvedJavaType componentType() {
        return componentType;
    }

    @Override
    public int entryCount() {
        return length;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        // nothing to do...
    }

    @Override
    public String toString(Verbosity verbosity) {
        if (verbosity == Verbosity.Name) {
            return super.toString(Verbosity.Name) + " " + componentType.getName() + "[" + length + "]";
        } else {
            return super.toString(verbosity);
        }
    }

    @Override
    public String entryName(int index) {
        return "[" + index + "]";
    }

    @Override
    public int entryIndexForOffset(long constantOffset) {
        int baseOffset;
        int indexScale;
        switch (componentType.getKind()) {
            case Boolean:
                baseOffset = Unsafe.ARRAY_BOOLEAN_BASE_OFFSET;
                indexScale = Unsafe.ARRAY_BOOLEAN_INDEX_SCALE;
                break;
            case Byte:
                baseOffset = Unsafe.ARRAY_BYTE_BASE_OFFSET;
                indexScale = Unsafe.ARRAY_BYTE_INDEX_SCALE;
                break;
            case Short:
                baseOffset = Unsafe.ARRAY_SHORT_BASE_OFFSET;
                indexScale = Unsafe.ARRAY_SHORT_INDEX_SCALE;
                break;
            case Char:
                baseOffset = Unsafe.ARRAY_CHAR_BASE_OFFSET;
                indexScale = Unsafe.ARRAY_CHAR_INDEX_SCALE;
                break;
            case Int:
                baseOffset = Unsafe.ARRAY_INT_BASE_OFFSET;
                indexScale = Unsafe.ARRAY_INT_INDEX_SCALE;
                break;
            case Long:
                baseOffset = Unsafe.ARRAY_LONG_BASE_OFFSET;
                indexScale = Unsafe.ARRAY_LONG_INDEX_SCALE;
                break;
            case Float:
                baseOffset = Unsafe.ARRAY_FLOAT_BASE_OFFSET;
                indexScale = Unsafe.ARRAY_FLOAT_INDEX_SCALE;
                break;
            case Double:
                baseOffset = Unsafe.ARRAY_DOUBLE_BASE_OFFSET;
                indexScale = Unsafe.ARRAY_DOUBLE_INDEX_SCALE;
                break;
            case Object:
                baseOffset = Unsafe.ARRAY_OBJECT_BASE_OFFSET;
                indexScale = Unsafe.ARRAY_OBJECT_INDEX_SCALE;
                break;
            default:
                return -1;
        }
        long index = constantOffset - baseOffset;
        if (index % indexScale != 0) {
            return -1;
        }
        long elementIndex = index / indexScale;
        if (elementIndex < 0 || elementIndex >= length) {
            return -1;
        }
        return (int) elementIndex;
    }

    @Override
    public Kind entryKind(int index) {
        assert index >= 0 && index < length;
        return componentType.getKind();
    }

    @Override
    public VirtualArrayNode duplicate() {
        return VirtualArrayNode.create(componentType, length);
    }

    @Override
    public ValueNode getMaterializedRepresentation(FixedNode fixed, ValueNode[] entries, LockState locks) {
        return AllocatedObjectNode.create(this);
    }

    public ValueNode length() {
        return ConstantNode.forInt(length);
    }
}
