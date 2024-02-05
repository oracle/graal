/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.nfi.backend.libffi;

import java.nio.ByteOrder;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidBufferOffsetException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.memory.ByteArraySupport;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.nfi.api.SerializableLibrary;

abstract class NativeBuffer implements TruffleObject {

    @ExportLibrary(value = SerializableLibrary.class, useForAOT = false)
    @ExportLibrary(InteropLibrary.class)
    static final class Array extends NativeBuffer {

        final byte[] content;

        Array(byte[] content) {
            this.content = content;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean isSerializable() {
            return true;
        }

        @ExportMessage(limit = "1")
        void serialize(Object buffer,
                        @CachedLibrary("buffer") InteropLibrary interop) {
            try {
                for (int i = 0; i < content.length; i++) {
                    interop.writeBufferByte(buffer, i, content[i]);
                }
            } catch (UnsupportedMessageException | InvalidBufferOffsetException ex) {
                throw CompilerDirectives.shouldNotReachHere(ex);
            }
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean hasBufferElements() {
            return true;
        }

        @ExportMessage
        int getBufferSize() {
            return content.length;
        }

        @ExportMessage
        byte readBufferByte(long offset,
                        @Bind("$node") Node node,
                        @Shared("exception") @Cached InlinedBranchProfile exception) throws InvalidBufferOffsetException {
            if (Long.compareUnsigned(offset, content.length) >= 0) {
                exception.enter(node);
                throw InvalidBufferOffsetException.create(offset, 1);
            }
            return content[(int) offset];
        }

        @ExportMessage
        void readBuffer(long offset, byte[] destination, int destinationOffset, int length,
                        @Bind("$node") Node node,
                        @Shared("exception") @Cached InlinedBranchProfile exception) throws InvalidBufferOffsetException {
            ByteArraySupport support = byteArraySupport(ByteOrder.BIG_ENDIAN);
            System.arraycopy(content, check(support, offset, length, exception, node), destination, destinationOffset, length);
        }

        private int check(ByteArraySupport support, long offset, int len, InlinedBranchProfile exception, Node node) throws InvalidBufferOffsetException {
            int ret = (int) offset;
            if (ret != offset || !support.inBounds(content, ret, len)) {
                exception.enter(node);
                throw InvalidBufferOffsetException.create(offset, len);
            }
            return ret;
        }

        private static ByteArraySupport byteArraySupport(ByteOrder order) {
            if (order == ByteOrder.BIG_ENDIAN) {
                return ByteArraySupport.bigEndian();
            } else {
                return ByteArraySupport.littleEndian();
            }
        }

        @ExportMessage
        short readBufferShort(ByteOrder order, long offset,
                        @Bind("$node") Node node,
                        @Shared("exception") @Cached InlinedBranchProfile exception) throws InvalidBufferOffsetException {
            ByteArraySupport support = byteArraySupport(order);
            return support.getShort(content, check(support, offset, Short.BYTES, exception, node));
        }

        @ExportMessage
        int readBufferInt(ByteOrder order, long offset,
                        @Bind("$node") Node node,
                        @Shared("exception") @Cached InlinedBranchProfile exception) throws InvalidBufferOffsetException {
            ByteArraySupport support = byteArraySupport(order);
            return support.getInt(content, check(support, offset, Integer.BYTES, exception, node));
        }

        @ExportMessage
        long readBufferLong(ByteOrder order, long offset,
                        @Bind("$node") Node node,
                        @Shared("exception") @Cached InlinedBranchProfile exception) throws InvalidBufferOffsetException {
            ByteArraySupport support = byteArraySupport(order);
            return support.getLong(content, check(support, offset, Long.BYTES, exception, node));
        }

        @ExportMessage
        float readBufferFloat(ByteOrder order, long offset,
                        @Bind("$node") Node node,
                        @Shared("exception") @Cached InlinedBranchProfile exception) throws InvalidBufferOffsetException {
            ByteArraySupport support = byteArraySupport(order);
            return support.getFloat(content, check(support, offset, Float.BYTES, exception, node));
        }

        @ExportMessage
        double readBufferDouble(ByteOrder order, long offset,
                        @Bind("$node") Node node,
                        @Shared("exception") @Cached InlinedBranchProfile exception) throws InvalidBufferOffsetException {
            ByteArraySupport support = byteArraySupport(order);
            return support.getDouble(content, check(support, offset, Double.BYTES, exception, node));
        }
    }
}
