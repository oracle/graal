/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.regex.tregex.automaton.TransitionOp.set1;

import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.regex.tregex.automaton.TransitionOp;

public class CounterTrackerList extends CounterTracker {
    private final int fixedOffset;
    private final int arrayOffset;
    private final int min;
    private final int max;
    private final int numberOfCells;

    public CounterTrackerList(int min, int max, int numberOfCells, CounterTrackerData.Builder dataBuilder) {
        this.min = min;
        this.max = max;
        this.fixedOffset = dataBuilder.getFixedDataSize();
        dataBuilder.requestFixedSize(Field.values().length * numberOfCells);
        this.arrayOffset = dataBuilder.getnIntArraySize();
        dataBuilder.requestIntArray(numberOfCells);
        this.numberOfCells = numberOfCells;
    }

    enum Field {
        start,
        size,
        offset,
        index,
    }

    private int getField(int sId, long[] fixedData, Field field) {
        assert Field.values().length == 4 : "replace sId << 2 with sId * Field.values().length";
        return (int) fixedData[fixedOffset + (sId << 2) + field.ordinal()];
    }

    private void setField(int sId, long[] fixedData, Field field, int value) {
        assert Field.values().length == 4 : "replace sId << 2 with sId * Field.values().length";
        fixedData[fixedOffset + (sId << 2) + field.ordinal()] = value;
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

    private int getIndex(int sId, long[] fixedData) {
        return getField(sId, fixedData, Field.index);
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

    private void setIndex(int sId, long[] fixedData, int value) {
        setField(sId, fixedData, Field.index, value);
    }

    @Override
    protected boolean canLoop(int sId, long[] fixedData, int[][] intArrays) {
        if (max == -1) {
            return true;
        }
        int idx = getStart(sId, fixedData) + getSize(sId, fixedData) - 1;
        return getOffset(sId, fixedData) - intArrays[getIndex(sId, fixedData)][idx] < max;
    }

    @Override
    protected boolean ltMin(int sId, long[] fixedData, int[][] intArrays) {
        int idx = getStart(sId, fixedData);
        int size = getSize(sId, fixedData);
        int offset = getOffset(sId, fixedData);
        int[] data = intArrays[getIndex(sId, fixedData)];
        for (int i = idx; i < idx + size; i++) {
            if (offset - data[i] < min) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean canExit(int sId, long[] fixedData, int[][] intArrays) {
        int[] intArray = intArrays[getIndex(sId, fixedData)];
        return getOffset(sId, fixedData) - intArray[getStart(sId, fixedData)] >= min;
    }

    private void clear(int sId, long[] fixedData) {
        setOffset(sId, fixedData, 1);
        setSize(sId, fixedData, 0);
        setStart(sId, fixedData, 0);
    }

    @Override
    public void apply(long op, long[] fixedData, int[][] intArrays) {
        var dest = TransitionOp.getTarget(op);
        var kind = TransitionOp.getKind(op);
        var modifier = TransitionOp.getModifier(op);
        switch (kind) {
            case set1 -> {
                if (modifier == TransitionOp.overwrite || modifier == TransitionOp.move) {
                    clear(dest, fixedData);
                }
                set1(dest, fixedData, intArrays);
            }
            case TransitionOp.setMin -> throw CompilerDirectives.shouldNotReachHere();
            case TransitionOp.inc -> {
                var source = TransitionOp.getSource(op);
                if (modifier == TransitionOp.overwrite || modifier == TransitionOp.union) {
                    throw CompilerDirectives.shouldNotReachHere();
                } else {
                    assert modifier == TransitionOp.move;

                    swap(source, dest, fixedData);
                    incAll(dest, fixedData, intArrays);
                }
            }
            case TransitionOp.maintain -> {
                var source = TransitionOp.getSource(op);
                if (modifier == TransitionOp.overwrite || modifier == TransitionOp.union) {
                    throw CompilerDirectives.shouldNotReachHere();
                } else {
                    assert modifier == TransitionOp.move;

                    swap(source, dest, fixedData);
                }
            }
        }
    }

    private void swap(int from, int to, long[] fixedData) {
        if (from == to) {
            return;
        }
        int temp = getStart(from, fixedData);
        setStart(from, fixedData, getStart(to, fixedData));
        setStart(to, fixedData, temp);

        temp = getSize(from, fixedData);
        setSize(from, fixedData, getSize(to, fixedData));
        setSize(to, fixedData, temp);

        temp = getOffset(from, fixedData);
        setOffset(from, fixedData, getOffset(to, fixedData));
        setOffset(to, fixedData, temp);

        temp = getIndex(from, fixedData);
        setIndex(from, fixedData, getIndex(to, fixedData));
        setIndex(to, fixedData, temp);
    }

    private void ensureCanAlloc1(int sId, long[] fixedData, int[][] intArrays) {
        int idx = getIndex(sId, fixedData);
        if (getStart(sId, fixedData) + getSize(sId, fixedData) == intArrays[idx].length) {
            intArrays[idx] = Arrays.copyOf(intArrays[idx], intArrays[idx].length << 1);
        }
    }

    private void incAll(int sId, long[] fixedData, int[][] intArrays) {
        var intArray = intArrays[getIndex(sId, fixedData)];
        var offset = getOffset(sId, fixedData) + 1;
        setOffset(sId, fixedData, offset);

        var start = getStart(sId, fixedData);
        if (max != -1) {
            if (offset - intArray[start] > max) {
                var size = getSize(sId, fixedData);
                setStart(sId, fixedData, start + 1);
                setSize(sId, fixedData, size - 1);
            }
        } else {
            if (offset - intArray[start] > min) {
                intArray[start] = offset - min;
                var size = getSize(sId, fixedData);
                if (size > 1 && intArray[start + 1] == offset - min) {
                    setStart(sId, fixedData, start + 1);
                    setSize(sId, fixedData, size - 1);
                }
            }
        }
    }

    private void set1(int sId, long[] fixedData, int[][] intArrays) {
        ensureCanAlloc1(sId, fixedData, intArrays);
        var arrayIndex = getIndex(sId, fixedData);

        var size = getSize(sId, fixedData);
        intArrays[arrayIndex][getStart(sId, fixedData) + size] = getOffset(sId, fixedData) - 1;
        setSize(sId, fixedData, size + 1);
    }

    @Override
    public void init(long[] fixedData, int[][] intArrays) {
        for (int i = 0; i < numberOfCells; i++) {
            setOffset(i, fixedData, 1);
            setSize(i, fixedData, 0);
            setStart(i, fixedData, 0);
            setIndex(i, fixedData, arrayOffset + i);
            intArrays[getIndex(i, fixedData)] = new int[128];
        }
    }

    @Override
    public boolean support(long operation) {
        var kind = TransitionOp.getKind(operation);
        var modifier = TransitionOp.getModifier(operation);
        if (kind == TransitionOp.setMin) {
            return false;
        }
        if ((modifier == TransitionOp.overwrite || modifier == TransitionOp.union) && kind != set1) {
            return false;
        }
        return true;
    }
}
