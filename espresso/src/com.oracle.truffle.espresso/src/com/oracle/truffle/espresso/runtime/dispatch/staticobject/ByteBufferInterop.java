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
                    @Cached.Shared("error") @Cached BranchProfile error) throws InvalidBufferOffsetException {
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
                    @Bind("getMeta().java_nio_ByteBuffer_getShort") Method getShortMethod,
                    @Cached LookupAndInvokeKnownMethodNode get,
                    @Bind("getMeta().java_nio_Buffer_limit") Method limitMethod,
                    @Cached LookupAndInvokeKnownMethodNode size,
                    @Bind("getMeta().java_nio_ByteBuffer_order") Method orderMethod,
                    @Cached LookupAndInvokeKnownMethodNode orderNode,
                    @Bind("getMeta().java_nio_ByteBuffer_setOrder") Method setOrderMethod,
                    @Cached LookupAndInvokeKnownMethodNode setOrderNode,
                    @Cached.Shared("error") @Cached BranchProfile error) throws InvalidBufferOffsetException {
        int bufferSize = (int) size.execute(receiver, limitMethod);
        if (byteOffset < 0 || byteOffset >= bufferSize - 1) {
            error.enter();
            throw InvalidBufferOffsetException.create(byteOffset, bufferSize);
        }
        return (short) get(receiver, byteOffset, order, get, getShortMethod, orderNode, orderMethod, setOrderNode, setOrderMethod);
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
        put(receiver, byteOffset, value, order, put, putShortMethod, orderNode, orderMethod, setOrderNode, setOrderMethod);
    }

    @ExportMessage
    static int readBufferInt(StaticObject receiver, ByteOrder order, long byteOffset,
                    @Bind("getMeta().java_nio_ByteBuffer_getInt") Method getIntMethod,
                    @Cached LookupAndInvokeKnownMethodNode get,
                    @Bind("getMeta().java_nio_Buffer_limit") Method limitMethod,
                    @Cached LookupAndInvokeKnownMethodNode size,
                    @Bind("getMeta().java_nio_ByteBuffer_order") Method orderMethod,
                    @Cached LookupAndInvokeKnownMethodNode orderNode,
                    @Bind("getMeta().java_nio_ByteBuffer_setOrder") Method setOrderMethod,
                    @Cached LookupAndInvokeKnownMethodNode setOrderNode,
                    @Cached.Shared("error") @Cached BranchProfile error) throws InvalidBufferOffsetException {
        int bufferSize = (int) size.execute(receiver, limitMethod);
        if (byteOffset < 0 || byteOffset >= bufferSize - 3) {
            error.enter();
            throw InvalidBufferOffsetException.create(byteOffset, bufferSize);
        }
        return (int) get(receiver, byteOffset, order, get, getIntMethod, orderNode, orderMethod, setOrderNode, setOrderMethod);
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
        put(receiver, byteOffset, value, order, put, putIntMethod, orderNode, orderMethod, setOrderNode, setOrderMethod);
    }

    @ExportMessage
    static long readBufferLong(StaticObject receiver, ByteOrder order, long byteOffset,
                    @Bind("getMeta().java_nio_ByteBuffer_getLong") Method getLongMethod,
                    @Cached LookupAndInvokeKnownMethodNode get,
                    @Bind("getMeta().java_nio_Buffer_limit") Method limitMethod,
                    @Cached LookupAndInvokeKnownMethodNode size,
                    @Bind("getMeta().java_nio_ByteBuffer_order") Method orderMethod,
                    @Cached LookupAndInvokeKnownMethodNode orderNode,
                    @Bind("getMeta().java_nio_ByteBuffer_setOrder") Method setOrderMethod,
                    @Cached LookupAndInvokeKnownMethodNode setOrderNode,
                    @Cached.Shared("error") @Cached BranchProfile error) throws InvalidBufferOffsetException {
        int bufferSize = (int) size.execute(receiver, limitMethod);
        if (byteOffset < 0 || byteOffset >= bufferSize - 7) {
            error.enter();
            throw InvalidBufferOffsetException.create(byteOffset, bufferSize);
        }
        return (long) get(receiver, byteOffset, order, get, getLongMethod, orderNode, orderMethod, setOrderNode, setOrderMethod);
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
        put(receiver, byteOffset, value, order, put, putLongMethod, orderNode, orderMethod, setOrderNode, setOrderMethod);
    }

    @ExportMessage
    static float readBufferFloat(StaticObject receiver, ByteOrder order, long byteOffset,
                    @Bind("getMeta().java_nio_ByteBuffer_getFloat") Method getFloatMethod,
                    @Cached LookupAndInvokeKnownMethodNode get,
                    @Bind("getMeta().java_nio_Buffer_limit") Method limitMethod,
                    @Cached LookupAndInvokeKnownMethodNode size,
                    @Bind("getMeta().java_nio_ByteBuffer_order") Method orderMethod,
                    @Cached LookupAndInvokeKnownMethodNode orderNode,
                    @Bind("getMeta().java_nio_ByteBuffer_setOrder") Method setOrderMethod,
                    @Cached LookupAndInvokeKnownMethodNode setOrderNode,
                    @Cached.Shared("error") @Cached BranchProfile error) throws InvalidBufferOffsetException {
        int bufferSize = (int) size.execute(receiver, limitMethod);
        if (byteOffset < 0 || byteOffset >= bufferSize - 3) {
            error.enter();
            throw InvalidBufferOffsetException.create(byteOffset, bufferSize);
        }
        return (float) get(receiver, byteOffset, order, get, getFloatMethod, orderNode, orderMethod, setOrderNode, setOrderMethod);
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
        put(receiver, byteOffset, value, order, put, putFloatMethod, orderNode, orderMethod, setOrderNode, setOrderMethod);
    }

    @ExportMessage
    static double readBufferDouble(StaticObject receiver, ByteOrder order, long byteOffset,
                    @Bind("getMeta().java_nio_ByteBuffer_getDouble") Method getDoubleMethod,
                    @Cached LookupAndInvokeKnownMethodNode get,
                    @Bind("getMeta().java_nio_Buffer_limit") Method limitMethod,
                    @Bind("getMeta().java_nio_ByteBuffer_order") Method orderMethod,
                    @Cached LookupAndInvokeKnownMethodNode orderNode,
                    @Bind("getMeta().java_nio_ByteBuffer_setOrder") Method setOrderMethod,
                    @Cached LookupAndInvokeKnownMethodNode setOrderNode,
                    @Cached LookupAndInvokeKnownMethodNode size,
                    @Cached.Shared("error") @Cached BranchProfile error) throws InvalidBufferOffsetException {
        int bufferSize = (int) size.execute(receiver, limitMethod);
        if (byteOffset < 0 || byteOffset >= bufferSize - 7) {
            error.enter();
            throw InvalidBufferOffsetException.create(byteOffset, bufferSize);
        }
        return (double) get(receiver, byteOffset, order, get, getDoubleMethod, orderNode, orderMethod, setOrderNode, setOrderMethod);
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
        put(receiver, byteOffset, value, order, put, putDoubleMethod, orderNode, orderMethod, setOrderNode, setOrderMethod);
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

    private static Object get(StaticObject receiver, long byteOffset, ByteOrder order, LookupAndInvokeKnownMethodNode get, Method getMethod, LookupAndInvokeKnownMethodNode orderNode,
                    Method orderMethod, LookupAndInvokeKnownMethodNode setOrderNode, Method setOrderMethod) {
        StaticObject originalOrder = (StaticObject) orderNode.execute(receiver, orderMethod);
        StaticObject desiredOrder = toGuestEndian(order, getMeta());
        if (originalOrder == desiredOrder) {
            return get.execute(receiver, getMethod, new Object[]{byteOffset});
        } else {
            try {
                setOrderNode.execute(receiver, setOrderMethod, new Object[]{desiredOrder});
                return get.execute(receiver, getMethod, new Object[]{byteOffset});
            } finally {
                setOrderNode.execute(receiver, setOrderMethod, new Object[]{originalOrder});
            }
        }
    }

    private static void put(StaticObject receiver, long byteOffset, Object value, ByteOrder order, LookupAndInvokeKnownMethodNode put, Method putMethod, LookupAndInvokeKnownMethodNode orderNode,
                    Method orderMethod, LookupAndInvokeKnownMethodNode setOrderNode, Method setOrderMethod) {
        StaticObject originalOrder = (StaticObject) orderNode.execute(receiver, orderMethod);
        StaticObject desiredOrder = toGuestEndian(order, getMeta());
        if (originalOrder == desiredOrder) {
            put.execute(receiver, putMethod, new Object[]{byteOffset, value});
        } else {
            try {
                setOrderNode.execute(receiver, setOrderMethod, new Object[]{desiredOrder});
                put.execute(receiver, putMethod, new Object[]{byteOffset, value});
            } finally {
                setOrderNode.execute(receiver, setOrderMethod, new Object[]{originalOrder});
            }
        }
    }

    private static @JavaType(ByteOrder.class) StaticObject toGuestEndian(ByteOrder order, Meta meta) {
        StaticObject staticStorage = meta.java_nio_ByteOrder.tryInitializeAndGetStatics();
        if (order == ByteOrder.LITTLE_ENDIAN) {
            return meta.java_nio_ByteOrder_LITTLE_ENDIAN.getObject(staticStorage);
        } else {
            return meta.java_nio_ByteOrder_BIG_ENDIAN.getObject(staticStorage);
        }
    }
}
