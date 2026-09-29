/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;

import com.oracle.truffle.regex.charset.CharMatchers;
import com.oracle.truffle.regex.charset.CodePointSet;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.buffer.IntArrayBuffer;

/**
 * Packed records mapping DFA transitions to records in the executor's encoded character matcher
 * table. Records are specialized for the executor's string encoding. The four possible lanes are
 * ASCII, Latin-1 / UTF-8 two-byte, BMP / UTF-8 three-byte, and astral / UTF-8 four-byte.
 *
 * <pre>
 * sequential matcher record:
 *
 * +-----------------+---------------------+---------------+-----+---------------+
 * | transitionCount | noMatch | lane mask | lane 0 refs[] | ... | lane 3 refs[] |
 * +-----------------+---------------------+---------------+-----+---------------+
 *
 * Each present lane contains {@code transitionCount} integer references. Absent lanes are omitted.
 * {@code noMatch} and the four-bit lane mask share one integer word.
 * </pre>
 */
public final class SequentialMatchers {

    public static final int NO_MATCHER = -1;
    public static final int NO_LANE = -1;

    private static final int FIELD_TRANSITION_COUNT = 0;
    private static final int FIELD_NO_MATCH_SUCCESSOR_AND_LANE_MASK = 1;
    private static final int RECORD_HEADER_SIZE = 2;

    private static final int NUMBER_OF_LANES = 4;
    private static final int LANE_MASK = (1 << NUMBER_OF_LANES) - 1;
    private static final int NO_MATCH_SUCCESSOR_SHIFT = NUMBER_OF_LANES;

    private SequentialMatchers() {
    }

    public static int getTransitionCount(int[] matcherRecords, int matcherRecordRef) {
        return matcherRecords[matcherRecordRef + FIELD_TRANSITION_COUNT];
    }

    public static short getNoMatchSuccessor(int[] matcherRecords, int matcherRecordRef) {
        return (short) (matcherRecords[matcherRecordRef + FIELD_NO_MATCH_SUCCESSOR_AND_LANE_MASK] >> NO_MATCH_SUCCESSOR_SHIFT);
    }

    public static int getLaneRef(int[] matcherRecords, int matcherRecordRef, int lane) {
        assert 0 <= lane && lane < NUMBER_OF_LANES;
        int laneMask = getLaneMask(matcherRecords, matcherRecordRef);
        int laneFlag = 1 << lane;
        if ((laneMask & laneFlag) == 0) {
            return NO_LANE;
        }
        int precedingLanes = Integer.bitCount(laneMask & (laneFlag - 1));
        return matcherRecordRef + RECORD_HEADER_SIZE + precedingLanes * getTransitionCount(matcherRecords, matcherRecordRef);
    }

    public static int getMaxBytes(int[] matcherRecords, int matcherRecordRef) {
        int laneMask = getLaneMask(matcherRecords, matcherRecordRef);
        return laneMask == 0 ? 0 : Integer.SIZE - Integer.numberOfLeadingZeros(laneMask);
    }

    private static int getLaneMask(int[] matcherRecords, int matcherRecordRef) {
        return matcherRecords[matcherRecordRef + FIELD_NO_MATCH_SUCCESSOR_AND_LANE_MASK] & LANE_MASK;
    }

    /** Builds and content-deduplicates packed sequential matcher records. */
    public static final class Builder {

        @SuppressWarnings("rawtypes") private static final Equivalence INT_ARRAY_EQUIVALENCE = new Equivalence() {
            @Override
            public boolean equals(Object a, Object b) {
                return Arrays.equals((int[]) a, (int[]) b);
            }

            @Override
            public int hashCode(Object o) {
                return Arrays.hashCode((int[]) o);
            }
        };

        // DFA-wide state that accumulates entries over the entire DFA generation.
        private final CharMatchers.Builder charMatcherBuilder = new CharMatchers.Builder();
        private final IntArrayBuffer matcherRecords = new IntArrayBuffer();
        private final EconomicMap<int[], Integer> matcherRecordRefs = EconomicMap.create(INT_ARRAY_EQUIVALENCE);

        // Per-state data reset between states.
        private final IntArrayBuffer[] buffers;
        private short noMatchSuccessor = -1;

        // Scratch storage used only by createMatcherRecord; it carries no state between calls.
        private final IntArrayBuffer matcherRecordBuffer = new IntArrayBuffer();

        public Builder(int nBuffers) {
            assert 0 < nBuffers && nBuffers <= NUMBER_OF_LANES;
            buffers = new IntArrayBuffer[nBuffers];
            for (int i = 0; i < buffers.length; i++) {
                buffers[i] = new IntArrayBuffer();
            }
        }

        public void reset(int nTransitions) {
            for (IntArrayBuffer buf : buffers) {
                buf.asFixedSizeArray(nTransitions, NO_MATCHER);
            }
            noMatchSuccessor = -1;
        }

        public IntArrayBuffer getBuffer(int i) {
            return buffers[i];
        }

        public CharMatchers.Builder getMatcherBuilder() {
            return charMatcherBuilder;
        }

        public int[] getEncodedMatchers() {
            return charMatcherBuilder.toArray();
        }

        public int[] getMatcherRecords() {
            return matcherRecords.toArray();
        }

        public void setNoMatchSuccessor(short noMatchSuccessor) {
            this.noMatchSuccessor = noMatchSuccessor;
        }

        public int estimatedCost(int i) {
            int ret = 0;
            for (IntArrayBuffer buf : buffers) {
                if (buf.get(i) != NO_MATCHER) {
                    ret = Math.max(ret, charMatcherBuilder.estimatedCost(buf.get(i)));
                }
            }
            return ret;
        }

        public void createSplitMatcher(int i, CodePointSet cps, CompilationBuffer compilationBuffer, CodePointSet... splitRanges) {
            for (int j = 0; j < splitRanges.length; j++) {
                CodePointSet intersection = splitRanges[j].createIntersection(cps, compilationBuffer);
                assert i < buffers[j].length();
                if (intersection.matchesSomething()) {
                    buffers[j].set(i, charMatcherBuilder.getOrCreateMatcher(intersection, compilationBuffer));
                } else {
                    buffers[j].set(i, NO_MATCHER);
                }
            }
        }

        public int createMatcherRecord() {
            int laneMask = 0;
            int transitionCount = 0;
            for (int lane = 0; lane < buffers.length; lane++) {
                if (!isEmpty(buffers[lane])) {
                    laneMask |= 1 << lane;
                    if (transitionCount == 0) {
                        transitionCount = buffers[lane].length();
                    } else {
                        assert transitionCount == buffers[lane].length();
                    }
                }
            }
            matcherRecordBuffer.clear();
            matcherRecordBuffer.add(transitionCount);
            matcherRecordBuffer.add((noMatchSuccessor << NO_MATCH_SUCCESSOR_SHIFT) | laneMask);
            for (int lane = 0; lane < buffers.length; lane++) {
                if ((laneMask & (1 << lane)) != 0) {
                    matcherRecordBuffer.addAll(buffers[lane]);
                }
            }
            int[] matcherRecord = matcherRecordBuffer.toArray();
            Integer existingRef = matcherRecordRefs.get(matcherRecord);
            if (existingRef != null) {
                return existingRef;
            }
            int matcherRecordRef = matcherRecords.length();
            matcherRecords.addAll(matcherRecord);
            matcherRecordRefs.put(matcherRecord, matcherRecordRef);
            return matcherRecordRef;
        }

        private static boolean isEmpty(IntArrayBuffer buf) {
            for (int i : buf) {
                if (i != NO_MATCHER) {
                    return false;
                }
            }
            return true;
        }
    }
}
