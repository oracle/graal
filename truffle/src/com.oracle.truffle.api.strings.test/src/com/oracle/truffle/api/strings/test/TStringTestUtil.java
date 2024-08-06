/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.oracle.truffle.api.strings.test;

import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import com.oracle.truffle.api.strings.AbstractTruffleString;
import com.oracle.truffle.api.strings.InternalByteArray;
import com.oracle.truffle.api.strings.TruffleString;

public final class TStringTestUtil {

    public static byte[] byteArray(int... values) {
        byte[] ret = new byte[values.length];
        for (int i = 0; i < ret.length; i++) {
            assert 0 <= values[i] && values[i] <= 0xff;
            ret[i] = (byte) values[i];
        }
        return ret;
    }

    public static int readS0(byte[] array, int index) {
        return Byte.toUnsignedInt(array[index]);
    }

    public static int readValue(byte[] array, int stride, int index) {
        int i = index << stride;
        if (stride == 0) {
            return Byte.toUnsignedInt(array[i]);
        }
        if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
            if (stride == 1) {
                return Byte.toUnsignedInt(array[i]) | (Byte.toUnsignedInt(array[i + 1]) << 8);
            } else {
                return Byte.toUnsignedInt(array[i]) | (Byte.toUnsignedInt(array[i + 1]) << 8) | (Byte.toUnsignedInt(array[i + 2]) << 16) | (Byte.toUnsignedInt(array[i + 3]) << 24);
            }
        } else {
            if (stride == 1) {
                return Byte.toUnsignedInt(array[i + 1]) | (Byte.toUnsignedInt(array[i]) << 8);
            } else {
                return Byte.toUnsignedInt(array[i + 3]) | (Byte.toUnsignedInt(array[i + 2]) << 8) | (Byte.toUnsignedInt(array[i + 1]) << 16) | (Byte.toUnsignedInt(array[i]) << 24);
            }
        }
    }

    public static void writeValue(byte[] array, int stride, int index, int value) {
        writeValue(array, stride, index, value, ByteOrder.nativeOrder());
    }

    public static void writeValue(byte[] array, int stride, int index, int value, ByteOrder byteOrder) {
        int i = index << stride;
        if (stride == 0) {
            array[i] = (byte) value;
            return;
        }
        if (byteOrder.equals(ByteOrder.LITTLE_ENDIAN)) {
            if (stride == 1) {
                array[i] = (byte) value;
                array[i + 1] = (byte) (value >> 8);
            } else {
                array[i] = (byte) value;
                array[i + 1] = (byte) (value >> 8);
                array[i + 2] = (byte) (value >> 16);
                array[i + 3] = (byte) (value >> 24);
            }
        } else {
            if (stride == 1) {
                array[i] = (byte) (value >> 8);
                array[i + 1] = (byte) value;
            } else {
                array[i] = (byte) (value >> 24);
                array[i + 1] = (byte) (value >> 16);
                array[i + 2] = (byte) (value >> 8);
                array[i + 3] = (byte) value;
            }
        }
    }

    static String hex(byte[] array) {
        return hex(array, array.length);
    }

    static String hex(byte[] array, int length) {
        if (array.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("0x%02x", array[0]));
        for (int i = 1; i < length; i++) {
            sb.append(String.format(" 0x%02x", array[i]));
        }
        return sb.toString();
    }

    static char[] toCharArrayPunned(byte[] array) {
        assert (array.length & 1) == 0;
        char[] charArray = new char[array.length / 2];
        for (int i = 0; i < charArray.length; i++) {
            charArray[i] = (char) readValue(array, 1, i);
        }
        return charArray;
    }

    public static int[] toIntArray(byte[] array) {
        int[] ret = new int[array.length];
        for (int i = 0; i < array.length; i++) {
            ret[i] = Byte.toUnsignedInt(array[i]);
        }
        return ret;
    }

    public static int[] toIntArray(String str) {
        int[] ret = new int[str.length()];
        for (int i = 0; i < str.length(); i++) {
            ret[i] = str.charAt(i);
        }
        return ret;
    }

    public static byte[] asciiArray(TruffleString.Encoding encoding, String str) {
        if (encoding == TruffleString.Encoding.UTF_16) {
            return toStride(str, 1);
        } else if (encoding == TruffleString.Encoding.UTF_32) {
            return toStride(str, 2);
        }
        return str.getBytes(StandardCharsets.ISO_8859_1);
    }

    public static byte[] toStride(String str, int stride) {
        byte[] ret = new byte[str.length() << stride];
        for (int i = 0; i < str.length(); i++) {
            writeValue(ret, stride, i, str.charAt(i));
        }
        return ret;
    }

    public static int[] intRange(int start, int length) {
        return intRange(start, length, 1);
    }

    public static int[] intRange(int start, int length, int stride) {
        int[] ret = new int[length];
        int value = start;
        for (int i = 0; i < length; i++) {
            ret[i] = value;
            value += stride;
        }
        return ret;
    }

    public static byte[] concat(byte[] arrayA, byte[] arrayB) {
        byte[] array = Arrays.copyOf(arrayA, arrayA.length + arrayB.length);
        System.arraycopy(arrayB, 0, array, arrayA.length, arrayB.length);
        return array;
    }

    public static int utf8DecodeValid(AbstractTruffleString a, int i) {
        InternalByteArray internalByteArray = a.getInternalByteArrayUncached(TruffleString.Encoding.UTF_8);
        return utf8DecodeValid(internalByteArray.getArray(), internalByteArray.getOffset(), internalByteArray.getLength(), i);
    }

    @SuppressWarnings("fallthrough")
    static int utf8DecodeValid(byte[] arrayA, int offsetA, int lengthA, int index) {
        int i = offsetA + index;
        int b = readS0(arrayA, i);
        if (b < 0x80) {
            return b;
        }
        int nBytes = utf8CodePointLength(b);
        int codepoint = b & (0xff >>> nBytes);
        assert 1 < nBytes && nBytes < 5 : nBytes;
        assert index + nBytes <= lengthA;
        int j = i + 1;
        // Checkstyle: stop
        switch (nBytes) {
            case 4:
                assert isUTF8ContinuationByte(arrayA, j);
                codepoint = codepoint << 6 | (readS0(arrayA, j++) & 0x3f);
            case 3:
                assert isUTF8ContinuationByte(arrayA, j);
                codepoint = codepoint << 6 | (readS0(arrayA, j++) & 0x3f);
            default:
                assert isUTF8ContinuationByte(arrayA, j);
                codepoint = codepoint << 6 | (readS0(arrayA, j) & 0x3f);
        }
        // Checkstyle: resume
        return codepoint;
    }

    static int utf8CodePointLength(int firstByte) {
        return Integer.numberOfLeadingZeros(~(firstByte << 24));
    }

    static boolean isUTF8ContinuationByte(int b) {
        return (b & 0xc0) == 0x80;
    }

    static boolean isUTF8ContinuationByte(byte[] array, int i) {
        return isUTF8ContinuationByte(readS0(array, i));
    }
}
