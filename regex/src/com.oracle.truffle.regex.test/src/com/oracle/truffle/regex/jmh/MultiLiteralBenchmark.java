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
package com.oracle.truffle.regex.jmh;

import java.io.IOException;
import java.util.StringJoiner;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import com.oracle.truffle.api.strings.TranscodingErrorHandler;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.CompactionLevel;
import com.oracle.truffle.regex.test.dummylang.TRegexTestDummyLanguage;
import com.oracle.truffle.regex.test.dummylang.TRegexTestDummyLanguageOptions;
import com.oracle.truffle.regex.tregex.string.Encoding;

/**
 * Benchmark for TRegex's use of {@code TruffleString.ByteIndexOfStringSetNode}.
 */
public class MultiLiteralBenchmark extends BenchmarkBase {

    private static final char[] PATTERN_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();
    private static final int S2_WIDENING_CODE_POINT = 0x0100;
    private static final int S4_WIDENING_CODE_POINT = 0x10000;
    private static final int HAYSTACK_LENGTH_GRANULARITY = 32;
    private static final int PADDING_AFTER_MATCH_POSITION = 16;

    @State(Scope.Benchmark)
    public static class BenchState {

        @Param({"S1", "S2", "S4"}) CompactionLevel stride;
        @Param({"0", "8", "32", "1024"}) int matchPosition;
        @Param({"8", "16"}) int literalCount;
        @Param({"2", "3", "4"}) int tableCount;

        Encoding tRegexEncoding;
        TruffleString.Encoding tStringEncoding;
        int wideningCodePoint;

        Context context;
        Value tregex;
        TruffleString input;

        public BenchState() {
        }

        @Setup
        public void setUp() {
            tRegexEncoding = switch (stride) {
                case S1 -> Encoding.UTF_8;
                case S2 -> Encoding.UTF_16;
                case S4 -> Encoding.UTF_32;
            };
            tStringEncoding = tRegexEncoding.getTStringEncoding();
            wideningCodePoint = switch (stride) {
                case S1 -> -1;
                case S2 -> S2_WIDENING_CODE_POINT;
                case S4 -> S4_WIDENING_CODE_POINT;
            };
            if (literalCount > tableCount * 16) {
                throw new IllegalStateException("parameters fall back to Aho-Corasick");
            }

            String[] literals = createLiterals(literalCount, tableCount);
            String inputJavaString = createHaystack(literals, matchPosition, literals[literalCount - 1], wideningCodePoint);
            input = toTruffleString(inputJavaString, tRegexEncoding);
            if (!input.getStringCompactionLevelUncached(tStringEncoding).equals(stride)) {
                throw new IllegalStateException("input was compacted to " + input.getStringCompactionLevelUncached(tStringEncoding));
            }
            context = Context.newBuilder(TRegexTestDummyLanguage.ID).allowExperimentalOptions(true).build();
            context.enter();
            tregex = context.parse(createSource(literals, tRegexEncoding));
            if (!tregex.execute(input, 0).asBoolean()) {
                throw new IllegalStateException("expected a match");
            }
        }

        @TearDown
        public void tearDown() {
            context.leave();
            context.close();
        }
    }

    @Benchmark
    public boolean tregex(BenchState state) {
        boolean result = state.tregex.execute(state.input, 0).asBoolean();
        if (!result) {
            throw new IllegalStateException("expected a match");
        }
        return result;
    }

    private static Source createSource(String[] literals, Encoding encoding) {
        try {
            Source.Builder builder = Source.newBuilder(TRegexTestDummyLanguage.ID, '/' + createAlternation(literals) + '/', "multiLiteral");
            builder.option(TRegexTestDummyLanguage.ID + ".GenerateDFAImmediately", "true");
            builder.option(TRegexTestDummyLanguage.ID + ".Mode", TRegexTestDummyLanguageOptions.ExecutionMode.Bench.name());
            builder.option(TRegexTestDummyLanguage.ID + ".Encoding", encoding.getName());
            return builder.build();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String createAlternation(String[] literals) {
        StringJoiner joiner = new StringJoiner("|");
        for (String literal : literals) {
            joiner.add(escapeRegexLiteral(literal));
        }
        return joiner.toString();
    }

    private static String escapeRegexLiteral(String literal) {
        StringBuilder sb = new StringBuilder(literal.length());
        for (int i = 0; i < literal.length(); i++) {
            char c = literal.charAt(i);
            switch (c) {
                case '\\':
                case '/':
                case '^':
                case '$':
                case '.':
                case '|':
                case '?':
                case '*':
                case '+':
                case '(':
                case ')':
                case '[':
                case ']':
                case '{':
                case '}':
                    sb.append('\\');
                    break;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static String[] createLiterals(int literalCount, int tableCount) {
        if (literalCount * 2 > PATTERN_ALPHABET.length) {
            throw new IllegalStateException("literalCount too large");
        }
        String[] ret = new String[literalCount];
        for (int i = 0; i < literalCount; i++) {
            int length = tableCount + (i & 1);
            char start = PATTERN_ALPHABET[i * 2];
            char fill = PATTERN_ALPHABET[PATTERN_ALPHABET.length - 1 - i];
            ret[i] = start + String.valueOf(fill).repeat(Math.max(0, length - 1));
        }
        return ret;
    }

    private static TruffleString toTruffleString(String input, Encoding encoding) {
        TruffleString tStringUTF16 = TruffleString.fromJavaStringUncached(input, TruffleString.Encoding.UTF_16);
        return tStringUTF16.switchEncodingUncached(encoding.getTStringEncoding(), TranscodingErrorHandler.DEFAULT_KEEP_SURROGATES_IN_UTF8);
    }

    private static String createHaystack(String[] literals, int matchPosition, String matchingLiteral, int wideningCodePoint) {
        int literalLength = matchingLiteral.codePointCount(0, matchingLiteral.length());
        int wideningLength = wideningCodePoint < 0 ? 0 : 1;
        if (matchPosition < 0) {
            throw new IllegalStateException("matchPosition must be non-negative");
        }
        int matchStart = matchPosition + PADDING_AFTER_MATCH_POSITION;
        int minimumHaystackLength = matchStart + literalLength + wideningLength;
        int haystackLength = roundUp(Math.max(HAYSTACK_LENGTH_GRANULARITY, minimumHaystackLength), HAYSTACK_LENGTH_GRANULARITY);
        int suffixLength = haystackLength - matchStart - literalLength - wideningLength;
        StringBuilder sb = new StringBuilder(haystackLength + (wideningCodePoint >= 0 ? 1 : 0));
        appendPartialMatches(sb, literals, matchPosition, matchingLiteral);
        sb.append(Character.toString('~').repeat(PADDING_AFTER_MATCH_POSITION));
        sb.append(matchingLiteral);
        if (wideningCodePoint >= 0) {
            sb.appendCodePoint(wideningCodePoint);
        }
        sb.append(Character.toString('|').repeat(suffixLength));
        return sb.toString();
    }

    private static void appendPartialMatches(StringBuilder sb, String[] literals, int matchPosition, String matchingLiteral) {
        sb.append(Character.toString('~').repeat(matchPosition));
        int markerCount = Math.min(Math.max(3, matchPosition / 50), matchPosition);
        for (int i = 1; i <= markerCount; i++) {
            int position = Math.min(matchPosition - 1, Math.max(0, Math.round((float) i * matchPosition / (markerCount + 1))));
            sb.setCharAt(position, literals[i % literals.length].charAt(0));
        }
    }

    private static int roundUp(int value, int multiple) {
        return ((value + multiple - 1) / multiple) * multiple;
    }
}
