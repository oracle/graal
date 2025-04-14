/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.wasmgc;

import static jdk.graal.compiler.core.common.spi.ForeignCallDescriptor.CallSideEffect.HAS_SIDE_EFFECT;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.word.LocationIdentity;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;

import jdk.vm.ci.meta.JavaKind;

/**
 * Support for arbitrary primitive unsafe accesses to arrays.
 * <p>
 * This is a fallback mechanism for when an access cannot be mapped to a single array access (i.e.
 * the offset is not aligned to the start of an element and/or does not match the element type). The
 * foreign calls in this class should be dispatched to when no regular accessor functions can be
 * used.
 * <p>
 * The access is emulated using individual byte accesses, modelling the underlying data as
 * little-endian.
 * <p>
 *
 * For example, to read a {@code short} value (see {@link #readArrayShort(Object, long)} at index
 * {@code baseOffset + 3} from an {@code int} array, the read would be done using two calls to
 * {@link #readArrayByte(Object, long)} at indices {@code baseOffset + 3} and {@code baseOffset + 4}
 * and the {@code short} reconstructed from the resulting bytes in
 * {@link #readArrayAndDeserialize(Object, long, int)}. Writes work in a similar way, first the
 * written primitive is serialized into a {@code byte} array and then written byte by byte (see
 * {@link #serializeAndWriteArray(Object, long, int, long)}).
 */
public class WasmGCUnalignedUnsafeSupport {
    /**
     * Accessor methods to read a primitive {@link JavaKind} from an array at some offset.
     * <p>
     * The signatures for these methods are {@code (Object array, int offset) -> <kind>}
     */
    public static final Map<JavaKind, SnippetRuntime.SubstrateForeignCallDescriptor> READ_ARRAY_FOREIGN_CALLS = new HashMap<>();
    /**
     * Sames as {@link #READ_ARRAY_FOREIGN_CALLS} but for writes.
     * <p>
     * The signatures for these methods are {@code (Object array, int offset, <kind>) -> void}
     */
    public static final Map<JavaKind, SnippetRuntime.SubstrateForeignCallDescriptor> WRITE_ARRAY_FOREIGN_CALLS = new HashMap<>();

    static {
        for (JavaKind componentKind : List.of(JavaKind.Boolean, JavaKind.Byte, JavaKind.Short, JavaKind.Char, JavaKind.Int, JavaKind.Long, JavaKind.Float, JavaKind.Double)) {
            READ_ARRAY_FOREIGN_CALLS.put(componentKind,
                            SnippetRuntime.findForeignCall(WasmGCUnalignedUnsafeSupport.class, "readArray" + componentKind.name(), HAS_SIDE_EFFECT, LocationIdentity.ANY_LOCATION));
            WRITE_ARRAY_FOREIGN_CALLS.put(componentKind,
                            SnippetRuntime.findForeignCall(WasmGCUnalignedUnsafeSupport.class, "writeArray" + componentKind.name(), HAS_SIDE_EFFECT, LocationIdentity.ANY_LOCATION));
        }
    }

    /**
     * Accessor to read a byte from an arbitrary primitive array at the given offset.
     * <p>
     * Will fail if the access is out of bounds or the given object is not a primitive array.
     * <p>
     * This method is also used as the building block for larger reads, these perform multiple byte
     * reads to reconstruct a larger primitive value.
     *
     * <h2>Index Calculations</h2>
     *
     * The calculations all use {@code scaledOffset} (see {@link #getScaledOffset(Object, long)}).
     * That offset is converted to an array index as follows:
     * {@code index = scaledOffset / indexScale}
     * <p>
     * At that index in the array, the desired byte is the {@code (scaledOffset % indexScale)}th
     * least significant byte of the primitive data.
     */
    @SubstrateForeignCallTarget(stubCallingConvention = false)
    public static byte readArrayByte(Object o, long offset) {
        int scaledOffset = getScaledOffset(o, offset);
        int indexScale = getArrayIndexScale(o);
        int index = scaledOffset / indexScale;
        /*
         * The raw bits read from the array expanded to a long. The indexScale number of least
         * significant bytes contain the actual data, the rest is not relevant.
         */
        long longBits = switch (o) {
            case boolean[] bools -> bools[index] ? 1 : 0;
            case byte[] bytes -> bytes[index];
            case short[] shorts -> shorts[index];
            case char[] chars -> chars[index];
            case int[] ints -> ints[index];
            case long[] longs -> longs[index];
            case float[] floats -> Float.floatToRawIntBits(floats[index]);
            case double[] doubles -> Double.doubleToRawLongBits(doubles[index]);
            default -> {
                if (WasmGCUnsafeSupport.includeErrorMessage()) {
                    WasmGCUnsafeSupport.fatalAccessError(o, "Unsupported type for unaligned array access", offset, true);
                }
                throw new UnsupportedOperationException();
            }
        };

        int rightShift = 8 * elementByteOffset(scaledOffset, indexScale);
        return (byte) (longBits >> rightShift);
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    public static boolean readArrayBoolean(Object o, long offset) {
        return readArrayByte(o, offset) != 0;
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    public static char readArrayChar(Object o, long offset) {
        return (char) readArrayAndDeserialize(o, offset, getArrayIndexScale(JavaKind.Char));
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    public static short readArrayShort(Object o, long offset) {
        return (short) readArrayAndDeserialize(o, offset, getArrayIndexScale(JavaKind.Short));
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    public static int readArrayInt(Object o, long offset) {
        return (int) readArrayAndDeserialize(o, offset, getArrayIndexScale(JavaKind.Int));
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    public static long readArrayLong(Object o, long offset) {
        return readArrayAndDeserialize(o, offset, getArrayIndexScale(JavaKind.Long));
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    public static float readArrayFloat(Object o, long offset) {
        return Float.intBitsToFloat((int) readArrayAndDeserialize(o, offset, getArrayIndexScale(JavaKind.Float)));
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    public static double readArrayDouble(Object o, long offset) {
        return Double.longBitsToDouble(readArrayAndDeserialize(o, offset, getArrayIndexScale(JavaKind.Double)));
    }

    /**
     * Reads {@code byteWidth} bytes from the given object (if it is a primitive array) at
     * {@code offset} and returns the raw bits as a {@code long}.
     *
     * @see #readArrayByte(Object, long)
     */
    private static long readArrayAndDeserialize(Object o, long offset, int byteWidth) {
        long result = 0;
        for (int i = 0; i < byteWidth; i++) {
            byte value = readArrayByte(o, offset + i);
            result |= (value & 0xffL) << (8 * i);
        }

        return result;
    }

    /**
     * The byte offset within an array element that the given offset points to.
     *
     * @param offset Byte offset relative to the array data base (not the object base).
     */
    private static int elementByteOffset(long offset, int byteWidth) {
        return (int) (offset % byteWidth);
    }

    /**
     * Accessor to write a byte to an arbitrary primitive array at the given offset.
     * <p>
     * Will fail if the access is out of bounds or the given object is not a primitive array.
     * <p>
     * This method is also used as the building block for larger writes, these split the data to
     * write into multiple bytes and perform individual byte writes.
     * <p>
     * See {@link #readArrayByte(Object, long)} for index calculations.
     */
    @SubstrateForeignCallTarget(stubCallingConvention = false)
    public static void writeArrayByte(Object o, long offset, byte value) {
        int scaledOffset = getScaledOffset(o, offset);
        int indexScale = getArrayIndexScale(o);
        int index = scaledOffset / indexScale;
        // Byte offset within the array element value
        int valueOffset = elementByteOffset(scaledOffset, indexScale);
        switch (o) {
            case boolean[] bools -> bools[index] = value != 0;
            case byte[] bytes -> bytes[index] = value;
            case short[] shorts -> shorts[index] = (short) setByte(shorts[index], valueOffset, value);
            case char[] chars -> chars[index] = (char) setByte(chars[index], valueOffset, value);
            case int[] ints -> ints[index] = (int) setByte(ints[index], valueOffset, value);
            case long[] longs -> longs[index] = setByte(longs[index], valueOffset, value);
            case float[] floats -> floats[index] = Float.intBitsToFloat((int) setByte(Float.floatToRawIntBits(floats[index]), valueOffset, value));
            case double[] doubles -> doubles[index] = Double.longBitsToDouble(setByte(Double.doubleToRawLongBits(doubles[index]), valueOffset, value));
            default -> {
                if (WasmGCUnsafeSupport.includeErrorMessage()) {
                    WasmGCUnsafeSupport.fatalAccessError(o, "Unsupported type for unaligned array access", offset, true);
                }
                throw new UnsupportedOperationException();
            }
        }
    }

    /**
     * Sets the nth byte in {@code currentValue} to {@code value} where {@code n = position}.
     */
    private static long setByte(long currentValue, int position, byte value) {
        int leftShift = position * 8;
        return (currentValue & ~(0xffL << leftShift)) | ((long) value << leftShift);
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    public static void writeArrayBoolean(Object o, long offset, boolean value) {
        writeArrayByte(o, offset, (byte) (value ? 1 : 0));
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    public static void writeArrayChar(Object o, long offset, char value) {
        serializeAndWriteArray(o, offset, getArrayIndexScale(JavaKind.Char), value);
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    public static void writeArrayShort(Object o, long offset, short value) {
        serializeAndWriteArray(o, offset, getArrayIndexScale(JavaKind.Short), value);
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    public static void writeArrayInt(Object o, long offset, int value) {
        serializeAndWriteArray(o, offset, getArrayIndexScale(JavaKind.Int), value);
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    public static void writeArrayLong(Object o, long offset, long value) {
        serializeAndWriteArray(o, offset, getArrayIndexScale(JavaKind.Long), value);
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    public static void writeArrayFloat(Object o, long offset, float value) {
        serializeAndWriteArray(o, offset, getArrayIndexScale(JavaKind.Float), Float.floatToRawIntBits(value));
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    public static void writeArrayDouble(Object o, long offset, double value) {
        serializeAndWriteArray(o, offset, getArrayIndexScale(JavaKind.Double), Double.doubleToRawLongBits(value));
    }

    /**
     * Writes the first {@code byteWidth} least significant bytes from {@code value} into the given
     * object (if it is a primitive array) at {@code offset}.
     *
     * @see #writeArrayByte(Object, long, byte)
     */
    private static void serializeAndWriteArray(Object o, long offset, int byteWidth, long value) {
        for (int i = 0; i < byteWidth; i++) {
            writeArrayByte(o, offset + i, (byte) (value >>> (8 * i)));
        }
    }

    /**
     * Calculates the byte-offset relative to the start of the array data from the original offset
     * (which is relative to the object start).
     * <p>
     * That value is the array index scaled using {@link #getArrayIndexScale(Object)}.
     *
     * @param o The primitive array. Will not fail if it's not a primitive array, but will just
     *            return {@code offset} as is. Callees should handle non-primitive arrays
     *            themselves.
     */
    private static int getScaledOffset(Object o, long offset) {
        return Math.toIntExact(offset - getArrayBaseOffset(o));
    }

    /**
     * Returns the byte offset of the first element of the given primitive array.
     * <p>
     * If the passed object is not a primitive array, this method returns 0.
     */
    private static int getArrayBaseOffset(Object o) {
        return switch (o) {
            case boolean[] bools -> getArrayBaseOffset(JavaKind.Boolean);
            case byte[] bytes -> getArrayBaseOffset(JavaKind.Byte);
            case short[] shorts -> getArrayBaseOffset(JavaKind.Short);
            case char[] chars -> getArrayBaseOffset(JavaKind.Char);
            case int[] ints -> getArrayBaseOffset(JavaKind.Int);
            case long[] longs -> getArrayBaseOffset(JavaKind.Long);
            case float[] floats -> getArrayBaseOffset(JavaKind.Float);
            case double[] doubles -> getArrayBaseOffset(JavaKind.Double);
            default -> 0;
        };
    }

    /**
     * Returns the byte size of array elements of the given primitive array.
     * <p>
     * If the passed object is not a primitive array, this method returns 1 (to not fail divisions
     * that may be performed using this value before errors are produced for non-primitive arrays).
     */
    private static int getArrayIndexScale(Object o) {
        return switch (o) {
            case boolean[] bools -> getArrayIndexScale(JavaKind.Boolean);
            case byte[] bytes -> getArrayIndexScale(JavaKind.Byte);
            case short[] shorts -> getArrayIndexScale(JavaKind.Short);
            case char[] chars -> getArrayIndexScale(JavaKind.Char);
            case int[] ints -> getArrayIndexScale(JavaKind.Int);
            case long[] longs -> getArrayIndexScale(JavaKind.Long);
            case float[] floats -> getArrayIndexScale(JavaKind.Float);
            case double[] doubles -> getArrayIndexScale(JavaKind.Double);
            default -> 1;
        };
    }

    @AlwaysInline("kind is a constant")
    private static int getArrayBaseOffset(JavaKind kind) {
        return ImageSingletons.lookup(ObjectLayout.class).getArrayBaseOffset(kind);
    }

    @AlwaysInline("kind is a constant")
    private static int getArrayIndexScale(JavaKind kind) {
        return ImageSingletons.lookup(ObjectLayout.class).getArrayIndexScale(kind);
    }
}
