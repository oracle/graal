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

import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.type.*;

/**
 * Compares two arrays with the same length.
 */
public class ArrayEqualsNode extends FloatingNode implements LIRGenLowerable, Canonicalizable {

    /** {@link Kind} of the arrays to compare. */
    private final Kind kind;

    /** One array to be tested for equality. */
    @Input private ValueNode array1;

    /** The other array to be tested for equality. */
    @Input private ValueNode array2;

    /** Length of both arrays. */
    @Input private ValueNode length;

    public ArrayEqualsNode(ValueNode array1, ValueNode array2, ValueNode length) {
        super(StampFactory.forKind(Kind.Boolean));

        assert array1.stamp().equals(array2.stamp());
        ObjectStamp stamp = (ObjectStamp) array1.stamp();
        ResolvedJavaType componentType = stamp.type().getComponentType();
        this.kind = componentType.getKind();

        this.array1 = array1;
        this.array2 = array2;
        this.length = length;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (!array1.isConstant() || !array2.isConstant()) {
            return this;
        }

        Object a1 = array1.asConstant().asObject();
        Object a2 = array2.asConstant().asObject();
        boolean x;
        switch (kind) {
            case Boolean:
                x = Arrays.equals((boolean[]) a1, (boolean[]) a2);
                break;
            case Byte:
                x = Arrays.equals((byte[]) a1, (byte[]) a2);
                break;
            case Char:
                x = Arrays.equals((char[]) a1, (char[]) a2);
                break;
            case Short:
                x = Arrays.equals((short[]) a1, (short[]) a2);
                break;
            case Int:
                x = Arrays.equals((int[]) a1, (int[]) a2);
                break;
            case Long:
                x = Arrays.equals((long[]) a1, (long[]) a2);
                break;
            case Float:
                x = Arrays.equals((float[]) a1, (float[]) a2);
                break;
            case Double:
                x = Arrays.equals((double[]) a1, (double[]) a2);
                break;
            default:
                throw GraalInternalError.shouldNotReachHere("unknown kind " + kind);
        }
        return ConstantNode.forBoolean(x, graph());
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
    public void generate(LIRGenerator gen) {
        Variable result = gen.newVariable(Kind.Int);
        gen.emitArrayEquals(kind, result, gen.operand(array1), gen.operand(array2), gen.operand(length));
        gen.setResult(this, result);
    }
}
