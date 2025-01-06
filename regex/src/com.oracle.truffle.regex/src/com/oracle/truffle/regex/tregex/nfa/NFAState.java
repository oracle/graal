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
package com.oracle.truffle.regex.tregex.nfa;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.graalvm.collections.EconomicMap;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.tregex.TRegexOptions;
import com.oracle.truffle.regex.tregex.automaton.BasicState;
import com.oracle.truffle.regex.tregex.automaton.StateSet;
import com.oracle.truffle.regex.tregex.parser.ast.LookBehindAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTNode;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonArray;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonObject;
import com.oracle.truffle.regex.util.TBitSet;

/**
 * Represents a single state in the NFA form of a regular expression. States may either be matcher
 * states or final states, where a matcher state matches a set of characters and a final state
 * indicates that a match has been found. A state may represent multiple nodes of a regex AST, if it
 * is the result of a product automaton composition of the "regular" regular expression and its
 * lookaround assertions, e.g. the NFA of an expression like /(?=[ab])a/ will contain a state that
 * matches both the 'a' in the lookahead assertion as well as following 'a' in the expression, and
 * therefore will have a state set containing two AST nodes.
 */
public final class NFAState extends BasicState<NFAState, NFAStateTransition> implements JsonConvertible {

    private static final byte FLAGS_NONE = 0;
    private static final byte FLAG_HAS_PREFIX_STATES = 1 << N_FLAGS;
    private static final byte FLAG_MUST_ADVANCE = 1 << N_FLAGS + 1;

    private static final NFAStateTransition[] EMPTY_TRANSITIONS = new NFAStateTransition[0];

    private final StateSet<RegexAST, ? extends RegexASTNode> stateSet;
    @CompilationFinal private short transitionToAnchoredFinalState = -1;
    @CompilationFinal private short transitionToUnAnchoredFinalState = -1;
    @CompilationFinal private short revTransitionToAnchoredFinalState = -1;
    @CompilationFinal private short revTransitionToUnAnchoredFinalState = -1;
    private TBitSet possibleResults;
    private final Set<LookBehindAssertion> finishedLookBehinds;
    private final EconomicMap<Integer, TBitSet> matchedConditionGroupsMap;

    public NFAState(short id,
                    StateSet<RegexAST, ? extends RegexASTNode> stateSet,
                    Set<LookBehindAssertion> finishedLookBehinds,
                    boolean hasPrefixStates,
                    boolean mustAdvance) {
        this(id, stateSet, initFlags(hasPrefixStates, mustAdvance), null, finishedLookBehinds, initMatchedConditionGroupsMap(stateSet));
    }

    public NFAState(short id,
                    StateSet<RegexAST, ? extends RegexASTNode> stateSet,
                    Set<LookBehindAssertion> finishedLookBehinds,
                    boolean hasPrefixStates,
                    boolean mustAdvance,
                    EconomicMap<Integer, TBitSet> matchedConditionGroupsMap) {
        this(id, stateSet, initFlags(hasPrefixStates, mustAdvance), null, finishedLookBehinds, matchedConditionGroupsMap);
    }

    private static EconomicMap<Integer, TBitSet> initMatchedConditionGroupsMap(StateSet<RegexAST, ? extends RegexASTNode> stateSet) {
        if (!stateSet.getStateIndex().getProperties().hasConditionalBackReferences()) {
            return null;
        }
        EconomicMap<Integer, TBitSet> matchedConditionGroupsMap = EconomicMap.create();
        for (RegexASTNode node : stateSet) {
            matchedConditionGroupsMap.put(node.getId(), TBitSet.getEmptyInstance());
        }
        return matchedConditionGroupsMap;
    }

    private static byte initFlags(boolean hasPrefixStates, boolean mustAdvance) {
        return (byte) ((hasPrefixStates ? FLAG_HAS_PREFIX_STATES : FLAGS_NONE) | (mustAdvance ? FLAG_MUST_ADVANCE : FLAGS_NONE));
    }

    private NFAState(short id,
                    StateSet<RegexAST, ? extends RegexASTNode> stateSet,
                    short flags,
                    Set<LookBehindAssertion> finishedLookBehinds,
                    EconomicMap<Integer, TBitSet> matchedConditionGroupsMap) {
        this(id, stateSet, flags, null, finishedLookBehinds, matchedConditionGroupsMap);
    }

    private NFAState(short id,
                    StateSet<RegexAST, ? extends RegexASTNode> stateSet,
                    short flags,
                    TBitSet possibleResults,
                    Set<LookBehindAssertion> finishedLookBehinds,
                    EconomicMap<Integer, TBitSet> matchedConditionGroupsMap) {
        super(id, EMPTY_TRANSITIONS);
        setFlag(flags);
        this.stateSet = stateSet;
        this.possibleResults = possibleResults;
        this.finishedLookBehinds = finishedLookBehinds;
        this.matchedConditionGroupsMap = matchedConditionGroupsMap;
    }

    public NFAState createTraceFinderCopy(short copyID) {
        return new NFAState(copyID, getStateSet(), getFlags(), finishedLookBehinds, matchedConditionGroupsMap);
    }

    public Set<LookBehindAssertion> getFinishedLookBehinds() {
        return finishedLookBehinds;
    }

    public StateSet<RegexAST, ? extends RegexASTNode> getStateSet() {
        return stateSet;
    }

    public boolean hasPrefixStates() {
        return getFlag(FLAG_HAS_PREFIX_STATES);
    }

    public void setHasPrefixStates(boolean value) {
        setFlag(FLAG_HAS_PREFIX_STATES, value);
    }

    public boolean isMustAdvance() {
        return getFlag(FLAG_MUST_ADVANCE);
    }

    public void setMustAdvance(boolean value) {
        setFlag(FLAG_MUST_ADVANCE, value);
    }

    public EconomicMap<Integer, TBitSet> getMatchedConditionGroupsMap() {
        return matchedConditionGroupsMap;
    }

    public TBitSet getMatchedConditionGroups(RegexASTNode t) {
        if (!stateSet.getStateIndex().getProperties().hasConditionalBackReferences()) {
            return TBitSet.getEmptyInstance();
        }
        assert matchedConditionGroupsMap.containsKey(t.getId());
        return matchedConditionGroupsMap.get(t.getId());
    }

    public TBitSet getMatchedConditionGroupsDebug() {
        if (!stateSet.getStateIndex().getProperties().hasConditionalBackReferences()) {
            return TBitSet.getEmptyInstance();
        }
        TBitSet matchedConditionGroups = new TBitSet(Long.SIZE);
        for (RegexASTNode t : stateSet) {
            matchedConditionGroups.union(matchedConditionGroupsMap.get(t.getId()));
        }
        return matchedConditionGroups;
    }

    public boolean hasTransitionToAnchoredFinalState(boolean forward) {
        return getTransitionToAnchoredFinalStateId(forward) >= 0;
    }

    public short getTransitionToAnchoredFinalStateId(boolean forward) {
        return forward ? transitionToAnchoredFinalState : revTransitionToAnchoredFinalState;
    }

    public NFAStateTransition getTransitionToAnchoredFinalState(boolean forward) {
        assert hasTransitionToAnchoredFinalState(forward);
        return getSuccessors(forward)[getTransitionToAnchoredFinalStateId(forward)];
    }

    @Override
    public boolean hasTransitionToUnAnchoredFinalState(boolean forward) {
        return getTransitionToUnAnchoredFinalStateId(forward) >= 0;
    }

    public NFAStateTransition getTransitionToUnAnchoredFinalState(boolean forward) {
        assert hasTransitionToUnAnchoredFinalState(forward);
        return getSuccessors(forward)[getTransitionToUnAnchoredFinalStateId(forward)];
    }

    public short getTransitionToUnAnchoredFinalStateId(boolean forward) {
        return forward ? transitionToUnAnchoredFinalState : revTransitionToUnAnchoredFinalState;
    }

    public boolean hasTransitionToFinalState(boolean forward) {
        return hasTransitionToAnchoredFinalState(forward) || hasTransitionToUnAnchoredFinalState(forward);
    }

    public int getFirstTransitionToFinalStateIndex(boolean forward) {
        assert hasTransitionToFinalState(forward);
        return Math.min(Short.toUnsignedInt(getTransitionToAnchoredFinalStateId(forward)), Short.toUnsignedInt(getTransitionToUnAnchoredFinalStateId(forward)));
    }

    public NFAStateTransition getFirstTransitionToFinalState(boolean forward) {
        return getSuccessors(forward)[getFirstTransitionToFinalStateIndex(forward)];
    }

    public void addLoopBackNext(NFAStateTransition transition) {
        // loopBack transitions always have minimal priority, so no sorting is necessary
        updateFinalStateTransitions(transition, (short) getSuccessors().length);
        setSuccessors(Arrays.copyOf(getSuccessors(), getSuccessors().length + 1));
        getSuccessors()[getSuccessors().length - 1] = transition;
    }

    public void removeLoopBackNext() {
        setSuccessors(Arrays.copyOf(getSuccessors(), getSuccessors().length - 1));
        if (transitionToAnchoredFinalState == getSuccessors().length) {
            transitionToAnchoredFinalState = -1;
        }
        if (transitionToUnAnchoredFinalState == getSuccessors().length) {
            transitionToUnAnchoredFinalState = -1;
        }
    }

    public void setSuccessors(NFAStateTransition[] transitions, boolean createReverseTransitions) {
        setSuccessors(transitions);
        for (short i = 0; i < transitions.length; i++) {
            NFAStateTransition t = transitions[i];
            updateFinalStateTransitions(t, i);
            if (createReverseTransitions) {
                t.getTarget().incPredecessors();
            }
        }
    }

    private void updateFinalStateTransitions(NFAStateTransition transition, short i) {
        if (transitionToAnchoredFinalState == -1 && transition.getTarget().isAnchoredFinalState()) {
            transitionToAnchoredFinalState = i;
        }
        if (transitionToUnAnchoredFinalState == -1 && transition.getTarget().isUnAnchoredFinalState()) {
            transitionToUnAnchoredFinalState = i;
        }
    }

    public void removeSuccessor(NFAState state) {
        int toRemove = 0;
        for (NFAStateTransition successor : getSuccessors()) {
            if (successor.getTarget() == state) {
                toRemove++;
            }
        }
        if (toRemove == 0) {
            return;
        }
        NFAStateTransition[] newNext = new NFAStateTransition[getSuccessors().length - toRemove];
        short iNew = 0;
        for (short i = 0; i < getSuccessors().length; i++) {
            if (getSuccessors()[i].getTarget() == state) {
                if (i == transitionToAnchoredFinalState) {
                    transitionToAnchoredFinalState = -1;
                }
                if (i == transitionToUnAnchoredFinalState) {
                    transitionToUnAnchoredFinalState = -1;
                }
            } else {
                if (i == transitionToAnchoredFinalState) {
                    transitionToAnchoredFinalState = iNew;
                }
                if (i == transitionToUnAnchoredFinalState) {
                    transitionToUnAnchoredFinalState = iNew;
                }
                newNext[iNew++] = getSuccessors()[i];
            }
        }
        setSuccessors(newNext);
    }

    public void linkPredecessors() {
        for (NFAStateTransition t : getSuccessors()) {
            t.getTarget().addPredecessor(t);
            if (isAnchoredInitialState()) {
                t.getTarget().revTransitionToAnchoredFinalState = (short) t.getTarget().getNPredecessors();
            }
            if (isUnAnchoredInitialState()) {
                t.getTarget().revTransitionToUnAnchoredFinalState = (short) t.getTarget().getNPredecessors();
            }
        }
    }

    /**
     * Set of possible pre-calculated result indices as generated by the
     * {@link NFATraceFinderGenerator}. This set must be sorted, since the index values indicate the
     * priority of their respective pre-calculated results. Example: /(a)|([ab])/ will yield two
     * possible results, where the one corresponding to capture group 1 will have the higher
     * priority, so when a single 'a' is encountered when searching for a match, the pre-calculated
     * result corresponding to capture group 1 must be preferred.
     */
    public TBitSet getPossibleResults() {
        if (possibleResults == null) {
            return TBitSet.getEmptyInstance();
        }
        return possibleResults;
    }

    public boolean hasPossibleResults() {
        return !(possibleResults == null || possibleResults.isEmpty());
    }

    public void addPossibleResult(int index) {
        if (possibleResults == null) {
            possibleResults = new TBitSet(TRegexOptions.TRegexTraceFinderMaxNumberOfResults);
        }
        possibleResults.set(index);
    }

    /**
     * Creates a copy of the {@code original} state. This copy is shallow as the state is just a
     * part of a larger cyclic graph. However, it has its own copy of the {@link #getSuccessors()}
     * and {@link #getPredecessors()} arrays. When copying the entire NFA, the
     * {@link #getSuccessors()} and {@link #getPredecessors()} must be updated to point to
     * transitions in the new NFA.
     */
    public NFAState(NFAState original) {
        super(original);
        this.stateSet = original.stateSet;
        this.transitionToAnchoredFinalState = original.transitionToAnchoredFinalState;
        this.transitionToUnAnchoredFinalState = original.transitionToUnAnchoredFinalState;
        this.revTransitionToAnchoredFinalState = original.revTransitionToAnchoredFinalState;
        this.revTransitionToUnAnchoredFinalState = original.revTransitionToUnAnchoredFinalState;
        this.possibleResults = original.possibleResults;
        this.finishedLookBehinds = original.finishedLookBehinds;
        this.matchedConditionGroupsMap = original.matchedConditionGroupsMap;
    }

    @TruffleBoundary
    public String idToString() {
        return getStateSet().stream().map(x -> String.valueOf(x.getId())).collect(Collectors.joining(",", "(", ")")) + "[" + getId() + "]";
    }

    @TruffleBoundary
    @Override
    public String toString() {
        return idToString();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof NFAState && getId() == ((NFAState) o).getId();
    }

    @Override
    public int hashCode() {
        return getId();
    }

    @Override
    protected NFAStateTransition[] createTransitionsArray(int length) {
        return new NFAStateTransition[length];
    }

    @TruffleBoundary
    private JsonArray sourceSectionsToJson() {
        return RegexAST.sourceSectionsToJson(getStateSet().stream().map(x -> getStateSet().getStateIndex().getSourceSections(x)).filter(Objects::nonNull).flatMap(Collection::stream));
    }

    @TruffleBoundary
    @Override
    public JsonObject toJson() {
        return Json.obj(Json.prop("id", getId()),
                        Json.prop("stateSet", getStateSet().stream().map(x -> Json.val(x.getId()))),
                        Json.prop("mustAdvance", isMustAdvance()),
                        Json.prop("sourceSections", sourceSectionsToJson()),
                        Json.prop("forwardAnchoredFinalState", isAnchoredFinalState()),
                        Json.prop("forwardUnAnchoredFinalState", isUnAnchoredFinalState()),
                        Json.prop("reverseAnchoredFinalState", isAnchoredInitialState()),
                        Json.prop("reverseUnAnchoredFinalState", isUnAnchoredInitialState()),
                        Json.prop("next", Arrays.stream(getSuccessors()).map(x -> Json.val(x.getId()))),
                        Json.prop("prev", Arrays.stream(getPredecessors()).map(x -> Json.val(x.getId()))));
    }

    @TruffleBoundary
    public JsonObject toJson(boolean forward) {
        return Json.obj(Json.prop("id", getId()),
                        Json.prop("stateSet", getStateSet().stream().map(x -> Json.val(x.getId()))),
                        Json.prop("matcherBuilder", Arrays.stream(getPredecessors()).findFirst().map(t -> t.getCodePointSet().toString()).orElse("")),
                        Json.prop("mustAdvance", isMustAdvance()),
                        Json.prop("sourceSections", sourceSectionsToJson()),
                        Json.prop("anchoredFinalState", isAnchoredFinalState(forward)),
                        Json.prop("unAnchoredFinalState", isUnAnchoredFinalState(forward)),
                        Json.prop("transitions", Arrays.stream(getSuccessors(forward)).map(x -> Json.val(x.getId()))));
    }
}
