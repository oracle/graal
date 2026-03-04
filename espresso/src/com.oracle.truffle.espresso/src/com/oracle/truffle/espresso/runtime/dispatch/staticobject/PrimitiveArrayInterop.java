/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidBufferOffsetException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.runtime.dispatch.messages.GenerateInteropNodes;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.vm.UnsafeAccess;

import sun.misc.Unsafe;

/**
 * Interop dispatch for Espresso primitive arrays that exposes read-only Truffle buffer messages.
 */
@GenerateInteropNodes
@ExportLibrary(value = InteropLibrary.class, receiverType = StaticObject.class)
public class PrimitiveArrayInterop extends EspressoInterop {

    /**
     * Returns {@code true} when the receiver is a primitive Espresso array and therefore supports
     * buffer messages.
     */
    @ExportMessage
    static boolean hasBufferElements(StaticObject receiver) {
        receiver.checkNotForeign();
        return isPrimitiveArray(receiver);
    }

    /**
     * Primitive array buffers are intentionally read-only through interop.
     */
    @ExportMessage
    static boolean isBufferWritable(StaticObject receiver,
                    @Cached InlinedBranchProfile error, @Bind Node node) throws UnsupportedMessageException {
        receiver.checkNotForeign();
        if (!hasBufferElements(receiver)) {
            error.enter(node);
            throw UnsupportedMessageException.create();
        }
        return false;
    }

    /**
     * Returns the byte length of the primitive array exposed as a foreign buffer.
     */
    @ExportMessage
    static long getBufferSize(StaticObject receiver,
                    @Cached InlinedBranchProfile error, @Bind Node node) throws UnsupportedMessageException {
        receiver.checkNotForeign();
        if (!hasBufferElements(receiver)) {
            error.enter(node);
            throw UnsupportedMessageException.create();
        }
        EspressoLanguage language = EspressoLanguage.get(node);
        return getPrimitiveArraySizeInBytes(receiver, language, error, node);
    }

    /**
     * Copies a byte range from a primitive array into the destination byte array.
     */
    @ExportMessage
    static void readBuffer(StaticObject receiver, long byteOffset, byte[] destination, int destinationOffset, int length,
                    @Cached InlinedBranchProfile error, @Bind Node node) throws UnsupportedMessageException, InvalidBufferOffsetException {
        receiver.checkNotForeign();
        if (!hasBufferElements(receiver)) {
            error.enter(node);
            throw UnsupportedMessageException.create();
        }
        if (length < 0 || destinationOffset < 0 || destinationOffset > destination.length - length) {
            error.enter(node);
            throw InvalidBufferOffsetException.create(byteOffset, length);
        }
        EspressoLanguage language = EspressoLanguage.get(node);
        long sourceOffset = getByteOffsetInArray(receiver, byteOffset, length, language, error, node);
        UnsafeAccess.get().copyMemory(receiver.unwrap(language), sourceOffset, destination, Unsafe.ARRAY_BYTE_BASE_OFFSET + destinationOffset, length);
    }

    /**
     * Reads a single byte from the primitive array byte view.
     */
    @ExportMessage
    static byte readBufferByte(StaticObject receiver, long byteOffset,
                    @Cached InlinedBranchProfile error, @Bind Node node) throws UnsupportedMessageException, InvalidBufferOffsetException {
        receiver.checkNotForeign();
        if (!hasBufferElements(receiver)) {
            error.enter(node);
            throw UnsupportedMessageException.create();
        }
        EspressoLanguage language = EspressoLanguage.get(node);
        long sourceOffset = getByteOffsetInArray(receiver, byteOffset, Byte.BYTES, language, error, node);
        return UnsafeAccess.get().getByte(receiver.unwrap(language), sourceOffset);
    }

    /**
     * Reads a short value at the given byte offset and converts it to the requested byte order.
     */
    @ExportMessage
    static short readBufferShort(StaticObject receiver, ByteOrder order, long byteOffset,
                    @Cached InlinedBranchProfile error, @Bind Node node) throws UnsupportedMessageException, InvalidBufferOffsetException {
        receiver.checkNotForeign();
        if (!hasBufferElements(receiver)) {
            error.enter(node);
            throw UnsupportedMessageException.create();
        }
        EspressoLanguage language = EspressoLanguage.get(node);
        long sourceOffset = getByteOffsetInArray(receiver, byteOffset, Short.BYTES, language, error, node);
        short value = UnsafeAccess.get().getShort(receiver.unwrap(language), sourceOffset);
        return order == ByteOrder.nativeOrder() ? value : Short.reverseBytes(value);
    }

    /**
     * Reads an int value at the given byte offset and converts it to the requested byte order.
     */
    @ExportMessage
    static int readBufferInt(StaticObject receiver, ByteOrder order, long byteOffset,
                    @Cached InlinedBranchProfile error, @Bind Node node) throws UnsupportedMessageException, InvalidBufferOffsetException {
        receiver.checkNotForeign();
        if (!hasBufferElements(receiver)) {
            error.enter(node);
            throw UnsupportedMessageException.create();
        }
        EspressoLanguage language = EspressoLanguage.get(node);
        long sourceOffset = getByteOffsetInArray(receiver, byteOffset, Integer.BYTES, language, error, node);
        int value = UnsafeAccess.get().getInt(receiver.unwrap(language), sourceOffset);
        return order == ByteOrder.nativeOrder() ? value : Integer.reverseBytes(value);
    }

    /**
     * Reads a long value at the given byte offset and converts it to the requested byte order.
     */
    @ExportMessage
    static long readBufferLong(StaticObject receiver, ByteOrder order, long byteOffset,
                    @Cached InlinedBranchProfile error, @Bind Node node) throws UnsupportedMessageException, InvalidBufferOffsetException {
        receiver.checkNotForeign();
        if (!hasBufferElements(receiver)) {
            error.enter(node);
            throw UnsupportedMessageException.create();
        }
        EspressoLanguage language = EspressoLanguage.get(node);
        long sourceOffset = getByteOffsetInArray(receiver, byteOffset, Long.BYTES, language, error, node);
        long value = UnsafeAccess.get().getLong(receiver.unwrap(language), sourceOffset);
        return order == ByteOrder.nativeOrder() ? value : Long.reverseBytes(value);
    }

    /**
     * Reads a float value at the given byte offset in the requested byte order.
     */
    @ExportMessage
    static float readBufferFloat(StaticObject receiver, ByteOrder order, long byteOffset,
                    @Cached InlinedBranchProfile error, @Bind Node node) throws UnsupportedMessageException, InvalidBufferOffsetException {
        int bits = readBufferInt(receiver, order, byteOffset, error, node);
        return Float.intBitsToFloat(bits);
    }

    /**
     * Reads a double value at the given byte offset in the requested byte order.
     */
    @ExportMessage
    static double readBufferDouble(StaticObject receiver, ByteOrder order, long byteOffset,
                    @Cached InlinedBranchProfile error, @Bind Node node) throws UnsupportedMessageException, InvalidBufferOffsetException {
        long bits = readBufferLong(receiver, order, byteOffset, error, node);
        return Double.longBitsToDouble(bits);
    }

    /**
     * Primitive array interop is read-only, so byte writes are rejected.
     */
    @ExportMessage
    @SuppressWarnings("unused")
    static void writeBufferByte(StaticObject receiver, long byteOffset, byte value) throws UnsupportedMessageException {
        receiver.checkNotForeign();
        throw UnsupportedMessageException.create();
    }

    /**
     * Primitive array interop is read-only, so short writes are rejected.
     */
    @ExportMessage
    @SuppressWarnings("unused")
    static void writeBufferShort(StaticObject receiver, ByteOrder order, long byteOffset, short value) throws UnsupportedMessageException {
        receiver.checkNotForeign();
        throw UnsupportedMessageException.create();
    }

    /**
     * Primitive array interop is read-only, so int writes are rejected.
     */
    @ExportMessage
    @SuppressWarnings("unused")
    static void writeBufferInt(StaticObject receiver, ByteOrder order, long byteOffset, int value) throws UnsupportedMessageException {
        receiver.checkNotForeign();
        throw UnsupportedMessageException.create();
    }

    /**
     * Primitive array interop is read-only, so long writes are rejected.
     */
    @ExportMessage
    @SuppressWarnings("unused")
    static void writeBufferLong(StaticObject receiver, ByteOrder order, long byteOffset, long value) throws UnsupportedMessageException {
        receiver.checkNotForeign();
        throw UnsupportedMessageException.create();
    }

    /**
     * Primitive array interop is read-only, so float writes are rejected.
     */
    @ExportMessage
    @SuppressWarnings("unused")
    static void writeBufferFloat(StaticObject receiver, ByteOrder order, long byteOffset, float value) throws UnsupportedMessageException {
        receiver.checkNotForeign();
        throw UnsupportedMessageException.create();
    }

    /**
     * Primitive array interop is read-only, so double writes are rejected.
     */
    @ExportMessage
    @SuppressWarnings("unused")
    static void writeBufferDouble(StaticObject receiver, ByteOrder order, long byteOffset, double value) throws UnsupportedMessageException {
        receiver.checkNotForeign();
        throw UnsupportedMessageException.create();
    }

    /**
     * Validates bounds and computes the absolute byte offset in the underlying primitive array.
     */
    private static long getByteOffsetInArray(StaticObject receiver, long byteOffset, int opLength, EspressoLanguage language, InlinedBranchProfile error, Node node)
                    throws InvalidBufferOffsetException, UnsupportedMessageException {
        long size = getPrimitiveArraySizeInBytes(receiver, language, error, node);
        if (byteOffset < 0 || opLength < 0 || byteOffset > size - opLength || byteOffset > Integer.MAX_VALUE) {
            error.enter(node);
            throw InvalidBufferOffsetException.create(byteOffset, opLength);
        }
        return UnsafeAccess.get().arrayBaseOffset(receiver.unwrap(language).getClass()) + byteOffset;
    }

    /**
     * Computes the total size in bytes of the primitive array represented by {@code receiver}.
     */
    private static long getPrimitiveArraySizeInBytes(StaticObject receiver, EspressoLanguage language, InlinedBranchProfile error, Node node) throws UnsupportedMessageException {
        if (!(receiver.getKlass() instanceof ArrayKlass arrayKlass && arrayKlass.getComponentType().isPrimitive())) {
            error.enter(node);
            throw UnsupportedMessageException.create();
        }
        return (long) receiver.length(language) * arrayKlass.getComponentType().getJavaKind().getByteCount();
    }
}
