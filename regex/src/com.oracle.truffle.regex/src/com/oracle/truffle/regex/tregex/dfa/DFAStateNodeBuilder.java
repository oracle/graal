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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.tregex.nfa.NFAState;
import com.oracle.truffle.regex.tregex.nfa.NFAStateTransition;
import com.oracle.truffle.regex.tregex.nodes.TraceFinderDFAStateNode;
import com.oracle.truffle.regex.tregex.util.DebugUtil;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class DFAStateNodeBuilder implements JsonConvertible {

    private static final List<DFACaptureGroupTransitionBuilder> NODE_SPLIT_TAINTED = new ArrayList<>();
    private static final String NODE_SPLIT_UNINITIALIZED_PRECEDING_TRANSITIONS_ERROR_MSG = "this state node builder was altered by the node splitter and does not have valid information about preceding transitions!";

    private final short id;
    private NFATransitionSet nfaStateSet;
    private boolean initialState = false;
    private boolean overrideFinalState = false;
    private boolean isFinalStateSuccessor = false;
    private boolean isBackwardPrefixState;
    private short backwardPrefixState = -1;
    private DFAStateTransitionBuilder[] transitions;
    private List<DFACaptureGroupTransitionBuilder> precedingTransitions;
    private NFAStateTransition anchoredFinalStateTransition;
    private NFAStateTransition unAnchoredFinalStateTransition;
    private byte preCalculatedUnAnchoredResult = TraceFinderDFAStateNode.NO_PRE_CALC_RESULT;
    private byte preCalculatedAnchoredResult = TraceFinderDFAStateNode.NO_PRE_CALC_RESULT;

    DFAStateNodeBuilder(short id, NFATransitionSet nfaStateSet, boolean isBackwardPrefixState) {
        this.id = id;
        this.nfaStateSet = nfaStateSet;
        this.isBackwardPrefixState = isBackwardPrefixState;
        if (isBackwardPrefixState) {
            this.backwardPrefixState = this.id;
        }
    }

    private DFAStateNodeBuilder(DFAStateNodeBuilder copy, short copyID) {
        id = copyID;
        nfaStateSet = copy.nfaStateSet;
        initialState = copy.initialState;
        overrideFinalState = copy.overrideFinalState;
        isFinalStateSuccessor = copy.isFinalStateSuccessor;
        isBackwardPrefixState = copy.isBackwardPrefixState;
        backwardPrefixState = copy.backwardPrefixState;
        transitions = new DFAStateTransitionBuilder[copy.transitions.length];
        for (int i = 0; i < transitions.length; i++) {
            transitions[i] = copy.transitions[i].createNodeSplitCopy();
        }
        precedingTransitions = NODE_SPLIT_TAINTED;
        anchoredFinalStateTransition = copy.anchoredFinalStateTransition;
        unAnchoredFinalStateTransition = copy.unAnchoredFinalStateTransition;
        preCalculatedAnchoredResult = copy.preCalculatedAnchoredResult;
        preCalculatedUnAnchoredResult = copy.preCalculatedUnAnchoredResult;
    }

    public DFAStateNodeBuilder createNodeSplitCopy(short copyID) {
        return new DFAStateNodeBuilder(this, copyID);
    }

    public void nodeSplitUpdateSuccessors(short[] newSuccessors, DFAStateNodeBuilder[] stateIndexMap) {
        for (int i = 0; i < transitions.length; i++) {
            DFAStateNodeBuilder successor = stateIndexMap[newSuccessors[i]];
            assert successor != null;
            successor.precedingTransitions = NODE_SPLIT_TAINTED;
            transitions[i].setTarget(successor);
        }
        if (hasBackwardPrefixState()) {
            assert newSuccessors.length == transitions.length + 1;
            backwardPrefixState = newSuccessors[newSuccessors.length - 1];
        }
    }

    public short getId() {
        return id;
    }

    public void setNfaStateSet(NFATransitionSet nfaStateSet) {
        this.nfaStateSet = nfaStateSet;
    }

    public NFATransitionSet getNfaStateSet() {
        return nfaStateSet;
    }

    public void setInitialState(boolean initialState) {
        this.initialState = initialState;
    }

    public boolean isInitialState() {
        return initialState;
    }

    public boolean isFinalState() {
        return unAnchoredFinalStateTransition != null || overrideFinalState;
    }

    public void setOverrideFinalState(boolean overrideFinalState) {
        this.overrideFinalState = overrideFinalState;
    }

    public boolean isAnchoredFinalState() {
        return anchoredFinalStateTransition != null;
    }

    public int getNumberOfSuccessors() {
        return transitions.length + (hasBackwardPrefixState() ? 1 : 0);
    }

    public DFAStateTransitionBuilder[] getTransitions() {
        return transitions;
    }

    public void setTransitions(DFAStateTransitionBuilder[] transitions) {
        this.transitions = transitions;
    }

    /**
     * Used in pruneUnambiguousPaths mode. States that are NOT final states or successors of final
     * states may have their last matcher replaced with an AnyMatcher.
     */
    public boolean isFinalStateSuccessor() {
        return isFinalStateSuccessor;
    }

    public void setFinalStateSuccessor() {
        isFinalStateSuccessor = true;
    }

    public void addPrecedingTransition(DFACaptureGroupTransitionBuilder transitionBuilder) {
        if (precedingTransitions == NODE_SPLIT_TAINTED) {
            throw new IllegalStateException(NODE_SPLIT_UNINITIALIZED_PRECEDING_TRANSITIONS_ERROR_MSG);
        }
        if (precedingTransitions == null) {
            precedingTransitions = new ArrayList<>();
        }
        precedingTransitions.add(transitionBuilder);
    }

    public List<DFACaptureGroupTransitionBuilder> getPrecedingTransitions() {
        if (precedingTransitions == NODE_SPLIT_TAINTED) {
            throw new IllegalStateException(NODE_SPLIT_UNINITIALIZED_PRECEDING_TRANSITIONS_ERROR_MSG);
        }
        if (precedingTransitions == null) {
            return Collections.emptyList();
        }
        return precedingTransitions;
    }

    public boolean isBackwardPrefixState() {
        return isBackwardPrefixState;
    }

    public void setIsBackwardPrefixState(boolean backwardPrefixState) {
        isBackwardPrefixState = backwardPrefixState;
    }

    public boolean hasBackwardPrefixState() {
        return backwardPrefixState >= 0;
    }

    public short getBackwardPrefixState() {
        return backwardPrefixState;
    }

    public void setBackwardPrefixState(short backwardPrefixState) {
        this.backwardPrefixState = backwardPrefixState;
    }

    public void setAnchoredFinalStateTransition(NFAStateTransition anchoredFinalStateTransition) {
        this.anchoredFinalStateTransition = anchoredFinalStateTransition;
    }

    public NFAStateTransition getAnchoredFinalStateTransition() {
        return anchoredFinalStateTransition;
    }

    public void setUnAnchoredFinalStateTransition(NFAStateTransition unAnchoredFinalStateTransition) {
        this.unAnchoredFinalStateTransition = unAnchoredFinalStateTransition;
    }

    public NFAStateTransition getUnAnchoredFinalStateTransition() {
        return unAnchoredFinalStateTransition;
    }

    public byte getPreCalculatedUnAnchoredResult() {
        return preCalculatedUnAnchoredResult;
    }

    public byte getPreCalculatedAnchoredResult() {
        return preCalculatedAnchoredResult;
    }

    void updatePreCalcUnAnchoredResult(int newResult) {
        if (newResult >= 0) {
            if (preCalculatedUnAnchoredResult == TraceFinderDFAStateNode.NO_PRE_CALC_RESULT || Byte.toUnsignedInt(preCalculatedUnAnchoredResult) > newResult) {
                preCalculatedUnAnchoredResult = (byte) newResult;
            }
        }
    }

    private void updatePreCalcAnchoredResult(int newResult) {
        if (newResult >= 0) {
            if (preCalculatedAnchoredResult == TraceFinderDFAStateNode.NO_PRE_CALC_RESULT || Byte.toUnsignedInt(preCalculatedAnchoredResult) > newResult) {
                preCalculatedAnchoredResult = (byte) newResult;
            }
        }
    }

    public void clearPreCalculatedResults() {
        preCalculatedUnAnchoredResult = TraceFinderDFAStateNode.NO_PRE_CALC_RESULT;
        preCalculatedAnchoredResult = TraceFinderDFAStateNode.NO_PRE_CALC_RESULT;
    }

    public void updateFinalStateData(DFAGenerator dfaGenerator) {
        boolean forward = nfaStateSet.isForward();
        for (NFAStateTransition t : nfaStateSet) {
            NFAState target = t.getTarget(forward);
            if (target.hasTransitionToAnchoredFinalState(forward)) {
                if (anchoredFinalStateTransition == null) {
                    setAnchoredFinalStateTransition(target.getTransitionToAnchoredFinalState(forward));
                }
            }
            if (target.hasTransitionToUnAnchoredFinalState(forward)) {
                setUnAnchoredFinalStateTransition(target.getTransitionToUnAnchoredFinalState(forward));
                if (forward) {
                    return;
                }
            }
            if (dfaGenerator.getNfa().isTraceFinderNFA()) {
                for (NFAStateTransition t2 : target.getNext(forward)) {
                    NFAState target2 = t2.getTarget(forward);
                    if (target2.isAnchoredFinalState(forward)) {
                        assert target2.hasPossibleResults() && target2.getPossibleResults().size() == 1;
                        updatePreCalcAnchoredResult(target2.getPossibleResults().get(0));
                    }
                    if (target2.isUnAnchoredFinalState(forward)) {
                        assert target2.hasPossibleResults() && target2.getPossibleResults().size() == 1;
                        updatePreCalcUnAnchoredResult(target2.getPossibleResults().get(0));
                    }
                }
            }
        }
    }

    public String stateSetToString() {
        StringBuilder sb = new StringBuilder(nfaStateSet.toString());
        if (preCalculatedUnAnchoredResult != TraceFinderDFAStateNode.NO_PRE_CALC_RESULT) {
            sb.append("_r").append(preCalculatedUnAnchoredResult);
        }
        if (preCalculatedAnchoredResult != TraceFinderDFAStateNode.NO_PRE_CALC_RESULT) {
            sb.append("_rA").append(preCalculatedAnchoredResult);
        }
        return sb.toString();
    }

    @Override
    public int hashCode() {
        int hashCode = nfaStateSet.hashCode();
        if (isBackwardPrefixState) {
            hashCode *= 31;
        }
        return hashCode;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof DFAStateNodeBuilder)) {
            return false;
        }
        DFAStateNodeBuilder o = (DFAStateNodeBuilder) obj;
        return nfaStateSet.equals(o.nfaStateSet) && isBackwardPrefixState == o.isBackwardPrefixState;
    }

    @TruffleBoundary
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        return DebugUtil.appendNodeId(sb, id).append(": ").append(stateSetToString()).toString();
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        return Json.obj(Json.prop("id", id),
                        Json.prop("stateSet", Json.array(nfaStateSet.stream().map(x -> Json.val(x.getTarget().getId())))),
                        Json.prop("finalState", isFinalState()),
                        Json.prop("anchoredFinalState", isAnchoredFinalState()),
                        Json.prop("transitions", Arrays.stream(transitions).map(x -> Json.val(x.getId()))));
    }
}
