/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.regex.tregex.dfa;

import com.oracle.truffle.regex.UnsupportedRegexException;
import com.oracle.truffle.regex.charset.CharSet;
import com.oracle.truffle.regex.tregex.automaton.TransitionBuilder;
import com.oracle.truffle.regex.tregex.buffer.ByteArrayBuffer;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.buffer.ObjectArrayBuffer;
import com.oracle.truffle.regex.tregex.nfa.NFAStateTransition;
import com.oracle.truffle.regex.tregex.nodes.DFACaptureGroupLazyTransitionNode;
import com.oracle.truffle.regex.tregex.nodes.DFACaptureGroupPartialTransitionNode;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonObject;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

import java.util.Arrays;

public class DFACaptureGroupTransitionBuilder extends DFAStateTransitionBuilder {

    private final DFAGenerator dfaGen;
    private NFAStateSet requiredStates = null;
    private DFACaptureGroupLazyTransitionNode lazyTransition = null;

    DFACaptureGroupTransitionBuilder(CharSet matcherBuilder, NFATransitionSet transitions, DFAGenerator dfaGen) {
        super(matcherBuilder, transitions);
        this.dfaGen = dfaGen;
    }

    @Override
    public DFAStateTransitionBuilder createNodeSplitCopy() {
        return new DFACaptureGroupTransitionBuilder(getMatcherBuilder(), getTransitionSet(), dfaGen);
    }

    @Override
    public DFACaptureGroupTransitionBuilder createMerged(TransitionBuilder<NFATransitionSet> other, CharSet mergedMatcher) {
        return new DFACaptureGroupTransitionBuilder(mergedMatcher, getTransitionSet().createMerged(other.getTransitionSet()), dfaGen);
    }

    public void setLazyTransition(DFACaptureGroupLazyTransitionNode lazyTransition) {
        this.lazyTransition = lazyTransition;
    }

    /**
     * Returns {@code true} if the DFA executor may safely omit the result set reordering step in
     * this transition.
     *
     * @see DFACaptureGroupPartialTransitionNode
     */
    private boolean skipReorder() {
        return !dfaGen.getProps().isSearching() && getSource().isInitialState();
    }

    private NFAStateSet getRequiredStates() {
        if (requiredStates == null) {
            requiredStates = new NFAStateSet(dfaGen.getNfa());
            for (NFAStateTransition nfaTransition : getTransitionSet()) {
                requiredStates.add(nfaTransition.getSource());
            }
        }
        return requiredStates;
    }

    private DFACaptureGroupPartialTransitionNode createPartialTransition(NFAStateSet targetStates, CompilationBuffer compilationBuffer) {
        int numberOfNFAStates = Math.max(getRequiredStates().size(), targetStates.size());
        PartialTransitionDebugInfo partialTransitionDebugInfo = null;
        if (dfaGen.getEngineOptions().isDumpAutomata()) {
            partialTransitionDebugInfo = new PartialTransitionDebugInfo(numberOfNFAStates);
        }
        dfaGen.updateMaxNumberOfNFAStatesInOneTransition(numberOfNFAStates);
        int[] newOrder = new int[numberOfNFAStates];
        Arrays.fill(newOrder, -1);
        boolean[] used = new boolean[newOrder.length];
        int[] copySource = new int[getRequiredStates().size()];
        ObjectArrayBuffer indexUpdates = compilationBuffer.getObjectBuffer1();
        ObjectArrayBuffer indexClears = compilationBuffer.getObjectBuffer2();
        ByteArrayBuffer arrayCopies = compilationBuffer.getByteArrayBuffer();
        for (NFAStateTransition nfaTransition : getTransitionSet()) {
            if (targetStates.contains(nfaTransition.getTarget())) {
                int sourceIndex = getRequiredStates().getStateIndex(nfaTransition.getSource());
                int targetIndex = targetStates.getStateIndex(nfaTransition.getTarget());
                if (dfaGen.getEngineOptions().isDumpAutomata()) {
                    partialTransitionDebugInfo.mapResultToNFATransition(targetIndex, nfaTransition);
                }
                assert !(nfaTransition.getTarget().isForwardFinalState()) || targetIndex == DFACaptureGroupPartialTransitionNode.FINAL_STATE_RESULT_INDEX;
                if (!used[sourceIndex]) {
                    used[sourceIndex] = true;
                    newOrder[targetIndex] = sourceIndex;
                    copySource[sourceIndex] = targetIndex;
                } else {
                    arrayCopies.add((byte) copySource[sourceIndex]);
                    arrayCopies.add((byte) targetIndex);
                }
                if (nfaTransition.getGroupBoundaries().hasIndexUpdates()) {
                    indexUpdates.add(nfaTransition.getGroupBoundaries().updatesToPartialTransitionArray(targetIndex));
                }
                if (nfaTransition.getGroupBoundaries().hasIndexClears()) {
                    indexClears.add(nfaTransition.getGroupBoundaries().clearsToPartialTransitionArray(targetIndex));
                }
            }
        }
        int order = 0;
        for (int i = 0; i < newOrder.length; i++) {
            if (newOrder[i] == -1) {
                while (used[order]) {
                    order++;
                }
                newOrder[i] = order++;
            }
        }
        byte preReorderFinalStateResultIndex = (byte) newOrder[DFACaptureGroupPartialTransitionNode.FINAL_STATE_RESULT_INDEX];
        // important: don't change the order, because newOrderToSequenceOfSwaps() reuses
        // CompilationBuffer#getByteArrayBuffer()
        byte[] byteArrayCopies = arrayCopies.length() == 0 ? DFACaptureGroupPartialTransitionNode.EMPTY_ARRAY_COPIES : arrayCopies.toArray();
        byte[] reorderSwaps = skipReorder() ? DFACaptureGroupPartialTransitionNode.EMPTY_REORDER_SWAPS : newOrderToSequenceOfSwaps(newOrder, compilationBuffer);
        DFACaptureGroupPartialTransitionNode dfaCaptureGroupPartialTransitionNode = DFACaptureGroupPartialTransitionNode.create(
                        dfaGen,
                        reorderSwaps,
                        byteArrayCopies,
                        indexUpdates.toArray(DFACaptureGroupPartialTransitionNode.EMPTY_INDEX_UPDATES),
                        indexClears.toArray(DFACaptureGroupPartialTransitionNode.EMPTY_INDEX_CLEARS),
                        preReorderFinalStateResultIndex);
        if (dfaGen.getEngineOptions().isDumpAutomata()) {
            partialTransitionDebugInfo.node = dfaCaptureGroupPartialTransitionNode;
            dfaGen.registerCGPartialTransitionDebugInfo(partialTransitionDebugInfo);
        }
        return dfaCaptureGroupPartialTransitionNode;
    }

    /**
     * Converts the ordering given by {@code newOrder} to a sequence of swap operations as needed by
     * {@link DFACaptureGroupPartialTransitionNode}. The number of swap operations is guaranteed to
     * be smaller than {@code newOrder.length}. Caution: this method uses
     * {@link CompilationBuffer#getByteArrayBuffer()}.
     */
    private static byte[] newOrderToSequenceOfSwaps(int[] newOrder, CompilationBuffer compilationBuffer) {
        ByteArrayBuffer swaps = compilationBuffer.getByteArrayBuffer();
        for (int i = 0; i < newOrder.length; i++) {
            int swapSource = newOrder[i];
            int swapTarget = swapSource;
            if (swapSource == i) {
                continue;
            }
            do {
                swapSource = swapTarget;
                swapTarget = newOrder[swapTarget];
                swaps.add((byte) swapSource);
                swaps.add((byte) swapTarget);
                newOrder[swapSource] = swapSource;
            } while (swapTarget != i);
        }
        assert swaps.length() / 2 < newOrder.length;
        return swaps.length() == 0 ? DFACaptureGroupPartialTransitionNode.EMPTY_REORDER_SWAPS : swaps.toArray();
    }

    public DFACaptureGroupLazyTransitionNode toLazyTransition(CompilationBuffer compilationBuffer) {
        if (lazyTransition == null) {
            DFAStateNodeBuilder successor = getTarget();
            DFACaptureGroupPartialTransitionNode[] partialTransitions = new DFACaptureGroupPartialTransitionNode[successor.getTransitions().length];
            for (int i = 0; i < successor.getTransitions().length; i++) {
                DFACaptureGroupTransitionBuilder successorTransition = (DFACaptureGroupTransitionBuilder) successor.getTransitions()[i];
                partialTransitions[i] = createPartialTransition(successorTransition.getRequiredStates(), compilationBuffer);
            }
            DFACaptureGroupPartialTransitionNode transitionToFinalState = null;
            DFACaptureGroupPartialTransitionNode transitionToAnchoredFinalState = null;
            if (successor.isFinalState()) {
                transitionToFinalState = createPartialTransition(
                                new NFAStateSet(dfaGen.getNfa(), successor.getUnAnchoredFinalStateTransition().getSource()), compilationBuffer);
            }
            if (successor.isAnchoredFinalState()) {
                transitionToAnchoredFinalState = createPartialTransition(
                                new NFAStateSet(dfaGen.getNfa(), successor.getAnchoredFinalStateTransition().getSource()), compilationBuffer);
            }
            assert getId() >= 0;
            if (getId() > Short.MAX_VALUE) {
                throw new UnsupportedRegexException("too many capture group transitions");
            }
            lazyTransition = new DFACaptureGroupLazyTransitionNode((short) getId(), partialTransitions, transitionToFinalState, transitionToAnchoredFinalState);
        }
        return lazyTransition;
    }

    public static class PartialTransitionDebugInfo implements JsonConvertible {

        private DFACaptureGroupPartialTransitionNode node;
        private final short[] resultToTransitionMap;

        public PartialTransitionDebugInfo(DFACaptureGroupPartialTransitionNode node) {
            this(node, 0);
        }

        public PartialTransitionDebugInfo(int nResults) {
            this(null, nResults);
        }

        public PartialTransitionDebugInfo(DFACaptureGroupPartialTransitionNode node, int nResults) {
            this.node = node;
            this.resultToTransitionMap = new short[nResults];
        }

        public DFACaptureGroupPartialTransitionNode getNode() {
            return node;
        }

        public void mapResultToNFATransition(int resultNumber, NFAStateTransition transition) {
            resultToTransitionMap[resultNumber] = transition.getId();
        }

        @Override
        public JsonValue toJson() {
            return ((JsonObject) node.toJson()).append(Json.prop("resultToNFATransitionMap", Json.array(resultToTransitionMap)));
        }
    }
}
