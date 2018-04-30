/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.regex.tregex.buffer.ByteArrayBuffer;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.buffer.ObjectArrayBuffer;
import com.oracle.truffle.regex.tregex.nfa.NFA;
import com.oracle.truffle.regex.tregex.nfa.NFAAbstractFinalState;
import com.oracle.truffle.regex.tregex.nfa.NFAStateTransition;
import com.oracle.truffle.regex.tregex.nodes.DFACaptureGroupLazyTransitionNode;
import com.oracle.truffle.regex.tregex.nodes.DFACaptureGroupPartialTransitionNode;
import com.oracle.truffle.regex.tregex.parser.Counter;
import com.oracle.truffle.regex.tregex.util.DebugUtil;

import java.util.Arrays;

public class DFACaptureGroupTransitionBuilder {

    private final NFA nfa;
    private final DFAStateTransitionBuilder transitionBuilder;
    private final DFAStateNodeBuilder successor;
    private final boolean isInitialTransition;
    private NFAStateSet requiredStates = null;
    private DFACaptureGroupLazyTransitionNode lazyTransition = null;

    public DFACaptureGroupTransitionBuilder(NFA nfa, DFAStateTransitionBuilder transitionBuilder, DFAStateNodeBuilder successor, boolean isInitialTransition) {
        this.nfa = nfa;
        this.transitionBuilder = transitionBuilder;
        this.successor = successor;
        this.isInitialTransition = isInitialTransition;
    }

    private NFAStateSet getRequiredStates() {
        if (requiredStates == null) {
            requiredStates = new NFAStateSet(nfa);
            for (NFAStateTransition transition : transitionBuilder.getTargetState()) {
                requiredStates.add(transition.getSource());
            }
        }
        return requiredStates;
    }

    private DFACaptureGroupPartialTransitionNode createPartialTransition(NFAStateSet targetStates, CompilationBuffer compilationBuffer) {
        int[] newOrder = new int[Math.max(getRequiredStates().size(), targetStates.size())];
        Arrays.fill(newOrder, -1);
        boolean[] used = new boolean[newOrder.length];
        int[] copySource = new int[getRequiredStates().size()];
        ObjectArrayBuffer indexUpdates = compilationBuffer.getObjectBuffer1();
        ObjectArrayBuffer indexClears = compilationBuffer.getObjectBuffer2();
        ByteArrayBuffer arrayCopies = compilationBuffer.getByteArrayBuffer();
        for (NFAStateTransition nfaTransition : transitionBuilder.getTargetState()) {
            if (targetStates.contains(nfaTransition.getTarget())) {
                int sourceIndex = getRequiredStates().getStateIndex(nfaTransition.getSource());
                int targetIndex = targetStates.getStateIndex(nfaTransition.getTarget());
                assert !(nfaTransition.getTarget() instanceof NFAAbstractFinalState) || targetIndex == DFACaptureGroupPartialTransitionNode.FINAL_STATE_RESULT_INDEX;
                if (!used[sourceIndex]) {
                    used[sourceIndex] = true;
                    newOrder[targetIndex] = sourceIndex;
                    copySource[sourceIndex] = targetIndex;
                } else {
                    arrayCopies.add((byte) copySource[sourceIndex]);
                    arrayCopies.add((byte) targetIndex);
                }
                if (nfaTransition.getGroupBoundaries().hasIndexUpdates()) {
                    indexUpdates.add(createIndexManipulationArray(targetIndex, nfaTransition.getGroupBoundaries().getUpdateIndices()));
                }
                if (nfaTransition.getGroupBoundaries().hasIndexClears()) {
                    indexClears.add(createIndexManipulationArray(targetIndex, nfaTransition.getGroupBoundaries().getClearIndices()));
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
        int nChanged = newOrder.length;
        for (int i = newOrder.length - 1; i >= 0; i--) {
            if (newOrder[i] == i) {
                nChanged = i;
            } else {
                break;
            }
        }
        byte[] byteNewOrder;
        if (nChanged == 0 || isInitialTransition) {
            byteNewOrder = null;
        } else {
            byteNewOrder = new byte[nChanged];
            for (int i = 0; i < byteNewOrder.length; i++) {
                byteNewOrder[i] = (byte) newOrder[i];
            }
        }
        byte[] byteArrayCopies = arrayCopies.size() == 0 ? DFACaptureGroupPartialTransitionNode.EMPTY_ARRAY_COPIES : arrayCopies.toArray();
        return DFACaptureGroupPartialTransitionNode.create(byteNewOrder, byteArrayCopies,
                        indexUpdates.toArray(DFACaptureGroupPartialTransitionNode.EMPTY_INDEX_UPDATES),
                        indexClears.toArray(DFACaptureGroupPartialTransitionNode.EMPTY_INDEX_CLEARS));
    }

    private static byte[] createIndexManipulationArray(int targetIndex, byte[] groupBoundaryIndices) {
        final byte[] indexUpdate = new byte[groupBoundaryIndices.length + 1];
        indexUpdate[0] = (byte) targetIndex;
        System.arraycopy(groupBoundaryIndices, 0, indexUpdate, 1, groupBoundaryIndices.length);
        return indexUpdate;
    }

    public DFACaptureGroupLazyTransitionNode toLazyTransition(Counter idCounter, CompilationBuffer compilationBuffer) {
        if (lazyTransition == null) {
            DFACaptureGroupPartialTransitionNode[] partialTransitions = new DFACaptureGroupPartialTransitionNode[successor.getCaptureGroupTransitions().length];
            for (int i = 0; i < successor.getCaptureGroupTransitions().length; i++) {
                DFACaptureGroupTransitionBuilder successorTransition = successor.getCaptureGroupTransitions()[i];
                partialTransitions[i] = createPartialTransition(successorTransition.getRequiredStates(), compilationBuffer);
            }
            DFACaptureGroupPartialTransitionNode transitionToFinalState = null;
            DFACaptureGroupPartialTransitionNode transitionToAnchoredFinalState = null;
            if (successor.isFinalState()) {
                transitionToFinalState = createPartialTransition(new NFAStateSet(nfa, successor.getNfaStateSet().getFinalState()), compilationBuffer);
            }
            if (successor.isAnchoredFinalState()) {
                transitionToAnchoredFinalState = createPartialTransition(new NFAStateSet(nfa, successor.getNfaStateSet().getAnchoredFinalState()), compilationBuffer);
            }
            lazyTransition = new DFACaptureGroupLazyTransitionNode((short) idCounter.inc(), partialTransitions, transitionToFinalState, transitionToAnchoredFinalState);
        }
        return lazyTransition;
    }

    public DebugUtil.Table toTable() {
        DebugUtil.Table table = new DebugUtil.Table("CGTransition");
        for (NFAStateTransition nfaTransition : transitionBuilder.getTargetState()) {
            table.append(new DebugUtil.Value("nfaTransition", String.format("%s -> %s",
                            nfaTransition.getSource().idToString(),
                            nfaTransition.getTarget().idToString())),
                            nfaTransition.getGroupBoundaries().toTable());
        }
        return table;
    }
}
