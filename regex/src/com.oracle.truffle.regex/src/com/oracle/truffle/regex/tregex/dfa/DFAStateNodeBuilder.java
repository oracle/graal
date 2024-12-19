/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.charset.CodePointSet;
import com.oracle.truffle.regex.tregex.automaton.BasicState;
import com.oracle.truffle.regex.tregex.automaton.TransitionSet;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.buffer.IntArrayBuffer;
import com.oracle.truffle.regex.tregex.nfa.NFA;
import com.oracle.truffle.regex.tregex.nfa.NFAState;
import com.oracle.truffle.regex.tregex.nfa.NFAStateTransition;
import com.oracle.truffle.regex.tregex.nodes.dfa.TraceFinderDFAStateNode;
import com.oracle.truffle.regex.tregex.string.Encodings.Encoding;
import com.oracle.truffle.regex.tregex.util.DebugUtil;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

public final class DFAStateNodeBuilder extends BasicState<DFAStateNodeBuilder, DFAStateTransitionBuilder> implements JsonConvertible {

    private static final short FLAG_OVERRIDE_FINAL_STATE = 1 << (N_FLAGS + 0);
    private static final short FLAG_FINAL_STATE_SUCCESSOR = 1 << (N_FLAGS + 1);
    private static final short FLAG_BACKWARD_PREFIX_STATE = 1 << (N_FLAGS + 2);
    private static final short FLAG_FORWARD = 1 << (N_FLAGS + 3);
    private static final short FLAG_PRIORITY_SENSITIVE = 1 << (N_FLAGS + 4);

    private static final DFAStateTransitionBuilder[] EMPTY_TRANSITIONS = new DFAStateTransitionBuilder[0];
    private static final DFAStateTransitionBuilder[] NODE_SPLIT_TAINTED = new DFAStateTransitionBuilder[0];
    private static final String NODE_SPLIT_UNINITIALIZED_PRECEDING_TRANSITIONS_ERROR_MSG = "this state node builder was altered by the node splitter and does not have valid information about preceding transitions!";

    private TransitionSet<NFA, NFAState, NFAStateTransition> nfaTransitionSet;
    private short backwardPrefixState = -1;
    private NFAStateTransition anchoredFinalStateTransition;
    private NFAStateTransition unAnchoredFinalStateTransition;
    private byte preCalculatedUnAnchoredResult = TraceFinderDFAStateNode.NO_PRE_CALC_RESULT;
    private byte preCalculatedAnchoredResult = TraceFinderDFAStateNode.NO_PRE_CALC_RESULT;

    DFAStateNodeBuilder(int id, TransitionSet<NFA, NFAState, NFAStateTransition> nfaStateSet, boolean isBackwardPrefixState, boolean isInitialState, boolean forward, boolean prioritySensitive) {
        super(id, EMPTY_TRANSITIONS);
        assert id <= Short.MAX_VALUE;
        this.nfaTransitionSet = nfaStateSet;
        setFlag(FLAG_BACKWARD_PREFIX_STATE, isBackwardPrefixState);
        setFlag(FLAG_FORWARD, forward);
        setFlag(FLAG_PRIORITY_SENSITIVE, prioritySensitive);
        setUnAnchoredInitialState(isInitialState);
        if (isBackwardPrefixState) {
            this.backwardPrefixState = (short) id;
        }
    }

    private DFAStateNodeBuilder(DFAStateNodeBuilder copy, short copyID) {
        super(copyID, copy.getFlags(), EMPTY_TRANSITIONS);
        nfaTransitionSet = copy.nfaTransitionSet;
        backwardPrefixState = copy.backwardPrefixState;
        DFAStateTransitionBuilder[] transitions = new DFAStateTransitionBuilder[copy.getSuccessors().length];
        for (int i = 0; i < transitions.length; i++) {
            transitions[i] = copy.getSuccessors()[i].createNodeSplitCopy();
        }
        setSuccessors(transitions);
        setPredecessors(NODE_SPLIT_TAINTED);
        anchoredFinalStateTransition = copy.anchoredFinalStateTransition;
        unAnchoredFinalStateTransition = copy.unAnchoredFinalStateTransition;
        preCalculatedAnchoredResult = copy.preCalculatedAnchoredResult;
        preCalculatedUnAnchoredResult = copy.preCalculatedUnAnchoredResult;
    }

    public DFAStateNodeBuilder createNodeSplitCopy(short copyID) {
        return new DFAStateNodeBuilder(this, copyID);
    }

    public void nodeSplitUpdateSuccessors(short[] newSuccessors, DFAStateNodeBuilder[] stateIndexMap) {
        for (int i = 0; i < getSuccessors().length; i++) {
            DFAStateNodeBuilder successor = stateIndexMap[newSuccessors[i]];
            assert successor != null;
            successor.setPredecessors(NODE_SPLIT_TAINTED);
            getSuccessors()[i].setTarget(successor);
        }
        if (hasBackwardPrefixState()) {
            assert newSuccessors.length == getSuccessors().length + 1;
            backwardPrefixState = newSuccessors[newSuccessors.length - 1];
        }
    }

    public void setNfaTransitionSet(TransitionSet<NFA, NFAState, NFAStateTransition> nfaTransitionSet) {
        this.nfaTransitionSet = nfaTransitionSet;
    }

    public TransitionSet<NFA, NFAState, NFAStateTransition> getNfaTransitionSet() {
        return nfaTransitionSet;
    }

    public void setOverrideFinalState(boolean overrideFinalState) {
        setFlag(FLAG_OVERRIDE_FINAL_STATE, overrideFinalState);
    }

    /**
     * Used in pruneUnambiguousPaths mode. States that are NOT final states or successors of final
     * states may have their last matcher replaced with an AnyMatcher.
     */
    public boolean isFinalStateSuccessor() {
        return getFlag(FLAG_FINAL_STATE_SUCCESSOR);
    }

    public void setFinalStateSuccessor() {
        setFlag(FLAG_FINAL_STATE_SUCCESSOR);
    }

    public boolean isBackwardPrefixState() {
        return getFlag(FLAG_BACKWARD_PREFIX_STATE);
    }

    public void setIsBackwardPrefixState(boolean backwardPrefixState) {
        setFlag(FLAG_BACKWARD_PREFIX_STATE, backwardPrefixState);
    }

    @Override
    public boolean isUnAnchoredFinalState() {
        return getFlag((byte) (FLAG_OVERRIDE_FINAL_STATE | FLAG_UN_ANCHORED_FINAL_STATE));
    }

    @Override
    public boolean isFinalState() {
        return getFlag((byte) (FLAG_OVERRIDE_FINAL_STATE | FLAG_ANY_FINAL_STATE));
    }

    public boolean isForward() {
        return getFlag(FLAG_FORWARD);
    }

    public boolean isPrioritySensitive() {
        return getFlag(FLAG_PRIORITY_SENSITIVE);
    }

    public int getNumberOfSuccessors() {
        return getSuccessors().length + (hasBackwardPrefixState() ? 1 : 0);
    }

    @Override
    protected DFAStateTransitionBuilder[] createTransitionsArray(int length) {
        return new DFAStateTransitionBuilder[length];
    }

    /**
     * Returns {@code true} iff the union of the {@link DFAStateTransitionBuilder#getCodePointSet()}
     * of all transitions in this state is equal to {@link Encoding#getFullSet()}.
     */
    public boolean coversFullCharSpace(CompilationBuffer compilationBuffer) {
        IntArrayBuffer indicesBuf = compilationBuffer.getIntRangesBuffer1();
        indicesBuf.ensureCapacity(getSuccessors().length);
        int[] indices = indicesBuf.getBuffer();
        Arrays.fill(indices, 0, getSuccessors().length, 0);
        int nextLo = compilationBuffer.getEncoding().getMinValue();
        while (true) {
            int i = findNextLo(indices, nextLo);
            if (i < 0) {
                return false;
            }
            CodePointSet ranges = getSuccessors()[i].getCodePointSet();
            if (ranges.getHi(indices[i]) == compilationBuffer.getEncoding().getMaxValue()) {
                return true;
            }
            nextLo = ranges.getHi(indices[i]) + 1;
            indices[i]++;
        }
    }

    private int findNextLo(int[] indices, int findLo) {
        for (int i = 0; i < getSuccessors().length; i++) {
            CodePointSet ranges = getSuccessors()[i].getCodePointSet();
            if (indices[i] == ranges.size()) {
                continue;
            }
            if (ranges.getLo(indices[i]) == findLo) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public DFAStateTransitionBuilder[] getPredecessors() {
        if (super.getPredecessors() == NODE_SPLIT_TAINTED) {
            throw CompilerDirectives.shouldNotReachHere(NODE_SPLIT_UNINITIALIZED_PRECEDING_TRANSITIONS_ERROR_MSG);
        }
        return super.getPredecessors();
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

    public DFAStateNodeBuilder updateFinalStateData(DFAGenerator dfaGenerator) {
        boolean forward = dfaGenerator.isForward();
        boolean traceFinder = dfaGenerator.getNfa().isTraceFinderNFA();
        if (forward) {
            for (NFAStateTransition t : nfaTransitionSet.getTransitions()) {
                // In forward mode, a state is final if it contains a NFA transition to a NFA state
                // that has a subsequent transition to a final state
                NFAState target = t.getTarget(true);
                if (target.hasTransitionToAnchoredFinalState(true) && anchoredFinalStateTransition == null) {
                    setAnchoredFinalState();
                    setAnchoredFinalStateTransition(target.getFirstTransitionToFinalState(true));
                }
                if (target.hasTransitionToUnAnchoredFinalState(true)) {
                    setUnAnchoredFinalState();
                    setUnAnchoredFinalStateTransition(target.getTransitionToUnAnchoredFinalState(true));
                    return this;
                }
            }
        } else {
            for (NFAStateTransition t : nfaTransitionSet.getTransitions()) {
                // In backward mode, a state is final if it contains a NFA transition to a NFA final
                // state
                NFAState target = t.getTarget(false);
                if (target.isAnchoredFinalState(false)) {
                    if (!(traceFinder && isBackwardPrefixState()) || target.hasPrefixStates()) {
                        if (traceFinder) {
                            assert target.hasPossibleResults() && target.getPossibleResults().numberOfSetBits() == 1;
                            updatePreCalcAnchoredResult(target.getPossibleResults().iterator().nextInt());
                        }
                        setAnchoredFinalState();
                        setAnchoredFinalStateTransition(t);
                    }
                }
                if (target.isUnAnchoredFinalState(false)) {
                    if (!(traceFinder && isBackwardPrefixState()) || target.hasPrefixStates()) {
                        if (traceFinder) {
                            assert target.hasPossibleResults() && target.getPossibleResults().numberOfSetBits() == 1;
                            updatePreCalcUnAnchoredResult(target.getPossibleResults().iterator().nextInt());
                        }
                        setUnAnchoredFinalState();
                        setUnAnchoredFinalStateTransition(t);
                    }
                }
            }
        }
        return this;
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
        int hashCode;
        if (isPrioritySensitive()) {
            hashCode = 1;
            for (int i = 0; i < nfaTransitionSet.size(); i++) {
                hashCode = 31 * hashCode + nfaTransitionSet.getTransition(i).getTarget().hashCode();
            }
        } else {
            hashCode = nfaTransitionSet.getTargetStateSet().hashCode();
        }
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
        if (isBackwardPrefixState() != o.isBackwardPrefixState()) {
            return false;
        }
        if (isPrioritySensitive()) {
            if (nfaTransitionSet.size() != o.nfaTransitionSet.size()) {
                return false;
            }
            for (int i = 0; i < nfaTransitionSet.size(); i++) {
                if (!nfaTransitionSet.getTransition(i).getTarget(isForward()).equals(o.nfaTransitionSet.getTransition(i).getTarget())) {
                    return false;
                }
            }
            return true;
        } else {
            return nfaTransitionSet.getTargetStateSet().equals(o.nfaTransitionSet.getTargetStateSet());
        }
    }

    @TruffleBoundary
    @Override
    protected boolean hasTransitionToUnAnchoredFinalState(boolean forward) {
        throw new UnsupportedOperationException();
    }

    @TruffleBoundary
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        return DebugUtil.appendNodeId(sb, getId()).append(": ").append(stateSetToString()).toString();
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        return Json.obj(Json.prop("id", getId()),
                        Json.prop("stateSet", Json.array(Arrays.stream(nfaTransitionSet.getTransitions()).map(x -> Json.val(x.getTarget(isForward()).getId())))),
                        Json.prop("finalState", isUnAnchoredFinalState()),
                        Json.prop("anchoredFinalState", isAnchoredFinalState()),
                        Json.prop("transitions", Arrays.stream(getSuccessors()).map(x -> Json.val(x.getId()))));
    }
}
