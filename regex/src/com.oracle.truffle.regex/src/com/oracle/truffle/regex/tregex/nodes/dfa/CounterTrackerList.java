/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere;

import java.util.Arrays;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.regex.RegexOptions;
import com.oracle.truffle.regex.tregex.TRegexOptions;
import com.oracle.truffle.regex.tregex.automaton.TransitionOp;

/**
 * This implementation of {@link CounterTracker} is used for quantifiers with very large bounds. It
 * implements the counter value set with a growable {@code int}-buffer holding a sorted list of
 * non-negative integers, which together with an offset encode the values held by the set. Each set
 * has four additional fields:
 * <ul>
 * <li>{@code buffer}: the index which identifies the {@code int[]} in
 * {@link CounterTrackerData#intArrays()} to be used as a backing buffer for the sorted list.</li>
 * <li>{@code start}: the offset into the {@code int[]}-buffer
 * {@code CounterTrackerData.intArrays[buffer]} at which the sorted list starts.</li>
 * <li>{@code size}: the length of the sorted list.</li>
 * <li>{@code offset}: the offset from which the values of the sorted list are subtracted to
 * retrieve the encoded possible counter value.</li>
 * </ul>
 * Every {@link TransitionOp#set1} operation adds a new entry to the list of values, which
 * consequently can grow up to {@link #max} values in size. Therefore,
 * {@link RegexOptions#ForceLinearExecution} will cause a bailout if this counter tracker
 * implementation is used, and the potential memory consumption would be greater than
 * {@link TRegexOptions#TRegexMaxCounterTrackerMemoryConsumptionInForceLinearExecutionMode}.
 */
public class CounterTrackerList extends CounterTracker {

    /**
     * Maximum space to leave empty in a set's buffer before reclaiming it via a full array copy.
     */
    private static final int MAX_UNUSED_BUFFER_SPACE = 512;

    private final int fixedOffset;
    private final int min;
    private final int max;
    private final int numberOfCells;
    @CompilationFinal(dimensions = 1) private final long[] initTemplate;

    public CounterTrackerList(int min, int max, int numberOfCells, CounterTrackerData.Builder dataBuilder) {
        this.min = min;
        this.max = max;
        this.fixedOffset = dataBuilder.getFixedDataSize();
        dataBuilder.requestFixedSize(Field.values().length * numberOfCells);
        int arrayOffset = dataBuilder.getNumberOfIntArrays();
        dataBuilder.requestIntArrays(numberOfCells);
        this.numberOfCells = numberOfCells;
        initTemplate = new long[numberOfCells * Field.values().length];
        for (int i = 0; i < numberOfCells; i++) {
            setField(i, initTemplate, 0, Field.offset, 1);
            setField(i, initTemplate, 0, Field.size, 0);
            setField(i, initTemplate, 0, Field.start, 0);
            setField(i, initTemplate, 0, Field.buffer, arrayOffset + i);
        }
    }

    enum Field {
        start,
        size,
        offset,
        buffer,
    }

    private int getField(int sId, long[] fixedData, Field field) {
        assert Field.values().length == 4 : "replace sId << 2 with sId * Field.values().length";
        return (int) fixedData[fixedOffset + (sId << 2) + field.ordinal()];
    }

    private void setField(int sId, long[] fixedData, Field field, int value) {
        setField(sId, fixedData, fixedOffset, field, value);
    }

    private static void setField(int sId, long[] fixedData, int offset, Field field, int value) {
        assert Field.values().length == 4 : "replace sId << 2 with sId * Field.values().length";
        fixedData[offset + (sId << 2) + field.ordinal()] = value;
    }

    private int getStart(int sId, long[] fixedData) {
        return getField(sId, fixedData, Field.start);
    }

    private int getSize(int sId, long[] fixedData) {
        return getField(sId, fixedData, Field.size);
    }

    private int getOffset(int sId, long[] fixedData) {
        return getField(sId, fixedData, Field.offset);
    }

    private int getBufferPointer(int sId, long[] fixedData) {
        return getField(sId, fixedData, Field.buffer);
    }

    private int[] getBuffer(int sId, long[] fixedData, int[][] intArrays) {
        return intArrays[getBufferPointer(sId, fixedData)];
    }

    private void setStart(int sId, long[] fixedData, int value) {
        setField(sId, fixedData, Field.start, value);
    }

    private void setSize(int sId, long[] fixedData, int value) {
        setField(sId, fixedData, Field.size, value);
    }

    private void setOffset(int sId, long[] fixedData, int value) {
        setField(sId, fixedData, Field.offset, value);
    }

    @Override
    protected boolean anyLtMax(int sId, long[] fixedData, int[][] intArrays) {
        assert getSize(sId, fixedData) > 0;
        int offset = getOffset(sId, fixedData);
        if (max == -1 || offset < max) {
            return true;
        }
        return getMinValue(sId, fixedData, intArrays, offset) < max;
    }

    @Override
    protected boolean anyLtMin(int sId, long[] fixedData, int[][] intArrays) {
        assert getSize(sId, fixedData) > 0;
        int offset = getOffset(sId, fixedData);
        if (offset < min) {
            return true;
        }
        return getMinValue(sId, fixedData, intArrays, offset) < min;
    }

    @Override
    protected boolean anyGeMin(int sId, long[] fixedData, int[][] intArrays) {
        assert getSize(sId, fixedData) > 0;
        return getMaxValue(sId, fixedData, intArrays, getOffset(sId, fixedData)) >= min;
    }

    /**
     * Get the maximum value currently contained in the given counter value set.
     */
    private int getMaxValue(int sId, long[] fixedData, int[][] intArrays, int offset) {
        return offset - getBuffer(sId, fixedData, intArrays)[getStart(sId, fixedData)];
    }

    /**
     * Get the minimum value currently contained in the given counter value set.
     */
    private int getMinValue(int sId, long[] fixedData, int[][] intArrays, int offset) {
        return offset - getBuffer(sId, fixedData, intArrays)[getStart(sId, fixedData) + getSize(sId, fixedData) - 1];
    }

    private void clear(int sId, long[] fixedData) {
        setOffset(sId, fixedData, 1);
        setSize(sId, fixedData, 0);
        setStart(sId, fixedData, 0);
    }

    private void copy(int src, int dst, long[] fixedData, int[][] intArrays) {
        CompilerAsserts.partialEvaluationConstant(src);
        CompilerAsserts.partialEvaluationConstant(dst);
        int start = getStart(src, fixedData);
        int size = getSize(src, fixedData);
        setOffset(dst, fixedData, getOffset(src, fixedData));
        setSize(dst, fixedData, size);
        setStart(dst, fixedData, 0);
        int bufferPointerDst = getBufferPointer(dst, fixedData);
        int[] bufDst = intArrays[bufferPointerDst];
        if (CompilerDirectives.injectBranchProbability(UNLIKELY_PROBABILITY, size > bufDst.length)) {
            // round buffer size to nearest power of 2
            bufDst = new int[1 << (32 - Integer.numberOfLeadingZeros(size - 1))];
            intArrays[bufferPointerDst] = bufDst;
        }
        System.arraycopy(getBuffer(src, fixedData, intArrays), start, bufDst, 0, size);
    }

    @Override
    public void apply(long op, long[] fixedData, int[][] intArrays) {
        int dest = TransitionOp.getTarget(op);
        int kind = TransitionOp.getKind(op);
        int modifier = TransitionOp.getModifier(op);
        switch (kind) {
            case TransitionOp.set1 -> {
                if (modifier == TransitionOp.overwrite) {
                    clear(dest, fixedData);
                }
                assert modifier != TransitionOp.swap;
                set1(dest, fixedData, intArrays);
            }
            case TransitionOp.inc -> {
                assert modifier == TransitionOp.overwrite;
                int src = TransitionOp.getSource(op);
                if (src != dest) {
                    copy(src, dest, fixedData, intArrays);
                }
                incAll(dest, fixedData, intArrays);
            }
            case TransitionOp.maintain -> {
                assert modifier == TransitionOp.swap;
                swap(TransitionOp.getSource(op), dest, fixedData);
            }
        }
    }

    @ExplodeLoop
    private void swap(int from, int to, long[] fixedData) {
        if (from == to) {
            return;
        }
        for (Field field : Field.values()) {
            CompilerAsserts.partialEvaluationConstant(field);
            int temp = getField(from, fixedData, field);
            setField(from, fixedData, field, getField(to, fixedData, field));
            setField(to, fixedData, field, temp);
        }
    }

    private void incAll(int sId, long[] fixedData, int[][] intArrays) {
        int[] buffer = getBuffer(sId, fixedData, intArrays);
        int offset = getOffset(sId, fixedData) + 1;
        setOffset(sId, fixedData, offset);

        int start = getStart(sId, fixedData);
        if (max != -1) {
            if (offset - buffer[start] > max) {
                int size = getSize(sId, fixedData);
                removeMaxValue(sId, fixedData, buffer, start, size);
            }
        } else {
            if (offset - buffer[start] > min) {
                buffer[start] = offset - min;
                int size = getSize(sId, fixedData);
                if (size > 1 && buffer[start + 1] == offset - min) {
                    removeMaxValue(sId, fixedData, buffer, start, size);
                }
            }
        }
    }

    /**
     * Remove the largest value in the counter value set.
     */
    private void removeMaxValue(int sId, long[] fixedData, int[] buffer, int start, int size) {
        if (start >= MAX_UNUSED_BUFFER_SPACE) {
            System.arraycopy(buffer, start + 1, buffer, 0, size - 1);
            setStart(sId, fixedData, 0);
        } else {
            setStart(sId, fixedData, start + 1);
        }
        setSize(sId, fixedData, size - 1);
    }

    private void set1(int sId, long[] fixedData, int[][] intArrays) {
        int bufferPointer = getBufferPointer(sId, fixedData);
        int[] buf = intArrays[bufferPointer];
        int start = getStart(sId, fixedData);
        int size = getSize(sId, fixedData);
        int offset = getOffset(sId, fixedData);
        int insert1Value = offset - 1;
        if (size == 0 || buf[start + size - 1] != insert1Value) {
            if (start + size == buf.length) {
                buf = Arrays.copyOf(buf, buf.length << 1);
                intArrays[bufferPointer] = buf;
            }
            buf[start + size] = insert1Value;
            setSize(sId, fixedData, size + 1);
        }
    }

    @TruffleBoundary
    @Override
    public void init(long[] fixedData, int[][] intArrays) {
        System.arraycopy(initTemplate, 0, fixedData, fixedOffset, initTemplate.length);
        for (int i = 0; i < numberOfCells; i++) {
            intArrays[getBufferPointer(i, fixedData)] = new int[128];
        }
    }

    @Override
    public boolean support(long operation) {
        int kind = TransitionOp.getKind(operation);
        int modifier = TransitionOp.getModifier(operation);
        // supported operations are:
        // set1 with overwrite or union modifier
        // all other operations with swap modifier
        assert modifier != TransitionOp.swap || kind == TransitionOp.maintain;
        return switch (kind) {
            case TransitionOp.maintain -> modifier == TransitionOp.swap;
            case TransitionOp.inc -> modifier == TransitionOp.overwrite;
            case TransitionOp.set1 -> true;
            default -> throw shouldNotReachHere();
        };
    }

    @TruffleBoundary
    @Override
    public String dumpState(int sId, long[] fixedData, int[][] intArrays) {
        int start = getStart(sId, fixedData);
        int size = getSize(sId, fixedData);
        int offset = getOffset(sId, fixedData);
        int[] buffer = getBuffer(sId, fixedData, intArrays);
        int[] values = new int[size];
        for (int i = 0; i < size; i++) {
            values[i] = offset - buffer[start + i];
        }
        return String.format("List, start: %d, size: %d, offset: %d, values: %s", start, size, offset, Arrays.toString(values));
    }
}
