/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.nodes.consumer;

import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.java.StoreIndexedNode;
import jdk.graal.compiler.nodes.spi.Lowerable;

import jdk.graal.compiler.vector.nodes.type.Vector.BooleanVector;
import jdk.graal.compiler.vector.nodes.type.Vector.ByteVector;
import jdk.graal.compiler.vector.nodes.type.Vector.CharVector;
import jdk.graal.compiler.vector.nodes.type.Vector.DoubleVector;
import jdk.graal.compiler.vector.nodes.type.Vector.FloatVector;
import jdk.graal.compiler.vector.nodes.type.Vector.IntVector;
import jdk.graal.compiler.vector.nodes.type.Vector.LongVector;
import jdk.graal.compiler.vector.nodes.type.Vector.ObjectVector;
import jdk.graal.compiler.vector.nodes.type.Vector.ShortVector;

import jdk.vm.ci.meta.JavaKind;

/**
 * Allocate a new array and initialize it. This node stores the initial values with the implicit
 * conversion semantics of {@link StoreIndexedNode}.
 */
// JaCoCo Exclude
@NodeInfo
public final class MaterializeVectorNode extends AbstractMaterializeVectorNode implements Lowerable {
    public static final NodeClass<MaterializeVectorNode> TYPE = NodeClass.create(MaterializeVectorNode.class);

    public MaterializeVectorNode(@InjectedNodeParameter Stamp stamp, ValueNode allocator, ValueNode vector, ValueNode length) {
        super(TYPE, allocator, stamp, vector, length);
    }

    public static boolean[] materializeVector(JavaKind arrayKind, BooleanVector vector, int length) {
        return materializeVector(primitiveAllocator(arrayKind), vector, length);
    }

    public static byte[] materializeVector(JavaKind arrayKind, ByteVector vector, int length) {
        return materializeVector(primitiveAllocator(arrayKind), vector, length);
    }

    public static short[] materializeVector(JavaKind arrayKind, ShortVector vector, int length) {
        return materializeVector(primitiveAllocator(arrayKind), vector, length);
    }

    public static char[] materializeVector(JavaKind arrayKind, CharVector vector, int length) {
        return materializeVector(primitiveAllocator(arrayKind), vector, length);
    }

    public static int[] materializeVector(JavaKind arrayKind, IntVector vector, int length) {
        return materializeVector(primitiveAllocator(arrayKind), vector, length);
    }

    public static long[] materializeVector(JavaKind arrayKind, LongVector vector, int length) {
        return materializeVector(primitiveAllocator(arrayKind), vector, length);
    }

    public static float[] materializeVector(JavaKind arrayKind, FloatVector vector, int length) {
        return materializeVector(primitiveAllocator(arrayKind), vector, length);
    }

    public static double[] materializeVector(JavaKind arrayKind, DoubleVector vector, int length) {
        return materializeVector(primitiveAllocator(arrayKind), vector, length);
    }

    public static Object[] materializeVector(Class<?> elementType, ObjectVector vector, int length) {
        return materializeVector(objectAllocator(elementType), vector, length);
    }

    @NodeIntrinsic(value = PrimitiveVectorAllocator.class)
    private static native Object primitiveAllocator(@ConstantNodeParameter JavaKind arrayKind);

    @NodeIntrinsic(value = DynamicObjectVectorAllocator.class)
    private static native Object objectAllocator(Class<?> elementType);

    @NodeIntrinsic
    private static native boolean[] materializeVector(Object allocator, BooleanVector vector, int length);

    @NodeIntrinsic
    private static native byte[] materializeVector(Object allocator, ByteVector vector, int length);

    @NodeIntrinsic
    private static native short[] materializeVector(Object allocator, ShortVector vector, int length);

    @NodeIntrinsic
    private static native char[] materializeVector(Object allocator, CharVector vector, int length);

    @NodeIntrinsic
    private static native int[] materializeVector(Object allocator, IntVector vector, int length);

    @NodeIntrinsic
    private static native long[] materializeVector(Object allocator, LongVector vector, int length);

    @NodeIntrinsic
    private static native float[] materializeVector(Object allocator, FloatVector vector, int length);

    @NodeIntrinsic
    private static native double[] materializeVector(Object allocator, DoubleVector vector, int length);

    @NodeIntrinsic
    private static native Object[] materializeVector(Object allocator, ObjectVector vector, int length);
}
