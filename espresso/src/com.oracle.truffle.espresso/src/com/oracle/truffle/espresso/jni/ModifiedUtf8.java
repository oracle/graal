/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.jni;

import java.io.IOException;
import java.io.UTFDataFormatException;
import java.nio.ByteBuffer;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

/**
 * Modified UTF-8 conversions.
 */
public final class ModifiedUtf8 {

    private ModifiedUtf8() {
        /* no instances */
    }

    public static int utfLength(String str) {
        return utfLength(str, 0, str.length());
    }

    public static int utfLength(String str, int start, int len) {
        int utflen = 0;

        /* use charAt instead of copying String to char array */
        int i;
        for (i = 0; i < len; i++) {
            int c = str.charAt(start + i);
            if (!((c >= 0x0001) && (c <= 0x007F))) {
                break;
            }
            utflen++;
        }

        for (; i < len; i++) {
            int c = str.charAt(start + i);
            if ((c >= 0x0001) && (c <= 0x007F)) {
                utflen++;
            } else if (c > 0x07FF) {
                utflen += 3;
            } else {
                utflen += 2;
            }
        }
        return utflen;
    }

    public static byte[] fromJavaString(String str, boolean append0) {
        return fromJavaString(str, 0, str.length(), append0);
    }

    public static byte[] fromJavaString(String str, int start, int len, boolean append0) {
        int utflen = utfLength(str, start, len);
        byte[] bytearr = new byte[utflen + (append0 ? 1 : 0)]; // 0 terminated, even if empty.

        int count = 0;
        int i;
        for (i = 0; i < len; i++) {
            int c = str.charAt(start + i);
            if (!((c >= 0x0001) && (c <= 0x007F))) {
                break;
            }
            bytearr[count++] = (byte) c;
        }

        for (; i < len; i++) {
            int c = str.charAt(start + i);
            if ((c >= 0x0001) && (c <= 0x007F)) {
                bytearr[count++] = (byte) c;

            } else if (c > 0x07FF) {
                bytearr[count++] = (byte) (0xE0 | ((c >> 12) & 0x0F));
                bytearr[count++] = (byte) (0x80 | ((c >> 6) & 0x3F));
                bytearr[count++] = (byte) (0x80 | ((c >> 0) & 0x3F));
            } else {
                bytearr[count++] = (byte) (0xC0 | ((c >> 6) & 0x1F));
                bytearr[count++] = (byte) (0x80 | ((c >> 0) & 0x3F));
            }
        }

        if (append0) {
            bytearr[bytearr.length - 1] = (byte) 0;
        }

        return bytearr;
    }

    public static byte[] fromJavaString(String string) {
        return fromJavaString(string, false);
    }

    public static String toJavaString(byte[] bytearr) throws IOException {
        return toJavaString(bytearr, 0, bytearr.length);
    }

    public static String toJavaString(byte[] bytearr, int offset, int utflen) throws IOException {
        return toJavaString(ByteBuffer.wrap(bytearr, offset, utflen));
    }

    public static String toJavaString(ByteBuffer buffer) throws IOException {
        char[] chararr = new char[buffer.remaining()];

        int c;
        int char2;
        int char3;
        int chararrCount = 0;

        while (buffer.hasRemaining()) {
            c = buffer.get() & 0xff;
            if (c > 127) {
                buffer.position(buffer.position() - 1);
                break;
            }
            chararr[chararrCount++] = (char) c;
        }

        while (buffer.hasRemaining()) {
            c = buffer.get() & 0xff;
            switch (c >> 4) {
                case 0:
                case 1:
                case 2:
                case 3:
                case 4:
                case 5:
                case 6:
                case 7:
                    /* 0xxxxxxx */
                    chararr[chararrCount++] = (char) c;
                    break;
                case 12:
                case 13:
                    /* 110x xxxx 10xx xxxx */
                    if (!buffer.hasRemaining()) {
                        throw throwUTFDataFormatException("malformed input: partial character at end");
                    }
                    char2 = buffer.get();
                    if ((char2 & 0xC0) != 0x80) {
                        throw throwUTFDataFormatException(malformedInputMessage(buffer.position()));
                    }
                    chararr[chararrCount++] = (char) (((c & 0x1F) << 6) |
                                    (char2 & 0x3F));
                    break;
                case 14:
                    /* 1110 xxxx 10xx xxxx 10xx xxxx */
                    if (buffer.remaining() < 2) {
                        throw throwUTFDataFormatException("malformed input: partial character at end");
                    }
                    char2 = buffer.get();
                    char3 = buffer.get();
                    if (((char2 & 0xC0) != 0x80) || ((char3 & 0xC0) != 0x80)) {
                        throw throwUTFDataFormatException(malformedInputMessage(buffer.position() - 1));
                    }
                    chararr[chararrCount++] = (char) (((c & 0x0F) << 12) |
                                    ((char2 & 0x3F) << 6) |
                                    ((char3 & 0x3F) << 0));
                    break;
                default:
                    /* 10xx xxxx, 1111 xxxx */
                    throw throwUTFDataFormatException(malformedInputMessage(buffer.position()));
            }
        }
        // The number of chars produced may be less than utflen
        return new String(chararr, 0, chararrCount);
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    private static UTFDataFormatException throwUTFDataFormatException(String message) throws UTFDataFormatException {
        throw new UTFDataFormatException(message);
    }

    @TruffleBoundary
    private static String malformedInputMessage(int count) {
        return "malformed input around byte " + count;
    }

    public static boolean isValid(byte[] bytearr, int offset, int length) {
        int c;
        int char2;
        int char3;
        int count = 0;

        while (count < length) {
            c = bytearr[count + offset] & 0xff;
            if (c == 0 || c > 127) {
                break;
            }
            count++;
        }

        while (count < length) {
            c = bytearr[count + offset] & 0xff;
            if (c == 0) {
                count += 2;
                if (count > length) {
                    return false;
                }
                char2 = bytearr[count - 1 + offset];
                if (char2 != 0) {
                    return false;
                }
            } else {
                switch (c >> 4) {
                    case 0:
                    case 1:
                    case 2:
                    case 3:
                    case 4:
                    case 5:
                    case 6:
                    case 7:
                        /* 0xxxxxxx */
                        count++;
                        break;
                    case 12:
                    case 13:
                        /* 110x xxxx 10xx xxxx */
                        count += 2;
                        if (count > length) {
                            return false;
                        }
                        char2 = bytearr[count - 1 + offset];
                        if ((char2 & 0xC0) != 0x80) {
                            return false;
                        }
                        break;
                    case 14:
                        /* 1110 xxxx 10xx xxxx 10xx xxxx */
                        count += 3;
                        if (count > length) {
                            return false;
                        }
                        char2 = bytearr[count - 2 + offset];
                        char3 = bytearr[count - 1 + offset];
                        if (((char2 & 0xC0) != 0x80) || ((char3 & 0xC0) != 0x80)) {
                            return false;
                        }
                        break;
                    default:
                        /* 10xx xxxx, 1111 xxxx */
                        return false;
                }
            }
        }
        // The number of chars produced may be less than utflen
        return true;
    }
}
