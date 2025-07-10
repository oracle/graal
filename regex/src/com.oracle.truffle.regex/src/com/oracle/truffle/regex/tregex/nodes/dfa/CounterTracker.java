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
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.regex.UnsupportedRegexException;
import com.oracle.truffle.regex.tregex.TRegexOptions;
import com.oracle.truffle.regex.tregex.automaton.TransitionConstraint;
import com.oracle.truffle.regex.util.TBitSet;

/**
 * At runtime every non-unrolled bounded quantifier is tracked using a data-structure which inherits
 * from {@link CounterTracker}. Every {@link CounterTracker} manages a fixed number of integer sets.
 * The content of one set represent the current possible values of a single quantifier's counter, in
 * respect to one NFA state contained in the current DFA state. An individual set is stored in a
 * so-called cell.
 * 
 * <p>
 * Depending on the bounds, a different implementation is used. For instance if the bounds are
 * smaller than 65, the count of a single state can be tracked using a single long value encoding
 * the various counts as a bitset, using {@link CounterTrackerLong}. An important thing to note is
 * that if max = -1 (meaning there is no upperbound) then the trackers will be saturating, meaning
 * that once the minimum is reach, then increment will not influence it anymore. The code is a
 * little convoluted because the data must be separated from the logic, that way partial evaluation
 * is capable of removing all dynamic dispatch, because the logic is constant, while the data (which
 * is mutated at runtime) is kept in {@link TRegexDFAExecutorLocals} as a long[] for data whose size
 * is fixed at compile time and a long[][] for data of dynamic size.
 */
public abstract class CounterTracker {

    /**
     * Constructs an array for all CounterTracker used to track all non-unrolled bounded quantifiers
     * of a regex.
     *
     * @param quantifierBounds An array of pairs min, max of all non-unrolled bounded quantifiers.
     * @param trackerSizes An array of the number of cells used by each non-unrolled bounded
     *            quantifier.
     * @param trivialAlwaysReEnter Bitset marking all quantifiers that can be tracked with
     *            {@link CounterTrackerTrivialAlwaysReEnter}.
     * @param trivialNeverReEnter Bitset marking all quantifiers that can be tracked with
     *            {@link CounterTrackerTrivialNeverReEnter}.
     */
    public static CounterTracker[] build(int[] quantifierBounds, int[] trackerSizes, CounterTrackerData.Builder dataBuilder, TBitSet trivialAlwaysReEnter, TBitSet trivialNeverReEnter,
                    boolean regressionTestMode) {
        CounterTracker[] result = new CounterTracker[trackerSizes.length];
        for (int i = 0; i < quantifierBounds.length / 2; i++) {
            int min = quantifierBounds[i * 2];
            int max = quantifierBounds[i * 2 + 1];
            assert max == -1 || max >= 2;
            int upperBound = Math.max(min, max);
            CounterTracker tracker;
            int numberOfCells = trackerSizes[i];
            if (regressionTestMode) {
                tracker = new RegressionModeCounterTracker(min, max, numberOfCells, trivialAlwaysReEnter.get(i), trivialNeverReEnter.get(i), dataBuilder);
            } else if (trivialAlwaysReEnter.get(i)) {
                tracker = new CounterTrackerTrivialAlwaysReEnter(min, numberOfCells, dataBuilder);
            } else if (trivialNeverReEnter.get(i)) {
                tracker = new CounterTrackerTrivialNeverReEnter(min, max, numberOfCells, dataBuilder);
            } else if (upperBound <= 64) {
                tracker = new CounterTrackerLong(min, max, numberOfCells, dataBuilder);
            } else if (upperBound <= 128) {
                tracker = new CounterTrackerLong2(min, max, numberOfCells, dataBuilder);
            } else if (upperBound <= 64 * CounterTrackerBitSetWithOffset.MAX_BITSET_SIZE) {
                tracker = new CounterTrackerBitSetWithOffset(min, max, numberOfCells, dataBuilder);
            } else {
                long maxMemoryConsumption = (long) numberOfCells * (long) upperBound * 4;
                if (maxMemoryConsumption > TRegexOptions.TRegexMaxCounterTrackerMemoryConsumptionInForceLinearExecutionMode) {
                    throw new UnsupportedRegexException(String.format("Bounded quantifier tracking would consume too much memory at match time: up to %d bytes. Limit: %d bytes", maxMemoryConsumption,
                                    TRegexOptions.TRegexMaxCounterTrackerMemoryConsumptionInForceLinearExecutionMode));
                }
                tracker = new CounterTrackerList(min, max, numberOfCells, dataBuilder);
            }
            result[i] = tracker;
        }
        return result;
    }

    /**
     * Returns true whenever the current state of the counter satisfies the given constraint. The
     * two data array arguments are the mutable data in which the counters are stored. Note that
     * this assumes that the constraints concerns the counter tracked by this tracker.
     */
    public boolean canExecute(long constraint, long[] fixedData, int[][] intArrays) {
        int kind = TransitionConstraint.getKind(constraint);
        int sId = TransitionConstraint.getStateID(constraint);
        CompilerAsserts.partialEvaluationConstant(constraint);
        CompilerAsserts.partialEvaluationConstant(kind);
        CompilerAsserts.partialEvaluationConstant(sId);
        return switch (kind) {
            case TransitionConstraint.anyGeMin -> anyGeMin(sId, fixedData, intArrays);
            case TransitionConstraint.allLtMin -> !anyGeMin(sId, fixedData, intArrays);
            case TransitionConstraint.anyLtMin -> anyLtMin(sId, fixedData, intArrays);
            case TransitionConstraint.allGeMin -> !anyLtMin(sId, fixedData, intArrays);
            case TransitionConstraint.anyLtMax -> anyLtMax(sId, fixedData, intArrays);
            case TransitionConstraint.allGeMax -> !anyLtMax(sId, fixedData, intArrays);
            default -> throw CompilerDirectives.shouldNotReachHere();
        };
    }

    /**
     * Return true if there is a counter value in the tracker for sId which is less than maximum.
     */
    protected abstract boolean anyLtMax(int sId, long[] fixedData, int[][] intArrays);

    /**
     * Return true if there is a counter value in the tracker for sId which is less than minimum.
     */
    protected abstract boolean anyLtMin(int sId, long[] fixedData, int[][] intArrays);

    /**
     * Return true if there is a counter value in the tracker for sId which is greater or equal
     * minimum.
     */
    protected abstract boolean anyGeMin(int sId, long[] fixedData, int[][] intArrays);

    /**
     * Apply the given operation to the counter. Note that this assumes that the operation concerns
     * the counter tracked by this tracker.
     */
    public abstract void apply(long operation, long[] fixedData, int[][] intArrays);

    public abstract void init(long[] fixedData, int[][] intArrays);

    /**
     * Unfortunately, some counters implementation cannot support all operations in a reasonable way
     * (for instance merging two counters is very slow when using list based counter trackers).
     * <p>
     * This function returns true if the given operation is supported by the given tracker.
     */
    public abstract boolean support(long operation);

    public abstract String dumpState(int sId, long[] fixedData, int[][] intArrays);
}
