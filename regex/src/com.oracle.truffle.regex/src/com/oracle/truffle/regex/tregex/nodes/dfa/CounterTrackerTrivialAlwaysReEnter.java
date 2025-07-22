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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.regex.tregex.automaton.TransitionOp;

/**
 * Counter Tracker for quantifiers where all {@link TransitionOp#inc} operations are accompanied by
 * a {@link TransitionOp#set1}. In this case, it is not necessary to track individual entries in the
 * counting set, since the set will always contain all possible values from 1 to the current
 * maximum. Therefore, this tracker maintains a single value representing the current largest value
 * in the set.
 */
public final class CounterTrackerTrivialAlwaysReEnter extends CounterTracker {
    private final int min;
    private final int fixedOffset;

    public CounterTrackerTrivialAlwaysReEnter(int min, int numberOfCells, CounterTrackerData.Builder dataBuilder) {
        this.min = min;
        this.fixedOffset = dataBuilder.getFixedDataSize();
        dataBuilder.requestFixedSize(numberOfCells);
    }

    @Override
    @ExplodeLoop
    public void apply(long op, long[] data, int[][] intArrays) {
        CompilerAsserts.partialEvaluationConstant(op);
        int dst = mapId(TransitionOp.getTarget(op));
        int kind = TransitionOp.getKind(op);
        int modifier = TransitionOp.getModifier(op);
        switch (kind) {
            case TransitionOp.set1 -> {
                if (modifier == TransitionOp.union) {
                    // implicit
                    return;
                }
                update(modifier, data, dst, 1);
            }
            case TransitionOp.inc -> {
                update(modifier, data, dst, data[mapId(TransitionOp.getSource(op))] + 1);
            }
            case TransitionOp.maintain -> {
                int src = mapId(TransitionOp.getSource(op));
                if (modifier == TransitionOp.swap) {
                    long tmp = data[dst];
                    data[dst] = data[src];
                    data[src] = tmp;
                } else {
                    update(modifier, data, dst, data[src]);
                }
            }
        }
    }

    private static void update(int modifier, long[] data, int dst, long newCount) {
        CompilerAsserts.partialEvaluationConstant(modifier);
        if (modifier == TransitionOp.overwrite) {
            data[dst] = newCount;
        } else {
            assert modifier == TransitionOp.union;
            data[dst] = Math.max(newCount, data[dst]);
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
        return true;
    }

    @Override
    protected boolean anyGeMin(int sId, long[] fixedData, int[][] intArrays) {
        assert min != 0;
        return fixedData[mapId(sId)] >= min;
    }

    @Override
    protected boolean anyLtMin(int sId, long[] fixedData, int[][] intArrays) {
        assert min != 0;
        return min > 1;
    }

    @Override
    public String dumpState(int sId, long[] fixedData, int[][] intArrays) {
        return "TrivialAlwaysReEnter, current value: " + fixedData[mapId(sId)];
    }
}
