/*
 * Copyright (c) 2007, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.io.CharConversionException;
import java.nio.ByteBuffer;

/**
 * Implements UTF-8 encoding and decoding of strings with support for zero-bytes as string
 * terminators.
 */
public final class Utf8 {

    private Utf8() {
    }

    private static int utf8Size(char c) {
        // Based On
        // https://github.com/openjdk/jdk/blob/jdk-24+16/src/hotspot/share/utilities/utf8.cpp#L479-L488
        if ((0x0001 <= c) && (c <= 0x007F)) {
            // ASCII character
            return 1;
        } else if (c <= 0x07FF) {
            return 2;
        } else {
            return 3;
        }
    }

    /**
     * @return the length as {@code int} in bytes of the UTF8 representation of the string. Might
     *         return a truncated size if the value does not fit into {@code int} (see JDK-8328877).
     */
    public static int utf8Length(String string) {
        return utf8Length(string, 0, string.length());
    }

    /**
     * @param beginIndex first index that is part of the region, inclusive
     * @param endIndex index at the end of the region, exclusive
     * @return the length as {@code int} in bytes of the UTF8 representation of the string region.
     *         Might return a truncated size if the value does not fit into {@code int} (see
     *         JDK-8328877).
     */
    public static int utf8Length(String s, int beginIndex, int endIndex) {
        // Based on
        // https://github.com/openjdk/jdk/blob/jdk-24+16/src/hotspot/share/utilities/utf8.cpp#L511-L526.
        if (beginIndex < 0 || endIndex > s.length() || beginIndex > endIndex) {
            throw new StringIndexOutOfBoundsException();
        }
        long result = 0;
        for (int index = beginIndex; index < endIndex; index++) {
            char c = s.charAt(index);
            long sz = utf8Size(c);
            // If the length is > INT_MAX-1 we truncate at a completed
            // modified-UTF8 encoding. This allows for +1 to be added
            // by the caller for NUL-termination, without overflow.
            if (result + sz > Integer.MAX_VALUE - 1) {
                break;
            }
            result += sz;
        }
        return (int) result;
    }

    /**
     * Writes an UTF8-encoded string region to a given byte buffer.
     *
     * @param dest the byte buffer to write to
     * @param source the String to be written
     * @param beginIndex first index in {@code source} that is part of the region, inclusive
     * @param endIndex index in {@code source} at the end of the region, exclusive
     * @param zeroTerminate whether to write a final zero byte
     */
    public static void substringToUtf8(ByteBuffer dest, String source, int beginIndex, int endIndex, boolean zeroTerminate) {
        if (beginIndex < 0 || endIndex > source.length() || beginIndex > endIndex) {
            throw new StringIndexOutOfBoundsException();
        }
        for (int i = beginIndex; i < endIndex; i++) {
            final char c = source.charAt(i);
            if ((c >= 0x0001) && (c <= 0x007F)) {
                dest.put((byte) c);
            } else if (c > 0x07FF) {
                dest.put((byte) (0xe0 | (byte) (c >> 12)));
                dest.put((byte) (0x80 | ((c & 0xfc0) >> 6)));
                dest.put((byte) (0x80 | (c & 0x3f)));
            } else {
                dest.put((byte) (0xc0 | (byte) (c >> 6)));
                dest.put((byte) (0x80 | (c & 0x3f)));
            }
        }
        if (zeroTerminate) {
            dest.put((byte) 0);
        }
    }

    /**
     * Converts a byte buffer of UTF-8 data to a String. The entire buffer until the
     * {@link ByteBuffer#limit() buffer's limit} is converted unless {@code zeroTerminated} is
     * {@code true}, in which case conversion stops at the first zero byte.
     *
     * @param zeroTerminated if true, then a 0 byte marks the end of the string, and character '\0'
     *            in the input must be encoded as two bytes as opposed to one
     * @param source the byte buffer to read from
     * @return the decoded string
     */
    public static String utf8ToString(boolean zeroTerminated, ByteBuffer source) throws CharConversionException {
        final StringBuilder sb = new StringBuilder();
        while (source.hasRemaining()) {
            final int c0 = source.get() & 0xff;
            if (zeroTerminated && c0 == 0) {
                break;
            }
            switch (c0 >> 4) {
                case 0:
                case 1:
                case 2:
                case 3:
                case 4:
                case 5:
                case 6:
                case 7: {
                    /* 0xxxxxxx */
                    sb.append((char) c0);
                    break;
                }
                case 12:
                case 13: {
                    /* 110x xxxx 10xx xxxx */
                    final int c1 = source.get();
                    if ((c1 & 0xC0) != 0x80) {
                        throw new CharConversionException();
                    }
                    sb.append((char) (((c0 & 0x1F) << 6) | (c1 & 0x3F)));
                    break;
                }
                case 14: {
                    /* 1110 xxxx 10xx xxxx 10xx xxxx */
                    final int c1 = source.get();
                    final int c2 = source.get();
                    if (((c1 & 0xC0) != 0x80) || ((c2 & 0xC0) != 0x80)) {
                        throw new CharConversionException();
                    }
                    sb.append((char) (((c0 & 0x0F) << 12) | ((c1 & 0x3F) << 6) | (c2 & 0x3F)));
                    break;
                }
                default: {
                    /* 10xx xxxx, 1111 xxxx */
                    throw new CharConversionException();
                }
            }
        }
        return sb.toString();
    }

}
