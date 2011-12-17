/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.lang;

import java.io.*;
import java.nio.*;

/**
 * Enumerated type with values for the most common more ways to arrange bits, bytes, etc.
 */
public enum Endianness {

    LITTLE {
        @Override
        public short readShort(InputStream stream) throws IOException {
            final int low = readByte(stream) & 0xff;
            final int high = readByte(stream);
            return (short) ((high << 8) | low);
        }

        @Override
        public int readInt(InputStream stream) throws IOException {
            final int low = readShort(stream) & 0xffff;
            final int high = readShort(stream);
            return (high << 16) | low;
        }

        @Override
        public long readLong(InputStream stream) throws IOException {
            final long low = readInt(stream) & 0xffffffffL;
            final long high = readInt(stream);
            return (high << 32) | low;
        }

        @Override
        public void writeShort(OutputStream stream, short value) throws IOException {
            short v = value;
            stream.write(v & 0xff);
            v >>= 8;
            stream.write(v & 0xff);
        }

        @Override
        public void writeInt(OutputStream stream, int value) throws IOException {
            int v = value;
            stream.write(v & 0xff);
            v >>= 8;
            stream.write(v & 0xff);
            v >>= 8;
            stream.write(v & 0xff);
            v >>= 8;
            stream.write(v & 0xff);
        }

        @Override
        public void writeLong(OutputStream stream, long value) throws IOException {
            long v = value;
            stream.write((int) v & 0xff);
            v >>= 8;
            stream.write((int) v & 0xff);
            v >>= 8;
            stream.write((int) v & 0xff);
            v >>= 8;
            stream.write((int) v & 0xff);
            v >>= 8;
            stream.write((int) v & 0xff);
            v >>= 8;
            stream.write((int) v & 0xff);
            v >>= 8;
            stream.write((int) v & 0xff);
            v >>= 8;
            stream.write((int) v & 0xff);
        }

        @Override
        public byte[] toBytes(short value) {
            short v = value;
            final byte[] bytes = new byte[2];
            bytes[0] = (byte) (v & 0xff);
            v >>= 8;
            bytes[1] = (byte) (v & 0xff);
            return bytes;
        }

        @Override
        public byte[] toBytes(int value) {
            int v = value;
            final byte[] bytes = new byte[4];
            bytes[0] = (byte) (v & 0xff);
            v >>= 8;
            bytes[1] = (byte) (v & 0xff);
            v >>= 8;
            bytes[2] = (byte) (v & 0xff);
            v >>= 8;
            bytes[3] = (byte) (v & 0xff);
            return bytes;
        }

        @Override
        public byte[] toBytes(long value) {
            long v = value;
            final byte[] bytes = new byte[8];
            bytes[0] = (byte) (v & 0xff);
            v >>= 8;
            bytes[1] = (byte) (v & 0xff);
            v >>= 8;
            bytes[2] = (byte) (v & 0xff);
            v >>= 8;
            bytes[3] = (byte) (v & 0xff);
            v >>= 8;
            bytes[4] = (byte) (v & 0xff);
            v >>= 8;
            bytes[5] = (byte) (v & 0xff);
            v >>= 8;
            bytes[6] = (byte) (v & 0xff);
            v >>= 8;
            bytes[7] = (byte) (v & 0xff);
            return bytes;
        }

        @Override
        public void toBytes(short value, byte[] result, int offset) {
            short v = value;
            for (int i = 0; i < Shorts.SIZE && i < result.length; i++) {
                result[i + offset] = (byte) (v & 0xff);
                v >>= 8;
            }
        }

        @Override
        public void toBytes(int value, byte[] result, int offset) {
            int v = value;
            for (int i = 0; i < Ints.SIZE && i < result.length; i++) {
                result[i + offset] = (byte) (v & 0xff);
                v >>= 8;
            }
        }

        @Override
        public void toBytes(long value, byte[] result, int offset) {
            long v = value;
            for (int i = 0; i < Longs.SIZE && i < result.length; i++) {
                result[i + offset] = (byte) (v & 0xff);
                v >>= 8;
            }
        }

        @Override
        public int offsetWithinWord(WordWidth wordWidth, WordWidth dataWidth) {
            return 0;
        }

        @Override
        public ByteOrder asByteOrder() {
            return ByteOrder.LITTLE_ENDIAN;
        }
    },
    BIG {
        @Override
        public short readShort(InputStream stream) throws IOException {
            final int high = readByte(stream);
            final int low = readByte(stream) & 0xff;
            return (short) ((high << 8) | low);
        }

        @Override
        public int readInt(InputStream stream) throws IOException {
            final int high = readShort(stream);
            final int low = readShort(stream) & 0xffff;
            return (high << 16) | low;
        }

        @Override
        public long readLong(InputStream stream) throws IOException {
            final long high = readInt(stream);
            final long low = readInt(stream) & 0xffffffffL;
            return (high << 32) | low;
        }

        @Override
        public void writeShort(OutputStream stream, short value) throws IOException {
            stream.write((value >> 8) & 0xff);
            stream.write(value & 0xff);
        }

        @Override
        public void writeInt(OutputStream stream, int value) throws IOException {
            stream.write((value >> 24) & 0xff);
            stream.write((value >> 16) & 0xff);
            stream.write((value >> 8) & 0xff);
            stream.write(value & 0xff);
        }

        @Override
        public void writeLong(OutputStream stream, long value) throws IOException {
            stream.write((int) (value >> 56) & 0xff);
            stream.write((int) (value >> 48) & 0xff);
            stream.write((int) (value >> 40) & 0xff);
            stream.write((int) (value >> 32) & 0xff);
            stream.write((int) (value >> 24) & 0xff);
            stream.write((int) (value >> 16) & 0xff);
            stream.write((int) (value >> 8) & 0xff);
            stream.write((int) value & 0xff);
        }

        @Override
        public byte[] toBytes(short value) {
            short v = value;
            final byte[] bytes = new byte[2];
            bytes[1] = (byte) (v & 0xff);
            v >>= 8;
            bytes[0] = (byte) (v & 0xff);
            return bytes;
        }

        @Override
        public byte[] toBytes(int value) {
            int v = value;
            final byte[] bytes = new byte[4];
            bytes[3] = (byte) (v & 0xff);
            v >>= 8;
            bytes[2] = (byte) (v & 0xff);
            v >>= 8;
            bytes[1] = (byte) (v & 0xff);
            v >>= 8;
            bytes[0] = (byte) (v & 0xff);
            return bytes;
        }

        @Override
        public byte[] toBytes(long value) {
            long v = value;
            final byte[] bytes = new byte[8];
            bytes[7] = (byte) (v & 0xff);
            v >>= 8;
            bytes[6] = (byte) (v & 0xff);
            v >>= 8;
            bytes[5] = (byte) (v & 0xff);
            v >>= 8;
            bytes[4] = (byte) (v & 0xff);
            v >>= 8;
            bytes[3] = (byte) (v & 0xff);
            v >>= 8;
            bytes[2] = (byte) (v & 0xff);
            v >>= 8;
            bytes[1] = (byte) (v & 0xff);
            v >>= 8;
            bytes[0] = (byte) (v & 0xff);
            return bytes;
        }

        @Override
        public void toBytes(short value, byte[] result, int offset) {
            short v = value;
            int toIndex = offset + Shorts.SIZE - 1;
            for (int i = 1; i <= Shorts.SIZE && i <= result.length; i++) {
                result[toIndex--] = (byte) (v & 0xff);
                v >>= 8;
            }
        }

        @Override
        public void toBytes(int value, byte[] result, int offset) {
            int v = value;
            int toIndex = offset + Ints.SIZE - 1;
            for (int i = 1; i <= Ints.SIZE && i <= result.length; i++) {
                result[toIndex--] = (byte) (v & 0xff);
                v >>= 8;
            }
        }

        @Override
        public void toBytes(long value, byte[] result, int offset) {
            long v = value;
            int toIndex = offset + Longs.SIZE - 1;
            for (int i = 1; i <= Longs.SIZE && i <= result.length; i++) {
                result[toIndex--] = (byte) (v & 0xff);
                v >>= 8;
            }
        }

        @Override
        public int offsetWithinWord(WordWidth wordWidth, WordWidth dataWidth) {
            assert wordWidth.numberOfBytes >= dataWidth.numberOfBytes;
            return wordWidth.numberOfBytes - dataWidth.numberOfBytes;
        }

        @Override
        public ByteOrder asByteOrder() {
            return ByteOrder.BIG_ENDIAN;
        }
    };

    @Override
    public String toString() {
        return name().toLowerCase();
    }

    public byte readByte(InputStream stream) throws IOException {
        final int result = stream.read();
        if (result < 0) {
            throw new IOException();
        }
        return (byte) result;
    }

    public abstract short readShort(InputStream stream) throws IOException;

    public abstract int readInt(InputStream stream) throws IOException;

    public abstract long readLong(InputStream stream) throws IOException;

    public abstract void writeShort(OutputStream stream, short value) throws IOException;

    public abstract void writeInt(OutputStream stream, int value) throws IOException;

    public abstract void writeLong(OutputStream stream, long value) throws IOException;

    public byte[] toBytes(byte value) {
        final byte[] bytes = new byte[1];
        bytes[0] = value;
        return bytes;
    }

    public abstract byte[] toBytes(short value);

    public abstract byte[] toBytes(int value);

    public abstract byte[] toBytes(long value);

    public void toBytes(byte value, byte[] result, int offset) {
        if (result.length > 0) {
            result[0] = value;
        }
    }

    public abstract void toBytes(short value, byte[] result, int offset);

    public abstract void toBytes(int value, byte[] result, int offset);

    public abstract void toBytes(long value, byte[] result, int offset);

    public abstract int offsetWithinWord(WordWidth wordWith, WordWidth dataWidth);

    public abstract ByteOrder asByteOrder();
}
