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
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.regex.tregex.automaton.TransitionOp;
import com.oracle.truffle.regex.util.BitSets;

/**
 * Counter trackers backed by a bitset. Similar to {@link CounterTrackerLong}, but where each bitset
 * may use multiple long values. Can technically support with arbitrary bounds, but the bigger the
 * bounds the slower each operation become.
 */
public abstract class AbstractCounterTrackerBitSet extends CounterTracker {
    @CompilationFinal(dimensions = 1) private final long[] ltMaxMask;
    @CompilationFinal(dimensions = 1) private final long[] geMinMask;
    @CompilationFinal(dimensions = 1) private final long[] ltMinMask;
    private final long saturateMinMask;
    protected final int n;
    protected final int min;
    protected final int max;

    protected AbstractCounterTrackerBitSet(int min, int max) {
        this.min = min;
        this.max = max;
        int upperBound = max;
        if (max == -1) {
            upperBound = min;
            saturateMinMask = 1L << ((min - 1) % 64);
        } else {
            saturateMinMask = 0;
        }
        this.n = upperBound % 64 == 0 ? upperBound / 64 : upperBound / 64 + 1;
        geMinMask = new long[n];
        ltMaxMask = new long[n];
        ltMinMask = new long[n];
        if (min > 0) {
            BitSets.setRange(geMinMask, min - 1, upperBound - 1);
        }
        if (upperBound > 1) {
            BitSets.setRange(ltMaxMask, 0, upperBound - 2);
        }
        if (min > 1) {
            BitSets.setRange(ltMinMask, 0, min - 2);
        }
    }

    /**
     * Returns the location of the bitset for that id.
     */
    abstract int mapId(int sId, long[] fixedData);

    @ExplodeLoop
    private boolean intersect(int sId, long[] fixedData, long[] mask) {
        int offset = mapId(sId, fixedData);
        long intersection = 0;
        for (int i = 0; i < n; i++) {
            intersection |= fixedData[i + offset] & mask[i];
        }
        return intersection != 0;
    }

    @Override
    protected boolean ltMin(int sId, long[] fixedData, int[][] intArrays) {
        return intersect(sId, fixedData, ltMinMask);
    }

    @Override
    protected boolean canLoop(int sId, long[] fixedData, int[][] intArrays) {
        if (max == -1) {
            return true;
        }
        return intersect(sId, fixedData, ltMaxMask);
    }

    @Override
    public boolean canExit(int sId, long[] fixedData, int[][] intArrays) {
        return intersect(sId, fixedData, geMinMask);
    }

    @ExplodeLoop
    protected void set1(int targetId, int modifier, long[] fixedData) {
        CompilerAsserts.partialEvaluationConstant(modifier);
        int target = mapId(targetId, fixedData);
        if (modifier == TransitionOp.overwrite) {
            fixedData[target] = 1;
            for (int i = 1; i < n; i++) {
                fixedData[target + i] = 0;
            }
        } else {
            fixedData[target] |= 1;
        }
    }

    @ExplodeLoop
    protected void setMin(int sourceId, int targetId, int modifier, long[] fixedData) {
        CompilerAsserts.partialEvaluationConstant(modifier);
        int target = mapId(targetId, fixedData);
        int source = mapId(sourceId, fixedData);
        int currMinBitIdx = -1;
        for (int i = 0; i < n; i++) {
            if (fixedData[source + i] != 0) {
                currMinBitIdx = Long.numberOfTrailingZeros(fixedData[source + i]) + (i << 6);
                break;
            }
        }
        if (modifier == TransitionOp.overwrite || modifier == TransitionOp.move) {
            for (int i = 0; i < n; i++) {
                fixedData[target + i] = 0;
            }
        }
        int bitOffset = target << 6;
        // The reason is that setMin instruction can have a state whose value is 0 as a source,
        // in which case it sets all values in [1, min]. A cleaner approach could be to
        // split this into two operations: set1ToMin, and setCurrMinToMin.
        int fromBit = bitOffset + currMinBitIdx + 1;
        int toBit = bitOffset + min;
        BitSets.setRange(fixedData, fromBit, toBit);
    }

    @ExplodeLoop
    protected void inc(int sourceId, int targetId, int modifier, long[] fixedData) {
        CompilerAsserts.partialEvaluationConstant(modifier);
        int source = mapId(sourceId, fixedData);
        int target = mapId(targetId, fixedData);
        long saturate = 0L;
        if (max == -1) {
            saturate = fixedData[source + n - 1] & saturateMinMask;
        }
        for (int i = n - 1; i >= 0; i--) {
            if (modifier == TransitionOp.overwrite || modifier == TransitionOp.move) {
                fixedData[target + i] = fixedData[source + i] << 1;
            } else {
                fixedData[target + i] |= fixedData[source + i] << 1;
            }
            if (i > 0) {
                fixedData[target + i] |= fixedData[source + i - 1] >>> 63;
            }
        }
        fixedData[target + n - 1] |= saturate;
    }

    @ExplodeLoop
    protected void maintain(int sourceId, int targetId, int modifier, long[] fixedData) {
        CompilerAsserts.partialEvaluationConstant(modifier);
        int source = mapId(sourceId, fixedData);
        int target = mapId(targetId, fixedData);
        for (int i = 0; i < n; i++) {
            if (modifier == TransitionOp.overwrite || modifier == TransitionOp.move) {
                fixedData[target + i] = fixedData[source + i];
            } else {
                fixedData[target + i] |= fixedData[source + i];
            }
        }
    }

    @Override
    public void apply(long op, long[] fixedData, int[][] intArrays) {
        CompilerAsserts.partialEvaluationConstant(op);
        int target = TransitionOp.getTarget(op);
        int kind = TransitionOp.getKind(op);
        int modifier = TransitionOp.getModifier(op);
        CompilerAsserts.partialEvaluationConstant(target);
        CompilerAsserts.partialEvaluationConstant(kind);
        CompilerAsserts.partialEvaluationConstant(modifier);
        switch (kind) {
            case TransitionOp.set1 -> set1(target, modifier, fixedData);
            case TransitionOp.setMin -> {
                int source = TransitionOp.getSource(op);
                setMin(source, target, modifier, fixedData);
            }
            case TransitionOp.inc -> {
                int source = TransitionOp.getSource(op);
                inc(source, target, modifier, fixedData);
            }
            case TransitionOp.maintain -> {
                int source = TransitionOp.getSource(op);
                maintain(source, target, modifier, fixedData);
            }
        }
    }

    @Override
    public boolean support(long operation) {
        return true;
    }
}
