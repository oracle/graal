/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.strings.TruffleString.Encoding.Emacs_Mule;
import static com.oracle.truffle.api.strings.TruffleString.Encoding.ISO_8859_1;
import static com.oracle.truffle.api.strings.TruffleString.Encoding.Stateless_ISO_2022_JP;
import static com.oracle.truffle.api.strings.TruffleString.Encoding.Stateless_ISO_2022_JP_KDDI;
import static com.oracle.truffle.api.strings.TruffleString.Encoding.US_ASCII;
import static com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_16;
import static com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_32;
import static com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_8;
import static com.oracle.truffle.api.strings.TruffleString.Encoding.values;

import java.lang.reflect.Field;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumSet;

import org.graalvm.polyglot.Context;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;

import com.oracle.truffle.api.dsl.UnsupportedSpecializationException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.strings.AbstractTruffleString;
import com.oracle.truffle.api.strings.MutableTruffleString;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;
import com.oracle.truffle.api.strings.TruffleStringIterator;
import com.oracle.truffle.api.strings.bench.TStringTestDummyLanguage;

import sun.misc.Unsafe;

public class TStringTestBase {

    static Context context;

    @BeforeClass
    public static void setUp() {
        context = Context.newBuilder(TStringTestDummyLanguage.ID).allowNativeAccess(true).build();
        context.initialize(TStringTestDummyLanguage.ID);
        context.enter();
    }

    @AfterClass
    public static void tearDown() {
        context.leave();
        context.close();
    }

    protected static final TruffleString S_UTF8 = TruffleString.fromCodePointUncached('a', UTF_8);
    protected static final TruffleString S_UTF16 = TruffleString.fromCodePointUncached('a', UTF_16);
    protected static final TruffleString S_UTF32 = TruffleString.fromCodePointUncached('a', UTF_32);

    private static final sun.misc.Unsafe UNSAFE = getUnsafe();

    private static Unsafe getUnsafe() {
        try {
            return Unsafe.getUnsafe();
        } catch (SecurityException e1) {
            try {
                Field theUnsafeInstance = Unsafe.class.getDeclaredField("theUnsafe");
                theUnsafeInstance.setAccessible(true);
                return (Unsafe) theUnsafeInstance.get(Unsafe.class);
            } catch (Exception e2) {
                throw new RuntimeException("exception while trying to get Unsafe.theUnsafe via reflection:", e2);
            }
        }
    }

    private static final long byteBufferAddressOffset;

    static {
        Field addressField;
        try {
            addressField = Buffer.class.getDeclaredField("address");
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("exception while trying to get Buffer.address via reflection:", e);
        }
        byteBufferAddressOffset = UNSAFE.objectFieldOffset(addressField);
    }

    protected static boolean isDebugStrictEncodingChecks() {
        return Boolean.getBoolean("truffle.strings.debug-strict-encoding-checks");
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class PointerObject implements TruffleObject {

        private final ByteBuffer buffer;

        PointerObject(ByteBuffer buffer) {
            this.buffer = buffer;
        }

        public static PointerObject create(byte[] array) {
            ByteBuffer buffer = ByteBuffer.allocateDirect(array.length);
            UNSAFE.copyMemory(array, Unsafe.ARRAY_BYTE_BASE_OFFSET, null, getBufferAddress(buffer), array.length);
            return new PointerObject(buffer);
        }

        public static PointerObject create(int size) {
            return new PointerObject(ByteBuffer.allocateDirect(size));
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        public boolean isPointer() {
            return true;
        }

        @ExportMessage
        public long asPointer() {
            return getBufferAddress(buffer);
        }

        public void writeByte(int offset, byte value) {
            UNSAFE.putByte(getBufferAddress(buffer) + offset, value);
        }

        private static long getBufferAddress(ByteBuffer buffer) {
            return UNSAFE.getLong(buffer, byteBufferAddressOffset);
        }

        public boolean contentEquals(byte[] array) {
            long address = getBufferAddress(buffer);
            for (int i = 0; i < array.length; i++) {
                if (UNSAFE.getByte(address + i) != array[i]) {
                    return false;
                }
            }
            return true;
        }
    }

    public interface TestStrings {
        void run(AbstractTruffleString a, byte[] array, TruffleString.CodeRange codeRange, boolean isValid, TruffleString.Encoding encoding, int[] codepoints, int[] byteIndices) throws Exception;
    }

    public interface TestIndexOfString {
        void run(AbstractTruffleString b, int expectedIndex);
    }

    public interface TestS {
        void run(AbstractTruffleString a) throws Exception;
    }

    public interface TestI {
        void run(int i) throws Exception;
    }

    public interface TestRegion {
        void run(int fromIndex, int length) throws Exception;
    }

    public interface TestSE {
        void run(AbstractTruffleString a, TruffleString.Encoding encoding);
    }

    public interface TestSS {
        void run(AbstractTruffleString a, AbstractTruffleString b);
    }

    public interface TestSEE {
        void run(AbstractTruffleString a, TruffleString.Encoding expectedEncoding, TruffleString.Encoding targetEncoding);
    }

    public interface TestSIE {
        void run(AbstractTruffleString a, int i, TruffleString.Encoding encoding);
    }

    public interface TestSIIE {
        void run(AbstractTruffleString a, int i, int j, TruffleString.Encoding encoding);
    }

    public interface TestSSE {
        void run(AbstractTruffleString a, AbstractTruffleString b, TruffleString.Encoding encoding);
    }

    public interface TestEncoding {
        void run(TruffleString.Encoding encoding) throws Exception;
    }

    public interface TestEncodingCodePoint {
        void run(TruffleString.Encoding encoding, int codepoint) throws Exception;
    }

    public interface TestEncodingCodePointList {
        void run(TruffleString.Encoding encoding, int[] codepoint) throws Exception;
    }

    public interface TestNoParam {
        void run() throws Exception;
    }

    public static void forAllEncodings(TestEncoding test) throws Exception {
        for (TruffleString.Encoding e : values()) {
            test.run(e);
        }
    }

    public static void checkNullS(TestS test) throws Exception {
        expectNullPointerException(() -> test.run(null));
    }

    public static void checkNullSE(TestSE test) throws Exception {
        expectNullPointerException(() -> test.run(null, UTF_8));
        expectNullPointerException(() -> test.run(S_UTF8, null));
    }

    public static void checkNullSEE(TestSEE test) throws Exception {
        expectNullPointerException(() -> test.run(null, UTF_8, UTF_8));
        expectNullPointerException(() -> test.run(S_UTF8, null, UTF_8));
        expectNullPointerException(() -> test.run(S_UTF8, UTF_8, null));
    }

    public static void checkNullSS(TestSS test) throws Exception {
        expectNullPointerException(() -> test.run(null, S_UTF8));
        expectNullPointerException(() -> test.run(S_UTF8, null));
    }

    public static void checkNullSSE(TestSSE test) throws Exception {
        expectNullPointerException(() -> test.run(null, S_UTF8, UTF_8));
        expectNullPointerException(() -> test.run(S_UTF8, null, UTF_8));
        expectNullPointerException(() -> test.run(S_UTF8, S_UTF8, null));
    }

    public static void checkOutOfBoundsFromTo(boolean byteIndex, TestSIIE test) throws Exception {
        checkOutOfBoundsFromTo(byteIndex, 0, TruffleString.Encoding.values(), test);
    }

    public static void checkOutOfBoundsFromTo(boolean byteIndex, int stride, TruffleString.Encoding[] encodings, TestSIIE test) throws Exception {
        forAllStrings(encodings, true, (a, array, codeRange, isValid, encoding1, codepoints, byteIndices) -> {
            int len = getLength(array, codepoints, byteIndex, stride);
            forOutOfBoundsIndices(len, true, i -> expectOutOfBoundsException(() -> test.run(a, 0, i, encoding1)));
            forOutOfBoundsIndices(len, false, i -> expectOutOfBoundsException(() -> test.run(a, i, 1, encoding1)));
        });
    }

    public static void checkOutOfBoundsRegion(boolean byteIndex, TestSIIE test) throws Exception {
        checkOutOfBoundsRegion(byteIndex, 0, TruffleString.Encoding.values(), test);
    }

    public static void checkOutOfBoundsRegion(boolean byteIndex, int stride, TruffleString.Encoding[] encodings, TestSIIE test) throws Exception {
        forAllStrings(encodings, true, (a, array, codeRange, isValid, encoding, codepoints, byteIndices) -> {
            int len = getLength(array, codepoints, byteIndex, stride);
            forOutOfBoundsIndices(len, true, i -> expectOutOfBoundsException(() -> test.run(a, 0, i, encoding)));
            forOutOfBoundsIndices(len, false, i -> expectOutOfBoundsException(() -> test.run(a, i, 1, encoding)));
            forOutOfBoundsRegions(len, (fromIndex, length) -> expectOutOfBoundsException(() -> test.run(a, fromIndex, length, encoding)));
        });
    }

    public static void checkOutOfBounds(boolean byteIndex, boolean isLength, TestSIE test) throws Exception {
        checkOutOfBounds(byteIndex, 0, TruffleString.Encoding.values(), isLength, test);
    }

    public static void checkOutOfBounds(boolean byteIndex, int stride, TruffleString.Encoding[] encodings, boolean isLength, TestSIE test) throws Exception {
        forAllStrings(encodings, true, (a, array, codeRange, isValid, encoding, codepoints, byteIndices) -> {
            forOutOfBoundsIndices(getLength(array, codepoints, byteIndex, stride), isLength, i -> expectOutOfBoundsException(() -> test.run(a, i, encoding)));
        });
    }

    private static int getLength(byte[] array, int[] codepoints, boolean byteIndex, int stride) {
        return byteIndex ? array.length >> stride : codepoints.length;
    }

    public static void forOutOfBoundsIndices(int length, boolean isLength, TestI test) throws Exception {
        for (int i : new int[]{Integer.MIN_VALUE, Integer.MIN_VALUE + 1, Integer.MIN_VALUE & ~3, -4, -2, -1, length + (isLength ? 1 : 0), length + 2, length + 4, Integer.MAX_VALUE - 16,
                        Integer.MAX_VALUE & ~3,
                        Integer.MAX_VALUE - 1, Integer.MAX_VALUE}) {
            test.run(i);
        }
    }

    public static void forOutOfBoundsRegions(int length, TestRegion test) throws Exception {
        for (int[] bounds : new int[][]{
                        {Integer.MIN_VALUE, Integer.MIN_VALUE},
                        {Integer.MIN_VALUE, Integer.MIN_VALUE + 1},
                        {Integer.MIN_VALUE + 1, Integer.MIN_VALUE},
                        {Integer.MIN_VALUE + length, Integer.MIN_VALUE},
                        {-1, 1},
                        {1, -1},
                        {-1, 2},
                        {2, -1}
        }) {
            test.run(bounds[0], bounds[1]);
        }
    }

    public static void forAllStrings(boolean concat, TestStrings test) throws Exception {
        forAllStrings(TruffleString.Encoding.values(), concat, test);
    }

    public static void forAllStrings(TruffleString.Encoding[] encodings, boolean concat, TestStrings test) throws Exception {
        boolean[] isValid = {true, true, true, true, false};
        int[] indices0 = {0};
        int[] indices01 = {0, 1};
        int[] indices02 = {0, 2};
        int[] indices04 = {0, 4};
        byte[] lazyLongBytes = {'1', '0'};
        byte[] lazyLongBytesUTF16 = {'1', 0, '0', 0};
        byte[] lazyLongBytesUTF32 = {'1', 0, 0, 0, '0', 0, 0, 0};
        int[] lazyLongCodePoints = {'1', '0'};
        int[] asciiCodePoints = {0x00, 0x7f};
        int[] latinCodePoints = {0x00, 0xff};
        int[] bmpCodePoints = {0x0000, 0xffff};
        for (TruffleString.Encoding encoding : encodings) {
            TruffleString.CodeRange codeRangeValid = EnumSet.of(US_ASCII, Emacs_Mule, Stateless_ISO_2022_JP, Stateless_ISO_2022_JP_KDDI).contains(encoding) ? TruffleString.CodeRange.ASCII
                            : encoding == ISO_8859_1 ? TruffleString.CodeRange.LATIN_1 : TruffleString.CodeRange.VALID;
            TruffleString.CodeRange[] codeRanges = {TruffleString.CodeRange.ASCII, TruffleString.CodeRange.LATIN_1, TruffleString.CodeRange.BMP, codeRangeValid, TruffleString.CodeRange.BROKEN};
            Encodings.TestData dat = Encodings.TEST_DATA[encoding.ordinal()];
            int[] byteIndices01 = encoding == UTF_32 ? indices04 : encoding == UTF_16 ? indices02 : indices01;
            byte[][] bytes = new byte[][]{dat.encodedAscii, dat.encodedLatin, dat.encodedBMP, dat.encodedValid, dat.encodedBroken};
            int[][] codepoints = new int[][]{asciiCodePoints, latinCodePoints, bmpCodePoints, dat.codepoints, dat.codepointsBroken};
            int[][] byteIndices = new int[][]{byteIndices01, byteIndices01, byteIndices01, dat.byteIndices, indices0};
            for (int i = 0; i < bytes.length; i++) {
                if (bytes[i] == null) {
                    continue;
                }
                checkStringVariants(bytes[i], codeRanges[i], isValid[i], encoding, codepoints[i], byteIndices[i], test);
            }
            if (concat) {
                byte[] concatBytes = Arrays.copyOf(dat.encodedValid, dat.encodedValid.length * 2);
                System.arraycopy(dat.encodedValid, 0, concatBytes, dat.encodedValid.length, dat.encodedValid.length);
                int[] concatCodepoints = Arrays.copyOf(dat.codepoints, dat.codepoints.length * 2);
                System.arraycopy(dat.codepoints, 0, concatCodepoints, dat.codepoints.length, dat.codepoints.length);
                int[] concatByteIndices = Arrays.copyOf(dat.byteIndices, dat.byteIndices.length * 2);
                for (int i = 0; i < dat.byteIndices.length; i++) {
                    concatByteIndices[dat.byteIndices.length + i] = dat.encodedValid.length + dat.byteIndices[i];
                }
                byte[] encodedValidPadded = pad(dat.encodedValid);
                TruffleString substring = TruffleString.fromByteArrayUncached(encodedValidPadded, 1, dat.encodedValid.length, encoding, false);
                TruffleString nativeSubstring = TruffleString.fromNativePointerUncached(PointerObject.create(encodedValidPadded), 1, dat.encodedValid.length, encoding, false);
                test.run(substring.concatUncached(nativeSubstring, encoding, true), concatBytes, codeRangeValid, true, encoding, concatCodepoints, concatByteIndices);
                if (isAsciiCompatible(encoding)) {
                    byte[] array = encoding == UTF_32 ? lazyLongBytesUTF32 : encoding == UTF_16 ? lazyLongBytesUTF16 : lazyLongBytes;
                    test.run(TruffleString.fromLongUncached(10, encoding, true), array, TruffleString.CodeRange.ASCII, true, encoding, lazyLongCodePoints, byteIndices01);
                }
            }
        }
    }

    protected static void checkStringVariants(byte[] array, TruffleString.CodeRange codeRange, boolean isValid, TruffleString.Encoding encoding, int[] codepoints, int[] byteIndices, TestStrings test)
                    throws Exception {
        byte[] arrayPadded = pad(array);
        for (AbstractTruffleString string : new AbstractTruffleString[]{
                        TruffleString.fromByteArrayUncached(array, 0, array.length, encoding, false),
                        TruffleString.fromNativePointerUncached(PointerObject.create(array), 0, array.length, encoding, false),
                        TruffleString.fromNativePointerUncached(PointerObject.create(array), 0, array.length, encoding, true),
                        MutableTruffleString.fromByteArrayUncached(array, 0, array.length, encoding, true),
                        MutableTruffleString.fromNativePointerUncached(PointerObject.create(array), 0, array.length, encoding, false),
                        MutableTruffleString.fromNativePointerUncached(PointerObject.create(array), 0, array.length, encoding, true),
                        TruffleString.fromByteArrayUncached(arrayPadded, 1, array.length, encoding, false),
                        TruffleString.fromNativePointerUncached(PointerObject.create(arrayPadded), 1, array.length, encoding, false),
                        MutableTruffleString.fromByteArrayUncached(arrayPadded, 1, array.length, encoding, true),
                        MutableTruffleString.fromNativePointerUncached(PointerObject.create(arrayPadded), 1, array.length, encoding, false),
                        MutableTruffleString.fromNativePointerUncached(PointerObject.create(arrayPadded), 1, array.length, encoding, true),
        }) {
            test.run(string, array, codeRange, isValid, encoding, codepoints, byteIndices);
        }
    }

    protected static void checkAsciiString(String string, TestStrings test) throws Exception {
        byte[] array = string.getBytes(StandardCharsets.ISO_8859_1);
        int[] codepoints = TStringTestUtil.toIntArray(array);
        int[] byteIndices = TStringTestUtil.intRange(0, array.length);
        byte[] bytesUTF16 = string.getBytes(ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN ? StandardCharsets.UTF_16LE : StandardCharsets.UTF_16BE);
        byte[] bytesUTF32 = new byte[array.length * 4];
        for (int i = 0; i < array.length; i++) {
            TStringTestUtil.writeValue(bytesUTF32, 2, i, array[i]);
        }
        checkStringVariants(bytesUTF16, TruffleString.CodeRange.ASCII, true, UTF_16, codepoints, TStringTestUtil.intRange(0, array.length, 2), test);
        checkStringVariants(bytesUTF32, TruffleString.CodeRange.ASCII, true, UTF_32, codepoints, TStringTestUtil.intRange(0, array.length, 4), test);
        forAllEncodings(encoding -> {
            if (encoding != UTF_16 && encoding != UTF_32 && isAsciiCompatible(encoding)) {
                checkStringVariants(array, TruffleString.CodeRange.ASCII, true, encoding, codepoints, byteIndices, test);
            }
        });
    }

    private static byte[] pad(byte[] array) {
        byte[] ret = new byte[array.length + 2];
        ret[0] = ~0;
        System.arraycopy(array, 0, ret, 1, array.length);
        ret[ret.length - 1] = ~0;
        return ret;
    }

    protected static void testIndexOfString(AbstractTruffleString a, byte[] array, boolean isValid, TruffleString.Encoding encoding, int[] codepoints, int[] byteIndices, boolean byteIndex,
                    boolean lastIndex, TestIndexOfString test) {
        if (!isValid) {
            // ignore broken strings
            return;
        }
        int lastCodepoint = codepoints[codepoints.length - 1];
        TruffleString first = TruffleString.fromCodePointUncached(codepoints[0], encoding);
        TruffleString firstSubstring = a.substringByteIndexUncached(0, codepoints.length == 1 ? array.length : byteIndices[1], encoding, true);
        TruffleString last = TruffleString.fromCodePointUncached(lastCodepoint, encoding);
        TruffleString lastSubstring = a.substringByteIndexUncached(byteIndices[codepoints.length - 1], array.length - byteIndices[codepoints.length - 1], encoding, true);
        int expectedFirst = lastIndex ? lastIndexOfCodePoint(codepoints, byteIndices, byteIndex, codepoints[0]) : 0;
        int expectedLast = lastIndex ? byteIndex ? byteIndices[codepoints.length - 1] : codepoints.length - 1 : indexOfCodePoint(codepoints, byteIndices, byteIndex, lastCodepoint);
        test.run(first, expectedFirst);
        test.run(firstSubstring, expectedFirst);
        test.run(last, expectedLast);
        test.run(lastSubstring, expectedLast);
    }

    private static int indexOfCodePoint(int[] codepoints, int[] byteIndices, boolean byteIndex, int cp) {
        for (int i = 0; i < codepoints.length; i++) {
            if (codepoints[i] == cp) {
                return byteIndex ? byteIndices[i] : i;
            }
        }
        return -1;
    }

    private static int lastIndexOfCodePoint(int[] codepoints, int[] byteIndices, boolean byteIndex, int cp) {
        for (int i = codepoints.length - 1; i >= 0; i--) {
            if (codepoints[i] == cp) {
                return byteIndex ? byteIndices[i] : i;
            }
        }
        return -1;
    }

    public static void forAllEncodingsAndCodePoints(TestEncodingCodePoint test) throws Exception {
        for (TruffleString.Encoding e : values()) {
            int[] cpRanges = Encodings.codepoints(e);
            for (int i = 0; i < cpRanges.length; i += 2) {
                int lo = cpRanges[i];
                int hi = cpRanges[i + 1];
                assert hi >= lo;
                test.run(e, lo);
                if (hi > lo) {
                    test.run(e, lo + 1);
                    test.run(e, hi - 1);
                    test.run(e, hi);
                }
            }
        }
    }

    public static void forAllEncodingsAndCodePointLists(TestEncodingCodePointList test) throws Exception {
        for (TruffleString.Encoding e : values()) {
            test.run(e, Encodings.codepoints(e));
        }
    }

    public static void forAllEncodingsAndInvalidCodePoints(TestEncodingCodePoint test) throws Exception {
        for (TruffleString.Encoding e : values()) {
            int[] cpRanges = Encodings.codepoints(e);
            int prevHi = -1;
            for (int i = 0; i < cpRanges.length; i += 2) {
                int lo = cpRanges[i];
                int hi = cpRanges[i + 1];
                assert hi >= lo;
                assert prevHi < lo;
                if (lo > 0 && lo - prevHi > 1) {
                    test.run(e, lo - 1);
                    test.run(e, prevHi + 1);
                }
                prevHi = hi;
            }
            test.run(e, prevHi + 1);
        }
    }

    protected static void checkStringBuilderResult(byte[] array, TruffleString.CodeRange codeRange, boolean isValid, TruffleString.Encoding encoding, int[] codepoints, TruffleStringBuilder sb) {
        TruffleString string = sb.toStringUncached();
        assertBytesEqual(string, encoding, array);
        assertCodePointsEqual(string, encoding, codepoints);
        Assert.assertEquals(codeRange, string.getCodeRangeUncached(encoding));
        Assert.assertEquals(isValid, string.isValidUncached(encoding));
    }

    protected static void expectIllegalArgumentException(TestNoParam test) throws Exception {
        try {
            test.run();
        } catch (IllegalArgumentException e) {
            return;
        }
        Assert.fail("expected IllegalArgumentException was not thrown");
    }

    protected static void expectOutOfBoundsException(TestNoParam test) throws Exception {
        try {
            test.run();
        } catch (IndexOutOfBoundsException | IllegalArgumentException | UnsupportedSpecializationException e) {
            return;
        }
        Assert.fail("expected IllegalArgumentException was not thrown");
    }

    protected static void expectNullPointerException(TestNoParam test) throws Exception {
        try {
            test.run();
        } catch (NullPointerException | UnsupportedSpecializationException e) {
            return;
        }
        Assert.fail("expected NullPointerException was not thrown");
    }

    protected static void expectUnsupportedOperationException(TestNoParam test) throws Exception {
        try {
            test.run();
        } catch (UnsupportedOperationException e) {
            return;
        }
        Assert.fail("expected UnsupportedOperationException was not thrown");
    }

    protected static void assertCodePointsEqual(AbstractTruffleString a, TruffleString.Encoding encoding, int[] codepoints) {
        assertCodePointsEqual(a, encoding, codepoints, 0, codepoints.length);
    }

    protected static void assertCodePointsEqual(AbstractTruffleString a, TruffleString.Encoding encoding, int[] codepoints, int fromIndex, int length) {
        TruffleStringIterator it = a.createCodePointIteratorUncached(encoding);
        for (int i = 0; i < length; i++) {
            Assert.assertEquals(codepoints[fromIndex + i], it.nextUncached());
        }
    }

    protected static void assertBytesEqual(AbstractTruffleString a, TruffleString.Encoding encoding, byte[] array) {
        assertBytesEqual(a, encoding, array, 0, array.length);
    }

    protected static void assertBytesEqual(AbstractTruffleString a, TruffleString.Encoding encoding, byte[] array, int fromIndex, int length) {
        byte[] cmp = new byte[length];
        a.copyToByteArrayNodeUncached(0, cmp, 0, length, encoding);
        if (array.length == length) {
            Assert.assertArrayEquals(array, cmp);
        } else {
            for (int i = 0; i < length; i++) {
                Assert.assertEquals(array[fromIndex + i], cmp[i]);
            }
        }
    }

    protected static boolean isAsciiCompatible(TruffleString.Encoding encoding) {
        return isUTF(encoding) || Encodings.getJCoding(encoding).isAsciiCompatible();
    }

    static boolean isSupportedEncoding(TruffleString.Encoding encoding) {
        return isUTF(encoding) || encoding == US_ASCII || encoding == ISO_8859_1;
    }

    protected static boolean isUTF(TruffleString.Encoding encoding) {
        return encoding == UTF_8 || isUTF16(encoding) || isUTF32(encoding);
    }

    static boolean isUTF16(TruffleString.Encoding encoding) {
        return encoding == UTF_16;
    }

    static boolean isUTF32(TruffleString.Encoding encoding) {
        return encoding == UTF_32;
    }

    static int byteIndex(int i, TruffleString.Encoding encoding) {
        if (isUTF32(encoding)) {
            return i << 2;
        }
        if (isUTF16(encoding)) {
            return i << 1;
        }
        return i;
    }

    static int getStride(TruffleString.Encoding encoding) {
        if (isUTF32(encoding)) {
            return 2;
        }
        if (isUTF16(encoding)) {
            return 1;
        }
        return 0;
    }

}
