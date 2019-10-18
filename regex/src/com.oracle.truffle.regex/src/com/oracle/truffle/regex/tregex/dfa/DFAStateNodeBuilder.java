/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.charset.CharSet;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.buffer.IntArrayBuffer;
import com.oracle.truffle.regex.tregex.nfa.NFAState;
import com.oracle.truffle.regex.tregex.nfa.NFAStateTransition;
import com.oracle.truffle.regex.tregex.nodes.dfa.TraceFinderDFAStateNode;
import com.oracle.truffle.regex.tregex.util.DebugUtil;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class DFAStateNodeBuilder implements JsonConvertible {

    private static final byte FLAG_INITIAL_STATE = 1;
    private static final byte FLAG_OVERRIDE_FINAL_STATE = 1 << 1;
    private static final byte FLAG_FINAL_STATE_SUCCESSOR = 1 << 2;
    private static final byte FLAG_BACKWARD_PREFIX_STATE = 1 << 3;

    private static final List<DFACaptureGroupTransitionBuilder> NODE_SPLIT_TAINTED = new ArrayList<>();
    private static final String NODE_SPLIT_UNINITIALIZED_PRECEDING_TRANSITIONS_ERROR_MSG = "this state node builder was altered by the node splitter and does not have valid information about preceding transitions!";

    private final short id;
    private byte flags;
    private NFATransitionSet nfaTransitionSet;
    private short backwardPrefixState = -1;
    private DFAStateTransitionBuilder[] transitions;
    private List<DFACaptureGroupTransitionBuilder> precedingTransitions;
    private NFAStateTransition anchoredFinalStateTransition;
    private NFAStateTransition unAnchoredFinalStateTransition;
    private byte preCalculatedUnAnchoredResult = TraceFinderDFAStateNode.NO_PRE_CALC_RESULT;
    private byte preCalculatedAnchoredResult = TraceFinderDFAStateNode.NO_PRE_CALC_RESULT;

    DFAStateNodeBuilder(short id, NFATransitionSet nfaStateSet, boolean isBackwardPrefixState, boolean isInitialState) {
        this.id = id;
        this.nfaTransitionSet = nfaStateSet;
        setFlag(FLAG_BACKWARD_PREFIX_STATE, isBackwardPrefixState);
        setFlag(FLAG_INITIAL_STATE, isInitialState);
        if (isBackwardPrefixState) {
            this.backwardPrefixState = this.id;
        }
    }

    private DFAStateNodeBuilder(DFAStateNodeBuilder copy, short copyID) {
        id = copyID;
        flags = copy.flags;
        nfaTransitionSet = copy.nfaTransitionSet;
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

    public void setNfaTransitionSet(NFATransitionSet nfaTransitionSet) {
        this.nfaTransitionSet = nfaTransitionSet;
    }

    public NFATransitionSet getNfaTransitionSet() {
        return nfaTransitionSet;
    }

    public void setInitialState(boolean initialState) {
        setFlag(FLAG_INITIAL_STATE, initialState);
    }

    public boolean isInitialState() {
        return isFlagSet(FLAG_INITIAL_STATE);
    }

    public void setOverrideFinalState(boolean overrideFinalState) {
        setFlag(FLAG_OVERRIDE_FINAL_STATE, overrideFinalState);
    }

    /**
     * Used in pruneUnambiguousPaths mode. States that are NOT final states or successors of final
     * states may have their last matcher replaced with an AnyMatcher.
     */
    public boolean isFinalStateSuccessor() {
        return isFlagSet(FLAG_FINAL_STATE_SUCCESSOR);
    }

    public void setFinalStateSuccessor() {
        setFlag(FLAG_FINAL_STATE_SUCCESSOR);
    }

    public boolean isBackwardPrefixState() {
        return isFlagSet(FLAG_BACKWARD_PREFIX_STATE);
    }

    public void setIsBackwardPrefixState(boolean backwardPrefixState) {
        setFlag(FLAG_BACKWARD_PREFIX_STATE, backwardPrefixState);
    }

    public boolean isFinalState() {
        return unAnchoredFinalStateTransition != null || isFlagSet(FLAG_OVERRIDE_FINAL_STATE);
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

    private boolean isFlagSet(byte flag) {
        return (flags & flag) != 0;
    }

    private void setFlag(byte flag) {
        setFlag(flag, true);
    }

    private void setFlag(byte flag, boolean value) {
        if (value) {
            flags |= flag;
        } else {
            flags &= ~flag;
        }
    }

    /**
     * Returns {@code true} iff the union of the
     * {@link DFAStateTransitionBuilder#getMatcherBuilder()} of all transitions in this state is
     * equal to {@link CharSet#getFull()}.
     */
    public boolean coversFullCharSpace(CompilationBuffer compilationBuffer) {
        IntArrayBuffer indicesBuf = compilationBuffer.getIntRangesBuffer1();
        indicesBuf.ensureCapacity(transitions.length);
        int[] indices = indicesBuf.getBuffer();
        Arrays.fill(indices, 0, transitions.length, 0);
        int nextLo = Character.MIN_VALUE;
        while (true) {
            int i = findNextLo(indices, nextLo);
            if (i < 0) {
                return false;
            }
            CharSet mb = transitions[i].getMatcherBuilder();
            if (mb.getHi(indices[i]) == Character.MAX_VALUE) {
                return true;
            }
            nextLo = mb.getHi(indices[i]) + 1;
            indices[i]++;
        }
    }

    private int findNextLo(int[] indices, int findLo) {
        for (int i = 0; i < transitions.length; i++) {
            CharSet mb = transitions[i].getMatcherBuilder();
            if (indices[i] == mb.size()) {
                continue;
            }
            if (mb.getLo(indices[i]) == findLo) {
                return i;
            }
        }
        return -1;
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
        boolean forward = nfaTransitionSet.isForward();
        for (NFAStateTransition t : nfaTransitionSet) {
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
        StringBuilder sb = new StringBuilder(nfaTransitionSet.toString());
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
        int hashCode = nfaTransitionSet.hashCode();
        if (isBackwardPrefixState()) {
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
        return nfaTransitionSet.equals(o.nfaTransitionSet) && isBackwardPrefixState() == o.isBackwardPrefixState();
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
                        Json.prop("stateSet", Json.array(nfaTransitionSet.stream().map(x -> Json.val(x.getTarget().getId())))),
                        Json.prop("finalState", isFinalState()),
                        Json.prop("anchoredFinalState", isAnchoredFinalState()),
                        Json.prop("transitions", Arrays.stream(transitions).map(x -> Json.val(x.getId()))));
    }
}
