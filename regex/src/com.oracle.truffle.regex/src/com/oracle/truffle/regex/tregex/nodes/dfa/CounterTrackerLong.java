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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.regex.tregex.automaton.TransitionOp;

/**
 * Counter Tracker backed by a bitset using a single long per bitset. It can therefore only be used
 * with counter whose maximum value is less than 64. All operations are efficient.
 */
public final class CounterTrackerLong extends CounterTracker {
    private final int min;
    private final int max;
    private final long canLoopMask;
    private final long canExitMask;
    private final long ltMinMask;
    private final long saturateMinMask;
    private final int fixedOffset;

    public CounterTrackerLong(int min, int max, int numberOfCells, CounterTrackerData.Builder dataBuilder) {
        this.min = min;
        this.max = max;
        this.canLoopMask = setAllTo(max - 1);
        // Note, if min = 0 then this mask makes no sense.
        // However, if min = 0 then we don't generate exit guards.
        this.canExitMask = setAllFrom(min) & setAllTo(Math.max(min, max));
        this.ltMinMask = setAllTo(min - 1);
        this.fixedOffset = dataBuilder.getFixedDataSize();
        if (max == -1) {
            saturateMinMask = 1L << (min - 1);
        } else {
            saturateMinMask = 0;
        }
        dataBuilder.requestFixedSize(numberOfCells);
    }

    private static long setAllTo(int n) {
        if (n == 0) {
            return 0;
        }
        return (~0L) >>> (64 - n);
    }

    private static long setAllFrom(int n) {
        return ((~0L) << (n - 1));
    }

    @Override
    @ExplodeLoop
    public void apply(long op, long[] data, int[][] intArrays) {
        CompilerAsserts.partialEvaluationConstant(op);
        int dest = mapId(TransitionOp.getTarget(op));
        int kind = TransitionOp.getKind(op);
        int modifier = TransitionOp.getModifier(op);
        switch (kind) {
            case TransitionOp.set1 -> {
                if (modifier == TransitionOp.overwrite || modifier == TransitionOp.move) {
                    data[dest] = 1;
                } else {
                    data[dest] |= 1;
                }
            }
            case TransitionOp.setMin -> {
                var source = fixedOffset + TransitionOp.getSource(op);
                // Note: the +1 is because setMin also increments the counters, therefore it sets
                // all value from currMin + 1 to min + 1.
                long rangeFromCurrMinToTop = data[source] == 0 ? (~0L) : ((~0L) << (Long.numberOfTrailingZeros(data[source]) + 1));
                long rangeFromMinToBottom = setAllTo(min + 1);
                var range = rangeFromCurrMinToTop & rangeFromMinToBottom;
                if (modifier == TransitionOp.overwrite || modifier == TransitionOp.move) {
                    data[dest] = range;
                } else {
                    data[dest] |= range;
                }
            }
            case TransitionOp.inc -> {
                var source = fixedOffset + TransitionOp.getSource(op);
                long saturate = data[source] & saturateMinMask;
                if (modifier == TransitionOp.overwrite || modifier == TransitionOp.move) {
                    data[dest] = (data[source] << 1);
                } else {
                    data[dest] |= (data[source] << 1);
                }
                data[dest] |= saturate;
            }
            case TransitionOp.maintain -> {
                var source = mapId(TransitionOp.getSource(op));
                if (modifier == TransitionOp.overwrite || modifier == TransitionOp.move) {
                    data[dest] = data[source];
                } else {
                    data[dest] |= data[source];
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
    protected boolean canLoop(int sId, long[] fixedData, int[][] intArrays) {
        if (max == -1) {
            return true;
        }
        return (fixedData[mapId(sId)] & canLoopMask) != 0;
    }

    @Override
    protected boolean canExit(int sId, long[] fixedData, int[][] intArrays) {
        assert min != 0;
        return (fixedData[mapId(sId)] & canExitMask) != 0;
    }

    @Override
    protected boolean ltMin(int sId, long[] fixedData, int[][] intArrays) {
        assert min != 0;
        return (fixedData[mapId(sId)] & ltMinMask) != 0;
    }
}
