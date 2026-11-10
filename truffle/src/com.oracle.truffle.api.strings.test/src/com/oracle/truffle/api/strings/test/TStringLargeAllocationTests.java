/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.strings.MutableTruffleString;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;
import com.oracle.truffle.api.test.SubprocessTestUtils;

public class TStringLargeAllocationTests extends TStringTestBase {

    private static final String LARGE_HEAP = "-Xmx4g";

    private static void assertOutOfMemory(Runnable action) {
        try {
            action.run();
            Assert.fail("expected OutOfMemoryError");
        } catch (OutOfMemoryError expected) {
            // expected
        }
    }

    private static void runWithLargeHeap(Runnable action, String... additionalVmOptions) throws IOException, InterruptedException {
        SubprocessTestUtils.Builder builder = SubprocessTestUtils.newBuilder(TStringLargeAllocationTests.class, action).prefixVmOption(SubprocessTestUtils.markForRemoval("-Xmx")).postfixVmOption(
                        LARGE_HEAP);
        builder.postfixVmOption(additionalVmOptions).run();
    }

    @Test
    public void testFromNativePointerTooLarge() {
        int oversizedByteLength = Integer.MAX_VALUE - 7;
        for (TruffleString.Encoding encoding : new TruffleString.Encoding[]{TruffleString.Encoding.UTF_8, TruffleString.Encoding.UTF_16, TruffleString.Encoding.UTF_32}) {
            for (boolean copy : new boolean[]{true, false}) {
                assertOutOfMemory(() -> TruffleString.fromNativePointerUncached(0L, 0, oversizedByteLength, encoding, copy));
                assertOutOfMemory(() -> MutableTruffleString.fromNativePointerUncached(0L, 0, oversizedByteLength, encoding, copy));
            }
        }
    }

    @Test
    public void testSwitchEncodingForeignEndianUTF16TooLarge() throws IOException, InterruptedException {
        runWithLargeHeap(() -> {
            TruffleString.Encoding foreign = TruffleString.Encoding.UTF_16 == TruffleString.Encoding.UTF_16LE ? TruffleString.Encoding.UTF_16BE : TruffleString.Encoding.UTF_16LE;
            byte[] bytes = new byte[1 << 30];
            TruffleString source = TruffleString.fromByteArrayUncached(bytes, 0, bytes.length, foreign, false);
            assertOutOfMemory(() -> source.switchEncodingUncached(TruffleString.Encoding.UTF_32));
        });
    }

    @Test
    public void testFromByteArrayWithCompactionUTF32LargeOffset() throws IOException, InterruptedException {
        runWithLargeHeap(() -> {
            byte[] bytes = new byte[Integer.MAX_VALUE - 8];
            TruffleString result = TruffleString.fromByteArrayWithCompactionUTF32Uncached(bytes, bytes.length - 4, 4, TruffleString.CompactionLevel.S4, false);
            Assert.assertEquals(4, result.byteLength(TruffleString.Encoding.UTF_32));
        });
    }

    @Test
    public void testFromByteArrayDebugNonZeroOffsetLarge() throws IOException, InterruptedException {
        runWithLargeHeap(() -> {
            byte[] bytes = new byte[1 << 30];
            TruffleString result = TruffleString.fromByteArrayUncached(bytes, TruffleString.Encoding.US_ASCII, false);
            Assert.assertEquals(bytes.length, result.byteLength(TruffleString.Encoding.US_ASCII));
        }, "-Dtruffle.strings.debug-non-zero-offset-arrays=true");
    }

    @Test
    public void testFromCharArrayUTF16LargeArraySmallSlice() throws IOException, InterruptedException {
        runWithLargeHeap(() -> {
            char[] chars = new char[1 << 30];
            chars[0] = 'a';
            chars[1] = 'b';
            TruffleString result = TruffleString.fromCharArrayUTF16Uncached(chars, 0, 2);
            Assert.assertEquals("ab", result.toJavaStringUncached());
        });
    }

    @Test
    public void testUTF16BuilderInitialCapacityLimit() throws IOException, InterruptedException {
        runWithLargeHeap(() -> {
            int maxUTF16Length = (Integer.MAX_VALUE - 8) >> 1;
            TruffleStringBuilder builder = TruffleStringBuilder.createUTF16(maxUTF16Length);
            Assert.assertEquals(0, builder.byteLength());
            assertOutOfMemory(() -> TruffleStringBuilder.createUTF16(maxUTF16Length + 1));
        });
    }

    @Test
    public void testToJavaStringWithoutCompactStringsTooLarge() throws IOException, InterruptedException {
        runWithLargeHeap(() -> {
            TruffleString string = TruffleString.fromCodePointUncached('a', TruffleString.Encoding.US_ASCII);
            for (int i = 0; i < 30; i++) {
                string = string.concatUncached(string, TruffleString.Encoding.US_ASCII, true);
            }
            TruffleString result = string;
            assertOutOfMemory(result::toJavaStringUncached);
        }, "-XX:-CompactStrings");
    }

    @Test
    public void testAppendCodePointTooLargeUTF32() {
        assertOutOfMemory(() -> TruffleStringBuilder.createUTF32().appendCodePointUncached(0x10000, 1 << 30, false));
    }

    @Test
    public void testAppendCodePointTooLargeForeignEndianUTF16() {
        TruffleString.Encoding encoding = ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN ? TruffleString.Encoding.UTF_16LE : TruffleString.Encoding.UTF_16BE;
        assertOutOfMemory(() -> TruffleStringBuilder.create(encoding).appendCodePointUncached('a', 1 << 30, false));
    }

    @Test
    public void testAppendCodePointTooLargeForeignEndianUTF32() {
        TruffleString.Encoding encoding = ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN ? TruffleString.Encoding.UTF_32LE : TruffleString.Encoding.UTF_32BE;
        assertOutOfMemory(() -> TruffleStringBuilder.create(encoding).appendCodePointUncached('a', 1 << 30, false));
    }

    @Test
    public void testAppendSubstringLargeSourceSmallUTF32Slice() throws IOException, InterruptedException {
        runWithLargeHeap(() -> {
            int length = (Integer.MAX_VALUE - 8) >> 2;
            byte[] data = new byte[length * 2];
            data[0] = 1;
            data[1] = 1;
            TruffleString source = TruffleString.fromByteArrayWithCompactionUTF32Uncached(data, 0, data.length, TruffleString.CompactionLevel.S2, false);

            TruffleStringBuilder builder = TruffleStringBuilder.createUTF32();
            builder.appendCodePointUncached('a');
            builder.appendSubstringByteIndexUncached(source, 0, 4);

            Assert.assertEquals("a\u0101", builder.toStringUncached().toJavaStringUncached());
        });
    }

    @Test
    public void testAhoCorasickLargeOffset() throws IOException, InterruptedException {
        runWithLargeHeap(() -> {
            String pattern = "a".repeat(48) + "b" + "a".repeat(15);
            TruffleString[] patterns = new TruffleString[17];
            patterns[0] = TruffleString.fromJavaStringUncached(pattern, TruffleString.Encoding.US_ASCII);
            for (int i = 1; i < patterns.length; i++) {
                patterns[i] = TruffleString.fromCodePointUncached('a' + i, TruffleString.Encoding.US_ASCII);
            }
            TruffleString.StringSet stringSet = TruffleString.StringSet.fromArray(patterns, TruffleString.Encoding.US_ASCII);

            byte[] bytes = new byte[Integer.MAX_VALUE - 8];
            int start = bytes.length - pattern.length();
            System.arraycopy(pattern.getBytes(StandardCharsets.US_ASCII), 0, bytes, start, pattern.length());
            TruffleString haystack = TruffleString.fromByteArrayUncached(bytes, TruffleString.Encoding.US_ASCII, false);
            long result = TruffleString.ByteIndexOfStringSetNode.getUncached().execute(haystack, start, bytes.length, stringSet);

            Assert.assertTrue(TruffleString.ByteIndexOfStringSetNode.resultIsMatch(result));
            Assert.assertEquals(start, TruffleString.ByteIndexOfStringSetNode.unpackResultByteIndex(result));
            Assert.assertEquals(0, TruffleString.ByteIndexOfStringSetNode.unpackResultPatternIndex(result));
        });
    }

    @Test
    public void testConcatTooLargeEmpty() {
        TruffleString string = TruffleString.fromJavaStringUncached("a", TruffleString.Encoding.US_ASCII);
        for (int i = 0; i < 30; i++) {
            string = string.concatUncached(string, TruffleString.Encoding.US_ASCII, true);
        }
        TruffleString result = string;
        assertOutOfMemory(() -> result.concatUncached(TruffleString.Encoding.UTF_16.getEmpty(), TruffleString.Encoding.UTF_16, true));
    }

    @Test
    public void testRepeatTooLargeUTF16() {
        assertOutOfMemory(() -> S_UTF16.repeatUncached(Integer.MAX_VALUE >>> 1, TruffleString.Encoding.UTF_16));
    }

    @Test
    public void testRepeatTooLargeUTF32() {
        assertOutOfMemory(() -> S_UTF32.repeatUncached(Integer.MAX_VALUE >>> 2, TruffleString.Encoding.UTF_32));
    }
}
