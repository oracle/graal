/*
 * Copyright (c) 2011, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.virtual;

import java.nio.ByteOrder;

import org.graalvm.compiler.core.common.spi.MetaAccessExtensionProvider;
import org.graalvm.compiler.core.common.type.PrimitiveStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.Verbosity;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.ArrayLengthProvider;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.nodes.spi.VirtualizerTool;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

@NodeInfo(nameTemplate = "VirtualArray({p#objectId}) {p#componentType/s}[{p#length}]")
public class VirtualArrayNode extends VirtualObjectNode implements ArrayLengthProvider {

    public static final NodeClass<VirtualArrayNode> TYPE = NodeClass.create(VirtualArrayNode.class);
    protected final ResolvedJavaType componentType;
    protected final int length;

    public VirtualArrayNode(ResolvedJavaType componentType, int length) {
        this(TYPE, componentType, length);
    }

    protected VirtualArrayNode(NodeClass<? extends VirtualObjectNode> c, ResolvedJavaType componentType, int length) {
        super(c, componentType.getArrayClass(), true);
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
            return super.toString(Verbosity.Name) + "(" + getObjectId() + ") " + componentType.getName() + "[" + length + "]";
        } else {
            return super.toString(verbosity);
        }
    }

    @Override
    public String entryName(int index) {
        return "[" + index + "]";
    }

    @Override
    public int entryIndexForOffset(MetaAccessProvider metaAccess, long constantOffset, JavaKind expectedEntryKind) {
        return entryIndexForOffset(metaAccess, constantOffset, expectedEntryKind, componentType, length);
    }

    public static int entryIndexForOffset(MetaAccessProvider metaAccess, long constantOffset, JavaKind expectedEntryKind, ResolvedJavaType componentType, int length) {
        int baseOffset = metaAccess.getArrayBaseOffset(componentType.getJavaKind());
        int indexScale = metaAccess.getArrayIndexScale(componentType.getJavaKind());

        long offset;
        if (ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN && componentType.isPrimitive()) {
            // On big endian, we expect the value to be correctly aligned in memory
            int componentByteCount = componentType.getJavaKind().getByteCount();
            offset = constantOffset - (componentByteCount - Math.min(componentByteCount, 4 + expectedEntryKind.getByteCount()));
        } else {
            offset = constantOffset;
        }
        long index = offset - baseOffset;
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
    public JavaKind entryKind(MetaAccessExtensionProvider metaAccessExtensionProvider, int index) {
        assert index >= 0 && index < length;
        return metaAccessExtensionProvider.getStorageKind(componentType);
    }

    @Override
    public VirtualArrayNode duplicate() {
        VirtualArrayNode node = new VirtualArrayNode(componentType, length);
        node.setNodeSourcePosition(this.getNodeSourcePosition());
        return node;
    }

    @Override
    public ValueNode getMaterializedRepresentation(FixedNode fixed, ValueNode[] entries, LockState locks) {
        AllocatedObjectNode node = new AllocatedObjectNode(this);
        node.setNodeSourcePosition(this.getNodeSourcePosition());
        return node;
    }

    @Override
    public ValueNode findLength(FindLengthMode mode, ConstantReflectionProvider constantReflection) {
        return ConstantNode.forInt(length);
    }

    /**
     * Returns the number of bytes that the entry at a given index actually occupies.
     */
    public int byteArrayEntryByteCount(int index, VirtualizerTool tool) {
        int i = index + 1;
        while (i < entryCount() && tool.getEntry(this, i).isIllegalConstant()) {
            i++;
        }
        return (i - index);
    }

    /**
     * Performs some sanity checks.
     */
    public static ValueNode virtualizeByteArrayRead(ValueNode entry, JavaKind accessKind, Stamp targetStamp) {
        assert !entry.isIllegalConstant();
        assert targetStamp.getStackKind().isPrimitive();
        assert accessKind.getBitCount() <= PrimitiveStamp.getBits(targetStamp);
        return entry;
    }
}
