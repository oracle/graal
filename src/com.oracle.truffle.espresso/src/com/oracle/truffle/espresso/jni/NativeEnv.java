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
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.nfi.spi.types.NativeSimpleType;

public abstract class NativeEnv {

    static final Map<Class<?>, NativeSimpleType> classToNative = buildClassToNative();

    static Map<Class<?>, NativeSimpleType> buildClassToNative() {
        Map<Class<?>, NativeSimpleType> map = new HashMap<>();
        map.put(boolean.class, NativeSimpleType.SINT8);
        map.put(byte.class, NativeSimpleType.SINT8);
        map.put(short.class, NativeSimpleType.SINT16);
        map.put(char.class, NativeSimpleType.SINT16);
        map.put(int.class, NativeSimpleType.SINT32);
        map.put(float.class, NativeSimpleType.FLOAT);
        map.put(long.class, NativeSimpleType.SINT64);
        map.put(double.class, NativeSimpleType.DOUBLE);
        map.put(void.class, NativeSimpleType.VOID);
        map.put(String.class, NativeSimpleType.STRING);
        return Collections.unmodifiableMap(map);
    }

    public static long unwrapPointer(Object nativePointer) {
        try {
            return InteropLibrary.getFactory().getUncached().asPointer(nativePointer);
        } catch (UnsupportedMessageException e) {
            throw new RuntimeException(e);
        }
    }

    public static NativeSimpleType classToType(Class<?> clazz, boolean javaToNative) {
        return classToNative.getOrDefault(clazz, javaToNative ? NativeSimpleType.NULLABLE : NativeSimpleType.OBJECT);
    }

    protected static ByteBuffer directByteBuffer(long address, long capacity, JavaKind kind) {
        return directByteBuffer(address, Math.multiplyExact(capacity, kind.getByteCount()));
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

    protected static ByteBuffer directByteBuffer(long address, long capacity) {
        ByteBuffer buffer = null;
        try {
            buffer = constructor.newInstance(address, Math.toIntExact(capacity));
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
        buffer.order(ByteOrder.nativeOrder());
        return buffer;
    }

    protected static long byteBufferAddress(ByteBuffer byteBuffer) {
        try {
            return (long) addressField.get(byteBuffer);
        } catch (IllegalAccessException e) {
            throw EspressoError.shouldNotReachHere(e);
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

    public static TruffleObject loadLibrary(List<Path> searchPaths, String name) {
        for (Path path : searchPaths) {
            Path libPath = path.resolve(System.mapLibraryName(name));
            try {
                return NativeLibrary.loadLibrary(libPath.toAbsolutePath());
            } catch (UnsatisfiedLinkError e) {
                // continue
            }
        }
        throw EspressoError.shouldNotReachHere("Cannot load library: " + name);
    }

    public static String fromUTF8Ptr(@Word long rawBytesPtr) {
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
