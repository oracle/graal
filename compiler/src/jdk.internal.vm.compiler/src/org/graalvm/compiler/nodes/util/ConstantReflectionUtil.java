/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.util;

import java.nio.ByteOrder;

import org.graalvm.compiler.core.common.Stride;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.CanonicalizerTool;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Helper functions to access complex objects on the application heap via the
 * {@link ConstantReflectionProvider}.
 */
public final class ConstantReflectionUtil {
    private ConstantReflectionUtil() {
    }

    public static int[] loadIntArrayConstant(ConstantReflectionProvider crp, JavaConstant targetArg, int maxLength) {
        int targetArgLength = Integer.min(maxLength, crp.readArrayLength(targetArg));
        int[] targetCharArray = new int[targetArgLength];
        for (int i = 0; i < targetArgLength; i++) {
            targetCharArray[i] = (char) crp.readArrayElement(targetArg, i).asInt();
        }
        return targetCharArray;
    }

    /**
     * Reads and zero extends 1, 2 or 4 bytes from a constant byte[], char[] or int[] array.
     *
     * @param array a constant java byte[], char[] or int[] array.
     * @param arrayKind the array's element kind ({@link JavaKind#Byte} for byte[],
     *            {@link JavaKind#Char} for char[] and {@link JavaKind#Int} for int[]).
     * @param stride the element width to read from the array. The stride must not be smaller than
     *            the array element kind, e.g. reading a byte from an int[] array is not supported.
     * @param index the index to read from, scaled to {@code stride}.
     * @return the bytes read, zero-extended to an int value.
     */
    public static int readTypePunned(ConstantReflectionProvider provider, JavaConstant array, JavaKind arrayKind, Stride stride, int index) {
        assert arrayKind == JavaKind.Byte || arrayKind == JavaKind.Char || arrayKind == JavaKind.Int;
        assert stride.value <= 4;
        assert stride.value >= arrayKind.getByteCount();
        if (arrayKind == JavaKind.Byte) {
            int i = index * stride.value;
            if (stride == Stride.S1) {
                return provider.readArrayElement(array, i).asInt() & 0xff;
            }
            if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
                if (stride == Stride.S2) {
                    return (provider.readArrayElement(array, i).asInt() & 0xff) |
                                    ((provider.readArrayElement(array, i + 1).asInt() & 0xff) << 8);
                } else {
                    return (provider.readArrayElement(array, i).asInt() & 0xff) |
                                    ((provider.readArrayElement(array, i + 1).asInt() & 0xff) << 8) |
                                    ((provider.readArrayElement(array, i + 2).asInt() & 0xff) << 16) |
                                    ((provider.readArrayElement(array, i + 3).asInt() & 0xff) << 24);
                }
            } else {
                if (stride == Stride.S2) {
                    return (provider.readArrayElement(array, i + 1).asInt() & 0xff) |
                                    ((provider.readArrayElement(array, i).asInt() & 0xff) << 8);
                } else {
                    return (provider.readArrayElement(array, i + 3).asInt() & 0xff) |
                                    ((provider.readArrayElement(array, i + 2).asInt() & 0xff) << 8) |
                                    ((provider.readArrayElement(array, i + 1).asInt() & 0xff) << 16) |
                                    ((provider.readArrayElement(array, i).asInt() & 0xff) << 24);
                }
            }
        } else if (arrayKind == JavaKind.Char) {
            if (stride == Stride.S2) {
                return provider.readArrayElement(array, index).asInt() & 0xffff;
            } else {
                assert stride == Stride.S4;
                int i = index * 2;
                if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
                    return (provider.readArrayElement(array, i).asInt() & 0xffff) |
                                    ((provider.readArrayElement(array, i + 1).asInt() & 0xffff) << 16);
                } else {
                    return (provider.readArrayElement(array, i + 1).asInt() & 0xffff) |
                                    ((provider.readArrayElement(array, i).asInt() & 0xffff) << 16);
                }
            }
        } else {
            assert stride == Stride.S4;
            return provider.readArrayElement(array, index).asInt();
        }
    }

    public interface ArrayBaseOffsetProvider {
        int getArrayBaseOffset(MetaAccessProvider metaAccess, ValueNode array, JavaKind arrayKind);
    }

    /**
     * Return {@code true} if the array and the starting offset are constants, and we can perform
     * {@code len} in-bounds constant reads starting at byte offset {@code offset}. Return
     * {@code false} if not everything constant or if we would try to read out of bounds.
     */
    public static boolean canFoldReads(CanonicalizerTool tool, ValueNode array, ValueNode offset, Stride stride, int len, ArrayBaseOffsetProvider arrayBaseOffsetProvider) {
        if (array.isJavaConstant() && ((ConstantNode) array).getStableDimension() >= 1 && offset.isJavaConstant()) {
            ResolvedJavaType arrayType = array.stamp(NodeView.DEFAULT).javaType(tool.getMetaAccess());
            if (arrayType.isArray()) {
                Integer arrayLength = tool.getConstantReflection().readArrayLength(array.asJavaConstant());
                Integer index = startIndex(tool, array, offset.asJavaConstant(), stride, arrayBaseOffsetProvider);
                return arrayLength != null && index != null && index >= 0 && index + len <= arrayLength;
            }
        }
        return false;
    }

    /**
     * Compute an element index from a byte offset from the start of the array object. Returns
     * {@code null} if the given offset is not aligned correctly for the element kind's stride.
     */
    public static Integer startIndex(CanonicalizerTool tool, ValueNode array, JavaConstant offset, Stride stride, ArrayBaseOffsetProvider arrayBaseOffsetProvider) {
        JavaKind arrayKind = array.stamp(NodeView.DEFAULT).javaType(tool.getMetaAccess()).getComponentType().getJavaKind();
        long elementOffset = offset.asLong() - arrayBaseOffsetProvider.getArrayBaseOffset(tool.getMetaAccess(), array, arrayKind);
        if (elementOffset % stride.value != 0) {
            return null;
        }
        return (int) (elementOffset / stride.value);
    }
}
