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

import java.util.Arrays;
import java.util.PrimitiveIterator;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.regex.tregex.automaton.TransitionOp;
import com.oracle.truffle.regex.util.BitSets;

/**
 * Counter Tracker backed by a bitset using a single long per bitset. It can therefore only be used
 * with counter whose maximum value is less than 64. All operations are efficient.
 */
public final class CounterTrackerLong extends CounterTracker {
    private final int min;
    private final int max;
    private final long maskLtMax;
    private final long maskGeMin;
    private final long maskLtMin;
    private final long saturateMinMask;
    private final int fixedOffset;

    public CounterTrackerLong(int min, int max, int numberOfCells, CounterTrackerData.Builder dataBuilder) {
        this.min = min;
        this.max = max;
        this.maskLtMax = BitSets.getRange(0, max - 2);
        // Note, if min = 0 then this mask makes no sense.
        // However, if min = 0 then we don't generate exit guards.
        this.maskGeMin = BitSets.getRange(min - 1, Math.max(min, max) - 1);
        this.maskLtMin = min > 1 ? BitSets.getRange(0, min - 2) : 0;
        this.fixedOffset = dataBuilder.getFixedDataSize();
        if (max == -1) {
            saturateMinMask = 1L << (min - 1);
        } else {
            saturateMinMask = 0;
        }
        dataBuilder.requestFixedSize(numberOfCells);
    }

    @Override
    @ExplodeLoop
    public void apply(long op, long[] data, int[][] intArrays) {
        CompilerAsserts.partialEvaluationConstant(op);
        int dst = mapId(TransitionOp.getTarget(op));
        int kind = TransitionOp.getKind(op);
        int modifier = TransitionOp.getModifier(op);
        if (kind == TransitionOp.set1) {
            if (modifier == TransitionOp.overwrite) {
                data[dst] = 1;
            } else {
                assert modifier == TransitionOp.union;
                data[dst] |= 1;
            }
        } else {
            int src = mapId(TransitionOp.getSource(op));
            long bits = data[src];
            switch (kind) {
                case TransitionOp.inc -> {
                    long shifted = max == -1 ? (bits << 1) | (bits & saturateMinMask) : (bits << 1);
                    if (modifier == TransitionOp.overwrite) {
                        data[dst] = shifted;
                    } else {
                        assert modifier == TransitionOp.union;
                        data[dst] |= shifted;
                    }
                }
                case TransitionOp.maintain -> {
                    if (modifier == TransitionOp.swap) {
                        data[src] = data[dst];
                        data[dst] = bits;
                    } else if (modifier == TransitionOp.overwrite) {
                        data[dst] = bits;
                    } else {
                        data[dst] |= bits;
                    }
                }
            }
        }
    }

    @Override
    public void init(long[] fixedData, int[][] intArrays) {
    }

    @Override
    public boolean support(long operation) {
        return true;
    }

    private int mapId(int sId) {
        return sId + fixedOffset;
    }

    @Override
    protected boolean anyLtMax(int sId, long[] fixedData, int[][] intArrays) {
        if (max == -1) {
            return true;
        }
        return intersect(sId, fixedData, maskLtMax);
    }

    @Override
    protected boolean anyGeMin(int sId, long[] fixedData, int[][] intArrays) {
        assert min != 0;
        return intersect(sId, fixedData, maskGeMin);
    }

    @Override
    protected boolean anyLtMin(int sId, long[] fixedData, int[][] intArrays) {
        assert min != 0;
        return intersect(sId, fixedData, maskLtMin);
    }

    private boolean intersect(int sId, long[] fixedData, long mask) {
        return (fixedData[mapId(sId)] & mask) != 0;
    }

    @Override
    public String dumpState(int sId, long[] fixedData, int[][] intArrays) {
        long[] bs = {fixedData[mapId(sId)] & BitSets.getRange(0, max - 1)};
        int[] values = new int[BitSets.size(bs)];
        PrimitiveIterator.OfInt it = BitSets.iterator(bs);
        for (int i = values.length - 1; i >= 0; i--) {
            values[i] = it.nextInt() + 1;
        }
        return "BitsetLong, current values: " + Arrays.toString(values);
    }
}
