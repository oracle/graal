/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.impl.jvmci.external;

import java.nio.ByteOrder;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidBufferOffsetException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.memory.ByteArraySupport;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

@ExportLibrary(InteropLibrary.class)
public final class TruffleReadOnlyBytes implements TruffleObject {
    private final byte[] bytes;

    public TruffleReadOnlyBytes(byte[] bytes) {
        this.bytes = bytes;
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    boolean hasBufferElements() {
        return true;
    }

    @ExportMessage
    long getBufferSize() {
        return bytes.length;
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    boolean isBufferWritable() {
        return false;
    }

    @ExportMessage
    void readBuffer(long byteOffset, byte[] destination, int destinationOffset, int length,
                    @Bind Node node,
                    @Shared @Cached InlinedBranchProfile error) throws InvalidBufferOffsetException {
        if (length < 0) {
            error.enter(node);
            throw InvalidBufferOffsetException.create(byteOffset, length);
        }
        try {
            int index = Math.toIntExact(byteOffset);
            System.arraycopy(bytes, index, destination, destinationOffset, length);
        } catch (ArithmeticException | IndexOutOfBoundsException e) {
            error.enter(node);
            throw InvalidBufferOffsetException.create(byteOffset, length);
        }
    }

    @ExportMessage
    byte readBufferByte(long byteOffset,
                    @Bind Node node,
                    @Shared @Cached InlinedBranchProfile error) throws InvalidBufferOffsetException {
        try {
            int index = Math.toIntExact(byteOffset);
            return bytes[index];
        } catch (ArithmeticException | IndexOutOfBoundsException e) {
            error.enter(node);
            throw InvalidBufferOffsetException.create(byteOffset, getBufferSize());
        }
    }

    @ExportMessage
    short readBufferShort(ByteOrder order, long byteOffset,
                    @Bind Node node,
                    @Shared @Cached InlinedBranchProfile error,
                    @Shared @Cached InlinedConditionProfile littleEndian) throws InvalidBufferOffsetException {
        try {
            int index = Math.toIntExact(byteOffset);
            ByteArraySupport byteArraySupport;
            if (littleEndian.profile(node, order == ByteOrder.LITTLE_ENDIAN)) {
                byteArraySupport = ByteArraySupport.littleEndian();
            } else {
                byteArraySupport = ByteArraySupport.bigEndian();
            }
            return byteArraySupport.getShort(bytes, index);
        } catch (ArithmeticException | IndexOutOfBoundsException e) {
            error.enter(node);
            throw InvalidBufferOffsetException.create(byteOffset, getBufferSize());
        }
    }

    @ExportMessage
    int readBufferInt(ByteOrder order, long byteOffset,
                    @Bind Node node,
                    @Shared @Cached InlinedBranchProfile error,
                    @Shared @Cached InlinedConditionProfile littleEndian) throws InvalidBufferOffsetException {
        try {
            int index = Math.toIntExact(byteOffset);
            ByteArraySupport byteArraySupport;
            if (littleEndian.profile(node, order == ByteOrder.LITTLE_ENDIAN)) {
                byteArraySupport = ByteArraySupport.littleEndian();
            } else {
                byteArraySupport = ByteArraySupport.bigEndian();
            }
            return byteArraySupport.getInt(bytes, index);
        } catch (ArithmeticException | IndexOutOfBoundsException e) {
            error.enter(node);
            throw InvalidBufferOffsetException.create(byteOffset, getBufferSize());
        }
    }

    @ExportMessage
    long readBufferLong(ByteOrder order, long byteOffset,
                    @Bind Node node,
                    @Shared @Cached InlinedBranchProfile error,
                    @Shared @Cached InlinedConditionProfile littleEndian) throws InvalidBufferOffsetException {
        try {
            int index = Math.toIntExact(byteOffset);
            ByteArraySupport byteArraySupport;
            if (littleEndian.profile(node, order == ByteOrder.LITTLE_ENDIAN)) {
                byteArraySupport = ByteArraySupport.littleEndian();
            } else {
                byteArraySupport = ByteArraySupport.bigEndian();
            }
            return byteArraySupport.getLong(bytes, index);
        } catch (ArithmeticException | IndexOutOfBoundsException e) {
            error.enter(node);
            throw InvalidBufferOffsetException.create(byteOffset, getBufferSize());
        }
    }

    @ExportMessage
    float readBufferFloat(ByteOrder order, long byteOffset,
                    @Bind Node node,
                    @Shared @Cached InlinedBranchProfile error,
                    @Shared @Cached InlinedConditionProfile littleEndian) throws InvalidBufferOffsetException {
        try {
            int index = Math.toIntExact(byteOffset);
            ByteArraySupport byteArraySupport;
            if (littleEndian.profile(node, order == ByteOrder.LITTLE_ENDIAN)) {
                byteArraySupport = ByteArraySupport.littleEndian();
            } else {
                byteArraySupport = ByteArraySupport.bigEndian();
            }
            return byteArraySupport.getFloat(bytes, index);
        } catch (ArithmeticException | IndexOutOfBoundsException e) {
            error.enter(node);
            throw InvalidBufferOffsetException.create(byteOffset, getBufferSize());
        }
    }

    @ExportMessage
    double readBufferDouble(ByteOrder order, long byteOffset,
                    @Bind Node node,
                    @Shared @Cached InlinedBranchProfile error,
                    @Shared @Cached InlinedConditionProfile littleEndian) throws InvalidBufferOffsetException {
        try {
            int index = Math.toIntExact(byteOffset);
            ByteArraySupport byteArraySupport;
            if (littleEndian.profile(node, order == ByteOrder.LITTLE_ENDIAN)) {
                byteArraySupport = ByteArraySupport.littleEndian();
            } else {
                byteArraySupport = ByteArraySupport.bigEndian();
            }
            return byteArraySupport.getDouble(bytes, index);
        } catch (ArithmeticException | IndexOutOfBoundsException e) {
            error.enter(node);
            throw InvalidBufferOffsetException.create(byteOffset, getBufferSize());
        }
    }

    @SuppressWarnings({"static-method", "unused"})
    @ExportMessage
    void writeBufferByte(long byteOffset, byte value) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @SuppressWarnings({"static-method", "unused"})
    @ExportMessage
    void writeBufferShort(ByteOrder order, long byteOffset, short value) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @SuppressWarnings({"static-method", "unused"})
    @ExportMessage
    void writeBufferInt(ByteOrder order, long byteOffset, int value) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @SuppressWarnings({"static-method", "unused"})
    @ExportMessage
    void writeBufferLong(ByteOrder order, long byteOffset, long value) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @SuppressWarnings({"static-method", "unused"})
    @ExportMessage
    void writeBufferFloat(ByteOrder order, long byteOffset, float value) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @SuppressWarnings({"static-method", "unused"})
    @ExportMessage
    void writeBufferDouble(ByteOrder order, long byteOffset, double value) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }
}
