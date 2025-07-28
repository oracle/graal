/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.nodes.dfa;

import static com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import static com.oracle.truffle.api.CompilerDirectives.UNLIKELY_PROBABILITY;
import static com.oracle.truffle.api.CompilerDirectives.injectBranchProbability;

import java.util.Arrays;
import java.util.PrimitiveIterator;
import java.util.StringJoiner;
import java.util.function.IntConsumer;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.regex.tregex.automaton.TransitionOp;
import com.oracle.truffle.regex.tregex.buffer.IntArrayBuffer;
import com.oracle.truffle.regex.util.BitSets;

/**
 * Counter trackers backed by a bitset and an offset value. Every 1-bit at a position {@code i} in
 * the bitset represents the counter tracker value {@code offset - i}. The values are calculated by
 * subtraction because the set needs to support efficient insertion of new 1-values, which wouldn't
 * be possible if e.g. values were calculated as {@code offset + i}.
 * <p>
 * To track a maximum of {@code n} sets in parallel, this counter tracker allocates the following
 * data inside {@code fixedData}, starting at {@code fixedOffset} (shown in pseudo-java):
 *
 * <code>
 * <pre>
 * class BitSetWithOffset {
 *     // position of first 1-bit in bitset
 *     short minBit;
 *     // position of last 1-bit in bitset
 *     short maxBit;
 *     int offset;
 *     long[] bitset;
 * }
 *
 * // set <-> stateID mapping table.
 * // maps every stateID from 0 to n to a set's position in bitsets
 * long[] stateIDMap = new long[n];
 *
 * BitSetWithOffset[] bitsets = new BitSetWithOffset[n];
 * for (int i = 0; i < n; i++) {
 *     bitsets[i] = new BitSetWithOffset(0, 0, 0, new long[BitSets.requiredArraySize(quantifierUpperBound + 64)]);
 * }
 * </pre>
 * </code>
 *
 * {@code minBit} and {@code maxBit} are tracked because they allow
 * {@link #anyLtMin(int, long[], int[][]) anyLtMin}, {@link #anyGeMin(int, long[], int[][])
 * anyGeMin} and {@link #anyLtMax(int, long[], int[][]) anyLtMax} to be evaluated in constant time.
 * <p>
 * Each bitset is sized to hold all values from 0 to the quantifier's upper bound, but the tracker
 * must also be able to insert and increment values after the upper bound has already been reached,
 * while dropping values greater than the upper bound from the set. A naive solution to this would
 * be to shift the entire bitset by one bit every time an {@code inc}-operation would result in a
 * value greater than the upper bound, but that is too expensive for larger bitsets. Instead, we
 * over-allocate each bitset by 64 bits, and shift by 64 bits once an {@code inc}-operation would
 * yield a value greater than upper bound + 64. Values between upper bound and upper bound + 64 are
 * ignored by adjusting {@code minBit} after {@code inc}-operations.
 */
public class CounterTrackerBitSetWithOffset extends CounterTracker {

    public static final int MAX_BITSET_SIZE = 16;

    private static final int N_FIELDS = 1;
    @CompilationFinal(dimensions = 1) private final long[] set1Template;
    @CompilationFinal(dimensions = 1) private final long[] initTemplate;

    private final int bitsetLength;
    private final int min;
    private final int max;
    private final int upperBound;

    private final int fixedOffset;

    protected CounterTrackerBitSetWithOffset(int min, int max, int numberOfSets, CounterTrackerData.Builder dataBuilder) {
        this.min = min;
        this.max = max;
        this.upperBound = max == -1 ? this.min : this.max;
        assert upperBound + 64 <= 0xffff;
        this.bitsetLength = BitSets.requiredArraySize(upperBound + 64);
        set1Template = new long[N_FIELDS + bitsetLength];
        set1Template[0] = setFields(0, 0, 1);
        set1Template[N_FIELDS] = 1;

        int size = (bitsetLength + N_FIELDS + 1) * numberOfSets;
        this.fixedOffset = dataBuilder.getFixedDataSize();
        dataBuilder.requestFixedSize(size);
        initTemplate = new long[numberOfSets];
        for (int i = 0; i < numberOfSets; i++) {
            initTemplate[i] = (fixedOffset + numberOfSets) + ((long) i * (bitsetLength + N_FIELDS));
        }
    }

    @Override
    public void init(long[] fixed, int[][] intArrays) {
        System.arraycopy(initTemplate, 0, fixed, fixedOffset, initTemplate.length);
    }

    /**
     * Returns the location of the bitset for that id.
     */
    private int mapId(int sId, long[] fixedData) {
        return (int) fixedData[fixedOffset + sId];
    }

    private static int getOffset(long fields) {
        return (int) fields;
    }

    private static int getMinBit(long fields) {
        return (int) (fields >>> 32) & 0xffff;
    }

    private static int getMaxBit(long fields) {
        return (int) (fields >>> 48);
    }

    private static long setFields(int minBit, int maxBit, int offset) {
        return (((long) maxBit) << 48) | (((long) minBit) << 32) | offset;
    }

    @Override
    protected boolean anyLtMin(int sId, long[] fixedData, int[][] intArrays) {
        int mapId = mapId(sId, fixedData);
        long fields = fixedData[mapId];
        return getOffset(fields) - getMaxBit(fields) < min;
    }

    @Override
    protected boolean anyLtMax(int sId, long[] fixedData, int[][] intArrays) {
        int mapId = mapId(sId, fixedData);
        long fields = fixedData[mapId];
        return max == -1 || getOffset(fields) - getMaxBit(fields) < max;
    }

    @Override
    protected boolean anyGeMin(int sId, long[] fixedData, int[][] intArrays) {
        int mapId = mapId(sId, fixedData);
        long fields = fixedData[mapId];
        return getOffset(fields) - getMinBit(fields) >= min;
    }

    @ExplodeLoop
    protected void set1(int idDst, int modifier, long[] fixedData) {
        CompilerAsserts.partialEvaluationConstant(modifier);
        int dst = mapId(idDst, fixedData);
        if (modifier == TransitionOp.overwrite) {
            System.arraycopy(set1Template, 0, fixedData, dst, set1Template.length);
        } else {
            assert modifier == TransitionOp.union;
            // set bit at bitset index (offset - 1)
            long fields = fixedData[dst];
            // if offset is still zero, the bitset is empty. in this case we initialize it to 1
            assert getOffset(fields) >= 1 || !iterator(fixedData, idDst).hasNext();
            int offset = Math.max(1, getOffset(fields));
            int bitIndex = offset - 1;
            int wordIndex = bitIndex >>> 6;
            assert wordIndex < bitsetLength;
            fixedData[dst + N_FIELDS + wordIndex] |= 1L << (bitIndex & 63);
            fixedData[dst] = setFields(getMinBit(fields), bitIndex, offset);
        }
    }

    protected void inc(int idSrc, int idDst, int modifier, long[] fixedData) {
        CompilerAsserts.partialEvaluationConstant(modifier);
        CompilerAsserts.partialEvaluationConstant(idSrc);
        CompilerAsserts.partialEvaluationConstant(idDst);
        final boolean isSelfUpdate = idSrc == idDst;
        CompilerAsserts.partialEvaluationConstant(isSelfUpdate);
        int src = mapId(idSrc, fixedData);
        int dst = mapId(idDst, fixedData);
        long fieldsSrc = fixedData[src];
        int offsetSrc = getOffset(fieldsSrc);
        int minBitSrc = getMinBit(fieldsSrc);
        int maxBitSrc = getMaxBit(fieldsSrc);
        // save minBitDst before it potentially gets overwritten by recalculateMinBit on a
        // self-update. We need the original value for incUnion.
        long fieldsDst = fixedData[dst];
        int minBitDst = getMinBit(fieldsDst);
        int copyIndexSrc = src;
        int copyLengthSrc = bitsetLength;

        if (injectBranchProbability(UNLIKELY_PROBABILITY, offsetSrc >= upperBound + 63)) {
            // offsetSrc would grow larger than (max + 63), which would cause subsequent set1
            // operations to go out of bounds of the bitset. To avoid this, we shift the bitset by
            // one chunk, so the first chunk is removed. This is correct, because all values in the
            // first chunk are guaranteed to be larger than max and therefore should be ignored.
            copyIndexSrc++;
            copyLengthSrc--;
            if (modifier == TransitionOp.overwrite) {
                if (isSelfUpdate) {
                    copy(fixedData, copyIndexSrc + N_FIELDS, dst + N_FIELDS, copyLengthSrc);
                    if (max == -1) {
                        fixedData[dst + N_FIELDS] |= 1;
                    }
                }
                fixedData[dst + N_FIELDS + bitsetLength - 1] = 0;
            }
            if (max == -1) {
                minBitSrc = 0;
            } else {
                // a negative result in minBit will always trigger recalculateMinBit below
                minBitSrc -= 64;
            }
            maxBitSrc -= 64;
            if (maxBitSrc < 0) {
                // maxBit was in the chunk we just shifted out, reset it
                maxBitSrc = 0;
            }
            offsetSrc -= 63;
        } else {
            offsetSrc++;
        }
        if (max != -1) {
            if (injectBranchProbability(UNLIKELY_PROBABILITY, offsetSrc - minBitSrc > max)) {
                if ((modifier == TransitionOp.overwrite) && isSelfUpdate) {
                    minBitSrc = recalculateMinBit(fixedData, dst, bitsetLength, offsetSrc);
                } else {
                    minBitSrc = recalculateMinBit(fixedData, copyIndexSrc, copyLengthSrc, offsetSrc);
                }
            }
        }

        if (modifier == TransitionOp.overwrite) {
            fixedData[dst] = setFields(minBitSrc, maxBitSrc, offsetSrc);
            if (!isSelfUpdate) {
                copy(fixedData, copyIndexSrc + N_FIELDS, dst + N_FIELDS, copyLengthSrc);
                if (max == -1 && copyLengthSrc == bitsetLength - 1) {
                    fixedData[dst + N_FIELDS] |= 1;
                }
            }
        } else {
            assert modifier == TransitionOp.union;
            incUnion(fixedData, dst, offsetSrc, minBitSrc, maxBitSrc, minBitDst, copyIndexSrc, copyLengthSrc, isSelfUpdate);
        }
    }

    /**
     * Analogous to {@link #maintainUnion}, but in this variant {@code src} may also be shifted at
     * the same time, because we may have hit the {@code (max + 64)} shift threshold.
     */
    @ExplodeLoop
    private void incUnion(long[] fixedData, int dstArg, int offsetSrc, int minBitSrcArg, int maxBitSrcArg, int minBitDstArg, int copyIndexSrc, int copyLengthSrc, boolean isSelfUpdate) {
        int minBitSrc = minBitSrcArg;
        int maxBitSrc = maxBitSrcArg;
        int offsetDst;
        int minBitDst = minBitDstArg;
        int maxBitDst;
        int dst;
        int lengthDst;
        if (isSelfUpdate) {
            offsetDst = offsetSrc - 1;
            maxBitDst = maxBitSrcArg;
            dst = copyIndexSrc;
            lengthDst = copyLengthSrc;
        } else {
            long fieldsDst = fixedData[dstArg];
            offsetDst = getOffset(fieldsDst);
            maxBitDst = getMaxBit(fieldsDst);
            dst = dstArg;
            lengthDst = bitsetLength;
        }
        final int smallerSet;
        final int smallerLength;
        final int biggerSet;
        final int biggerLength;
        final int delta;
        final int offsetMax;
        if (offsetSrc < offsetDst) {
            smallerSet = copyIndexSrc;
            smallerLength = copyLengthSrc;
            biggerSet = dst;
            biggerLength = lengthDst;
            delta = offsetDst - offsetSrc;
            offsetMax = offsetDst;
            minBitSrc += delta;
            maxBitSrc += delta;
        } else {
            smallerSet = dst;
            smallerLength = lengthDst;
            biggerSet = copyIndexSrc;
            biggerLength = copyLengthSrc;
            delta = offsetSrc - offsetDst;
            offsetMax = offsetSrc;
            minBitDst += delta;
            maxBitDst += delta;
        }
        fixedData[dstArg] = setFields(Math.min(minBitSrc, minBitDst), Math.max(maxBitSrc, maxBitDst), offsetMax);
        int iSmallerSet = (smallerLength - 1) - (delta >>> 6);
        assert iSmallerSet >= 0;
        int shiftAmountHi = delta & 63;
        int shiftAmountLo = 64 - shiftAmountHi;
        // corner case: if the delta is a multiple of 64, we don't need the lower shifted part
        long shiftLoMask = shiftAmountHi == 0 ? 0 : ~0L;
        long bitSetChunkSmallerHi = fixedData[smallerSet + N_FIELDS + iSmallerSet--];
        assert biggerLength == bitsetLength || biggerLength == bitsetLength - 1;
        if (biggerLength == bitsetLength) {
            long bitSetChunkBigger = fixedData[biggerSet + N_FIELDS + bitsetLength - 1];
            long bitSetChunkSmallerLo = iSmallerSet < 0 ? 0 : fixedData[smallerSet + N_FIELDS + iSmallerSet--];
            long union = bitSetChunkBigger | ((bitSetChunkSmallerLo & shiftLoMask) >>> shiftAmountLo) | (bitSetChunkSmallerHi << shiftAmountHi);
            fixedData[dstArg + N_FIELDS + bitsetLength - 1] = union;
            bitSetChunkSmallerHi = bitSetChunkSmallerLo;
        }
        for (int i = bitsetLength - 2; i >= 0; i--) {
            long bitSetChunkBigger = fixedData[biggerSet + N_FIELDS + i];
            long bitSetChunkSmallerLo = iSmallerSet < 0 ? 0 : fixedData[smallerSet + N_FIELDS + iSmallerSet--];
            long union = bitSetChunkBigger | ((bitSetChunkSmallerLo & shiftLoMask) >>> shiftAmountLo) | (bitSetChunkSmallerHi << shiftAmountHi);
            fixedData[dstArg + N_FIELDS + i] = union;
            bitSetChunkSmallerHi = bitSetChunkSmallerLo;
        }
        if (lengthDst == bitsetLength - 1) {
            if (max == -1) {
                fixedData[dstArg + N_FIELDS] |= 1;
            }
            if (isSelfUpdate) {
                fixedData[dstArg + N_FIELDS + bitsetLength - 1] = 0;
            }
        }
    }

    /**
     * Called by {@link #inc} when the new largest value in the set would be larger than
     * {@code max}. In this case, {@link #getMinBit(long) minBit} must be re-calculated so it points
     * to the next entry in the set that is less than or equal to {@code max}.
     * <p>
     * Ideally, this should be outlined into a stub.
     */
    private int recalculateMinBit(long[] fixedData, int src, int srcLength, int offsetSrc) {
        assert srcLength <= bitsetLength;
        long bitSetChunk = fixedData[src + N_FIELDS];
        int delta = offsetSrc - max;
        // the bitset is shifted via System.arraycopy any time the offset would get larger than
        // max + 64, so the delta is always between 0 and 64.
        assert delta >= 0 && delta < 64 : delta;
        // shift out invalid entries in the first bitset chunk. This effectively removes all entries
        // representing values larger than max.
        bitSetChunk >>>= delta;
        bitSetChunk <<= delta;
        // find the next set bit.
        int i = 1;
        while (i < srcLength && bitSetChunk == 0) {
            bitSetChunk = fixedData[src + N_FIELDS + i];
            i++;
        }
        return ((i - 1) << 6) + Long.numberOfTrailingZeros(bitSetChunk);
    }

    private static void copy(long[] fixedData, int src, int dst, int length) {
        System.arraycopy(fixedData, src, fixedData, dst, length);
    }

    protected void maintain(int idSrc, int idDst, int modifier, long[] fixedData) {
        CompilerAsserts.partialEvaluationConstant(modifier);
        if (idSrc == idDst) {
            return;
        }
        if (modifier == TransitionOp.swap) {
            swapIndices(idSrc, idDst, fixedData);
        } else {
            int src = mapId(idSrc, fixedData);
            int dst = mapId(idDst, fixedData);
            if (modifier == TransitionOp.overwrite) {
                copy(fixedData, src, dst, bitsetLength + N_FIELDS);
            } else {
                maintainUnion(fixedData, src, dst);
            }
        }
    }

    private void swapIndices(int idA, int idB, long[] fixedData) {
        var tmp = fixedData[fixedOffset + idA];
        fixedData[fixedOffset + idA] = fixedData[fixedOffset + idB];
        fixedData[fixedOffset + idB] = tmp;
    }

    /**
     * Stores the union of the bitsets at {@code src} and {@code dst} into {@code dst}.
     */
    @ExplodeLoop
    private void maintainUnion(long[] fixedData, int src, int dst) {
        long fieldsSrc = fixedData[src];
        long fieldsDst = fixedData[dst];
        int offsetSrc = getOffset(fieldsSrc);
        int offsetDst = getOffset(fieldsDst);
        int minBitSrc = getMinBit(fieldsSrc);
        int minBitDst = getMinBit(fieldsDst);
        int maxBitSrc = getMaxBit(fieldsSrc);
        int maxBitDst = getMaxBit(fieldsDst);
        final int smallerSet;
        final int biggerSet;
        final int delta;
        final int offsetMax;
        if (offsetSrc < offsetDst) {
            smallerSet = src;
            biggerSet = dst;
            delta = offsetDst - offsetSrc;
            offsetMax = offsetDst;
            minBitSrc += delta;
            maxBitSrc += delta;
        } else {
            smallerSet = dst;
            biggerSet = src;
            delta = offsetSrc - offsetDst;
            offsetMax = offsetSrc;
            minBitDst += delta;
            maxBitDst += delta;
        }
        fixedData[dst] = setFields(Math.min(minBitSrc, minBitDst), Math.max(maxBitSrc, maxBitDst), offsetMax);
        // since the meaning of a bitset entry depends on the offset value, we have to left-shift
        // the bitset with the smaller offset by the difference between the offsets before creating
        // the union.
        int iSmallerSet = (bitsetLength - 1) - (delta >>> 6);
        assert iSmallerSet >= 0;
        int shiftAmountHi = delta & 63;
        int shiftAmountLo = 64 - shiftAmountHi;
        // corner case: if the delta is a multiple of 64, we don't need the lower shifted part
        long shiftLoMask = shiftAmountHi == 0 ? 0 : ~0L;
        long bitSetChunkSmallerHi = fixedData[smallerSet + N_FIELDS + iSmallerSet--];
        // we have to start writing at the end of the array, otherwise we may overwrite chunks of
        // the smaller set before reading them
        for (int i = bitsetLength - 1; i >= 0; i--) {
            long bitSetChunkBigger = fixedData[biggerSet + N_FIELDS + i];
            long bitSetChunkSmallerLo = iSmallerSet < 0 ? 0 : fixedData[smallerSet + N_FIELDS + iSmallerSet--];
            long union = bitSetChunkBigger | ((bitSetChunkSmallerLo & shiftLoMask) >>> shiftAmountLo) | (bitSetChunkSmallerHi << shiftAmountHi);
            fixedData[dst + N_FIELDS + i] = union;
            bitSetChunkSmallerHi = bitSetChunkSmallerLo;
        }
    }

    @Override
    public void apply(long op, long[] fixedData, int[][] intArrays) {
        CompilerAsserts.partialEvaluationConstant(op);
        int dst = TransitionOp.getTarget(op);
        int kind = TransitionOp.getKind(op);
        int modifier = TransitionOp.getModifier(op);
        CompilerAsserts.partialEvaluationConstant(dst);
        CompilerAsserts.partialEvaluationConstant(kind);
        CompilerAsserts.partialEvaluationConstant(modifier);
        switch (kind) {
            case TransitionOp.set1 -> {
                set1(dst, modifier, fixedData);
            }
            case TransitionOp.inc -> {
                int src = TransitionOp.getSource(op);
                inc(src, dst, modifier, fixedData);
            }
            case TransitionOp.maintain -> {
                int src = TransitionOp.getSource(op);
                maintain(src, dst, modifier, fixedData);
            }
        }
        assert checkConsistency(fixedData, dst);
    }

    @TruffleBoundary
    private boolean checkConsistency(long[] fixedData, int idDst) {
        int dst = mapId(idDst, fixedData);
        long fieldsDst = fixedData[dst];
        int offsetDst = getOffset(fieldsDst);
        int minBitDst = getMinBit(fieldsDst);
        int maxBitDst = getMaxBit(fieldsDst);

        int actualMinBit = Integer.MAX_VALUE;
        int actualMaxBit = 0;
        long firstChunk = fixedData[dst + N_FIELDS];
        if (offsetDst > upperBound) {
            int delta = offsetDst - upperBound;
            assert delta >= 0 && delta < 64 : delta;
            firstChunk >>>= delta;
            firstChunk <<= delta;
        }
        if (firstChunk != 0) {
            actualMinBit = Math.min(actualMinBit, Long.numberOfTrailingZeros(firstChunk));
            actualMaxBit = Math.max(actualMaxBit, (63 - Long.numberOfLeadingZeros(firstChunk)));
        }
        for (int i = 1; i < bitsetLength; i++) {
            long bitSetChunk = fixedData[dst + N_FIELDS + i];
            if (bitSetChunk != 0) {
                actualMinBit = Math.min(actualMinBit, (i << 6) + Long.numberOfTrailingZeros(bitSetChunk));
                actualMaxBit = Math.max(actualMaxBit, (i << 6) + (63 - Long.numberOfLeadingZeros(bitSetChunk)));
            }
        }
        if (actualMinBit == Integer.MAX_VALUE) {
            actualMinBit = 0;
        }
        if (actualMinBit == bitsetLength << 6 && minBitDst == 0 || minBitDst == bitsetLength << 6 && actualMinBit == 0) {
            actualMinBit = minBitDst;
        }
        assert minBitDst == actualMinBit && maxBitDst == actualMaxBit : String.format("actualMinBit: %d, actualMaxBit: %d, %s", actualMinBit, actualMaxBit, bitSetDataToString(fixedData, idDst));
        return true;
    }

    @Override
    public boolean support(long operation) {
        return true;
    }

    /**
     * For debugging purposes only.
     */
    @TruffleBoundary
    @SuppressWarnings("unused")
    String valuesToString(long[] fixedData, int stateId) {
        StringJoiner stringJoiner = new StringJoiner(", ", "[", "]");
        iterator(fixedData, stateId).forEachRemaining((IntConsumer) i -> stringJoiner.add(Integer.toString(i)));
        return stringJoiner.toString();
    }

    /**
     * For debugging purposes only.
     */
    @TruffleBoundary
    String bitSetDataToString(long[] fixedData, int stateId) {
        int mapId = mapId(stateId, fixedData);
        long fields = fixedData[mapId];
        int offset = getOffset(fields);
        int minBit = getMinBit(fields);
        int maxBit = getMaxBit(fields);
        assert bitsetLength > 0;
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("offset: %3d, minBit: %3d, maxBit: %3d, bits: [0x%016x", offset, minBit, maxBit, fixedData[mapId + N_FIELDS]));
        for (int i = 1; i < bitsetLength; i++) {
            sb.append(String.format(", 0x%016x", fixedData[mapId + N_FIELDS + i]));
        }
        return sb.append("], values: ").append(valuesToString(fixedData, stateId)).toString();
    }

    /**
     * For debugging purposes only.
     */
    @TruffleBoundary
    int[] getValues(long[] fixedData, int stateId) {
        IntArrayBuffer buf = new IntArrayBuffer();
        PrimitiveIterator.OfInt iterator = iterator(fixedData, stateId);
        while (iterator.hasNext()) {
            int next = iterator.next();
            if (next <= max) {
                buf.add(next);
            }
        }
        return buf.toArray();
    }

    /**
     * For debugging purposes only.
     */
    @TruffleBoundary
    PrimitiveIterator.OfInt iterator(long[] fixedData, int stateId) {
        int mapId = mapId(stateId, fixedData);
        long[] bitset = Arrays.copyOfRange(fixedData, mapId + N_FIELDS, mapId + N_FIELDS + bitsetLength);
        return new AbstractCounterTrackerBitSetWithOffsetIterator(BitSets.iterator(bitset), getOffset(fixedData[mapId]));
    }

    /**
     * For debugging purposes only.
     */
    private static final class AbstractCounterTrackerBitSetWithOffsetIterator implements PrimitiveIterator.OfInt {

        private final PrimitiveIterator.OfInt bitSetIterator;
        private final int offset;

        private AbstractCounterTrackerBitSetWithOffsetIterator(PrimitiveIterator.OfInt bitSetIterator, int offset) {
            this.bitSetIterator = bitSetIterator;
            this.offset = offset;
        }

        @Override
        public int nextInt() {
            return (offset - bitSetIterator.nextInt());
        }

        @Override
        public boolean hasNext() {
            return bitSetIterator.hasNext();
        }
    }

    @Override
    public String dumpState(int sId, long[] fixedData, int[][] intArrays) {
        return "Bitset " + bitSetDataToString(fixedData, sId);
    }
}
