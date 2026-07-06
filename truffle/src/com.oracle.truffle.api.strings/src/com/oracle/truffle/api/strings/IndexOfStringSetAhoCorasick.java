/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.strings;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;

import org.graalvm.collections.EconomicMap;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString.Encoding;

/**
 * Classic Aho-Corasick (simple DFA-style) search implementation for
 * {@link TruffleString.ByteIndexOfStringSetNode}. This implementation is used as a
 * fallback when {@link IndexOfStringSetTeddy} cannot be used.
 */
final class IndexOfStringSetAhoCorasick extends IndexOfStringSet.SearchPlan {

    /** State field: best matching pattern id for this state. */
    private static final int STATE_FIELD_BEST_PATTERN_ID = 0;
    /** State field: number of explicit transition records following this state header. */
    private static final int STATE_FIELD_TRANSITION_COUNT = 1;
    /** State field: offset of the first explicit transition record. */
    private static final int STATE_FIELD_TRANSITIONS = 2;
    /** Number of int fields per transition record. */
    private static final int TRANSITION_RECORD_SIZE = 2;
    /** Transition field: input symbol to match. */
    private static final int TRANSITION_FIELD_SYMBOL = 0;
    /** Transition field: target state offset. */
    private static final int TRANSITION_FIELD_TARGET = 1;
    /** Number of int fields per pattern record. */
    private static final int PATTERN_RECORD_SIZE = 2;
    /** Pattern field: pattern length in raw code units. */
    private static final int PATTERN_FIELD_RAW_LENGTH = 0;
    /** Pattern field: original index in the user-provided pattern array. */
    private static final int PATTERN_FIELD_ORIGINAL_INDEX = 1;

    /**
     * Flattened pattern metadata. Pattern ids are record numbers. Each pattern record is:
     * {@code [rawLength, originalIndex]}.
     */
    @CompilationFinal(dimensions = 1) private final int[] patternData;
    /**
     * Flattened deterministic automaton state data. State ids are offsets into this array. Each
     * state record is:
     * {@code [bestPatternId, transitionCount, symbol0, target0, symbol1, target1, ...]}. Missing
     * transitions implicitly go to the root state.
     */
    @CompilationFinal(dimensions = 1) private final int[] states;

    private IndexOfStringSetAhoCorasick(int maxCodeRange, int minPatternLength, int maxPatternLength,
                    int[] patternData,
                    int[] states) {
        super(maxCodeRange, minPatternLength, maxPatternLength);
        this.patternData = patternData;
        this.states = states;
    }

    static IndexOfStringSetAhoCorasick create(int maxCodeRange, Encoding encoding, int minPatternLength, int maxPatternLength,
                    byte[][] encodedPatterns, byte[][] encodedMasks, int[] patternOriginalIndices) {
        Builder builder = new Builder(encodedPatterns, encodedMasks, encoding, patternOriginalIndices);
        return builder.createSearchPlan(maxCodeRange, minPatternLength, maxPatternLength);
    }

    @Override
    long runSearchImpl(Node location, byte[] arrayA, long offsetA, int lengthA, int strideA, int fromIndex, int toIndex, Encoding encoding) {
        CompilerAsserts.partialEvaluationConstant(encoding);
        int state = 0;
        int bestMatchStart = -1;
        int bestPatternIndex = -1;
        for (int i = fromIndex; i < toIndex; i++) {
            state = advanceState(state, TStringOps.readValue(arrayA, offsetA, lengthA, strideA, i));

            int patternId = states[state + STATE_FIELD_BEST_PATTERN_ID];
            if (patternId >= 0) {
                int patternLength = patternRawLength(patternId);
                int patternIndex = patternOriginalIndex(patternId);
                int matchStart = i - patternLength + 1;
                if (matchStart >= fromIndex && isBetterMatch(matchStart, patternIndex, bestMatchStart, bestPatternIndex)) {
                    bestMatchStart = matchStart;
                    bestPatternIndex = patternIndex;
                }
            }
            // Once we have scanned maxPatternLength - 1 symbols past bestMatchStart, any future match must
            // start later and therefore cannot beat the current best match.
            if (bestMatchStart >= 0 && i >= bestMatchStart + maxPatternLength - 1) {
                break;
            }
            TStringConstants.truffleSafePointPoll(location, i + 1);
        }
        if (bestMatchStart < 0) {
            return TruffleString.ByteIndexOfStringSetNode.NO_MATCH;
        }
        return TruffleString.ByteIndexOfStringSetNode.packResult(AbstractTruffleString.byteIndex(bestMatchStart, encoding), bestPatternIndex);
    }

    private static boolean isBetterMatch(int matchStart, int patternIndex, int bestMatchStart, int bestPatternIndex) {
        return Integer.compareUnsigned(matchStart, bestMatchStart) < 0 || matchStart == bestMatchStart && patternIndex < bestPatternIndex;
    }

    private int advanceState(int state, int inputValue) {
        int transitionStart = state + STATE_FIELD_TRANSITIONS;
        int transitionEnd = transitionStart + states[state + STATE_FIELD_TRANSITION_COUNT] * TRANSITION_RECORD_SIZE;
        for (int i = transitionStart; i < transitionEnd; i += TRANSITION_RECORD_SIZE) {
            if (states[i + TRANSITION_FIELD_SYMBOL] == inputValue) {
                return states[i + TRANSITION_FIELD_TARGET];
            }
        }
        return 0;
    }

    @Override
    boolean codeEquals(IndexOfStringSet.SearchPlan other) {
        if (!basicCodeEquals(other)) {
            return false;
        }
        IndexOfStringSetAhoCorasick o = (IndexOfStringSetAhoCorasick) other;
        return Arrays.equals(patternData, o.patternData) && Arrays.equals(states, o.states);
    }

    private int patternRawLength(int patternId) {
        return patternData[patternDataOffset(patternId) + PATTERN_FIELD_RAW_LENGTH];
    }

    private int patternOriginalIndex(int patternId) {
        return patternData[patternDataOffset(patternId) + PATTERN_FIELD_ORIGINAL_INDEX];
    }

    private static int patternDataOffset(int patternId) {
        return patternId * PATTERN_RECORD_SIZE;
    }

    private static int patternRawLength(int[] patternData, int patternId) {
        return patternData[patternDataOffset(patternId) + PATTERN_FIELD_RAW_LENGTH];
    }

    private static void setPatternRawLength(int[] patternData, int patternId, int rawLength) {
        patternData[patternDataOffset(patternId) + PATTERN_FIELD_RAW_LENGTH] = rawLength;
    }

    private static int patternOriginalIndex(int[] patternData, int patternId) {
        return patternData[patternDataOffset(patternId) + PATTERN_FIELD_ORIGINAL_INDEX];
    }

    private static void setPatternOriginalIndex(int[] patternData, int patternId, int originalIndex) {
        patternData[patternDataOffset(patternId) + PATTERN_FIELD_ORIGINAL_INDEX] = originalIndex;
    }

    private static final class Builder {
        private final Encoding encoding;
        private final byte[][] encodedPatterns;
        private final byte[][] encodedMasks;
        private final int[] patternData;
        private final ArrayList<NFAState> nfaStates = new ArrayList<>();
        private final ArrayList<DFAState> dfaStates = new ArrayList<>();
        private final HashMap<StateSet, Integer> dfaStateMap = new HashMap<>();

        Builder(byte[][] encodedPatterns, byte[][] encodedMasks, Encoding encoding, int[] patternOriginalIndices) {
            this.encoding = encoding;
            this.encodedPatterns = encodedPatterns;
            this.encodedMasks = encodedMasks;
            this.patternData = createPatternData(encodedPatterns, encoding, patternOriginalIndices);
            nfaStates.add(new NFAState());
        }

        private static int[] createPatternData(byte[][] encodedPatterns, Encoding encoding, int[] patternOriginalIndices) {
            int[] ret = new int[patternOriginalIndices.length * PATTERN_RECORD_SIZE];
            for (int patternId = 0; patternId < patternOriginalIndices.length; patternId++) {
                setPatternRawLength(ret, patternId, IndexOfStringSet.rawLength(encodedPatterns[patternId], encoding));
                setPatternOriginalIndex(ret, patternId, patternOriginalIndices[patternId]);
            }
            return ret;
        }

        IndexOfStringSetAhoCorasick createSearchPlan(int maxCodeRange, int minPatternLength, int maxPatternLength) {
            buildTrie();
            buildDFA();
            int[] stateOffsets = computeStateOffsets();
            int[] flattenedStates = flattenStates(stateOffsets);
            return new IndexOfStringSetAhoCorasick(maxCodeRange, minPatternLength, maxPatternLength, patternData, flattenedStates);
        }

        private void buildTrie() {
            for (int patternId = 0; patternId < encodedPatterns.length; patternId++) {
                int state = 0;
                byte[] pattern = getEncodedPattern(patternId);
                byte[] mask = encodedMasks[patternId];
                int rawLength = patternRawLength(patternData, patternId);
                for (int i = 0; i < rawLength; i++) {
                    int value = TStringOps.readFromByteArray(pattern, encoding.naturalStride, i);
                    int maskValue = mask == null ? 0 : TStringOps.readFromByteArray(mask, encoding.naturalStride, i);
                    state = addTransition(state, value, maskValue);
                }
                NFAState terminal = nfaStates.get(state);
                // Multiple patterns may end in the same trie state. Keep the lowest original pattern
                // index; their start and end positions are identical.
                terminal.terminalPatternId = betterSameEnd(terminal.terminalPatternId, patternId, patternData);
            }
        }

        private byte[] getEncodedPattern(int patternId) {
            return encodedPatterns[patternId];
        }

        private int addTransition(int state, int value, int mask) {
            NFAState nfaState = nfaStates.get(state);
            for (Edge edge : nfaState.edges) {
                if (edge.value == value && edge.mask == mask) {
                    return edge.target;
                }
            }
            int next = nfaStates.size();
            nfaState.edges.add(new Edge(value, mask, next));
            nfaStates.add(new NFAState());
            return next;
        }

        private void buildDFA() {
            ArrayDeque<Integer> worklist = new ArrayDeque<>();
            internDFAState(new int[]{0}, worklist);
            while (!worklist.isEmpty()) {
                int stateId = worklist.removeFirst();
                DFAState state = dfaStates.get(stateId);
                int[] symbols = collectSymbols(state.nfaStates);
                for (int symbol : symbols) {
                    int[] nextSet = computeNextSet(state.nfaStates, symbol);
                    if (nextSet.length == 1 && nextSet[0] == 0) {
                        continue;
                    }
                    int target = internDFAState(nextSet, worklist);
                    state.transitions.put(symbol, target);
                }
            }
        }

        private int internDFAState(int[] nfaStateSet, ArrayDeque<Integer> worklist) {
            StateSet key = new StateSet(nfaStateSet);
            Integer existing = dfaStateMap.get(key);
            if (existing != null) {
                return existing;
            }
            int id = dfaStates.size();
            dfaStateMap.put(key, id);
            dfaStates.add(new DFAState(nfaStateSet, computeBestPatternId(nfaStateSet)));
            worklist.addLast(id);
            return id;
        }

        private int[] collectSymbols(int[] nfaStateSet) {
            int[] symbols = new int[symbolCountUpperBound(nfaStateSet)];
            int nSymbols = 0;
            for (int nfaStateId : nfaStateSet) {
                for (Edge edge : nfaStates.get(nfaStateId).edges) {
                    nSymbols = addSymbol(symbols, nSymbols, edge.value);
                    if (edge.mask != 0) {
                        nSymbols = addSymbol(symbols, nSymbols, edge.value ^ edge.mask);
                    }
                }
            }
            int[] ret = Arrays.copyOf(symbols, nSymbols);
            Arrays.sort(ret);
            return ret;
        }

        private int symbolCountUpperBound(int[] nfaStateSet) {
            int ret = 0;
            for (int nfaStateId : nfaStateSet) {
                // Each masked edge can contribute two concrete input symbols: value and value ^ mask.
                ret += nfaStates.get(nfaStateId).edges.size() * 2;
            }
            return ret;
        }

        private static int addSymbol(int[] symbols, int nSymbols, int symbol) {
            for (int i = 0; i < nSymbols; i++) {
                if (symbols[i] == symbol) {
                    return nSymbols;
                }
            }
            symbols[nSymbols] = symbol;
            return nSymbols + 1;
        }

        private int[] computeNextSet(int[] nfaStateSet, int symbol) {
            BitSet next = new BitSet(nfaStates.size());
            next.set(0);
            for (int nfaStateId : nfaStateSet) {
                for (Edge edge : nfaStates.get(nfaStateId).edges) {
                    if (edge.matches(symbol)) {
                        next.set(edge.target);
                    }
                }
            }
            return toStateSet(next);
        }

        private static int[] toStateSet(BitSet states) {
            int[] ret = new int[states.cardinality()];
            int i = 0;
            for (int state = states.nextSetBit(0); state >= 0; state = states.nextSetBit(state + 1)) {
                ret[i++] = state;
            }
            return ret;
        }

        private int computeBestPatternId(int[] nfaStateSet) {
            int best = -1;
            for (int nfaStateId : nfaStateSet) {
                best = betterSameEnd(best, nfaStates.get(nfaStateId).terminalPatternId, patternData);
            }
            return best;
        }

        private int[] computeStateOffsets() {
            int[] offsets = new int[dfaStates.size()];
            int nextOffset = 0;
            for (int i = 0; i < dfaStates.size(); i++) {
                offsets[i] = nextOffset;
                nextOffset += flattenedStateSize(dfaStates.get(i));
            }
            return offsets;
        }

        private int[] flattenStates(int[] stateOffsets) {
            int[] flattenedStates = new int[flattenedStatesTotalSize(stateOffsets)];
            for (int stateId = 0; stateId < dfaStates.size(); stateId++) {
                flattenState(flattenedStates, stateOffsets, stateId);
            }
            return flattenedStates;
        }

        private int flattenedStatesTotalSize(int[] stateOffsets) {
            int lastStateId = dfaStates.size() - 1;
            return stateOffsets[lastStateId] + flattenedStateSize(dfaStates.get(lastStateId));
        }

        private static int flattenedStateSize(DFAState state) {
            return STATE_FIELD_TRANSITIONS + state.transitions.size() * TRANSITION_RECORD_SIZE;
        }

        private void flattenState(int[] flattenedStates, int[] stateOffsets, int stateId) {
            DFAState state = dfaStates.get(stateId);
            int stateOffset = stateOffsets[stateId];
            flattenedStates[stateOffset + STATE_FIELD_BEST_PATTERN_ID] = state.bestPatternId;
            flattenedStates[stateOffset + STATE_FIELD_TRANSITION_COUNT] = state.transitions.size();
            int transitionOffset = stateOffset + STATE_FIELD_TRANSITIONS;
            for (int symbol : sortedTransitionSymbols(state)) {
                flattenedStates[transitionOffset + TRANSITION_FIELD_SYMBOL] = symbol;
                flattenedStates[transitionOffset + TRANSITION_FIELD_TARGET] = stateOffsets[state.transitions.get(symbol)];
                transitionOffset += TRANSITION_RECORD_SIZE;
            }
        }

        private static int[] sortedTransitionSymbols(DFAState state) {
            int[] symbols = new int[state.transitions.size()];
            int i = 0;
            for (int symbol : state.transitions.getKeys()) {
                symbols[i++] = symbol;
            }
            Arrays.sort(symbols);
            return symbols;
        }

        private static int betterSameEnd(int left, int right, int[] patternData) {
            if (left < 0) {
                return right;
            }
            if (right < 0) {
                return left;
            }
            int leftLength = patternRawLength(patternData, left);
            int rightLength = patternRawLength(patternData, right);
            if (leftLength != rightLength) {
                return leftLength > rightLength ? left : right;
            }
            return Integer.compareUnsigned(patternOriginalIndex(patternData, left), patternOriginalIndex(patternData, right)) < 0 ? left : right;
        }
    }

    private static final class NFAState {
        final ArrayList<Edge> edges = new ArrayList<>();
        int terminalPatternId = -1;
    }

    private static final class Edge {
        final int value;
        final int mask;
        final int target;

        Edge(int value, int mask, int target) {
            this.value = value;
            this.mask = mask;
            this.target = target;
        }

        boolean matches(int symbol) {
            return (symbol | mask) == value;
        }
    }

    private static final class DFAState {
        final int[] nfaStates;
        final EconomicMap<Integer, Integer> transitions = EconomicMap.create();
        final int bestPatternId;

        DFAState(int[] nfaStates, int bestPatternId) {
            this.nfaStates = nfaStates;
            this.bestPatternId = bestPatternId;
        }
    }

    private static final class StateSet {
        final int[] states;

        StateSet(int[] states) {
            this.states = states;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof StateSet other && Arrays.equals(states, other.states);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(states);
        }
    }
}
