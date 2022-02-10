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
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

/**
 * Modified UTF-8 conversions.
 */
public final class ModifiedUtf8 extends Charset {

    public ModifiedUtf8() {
        super("x-Modified-UTF-8", new String[]{});
    }

    public static int utfLength(String str) {
        return utfLength(str, 0, str.length());
    }

    public static int utfLength(String str, int start, int len) {
        int utflen = 0;

        /* use charAt instead of copying String to char array */
        for (int i = start; i < len; i++) {
            int c = str.charAt(i);
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
        char[] chararr = new char[utflen];

        int c;
        int char2;
        int char3;
        int count = 0;
        int chararrCount = 0;

        while (count < utflen) {
            c = bytearr[count + offset] & 0xff;
            if (c > 127) {
                break;
            }
            count++;
            chararr[chararrCount++] = (char) c;
        }

        while (count < utflen) {
            c = bytearr[count + offset] & 0xff;
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
                    chararr[chararrCount++] = (char) c;
                    break;
                case 12:
                case 13:
                    /* 110x xxxx 10xx xxxx */
                    count += 2;
                    if (count > utflen) {
                        throw throwUTFDataFormatException("malformed input: partial character at end");
                    }
                    char2 = bytearr[count - 1 + offset];
                    if ((char2 & 0xC0) != 0x80) {
                        throw throwUTFDataFormatException(malformedInputMessage(count));
                    }
                    chararr[chararrCount++] = (char) (((c & 0x1F) << 6) |
                                    (char2 & 0x3F));
                    break;
                case 14:
                    /* 1110 xxxx 10xx xxxx 10xx xxxx */
                    count += 3;
                    if (count > utflen) {
                        throw throwUTFDataFormatException("malformed input: partial character at end");
                    }
                    char2 = bytearr[count - 2 + offset];
                    char3 = bytearr[count - 1 + offset];
                    if (((char2 & 0xC0) != 0x80) || ((char3 & 0xC0) != 0x80)) {
                        throw throwUTFDataFormatException(malformedInputMessage(count - 1));
                    }
                    chararr[chararrCount++] = (char) (((c & 0x0F) << 12) |
                                    ((char2 & 0x3F) << 6) |
                                    ((char3 & 0x3F) << 0));
                    break;
                default:
                    /* 10xx xxxx, 1111 xxxx */
                    throw throwUTFDataFormatException(malformedInputMessage(count));
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

    @Override
    public boolean contains(Charset cs) {
        // Use a set?
        return ((cs.name().equals("US-ASCII"))
                || (cs.name().equals("ISO-8859-1"))
                || (cs.name().equals("ISO-8859-15"))
                || (cs.name().equals("ISO-8859-16"))
                || (cs.name().equals("windows-1252"))
                || (cs.name().equals("UTF-8"))
                || (cs.name().equals("UTF-16"))
                || (cs.name().equals("UTF-16BE"))
                || (cs.name().equals("UTF-16LE"))
                || (cs.name().equals("x-UTF-16LE-BOM"))
                || (cs.name().equals("GBK"))
                || (cs.name().equals("GB18030"))
                || (cs.name().equals("ISO-8859-2"))
                || (cs.name().equals("ISO-8859-3"))
                || (cs.name().equals("ISO-8859-4"))
                || (cs.name().equals("ISO-8859-5"))
                || (cs.name().equals("ISO-8859-6"))
                || (cs.name().equals("ISO-8859-7"))
                || (cs.name().equals("ISO-8859-8"))
                || (cs.name().equals("ISO-8859-9"))
                || (cs.name().equals("ISO-8859-13"))
                || (cs.name().equals("JIS_X0201"))
                || (cs.name().equals("x-JIS0208"))
                || (cs.name().equals("JIS_X0212-1990"))
                || (cs.name().equals("GB2312"))
                || (cs.name().equals("EUC-KR"))
                || (cs.name().equals("x-EUC-TW"))
                || (cs.name().equals("EUC-JP"))
                || (cs.name().equals("x-euc-jp-linux"))
                || (cs.name().equals("KOI8-R"))
                || (cs.name().equals("TIS-620"))
                || (cs.name().equals("x-ISCII91"))
                || (cs.name().equals("windows-1251"))
                || (cs.name().equals("windows-1253"))
                || (cs.name().equals("windows-1254"))
                || (cs.name().equals("windows-1255"))
                || (cs.name().equals("windows-1256"))
                || (cs.name().equals("windows-1257"))
                || (cs.name().equals("windows-1258"))
                || (cs.name().equals("windows-932"))
                || (cs.name().equals("x-mswin-936"))
                || (cs.name().equals("x-windows-949"))
                || (cs.name().equals("x-windows-950"))
                || (cs.name().equals("windows-31j"))
                || (cs.name().equals("Big5"))
                || (cs.name().equals("Big5-HKSCS"))
                || (cs.name().equals("x-MS950-HKSCS"))
                || (cs.name().equals("ISO-2022-JP"))
                || (cs.name().equals("ISO-2022-KR"))
                || (cs.name().equals("x-ISO-2022-CN-CNS"))
                || (cs.name().equals("x-ISO-2022-CN-GB"))
                || (cs.name().equals("x-Johab"))
                || (cs.name().equals("Shift_JIS")));
    }

    @Override
    public CharsetDecoder newDecoder() {
        return new Decoder(this);
    }

    @Override
    public CharsetEncoder newEncoder() {
        return new Encoder(this);
    }

    private static final class Decoder extends CharsetDecoder {
        private Decoder(Charset cs) {
            super(cs, 1.0f, 1.0f);
        }

        private static CoderResult overflow(Buffer in) {
            in.position(in.position() - 1);
            return CoderResult.OVERFLOW;
        }

        private static CoderResult underflow(Buffer in) {
            in.position(in.position() - 1);
            return CoderResult.UNDERFLOW;
        }

        private static CoderResult malformedForLength(ByteBuffer src,
                                                      int delta,
                                                      int malformedNB) {
            src.position(src.position() + delta);
            return CoderResult.malformedForLength(malformedNB);
        }

        @Override
        protected CoderResult decodeLoop(ByteBuffer in, CharBuffer out) {
            while (in.hasRemaining()) {
                int c = in.get() & 0xff;
                if (c > 127) {
                    in.position(in.position() - 1);
                    break;
                }
                if (!out.hasRemaining()) {
                    return overflow(in);
                }
                out.put((char) c);
            }

            while (in.hasRemaining()) {
                int c = in.get() & 0xff;
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
                        if (!out.hasRemaining()) {
                            return overflow(in);
                        }
                        out.put((char) c);
                        break;
                    case 12:
                    case 13: {
                        /* 110x xxxx  10xx xxxx */
                        if (!in.hasRemaining()) {
                            return underflow(in);
                        }
                        if (!out.hasRemaining()) {
                            return overflow(in);
                        }
                        int char2 = in.get() & 0xff;
                        if ((char2 & 0xC0) != 0x80) {
                            return malformedForLength(in, -2, 2);
                        }
                        out.put((char) (((c & 0x1F) << 6) |
                                (char2 & 0x3F)));
                        break;
                    }
                    case 14: {
                        /* 1110 xxxx  10xx xxxx  10xx xxxx */
                        if (in.remaining() < 2) {
                            return underflow(in);
                        }
                        if (!out.hasRemaining()) {
                            return overflow(in);
                        }
                        int char2 = in.get() & 0xff;
                        int char3 = in.get() & 0xff;
                        if (((char2 & 0xC0) != 0x80) || ((char3 & 0xC0) != 0x80)) {
                            return malformedForLength(in, -3, 3);
                        }
                        out.put((char) (((c & 0x0F) << 12) |
                                ((char2 & 0x3F) << 6) |
                                ((char3 & 0x3F) << 0)));
                        break;
                    }
                    default:
                        /* 10xx xxxx, 1111 xxxx */
                        return malformedForLength(in, -1, 1);
                }
            }
            return CoderResult.UNDERFLOW;
        }
    }

    private static class Encoder extends CharsetEncoder {
        private Encoder(Charset charset) {
            super(charset, 1.0f, 3.0f);
        }

        private static CoderResult overflow(Buffer in) {
            in.position(in.position() - 1);
            return CoderResult.OVERFLOW;
        }

        @Override
        protected CoderResult encodeLoop(CharBuffer in, ByteBuffer out) {
            while (in.hasRemaining()) {
                int c = in.get();
                if (!((c >= 0x0001) && (c <= 0x007F))) {
                    break;
                }
                if (!out.hasRemaining()) {
                    return overflow(in);
                }
                out.put((byte) c);
            }

            while (in.hasRemaining()) {
                int c = in.get();
                if ((c >= 0x0001) && (c <= 0x007F)) {
                    if (!out.hasRemaining()) {
                        return overflow(in);
                    }
                    out.put((byte) c);
                } else if (c > 0x07FF) {
                    if (out.remaining() < 3) {
                        return overflow(in);
                    }
                    out.put((byte) (0xE0 | ((c >> 12) & 0x0F)));
                    out.put((byte) (0x80 | ((c >> 6) & 0x3F)));
                    out.put((byte) (0x80 | ((c >> 0) & 0x3F)));
                } else {
                    if (out.remaining() < 2) {
                        return overflow(in);
                    }
                    out.put((byte) (0xC0 | ((c >> 6) & 0x1F)));
                    out.put((byte) (0x80 | ((c >> 0) & 0x3F)));
                }
            }
            return CoderResult.UNDERFLOW;
        }
    }
}
