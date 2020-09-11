/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.meta;

import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.substitutions.Target_sun_misc_Unsafe;

/**
 * Helper for converting Java 8 strings to and from Java 11 strings. Taken from the java 11 String
 * implementation.
 */
// TODO(garcia): Support more than UTF16
public class StringUtil {
    static final byte LATIN1 = 0;
    static final byte UTF16 = 1;

    private static final int MAX_LENGTH = Integer.MAX_VALUE >> 1;

    public static byte[] toBytes(char[] value) {
        return toBytes(value, 0, value.length);
    }

    public static char[] toChars(byte[] value) {
        char[] dst = new char[value.length >> 1];
        getChars(value, 0, dst.length, dst, 0);
        return dst;
    }

    public static byte[] compress(char[] val) {
        int off = 0;
        int len = val.length;
        byte[] ret = new byte[len];
        if (compress(val, off, ret, 0, len) == len) {
            return ret;
        }
        return null;
    }

    // compressedCopy char[] -> byte[]
    private static int compress(char[] src, int srcOffset, byte[] dst, int dstOffset, int len) {
        int dstOff = dstOffset;
        int srcOff = srcOffset;
        for (int i = 0; i < len; i++) {
            char c = src[srcOff];
            if (c > 0xFF) {
                return 0;
            }
            dst[dstOff] = (byte) c;
            srcOff++;
            dstOff++;
        }
        return len;
    }

    private static byte[] toBytes(char[] value, int offset, int len) {
        int off = offset;
        byte[] val = newBytesFor(len);
        for (int i = 0; i < len; i++) {
            putChar(val, i, value[off]);
            off++;
        }
        return val;
    }

    private static void getChars(byte[] value, int srcBegin, int srcEnd, char[] dst, int dstBegin) {
        int pos = dstBegin;
        // We need a range check here because 'getChar' has no checks
        if (srcBegin < srcEnd) {
            checkBoundsOffCount(srcBegin, srcEnd - srcBegin, value);
        }
        for (int i = srcBegin; i < srcEnd; i++) {
            dst[pos++] = getChar(value, i);
        }
    }

    private static void checkBoundsOffCount(int offset, int count, byte[] val) {
        checkBoundsOffCount(offset, count, length(val));
    }

    private static void checkBoundsOffCount(int offset, int count, int length) {
        if (offset < 0 || count < 0 || offset > length - count) {
            throw new StringIndexOutOfBoundsException(
                            "offset " + offset + ", count " + count + ", length " + length);
        }
    }

    private static char getChar(byte[] val, int index) {
        int pos = index;
        assert pos >= 0 && pos < length(val) : "Trusted caller missed bounds check";
        pos <<= 1;
        return (char) (((val[pos++] & 0xff) << HI_BYTE_SHIFT) |
                        ((val[pos] & 0xff) << LO_BYTE_SHIFT));
    }

    private static byte[] newBytesFor(int len) {
        if (len < 0) {
            throw new NegativeArraySizeException();
        }
        if (len > MAX_LENGTH) {
            throw new OutOfMemoryError("UTF16 String size is " + len +
                            ", should be less than " + MAX_LENGTH);
        }
        return new byte[len << 1];
    }

    private static void putChar(byte[] val, int index, int c) {
        int pos = index;
        assert pos >= 0 && pos < length(val) : "Trusted caller missed bounds check";
        pos <<= 1;
        val[pos++] = (byte) (c >> HI_BYTE_SHIFT);
        val[pos] = (byte) (c >> LO_BYTE_SHIFT);
    }

    private static int length(byte[] value) {
        return value.length >> 1;
    }

    private static boolean isBigEndian() {
        return Target_sun_misc_Unsafe.isBigEndian0(StaticObject.NULL);
    }

    private static final int HI_BYTE_SHIFT;
    private static final int LO_BYTE_SHIFT;
    static {
        if (isBigEndian()) {
            HI_BYTE_SHIFT = 8;
            LO_BYTE_SHIFT = 0;
        } else {
            HI_BYTE_SHIFT = 0;
            LO_BYTE_SHIFT = 8;
        }
    }
}
