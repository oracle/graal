/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.asm;

import java.util.*;

/**
 * Code buffer management for the assembler. Support for little endian and big endian architectures is implemented using subclasses.
 */
public abstract class Buffer {
    protected byte[] data;
    protected int position;

    public Buffer() {
        data = new byte[AsmOptions.InitialCodeBufferSize];
    }

    public void reset() {
        position = 0;
    }

    public int position() {
        return position;
    }

    public void setPosition(int position) {
        assert position >= 0 && position <= data.length;
        this.position = position;
    }

    /**
     * Closes this buffer. No extra data can be written to this buffer after
     * this call.
     *
     * @param trimmedCopy
     *            if {@code true}, then a copy of the underlying byte array up
     *            to (but not including) {@code position()} is returned
     * @return the data in this buffer or a trimmed copy if {@code trimmedCopy}
     *         is {@code true}
     */
    public byte[] close(boolean trimmedCopy) {
        byte[] result = trimmedCopy ? Arrays.copyOf(data, position()) : data;
        data = null;
        return result;
    }

    public byte[] copyData(int start, int end) {
        return Arrays.copyOfRange(data, start, end);
    }

    /**
     * Copies the data from this buffer into a given array.
     *
     * @param dst
     *            the destination array
     * @param off
     *            starting position in {@code dst}
     * @param len
     *            number of bytes to copy
     */
    public void copyInto(byte[] dst, int off, int len) {
        System.arraycopy(data, 0, dst, off, len);
    }

    protected void ensureSize(int length) {
        if (length >= data.length) {
            data = Arrays.copyOf(data, length * 4);
        }
    }

    public void emitBytes(byte[] arr, int off, int len) {
        ensureSize(position + len);
        System.arraycopy(arr, off, data, position, len);
        position += len;
    }

    public void emitByte(int b) {
        position = emitByte(b, position);
    }

    public void emitShort(int b) {
        position = emitShort(b, position);
    }

    public void emitInt(int b) {
        position = emitInt(b, position);
    }

    public void emitLong(long b) {
        position = emitLong(b, position);
    }

    public int emitByte(int b, int pos) {
        assert NumUtil.isUByte(b);
        ensureSize(pos + 1);
        data[pos++] = (byte) (b & 0xFF);
        return pos;
    }

    public abstract int emitShort(int b, int pos);

    public abstract int emitInt(int b, int pos);

    public abstract int emitLong(long b, int pos);

    public int getByte(int pos) {
        return data[pos] & 0xff;
    }

    public abstract int getShort(int pos);

    public abstract int getInt(int pos);

    public static final class BigEndian extends Buffer {
        @Override
        public int emitShort(int b, int pos) {
            assert NumUtil.isUShort(b);
            ensureSize(pos + 2);
            data[pos++] = (byte) ((b >> 8) & 0xFF);
            data[pos++] = (byte) (b & 0xFF);
            return pos;
        }

        @Override
        public int emitInt(int b, int pos) {
            ensureSize(pos + 4);
            data[pos++] = (byte) ((b >> 24) & 0xFF);
            data[pos++] = (byte) ((b >> 16) & 0xFF);
            data[pos++] = (byte) ((b >> 8) & 0xFF);
            data[pos++] = (byte) (b & 0xFF);
            return pos;
        }

        @Override
        public int emitLong(long b, int pos) {
            ensureSize(pos + 8);
            data[pos++] = (byte) ((b >> 56) & 0xFF);
            data[pos++] = (byte) ((b >> 48) & 0xFF);
            data[pos++] = (byte) ((b >> 40) & 0xFF);
            data[pos++] = (byte) ((b >> 32) & 0xFF);
            data[pos++] = (byte) ((b >> 24) & 0xFF);
            data[pos++] = (byte) ((b >> 16) & 0xFF);
            data[pos++] = (byte) ((b >> 8) & 0xFF);
            data[pos++] = (byte) (b & 0xFF);
            return pos;
        }

        @Override
        public int getShort(int pos) {
            return
                (data[pos + 0] & 0xff) << 8 |
                (data[pos + 1] & 0xff) << 0;
        }

        @Override
        public int getInt(int pos) {
            return
                (data[pos + 0] & 0xff) << 24 |
                (data[pos + 1] & 0xff) << 16 |
                (data[pos + 2] & 0xff) << 8 |
                (data[pos + 3] & 0xff) << 0;
        }
    }

    public static final class LittleEndian extends Buffer {
        @Override
        public int emitShort(int b, int pos) {
            assert NumUtil.isUShort(b);
            ensureSize(pos + 2);
            data[pos++] = (byte) (b & 0xFF);
            data[pos++] = (byte) ((b >> 8) & 0xFF);
            return pos;
        }

        @Override
        public int emitInt(int b, int pos) {
            ensureSize(pos + 4);
            data[pos++] = (byte) (b & 0xFF);
            data[pos++] = (byte) ((b >> 8) & 0xFF);
            data[pos++] = (byte) ((b >> 16) & 0xFF);
            data[pos++] = (byte) ((b >> 24) & 0xFF);
            return pos;
        }

        @Override
        public int emitLong(long b, int pos) {
            ensureSize(pos + 8);
            data[pos++] = (byte) (b & 0xFF);
            data[pos++] = (byte) ((b >> 8) & 0xFF);
            data[pos++] = (byte) ((b >> 16) & 0xFF);
            data[pos++] = (byte) ((b >> 24) & 0xFF);
            data[pos++] = (byte) ((b >> 32) & 0xFF);
            data[pos++] = (byte) ((b >> 40) & 0xFF);
            data[pos++] = (byte) ((b >> 48) & 0xFF);
            data[pos++] = (byte) ((b >> 56) & 0xFF);
            return pos;
        }

        @Override
        public int getShort(int pos) {
            return
                (data[pos + 1] & 0xff) << 8 |
                (data[pos + 0] & 0xff) << 0;
        }

        @Override
        public int getInt(int pos) {
            return
                (data[pos + 3] & 0xff) << 24 |
                (data[pos + 2] & 0xff) << 16 |
                (data[pos + 1] & 0xff) << 8 |
                (data[pos + 0] & 0xff) << 0;
        }
    }
}
