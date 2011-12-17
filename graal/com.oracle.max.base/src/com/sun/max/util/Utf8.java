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
package com.sun.max.util;

import java.io.*;

/**
 */
public final class Utf8 {

    private Utf8() {
    }

    /**
     * @return the length in bytes of the UTF8 representation of the string
     */
    public static int utf8Length(String string) {
        int result = 0;
        for (int i = 0; i < string.length(); i++) {
            final int ch = string.charAt(i);
            if ((ch >= 0x0001) && (ch <= 0x007F)) {
                result++;
            } else if (ch > 0x07FF) {
                result += 3;
            } else {
                result += 2;
            }
        }
        return result;
    }

    public static byte[] stringToUtf8(String string) {
        final byte[] result = new byte[utf8Length(string)];
        int index = 0;
        for (int i = 0; i < string.length(); i++) {
            final char ch = string.charAt(i);
            if ((ch >= 0x0001) && (ch <= 0x007F)) {
                result[index++] = (byte) ch;
            } else if (ch > 0x07FF) {
                result[index++] = (byte) (0xe0 | (byte) (ch >> 12));
                result[index++] = (byte) (0x80 | ((ch & 0xfc0) >> 6));
                result[index++] = (byte) (0x80 | (ch & 0x3f));
            } else {
                result[index++] = (byte) (0xc0 | (byte) (ch >> 6));
                result[index++] = (byte) (0x80 | (ch & 0x3f));
            }
        }
        return result;
    }

    /**
     * Reads a UTF-8 encoded String from {@code in}.
     *
     * @param in a data input source
     * @param zeroIsEncodedIn2Bytes if true, then 0 is decoded from two bytes as opposed to one
     * @param length the numbers of bytes to be decoded
     * @return the decoded string
     */
    public static String readUtf8(DataInput in, boolean zeroIsEncodedIn2Bytes, int length) throws IOException, Utf8Exception {
        if (length == 0) {
            return "";
        }
        final byte[] utf8Data = new byte[length];

        boolean sevenBit = true;
        for (int i = 0; i < length; i++) {
            final byte ch = in.readByte();
            utf8Data[i] = ch;
            if (ch < 0 || (zeroIsEncodedIn2Bytes && ch == 0)) {
                sevenBit = false;
            }
        }

        if (sevenBit) {
            final char[] charData = new char[length];
            for (int i = 0; i < length; i++) {
                charData[i] = (char) (utf8Data[i] & 0xff);
            }
            return new String(charData);
        }

        return utf8ToString(zeroIsEncodedIn2Bytes, utf8Data);
    }

    /**
     * Converts an array of UTF-8 data to a String.
     *
     * @param zeroIsEncodedIn2Bytes if true, then 0 is decoded from two bytes as opposed to one
     * @param utf8Data the data
     * @return the decoded string
     */
    public static String utf8ToString(boolean zeroIsEncodedIn2Bytes, byte[] utf8Data) throws Utf8Exception {
        final int length = utf8Data.length;
        int count = 0;
        final StringBuilder sb = new StringBuilder(length);

        while (count < length) {
            final int c = utf8Data[count] & 0xff;
            if (zeroIsEncodedIn2Bytes && c == 0) {
                throw new Utf8Exception();
            }
            switch (c >> 4) {
                case 0: case 1: case 2: case 3: case 4: case 5: case 6: case 7: {
                    /* 0xxxxxxx*/
                    count++;
                    sb.append((char) c);
                    break;
                }
                case 12: case 13: {
                    /* 110x xxxx   10xx xxxx*/
                    count += 2;
                    if (count > length) {
                        throw new Utf8Exception();
                    }
                    final int char2 = utf8Data[count - 1];
                    if ((char2 & 0xC0) != 0x80) {
                        throw new Utf8Exception();
                    }
                    sb.append((char) (((c & 0x1F) << 6) | (char2 & 0x3F)));
                    break;
                }
                case 14: {
                    /* 1110 xxxx  10xx xxxx  10xx xxxx */
                    count += 3;
                    if (count > length) {
                        throw new Utf8Exception();
                    }
                    final int char2 = utf8Data[count - 2];
                    final int char3 = utf8Data[count - 1];
                    if (((char2 & 0xC0) != 0x80) || ((char3 & 0xC0) != 0x80)) {
                        throw new Utf8Exception();
                    }
                    sb.append((char) (((c & 0x0F) << 12) |
                                      ((char2 & 0x3F) << 6)  |
                                      ((char3 & 0x3F) << 0)));
                    break;
                }
                default: {
                    /* 10xx xxxx,  1111 xxxx */
                    throw new Utf8Exception();
                }
            }
        }
        // The number of chars produced may be less than utflen
        return new String(sb);
    }

    private static byte[] readZeroTerminatedBytes(InputStream inputStream) throws IOException {
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        while (true) {
            final int ch = inputStream.read();
            if (ch < 0) {
                throw new IOException();
            }
            if (ch == 0) {
                return buffer.toByteArray();
            }
            buffer.write(ch);
        }
    }

    /**
     * Reads a 0-terminated UTF8 encoded string from a given stream.
     *
     * @param inputStream the stream to read from
     * @return the String constructed from the UTF8 encoded chars read from {@code inputStream}, omitting the terminating 0
     */
    public static String readString(InputStream inputStream) throws IOException, Utf8Exception {
        final byte[] utf8Data = readZeroTerminatedBytes(inputStream);
        return Utf8.utf8ToString(false, utf8Data);
    }

    /**
     * Writes a 0-terminated UTF8 encoded string to a given stream.
     *
     * @param inputStream the stream to read from
     * @param string the String to be written
     */
    public static void writeString(OutputStream outputStream, String string) throws IOException {
        outputStream.write(stringToUtf8(string));
        outputStream.write((byte) 0);
    }
}
