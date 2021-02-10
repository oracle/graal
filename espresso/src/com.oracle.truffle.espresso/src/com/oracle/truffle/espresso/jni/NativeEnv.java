/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.jni;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.espresso._native.Buffer;
import com.oracle.truffle.espresso._native.Pointer;
import com.oracle.truffle.espresso._native.TruffleByteBuffer;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.nfi.spi.types.NativeSimpleType;

public abstract class NativeEnv {

    protected final Set<@Pointer TruffleObject> nativeClosures = Collections.newSetFromMap(new IdentityHashMap<>());

    public static NativeSimpleType word() {
        return NativeSimpleType.SINT64; // or SINT32
    }

    protected static ByteBuffer directByteBuffer(@Pointer TruffleObject addressPtr, long size, JavaKind kind) {
        return directByteBuffer(interopAsPointer(addressPtr), Math.multiplyExact(size, kind.getByteCount()));
    }

    protected static @Buffer TruffleObject asTruffleBuffer(@Pointer TruffleObject addressPtr, long size, JavaKind kind) {
        return TruffleByteBuffer.create(directByteBuffer(addressPtr, size, kind));
    }

    private static final Constructor<? extends ByteBuffer> constructor;
    private static final Field addressField;

    @SuppressWarnings("unchecked")
    private static Class<? extends ByteBuffer> getByteBufferClass(String className) {
        try {
            return (Class<? extends ByteBuffer>) Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    static {
        try {
            Class<? extends ByteBuffer> clazz = getByteBufferClass("java.nio.DirectByteBuffer");
            Class<? extends ByteBuffer> bufferClazz = getByteBufferClass("java.nio.Buffer");
            constructor = clazz.getDeclaredConstructor(long.class, int.class);
            addressField = bufferClazz.getDeclaredField("address");
            addressField.setAccessible(true);
            constructor.setAccessible(true);
        } catch (NoSuchMethodException | NoSuchFieldException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    @TruffleBoundary
    public static ByteBuffer directByteBuffer(long address, long capacity) {
        ByteBuffer buffer = null;
        try {
            buffer = constructor.newInstance(address, Math.toIntExact(capacity));
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
        buffer.order(ByteOrder.nativeOrder());
        return buffer;
    }

    public static long interopAsPointer(@Pointer TruffleObject interopPtr) {
        try {
            return InteropLibrary.getFactory().getUncached().asPointer(interopPtr);
        } catch (UnsupportedMessageException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    public static String interopPointerToString(@Pointer TruffleObject interopPtr) {
        return fromUTF8Ptr(interopAsPointer(interopPtr));
    }

    @TruffleBoundary
    protected static ByteBuffer directByteBuffer(@Pointer TruffleObject addressPtr, long capacity) {
        ByteBuffer buffer = null;
        try {
            long address = interopAsPointer(addressPtr);
            buffer = constructor.newInstance(address, Math.toIntExact(capacity));
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
        buffer.order(ByteOrder.nativeOrder());
        return buffer;
    }

    @TruffleBoundary
    protected static long byteBufferAddress(ByteBuffer byteBuffer) {
        try {
            return (long) addressField.get(byteBuffer);
        } catch (IllegalAccessException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    protected static @Pointer TruffleObject byteBufferPointer(ByteBuffer byteBuffer) {
        return new RawPointer(byteBufferAddress(byteBuffer));
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class RawPointer implements TruffleObject {
        private final long rawPtr;

        private static final RawPointer NULL = new RawPointer(0L);

        public static @Pointer TruffleObject nullInstance() {
            return NULL;
        }

        private RawPointer(long rawPtr) {
            this.rawPtr = rawPtr;
        }

        public static @Pointer TruffleObject create(long ptr) {
            if (ptr == 0L) {
                return NULL;
            }
            return new RawPointer(ptr);
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean isPointer() {
            return true;
        }

        @ExportMessage
        long asPointer() {
            return rawPtr;
        }

        @ExportMessage
        boolean isNull() {
            return rawPtr == 0L;
        }
    }

    protected static Object defaultValue(String returnType) {
        if (returnType.equals("boolean")) {
            return false;
        }
        if (returnType.equals("byte")) {
            return (byte) 0;
        }
        if (returnType.equals("char")) {
            return (char) 0;
        }
        if (returnType.equals("short")) {
            return (short) 0;
        }
        if (returnType.equals("int")) {
            return 0;
        }
        if (returnType.equals("float")) {
            return 0.0F;
        }
        if (returnType.equals("double")) {
            return 0.0;
        }
        if (returnType.equals("long")) {
            return 0L;
        }
        if (returnType.equals("StaticObject")) {
            return 0L; // NULL handle
        }
        return StaticObject.NULL;
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

}
