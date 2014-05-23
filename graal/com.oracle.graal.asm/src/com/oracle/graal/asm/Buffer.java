/*
 * Copyright (c) 2009, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.asm;

import java.util.*;

/**
 * Code buffer management for the assembler. Support for little endian and big endian architectures
 * is implemented using subclasses.
 */
abstract class Buffer {

    protected byte[] data;
    protected int position;

    public Buffer() {
        data = new byte[AsmOptions.InitialCodeBufferSize];
    }

    public int position() {
        return position;
    }

    public void setPosition(int position) {
        assert position >= 0 && position <= data.length;
        this.position = position;
    }

    /**
     * Closes this buffer. No extra data can be written to this buffer after this call.
     *
     * @param trimmedCopy if {@code true}, then a copy of the underlying byte array up to (but not
     *            including) {@code position()} is returned
     * @return the data in this buffer or a trimmed copy if {@code trimmedCopy} is {@code true}
     */
    public byte[] close(boolean trimmedCopy) {
        byte[] result = trimmedCopy ? Arrays.copyOf(data, position()) : data;
        data = null;
        return result;
    }

    public byte[] copyData(int start, int end) {
        if (data == null) {
            return null;
        }
        return Arrays.copyOfRange(data, start, end);
    }

    /**
     * Copies the data from this buffer into a given array.
     *
     * @param dst the destination array
     * @param off starting position in {@code dst}
     * @param len number of bytes to copy
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

    public int emitBytes(byte[] arr, int pos) {
        final int len = arr.length;
        final int newPos = pos + len;
        ensureSize(newPos);
        System.arraycopy(arr, 0, data, pos, len);
        return newPos;
    }

    public int emitByte(int b, int pos) {
        assert NumUtil.isUByte(b) || NumUtil.isByte(b);
        int newPos = pos + 1;
        ensureSize(newPos);
        data[pos] = (byte) (b & 0xFF);
        return newPos;
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
            assert NumUtil.isUShort(b) || NumUtil.isShort(b);
            int newPos = pos + 2;
            ensureSize(pos + 2);
            data[pos] = (byte) ((b >> 8) & 0xFF);
            data[pos + 1] = (byte) (b & 0xFF);
            return newPos;
        }

        @Override
        public int emitInt(int b, int pos) {
            int newPos = pos + 4;
            ensureSize(newPos);
            data[pos] = (byte) ((b >> 24) & 0xFF);
            data[pos + 1] = (byte) ((b >> 16) & 0xFF);
            data[pos + 2] = (byte) ((b >> 8) & 0xFF);
            data[pos + 3] = (byte) (b & 0xFF);
            return newPos;
        }

        @Override
        public int emitLong(long b, int pos) {
            int newPos = pos + 8;
            ensureSize(newPos);
            data[pos] = (byte) ((b >> 56) & 0xFF);
            data[pos + 1] = (byte) ((b >> 48) & 0xFF);
            data[pos + 2] = (byte) ((b >> 40) & 0xFF);
            data[pos + 3] = (byte) ((b >> 32) & 0xFF);
            data[pos + 4] = (byte) ((b >> 24) & 0xFF);
            data[pos + 5] = (byte) ((b >> 16) & 0xFF);
            data[pos + 6] = (byte) ((b >> 8) & 0xFF);
            data[pos + 7] = (byte) (b & 0xFF);
            return newPos;
        }

        @Override
        public int getShort(int pos) {
            return (data[pos + 0] & 0xff) << 8 | (data[pos + 1] & 0xff) << 0;
        }

        @Override
        public int getInt(int pos) {
            return (data[pos + 0] & 0xff) << 24 | (data[pos + 1] & 0xff) << 16 | (data[pos + 2] & 0xff) << 8 | (data[pos + 3] & 0xff) << 0;
        }
    }

    public static final class LittleEndian extends Buffer {

        @Override
        public int emitShort(int b, int pos) {
            assert NumUtil.isUShort(b) || NumUtil.isShort(b);
            int newPos = pos + 2;
            ensureSize(newPos);
            data[pos] = (byte) (b & 0xFF);
            data[pos + 1] = (byte) ((b >> 8) & 0xFF);
            return newPos;
        }

        @Override
        public int emitInt(int b, int pos) {
            int newPos = pos + 4;
            ensureSize(newPos);
            data[pos] = (byte) (b & 0xFF);
            data[pos + 1] = (byte) ((b >> 8) & 0xFF);
            data[pos + 2] = (byte) ((b >> 16) & 0xFF);
            data[pos + 3] = (byte) ((b >> 24) & 0xFF);
            return newPos;
        }

        @Override
        public int emitLong(long b, int pos) {
            int newPos = pos + 8;
            ensureSize(newPos);
            data[pos] = (byte) (b & 0xFF);
            data[pos + 1] = (byte) ((b >> 8) & 0xFF);
            data[pos + 2] = (byte) ((b >> 16) & 0xFF);
            data[pos + 3] = (byte) ((b >> 24) & 0xFF);
            data[pos + 4] = (byte) ((b >> 32) & 0xFF);
            data[pos + 5] = (byte) ((b >> 40) & 0xFF);
            data[pos + 6] = (byte) ((b >> 48) & 0xFF);
            data[pos + 7] = (byte) ((b >> 56) & 0xFF);
            return newPos;
        }

        @Override
        public int getShort(int pos) {
            return (data[pos + 1] & 0xff) << 8 | (data[pos + 0] & 0xff) << 0;
        }

        @Override
        public int getInt(int pos) {
            return (data[pos + 3] & 0xff) << 24 | (data[pos + 2] & 0xff) << 16 | (data[pos + 1] & 0xff) << 8 | (data[pos + 0] & 0xff) << 0;
        }
    }
}
