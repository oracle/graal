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
package com.oracle.graal.replacements.nodes;

import jdk.internal.jvmci.meta.JavaKind;
import jdk.internal.jvmci.meta.LocationIdentity;
import jdk.internal.jvmci.meta.Value;

import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.graph.spi.Canonicalizable;
import com.oracle.graal.graph.spi.CanonicalizerTool;
import com.oracle.graal.nodeinfo.InputType;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.FixedWithNextNode;
import com.oracle.graal.nodes.NamedLocationIdentity;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.ValueNodeUtil;
import com.oracle.graal.nodes.memory.MemoryAccess;
import com.oracle.graal.nodes.memory.MemoryNode;
import com.oracle.graal.nodes.spi.LIRLowerable;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;
import com.oracle.graal.nodes.spi.Virtualizable;
import com.oracle.graal.nodes.spi.VirtualizerTool;
import com.oracle.graal.nodes.util.GraphUtil;
import com.oracle.graal.nodes.virtual.VirtualObjectNode;

// JaCoCo Exclude

/**
 * Compares two arrays with the same length.
 */
@NodeInfo
public final class ArrayEqualsNode extends FixedWithNextNode implements LIRLowerable, Canonicalizable, Virtualizable, MemoryAccess {

    public static final NodeClass<ArrayEqualsNode> TYPE = NodeClass.create(ArrayEqualsNode.class);
    /** {@link JavaKind} of the arrays to compare. */
    protected final JavaKind kind;

    /** One array to be tested for equality. */
    @Input ValueNode array1;

    /** The other array to be tested for equality. */
    @Input ValueNode array2;

    /** Length of both arrays. */
    @Input ValueNode length;

    @OptionalInput(InputType.Memory) MemoryNode lastLocationAccess;

    public ArrayEqualsNode(ValueNode array1, ValueNode array2, ValueNode length, @ConstantNodeParameter JavaKind kind) {
        super(TYPE, StampFactory.forKind(JavaKind.Boolean));
        this.kind = kind;
        this.array1 = array1;
        this.array2 = array2;
        this.length = length;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (tool.allUsagesAvailable() && hasNoUsages()) {
            return null;
        }
        if (GraphUtil.unproxify(array1) == GraphUtil.unproxify(array2)) {
            return ConstantNode.forBoolean(true);
        }
        return this;
    }

    public void virtualize(VirtualizerTool tool) {
        ValueNode alias1 = tool.getAlias(array1);
        ValueNode alias2 = tool.getAlias(array2);
        if (alias1 == alias2) {
            // the same virtual objects will always have the same contents
            tool.replaceWithValue(ConstantNode.forBoolean(true, graph()));
        } else if (alias1 instanceof VirtualObjectNode && alias2 instanceof VirtualObjectNode) {
            VirtualObjectNode virtual1 = (VirtualObjectNode) alias1;
            VirtualObjectNode virtual2 = (VirtualObjectNode) alias2;

            if (virtual1.entryCount() == virtual2.entryCount()) {
                int entryCount = virtual1.entryCount();
                boolean allEqual = true;
                for (int i = 0; i < entryCount; i++) {
                    ValueNode entry1 = tool.getEntry(virtual1, i);
                    ValueNode entry2 = tool.getEntry(virtual2, i);
                    if (entry1 != entry2) {
                        // the contents might be different
                        allEqual = false;
                    }
                    if (entry1.stamp().alwaysDistinct(entry2.stamp())) {
                        // the contents are different
                        tool.replaceWithValue(ConstantNode.forBoolean(false, graph()));
                        return;
                    }
                }
                if (allEqual) {
                    tool.replaceWithValue(ConstantNode.forBoolean(true, graph()));
                }
            }
        }
    }

    @NodeIntrinsic
    public static native boolean equals(Object array1, Object array2, int length, @ConstantNodeParameter JavaKind kind);

    public static boolean equals(boolean[] array1, boolean[] array2, int length) {
        return equals(array1, array2, length, JavaKind.Boolean);
    }

    public static boolean equals(byte[] array1, byte[] array2, int length) {
        return equals(array1, array2, length, JavaKind.Byte);
    }

    public static boolean equals(char[] array1, char[] array2, int length) {
        return equals(array1, array2, length, JavaKind.Char);
    }

    public static boolean equals(short[] array1, short[] array2, int length) {
        return equals(array1, array2, length, JavaKind.Short);
    }

    public static boolean equals(int[] array1, int[] array2, int length) {
        return equals(array1, array2, length, JavaKind.Int);
    }

    public static boolean equals(long[] array1, long[] array2, int length) {
        return equals(array1, array2, length, JavaKind.Long);
    }

    public static boolean equals(float[] array1, float[] array2, int length) {
        return equals(array1, array2, length, JavaKind.Float);
    }

    public static boolean equals(double[] array1, double[] array2, int length) {
        return equals(array1, array2, length, JavaKind.Double);
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        Value result = gen.getLIRGeneratorTool().emitArrayEquals(kind, gen.operand(array1), gen.operand(array2), gen.operand(length));
        gen.setResult(this, result);
    }

    public LocationIdentity getLocationIdentity() {
        return NamedLocationIdentity.getArrayLocation(kind);
    }

    public MemoryNode getLastLocationAccess() {
        return lastLocationAccess;
    }

    public void setLastLocationAccess(MemoryNode lla) {
        updateUsages(ValueNodeUtil.asNode(lastLocationAccess), ValueNodeUtil.asNode(lla));
        lastLocationAccess = lla;
    }
}
