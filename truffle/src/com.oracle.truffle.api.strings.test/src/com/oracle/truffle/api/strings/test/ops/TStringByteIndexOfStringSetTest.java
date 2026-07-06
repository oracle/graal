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

package com.oracle.truffle.api.strings.test.ops;

import static org.junit.runners.Parameterized.Parameter;

import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.strings.AbstractTruffleString;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringIterator;
import com.oracle.truffle.api.strings.test.TStringTestBase;
import com.oracle.truffle.api.strings.test.TStringTestUtil;

@RunWith(Parameterized.class)
public class TStringByteIndexOfStringSetTest extends TStringTestBase {

    private static final TruffleString.Encoding[] TEST_ENCODINGS = {
                    TruffleString.Encoding.US_ASCII,
                    TruffleString.Encoding.ISO_8859_1,
                    TruffleString.Encoding.UTF_8,
                    TruffleString.Encoding.UTF_16LE,
                    TruffleString.Encoding.UTF_16BE,
                    TruffleString.Encoding.UTF_32LE,
                    TruffleString.Encoding.UTF_32BE,
    };

    private static final StringSetCase[] SEARCH_CASES = createSearchCases();
    private static final StringSetCase[] ASCII_IGNORE_CASE_SEARCH_CASES = createAsciiIgnoreCaseSearchCases();
    public static final int TEDDY_CUTOFF = 16;

    @Parameter public TruffleString.ByteIndexOfStringSetNode node;

    @Parameters(name = "{0}")
    public static Iterable<TruffleString.ByteIndexOfStringSetNode> data() {
        return Arrays.asList(TruffleString.ByteIndexOfStringSetNode.create(), TruffleString.ByteIndexOfStringSetNode.getUncached());
    }

    @Test
    public void testSearchScenarios() {
        runSearchScenarios(SEARCH_CASES, false);
    }

    @Test
    public void testAsciiIgnoreCaseSearchScenarios() {
        runSearchScenarios(ASCII_IGNORE_CASE_SEARCH_CASES, true);
    }

    private void runSearchScenarios(StringSetCase[] searchCases, boolean useMasks) {
        for (TruffleString.Encoding encoding : TEST_ENCODINGS) {
            for (StringSetCase stringSetCase : searchCases) {
                String[] patternJavaStrings = useMasks ? lowercaseAsciiLetters(stringSetCase.patterns) : stringSetCase.patterns;
                AbstractTruffleString[] patterns = patternStrings(encoding, patternJavaStrings);
                TruffleString.StringSet stringSet = createStringSet(patterns, encoding, useMasks);
                for (String haystack : stringSetCase.haystacks) {
                    SearchInput searchInput = createSearchInput(haystack, encoding, patterns, patternJavaStrings, useMasks);
                    for (SearchWindow window : searchInput.windows) {
                        String context = (useMasks ? "masked" : "exact") + " / " + encoding + " / " + stringSetCase.name + " / " + haystack + " / " + window.name;
                        int rawWindowLength = rawLength(window.toByteIndex - window.fromByteIndex, encoding);
                        if (window.shortWindow) {
                            Assert.assertTrue(context, rawWindowLength < 16);
                        } else {
                            Assert.assertTrue(context, rawWindowLength >= 16);
                        }
                        SearchExpectation expected = naiveReference(searchInput.haystack, patterns, encoding, window.fromByteIndex, window.toByteIndex, useMasks);
                        // re-create the cached node for every string set, otherwise the node will
                        // always fall back to the uncached specialization
                        TruffleString.ByteIndexOfStringSetNode indexOfNode = node == TruffleString.ByteIndexOfStringSetNode.getUncached() ? node : TruffleString.ByteIndexOfStringSetNode.create();
                        long actual = indexOfNode.execute(searchInput.haystack, window.fromByteIndex, window.toByteIndex, stringSet);
                        checkEqual(context, expected, actual);
                    }
                }
            }
        }
    }

    @Test
    public void testNull() throws Exception {
        AbstractTruffleString[] patterns = patternStrings(TruffleString.Encoding.UTF_8, "a", "b");
        TruffleString.StringSet stringSet = TruffleString.StringSet.fromArray(patterns, TruffleString.Encoding.UTF_8);
        expectNullPointerException(() -> node.execute(null, 0, 0, stringSet));
        expectNullPointerException(() -> node.execute(S_UTF8, 0, 1, null));
        expectNullPointerException(() -> TruffleString.StringSet.fromArray((AbstractTruffleString[]) null, TruffleString.Encoding.UTF_8));
        expectNullPointerException(() -> TruffleString.StringSet.fromArray(new AbstractTruffleString[]{S_UTF8, null}, TruffleString.Encoding.UTF_8));
        expectNullPointerException(() -> TruffleString.StringSet.fromArray((TruffleString.WithMask[]) null, TruffleString.Encoding.UTF_8));
        expectNullPointerException(() -> TruffleString.StringSet.fromArray(new TruffleString.WithMask[]{
                        TruffleString.WithMask.createUncached(TruffleString.fromJavaStringUncached("a", TruffleString.Encoding.UTF_8), new byte[]{0}, TruffleString.Encoding.UTF_8), null},
                        TruffleString.Encoding.UTF_8));
    }

    @Test
    public void testInvalidArguments() throws Exception {
        expectIllegalArgumentException(() -> TruffleString.StringSet.fromArray(
                        new AbstractTruffleString[]{TruffleString.fromJavaStringUncached("", TruffleString.Encoding.UTF_8)}, TruffleString.Encoding.UTF_8));
        expectIllegalArgumentException(() -> TruffleString.StringSet.fromArray(
                        new AbstractTruffleString[]{TruffleString.fromJavaStringUncached("\u00E4", TruffleString.Encoding.UTF_8)}, TruffleString.Encoding.US_ASCII));
        expectUnsupportedOperationException(() -> TruffleString.StringSet.fromArray(
                        new AbstractTruffleString[]{TruffleString.fromJavaStringUncached("a", TruffleString.Encoding.UTF_7)}, TruffleString.Encoding.UTF_7));
    }

    @Test
    public void testOutOfBounds() throws Exception {
        AbstractTruffleString[] patterns = patternStrings(TruffleString.Encoding.UTF_16LE, "a", "b");
        TruffleString.StringSet stringSet = TruffleString.StringSet.fromArray(patterns, TruffleString.Encoding.UTF_16LE);
        TruffleString haystack = TruffleString.fromJavaStringUncached("abc", TruffleString.Encoding.UTF_16LE);
        expectOutOfBoundsException(() -> node.execute(haystack, 0, haystack.byteLength(TruffleString.Encoding.UTF_16LE) + 2, stringSet));
        expectOutOfBoundsException(() -> node.execute(haystack, -2, 0, stringSet));
        expectIllegalArgumentException(() -> node.execute(haystack, 1, 2, stringSet));
    }

    @Test
    public void testGenericMasks() throws Exception {
        TruffleString.Encoding encoding = TruffleString.Encoding.UTF_8;
        AbstractTruffleString ax = TruffleString.fromJavaStringUncached("ax", encoding);
        AbstractTruffleString ayUpper = TruffleString.fromJavaStringUncached("Ay", encoding);
        TruffleString.StringSet stringSet = TruffleString.StringSet.fromArray(new TruffleString.WithMask[]{
                        TruffleString.WithMask.createUncached(ax, new byte[]{0x20, 0}, encoding),
                        TruffleString.WithMask.createUncached(ayUpper, new byte[]{0, 0}, encoding),
        }, encoding);

        assertNoMatch("masked-overlap-no-false-positive", node.execute(TruffleString.fromJavaStringUncached("zzzzzzzzzzzzzzzzay", encoding), 0, 18, stringSet));
        assertMatch("masked-overlap-masked", 16, 0, node.execute(TruffleString.fromJavaStringUncached("zzzzzzzzzzzzzzzzAx", encoding), 0, 18, stringSet));
        assertMatch("masked-overlap-exact", 16, 1, node.execute(TruffleString.fromJavaStringUncached("zzzzzzzzzzzzzzzzAy", encoding), 0, 18, stringSet));
    }

    @Test
    public void testInvalidMasks() throws Exception {
        TruffleString.Encoding encoding = TruffleString.Encoding.UTF_8;
        expectIllegalArgumentException(() -> TruffleString.StringSet.fromArray(new TruffleString.WithMask[]{
                        TruffleString.WithMask.createUncached(TruffleString.fromJavaStringUncached("g", encoding), new byte[]{0x03}, encoding),
        }, encoding));
        expectIllegalArgumentException(() -> TruffleString.StringSet.fromArray(new TruffleString.WithMask[]{
                        TruffleString.WithMask.createUncached(TruffleString.fromJavaStringUncached("a", encoding), new byte[]{0x02}, encoding),
        }, encoding));
    }

    private static StringSetCase[] createSearchCases() {
        String[] manySingleBytePatterns = new String[17];
        for (int i = 0; i < manySingleBytePatterns.length; i++) {
            manySingleBytePatterns[i] = String.valueOf((char) ('a' + i));
        }
        String[] asciiWithLatin1Patterns = {
                        "a", "b", "c", "d", "e", "f", "g", "h",
                        "i", "j", "k", "l", "m", "n", "o", "q",
                        "\u00E4"
        };
        String[] largePatterns = new String[32];
        for (int i = 0; i < 16; i++) {
            largePatterns[i] = "foobarb" + Character.forDigit(i, 16);
            largePatterns[16 + i] = "foobazb" + Character.forDigit(i, 16);
        }
        String largeHaystack = createLargePatternArrayHaystack(largePatterns, 23);
        return new StringSetCase[]{
                        new StringSetCase("empty-set", new String[0], new String[]{"abcdef"}),
                        new StringSetCase("foo-bar", new String[]{"foo", "bar"}, new String[]{"zzbarzz"}),
                        new StringSetCase("fingerprint-1", new String[]{"abcd", "a", "abc"}, new String[]{"zabc"}),
                        new StringSetCase("fingerprint-2", new String[]{"abx", "ab", "aby"}, new String[]{"zabya"}),
                        new StringSetCase("fingerprint-3", new String[]{"abcz", "abc", "abcy"}, new String[]{"zabcyd"}),
                        new StringSetCase("fingerprint-4", new String[]{"wxyzq", "wxyz", "wxyzt"}, new String[]{"zwxyzt"}),
                        new StringSetCase("many-single-byte-patterns", manySingleBytePatterns, new String[]{"zzq"}),
                        new StringSetCase("ascii-filtered-by-code-range", asciiWithLatin1Patterns, new String[]{"zzqzz"}),
                        new StringSetCase("single-pattern", new String[]{"abc"}, new String[]{"zzabczz"}),
                        new StringSetCase("per-bucket", new String[]{"abx", "ab", "\u0100b"}, new String[]{"zzabzz", "z\u0100bzz"}),
                        new StringSetCase("earliest-start", new String[]{"bc", "abc"}, new String[]{"zabcxxbc"}),
                        new StringSetCase("same-start-input-order", new String[]{"abcd", "ab", "abc"}, new String[]{"xabcd"}),
                        new StringSetCase("same-start-prefix-input-order", new String[]{"\r\n", "\r"}, new String[]{"z\r\n"}),
                        new StringSetCase("duplicate-patterns", new String[]{"bc", "bc"}, new String[]{"zzbczz"}),
                        new StringSetCase("bmp", new String[]{"\u1234x", "\u1234"}, new String[]{"\u1234x"}),
                        new StringSetCase("valid-utf8-latin1-patterns", new String[]{"abx", "ab", "abc"}, new String[]{"\u20acab"}),
                        new StringSetCase("wide-stride", new String[]{"abx", "ab", "abc"}, new String[]{"z\u1234abc"}),
                        new StringSetCase("latin1-native", new String[]{"\u00E4a", "ab"}, new String[]{"z\u00E4ab"}),
                        new StringSetCase("no-match", new String[]{"gh", "ijk"}, new String[]{"abcdef"}),
                        new StringSetCase("large-pattern-array", largePatterns, new String[]{largeHaystack})
        };
    }

    private static StringSetCase[] createAsciiIgnoreCaseSearchCases() {
        StringSetCase[] extraCases = {
                        new StringSetCase("ascii-ignore-case-single", new String[]{"abc"}, new String[]{"zzABCzz", "zzAbCzz"}),
                        new StringSetCase("ascii-ignore-case-earliest-start", new String[]{"bc", "ABC"}, new String[]{"zAbCxxBC"}),
                        new StringSetCase("ascii-ignore-case-same-start-input-order", new String[]{"ABCD", "ab", "abc"}, new String[]{"xABCD"}),
                        new StringSetCase("ascii-ignore-case-original-index", new String[]{"AB", "ab"}, new String[]{"zzAbzz"}),
                        new StringSetCase("ascii-ignore-case-non-ascii-exact", new String[]{"\u00E4", "abc"}, new String[]{"zz\u00C4zzAbC"}),
        };
        StringSetCase[] ret = Arrays.copyOf(SEARCH_CASES, SEARCH_CASES.length + extraCases.length);
        System.arraycopy(extraCases, 0, ret, SEARCH_CASES.length, extraCases.length);
        return ret;
    }

    private static String createLargePatternArrayHaystack(String[] patterns, int expectedPatternIndex) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 48; i++) {
            sb.append("xxfoobarb-yyfoobazb_q");
            if ((i & 1) == 0) {
                sb.append("foobarb/foobazb:");
            }
        }
        sb.append(patterns[expectedPatternIndex]);
        sb.append("tailfoobarb-");
        return sb.toString();
    }

    private static SearchInput createSearchInput(String haystackJS, TruffleString.Encoding encoding, AbstractTruffleString[] patterns, String[] patternJavaStrings,
                    boolean useMasks) {
        AbstractTruffleString haystack = TruffleString.fromJavaStringUncached(haystackJS, encoding);
        int hsByteLength = haystack.byteLength(encoding);
        if (rawLength(hsByteLength, encoding) < TEDDY_CUTOFF) {
            char paddingChar = findPaddingChar(encoding, haystackJS, patternJavaStrings, useMasks);
            TruffleString prefix = TruffleString.fromCodePointUncached(paddingChar, encoding).repeatUncached(4, encoding);
            TruffleString suffix = TruffleString.fromCodePointUncached(paddingChar, encoding).repeatUncached(12, encoding);
            haystack = prefix.concatUncached(haystack, encoding, true).concatUncached(suffix, encoding, true);
            int shortFromByteIndex = prefix.byteLength(encoding);
            int shortToByteIndex = shortFromByteIndex + hsByteLength;
            int longToByteIndex = haystack.byteLength(encoding);
            return new SearchInput(haystack, new SearchWindow[]{
                            new SearchWindow("short-core", true, shortFromByteIndex, shortToByteIndex),
                            new SearchWindow("long-full", false, 0, longToByteIndex)
            });
        }
        SearchWindow shortWindow = createShortWindow(haystack, patterns, encoding, useMasks);
        return new SearchInput(haystack, new SearchWindow[]{shortWindow, new SearchWindow("long-full", false, 0, hsByteLength)});
    }

    private static SearchWindow createShortWindow(AbstractTruffleString haystack, AbstractTruffleString[] patterns, TruffleString.Encoding encoding, boolean useMasks) {
        SearchExpectation fullWindow = naiveReference(haystack, patterns, encoding, 0, haystack.byteLength(encoding), useMasks);
        int shortByteLimit = 16 << getNaturalStride(encoding);
        if (fullWindow.byteIndex >= 0) {
            int patternByteLength = patterns[fullWindow.patternIndex].byteLength(encoding);
            if (patternByteLength < shortByteLimit) {
                return new SearchWindow("short-match", true, fullWindow.byteIndex, fullWindow.byteIndex + patternByteLength);
            }
        }
        int shortToByteIndex = largestBoundaryBelow(haystack, encoding, shortByteLimit);
        return new SearchWindow("short-prefix", true, 0, shortToByteIndex);
    }

    private static int largestBoundaryBelow(AbstractTruffleString haystack, TruffleString.Encoding encoding, int byteLimitExclusive) {
        TruffleStringIterator it = haystack.createCodePointIteratorUncached(encoding);
        int prevByteIndex = it.getByteIndex();
        while (it.hasNext() && it.getByteIndex() < byteLimitExclusive) {
            prevByteIndex = it.getByteIndex();
            it.nextUncached(encoding);
        }
        if (it.getByteIndex() < byteLimitExclusive) {
            return it.getByteIndex();
        }
        return prevByteIndex;
    }

    private static SearchExpectation naiveReference(AbstractTruffleString haystack, AbstractTruffleString[] patterns, TruffleString.Encoding encoding,
                    int fromByteIndex, int toByteIndex, boolean useMasks) {
        byte[] haystackBytes = haystack.copyToByteArrayUncached(encoding);
        int fromIndex = rawLength(fromByteIndex, encoding);
        int toIndex = rawLength(toByteIndex, encoding);
        int bestByteIndex = -1;
        int bestPatternIndex = -1;
        for (int i = 0; i < patterns.length; i++) {
            byte[] patternBytes = patterns[i].copyToByteArrayUncached(encoding);
            int matchIndex = indexOfPattern(haystackBytes, patternBytes, encoding, fromIndex, toIndex, useMasks);
            if (matchIndex < 0) {
                continue;
            }
            int matchByteIndex = matchIndex << getNaturalStride(encoding);
            if (bestByteIndex < 0 || matchByteIndex < bestByteIndex || matchByteIndex == bestByteIndex && i < bestPatternIndex) {
                bestByteIndex = matchByteIndex;
                bestPatternIndex = i;
            }
        }
        return new SearchExpectation(bestByteIndex, bestPatternIndex);
    }

    private static int indexOfPattern(byte[] haystack, byte[] pattern, TruffleString.Encoding encoding, int fromIndex, int toIndex, boolean useMasks) {
        int patternLength = rawLength(pattern.length, encoding);
        for (int i = fromIndex; i + patternLength <= toIndex; i++) {
            if (matchesAt(haystack, pattern, encoding, i, patternLength, useMasks)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean matchesAt(byte[] haystack, byte[] pattern, TruffleString.Encoding encoding, int fromIndex, int patternLength, boolean useMasks) {
        for (int i = 0; i < patternLength; i++) {
            int haystackValue = readEncodedValue(haystack, encoding, fromIndex + i);
            int patternValue = readEncodedValue(pattern, encoding, i);
            if (useMasks) {
                haystackValue = lowercaseAsciiLetter(haystackValue);
                patternValue = lowercaseAsciiLetter(patternValue);
            }
            if (haystackValue != patternValue) {
                return false;
            }
        }
        return true;
    }

    private static int readEncodedValue(byte[] array, TruffleString.Encoding encoding, int index) {
        int value = TStringTestUtil.readValue(array, getNaturalStride(encoding), index);
        if (encoding == TruffleString.Encoding.UTF_16BE) {
            return Character.reverseBytes((char) value);
        } else if (encoding == TruffleString.Encoding.UTF_32BE) {
            return Integer.reverseBytes(value);
        } else {
            return value;
        }
    }

    private static int lowercaseAsciiLetter(int value) {
        int lower = value | 0x20;
        return 'a' <= lower && lower <= 'z' ? lower : value;
    }

    private static boolean isAsciiLowercaseLetter(int value) {
        return 'a' <= value && value <= 'z';
    }

    private static TruffleString.StringSet createStringSet(AbstractTruffleString[] patterns, TruffleString.Encoding encoding, boolean useMasks) {
        if (!useMasks) {
            return TruffleString.StringSet.fromArray(patterns, encoding);
        }
        return TruffleString.StringSet.fromArray(asciiIgnoreCaseMasks(patterns, encoding), encoding);
    }

    private static TruffleString.WithMask[] asciiIgnoreCaseMasks(AbstractTruffleString[] patterns, TruffleString.Encoding encoding) {
        TruffleString.WithMask[] ret = new TruffleString.WithMask[patterns.length];
        for (int i = 0; i < patterns.length; i++) {
            int rawLength = rawLength(patterns[i].byteLength(encoding), encoding);
            switch (encoding) {
                case UTF_16LE, UTF_16BE -> {
                    char[] mask = new char[rawLength];
                    byte[] bytes = patterns[i].copyToByteArrayUncached(encoding);
                    for (int j = 0; j < rawLength; j++) {
                        if (isAsciiLowercaseLetter(readEncodedValue(bytes, encoding, j))) {
                            mask[j] = 0x20;
                        }
                    }
                    if (encoding == TruffleString.Encoding.UTF_16) {
                        ret[i] = TruffleString.WithMask.createUTF16Uncached(patterns[i], mask);
                    } else {
                        ret[i] = TruffleString.WithMask.createUncached(patterns[i], toByteMask(mask, encoding), encoding);
                    }
                }
                case UTF_32LE, UTF_32BE -> {
                    int[] mask = new int[rawLength];
                    byte[] bytes = patterns[i].copyToByteArrayUncached(encoding);
                    for (int j = 0; j < rawLength; j++) {
                        if (isAsciiLowercaseLetter(readEncodedValue(bytes, encoding, j))) {
                            mask[j] = 0x20;
                        }
                    }
                    if (encoding == TruffleString.Encoding.UTF_32) {
                        ret[i] = TruffleString.WithMask.createUTF32Uncached(patterns[i], mask);
                    } else {
                        ret[i] = TruffleString.WithMask.createUncached(patterns[i], toByteMask(mask, encoding), encoding);
                    }
                }
                default -> {
                    byte[] mask = new byte[patterns[i].byteLength(encoding)];
                    byte[] bytes = patterns[i].copyToByteArrayUncached(encoding);
                    for (int j = 0; j < mask.length; j++) {
                        if (isAsciiLowercaseLetter(Byte.toUnsignedInt(bytes[j]))) {
                            mask[j] = 0x20;
                        }
                    }
                    ret[i] = TruffleString.WithMask.createUncached(patterns[i], mask, encoding);
                }
            }
        }
        return ret;
    }

    private static byte[] toByteMask(char[] mask, TruffleString.Encoding encoding) {
        byte[] ret = new byte[mask.length << 1];
        for (int i = 0; i < mask.length; i++) {
            if (encoding == TruffleString.Encoding.UTF_16BE) {
                ret[i << 1] = (byte) (mask[i] >> 8);
                ret[(i << 1) + 1] = (byte) mask[i];
            } else {
                ret[i << 1] = (byte) mask[i];
                ret[(i << 1) + 1] = (byte) (mask[i] >> 8);
            }
        }
        return ret;
    }

    private static byte[] toByteMask(int[] mask, TruffleString.Encoding encoding) {
        byte[] ret = new byte[mask.length << 2];
        for (int i = 0; i < mask.length; i++) {
            if (encoding == TruffleString.Encoding.UTF_32BE) {
                ret[i << 2] = (byte) (mask[i] >> 24);
                ret[(i << 2) + 1] = (byte) (mask[i] >> 16);
                ret[(i << 2) + 2] = (byte) (mask[i] >> 8);
                ret[(i << 2) + 3] = (byte) mask[i];
            } else {
                ret[i << 2] = (byte) mask[i];
                ret[(i << 2) + 1] = (byte) (mask[i] >> 8);
                ret[(i << 2) + 2] = (byte) (mask[i] >> 16);
                ret[(i << 2) + 3] = (byte) (mask[i] >> 24);
            }
        }
        return ret;
    }

    private static AbstractTruffleString[] patternStrings(TruffleString.Encoding encoding, String... patterns) {
        AbstractTruffleString[] ret = new AbstractTruffleString[patterns.length];
        for (int i = 0; i < patterns.length; i++) {
            ret[i] = TruffleString.fromJavaStringUncached(patterns[i], encoding);
        }
        return ret;
    }

    private static String[] lowercaseAsciiLetters(String[] patterns) {
        String[] ret = new String[patterns.length];
        for (int i = 0; i < patterns.length; i++) {
            StringBuilder sb = new StringBuilder(patterns[i].length());
            for (int j = 0; j < patterns[i].length(); j++) {
                char c = patterns[i].charAt(j);
                sb.append('A' <= c && c <= 'Z' ? (char) (c | 0x20) : c);
            }
            ret[i] = sb.toString();
        }
        return ret;
    }

    private static int rawLength(int byteLength, TruffleString.Encoding encoding) {
        return byteLength >> getNaturalStride(encoding);
    }

    private static char findPaddingChar(TruffleString.Encoding encoding, String haystackJavaString, String[] patterns, boolean useMasks) {
        char[] preferred = switch (encoding) {
            case US_ASCII, ISO_8859_1, BYTES -> new char[]{'!', '#', '$', '%', '&', '?', '@', '~'};
            default -> new char[]{'!', '#', '$', '%', '&', '\u00A1', '\u00B5', '\u00FF', '\u0100', '\u20AC', '\u1100', '\u4E2D'};
        };
        int max = switch (encoding) {
            case US_ASCII -> 0x7f;
            case ISO_8859_1, BYTES -> 0xff;
            default -> Character.MAX_VALUE;
        };
        for (char candidate : preferred) {
            if (candidate <= max && isPaddingChar(candidate, haystackJavaString, patterns, useMasks)) {
                return candidate;
            }
        }
        for (char candidate = 1; candidate < max; candidate++) {
            if (!Character.isSurrogate(candidate) && isPaddingChar(candidate, haystackJavaString, patterns, useMasks)) {
                return candidate;
            }
        }
        throw new AssertionError("no padding char available");
    }

    private static boolean isPaddingChar(char candidate, String haystackJavaString, String[] patterns, boolean useMasks) {
        if (useMasks && ('A' <= candidate && candidate <= 'Z' || 'a' <= candidate && candidate <= 'z')) {
            return false;
        }
        if (haystackJavaString.indexOf(candidate) >= 0) {
            return false;
        }
        for (String pattern : patterns) {
            if (pattern.indexOf(candidate) >= 0) {
                return false;
            }
        }
        return true;
    }

    private static void checkEqual(String context, SearchExpectation expected, long actual) {
        if (expected.byteIndex < 0) {
            assertNoMatch(context, actual);
        } else {
            assertMatch(context, expected.byteIndex, expected.patternIndex, actual);
        }
    }

    private static void assertNoMatch(String context, long result) {
        Assert.assertFalse(context, TruffleString.ByteIndexOfStringSetNode.resultIsMatch(result));
        Assert.assertEquals(context, -1, TruffleString.ByteIndexOfStringSetNode.unpackResultByteIndex(result));
        Assert.assertEquals(context, -1, TruffleString.ByteIndexOfStringSetNode.unpackResultPatternIndex(result));
    }

    private static void assertMatch(String context, int expectedByteIndex, int expectedPatternIndex, long result) {
        Assert.assertTrue(context, TruffleString.ByteIndexOfStringSetNode.resultIsMatch(result));
        Assert.assertEquals(context, expectedByteIndex, TruffleString.ByteIndexOfStringSetNode.unpackResultByteIndex(result));
        Assert.assertEquals(context, expectedPatternIndex, TruffleString.ByteIndexOfStringSetNode.unpackResultPatternIndex(result));
    }

    private record StringSetCase(String name, String[] patterns, String[] haystacks) {
    }

    private record SearchInput(AbstractTruffleString haystack, SearchWindow[] windows) {
    }

    private record SearchWindow(String name, boolean shortWindow, int fromByteIndex, int toByteIndex) {
    }

    private record SearchExpectation(int byteIndex, int patternIndex) {
    }
}
