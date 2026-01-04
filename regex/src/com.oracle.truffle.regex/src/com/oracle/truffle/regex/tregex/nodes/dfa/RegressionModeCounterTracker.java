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

import java.util.ArrayList;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public class RegressionModeCounterTracker extends CounterTracker {
    private final ArrayList<CounterTracker> trackers = new ArrayList<>(5);
    private final int min;

    public RegressionModeCounterTracker(int min, int max, int numberOfCells, boolean isTrivialAlwaysReEnter, boolean isTrivialNeverReEnter, CounterTrackerData.Builder dataBuilder) {
        this.min = min;
        int upperBound = Math.max(min, max);
        if (upperBound <= 64) {
            trackers.add(new CounterTrackerLong(min, max, numberOfCells, dataBuilder));
        }
        if (upperBound <= 128) {
            trackers.add(new CounterTrackerLong2(min, max, numberOfCells, dataBuilder));
        }
        if (upperBound <= 64 * CounterTrackerBitSetWithOffset.MAX_BITSET_SIZE) {
            trackers.add(new CounterTrackerBitSetWithOffset(min, max, numberOfCells, dataBuilder));
        }
        if (isTrivialAlwaysReEnter) {
            trackers.add(new CounterTrackerTrivialAlwaysReEnter(min, numberOfCells, dataBuilder));
        }
        if (isTrivialNeverReEnter) {
            trackers.add(new CounterTrackerTrivialNeverReEnter(min, max, numberOfCells, dataBuilder));
        }
        trackers.add(new CounterTrackerList(min, max, numberOfCells, dataBuilder));
    }

    @TruffleBoundary
    @Override
    protected boolean anyLtMax(int sId, long[] fixedData, int[][] intArrays) {
        boolean result = onAll(sId, fixedData, intArrays, CounterTracker::anyLtMax);
        ensureConsistency(sId, fixedData, intArrays);
        return result;
    }

    @TruffleBoundary
    @Override
    protected boolean anyLtMin(int sId, long[] fixedData, int[][] intArrays) {
        boolean result = onAll(sId, fixedData, intArrays, CounterTracker::anyLtMin);
        ensureConsistency(sId, fixedData, intArrays);
        return result;
    }

    @TruffleBoundary
    @Override
    protected boolean anyGeMin(int sId, long[] fixedData, int[][] intArrays) {
        boolean result = onAll(sId, fixedData, intArrays, CounterTracker::anyGeMin);
        ensureConsistency(sId, fixedData, intArrays);
        return result;
    }

    @FunctionalInterface
    interface TrackerGuard {
        boolean apply(CounterTracker tracker, int sId, long[] fixedData, int[][] intArrays);
    }

    private boolean onAll(int sId, long[] fixedData, int[][] intArrays, TrackerGuard f) {
        CounterTracker firstTracker = trackers.get(0);
        boolean resultFirst = f.apply(firstTracker, sId, fixedData, intArrays);
        for (CounterTracker tracker : trackers) {
            boolean resultCur = f.apply(tracker, sId, fixedData, intArrays);
            if (resultCur != resultFirst) {
                throw new AssertionError(String.format("Inconsistent tracker results, firstTracker: %b, state: %s cur tracker: %b, state: %s",
                                resultFirst, firstTracker.dumpState(sId, fixedData, intArrays),
                                resultCur, tracker.dumpState(sId, fixedData, intArrays)));
            }
        }
        return resultFirst;
    }

    @TruffleBoundary
    @Override
    public void apply(long operation, long[] fixedData, int[][] intArrays) {
        for (CounterTracker tracker : trackers) {
            tracker.apply(operation, fixedData, intArrays);
        }
    }

    @TruffleBoundary
    @Override
    public void init(long[] fixedData, int[][] intArrays) {
        for (var tracker : trackers) {
            tracker.init(fixedData, intArrays);
        }
    }

    private void ensureConsistency(int sId, long[] fixedData, int[][] intArrays) {
        onAll(sId, fixedData, intArrays, CounterTracker::anyLtMax);
        if (min > 0) {
            onAll(sId, fixedData, intArrays, CounterTracker::anyGeMin);
            onAll(sId, fixedData, intArrays, CounterTracker::anyLtMin);
        }
    }

    @TruffleBoundary
    @Override
    public boolean support(long operation) {
        trackers.removeIf(tracker -> !tracker.support(operation));
        return !trackers.isEmpty();
    }

    @Override
    public String dumpState(int sId, long[] fixedData, int[][] intArrays) {
        throw new UnsupportedOperationException();
    }
}
