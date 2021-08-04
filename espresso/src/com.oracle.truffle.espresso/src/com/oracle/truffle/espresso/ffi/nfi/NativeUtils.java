/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.ffi.nfi;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.LongBuffer;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.espresso.ffi.Pointer;
import com.oracle.truffle.espresso.ffi.RawPointer;
import com.oracle.truffle.espresso.jni.ModifiedUtf8;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.vm.UnsafeAccess;

import sun.misc.Unsafe;

public final class NativeUtils {

    private static final Unsafe UNSAFE = UnsafeAccess.get();

    public static ByteBuffer directByteBuffer(@Pointer TruffleObject addressPtr, long size, JavaKind kind) {
        return directByteBuffer(addressPtr, Math.multiplyExact(size, kind.getByteCount()));
    }

    private static final Class<?> DIRECT_BYTE_BUFFER_CLASS;
    private static final long ADDRESS_FIELD_OFFSET;
    private static final long CAPACITY_FIELD_OFFSET;

    static {
        try {
            ADDRESS_FIELD_OFFSET = UNSAFE.objectFieldOffset(java.nio.Buffer.class.getDeclaredField("address"));
            CAPACITY_FIELD_OFFSET = UNSAFE.objectFieldOffset(java.nio.Buffer.class.getDeclaredField("capacity"));
            DIRECT_BYTE_BUFFER_CLASS = Class.forName("java.nio.DirectByteBuffer");
        } catch (ClassNotFoundException | NoSuchFieldException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    @TruffleBoundary
    public static ByteBuffer directByteBuffer(long address, long longCapacity) {
        int capacity = Math.toIntExact(longCapacity);
        ByteBuffer buffer = null;
        try {
            buffer = (ByteBuffer) UNSAFE.allocateInstance(DIRECT_BYTE_BUFFER_CLASS);
        } catch (InstantiationException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
        UNSAFE.putLong(buffer, ADDRESS_FIELD_OFFSET, address);
        UNSAFE.putInt(buffer, CAPACITY_FIELD_OFFSET, capacity);
        buffer.clear();
        buffer.order(ByteOrder.nativeOrder());
        return buffer;
    }

    @TruffleBoundary
    public static long interopAsPointer(@Pointer TruffleObject interopPtr) {
        try {
            return InteropLibrary.getUncached().asPointer(interopPtr);
        } catch (UnsupportedMessageException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    public static String interopPointerToString(@Pointer TruffleObject interopPtr) {
        return fromUTF8Ptr(interopAsPointer(interopPtr));
    }

    @TruffleBoundary
    public static ByteBuffer directByteBuffer(@Pointer TruffleObject addressPtr, long capacity) {
        return directByteBuffer(interopAsPointer(addressPtr), capacity);
    }

    public static void writeToIntPointer(TruffleObject pointer, int value) {
        writeToIntPointer(InteropLibrary.getUncached(), pointer, value);
    }

    @TruffleBoundary
    public static void writeToIntPointer(InteropLibrary library, TruffleObject pointer, int value) {
        if (library.isNull(pointer)) {
            throw new NullPointerException();
        }
        IntBuffer resultPointer = NativeUtils.directByteBuffer(pointer, 1, JavaKind.Int).asIntBuffer();
        resultPointer.put(value);
    }

    public static void writeToLongPointer(TruffleObject pointer, long value) {
        writeToLongPointer(InteropLibrary.getUncached(), pointer, value);
    }

    @TruffleBoundary
    public static void writeToLongPointer(InteropLibrary library, TruffleObject pointer, long value) {
        if (library.isNull(pointer)) {
            throw new NullPointerException();
        }
        LongBuffer resultPointer = NativeUtils.directByteBuffer(pointer, 1, JavaKind.Long).asLongBuffer();
        resultPointer.put(value);
    }

    public static void writeToPointerPointer(TruffleObject pointer, TruffleObject value) {
        writeToPointerPointer(InteropLibrary.getUncached(), pointer, value);
    }

    public static void writeToPointerPointer(InteropLibrary library, TruffleObject pointer, TruffleObject value) {
        writeToLongPointer(library, pointer, NativeUtils.interopAsPointer(value));
    }

    public static TruffleObject dereferencePointerPointer(TruffleObject pointer) {
        return dereferencePointerPointer(InteropLibrary.getUncached(), pointer);
    }

    @TruffleBoundary
    public static TruffleObject dereferencePointerPointer(InteropLibrary library, TruffleObject pointer) {
        if (library.isNull(pointer)) {
            throw new NullPointerException();
        }
        LongBuffer buffer = NativeUtils.directByteBuffer(pointer, 1, JavaKind.Long).asLongBuffer();
        return RawPointer.create(buffer.get());
    }

    @TruffleBoundary
    public static long byteBufferAddress(ByteBuffer byteBuffer) {
        assert byteBuffer.isDirect();
        return UNSAFE.getLong(byteBuffer, ADDRESS_FIELD_OFFSET);
    }

    public static @Pointer TruffleObject byteBufferPointer(ByteBuffer byteBuffer) {
        return new RawPointer(byteBufferAddress(byteBuffer));
    }

    public static String fromUTF8Ptr(@Pointer TruffleObject buffPtr) {
        return fromUTF8Ptr(interopAsPointer(buffPtr));
    }

    @TruffleBoundary
    public static String fromUTF8Ptr(long rawBytesPtr) {
        if (rawBytesPtr == 0) {
            return null;
        }
        ByteBuffer buf = directByteBuffer(rawBytesPtr, Integer.MAX_VALUE);

        int utfLen = 0;
        while (buf.get() != 0) {
            utfLen++;
        }

        byte[] bytes = new byte[utfLen];
        buf.clear();
        buf.get(bytes);
        try {
            return ModifiedUtf8.toJavaString(bytes);
        } catch (IOException e) {
            // return StaticObject.NULL;
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    @TruffleBoundary
    public static ByteBuffer allocateDirect(int capacity) {
        return ByteBuffer.allocateDirect(capacity).order(ByteOrder.nativeOrder());
    }
}
