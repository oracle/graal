/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.object;

import java.lang.reflect.Field;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.nodes.ExplodeLoop;

import sun.misc.Unsafe;

final class UnsafeAccess {

    private static final Unsafe UNSAFE = getUnsafe();

    private UnsafeAccess() {
    }

    @SuppressWarnings("deprecation")
    static long objectFieldOffset(Field field) {
        return UNSAFE.objectFieldOffset(field);
    }

    /**
     * Cast {@code boolean} to {@code int}.
     */
    static int intCast(boolean value) {
        return value ? 1 : 0;
    }

    /**
     * Cast {@code int} to {@code boolean}.
     */
    static boolean booleanCast(int value) {
        return value != 0;
    }

    /**
     * Casts the given value to the value of the given type without any checks. The type, nonNull,
     * and exact must evaluate to a constant. The condition parameter gives a hint to the compiler
     * under which circumstances this cast can be moved to an earlier location in the program.
     *
     * @param value the value that is known to have the specified type
     * @param type the specified new type of the value
     * @param condition the condition that makes this cast safe also at an earlier location
     * @param nonNull whether value is known to never be null
     * @param exact whether the value is known to be of exactly the specified class
     * @return the value to be cast to the new type
     */
    @SuppressWarnings("unchecked")
    static <T> T unsafeCast(Object value, Class<T> type, boolean condition, boolean nonNull, boolean exact) {
        return (T) value;
    }

    static <T> T unsafeCast(Object value, Class<T> type, boolean condition, boolean nonNull) {
        return unsafeCast(value, type, condition, nonNull, false);
    }

    /**
     * Like {@link System#arraycopy}, but kills any location.
     */
    static void arraycopy(Object from, int fromIndex, Object to, int toIndex, int length) {
        System.arraycopy(from, fromIndex, to, toIndex, length);
    }

    /**
     * Reads a boolean value from an object, with optional location identity and guarding condition.
     *
     * @param receiver the object that is accessed
     * @param offset the offset at which to access the object in bytes
     * @param condition the condition that guards this access, or {@code false}
     * @param locationIdentity the location identity token, or {@link #ANY_LOCATION}, or null
     * @return the accessed value
     */
    static boolean unsafeGetBoolean(Object receiver, long offset, boolean condition, Object locationIdentity) {
        assert receiver != null;
        return UNSAFE.getBoolean(receiver, offset);
    }

    /**
     * Reads a byte value from an object, with optional location identity and guarding condition.
     *
     * @param receiver the object that is accessed
     * @param offset the offset at which to access the object in bytes
     * @param condition the condition that guards this access, or {@code false}
     * @param locationIdentity the location identity token, or {@link #ANY_LOCATION}, or null
     * @return the accessed value
     */
    static byte unsafeGetByte(Object receiver, long offset, boolean condition, Object locationIdentity) {
        assert receiver != null;
        return UNSAFE.getByte(receiver, offset);
    }

    /**
     * Reads a short value from an object, with optional location identity and guarding condition.
     *
     * @param receiver the object that is accessed
     * @param offset the offset at which to access the object in bytes
     * @param condition the condition that guards this access, or {@code false}
     * @param locationIdentity the location identity token, or {@link #ANY_LOCATION}, or null
     * @return the accessed value
     */
    static short unsafeGetShort(Object receiver, long offset, boolean condition, Object locationIdentity) {
        assert receiver != null;
        return UNSAFE.getShort(receiver, offset);
    }

    /**
     * Reads an int value from an object, with optional location identity and guarding condition.
     *
     * @param receiver the object that is accessed
     * @param offset the offset at which to access the object in bytes
     * @param condition the condition that guards this access, or {@code false}
     * @param locationIdentity the location identity token, or {@link #ANY_LOCATION}, or null
     * @return the accessed value
     */
    static int unsafeGetInt(Object receiver, long offset, boolean condition, Object locationIdentity) {
        assert receiver != null;
        return UNSAFE.getInt(receiver, offset);
    }

    /**
     * Reads a long value from an object, with optional location identity and guarding condition.
     *
     * @param receiver the object that is accessed
     * @param offset the offset at which to access the object in bytes
     * @param condition the condition that guards this access, or {@code false}
     * @param locationIdentity the location identity token, or {@link #ANY_LOCATION}, or null
     * @return the accessed value
     */
    static long unsafeGetLong(Object receiver, long offset, boolean condition, Object locationIdentity) {
        assert receiver != null;
        return UNSAFE.getLong(receiver, offset);
    }

    /**
     * Reads a float value from an object, with optional location identity and guarding condition.
     *
     * @param receiver the object that is accessed
     * @param offset the offset at which to access the object in bytes
     * @param condition the condition that guards this access, or {@code false}
     * @param locationIdentity the location identity token, or {@link #ANY_LOCATION}, or null
     * @return the accessed value
     */
    static float unsafeGetFloat(Object receiver, long offset, boolean condition, Object locationIdentity) {
        assert receiver != null;
        return UNSAFE.getFloat(receiver, offset);
    }

    /**
     * Reads a double value from an object, with optional location identity and guarding condition.
     *
     * @param receiver the object that is accessed
     * @param offset the offset at which to access the object in bytes
     * @param condition the condition that guards this access, or {@code false}
     * @param locationIdentity the location identity token, or {@link #ANY_LOCATION}, or null
     * @return the accessed value
     */
    static double unsafeGetDouble(Object receiver, long offset, boolean condition, Object locationIdentity) {
        assert receiver != null;
        return UNSAFE.getDouble(receiver, offset);
    }

    /**
     * Reads an Object value from an object, with optional location identity and guarding condition.
     *
     * @param receiver the object that is accessed
     * @param offset the offset at which to access the object in bytes
     * @param condition the condition that guards this access, or {@code false}
     * @param locationIdentity the location identity token, or {@link #ANY_LOCATION}, or null
     * @return the accessed value
     */
    static Object unsafeGetObject(Object receiver, long offset, boolean condition, Object locationIdentity) {
        assert receiver != null;
        return UNSAFE.getObject(receiver, offset);
    }

    /**
     * Writes a boolean value into an object, with an optional custom location identity.
     *
     * @param receiver the object that is written to
     * @param offset the offset at which to write to the object in bytes
     * @param value the value to be written
     * @param locationIdentity the location identity token, or {@link #ANY_LOCATION}, or null
     */
    static void unsafePutBoolean(Object receiver, long offset, boolean value, Object locationIdentity) {
        assert receiver != null;
        UNSAFE.putBoolean(receiver, offset, value);
    }

    /**
     * Writes a byte value into an object, with an optional custom location identity.
     *
     * @param receiver the object that is written to
     * @param offset the offset at which to write to the object in bytes
     * @param value the value to be written
     * @param locationIdentity the location identity token, or {@link #ANY_LOCATION}, or null
     */
    static void unsafePutByte(Object receiver, long offset, byte value, Object locationIdentity) {
        assert receiver != null;
        UNSAFE.putByte(receiver, offset, value);
    }

    /**
     * Writes a short value into an object, with an optional custom location identity.
     *
     * @param receiver the object that is written to
     * @param offset the offset at which to write to the object in bytes
     * @param value the value to be written
     * @param locationIdentity the location identity token, or {@link #ANY_LOCATION}, or null
     */
    static void unsafePutShort(Object receiver, long offset, short value, Object locationIdentity) {
        assert receiver != null;
        UNSAFE.putShort(receiver, offset, value);
    }

    /**
     * Writes an int value into an object, with an optional custom location identity.
     *
     * @param receiver the object that is written to
     * @param offset the offset at which to write to the object in bytes
     * @param value the value to be written
     * @param locationIdentity the location identity token, or {@link #ANY_LOCATION}, or null
     */
    static void unsafePutInt(Object receiver, long offset, int value, Object locationIdentity) {
        assert receiver != null;
        UNSAFE.putInt(receiver, offset, value);
    }

    /**
     * Writes a long value into an object, with an optional custom location identity.
     *
     * @param receiver the object that is written to
     * @param offset the offset at which to write to the object in bytes
     * @param value the value to be written
     * @param locationIdentity the location identity token, or {@link #ANY_LOCATION}, or null
     */
    static void unsafePutLong(Object receiver, long offset, long value, Object locationIdentity) {
        assert receiver != null;
        UNSAFE.putLong(receiver, offset, value);
    }

    /**
     * Writes a float value into an object, with an optional custom location identity.
     *
     * @param receiver the object that is written to
     * @param offset the offset at which to write to the object in bytes
     * @param value the value to be written
     * @param locationIdentity the location identity token, or {@link #ANY_LOCATION}, or null
     */
    static void unsafePutFloat(Object receiver, long offset, float value, Object locationIdentity) {
        assert receiver != null;
        UNSAFE.putFloat(receiver, offset, value);
    }

    /**
     * Writes a double value into an object, with an optional custom location identity.
     *
     * @param receiver the object that is written to
     * @param offset the offset at which to write to the object in bytes
     * @param value the value to be written
     * @param locationIdentity the location identity token, or {@link #ANY_LOCATION}, or null
     */
    static void unsafePutDouble(Object receiver, long offset, double value, Object locationIdentity) {
        assert receiver != null;
        UNSAFE.putDouble(receiver, offset, value);
    }

    /**
     * Writes an Object value into an object, with an optional custom location identity.
     *
     * @param receiver the object that is written to
     * @param offset the offset at which to write to the object in bytes
     * @param value the value to be written
     * @param locationIdentity the location identity token, or {@link #ANY_LOCATION}, or null
     */
    static void unsafePutObject(Object receiver, long offset, Object value, Object locationIdentity) {
        assert receiver != null;
        UNSAFE.putObject(receiver, offset, value);
    }

    static int unsafeGetFinalInt(Object receiver, long offset, boolean condition, Object locationIdentity) {
        return unsafeGetInt(receiver, offset, condition, locationIdentity);
    }

    static long unsafeGetFinalLong(Object receiver, long offset, boolean condition, Object locationIdentity) {
        return unsafeGetLong(receiver, offset, condition, locationIdentity);
    }

    static double unsafeGetFinalDouble(Object receiver, long offset, boolean condition, Object locationIdentity) {
        return unsafeGetDouble(receiver, offset, condition, locationIdentity);
    }

    static Object unsafeGetFinalObject(Object receiver, long offset, boolean condition, Object locationIdentity) {
        return unsafeGetObject(receiver, offset, condition, locationIdentity);
    }

    static final Object ANY_LOCATION = new Object();

    private static final int MAX_UNROLL = 32;
    private static final boolean USE_ARRAYCOPY = true;

    static void arrayCopy(Object[] from, Object[] to, int length) {
        if (CompilerDirectives.isPartialEvaluationConstant(length) && length <= MAX_UNROLL) {
            arrayCopyUnroll(from, to, length);
        } else if (USE_ARRAYCOPY) {
            UnsafeAccess.arraycopy(from, 0, to, 0, length);
        } else {
            arrayCopyLoop(from, to, length);
        }
    }

    @ExplodeLoop
    private static void arrayCopyUnroll(Object[] from, Object[] to, int length) {
        for (int i = 0; i < length; ++i) {
            Object value = UnsafeAccess.unsafeGetObject(from, Unsafe.ARRAY_OBJECT_BASE_OFFSET + i * (long) Unsafe.ARRAY_OBJECT_INDEX_SCALE, false, null);
            UnsafeAccess.unsafePutObject(to, Unsafe.ARRAY_OBJECT_BASE_OFFSET + i * (long) Unsafe.ARRAY_OBJECT_INDEX_SCALE, value, UnsafeAccess.ANY_LOCATION);
        }
    }

    private static void arrayCopyLoop(Object[] from, Object[] to, int length) {
        for (int i = 0; i < length; ++i) {
            Object value = UnsafeAccess.unsafeGetObject(from, Unsafe.ARRAY_OBJECT_BASE_OFFSET + i * (long) Unsafe.ARRAY_OBJECT_INDEX_SCALE, false, null);
            UnsafeAccess.unsafePutObject(to, Unsafe.ARRAY_OBJECT_BASE_OFFSET + i * (long) Unsafe.ARRAY_OBJECT_INDEX_SCALE, value, UnsafeAccess.ANY_LOCATION);
        }
    }

    static void arrayCopy(int[] from, int[] to, int length) {
        if (CompilerDirectives.isPartialEvaluationConstant(length) && length <= MAX_UNROLL) {
            arrayCopyUnroll(from, to, length);
        } else if (USE_ARRAYCOPY) {
            UnsafeAccess.arraycopy(from, 0, to, 0, length);
        } else {
            arrayCopyLoop(from, to, length);
        }
    }

    @ExplodeLoop
    private static void arrayCopyUnroll(int[] from, int[] to, int length) {
        for (int i = 0; i < length; ++i) {
            int value = UnsafeAccess.unsafeGetInt(from, Unsafe.ARRAY_INT_BASE_OFFSET + i * (long) Unsafe.ARRAY_INT_INDEX_SCALE, false, null);
            UnsafeAccess.unsafePutInt(to, Unsafe.ARRAY_INT_BASE_OFFSET + i * (long) Unsafe.ARRAY_INT_INDEX_SCALE, value, UnsafeAccess.ANY_LOCATION);
        }
    }

    private static void arrayCopyLoop(int[] from, int[] to, int length) {
        for (int i = 0; i < length; ++i) {
            int value = UnsafeAccess.unsafeGetInt(from, Unsafe.ARRAY_INT_BASE_OFFSET + i * (long) Unsafe.ARRAY_INT_INDEX_SCALE, false, null);
            UnsafeAccess.unsafePutInt(to, Unsafe.ARRAY_INT_BASE_OFFSET + i * (long) Unsafe.ARRAY_INT_INDEX_SCALE, value, UnsafeAccess.ANY_LOCATION);
        }
    }

    private static Unsafe getUnsafe() {
        try {
            return Unsafe.getUnsafe();
        } catch (SecurityException e) {
        }
        try {
            Field theUnsafeInstance = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafeInstance.setAccessible(true);
            return (Unsafe) theUnsafeInstance.get(Unsafe.class);
        } catch (Exception e) {
            throw new RuntimeException("exception while trying to get Unsafe.theUnsafe via reflection:", e);
        }
    }
}
