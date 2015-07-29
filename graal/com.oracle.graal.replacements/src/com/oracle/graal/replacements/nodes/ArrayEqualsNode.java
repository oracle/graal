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

import jdk.internal.jvmci.meta.*;

import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.memory.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.nodes.virtual.*;

// JaCoCo Exclude

/**
 * Compares two arrays with the same length.
 */
@NodeInfo
public final class ArrayEqualsNode extends FixedWithNextNode implements LIRLowerable, Canonicalizable, Virtualizable, MemoryAccess {

    public static final NodeClass<ArrayEqualsNode> TYPE = NodeClass.create(ArrayEqualsNode.class);
    /** {@link Kind} of the arrays to compare. */
    protected final Kind kind;

    /** One array to be tested for equality. */
    @Input ValueNode array1;

    /** The other array to be tested for equality. */
    @Input ValueNode array2;

    /** Length of both arrays. */
    @Input ValueNode length;

    @OptionalInput(InputType.Memory) MemoryNode lastLocationAccess;

    public ArrayEqualsNode(ValueNode array1, ValueNode array2, ValueNode length, @ConstantNodeParameter Kind kind) {
        super(TYPE, StampFactory.forKind(Kind.Boolean));
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
    public static native boolean equals(Object array1, Object array2, int length, @ConstantNodeParameter Kind kind);

    public static boolean equals(boolean[] array1, boolean[] array2, int length) {
        return equals(array1, array2, length, Kind.Boolean);
    }

    public static boolean equals(byte[] array1, byte[] array2, int length) {
        return equals(array1, array2, length, Kind.Byte);
    }

    public static boolean equals(char[] array1, char[] array2, int length) {
        return equals(array1, array2, length, Kind.Char);
    }

    public static boolean equals(short[] array1, short[] array2, int length) {
        return equals(array1, array2, length, Kind.Short);
    }

    public static boolean equals(int[] array1, int[] array2, int length) {
        return equals(array1, array2, length, Kind.Int);
    }

    public static boolean equals(long[] array1, long[] array2, int length) {
        return equals(array1, array2, length, Kind.Long);
    }

    public static boolean equals(float[] array1, float[] array2, int length) {
        return equals(array1, array2, length, Kind.Float);
    }

    public static boolean equals(double[] array1, double[] array2, int length) {
        return equals(array1, array2, length, Kind.Double);
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
