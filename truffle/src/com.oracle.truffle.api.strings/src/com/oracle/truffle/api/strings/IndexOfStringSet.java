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
package com.oracle.truffle.api.strings;

import static com.oracle.truffle.api.strings.TStringUnsafe.byteArrayBaseOffset;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString.Encoding;

final class IndexOfStringSet {

    private static final byte[] CODE_RANGES_ASCII_BROKEN = {
                    (byte) TruffleString.CodeRange.ASCII.ordinal(),
                    (byte) TruffleString.CodeRange.BROKEN.ordinal()};
    private static final byte[] CODE_RANGES_ASCII_LATIN1 = {
                    (byte) TruffleString.CodeRange.ASCII.ordinal(),
                    (byte) TruffleString.CodeRange.LATIN_1.ordinal()};
    private static final byte[] CODE_RANGES_ASCII_VALID_BROKEN = {
                    (byte) TruffleString.CodeRange.ASCII.ordinal(),
                    (byte) TruffleString.CodeRange.VALID.ordinal(),
                    (byte) TruffleString.CodeRange.BROKEN.ordinal()};
    private static final byte[] CODE_RANGES_ALL = {
                    (byte) TruffleString.CodeRange.ASCII.ordinal(),
                    (byte) TruffleString.CodeRange.LATIN_1.ordinal(),
                    (byte) TruffleString.CodeRange.BMP.ordinal(),
                    (byte) TruffleString.CodeRange.VALID.ordinal(),
                    (byte) TruffleString.CodeRange.BROKEN.ordinal()};

    @TruffleBoundary
    static TruffleString.StringSet fromArray(AbstractTruffleString[] strings, Encoding encoding) {
        Objects.requireNonNull(strings, "strings");
        Objects.requireNonNull(encoding, "encoding");
        if (!encoding.isSupported()) {
            throw InternalErrors.unsupportedOperation();
        }
        for (AbstractTruffleString s : strings) {
            AbstractTruffleString string = Objects.requireNonNull(s);
            string.checkEncoding(encoding);
            if (string.isEmpty() || !string.isValidUncached(encoding)) {
                throw InternalErrors.illegalArgument("empty or broken patterns are not supported");
            }
        }
        return new TruffleString.StringSet(encoding, buildCodeRangePlans(collectPatternData(strings, encoding), encoding));
    }

    static TruffleString.StringSet fromArray(TruffleString.WithMask[] strings, Encoding encoding) {
        Objects.requireNonNull(strings, "strings");
        Objects.requireNonNull(encoding, "encoding");
        if (!encoding.isSupported()) {
            throw InternalErrors.unsupportedOperation();
        }
        for (TruffleString.WithMask s : strings) {
            TruffleString.WithMask string = Objects.requireNonNull(s);
            string.string.checkEncoding(encoding);
            if (string.string.isEmpty() || !string.string.isValidUncached(encoding)) {
                throw InternalErrors.illegalArgument("empty or broken patterns are not supported");
            }
        }
        return new TruffleString.StringSet(encoding, buildCodeRangePlans(collectPatternData(strings, encoding), encoding));
    }

    private static SearchPlan[] buildCodeRangePlans(PatternData patternData, Encoding encoding) {
        // Build one ordered entry per relevant haystack code-range bucket. Each entry keeps only
        // the patterns that are not more general than that haystack bucket.
        byte[] maxCodeRanges = getRelevantCodeRanges(encoding);
        ArrayList<SearchPlan> plans = new ArrayList<>(maxCodeRanges.length);
        PatternSelection previousPatternSelection = null;
        boolean previousTeddyCandidate = false;
        for (byte codeRange : maxCodeRanges) {
            int maxCodeRange = Byte.toUnsignedInt(codeRange);
            PatternSelection patternSelection = selectPatterns(patternData, encoding, maxCodeRange);
            boolean teddyCandidate = isTeddyCandidate(patternData, patternSelection, encoding, maxCodeRange);
            if (!plans.isEmpty() && patternSelection.selectionEquals(previousPatternSelection) && teddyCandidate == previousTeddyCandidate) {
                plans.get(plans.size() - 1).maxCodeRange = (byte) maxCodeRange;
                continue;
            }
            addOrExtendLastPlan(plans, buildSearchPlan(patternData, patternSelection, encoding, maxCodeRange, teddyCandidate));
            previousPatternSelection = patternSelection;
            previousTeddyCandidate = teddyCandidate;
        }
        return plans.toArray(SearchPlan[]::new);
    }

    private static SearchPlan buildSearchPlan(PatternData patternData, PatternSelection patternSelection, Encoding encoding, int maxCodeRange, boolean teddyCandidate) {
        SearchPlan fallbackPlan = buildFallbackPlan(patternData, patternSelection, encoding, maxCodeRange);
        if (!teddyCandidate) {
            return fallbackPlan;
        }
        SearchPlan teddyPlan = buildTeddyPlan(patternData, patternSelection, encoding, maxCodeRange, fallbackPlan);
        return teddyPlan == null ? fallbackPlan : teddyPlan;
    }

    private static void addOrExtendLastPlan(ArrayList<SearchPlan> plans, SearchPlan plan) {
        if (!plans.isEmpty()) {
            SearchPlan previousPlan = plans.get(plans.size() - 1);
            if (previousPlan.codeEquals(plan)) {
                previousPlan.maxCodeRange = plan.maxCodeRange;
                return;
            }
        }
        plans.add(plan);
    }

    private static byte[] getRelevantCodeRanges(Encoding encoding) {
        return switch (encoding) {
            case US_ASCII -> CODE_RANGES_ASCII_BROKEN;
            case ISO_8859_1 -> CODE_RANGES_ASCII_LATIN1;
            case UTF_16LE, UTF_16BE, UTF_32LE, UTF_32BE -> CODE_RANGES_ALL;
            default -> CODE_RANGES_ASCII_VALID_BROKEN;
        };
    }

    private static PatternData collectPatternData(AbstractTruffleString[] strings, Encoding encoding) {
        byte[][] encodedPatterns = new byte[strings.length][];
        byte[][] encodedMasks = new byte[strings.length][];
        byte[] patternCodeRanges = new byte[strings.length];
        int[] originalIndices = new int[strings.length];
        int maxPatternCodeRange = 0;
        for (int i = 0; i < strings.length; i++) {
            AbstractTruffleString string = strings[i];
            // materialize string contents in encoding's natural stride
            encodedPatterns[i] = string.copyToByteArrayUncached(encoding);
            // force computation of precise code range
            int codeRange = string.getCodeRangeUncached(encoding).ordinal();
            patternCodeRanges[i] = (byte) codeRange;
            originalIndices[i] = i;
            maxPatternCodeRange = Math.max(maxPatternCodeRange, codeRange);
        }
        return new PatternData(encodedPatterns, encodedMasks, patternCodeRanges, originalIndices, maxPatternCodeRange);
    }

    private static PatternData collectPatternData(TruffleString.WithMask[] strings, Encoding encoding) {
        byte[][] encodedPatterns = new byte[strings.length][];
        byte[][] encodedMasks = new byte[strings.length][];
        byte[] patternCodeRanges = new byte[strings.length];
        int[] originalIndices = new int[strings.length];
        int maxPatternCodeRange = 0;
        for (int i = 0; i < strings.length; i++) {
            TruffleString.WithMask string = strings[i];
            encodedPatterns[i] = string.string.copyToByteArrayUncached(encoding);
            encodedMasks[i] = copyMaskToNaturalStride(string, encoding);
            if (isAllZero(encodedMasks[i])) {
                encodedMasks[i] = null;
            } else {
                validateMask(encodedPatterns[i], encodedMasks[i], encoding);
            }
            int codeRange = string.string.getCodeRangeUncached(encoding).ordinal();
            patternCodeRanges[i] = (byte) codeRange;
            originalIndices[i] = i;
            maxPatternCodeRange = Math.max(maxPatternCodeRange, codeRange);
        }
        return new PatternData(encodedPatterns, encodedMasks, patternCodeRanges, originalIndices, maxPatternCodeRange);
    }

    private static PatternSelection selectPatterns(PatternData patternData, Encoding encoding, int maxCodeRange) {
        int[] patternIds = new int[patternData.encodedPatterns.length];
        int nPatterns = 0;
        int minPatternLength = Integer.MAX_VALUE;
        int maxPatternLength = 0;
        for (int i = 0; i < patternData.encodedPatterns.length; i++) {
            if (TSCodeRange.isMoreGeneralThan(Byte.toUnsignedInt(patternData.patternCodeRanges[i]), maxCodeRange)) {
                continue;
            }
            patternIds[nPatterns++] = i;
            int rawLength = rawLength(patternData.encodedPatterns[i], encoding);
            minPatternLength = Math.min(minPatternLength, rawLength);
            maxPatternLength = Math.max(maxPatternLength, rawLength);
        }
        if (nPatterns == 0) {
            return PatternSelection.EMPTY;
        }
        return new PatternSelection(Arrays.copyOf(patternIds, nPatterns), minPatternLength, maxPatternLength);
    }

    private static SearchPlan buildFallbackPlan(PatternData patternData, PatternSelection patternSelection, Encoding encoding, int maxCodeRange) {
        if (patternSelection.isEmpty()) {
            return new NoMatchSearchPlan(maxCodeRange);
        }
        if (patternSelection.isSinglePattern()) {
            return buildSinglePatternPlan(patternData, patternSelection, encoding, maxCodeRange);
        }
        return buildAhoCorasickPlan(patternData, patternSelection, encoding, maxCodeRange);
    }

    private static SearchPlan buildSinglePatternPlan(PatternData patternData, PatternSelection patternSelection, Encoding encoding, int maxCodeRange) {
        int patternId = patternSelection.patternIds[0];
        int stride = Stride.fromCodeRange(patternData.patternCodeRanges[patternId], encoding);
        return new SingleSearchPlan(maxCodeRange, patternSelection.minPatternLength, patternSelection.maxPatternLength,
                        compactIfPossible(patternData.encodedPatterns[patternId], encoding.naturalStride, stride),
                        compactIfPossible(patternData.encodedMasks[patternId], encoding.naturalStride, stride),
                        stride, patternData.originalIndices[patternId]);
    }

    private static SearchPlan buildAhoCorasickPlan(PatternData patternData, PatternSelection patternSelection, Encoding encoding, int maxCodeRange) {
        byte[][] encodedPatterns = new byte[patternSelection.patternIds.length][];
        int[] originalIndices = new int[patternSelection.patternIds.length];
        for (int i = 0; i < patternSelection.patternIds.length; i++) {
            int patternId = patternSelection.patternIds[i];
            encodedPatterns[i] = patternData.encodedPatterns[patternId];
            originalIndices[i] = patternData.originalIndices[patternId];
        }
        byte[][] encodedMasks = new byte[patternSelection.patternIds.length][];
        for (int i = 0; i < patternSelection.patternIds.length; i++) {
            encodedMasks[i] = patternData.encodedMasks[patternSelection.patternIds[i]];
        }
        return IndexOfStringSetAhoCorasick.create(maxCodeRange,
                        encoding,
                        patternSelection.minPatternLength,
                        patternSelection.maxPatternLength,
                        encodedPatterns,
                        encodedMasks,
                        originalIndices);
    }

    private static boolean isTeddyCandidate(PatternData patternData, PatternSelection patternSelection, Encoding encoding, int maxCodeRange) {
        // Teddy is used if there are at least two patterns and either the haystack code range or all
        // search patterns are ASCII/Latin-1, or the encoding is byte-based.
        return patternSelection.patternIds.length > 1 && encoding.isSupported() &&
                        (TSCodeRange.isMoreRestrictiveOrEqual(maxCodeRange, TSCodeRange.get8Bit()) ||
                                        TSCodeRange.isMoreRestrictiveOrEqual(patternData.maxPatternCodeRange, TSCodeRange.get8Bit()) ||
                                        encoding.naturalStride == 0);
    }

    private static SearchPlan buildTeddyPlan(PatternData patternData, PatternSelection patternSelection, Encoding encoding, int maxCodeRange, SearchPlan fallbackPlan) {
        assert isTeddyCandidate(patternData, patternSelection, encoding, maxCodeRange);
        int patternStride = encoding.isForeignEndian() ? encoding.naturalStride : 0;
        byte[][] encodedPatterns = new byte[patternSelection.patternIds.length][];
        byte[][] encodedMasks = new byte[patternSelection.patternIds.length][];
        int[] originalIndices = new int[patternSelection.patternIds.length];
        for (int i = 0; i < patternSelection.patternIds.length; i++) {
            int patternId = patternSelection.patternIds[i];
            byte[] encodedPattern = patternData.encodedPatterns[patternId];
            encodedPatterns[i] = compactIfPossible(encodedPattern, encoding.naturalStride, patternStride);
            encodedMasks[i] = compactIfPossible(patternData.encodedMasks[patternId], encoding.naturalStride, patternStride);
            originalIndices[i] = patternData.originalIndices[patternId];
        }
        return IndexOfStringSetTeddy.create(maxCodeRange, patternSelection.minPatternLength, patternSelection.maxPatternLength,
                        encoding, encodedPatterns, encodedMasks, patternStride, originalIndices, fallbackPlan);
    }

    private static final class PatternData {
        @CompilationFinal(dimensions = 2) final byte[][] encodedPatterns;
        @CompilationFinal(dimensions = 2) final byte[][] encodedMasks;
        @CompilationFinal(dimensions = 1) final byte[] patternCodeRanges;
        @CompilationFinal(dimensions = 1) final int[] originalIndices;
        final byte maxPatternCodeRange;

        PatternData(byte[][] encodedPatterns, byte[][] encodedMasks, byte[] patternCodeRanges, int[] originalIndices, int maxPatternCodeRange) {
            this.encodedPatterns = encodedPatterns;
            this.encodedMasks = encodedMasks;
            this.patternCodeRanges = patternCodeRanges;
            this.originalIndices = originalIndices;
            this.maxPatternCodeRange = (byte) maxPatternCodeRange;
        }
    }

    private static final class PatternSelection {

        static final PatternSelection EMPTY = new PatternSelection(new int[0], 0, 0);

        final int[] patternIds;
        final int minPatternLength;
        final int maxPatternLength;

        PatternSelection(int[] patternIds, int minPatternLength, int maxPatternLength) {
            this.patternIds = patternIds;
            this.minPatternLength = minPatternLength;
            this.maxPatternLength = maxPatternLength;
        }

        boolean isEmpty() {
            return patternIds.length == 0;
        }

        boolean isSinglePattern() {
            return patternIds.length == 1;
        }

        boolean selectionEquals(PatternSelection other) {
            return other != null && minPatternLength == other.minPatternLength && maxPatternLength == other.maxPatternLength && Arrays.equals(patternIds, other.patternIds);
        }
    }

    private static byte[] compactIfPossible(byte[] arrayA, int strideA, int strideB) {
        if (arrayA == null || strideA == strideB) {
            return arrayA;
        }
        int rawLength = rawLength(arrayA, strideA);
        return TStringOps.arraycopyOfWithStride(null, arrayA, byteArrayBaseOffset(), rawLength, strideA, rawLength, strideB);
    }

    private static byte[] copyMaskToNaturalStride(TruffleString.WithMask string, Encoding encoding) {
        int sourceStride = string.string.stride();
        int targetStride = encoding.naturalStride;
        int rawLength = string.string.length();
        byte[] ret = TStringOps.arraycopyOfWithStride(null, string.mask, byteArrayBaseOffset(), rawLength, sourceStride, rawLength, targetStride);
        if (encoding.isForeignEndian() && sourceStride < targetStride) {
            assert sourceStride == 0;
            byte[] swapped = new byte[ret.length];
            if (targetStride == 1) {
                TStringOps.byteSwapS1(null, ret, byteArrayBaseOffset(), swapped, byteArrayBaseOffset(), rawLength);
            } else {
                assert targetStride == 2;
                TStringOps.byteSwapS2(null, ret, byteArrayBaseOffset(), swapped, byteArrayBaseOffset(), rawLength);
            }
            return swapped;
        }
        return ret;
    }

    private static boolean isAllZero(byte[] mask) {
        for (byte b : mask) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    private static void validateMask(byte[] pattern, byte[] mask, Encoding encoding) {
        int rawLength = rawLength(pattern, encoding);
        int stride = encoding.naturalStride;
        boolean foreignEndian = encoding.isForeignEndian();
        for (int i = 0; i < rawLength; i++) {
            int patternValue = readValue(pattern, stride, foreignEndian, i);
            int maskValue = readValue(mask, stride, foreignEndian, i);
            if (Integer.bitCount(maskValue) > 1) {
                throw InternalErrors.illegalArgument("mask code units must have a population count of at most one");
            }
            if ((patternValue | maskValue) != patternValue) {
                throw InternalErrors.illegalArgument("mask bits must be set in the corresponding pattern code unit");
            }
            if (codeUnitRange(patternValue) != codeUnitRange(patternValue ^ maskValue)) {
                throw InternalErrors.illegalArgument("mask alternatives must stay in the same code range as the corresponding pattern code unit");
            }
        }
    }

    private static int codeUnitRange(int value) {
        if (value <= 0x7f) {
            return TSCodeRange.get7Bit();
        }
        if (value <= 0xff) {
            return TSCodeRange.get8Bit();
        }
        if (value <= 0xffff) {
            return TSCodeRange.get16Bit();
        }
        return TSCodeRange.getValidFixedWidth();
    }

    static int readValue(byte[] arrayA, int strideA, boolean foreignEndian, int i) {
        int value = TStringOps.readFromByteArray(arrayA, strideA, i);
        return foreignEndian ? Encodings.reverseBytes(value, strideA) : value;
    }

    static int rawLength(byte[] pattern, Encoding encoding) {
        return rawLength(pattern, encoding.naturalStride);
    }

    static int rawLength(byte[] pattern, int patternStride) {
        return pattern.length >> patternStride;
    }

    abstract static class SearchPlan {
        @CompilationFinal byte maxCodeRange;
        final int minPatternLength;
        final int maxPatternLength;

        SearchPlan(int maxCodeRange, int minPatternLength, int maxPatternLength) {
            this.maxCodeRange = (byte) maxCodeRange;
            this.minPatternLength = minPatternLength;
            this.maxPatternLength = maxPatternLength;
        }

        final long runSearch(Node location, byte[] arrayA, long offsetA, int lengthA, int strideA, int fromIndex, int toIndex, Encoding encoding) {
            if (toIndex - fromIndex < minPatternLength) {
                return TruffleString.ByteIndexOfStringSetNode.NO_MATCH;
            }
            return runSearchImpl(location, arrayA, offsetA, lengthA, strideA, fromIndex, toIndex, encoding);
        }

        abstract long runSearchImpl(Node location, byte[] arrayA, long offsetA, int lengthA, int strideA, int fromIndex, int toIndex, Encoding encoding);

        boolean isIntrinsicCandidate() {
            return false;
        }

        abstract boolean codeEquals(SearchPlan other);

        final boolean basicCodeEquals(SearchPlan other) {
            return getClass() == other.getClass() && minPatternLength == other.minPatternLength && maxPatternLength == other.maxPatternLength;
        }
    }

    static final class NoMatchSearchPlan extends SearchPlan {

        NoMatchSearchPlan(int maxCodeRange) {
            super(maxCodeRange, 0, 0);
        }

        @Override
        long runSearchImpl(Node location, byte[] arrayA, long offsetA, int lengthA, int strideA, int fromIndex, int toIndex, Encoding encoding) {
            return TruffleString.ByteIndexOfStringSetNode.NO_MATCH;
        }

        @Override
        boolean codeEquals(SearchPlan other) {
            return basicCodeEquals(other);
        }
    }

    static final class SingleSearchPlan extends SearchPlan {
        @CompilationFinal(dimensions = 1) final byte[] encodedPattern;
        @CompilationFinal(dimensions = 1) final byte[] encodedMask;
        final int patternStride;
        final int originalIndex;

        SingleSearchPlan(int maxCodeRange, int minPatternLength, int maxPatternLength, byte[] encodedPattern, byte[] encodedMask, int patternStride, int originalIndex) {
            super(maxCodeRange, minPatternLength, maxPatternLength);
            this.encodedPattern = encodedPattern;
            this.encodedMask = encodedMask;
            this.patternStride = patternStride;
            this.originalIndex = originalIndex;
        }

        @Override
        long runSearchImpl(Node location, byte[] arrayA, long offsetA, int lengthA, int strideA, int fromIndex, int toIndex, Encoding encoding) {
            int rawLength = encodedPattern.length >> patternStride;
            int rawIndex;
            if (rawLength == 1) {
                int value = TStringOps.readFromByteArray(encodedPattern, patternStride, 0);
                int mask = encodedMask == null ? 0 : TStringOps.readFromByteArray(encodedMask, patternStride, 0);
                rawIndex = TStringOps.indexOfCodePointWithOrMaskWithStride(location, arrayA, offsetA, strideA, fromIndex, toIndex, value, mask);
            } else {
                rawIndex = TStringOps.indexOfStringWithOrMaskWithStride(location,
                                arrayA, offsetA, lengthA, strideA,
                                encodedPattern, byteArrayBaseOffset(), rawLength, patternStride, fromIndex, toIndex, encodedMask);
            }
            if (rawIndex < 0) {
                return TruffleString.ByteIndexOfStringSetNode.NO_MATCH;
            }
            return TruffleString.ByteIndexOfStringSetNode.packResult(AbstractTruffleString.byteIndex(rawIndex, encoding), originalIndex);
        }

        @Override
        boolean codeEquals(SearchPlan other) {
            if (!basicCodeEquals(other)) {
                return false;
            }
            SingleSearchPlan o = (SingleSearchPlan) other;
            return patternStride == o.patternStride && originalIndex == o.originalIndex && Arrays.equals(encodedPattern, o.encodedPattern) && Arrays.equals(encodedMask, o.encodedMask);
        }
    }
}
