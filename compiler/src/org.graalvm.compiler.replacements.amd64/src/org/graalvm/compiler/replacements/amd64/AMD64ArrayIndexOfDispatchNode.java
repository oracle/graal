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
package org.graalvm.compiler.replacements.amd64;

import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_512;

import org.graalvm.compiler.core.common.spi.ForeignCallSignature;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.replacements.ArrayIndexOfDispatchNode;

import jdk.vm.ci.meta.JavaKind;

/**
 * This node is a placeholder for all variants of intrinsified indexof-operations. It may be lowered
 * to a {@link AMD64ArrayIndexOfNode} or a specialized snippet.
 */
@NodeInfo(size = SIZE_512, cycles = NodeCycles.CYCLES_UNKNOWN)
public class AMD64ArrayIndexOfDispatchNode extends ArrayIndexOfDispatchNode {

    public static final NodeClass<AMD64ArrayIndexOfDispatchNode> TYPE = NodeClass.create(AMD64ArrayIndexOfDispatchNode.class);

    public AMD64ArrayIndexOfDispatchNode(@ConstantNodeParameter ForeignCallSignature stubCallDescriptor, @ConstantNodeParameter JavaKind arrayKind, @ConstantNodeParameter JavaKind valueKind,
                    @ConstantNodeParameter boolean findTwoConsecutive, ValueNode arrayPointer, ValueNode arrayLength, ValueNode fromIndex, ValueNode... searchValues) {
        super(TYPE, stubCallDescriptor, arrayKind, valueKind, findTwoConsecutive, arrayPointer, arrayLength, fromIndex, searchValues);
    }

    @NodeIntrinsic
    private static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter ForeignCallSignature descriptor,
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind valueKind,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    Object array, int arrayLength, int fromIndex, byte v1, byte v2);

    @NodeIntrinsic
    private static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter ForeignCallSignature descriptor,
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind valueKind,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    Object array, int arrayLength, int fromIndex, byte v1, byte v2, byte v3);

    @NodeIntrinsic
    private static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter ForeignCallSignature descriptor,
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind valueKind,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    Object array, int arrayLength, int fromIndex, byte v1, byte v2, byte v3, byte v4);

    @NodeIntrinsic
    private static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter ForeignCallSignature descriptor,
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind valueKind,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    Object array, int arrayLength, int fromIndex, char v1, char v2);

    @NodeIntrinsic
    private static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter ForeignCallSignature descriptor,
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind valueKind,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    Object array, int arrayLength, int fromIndex, char v1, char v2, char v3);

    @NodeIntrinsic
    private static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter ForeignCallSignature descriptor,
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind valueKind,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    Object array, int arrayLength, int fromIndex, char v1, char v2, char v3, char v4);

    @NodeIntrinsic
    private static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter ForeignCallSignature descriptor,
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind valueKind,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    Object array, int arrayLength, int fromIndex, int searchValue);

    public static int indexOf(@ConstantNodeParameter ForeignCallSignature descriptor, byte[] array, int arrayLength, int fromIndex, byte v1, byte v2) {
        return optimizedArrayIndexOf(descriptor, JavaKind.Byte, JavaKind.Byte, false, array, arrayLength, fromIndex, v1, v2);
    }

    public static int indexOf(@ConstantNodeParameter ForeignCallSignature descriptor, byte[] array, int arrayLength, int fromIndex, byte v1, byte v2, byte v3) {
        return optimizedArrayIndexOf(descriptor, JavaKind.Byte, JavaKind.Byte, false, array, arrayLength, fromIndex, v1, v2, v3);
    }

    public static int indexOf(@ConstantNodeParameter ForeignCallSignature descriptor, byte[] array, int arrayLength, int fromIndex, byte v1, byte v2, byte v3, byte v4) {
        return optimizedArrayIndexOf(descriptor, JavaKind.Byte, JavaKind.Byte, false, array, arrayLength, fromIndex, v1, v2, v3, v4);
    }

    public static int indexOf(@ConstantNodeParameter ForeignCallSignature descriptor, byte[] array, int arrayLength, int fromIndex, char v1, char v2) {
        return optimizedArrayIndexOf(descriptor, JavaKind.Byte, JavaKind.Char, false, array, arrayLength, fromIndex, v1, v2);
    }

    public static int indexOf(@ConstantNodeParameter ForeignCallSignature descriptor, byte[] array, int arrayLength, int fromIndex, char v1, char v2, char v3) {
        return optimizedArrayIndexOf(descriptor, JavaKind.Byte, JavaKind.Char, false, array, arrayLength, fromIndex, v1, v2, v3);
    }

    public static int indexOf(@ConstantNodeParameter ForeignCallSignature descriptor, byte[] array, int arrayLength, int fromIndex, char v1, char v2, char v3, char v4) {
        return optimizedArrayIndexOf(descriptor, JavaKind.Byte, JavaKind.Char, false, array, arrayLength, fromIndex, v1, v2, v3, v4);
    }

    public static int indexOf(@ConstantNodeParameter ForeignCallSignature descriptor, char[] array, int arrayLength, int fromIndex, char v1) {
        return optimizedArrayIndexOf(descriptor, JavaKind.Char, JavaKind.Char, false, array, arrayLength, fromIndex, v1);
    }

    public static int indexOf(@ConstantNodeParameter ForeignCallSignature descriptor, char[] array, int arrayLength, int fromIndex, char v1, char v2) {
        return optimizedArrayIndexOf(descriptor, JavaKind.Char, JavaKind.Char, false, array, arrayLength, fromIndex, v1, v2);
    }

    public static int indexOf(@ConstantNodeParameter ForeignCallSignature descriptor, char[] array, int arrayLength, int fromIndex, char v1, char v2, char v3) {
        return optimizedArrayIndexOf(descriptor, JavaKind.Char, JavaKind.Char, false, array, arrayLength, fromIndex, v1, v2, v3);
    }

    public static int indexOf(@ConstantNodeParameter ForeignCallSignature descriptor, char[] array, int arrayLength, int fromIndex, char v1, char v2, char v3, char v4) {
        return optimizedArrayIndexOf(descriptor, JavaKind.Char, JavaKind.Char, false, array, arrayLength, fromIndex, v1, v2, v3, v4);
    }

    public static int indexOf2ConsecutiveBytes(@ConstantNodeParameter ForeignCallSignature descriptor, byte[] array, int arrayLength, int fromIndex, int values) {
        return optimizedArrayIndexOf(descriptor, JavaKind.Byte, JavaKind.Byte, true, array, arrayLength, fromIndex, values);
    }

    public static int indexOf2ConsecutiveChars(@ConstantNodeParameter ForeignCallSignature descriptor, byte[] array, int arrayLength, int fromIndex, int values) {
        return optimizedArrayIndexOf(descriptor, JavaKind.Byte, JavaKind.Char, true, array, arrayLength, fromIndex, values);
    }

    public static int indexOf2ConsecutiveChars(@ConstantNodeParameter ForeignCallSignature descriptor, char[] array, int arrayLength, int fromIndex, int values) {
        return optimizedArrayIndexOf(descriptor, JavaKind.Char, JavaKind.Char, true, array, arrayLength, fromIndex, values);
    }
}
