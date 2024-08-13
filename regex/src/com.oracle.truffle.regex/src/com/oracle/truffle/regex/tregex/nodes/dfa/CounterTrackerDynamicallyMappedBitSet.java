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

import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.regex.tregex.automaton.TransitionOp;

/**
 * Counter tracker backed by bitsets, but compared to {@link CounterTrackerBitSet} these bitsets are
 * accessed indirectly through an array. While each access are a bit slower (need to read the index
 * first) when an operation is a move (see {@link TransitionOp#move}) one can simply swap the two
 * indices in the array, which effectively swap the two underlying bitsets.
 */
public class CounterTrackerDynamicallyMappedBitSet extends AbstractCounterTrackerBitSet {
    public static final int MAX_N = 10;
    private final int numberOfCell;
    private final int fixedOffset;

    public CounterTrackerDynamicallyMappedBitSet(int min, int max, int numberOfCells, CounterTrackerData.Builder dataBuilder) {
        super(min, max);
        this.numberOfCell = numberOfCells;
        int size = (n + 1) * numberOfCell;
        this.fixedOffset = dataBuilder.getFixedDataSize();
        dataBuilder.requestFixedSize(size);
    }

    @Override
    int mapId(int sId, long[] fixedData) {
        return (int) fixedData[fixedOffset + sId];
    }

    private void swapIndices(int idA, int idB, long[] fixedData) {
        var tmp = fixedData[fixedOffset + idA];
        fixedData[fixedOffset + idA] = fixedData[fixedOffset + idB];
        fixedData[fixedOffset + idB] = tmp;
    }

    @Override
    protected void inc(int sourceId, int targetId, int modifier, long[] fixedData) {
        if (modifier == TransitionOp.move) {
            swapIndices(sourceId, targetId, fixedData);
            // target now points to source and we increment it itself
            super.inc(targetId, targetId, TransitionOp.overwrite, fixedData);
        } else {
            super.inc(sourceId, targetId, modifier, fixedData);
        }
    }

    @Override
    @ExplodeLoop
    protected void maintain(int sourceId, int targetId, int modifier, long[] fixedData) {
        int source = mapId(sourceId, fixedData);
        int target = mapId(targetId, fixedData);
        if (modifier == TransitionOp.move) {
            swapIndices(sourceId, targetId, fixedData);
        } else {
            for (int i = 0; i < n; i++) {
                if (modifier == TransitionOp.overwrite) {
                    fixedData[target + i] = fixedData[source + i];
                } else {
                    fixedData[target + i] |= fixedData[source + i];
                }
            }
        }
    }

    @Override
    public void init(long[] fixed, int[][] intArrays) {
        for (int i = 0; i < numberOfCell; i++) {
            fixed[fixedOffset + i] = (fixedOffset + numberOfCell) + ((long) i * n);
        }
    }
}
