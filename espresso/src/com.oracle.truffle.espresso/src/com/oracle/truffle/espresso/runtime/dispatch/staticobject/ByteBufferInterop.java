/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.runtime.dispatch.staticobject;

import java.nio.ByteOrder;
import java.nio.ReadOnlyBufferException;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidBufferOffsetException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.interop.LookupAndInvokeKnownMethodNode;
import com.oracle.truffle.espresso.runtime.dispatch.messages.GenerateInteropNodes;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.JavaType;

@GenerateInteropNodes
@ExportLibrary(value = InteropLibrary.class, receiverType = StaticObject.class)
public class ByteBufferInterop extends EspressoInterop {

    @ExportMessage
    static boolean hasBufferElements(StaticObject receiver) {
        receiver.checkNotForeign();
        return true;
    }

    @ExportMessage
    static boolean isBufferWritable(StaticObject receiver,
                    @Bind("getMeta().java_nio_Buffer_isReadOnly") Method isReadOnlyMethod,
                    @Cached LookupAndInvokeKnownMethodNode lookup) {
        receiver.checkNotForeign();
        return !(boolean) lookup.execute(receiver, isReadOnlyMethod);
    }

    @ExportMessage
    static long getBufferSize(StaticObject receiver,
                    @Bind("getMeta().java_nio_Buffer_limit") Method limitMethod,
                    @Cached LookupAndInvokeKnownMethodNode size) {
        return (int) size.execute(receiver, limitMethod);
    }

    @ExportMessage
    static byte readBufferByte(StaticObject receiver, long byteOffset,
                    @Bind("getMeta().java_nio_ByteBuffer_getByte") Method getByteMethod,
                    @Cached LookupAndInvokeKnownMethodNode get,
                    @Bind("getMeta().java_nio_Buffer_limit") Method limitMethod,
                    @Cached LookupAndInvokeKnownMethodNode size,
                    @Cached.Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException, InvalidBufferOffsetException {
        int bufferSize = (int) size.execute(receiver, limitMethod);
        if (byteOffset < 0 || byteOffset >= bufferSize) {
            error.enter();
            throw InvalidBufferOffsetException.create(byteOffset, bufferSize);
        }
        return (byte) get.execute(receiver, getByteMethod, new Object[]{byteOffset});
    }

    @ExportMessage
    static void writeBufferByte(StaticObject receiver, long byteOffset, byte value,
                    @Bind("getMeta().java_nio_Buffer_isReadOnly") Method isReadOnlyMethod,
                    @Cached LookupAndInvokeKnownMethodNode lookup,
                    @Bind("getMeta().java_nio_ByteBuffer_put") Method putByteAtMethod,
                    @Cached LookupAndInvokeKnownMethodNode put,
                    @Bind("getMeta().java_nio_Buffer_limit") Method limitMethod,
                    @Cached LookupAndInvokeKnownMethodNode size,
                    @Cached.Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException, InvalidBufferOffsetException {
        int bufferSize = (int) size.execute(receiver, limitMethod);
        if (byteOffset < 0 || byteOffset >= bufferSize) {
            error.enter();
            throw InvalidBufferOffsetException.create(byteOffset, bufferSize);
        }
        if ((boolean) lookup.execute(receiver, isReadOnlyMethod)) {
            error.enter();
            throw UnsupportedMessageException.create(new ReadOnlyBufferException());
        }
        put.execute(receiver, putByteAtMethod, new Object[]{byteOffset, value});
    }

    @ExportMessage
    static short readBufferShort(StaticObject receiver, ByteOrder order, long byteOffset,
                    @Bind("getMeta().java_nio_ByteBuffer_getByte") Method getShortMethod,
                    @Cached LookupAndInvokeKnownMethodNode get,
                    @Bind("getMeta().java_nio_Buffer_limit") Method limitMethod,
                    @Cached LookupAndInvokeKnownMethodNode size,
                    @Cached.Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException, InvalidBufferOffsetException {
        int bufferSize = (int) size.execute(receiver, limitMethod);
        if (byteOffset < 0 || byteOffset >= bufferSize - 1) {
            error.enter();
            throw InvalidBufferOffsetException.create(byteOffset, bufferSize);
        }

        int b1 = (byte) get.execute(receiver, getShortMethod, new Object[]{byteOffset}) & 0xFF;
        int b2 = (byte) get.execute(receiver, getShortMethod, new Object[]{byteOffset + 1}) & 0xFF;
        if (order == ByteOrder.BIG_ENDIAN) {
            return (short) ((b1 << 8) | b2);
        } else {
            return (short) ((b2 << 8) | b1);
        }
    }

    @ExportMessage
    static void writeBufferShort(StaticObject receiver, ByteOrder order, long byteOffset, short value,
                    @Bind("getMeta().java_nio_Buffer_isReadOnly") Method isReadOnlyMethod,
                    @Cached LookupAndInvokeKnownMethodNode lookup,
                    @Bind("getMeta().java_nio_ByteBuffer_putShort") Method putShortMethod,
                    @Cached LookupAndInvokeKnownMethodNode put,
                    @Bind("getMeta().java_nio_Buffer_limit") Method limitMethod,
                    @Cached LookupAndInvokeKnownMethodNode size,
                    @Bind("getMeta().java_nio_ByteBuffer_order") Method orderMethod,
                    @Cached LookupAndInvokeKnownMethodNode orderNode,
                    @Bind("getMeta().java_nio_ByteBuffer_setOrder") Method setOrderMethod,
                    @Cached LookupAndInvokeKnownMethodNode setOrderNode,
                    @Cached.Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException, InvalidBufferOffsetException {
        int bufferSize = (int) size.execute(receiver, limitMethod);
        if (byteOffset < 0 || byteOffset >= bufferSize - 1) {
            error.enter();
            throw InvalidBufferOffsetException.create(byteOffset, bufferSize);
        }
        if ((boolean) lookup.execute(receiver, isReadOnlyMethod)) {
            error.enter();
            throw UnsupportedMessageException.create(new ReadOnlyBufferException());
        }
        StaticObject originalOrder = (StaticObject) orderNode.execute(receiver, orderMethod);
        StaticObject littleEndian = getLittleEndian(getMeta());
        boolean isLittleEndian = originalOrder == littleEndian;
        try {
            if (order == ByteOrder.BIG_ENDIAN && isLittleEndian) {
                setOrderNode.execute(receiver, setOrderMethod, new Object[]{getBigEndian(getMeta())});
            } else if (order == ByteOrder.LITTLE_ENDIAN && !isLittleEndian) {
                setOrderNode.execute(receiver, setOrderMethod, new Object[]{littleEndian});
            }
            put.execute(receiver, putShortMethod, new Object[]{byteOffset, value});
        } finally {
            setOrderNode.execute(receiver, setOrderMethod, new Object[]{originalOrder});
        }
    }

    @ExportMessage
    static int readBufferInt(StaticObject receiver, ByteOrder order, long byteOffset,
                    @Bind("getMeta().java_nio_ByteBuffer_getByte") Method getByteMethod,
                    @Cached LookupAndInvokeKnownMethodNode get,
                    @Bind("getMeta().java_nio_Buffer_limit") Method limitMethod,
                    @Cached LookupAndInvokeKnownMethodNode size,
                    @Cached.Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException, InvalidBufferOffsetException {
        int bufferSize = (int) size.execute(receiver, limitMethod);
        if (byteOffset < 0 || byteOffset >= bufferSize - 3) {
            error.enter();
            throw InvalidBufferOffsetException.create(byteOffset, bufferSize);
        }

        int b1 = (byte) get.execute(receiver, getByteMethod, new Object[]{byteOffset}) & 0xFF;
        int b2 = (byte) get.execute(receiver, getByteMethod, new Object[]{byteOffset + 1}) & 0xFF;
        int b3 = (byte) get.execute(receiver, getByteMethod, new Object[]{byteOffset + 2}) & 0xFF;
        int b4 = (byte) get.execute(receiver, getByteMethod, new Object[]{byteOffset + 3}) & 0xFF;
        if (order == ByteOrder.BIG_ENDIAN) {
            return (b1 << 24) | (b2 << 16) | (b3 << 8) | b4;
        } else {
            return (b4 << 24) | (b3 << 16) | (b2 << 8) | b1;
        }
    }

    @ExportMessage
    static void writeBufferInt(StaticObject receiver, ByteOrder order, long byteOffset, int value,
                    @Bind("getMeta().java_nio_Buffer_isReadOnly") Method isReadOnlyMethod,
                    @Cached LookupAndInvokeKnownMethodNode lookup,
                    @Bind("getMeta().java_nio_ByteBuffer_putInt") Method putIntMethod,
                    @Cached LookupAndInvokeKnownMethodNode put,
                    @Bind("getMeta().java_nio_Buffer_limit") Method limitMethod,
                    @Cached LookupAndInvokeKnownMethodNode size,
                    @Bind("getMeta().java_nio_ByteBuffer_order") Method orderMethod,
                    @Cached LookupAndInvokeKnownMethodNode orderNode,
                    @Bind("getMeta().java_nio_ByteBuffer_setOrder") Method setOrderMethod,
                    @Cached LookupAndInvokeKnownMethodNode setOrderNode,
                    @Cached.Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException, InvalidBufferOffsetException {
        int bufferSize = (int) size.execute(receiver, limitMethod);
        if (byteOffset < 0 || byteOffset >= bufferSize - 3) {
            error.enter();
            throw InvalidBufferOffsetException.create(byteOffset, bufferSize);
        }
        if ((boolean) lookup.execute(receiver, isReadOnlyMethod)) {
            error.enter();
            throw UnsupportedMessageException.create(new ReadOnlyBufferException());
        }
        StaticObject originalOrder = (StaticObject) orderNode.execute(receiver, orderMethod);
        StaticObject littleEndian = getLittleEndian(getMeta());
        boolean isLittleEndian = originalOrder == littleEndian;
        try {
            if (order == ByteOrder.BIG_ENDIAN && isLittleEndian) {
                setOrderNode.execute(receiver, setOrderMethod, new Object[]{getBigEndian(getMeta())});
            } else if (order == ByteOrder.LITTLE_ENDIAN && !isLittleEndian) {
                setOrderNode.execute(receiver, setOrderMethod, new Object[]{littleEndian});
            }
            put.execute(receiver, putIntMethod, new Object[]{byteOffset, value});
        } finally {
            setOrderNode.execute(receiver, setOrderMethod, new Object[]{originalOrder});
        }
    }

    @ExportMessage
    static long readBufferLong(StaticObject receiver, ByteOrder order, long byteOffset,
                    @Bind("getMeta().java_nio_ByteBuffer_getByte") Method getByteMethod,
                    @Cached LookupAndInvokeKnownMethodNode get,
                    @Bind("getMeta().java_nio_Buffer_limit") Method limitMethod,
                    @Cached LookupAndInvokeKnownMethodNode size,
                    @Cached.Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException, InvalidBufferOffsetException {
        int bufferSize = (int) size.execute(receiver, limitMethod);
        if (byteOffset < 0 || byteOffset >= bufferSize - 7) {
            error.enter();
            throw InvalidBufferOffsetException.create(byteOffset, bufferSize);
        }

        long b1 = (byte) get.execute(receiver, getByteMethod, new Object[]{byteOffset}) & 0xFF;
        long b2 = (byte) get.execute(receiver, getByteMethod, new Object[]{byteOffset + 1}) & 0xFF;
        long b3 = (byte) get.execute(receiver, getByteMethod, new Object[]{byteOffset + 2}) & 0xFF;
        long b4 = (byte) get.execute(receiver, getByteMethod, new Object[]{byteOffset + 3}) & 0xFF;
        long b5 = (byte) get.execute(receiver, getByteMethod, new Object[]{byteOffset + 4}) & 0xFF;
        long b6 = (byte) get.execute(receiver, getByteMethod, new Object[]{byteOffset + 5}) & 0xFF;
        long b7 = (byte) get.execute(receiver, getByteMethod, new Object[]{byteOffset + 6}) & 0xFF;
        long b8 = (byte) get.execute(receiver, getByteMethod, new Object[]{byteOffset + 7}) & 0xFF;
        if (order == ByteOrder.BIG_ENDIAN) {
            return (b1 << 56) | (b2 << 48) | (b3 << 40) | (b4 << 32) | (b5 << 24) | (b6 << 16) | (b7 << 8) | b8;
        } else {
            return (b8 << 56) | (b7 << 48) | (b6 << 40) | (b5 << 32) | (b4 << 24) | (b3 << 16) | (b2 << 8) | b1;
        }
    }

    @ExportMessage
    static void writeBufferLong(StaticObject receiver, ByteOrder order, long byteOffset, long value,
                    @Bind("getMeta().java_nio_Buffer_isReadOnly") Method isReadOnlyMethod,
                    @Cached LookupAndInvokeKnownMethodNode lookup,
                    @Bind("getMeta().java_nio_ByteBuffer_putLong") Method putLongMethod,
                    @Cached LookupAndInvokeKnownMethodNode put,
                    @Bind("getMeta().java_nio_Buffer_limit") Method limitMethod,
                    @Cached LookupAndInvokeKnownMethodNode size,
                    @Bind("getMeta().java_nio_ByteBuffer_order") Method orderMethod,
                    @Cached LookupAndInvokeKnownMethodNode orderNode,
                    @Bind("getMeta().java_nio_ByteBuffer_setOrder") Method setOrderMethod,
                    @Cached LookupAndInvokeKnownMethodNode setOrderNode,
                    @Cached.Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException, InvalidBufferOffsetException {
        int bufferSize = (int) size.execute(receiver, limitMethod);
        if (byteOffset < 0 || byteOffset >= bufferSize - 7) {
            error.enter();
            throw InvalidBufferOffsetException.create(byteOffset, bufferSize);
        }
        if ((boolean) lookup.execute(receiver, isReadOnlyMethod)) {
            error.enter();
            throw UnsupportedMessageException.create(new ReadOnlyBufferException());
        }
        StaticObject originalOrder = (StaticObject) orderNode.execute(receiver, orderMethod);
        StaticObject littleEndian = getLittleEndian(getMeta());
        boolean isLittleEndian = originalOrder == littleEndian;
        try {
            if (order == ByteOrder.BIG_ENDIAN && isLittleEndian) {
                setOrderNode.execute(receiver, setOrderMethod, new Object[]{getBigEndian(getMeta())});
            } else if (order == ByteOrder.LITTLE_ENDIAN && !isLittleEndian) {
                setOrderNode.execute(receiver, setOrderMethod, new Object[]{littleEndian});
            }
            put.execute(receiver, putLongMethod, new Object[]{byteOffset, value});
        } finally {
            setOrderNode.execute(receiver, setOrderMethod, new Object[]{originalOrder});
        }
    }

    @ExportMessage
    static float readBufferFloat(StaticObject receiver, ByteOrder order, long byteOffset,
                    @Bind("getMeta().java_nio_ByteBuffer_getByte") Method getByteMethod,
                    @Cached LookupAndInvokeKnownMethodNode get,
                    @Bind("getMeta().java_nio_Buffer_limit") Method limitMethod,
                    @Cached LookupAndInvokeKnownMethodNode size,
                    @Cached.Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException, InvalidBufferOffsetException {
        int bufferSize = (int) size.execute(receiver, limitMethod);
        if (byteOffset < 0 || byteOffset >= bufferSize - 3) {
            error.enter();
            throw InvalidBufferOffsetException.create(byteOffset, bufferSize);
        }

        int b1 = (byte) get.execute(receiver, getByteMethod, new Object[]{byteOffset}) & 0xFF;
        int b2 = (byte) get.execute(receiver, getByteMethod, new Object[]{byteOffset + 1}) & 0xFF;
        int b3 = (byte) get.execute(receiver, getByteMethod, new Object[]{byteOffset + 2}) & 0xFF;
        int b4 = (byte) get.execute(receiver, getByteMethod, new Object[]{byteOffset + 3}) & 0xFF;
        if (order == ByteOrder.BIG_ENDIAN) {
            return Float.intBitsToFloat((b1 << 24) | (b2 << 16) | (b3 << 8) | b4);
        } else {
            return Float.intBitsToFloat((b4 << 24) | (b3 << 16) | (b2 << 8) | b1);
        }
    }

    @ExportMessage
    static void writeBufferFloat(StaticObject receiver, ByteOrder order, long byteOffset, float value,
                    @Bind("getMeta().java_nio_Buffer_isReadOnly") Method isReadOnlyMethod,
                    @Cached LookupAndInvokeKnownMethodNode lookup,
                    @Bind("getMeta().java_nio_ByteBuffer_putFloat") Method putFloatMethod,
                    @Cached LookupAndInvokeKnownMethodNode put,
                    @Bind("getMeta().java_nio_Buffer_limit") Method limitMethod,
                    @Cached LookupAndInvokeKnownMethodNode size,
                    @Bind("getMeta().java_nio_ByteBuffer_order") Method orderMethod,
                    @Cached LookupAndInvokeKnownMethodNode orderNode,
                    @Bind("getMeta().java_nio_ByteBuffer_setOrder") Method setOrderMethod,
                    @Cached LookupAndInvokeKnownMethodNode setOrderNode,
                    @Cached.Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException, InvalidBufferOffsetException {
        int bufferSize = (int) size.execute(receiver, limitMethod);
        if (byteOffset < 0 || byteOffset >= bufferSize - 3) {
            error.enter();
            throw InvalidBufferOffsetException.create(byteOffset, bufferSize);
        }
        if ((boolean) lookup.execute(receiver, isReadOnlyMethod)) {
            error.enter();
            throw UnsupportedMessageException.create(new ReadOnlyBufferException());
        }
        StaticObject originalOrder = (StaticObject) orderNode.execute(receiver, orderMethod);
        StaticObject littleEndian = getLittleEndian(getMeta());
        boolean isLittleEndian = originalOrder == littleEndian;
        try {
            if (order == ByteOrder.BIG_ENDIAN && isLittleEndian) {
                setOrderNode.execute(receiver, setOrderMethod, new Object[]{getBigEndian(getMeta())});
            } else if (order == ByteOrder.LITTLE_ENDIAN && !isLittleEndian) {
                setOrderNode.execute(receiver, setOrderMethod, new Object[]{littleEndian});
            }
            put.execute(receiver, putFloatMethod, new Object[]{byteOffset, value});
        } finally {
            setOrderNode.execute(receiver, setOrderMethod, new Object[]{originalOrder});
        }
    }

    @ExportMessage
    static double readBufferDouble(StaticObject receiver, ByteOrder order, long byteOffset,
                    @Bind("getMeta().java_nio_ByteBuffer_getByte") Method getByteMethod,
                    @Cached LookupAndInvokeKnownMethodNode get,
                    @Bind("getMeta().java_nio_Buffer_limit") Method limitMethod,
                    @Cached LookupAndInvokeKnownMethodNode size,
                    @Cached.Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException, InvalidBufferOffsetException {
        int bufferSize = (int) size.execute(receiver, limitMethod);
        if (byteOffset < 0 || byteOffset >= bufferSize - 7) {
            error.enter();
            throw InvalidBufferOffsetException.create(byteOffset, bufferSize);
        }

        long b1 = (byte) get.execute(receiver, getByteMethod, new Object[]{byteOffset}) & 0xFF;
        long b2 = (byte) get.execute(receiver, getByteMethod, new Object[]{byteOffset + 1}) & 0xFF;
        long b3 = (byte) get.execute(receiver, getByteMethod, new Object[]{byteOffset + 2}) & 0xFF;
        long b4 = (byte) get.execute(receiver, getByteMethod, new Object[]{byteOffset + 3}) & 0xFF;
        long b5 = (byte) get.execute(receiver, getByteMethod, new Object[]{byteOffset + 4}) & 0xFF;
        long b6 = (byte) get.execute(receiver, getByteMethod, new Object[]{byteOffset + 5}) & 0xFF;
        long b7 = (byte) get.execute(receiver, getByteMethod, new Object[]{byteOffset + 6}) & 0xFF;
        long b8 = (byte) get.execute(receiver, getByteMethod, new Object[]{byteOffset + 7}) & 0xFF;
        if (order == ByteOrder.BIG_ENDIAN) {
            return Double.longBitsToDouble((b1 << 56) | (b2 << 48) | (b3 << 40) | (b4 << 32) | (b5 << 24) | (b6 << 16) | (b7 << 8) | b8);
        } else {
            return Double.longBitsToDouble((b8 << 56) | (b7 << 48) | (b6 << 40) | (b5 << 32) | (b4 << 24) | (b3 << 16) | (b2 << 8) | b1);
        }
    }

    @ExportMessage
    static void writeBufferDouble(StaticObject receiver, ByteOrder order, long byteOffset, double value,
                    @Bind("getMeta().java_nio_Buffer_isReadOnly") Method isReadOnlyMethod,
                    @Cached LookupAndInvokeKnownMethodNode lookup,
                    @Bind("getMeta().java_nio_ByteBuffer_putDouble") Method putDoubleMethod,
                    @Cached LookupAndInvokeKnownMethodNode put,
                    @Bind("getMeta().java_nio_Buffer_limit") Method limitMethod,
                    @Cached LookupAndInvokeKnownMethodNode size,
                    @Bind("getMeta().java_nio_ByteBuffer_order") Method orderMethod,
                    @Cached LookupAndInvokeKnownMethodNode orderNode,
                    @Bind("getMeta().java_nio_ByteBuffer_setOrder") Method setOrderMethod,
                    @Cached LookupAndInvokeKnownMethodNode setOrderNode,
                    @Cached.Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException, InvalidBufferOffsetException {
        int bufferSize = (int) size.execute(receiver, limitMethod);
        if (byteOffset < 0 || byteOffset >= bufferSize - 7) {
            error.enter();
            throw InvalidBufferOffsetException.create(byteOffset, bufferSize);
        }
        if ((boolean) lookup.execute(receiver, isReadOnlyMethod)) {
            error.enter();
            throw UnsupportedMessageException.create(new ReadOnlyBufferException());
        }
        StaticObject originalOrder = (StaticObject) orderNode.execute(receiver, orderMethod);
        StaticObject littleEndian = getLittleEndian(getMeta());
        boolean isLittleEndian = originalOrder == littleEndian;
        try {
            if (order == ByteOrder.BIG_ENDIAN && isLittleEndian) {
                setOrderNode.execute(receiver, setOrderMethod, new Object[]{getBigEndian(getMeta())});
            } else if (order == ByteOrder.LITTLE_ENDIAN && !isLittleEndian) {
                setOrderNode.execute(receiver, setOrderMethod, new Object[]{littleEndian});
            }
            put.execute(receiver, putDoubleMethod, new Object[]{byteOffset, value});
        } finally {
            setOrderNode.execute(receiver, setOrderMethod, new Object[]{originalOrder});
        }
    }

    @ExportMessage
    static void readBuffer(StaticObject receiver, long byteOffset, byte[] destination, int destinationOffset, int length,
                    @Bind("getMeta().java_nio_ByteBuffer_get") Method get,
                    @Cached LookupAndInvokeKnownMethodNode readBuffer,
                    @Cached.Shared("error") @Cached BranchProfile error) throws InvalidBufferOffsetException {
        if (byteOffset < 0 || Integer.MAX_VALUE < byteOffset + length) {
            error.enter();
            throw InvalidBufferOffsetException.create(byteOffset, length);
        }
        try {
            readBuffer.execute(receiver, get, new Object[]{byteOffset, StaticObject.wrap(destination, getMeta()), destinationOffset, length});
        } catch (Throwable t) {
            error.enter();
            throw InvalidBufferOffsetException.create(byteOffset, length);
        }
    }

    @TruffleBoundary
    static @JavaType(ByteOrder.class) StaticObject getLittleEndian(Meta meta) {
        StaticObject staticStorage = meta.java_nio_ByteOrder.tryInitializeAndGetStatics();
        return meta.java_nio_ByteOrder_LITTLE_ENDIAN.getObject(staticStorage);
    }

    @TruffleBoundary
    static @JavaType(ByteOrder.class) StaticObject getBigEndian(Meta meta) {
        StaticObject staticStorage = meta.java_nio_ByteOrder.tryInitializeAndGetStatics();
        return meta.java_nio_ByteOrder_BIG_ENDIAN.getObject(staticStorage);
    }
}
