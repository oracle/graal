/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.dfa;

import com.oracle.truffle.regex.UnsupportedRegexException;
import com.oracle.truffle.regex.charset.CodePointSet;
import com.oracle.truffle.regex.tregex.automaton.StateSet;
import com.oracle.truffle.regex.tregex.automaton.StateSetToIntMap;
import com.oracle.truffle.regex.tregex.automaton.TransitionSet;
import com.oracle.truffle.regex.tregex.buffer.ByteArrayBuffer;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.buffer.IntArrayBuffer;
import com.oracle.truffle.regex.tregex.nfa.NFA;
import com.oracle.truffle.regex.tregex.nfa.NFAState;
import com.oracle.truffle.regex.tregex.nfa.NFAStateTransition;
import com.oracle.truffle.regex.tregex.nodes.dfa.DFACaptureGroupPartialTransition;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonObject;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;
import com.oracle.truffle.regex.util.TBitSet;

public class DFACaptureGroupTransitionBuilder extends DFAStateTransitionBuilder {

    private final DFAGenerator dfaGen;
    private StateSet<NFA, NFAState> requiredStates = null;
    private StateSetToIntMap<NFAState, NFAStateTransition> requiredStatesIndexMap = null;
    private DFACaptureGroupLazyTransitionBuilder lazyTransitionBuilder = null;

    DFACaptureGroupTransitionBuilder(NFAStateTransition[] transitions, StateSet<NFA, NFAState> targetStateSet, CodePointSet matcherBuilder, long[] constraints, long[] operations,
                    DFAGenerator dfaGen) {
        super(transitions, targetStateSet, matcherBuilder, constraints, operations);
        this.dfaGen = dfaGen;
    }

    DFACaptureGroupTransitionBuilder(TransitionSet<NFA, NFAState, NFAStateTransition> transitions, CodePointSet matcherBuilder, long[] constraints, long[] operations, DFAGenerator dfaGen) {
        super(transitions, matcherBuilder, constraints, operations);
        this.dfaGen = dfaGen;
    }

    public void setLazyTransition(DFACaptureGroupLazyTransitionBuilder lazyTransition) {
        this.lazyTransitionBuilder = lazyTransition;
    }

    /**
     * Returns {@code true} if the DFA executor may safely omit the result set reordering step in
     * this transition.
     *
     * @see DFACaptureGroupPartialTransition
     */
    private boolean skipReorder() {
        return !dfaGen.getProps().isSearching() && getSource().isInitialState();
    }

    private StateSet<NFA, NFAState> getRequiredStates() {
        if (requiredStates == null) {
            requiredStates = StateSet.create(dfaGen.getNfa());
            for (NFAStateTransition nfaTransition : getTransitionSet().getTransitions()) {
                requiredStates.add(nfaTransition.getSource());
            }
        }
        return requiredStates;
    }

    private StateSetToIntMap<NFAState, NFAStateTransition> getRequiredStatesIndexMap() {
        if (requiredStatesIndexMap == null) {
            requiredStatesIndexMap = StateSetToIntMap.create(getRequiredStates());
        }
        return requiredStatesIndexMap;
    }

    /**
     * Creates a {@link DFACaptureGroupPartialTransition} from the current state to the given target states.
     */
    private byte[] createPartialTransition(StateSet<NFA, NFAState> targetStates, StateSetToIntMap<NFAState, NFAStateTransition> targetStatesIndexMap,
                    CompilationBuffer compilationBuffer) {
        int numberOfNFAStates = Math.max(getRequiredStates().size(), targetStates.size());
        PartialTransitionDebugInfo partialTransitionDebugInfo = null;
        if (dfaGen.getOptions().isDumpAutomata()) {
            partialTransitionDebugInfo = new PartialTransitionDebugInfo(numberOfNFAStates);
        }
        dfaGen.updateMaxNumberOfNFAStatesInOneTransition(numberOfNFAStates);
        IntArrayBuffer newOrder = compilationBuffer.getIntRangesBuffer1().asFixedSizeArray(numberOfNFAStates, -1);
        IntArrayBuffer copySource = compilationBuffer.getIntRangesBuffer2().asFixedSizeArray(numberOfNFAStates, -1);
        ByteArrayBuffer indexUpdates = compilationBuffer.getByteArrayBuffer2();
        ByteArrayBuffer indexClears = compilationBuffer.getByteArrayBuffer3();
        ByteArrayBuffer lastGroupUpdates = compilationBuffer.getByteArrayBuffer4();
        ByteArrayBuffer reorderSwaps = compilationBuffer.getByteArrayBuffer5();
        ByteArrayBuffer arrayCopies = compilationBuffer.getByteArrayBuffer();
        int numberOfIndexUpdates = 0;
        int numberOfIndexClears = 0;

        for (NFAStateTransition nfaTransition : getTransitionSet().getTransitions()) {
            if (targetStates.contains(nfaTransition.getTarget())) {
                int sourceIndex = getRequiredStatesIndexMap().getKey(nfaTransition.getSource());
                int targetIndex = targetStatesIndexMap.getKey(nfaTransition.getTarget());
                if (dfaGen.getOptions().isDumpAutomata()) {
                    partialTransitionDebugInfo.mapResultToNFATransition(targetIndex, nfaTransition);
                }
                assert !(nfaTransition.getTarget().isFinalState()) || targetIndex == DFACaptureGroupPartialTransition.FINAL_STATE_RESULT_INDEX;
                if (copySource.get(sourceIndex) < 0) {
                    newOrder.set(targetIndex, sourceIndex);
                    copySource.set(sourceIndex, targetIndex);
                } else {
                    arrayCopies.add((byte) copySource.get(sourceIndex));
                    arrayCopies.add((byte) targetIndex);
                }
                if (nfaTransition.getGroupBoundaries().hasIndexUpdates()) {
                    appendIndexOperation(indexUpdates, targetIndex, nfaTransition.getGroupBoundaries().getUpdateIndices());
                    numberOfIndexUpdates++;
                }
                if (nfaTransition.getGroupBoundaries().hasIndexClears()) {
                    appendIndexOperation(indexClears, targetIndex, nfaTransition.getGroupBoundaries().getClearIndices());
                    numberOfIndexClears++;
                }
                if (nfaTransition.getGroupBoundaries().hasLastGroup()) {
                    appendLastGroupUpdate(lastGroupUpdates, targetIndex, nfaTransition.getGroupBoundaries().getLastGroup());
                }
            }
        }
        int order = 0;
        for (int i = 0; i < newOrder.length(); i++) {
            if (newOrder.get(i) == -1) {
                while (copySource.get(order) >= 0) {
                    order++;
                }
                newOrder.set(i, order++);
            }
        }
        byte preReorderFinalStateResultIndex = (byte) newOrder.get(DFACaptureGroupPartialTransition.FINAL_STATE_RESULT_INDEX);
        if (!skipReorder()) {
            newOrderToSequenceOfSwaps(newOrder, reorderSwaps);
        }
        byte[] partialTransitionRecord = dfaGen.internCGPartialTransition(DFACaptureGroupPartialTransition.create(
                        reorderSwaps,
                        arrayCopies,
                        indexUpdates,
                        numberOfIndexUpdates,
                        indexClears,
                        numberOfIndexClears,
                        lastGroupUpdates,
                        preReorderFinalStateResultIndex));
        if (dfaGen.getOptions().isDumpAutomata()) {
            partialTransitionDebugInfo.record = partialTransitionRecord;
            dfaGen.registerCGPartialTransitionDebugInfo(partialTransitionDebugInfo);
        }
        return partialTransitionRecord;
    }

    static void appendIndexOperation(ByteArrayBuffer buffer, int targetArray, TBitSet indices) {
        assert targetArray < 256;
        int numberOfIndices = indices.numberOfSetBits();
        assert numberOfIndices < 256;
        buffer.add((byte) targetArray);
        buffer.add((byte) numberOfIndices);
        for (int index : indices) {
            assert index < 256;
            buffer.add((byte) index);
        }
    }

    static void appendLastGroupUpdate(ByteArrayBuffer buffer, int targetArray, int lastGroup) {
        assert targetArray < 256;
        assert lastGroup < Byte.MAX_VALUE;
        assert lastGroup > 0;
        buffer.add((byte) targetArray);
        buffer.add((byte) lastGroup);
    }

    /**
     * Converts the ordering given by {@code newOrder} to a sequence of swap operations as needed by
     * {@link DFACaptureGroupPartialTransition}. The number of swap operations is guaranteed to be
     * smaller than {@code newOrder.length}.
     */
    static void newOrderToSequenceOfSwaps(IntArrayBuffer newOrder, ByteArrayBuffer swaps) {
        for (int i = 0; i < newOrder.length(); i++) {
            int swapSource = newOrder.get(i);
            int swapTarget = swapSource;
            if (swapSource == i) {
                continue;
            }
            do {
                swapSource = swapTarget;
                swapTarget = newOrder.get(swapTarget);
                swaps.add((byte) swapSource);
                swaps.add((byte) swapTarget);
                newOrder.set(swapSource, swapSource);
            } while (swapTarget != i);
        }
        assert swaps.length() / 2 < newOrder.length();
    }

    public DFACaptureGroupLazyTransitionBuilder toLazyTransitionBuilder(CompilationBuffer compilationBuffer) {
        if (lazyTransitionBuilder == null) {
            DFAStateNodeBuilder successor = getTarget();
            byte[][] partialTransitionRecords = new byte[successor.getSuccessors().length][];
            for (int i = 0; i < successor.getSuccessors().length; i++) {
                DFACaptureGroupTransitionBuilder successorTransition = (DFACaptureGroupTransitionBuilder) successor.getSuccessors()[i];
                partialTransitionRecords[i] = createPartialTransition(successorTransition.getRequiredStates(), successorTransition.getRequiredStatesIndexMap(), compilationBuffer);
            }
            byte[] transitionToFinalStateRecord = null;
            byte[] transitionToAnchoredFinalStateRecord = null;
            if (successor.isUnAnchoredFinalState()) {
                NFAState src = successor.getUnAnchoredFinalStateTransition().getSource();
                transitionToFinalStateRecord = createPartialTransition(StateSet.create(dfaGen.getNfa(), src), StateSetToIntMap.create(src), compilationBuffer);
            }
            if (successor.isAnchoredFinalState()) {
                NFAState src = successor.getAnchoredFinalStateTransition().getSource();
                transitionToAnchoredFinalStateRecord = createPartialTransition(StateSet.create(dfaGen.getNfa(), src), StateSetToIntMap.create(src), compilationBuffer);
            }
            assert getId() >= 0;
            if (getId() > Short.MAX_VALUE) {
                throw new UnsupportedRegexException("too many capture group transitions");
            }
            lazyTransitionBuilder = new DFACaptureGroupLazyTransitionBuilder(dfaGen, (short) getId(), partialTransitionRecords, transitionToFinalStateRecord,
                            transitionToAnchoredFinalStateRecord);
        }
        return lazyTransitionBuilder;
    }

    public static class PartialTransitionDebugInfo implements JsonConvertible {

        private byte[] record;
        private int id;
        private final short[] resultToTransitionMap;

        public PartialTransitionDebugInfo(byte[] record, int id) {
            this(record, id, 0);
        }

        public PartialTransitionDebugInfo(int nResults) {
            this(null, -1, nResults);
        }

        public PartialTransitionDebugInfo(byte[] record, int id, int nResults) {
            this.record = record;
            this.id = id;
            this.resultToTransitionMap = new short[nResults];
        }

        public byte[] getRecord() {
            return record;
        }

        public void setId(int id) {
            assert this.id < 0;
            this.id = id;
        }

        public boolean hasResultMapping() {
            return resultToTransitionMap.length > 0;
        }

        public void mapResultToNFATransition(int resultNumber, NFAStateTransition transition) {
            resultToTransitionMap[resultNumber] = (short) transition.getId();
        }

        @Override
        public JsonValue toJson() {
            return ((JsonObject) DFACaptureGroupPartialTransition.toJson(record, id)).append(Json.prop("resultToNFATransitionMap", Json.array(resultToTransitionMap)));
        }
    }
}
