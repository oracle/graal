/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package com.oracle.objectfile.io;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

/**
 * This is a wrapper for ByteBuffer which provides assembler-style operations to read and write
 * binary data at the granularity of bytes, words etc.. Its interface is intentionally modeled on
 * the analogous directives in the Oracle Solaris x86 assembler, as(1).
 * {@link "http://docs.oracle.com/cd/E26502_01/html/E28388/"}. Since this class supports both
 * writing (assembling) and reading (disassembling), each assembler directive has a corresponding
 * "read" and "write" method in this class.
 *
 * A note about signedness: for writing, there is no need for signed/unsigned versions of each
 * directive. Instead, it's the caller's responsibility to pass a (signed) value whose two's
 * complement encoding is the desired bit pattern (e.g. -1 for the 32-bit value 0xffffffff). When
 * reading, this is more complicated: if we read a byte with value 0xff, should we return (short)
 * 255, or (byte) -1? The latter is more consistent with the writing behavior, but also more
 * confusing when considered in isolation. So far, this distinction has only been important when
 * reading bytes, and we provide explicit readUbyte() and readByte() methods to cater to each
 * requirement separately. FIXME: this should probably be expanded to cover the full range of
 * integer data types (except long, which we're stuck with).
 */
public class AssemblyBuffer implements InputDisassembler, OutputAssembler {

    private static final double GROWTH_FACTOR = 2;
    public static final int INITIAL_STRING_SIZE = 64;

    public static InputDisassembler createInputDisassembler(ByteBuffer in) {
        return new AssemblyBuffer(in);
    }

    public static OutputAssembler createOutputAssembler(ByteBuffer out) {
        return new AssemblyBuffer(out);
    }

    public static OutputAssembler createOutputAssembler(ByteOrder order) {
        return new AssemblyBuffer(order);
    }

    public static OutputAssembler createOutputAssembler() {
        return new AssemblyBuffer();
    }

    ByteBuffer buf;

    public AssemblyBuffer(ByteBuffer in) {
        this.buf = in;
    }

    public AssemblyBuffer(ByteOrder order) {
        this(ByteBuffer.allocate(16).order(order));
    }

    public AssemblyBuffer() {
        this(ByteOrder.nativeOrder());
    }

    @Override
    public boolean eof() {
        return !buf.hasRemaining();
    }

    /**
     * See {@code org.graalvm.compiler.serviceprovider.BufferUtil}.
     */
    private static Buffer asBaseBuffer(Buffer obj) {
        return obj;
    }

    @Override
    public void seek(long pos) {
        if (pos > pos()) {
            ensure((int) (pos - pos()));
        }
        asBaseBuffer(buf).position((int) pos);
    }

    @Override
    public void skip(long diff) {
        seek(pos() + diff);
    }

    private Deque<Long> seekStack = new ArrayDeque<>();

    @Override
    public void pushSeek(long pos) {
        seekStack.push((long) pos());
        seek(pos);
    }

    @Override
    public void pushSkip(long diff) {
        seekStack.push((long) pos());
        seek(pos() + diff);
    }

    @Override
    public void pushPos() {
        seekStack.push((long) pos());
    }

    @Override
    public void pop() {
        seek(seekStack.pop());
    }

    @Override
    public int pos() {
        return buf.position();
    }

    @Override
    public void align(int alignment) {
        while (pos() % alignment != 0) {
            ensure(1);
            skip(1);
            // put((byte) 0);
        }
    }

    @Override
    public void even() {
        align(2);
    }

    @Override
    public void writeZero(int n) {
        for (int i = 0; i < n; ++i) {
            writeByte((byte) 0);
        }
    }

    @Override
    public void writeLEB128(long v) {
        long vv = v;
        if (vv == 0L) {
            writeByte((byte) 0);
            return;
        }
        do {
            byte b = (byte) (vv & 0x7F);
            vv >>= 7;
            if (vv != 0L) {
                b |= (byte) 0x80;
            }
            writeByte(b);
        } while (vv != 0L);
    }

    @Override
    public void writeSLEB128(long v) {
        long vv = v;
        boolean more = true;
        while (more) {
            byte b = (byte) (vv & 0x7f);
            vv >>= 7; // arithmetic shift!
            // sign bit of b is second high order bit (0x40)
            if ((vv == 0L && (b & 0x40) == 0) || (vv == -1L && (b & 0x40) == 0x40)) {
                more = false;
            } else {
                b |= 0x80;
            }
            writeByte(b);
        }
    }

    @Override
    public void writeBCD(double f) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void writeFloat(float f) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void writeDouble(double f) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void writeByte(byte b) {
        ensure(1);
        buf.put(b);
    }

    @Override
    public void write4Byte(int w) {
        ensure(4);
        buf.putInt(w);
    }

    @Override
    public void writeBlob(byte[] blob) {
        ensure(blob.length);
        buf.put(blob);
    }

    @Override
    public void write2Byte(short i) {
        ensure(2);
        buf.putShort(i);
    }

    @Override
    public void writeValue(short i) {
        write2Byte(i);
    }

    @Override
    public void write8Byte(long w) {
        ensure(8);
        buf.putLong(w);
    }

    @Override
    public void writeQuad(long w) {
        write8Byte(w);
    }

    @Override
    public void writeString(String s) {
        if (s != null) {
            writeBlob(s.getBytes());
        }
        writeByte((byte) 0);
    }

    @Override
    public void writeStringPadded(String s, int nBytes) {
        ensure(nBytes);
        assert s == null || s.length() < nBytes : "string oversize: " + s; // < b.c. of trailing \0
        writeString(s);
        final int pad = nBytes - (s == null ? 0 : s.length()) - 1;
        for (int i = 0; i < pad; ++i) {
            writeByte((byte) 0);
        }
    }

    @Override
    public ByteBuffer getBuffer() {
        return buf;
    }

    /**
     * Ensure there is enough space left in the buffer to write the given number of bytes.
     */
    private void ensure(int n) {
        if (buf.remaining() < n) {
            // determine a large enough grow factor
            final int cap = buf.capacity();
            final int pos = buf.position();
            final int req = pos + n;
            int newCap = (int) (cap * GROWTH_FACTOR);
            while (newCap < req) {
                newCap = (int) (newCap * GROWTH_FACTOR);
            }

            // grow and replace
            ByteBuffer nbuf = ByteBuffer.allocate(newCap);
            nbuf.order(ByteOrder.nativeOrder());
            byte[] old = new byte[pos];
            asBaseBuffer(buf).rewind();
            buf.get(old);
            nbuf.put(old);
            buf = nbuf;
        }
    }

    @Override
    public byte[] getBlob() {
        int len = buf.position();
        byte[] bytes = new byte[len];
        asBaseBuffer(buf).position(0);
        buf.get(bytes);
        return bytes;
    }

    @Override
    public void setByteOrder(ByteOrder byteOrder) {
        buf.order(byteOrder);
    }

    @Override
    public void skip(int n) {
        ensure(n);
        seek(pos() + n);
    }

    @Override
    public ByteOrder getByteOrder() {
        return buf.order();
    }

    @Override
    public void alignRead(int alignment) {
        while (pos() % alignment != 0) {
            buf.get();
        }
    }

    @Override
    public int read4Byte() {
        return buf.getInt();
    }

    @Override
    public byte readByte() {
        return buf.get();
    }

    @Override
    public char readUbyte() {
        return (char) (buf.get() & 0xff);
    }

    @Override
    public short read2Byte() {
        return buf.getShort();
    }

    @Override
    public short readValue() {
        return read2Byte();
    }

    @Override
    public long read8Byte() {
        return buf.getLong();
    }

    @Override
    public long readQuad() {
        return read8Byte();
    }

    @Override
    public void writeTruncatedLong(long value, int truncateTo) {
        switch (truncateTo) {
            case 8:
                write8Byte(value);
                break;
            case 4:
                write4Byte((int) value);
                break;
            case 2:
                write2Byte((short) value);
                break;
            case 1:
                writeByte((byte) value);
                break;
            default:
                throw new IllegalArgumentException("can only truncate to powers-of-two <= 8");
        }
    }

    @Override
    public long readTruncatedLong(int truncateTo) {
        switch (truncateTo) {
            case 8:
                return read8Byte();
            case 4:
                return read4Byte();
            case 2:
                return read2Byte();
            case 1:
                return readUbyte();
            default:
                throw new IllegalArgumentException("can only truncate to powers-of-two <= 8");
        }
    }

    @Override
    public String readZeroTerminatedString() {
        char[] buffer = new char[INITIAL_STRING_SIZE];
        int w = 1;
        buffer[0] = (char) buf.get();
        if (buffer[0] == '\0') {
            return "";
        }
        do {
            if (w == buffer.length) {
                buffer = Arrays.copyOf(buffer, 2 * buffer.length);
            }
            buffer[w] = (char) buf.get();
        } while (buffer[w++] != '\0');
        return String.valueOf(buffer, 0, w - 1);
    }

    @Override
    public long readLEB128() {
        long b = buf.get();
        if (b == 0L) {
            return 0L;
        }
        long r = 0L;
        int shift = 0;
        while ((b & 0x80) != 0) {
            r |= (b & 0x7f) << shift;
            shift += 7;
            b = buf.get();
        }
        r |= b << shift;
        return r;
    }

    @Override
    public long readSLEB128() {
        long r = 0L;
        int shift = 0;
        byte b;
        while (true) {
            b = buf.get();
            r |= (b & 0x7f) << shift;
            shift += 7;
            if ((b & 0x80) == 0) {
                break;
            }
        }
        // sign bit of b is second high order bit (0x40)
        // 64 is the number of bits in a long
        if ((shift < 64) && (b & 0x40) == 0x40) {
            r |= -(1 << shift);
        }
        return r;
    }

    @Override
    public byte[] readBlob(int length) {
        byte[] blob = new byte[length];
        buf.get(blob);
        return blob;
    }

}
