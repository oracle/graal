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
package com.sun.c1x.asm;

import java.util.*;

import com.sun.c1x.*;
import com.sun.cri.bytecode.*;
import com.sun.cri.ci.CiArchitecture.ByteOrder;

/**
 *
 * @author Thomas Wuerthinger
 */
public final class Buffer {

    private byte[] data;
    private int position;
    private int mark = -1;

    private final ByteOrder byteOrder;

    public Buffer(ByteOrder byteOrder) {
        this.byteOrder = byteOrder;
        this.data = new byte[C1XOptions.InitialCodeBufferSize];
    }

    public void reset() {
        position = 0;
        mark = -1;
    }

    /**
     * Closes this buffer. No extra data can be written to this buffer after this call.
     *
     * @param trimmedCopy if {@code true}, then a copy of the underlying byte array up to (but not including)
     *            {@code position()} is returned
     * @return the data in this buffer or a trimmed copy if {@code trimmedCopy} is {@code true}
     */
    public byte[] close(boolean trimmedCopy) {
        byte[] result = trimmedCopy ? Arrays.copyOf(data, position()) : data;
        data = null;
        return result;
    }

    public int emitBytes(byte[] arr, int off, int len) {
        assert data != null : "must not use buffer after calling finished!";
        int oldPos = position;
        ensureSize(position + len);
        System.arraycopy(arr, off, data, position, len);
        position += len;
        return oldPos;
    }

    public int emitByte(int b) {
        int oldPos = position;
        position = emitByte(b, oldPos);
        return oldPos;
    }

    public int emitShort(int b) {
        int oldPos = position;
        position = emitShort(b, oldPos);
        return oldPos;
    }

    public int emitInt(int b) {
        int oldPos = position;
        position = emitInt(b, oldPos);
        return oldPos;
    }

    public int emitLong(long b) {
        int oldPos = position;
        position = emitLong(b, oldPos);
        return oldPos;
    }

    private boolean isByte(int b) {
        return b == (b & 0xFF);
    }

    private boolean isShort(int s) {
        return s == (s & 0xFFFF);
    }

    /**
     * Places a bookmark at the {@linkplain #position() current position}.
     *
     * @return the previously placed bookmark or {@code -1} if there was no bookmark
     */
    public int mark() {
        int mark = this.mark;
        this.mark = position;
        return mark;
    }

    private void ensureSize(int length) {
        if (length >= data.length) {
            data = Arrays.copyOf(data, data.length * 4);
            C1XMetrics.CodeBufferCopies++;
        }
    }

    public int emitByte(int b, int pos) {
        assert data != null : "must not use buffer after calling finished!";
        assert isByte(b);
        ensureSize(pos + 1);
        data[pos++] = (byte) b;
        return pos;
    }

    public int emitShort(int b, int pos) {
        assert data != null : "must not use buffer after calling finished!";
        assert isShort(b);
        ensureSize(pos + 2);
        if (byteOrder == ByteOrder.BigEndian) {
            data[pos++] = (byte) ((b >> 8) & 0xFF);
            data[pos++] = (byte) (b & 0xFF);

        } else {
            assert byteOrder == ByteOrder.LittleEndian;
            data[pos++] = (byte) (b & 0xFF);
            data[pos++] = (byte) ((b >> 8) & 0xFF);
        }
        return pos;
    }

    public int emitInt(int b, int pos) {
        assert data != null : "must not use buffer after calling finished!";
        ensureSize(pos + 4);
        if (byteOrder == ByteOrder.BigEndian) {
            data[pos++] = (byte) ((b >> 24) & 0xFF);
            data[pos++] = (byte) ((b >> 16) & 0xFF);
            data[pos++] = (byte) ((b >> 8) & 0xFF);
            data[pos++] = (byte) (b & 0xFF);
        } else {
            assert byteOrder == ByteOrder.LittleEndian;
            data[pos++] = (byte) (b & 0xFF);
            data[pos++] = (byte) ((b >> 8) & 0xFF);
            data[pos++] = (byte) ((b >> 16) & 0xFF);
            data[pos++] = (byte) ((b >> 24) & 0xFF);
        }
        return pos;
    }

    public int emitLong(long b, int pos) {
        assert data != null : "must not use buffer after calling finished!";
        ensureSize(pos + 8);

        if (byteOrder == ByteOrder.BigEndian) {
            data[pos++] = (byte) ((b >> 56) & 0xFF);
            data[pos++] = (byte) ((b >> 48) & 0xFF);
            data[pos++] = (byte) ((b >> 40) & 0xFF);
            data[pos++] = (byte) ((b >> 32) & 0xFF);
            data[pos++] = (byte) ((b >> 24) & 0xFF);
            data[pos++] = (byte) ((b >> 16) & 0xFF);
            data[pos++] = (byte) ((b >> 8) & 0xFF);
            data[pos++] = (byte) (b & 0xFF);
        } else {
            assert byteOrder == ByteOrder.LittleEndian;
            data[pos++] = (byte) (b & 0xFF);
            data[pos++] = (byte) ((b >> 8) & 0xFF);
            data[pos++] = (byte) ((b >> 16) & 0xFF);
            data[pos++] = (byte) ((b >> 24) & 0xFF);
            data[pos++] = (byte) ((b >> 32) & 0xFF);
            data[pos++] = (byte) ((b >> 40) & 0xFF);
            data[pos++] = (byte) ((b >> 48) & 0xFF);
            data[pos++] = (byte) ((b >> 56) & 0xFF);
        }
        return pos;
    }

    public int position() {
        return position;
    }

    public void setPosition(int position) {
        assert position >= 0 && position <= data.length;
        this.position = position;
    }

    public int getByte(int pos) {
        return Bytes.beU1(data, pos);
    }

    public int getShort(int pos) {
        if (byteOrder == ByteOrder.BigEndian) {
            return
                (data[pos + 0] & 0xff) << 8 |
                (data[pos + 1] & 0xff) << 0;
        } else {
            assert byteOrder == ByteOrder.LittleEndian;
            return
                (data[pos + 1] & 0xff) << 8  |
                (data[pos + 0] & 0xff) << 0;
        }
    }

    public int getInt(int pos) {
        if (byteOrder == ByteOrder.BigEndian) {
            return
                (data[pos + 0] & 0xff) << 24 |
                (data[pos + 1] & 0xff) << 16 |
                (data[pos + 2] & 0xff) << 8  |
                (data[pos + 3] & 0xff) << 0;
        } else {
            assert byteOrder == ByteOrder.LittleEndian;
            return
                (data[pos + 3] & 0xff) << 24 |
                (data[pos + 2] & 0xff) << 16 |
                (data[pos + 1] & 0xff) << 8  |
                (data[pos + 0] & 0xff) << 0;
        }
    }

    public byte[] copyData(int start, int end) {
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
}
