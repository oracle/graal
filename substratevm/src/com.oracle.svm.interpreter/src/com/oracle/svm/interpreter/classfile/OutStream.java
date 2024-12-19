/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.interpreter.classfile;

import java.io.UTFDataFormatException;
import java.io.UncheckedIOException;
import java.util.Arrays;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.util.VMError;

@Platforms(Platform.HOSTED_ONLY.class)
final class OutStream {

    OutStream(int initialCapacity) {
        this.bytes = new byte[initialCapacity];
        this.offset = 0;
    }

    OutStream() {
        this(32);
    }

    private byte[] bytes;
    private int offset;

    public void writeByte(byte value) {
        ensureCapacity(offset + 1);
        bytes[offset++] = value;
    }

    public void writeU1(int value) {
        VMError.guarantee(0 <= value && value <= 0xFF);
        ensureCapacity(offset + 1);
        bytes[offset++] = (byte) value;
    }

    private void ensureCapacity(int capacity) {
        if (bytes.length < capacity) {
            int newCapacity = Math.max(capacity, bytes.length * 2 + 1);
            this.bytes = Arrays.copyOf(bytes, newCapacity);
        }
    }

    public void writeShort(short value) {
        writeByte((byte) (value >>> 8));
        writeByte((byte) value);
    }

    public void writeU2(int value) {
        VMError.guarantee(0 <= value && value <= 0xFFFF);
        writeByte((byte) (value >>> 8));
        writeByte((byte) value);
    }

    public void writeInt(int value) {
        writeByte((byte) (value >>> 24));
        writeByte((byte) (value >>> 16));
        writeByte((byte) (value >>> 8));
        writeByte((byte) (value >>> 0));
    }

    public void writeLong(long value) {
        writeByte((byte) (value >>> 56));
        writeByte((byte) (value >>> 48));
        writeByte((byte) (value >>> 40));
        writeByte((byte) (value >>> 32));
        writeByte((byte) (value >>> 24));
        writeByte((byte) (value >>> 16));
        writeByte((byte) (value >>> 8));
        writeByte((byte) (value >>> 0));
    }

    public void writeFloat(float value) {
        writeInt(Float.floatToIntBits(value));
    }

    public void writeDouble(double value) {
        writeLong(Double.doubleToLongBits(value));
    }

    public int writeUTF(String str) {
        final int strlen = str.length();
        int utflen = strlen; // optimized for ASCII

        for (int i = 0; i < strlen; i++) {
            int c = str.charAt(i);
            if (c >= 0x80 || c == 0) {
                utflen += (c >= 0x800) ? 2 : 1;
            }
        }

        if (utflen > 65535 || /* overflow */ utflen < strlen) {
            throw new UncheckedIOException(new UTFDataFormatException(tooLongMsg(str, utflen)));
        }

        writeByte((byte) ((utflen >>> 8) & 0xFF));
        writeByte((byte) ((utflen >>> 0) & 0xFF));

        int i = 0;
        for (i = 0; i < strlen; i++) { // optimized for initial run of ASCII
            int c = str.charAt(i);
            if (c >= 0x80 || c == 0) {
                break;
            }
            writeByte((byte) c);
        }

        for (; i < strlen; i++) {
            int c = str.charAt(i);
            if (c < 0x80 && c != 0) {
                writeByte((byte) c);
            } else if (c >= 0x800) {
                writeByte((byte) (0xE0 | ((c >> 12) & 0x0F)));
                writeByte((byte) (0x80 | ((c >> 6) & 0x3F)));
                writeByte((byte) (0x80 | ((c >> 0) & 0x3F)));
            } else {
                writeByte((byte) (0xC0 | ((c >> 6) & 0x1F)));
                writeByte((byte) (0x80 | ((c >> 0) & 0x3F)));
            }
        }

        return utflen + 2;
    }

    private static String tooLongMsg(String s, int bits32) {
        int slen = s.length();
        String head = s.substring(0, 8);
        String tail = s.substring(slen - 8, slen);
        // handle int overflow with max 3x expansion
        long actualLength = slen + Integer.toUnsignedLong(bits32 - slen);
        return "encoded string (" + head + "..." + tail + ") too long: " + actualLength + " bytes";
    }

    byte[] toArray() {
        return Arrays.copyOf(bytes, offset);
    }

    public void writeBytes(byte[] byteArray) {
        for (byte b : byteArray) {
            writeByte(b);
        }
    }

    int getOffset() {
        return offset;
    }

    void patchAtOffset(int targetOffset, Runnable action) {
        int oldOffset = getOffset();
        try {
            this.offset = targetOffset;
            action.run();
        } finally {
            this.offset = oldOffset;
        }
    }
}
