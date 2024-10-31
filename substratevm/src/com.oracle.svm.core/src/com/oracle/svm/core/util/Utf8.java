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
import java.util.function.Predicate;

import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;

/**
 * Implements UTF-8 encoding and decoding of strings with support for zero-bytes as string
 * terminators.
 */
public final class Utf8 {

    private Utf8() {
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+16/src/hotspot/share/utilities/utf8.cpp#L479-L488")
    private static int utf8Size(char c) {
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
     * @return the length as {@code long} in bytes of the UTF8 representation of the string
     */
    public static long utf8LengthAsLong(String string) {
        return utf8LengthAsLong(string, 0, string.length());
    }

    /**
     * @param beginIndex first index that is part of the region, inclusive
     * @param endIndex index at the end of the region, exclusive
     * @return the length as {@code long} in bytes of the UTF8 representation of the string
     */
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+16/src/hotspot/share/utilities/utf8.cpp#L502-L509")
    public static long utf8LengthAsLong(String s, int beginIndex, int endIndex) {
        if (beginIndex < 0 || endIndex > s.length() || beginIndex > endIndex) {
            throw new StringIndexOutOfBoundsException();
        }
        int length = s.length();
        long result = 0;
        for (int index = 0; index < length; index++) {
            result += utf8Size(s.charAt(index));
        }
        return result;
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
     * @return the length as {@code int} in bytes of the UTF8 representation of the string. Might
     *         return a truncated size if the value does not fit into {@code int} (see JDK-8328877).
     */
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+16/src/hotspot/share/utilities/utf8.cpp#L511-L526")
    public static int utf8Length(String s, int beginIndex, int endIndex) {
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

    /**
     * Wraps C memory with zero-terminated UTF-8 data or copies that data, returning a
     * {@link CharSequence}. If the provided pointer is the C null pointer, or the data is not a
     * valid UTF-8 string, then {@code null} is returned. The returned object is only safe to access
     * while the C memory is, too.
     *
     * @see WrappedAsciiCString
     */
    public static CharSequence wrapUtf8CString(CCharPointer source) {
        if (source.isNull()) {
            return null;
        }
        int hash = 0;
        int length = 0;
        byte c = source.read(length);
        while (c > 0) { // signed, so 1..127
            length++;
            hash = 31 * hash + c; // compatible with String.hashCode()
            c = source.read(length);
        }
        if (c < 0) { // non-ASCII character: fallback to copying
            return utf8ToString(source);
        }
        return new WrappedAsciiCString(source, length, hash);
    }

    /**
     * Wraps C memory that contains a string consisting of only 7-bit ASCII characters. This should
     * be the case with many Strings that are passed in via JNI, such as class and member names,
     * which can then be used in lookups without having to be converted between character sets and
     * copied. In order to do lookups efficiently, the {@link #hashCode} which is computed by
     * {@link #wrapUtf8CString} is compatible with that of {@link String#hashCode}.
     */
    public static final class WrappedAsciiCString implements CharSequence {
        private final CCharPointer chars;
        private final int length;
        private final int hashCode;

        public WrappedAsciiCString(CCharPointer chars, int length, int hashCode) {
            this.chars = chars;
            this.length = length;
            this.hashCode = hashCode;

            assert ((Predicate<String>) (s -> s.length() == length() && s.hashCode() == hashCode() && CharSequence.compare(s, this) == 0)).test(utf8ToString(chars));
        }

        @Override
        public int length() {
            return length;
        }

        @Override
        public char charAt(int index) {
            if (index < 0 || index >= length) {
                throw new IndexOutOfBoundsException(index);
            }
            return (char) chars.read(index);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public String toString() {
            return new StringBuilder(this).toString();
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            throw new UnsupportedOperationException();
        }
    }
}
