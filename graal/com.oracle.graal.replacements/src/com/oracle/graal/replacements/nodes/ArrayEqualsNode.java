/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.util.*;

/**
 * Compares two arrays with the same length.
 */
@NodeInfo
public class ArrayEqualsNode extends FixedWithNextNode implements LIRLowerable, Canonicalizable, Virtualizable, MemoryAccess {

    /** {@link Kind} of the arrays to compare. */
    protected final Kind kind;

    /** One array to be tested for equality. */
    @Input ValueNode array1;

    /** The other array to be tested for equality. */
    @Input ValueNode array2;

    /** Length of both arrays. */
    @Input ValueNode length;

    public static ArrayEqualsNode create(ValueNode array1, ValueNode array2, ValueNode length) {
        return USE_GENERATED_NODES ? new ArrayEqualsNodeGen(array1, array2, length) : new ArrayEqualsNode(array1, array2, length);
    }

    protected ArrayEqualsNode(ValueNode array1, ValueNode array2, ValueNode length) {
        super(StampFactory.forKind(Kind.Boolean));

        assert array1.stamp().equals(array2.stamp());
        ObjectStamp array1Stamp = (ObjectStamp) array1.stamp();
        ResolvedJavaType componentType = array1Stamp.type().getComponentType();
        this.kind = componentType.getKind();

        this.array1 = array1;
        this.array2 = array2;
        this.length = length;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (usages().isEmpty()) {
            return null;
        }
        if (GraphUtil.unproxify(array1) == GraphUtil.unproxify(array2)) {
            return ConstantNode.forBoolean(true);
        }
        return this;
    }

    public void virtualize(VirtualizerTool tool) {
        State state1 = tool.getObjectState(array1);
        if (state1 != null) {
            State state2 = tool.getObjectState(array2);
            if (state2 != null) {
                if (state1.getVirtualObject() == state2.getVirtualObject()) {
                    // the same virtual objects will always have the same contents
                    tool.replaceWithValue(ConstantNode.forBoolean(true, graph()));
                } else if (state1.getVirtualObject().entryCount() == state2.getVirtualObject().entryCount()) {
                    int entryCount = state1.getVirtualObject().entryCount();
                    boolean allEqual = true;
                    for (int i = 0; i < entryCount; i++) {
                        ValueNode entry1 = state1.getEntry(i);
                        ValueNode entry2 = state2.getEntry(i);
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
    }

    @NodeIntrinsic
    public static native boolean equals(boolean[] array1, boolean[] array2, int length);

    @NodeIntrinsic
    public static native boolean equals(byte[] array1, byte[] array2, int length);

    @NodeIntrinsic
    public static native boolean equals(char[] array1, char[] array2, int length);

    @NodeIntrinsic
    public static native boolean equals(short[] array1, short[] array2, int length);

    @NodeIntrinsic
    public static native boolean equals(int[] array1, int[] array2, int length);

    @NodeIntrinsic
    public static native boolean equals(long[] array1, long[] array2, int length);

    @NodeIntrinsic
    public static native boolean equals(float[] array1, float[] array2, int length);

    @NodeIntrinsic
    public static native boolean equals(double[] array1, double[] array2, int length);

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        Value result = gen.getLIRGeneratorTool().emitArrayEquals(kind, gen.operand(array1), gen.operand(array2), gen.operand(length));
        gen.setResult(this, result);
    }

    public LocationIdentity getLocationIdentity() {
        return NamedLocationIdentity.getArrayLocation(kind);
    }
}
