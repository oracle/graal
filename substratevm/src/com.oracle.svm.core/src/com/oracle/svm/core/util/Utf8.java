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
package com.oracle.svm.core.util;

import java.nio.ByteBuffer;

import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;

/**
 * Implements UTF-8 encoding and decoding of strings with support for zero-bytes as string
 * terminators.
 */
public final class Utf8 {

    private Utf8() {
    }

    /**
     * @return the length in bytes of the UTF8 representation of the string
     */
    public static int utf8Length(String string) {
        return utf8Length(string, 0, string.length());
    }

    /**
     * @param beginIndex first index that is part of the region, inclusive
     * @param endIndex index at the end of the region, exclusive
     * @return the length in bytes of the UTF8 representation of the string region
     */
    public static int utf8Length(String s, int beginIndex, int endIndex) {
        if (beginIndex < 0 || endIndex > s.length() || beginIndex > endIndex) {
            throw new StringIndexOutOfBoundsException();
        }
        int length = 0;
        for (int i = beginIndex; i < endIndex; i++) {
            final int c = s.charAt(i);
            if ((c >= 0x0001) && (c <= 0x007F)) {
                length++;
            } else if (c > 0x07FF) {
                length += 3;
            } else {
                length += 2;
            }
        }
        return length;
    }

    /**
     * @return maximum number of bytes needed to represent the specified number of characters.
     */
    public static int maxUtf8ByteLength(int charCount, boolean zeroTerminate) {
        assert charCount >= 0;
        return 3 * charCount + (zeroTerminate ? 1 : 0);
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

    public static byte[] stringToUtf8(String source, boolean zeroTerminate) {
        int length = utf8Length(source) + (zeroTerminate ? 1 : 0);
        ByteBuffer buffer = ByteBuffer.allocate(length);
        substringToUtf8(buffer, source, 0, source.length(), zeroTerminate);
        return buffer.array();
    }

    /**
     * Converts a byte buffer of UTF-8 data to a String. The entire buffer until the
     * {@link ByteBuffer#limit() buffer's limit} is converted unless {@code zeroTerminated} is
     * {@code true}, in which case conversion stops at the first zero byte.
     *
     * @param zeroTerminated if true, then a 0 byte marks the end of the string, and character '\0'
     *            in the input must be encoded as two bytes as opposed to one
     * @param source the byte buffer to read from
     * @return the decoded string, or null if the buffer is not a valid UTF-8 string.
     */
    public static String utf8ToString(boolean zeroTerminated, ByteBuffer source) {
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
                        return null;
                    }
                    sb.append((char) (((c0 & 0x1F) << 6) | (c1 & 0x3F)));
                    break;
                }
                case 14: {
                    /* 1110 xxxx 10xx xxxx 10xx xxxx */
                    final int c1 = source.get();
                    final int c2 = source.get();
                    if (((c1 & 0xC0) != 0x80) || ((c2 & 0xC0) != 0x80)) {
                        return null;
                    }
                    sb.append((char) (((c0 & 0x0F) << 12) | ((c1 & 0x3F) << 6) | (c2 & 0x3F)));
                    break;
                }
                default: {
                    /* 10xx xxxx, 1111 xxxx */
                    return null;
                }
            }
        }
        return sb.toString();
    }

    /**
     * Converts a pointer to zero-terminated UTF-8 data to a String. If the provided data is the C
     * null pointer, or the data is not a valid UTF-8 string, then a Java null value is returned.
     *
     * @param source the memory to read from
     * @return the decoded string
     */
    public static String utf8ToString(CCharPointer source) {
        if (source.isNull()) {
            return null;
        }
        return utf8ToString(true, CTypeConversion.asByteBuffer(source, Integer.MAX_VALUE));
    }
}
