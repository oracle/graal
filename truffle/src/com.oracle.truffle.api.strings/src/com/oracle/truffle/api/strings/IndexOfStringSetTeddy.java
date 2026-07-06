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

import java.util.Arrays;

import org.graalvm.collections.EconomicMap;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString.Encoding;

/**
 * Teddy-style prefilter/search plan inspired by the
 * <a href="https://github.com/BurntSushi/aho-corasick">aho-corasick</a> Rust crate.
 *
 * @see <a href=
 *      "https://github.com/BurntSushi/aho-corasick/blob/0f3f5da9bdec0d811f9a344e3cb9c033b15df20b/src/packed/teddy/README.md">aho-corasick
 *      teddy implementation</a>
 */
final class IndexOfStringSetTeddy extends IndexOfStringSet.SearchPlan {

    private static final int MAX_MASK_LENGTH = 4;
    private static final int MIN_TEDDY_SEARCH_LENGTH = 16;
    private static final int TABLE_SIZE = 16;
    private static final int TABLE_BYTES = TABLE_SIZE * 2;

    private static final int BUCKETS = Byte.SIZE;
    private static final int BUCKET_FIELD_PATTERN_COUNT = 0;
    private static final int BUCKET_FIELD_PATTERNS = 1;

    private final byte maskLength;
    private final byte patternStride;
    private final IndexOfStringSet.SearchPlan fallbackPlan;
    @CompilationFinal(dimensions = 2) private final byte[][] encodedPatterns;
    @CompilationFinal(dimensions = 2) private final byte[][] encodedMasks;
    @CompilationFinal(dimensions = 1) private final int[] patternOriginalIndices;
    /**
     * Flattened bucket index. The first {@link #BUCKETS} entries are bucket record offsets. Each
     * bucket record is {@code [patternCount, patternId0, patternId1, ...]}, with pattern ids sorted
     * by ascending original pattern index inside each bucket.
     */
    @CompilationFinal(dimensions = 1) private final int[] bucketData;
    @CompilationFinal(dimensions = 1) private final byte[] fingerprintTables;

    private IndexOfStringSetTeddy(int maxCodeRange, int minPatternLength, int maxPatternLength,
                    int maskLength,
                    int patternStride,
                    IndexOfStringSet.SearchPlan fallbackPlan,
                    byte[][] encodedPatterns,
                    byte[][] encodedMasks,
                    int[] patternOriginalIndices,
                    int[] bucketData,
                    byte[] fingerprintTables) {
        super(maxCodeRange, minPatternLength, maxPatternLength);
        this.maskLength = (byte) maskLength;
        this.patternStride = (byte) patternStride;
        this.fallbackPlan = fallbackPlan;
        this.encodedPatterns = encodedPatterns;
        this.encodedMasks = encodedMasks;
        this.patternOriginalIndices = patternOriginalIndices;
        this.bucketData = bucketData;
        this.fingerprintTables = fingerprintTables;
    }

    static IndexOfStringSet.SearchPlan create(int maxCodeRange, int minPatternLength, int maxPatternLength, Encoding encoding,
                    byte[][] patterns, byte[][] masks, int patternStride, int[] patternOriginalIndices, IndexOfStringSet.SearchPlan fallbackPlan) {
        int maskLength = Math.min(MAX_MASK_LENGTH, minPatternLength);
        // This simple Teddy variant uses at most 8 buckets. If we have too many patterns relative to
        // the fingerprint length, the bucket prefilter becomes too imprecise, and we prefer the
        // Aho-Corasick plan instead.
        if (patterns.length > maskLength * 16) {
            return null;
        }
        Builder builder = new Builder(encoding, patterns, masks, patternStride, patternOriginalIndices, maskLength);
        return builder.createSearchPlan(maxCodeRange, minPatternLength, maxPatternLength, fallbackPlan);
    }

    @Override
    long runSearchImpl(Node location, byte[] arrayA, long offsetA, int lengthA, int strideA, int fromIndex, int toIndex, Encoding encoding) {
        int searchIndex = fromIndex;
        while (searchIndex <= toIndex - MIN_TEDDY_SEARCH_LENGTH) {
            long fingerprintCandidate = findFingerprintCandidate(location, arrayA, offsetA, strideA, searchIndex, toIndex, encoding);
            if (!TStringOps.indexOfTablesResultIsMatch(fingerprintCandidate)) {
                return checkReturn(location, arrayA, offsetA, lengthA, strideA, fromIndex, toIndex, encoding, TruffleString.ByteIndexOfStringSetNode.NO_MATCH);
            }
            int matchStart = TStringOps.unpackIndexOfTablesResultIndex(fingerprintCandidate);
            int candidateBuckets = TStringOps.unpackIndexOfTablesResultBitSet(fingerprintCandidate);
            int patternIndex = verifyCandidate(location, arrayA, offsetA, lengthA, strideA, toIndex, matchStart, candidateBuckets);
            if (patternIndex >= 0) {
                long ret = TruffleString.ByteIndexOfStringSetNode.packResult(AbstractTruffleString.byteIndex(matchStart, encoding), patternIndex);
                return checkReturn(location, arrayA, offsetA, lengthA, strideA, fromIndex, toIndex, encoding, ret);
            }
            searchIndex = matchStart + 1;
            TStringConstants.truffleSafePointPoll(location, searchIndex);
        }
        if (searchIndex <= toIndex - maskLength) {
            return fallbackPlan.runSearch(location, arrayA, offsetA, lengthA, strideA, searchIndex, toIndex, encoding);
        }
        return checkReturn(location, arrayA, offsetA, lengthA, strideA, fromIndex, toIndex, encoding, TruffleString.ByteIndexOfStringSetNode.NO_MATCH);
    }

    long checkReturn(Node location, byte[] arrayA, long offsetA, int lengthA, int strideA, int fromIndex, int toIndex, Encoding encoding, long returnValue) {
        assert fallbackPlan.runSearch(location, arrayA, offsetA, lengthA, strideA, fromIndex, toIndex, encoding) == returnValue;
        return returnValue;
    }

    private long findFingerprintCandidate(Node location, byte[] arrayA, long offsetA, int strideA, int fromIndex, int toIndex, Encoding encoding) {
        if (encoding.isForeignEndian()) {
            return switch (maskLength) {
                case 1 -> TStringOps.indexOfTableWithBitSetForeignEndian(location, arrayA, offsetA, strideA, fromIndex, toIndex, fingerprintTables);
                case 2 -> TStringOps.indexOf2ConsecutiveTablesForeignEndian(location, arrayA, offsetA, strideA, fromIndex, toIndex, fingerprintTables);
                case 3 -> TStringOps.indexOf3ConsecutiveTablesForeignEndian(location, arrayA, offsetA, strideA, fromIndex, toIndex, fingerprintTables);
                default -> TStringOps.indexOf4ConsecutiveTablesForeignEndian(location, arrayA, offsetA, strideA, fromIndex, toIndex, fingerprintTables);
            };
        }
        return switch (maskLength) {
            case 1 -> TStringOps.indexOfTableWithBitSet(location, arrayA, offsetA, strideA, fromIndex, toIndex, fingerprintTables);
            case 2 -> TStringOps.indexOf2ConsecutiveTables(location, arrayA, offsetA, strideA, fromIndex, toIndex, fingerprintTables);
            case 3 -> TStringOps.indexOf3ConsecutiveTables(location, arrayA, offsetA, strideA, fromIndex, toIndex, fingerprintTables);
            default -> TStringOps.indexOf4ConsecutiveTables(location, arrayA, offsetA, strideA, fromIndex, toIndex, fingerprintTables);
        };
    }

    private int verifyCandidate(Node location, byte[] arrayA, long offsetA, int lengthA, int strideA, int toIndex, int start, int candidateBuckets) {
        int bestPatternIndex = -1;
        int buckets = candidateBuckets & 0xff;
        while (buckets != 0) {
            int bucket = Integer.numberOfTrailingZeros(buckets);
            bestPatternIndex = checkBucket(location, arrayA, offsetA, lengthA, strideA, toIndex, start, bucket, bestPatternIndex);
            if (bestPatternIndex == 0) {
                return 0;
            }
            buckets &= buckets - 1;
        }
        return bestPatternIndex;
    }

    @ExplodeLoop
    private int checkBucket(Node location, byte[] arrayA, long offsetA, int lengthA, int strideA, int toIndex, int matchStart, int bucket, int bestPatternIndex) {
        for (int i = 0; i < BUCKETS; i++) {
            if (bucket == i) {
                int bucketRecord = bucketData[i];
                int bucketStart = bucketRecord + BUCKET_FIELD_PATTERNS;
                int bucketEnd = bucketStart + bucketData[bucketRecord + BUCKET_FIELD_PATTERN_COUNT];
                CompilerAsserts.partialEvaluationConstant(i);
                CompilerAsserts.partialEvaluationConstant(bucketRecord);
                CompilerAsserts.partialEvaluationConstant(bucketStart);
                CompilerAsserts.partialEvaluationConstant(bucketEnd);
                for (int j = bucketStart; j < bucketEnd; j++) {
                    int patternId = bucketData[j];
                    int patternIndex = patternOriginalIndices[patternId];
                    CompilerAsserts.partialEvaluationConstant(j);
                    CompilerAsserts.partialEvaluationConstant(patternId);
                    if (Integer.compareUnsigned(patternIndex, bestPatternIndex) >= 0) {
                        return bestPatternIndex;
                    }
                    byte[] pattern = encodedPatterns[patternId];
                    byte[] mask = encodedMasks[patternId];
                    int patternLength = IndexOfStringSet.rawLength(pattern, patternStride);
                    CompilerAsserts.partialEvaluationConstant(pattern);
                    CompilerAsserts.partialEvaluationConstant(mask);
                    CompilerAsserts.partialEvaluationConstant(patternLength);
                    if (matchStart + patternLength <= toIndex && TStringOps.regionEqualsWithOrMaskWithStride(location,
                                    arrayA, offsetA, lengthA, strideA, matchStart,
                                    pattern, TStringUnsafe.byteArrayBaseOffset(), patternLength, patternStride, 0, mask, patternLength)) {
                        return patternIndex;
                    }
                }
            }
        }
        return bestPatternIndex;
    }

    @Override
    boolean isIntrinsicCandidate() {
        return true;
    }

    @Override
    boolean codeEquals(IndexOfStringSet.SearchPlan other) {
        if (!basicCodeEquals(other)) {
            return false;
        }
        IndexOfStringSetTeddy o = (IndexOfStringSetTeddy) other;
        return maskLength == o.maskLength && patternStride == o.patternStride && fallbackPlan.codeEquals(o.fallbackPlan) &&
                        Arrays.deepEquals(encodedPatterns, o.encodedPatterns) &&
                        Arrays.deepEquals(encodedMasks, o.encodedMasks) &&
                        Arrays.equals(patternOriginalIndices, o.patternOriginalIndices) &&
                        Arrays.equals(bucketData, o.bucketData) &&
                        Arrays.equals(fingerprintTables, o.fingerprintTables);
    }

    private static final class Builder {
        private final Encoding encoding;
        private final byte[][] patterns;
        private final byte[][] masks;
        private final int patternStride;
        private final int[] patternOriginalIndices;
        private final int maskLength;
        private final int[] patternBuckets;

        Builder(Encoding encoding, byte[][] patterns, byte[][] masks, int patternStride, int[] patternOriginalIndices, int maskLength) {
            this.encoding = encoding;
            this.patterns = patterns;
            this.masks = masks;
            this.patternStride = patternStride;
            this.patternOriginalIndices = patternOriginalIndices;
            this.maskLength = maskLength;
            this.patternBuckets = assignBuckets();
        }

        IndexOfStringSetTeddy createSearchPlan(int maxCodeRange, int minPatternLength, int maxPatternLength, IndexOfStringSet.SearchPlan fallbackPlan) {
            return new IndexOfStringSetTeddy(maxCodeRange, minPatternLength, maxPatternLength,
                            maskLength, patternStride, fallbackPlan,
                            patterns, masks, patternOriginalIndices,
                            createBucketData(), createFingerprintTables());
        }

        private int[] assignBuckets() {
            int[] buckets = new int[patterns.length];
            EconomicMap<Integer, Integer> signatureToBucket = EconomicMap.create(patterns.length);
            for (int patternId = 0; patternId < patterns.length; patternId++) {
                // Group patterns by the low nibbles of their fingerprint bytes. Patterns with the
                // same signature share a Teddy bucket and therefore share the same candidate bit.
                int signature = lowNibbleSignature(patterns[patternId], patternStride, encoding.isForeignEndian(), maskLength);
                Integer bucket = signatureToBucket.get(signature);
                if (bucket == null) {
                    bucket = (BUCKETS - 1) - (signatureToBucket.size() % BUCKETS);
                    signatureToBucket.put(signature, bucket);
                }
                buckets[patternId] = bucket;
            }
            return buckets;
        }

        private int[] createBucketData() {
            int[] bucketPatternCounts = new int[BUCKETS];
            for (int bucket : patternBuckets) {
                bucketPatternCounts[bucket]++;
            }
            int[] bucketData = new int[BUCKETS + BUCKETS + patterns.length];
            int[] bucketPatternOffsets = new int[BUCKETS];
            int nextRecord = BUCKETS;
            for (int bucket = 0; bucket < BUCKETS; bucket++) {
                bucketData[bucket] = nextRecord;
                bucketData[nextRecord + BUCKET_FIELD_PATTERN_COUNT] = bucketPatternCounts[bucket];
                bucketPatternOffsets[bucket] = nextRecord + BUCKET_FIELD_PATTERNS;
                nextRecord += BUCKET_FIELD_PATTERNS + bucketPatternCounts[bucket];
            }
            for (int patternId : sortedPatternIdsByPatternIndex()) {
                bucketData[bucketPatternOffsets[patternBuckets[patternId]]++] = patternId;
            }
            return bucketData;
        }

        private int[] sortedPatternIdsByPatternIndex() {
            int[] patternIds = new int[patterns.length];
            for (int i = 0; i < patternIds.length; i++) {
                patternIds[i] = i;
            }
            for (int i = 1; i < patternIds.length; i++) {
                int patternId = patternIds[i];
                int j = i;
                while (j > 0 && patternOriginalIndices[patternId] < patternOriginalIndices[patternIds[j - 1]]) {
                    patternIds[j] = patternIds[j - 1];
                    j--;
                }
                patternIds[j] = patternId;
            }
            return patternIds;
        }

        private byte[] createFingerprintTables() {
            byte[] ret = new byte[maskLength * TABLE_BYTES];
            for (int patternId = 0; patternId < patterns.length; patternId++) {
                int bucketBit = 1 << patternBuckets[patternId];
                for (int i = 0; i < maskLength; i++) {
                    addFingerprintEntry(ret, patternId, bucketBit, i);
                }
            }
            return ret;
        }

        private void addFingerprintEntry(byte[] fingerprintTables, int patternId, int bucketBit, int index) {
            int tableOffset = index * TABLE_BYTES;
            int value = IndexOfStringSet.readValue(patterns[patternId], patternStride, encoding.isForeignEndian(), index);
            addFingerprintValue(fingerprintTables, tableOffset, bucketBit, value);
            if (masks[patternId] != null) {
                int mask = IndexOfStringSet.readValue(masks[patternId], patternStride, encoding.isForeignEndian(), index);
                if (mask != 0) {
                    addFingerprintValue(fingerprintTables, tableOffset, bucketBit, value ^ mask);
                }
            }
        }
    }

    private static int lowNibbleSignature(byte[] pattern, int patternStride, boolean foreignEndian, int maskLength) {
        int signature = 0;
        for (int i = 0; i < maskLength; i++) {
            signature |= (IndexOfStringSet.readValue(pattern, patternStride, foreignEndian, i) & 0xf) << (i << 2);
        }
        return signature;
    }

    private static void addFingerprintValue(byte[] fingerprintTables, int tableOffset, int bucketBit, int value) {
        fingerprintTables[tableOffset + (value >>> 4)] |= (byte) bucketBit;
        fingerprintTables[tableOffset + TABLE_SIZE + (value & 0xf)] |= (byte) bucketBit;
    }
}
