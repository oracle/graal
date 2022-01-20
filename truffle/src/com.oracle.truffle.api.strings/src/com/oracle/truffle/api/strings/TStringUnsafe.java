/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.api.strings;

import java.lang.reflect.Field;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import sun.misc.Unsafe;

final class TStringUnsafe {

    @TruffleBoundary
    private static int getJavaSpecificationVersion() {
        String value = System.getProperty("java.specification.version");
        if (value.startsWith("1.")) {
            value = value.substring(2);
        }
        return Integer.parseInt(value);
    }

    /**
     * The integer value corresponding to the value of the {@code java.specification.version} system
     * property after any leading {@code "1."} has been stripped.
     */
    static final int JAVA_SPEC = getJavaSpecificationVersion();

    private static final sun.misc.Unsafe UNSAFE = getUnsafe();
    private static final long javaStringValueFieldOffset;
    private static final long javaStringCoderFieldOffset;

    static {
        Field valueField = getStringDeclaredField("value");
        javaStringValueFieldOffset = UNSAFE.objectFieldOffset(valueField);
        if (JAVA_SPEC <= 8) {
            javaStringCoderFieldOffset = 0;
        } else {
            Field coderField = getStringDeclaredField("coder");
            javaStringCoderFieldOffset = UNSAFE.objectFieldOffset(coderField);
        }
    }

    @TruffleBoundary
    private static Field getStringDeclaredField(String name) {
        try {
            return String.class.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("failed to get " + name + " field offset", e);
        }
    }

    @TruffleBoundary
    private static Unsafe getUnsafe() {
        try {
            return Unsafe.getUnsafe();
        } catch (SecurityException e1) {
            try {
                Field theUnsafeInstance = Unsafe.class.getDeclaredField("theUnsafe");
                theUnsafeInstance.setAccessible(true);
                return (Unsafe) theUnsafeInstance.get(Unsafe.class);
            } catch (Exception e2) {
                throw new RuntimeException("exception while trying to get Unsafe.theUnsafe via reflection:", e2);
            }
        }
    }

    static char[] getJavaStringArrayJDK8(String str) {
        assert JAVA_SPEC <= 8;
        Object value = UNSAFE.getObject(str, javaStringValueFieldOffset);
        assert value instanceof char[];
        return (char[]) value;
    }

    static byte[] getJavaStringArrayJDK9(String str) {
        assert JAVA_SPEC > 8;
        Object value = UNSAFE.getObject(str, javaStringValueFieldOffset);
        assert value instanceof byte[];
        return (byte[]) value;
    }

    static int getJavaStringStride(String s) {
        return JAVA_SPEC <= 8 ? 1 : UNSAFE.getByte(s, javaStringCoderFieldOffset);
    }

    static byte getByteManaged(Object array, long byteOffset) {
        return UNSAFE.getByte(array, byteOffset);
    }

    static byte getByteNative(long array, long byteOffset) {
        return UNSAFE.getByte(array + byteOffset);
    }

    static char getCharManaged(Object array, long byteOffset) {
        return UNSAFE.getChar(array, byteOffset);
    }

    static char getCharNative(long array, long byteOffset) {
        return UNSAFE.getChar(array + byteOffset);
    }

    static int getIntManaged(Object array, long byteOffset) {
        return UNSAFE.getInt(array, byteOffset);
    }

    static int getIntNative(long array, long byteOffset) {
        return UNSAFE.getInt(array + byteOffset);
    }

    static long getLongManaged(Object array, long byteOffset) {
        return UNSAFE.getLong(array, byteOffset);
    }

    static long getLongNative(long array) {
        return UNSAFE.getLong(array);
    }

    static void putByteManaged(byte[] array, long byteOffset, byte value) {
        UNSAFE.putByte(array, byteOffset, value);
    }

    static void putByteNative(long array, long byteOffset, byte value) {
        UNSAFE.putByte(array + byteOffset, value);
    }

    static void putCharManaged(byte[] array, long byteOffset, char value) {
        UNSAFE.putChar(array, byteOffset, value);
    }

    static void putCharNative(long array, long byteOffset, char value) {
        UNSAFE.putChar(array + byteOffset, value);
    }

    static void putIntManaged(byte[] array, long byteOffset, int value) {
        UNSAFE.putInt(array, byteOffset, value);
    }

    static void putIntNative(long array, long byteOffset, int value) {
        UNSAFE.putInt(array + byteOffset, value);
    }

    static void copyFromNative(long arraySrc, int offsetSrc, byte[] arrayDst, long offsetDst, int byteLength) {
        UNSAFE.copyMemory(null, arraySrc + offsetSrc, arrayDst, Unsafe.ARRAY_BYTE_BASE_OFFSET + offsetDst, byteLength);
    }
}
