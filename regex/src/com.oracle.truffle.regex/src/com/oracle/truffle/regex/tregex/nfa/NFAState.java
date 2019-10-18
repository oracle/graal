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
package com.oracle.truffle.regex.tregex.nfa;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.charset.CharSet;
import com.oracle.truffle.regex.tregex.automaton.IndexedState;
import com.oracle.truffle.regex.tregex.parser.ast.LookBehindAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTNode;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonArray;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonObject;

/**
 * Represents a single state in the NFA form of a regular expression. States may either be matcher
 * states or final states, where a matcher state matches a set of characters and a final state
 * indicates that a match has been found. A state may represent multiple nodes of a regex AST, if it
 * is the result of a product automaton composition of the "regular" regular expression and its
 * lookaround assertions, e.g. the NFA of an expression like /(?=[ab])a/ will contain a state that
 * matches both the 'a' in the lookahead assertion as well as following 'a' in the expression, and
 * therefore will have a state set containing two AST nodes.
 */
public class NFAState implements IndexedState, JsonConvertible {

    private static final byte FLAGS_NONE = 0;
    private static final byte FLAG_HAS_PREFIX_STATES = 1;
    private static final byte FLAG_FORWARD_ANCHORED_FINAL_STATE = 1 << 1;
    private static final byte FLAG_FORWARD_UN_ANCHORED_FINAL_STATE = 1 << 2;
    private static final byte FLAG_REVERSE_ANCHORED_FINAL_STATE = 1 << 3;
    private static final byte FLAG_REVERSE_UN_ANCHORED_FINAL_STATE = 1 << 4;
    private static final byte MASK_FORWARD_FINAL_STATES = FLAG_FORWARD_ANCHORED_FINAL_STATE | FLAG_FORWARD_UN_ANCHORED_FINAL_STATE;
    private static final byte MASK_REVERSE_FINAL_STATES = FLAG_REVERSE_ANCHORED_FINAL_STATE | FLAG_REVERSE_UN_ANCHORED_FINAL_STATE;

    private static final NFAStateTransition[] EMPTY_TRANSITIONS = new NFAStateTransition[0];

    private final short id;
    private final ASTNodeSet<? extends RegexASTNode> stateSet;
    @CompilationFinal private byte flags;
    @CompilationFinal private short transitionToAnchoredFinalState = -1;
    @CompilationFinal private short transitionToUnAnchoredFinalState = -1;
    @CompilationFinal private short revTransitionToAnchoredFinalState = -1;
    @CompilationFinal private short revTransitionToUnAnchoredFinalState = -1;
    @CompilationFinal(dimensions = 1) private NFAStateTransition[] next;
    @CompilationFinal(dimensions = 1) private NFAStateTransition[] prev;
    private short prevLength = 0;
    private List<Integer> possibleResults;
    private final CharSet matcherBuilder;
    private final Set<LookBehindAssertion> finishedLookBehinds;

    public NFAState(short id,
                    ASTNodeSet<? extends RegexASTNode> stateSet,
                    CharSet matcherBuilder,
                    Set<LookBehindAssertion> finishedLookBehinds,
                    boolean hasPrefixStates) {
        this(id, stateSet, hasPrefixStates ? FLAG_HAS_PREFIX_STATES : FLAGS_NONE,
                        EMPTY_TRANSITIONS, EMPTY_TRANSITIONS, null, matcherBuilder, finishedLookBehinds);
    }

    private NFAState(short id,
                    ASTNodeSet<? extends RegexASTNode> stateSet,
                    byte flags,
                    CharSet matcherBuilder,
                    Set<LookBehindAssertion> finishedLookBehinds) {
        this(id, stateSet, flags, EMPTY_TRANSITIONS, EMPTY_TRANSITIONS, null, matcherBuilder, finishedLookBehinds);
    }

    private NFAState(short id,
                    ASTNodeSet<? extends RegexASTNode> stateSet,
                    byte flags,
                    NFAStateTransition[] next,
                    NFAStateTransition[] prev,
                    List<Integer> possibleResults,
                    CharSet matcherBuilder,
                    Set<LookBehindAssertion> finishedLookBehinds) {
        this.id = id;
        this.stateSet = stateSet;
        this.flags = flags;
        this.next = next;
        this.prev = prev;
        this.possibleResults = possibleResults;
        this.matcherBuilder = matcherBuilder;
        this.finishedLookBehinds = finishedLookBehinds;
    }

    public NFAState createTraceFinderCopy(short copyID) {
        return new NFAState(copyID, getStateSet(), getFlags(), matcherBuilder, finishedLookBehinds);
    }

    public CharSet getCharSet() {
        return matcherBuilder;
    }

    public Set<LookBehindAssertion> getFinishedLookBehinds() {
        return finishedLookBehinds;
    }

    public ASTNodeSet<? extends RegexASTNode> getStateSet() {
        return stateSet;
    }

    private boolean isFlagSet(byte flag) {
        return (flags & flag) != 0;
    }

    private void setFlag(byte flag, boolean value) {
        if (value) {
            flags |= flag;
        } else {
            flags &= ~flag;
        }
    }

    byte getFlags() {
        return flags;
    }

    public boolean hasPrefixStates() {
        return isFlagSet(FLAG_HAS_PREFIX_STATES);
    }

    public void setHasPrefixStates(boolean value) {
        setFlag(FLAG_HAS_PREFIX_STATES, value);
    }

    public boolean isFinalState(boolean forward) {
        return forward ? isForwardFinalState() : isReverseFinalState();
    }

    public boolean isAnchoredFinalState(boolean forward) {
        return isFlagSet(forward ? FLAG_FORWARD_ANCHORED_FINAL_STATE : FLAG_REVERSE_ANCHORED_FINAL_STATE);
    }

    public boolean isUnAnchoredFinalState(boolean forward) {
        return isFlagSet(forward ? FLAG_FORWARD_UN_ANCHORED_FINAL_STATE : FLAG_REVERSE_UN_ANCHORED_FINAL_STATE);
    }

    public boolean isForwardFinalState() {
        return isFlagSet(MASK_FORWARD_FINAL_STATES);
    }

    public boolean isForwardAnchoredFinalState() {
        return isFlagSet(FLAG_FORWARD_ANCHORED_FINAL_STATE);
    }

    public void setForwardAnchoredFinalState(boolean value) {
        setFlag(FLAG_FORWARD_ANCHORED_FINAL_STATE, value);
    }

    public boolean isForwardUnAnchoredFinalState() {
        return isFlagSet(FLAG_FORWARD_UN_ANCHORED_FINAL_STATE);
    }

    public void setForwardUnAnchoredFinalState(boolean value) {
        setFlag(FLAG_FORWARD_UN_ANCHORED_FINAL_STATE, value);
    }

    public boolean isReverseFinalState() {
        return isFlagSet(MASK_REVERSE_FINAL_STATES);
    }

    public boolean isReverseAnchoredFinalState() {
        return isFlagSet(FLAG_REVERSE_ANCHORED_FINAL_STATE);
    }

    public void setReverseAnchoredFinalState(boolean value) {
        setFlag(FLAG_REVERSE_ANCHORED_FINAL_STATE, value);
    }

    public boolean isReverseUnAnchoredFinalState() {
        return isFlagSet(FLAG_REVERSE_UN_ANCHORED_FINAL_STATE);
    }

    public void setReverseUnAnchoredFinalState(boolean value) {
        setFlag(FLAG_REVERSE_UN_ANCHORED_FINAL_STATE, value);
    }

    public boolean hasTransitionToAnchoredFinalState(boolean forward) {
        return (forward ? transitionToAnchoredFinalState : revTransitionToAnchoredFinalState) >= 0;
    }

    public short getTransitionToAnchoredFinalStateId(boolean forward) {
        return forward ? transitionToAnchoredFinalState : revTransitionToAnchoredFinalState;
    }

    public NFAStateTransition getTransitionToAnchoredFinalState(boolean forward) {
        assert hasTransitionToAnchoredFinalState(forward);
        return forward ? next[transitionToAnchoredFinalState] : prev[revTransitionToAnchoredFinalState];
    }

    public boolean hasTransitionToUnAnchoredFinalState(boolean forward) {
        return (forward ? transitionToUnAnchoredFinalState : revTransitionToUnAnchoredFinalState) >= 0;
    }

    public NFAStateTransition getTransitionToUnAnchoredFinalState(boolean forward) {
        assert hasTransitionToUnAnchoredFinalState(forward);
        return forward ? next[transitionToUnAnchoredFinalState] : prev[revTransitionToUnAnchoredFinalState];
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
        return getNext(forward)[getFirstTransitionToFinalStateIndex(forward)];
    }

    /**
     * List of possible next states, sorted by priority.
     */
    public NFAStateTransition[] getNext() {
        return next;
    }

    public NFAStateTransition[] getNext(boolean forward) {
        return forward ? next : prev;
    }

    public void addLoopBackNext(NFAStateTransition transition) {
        // loopBack transitions always have minimal priority, so no sorting is necessary
        updateFinalStateTransitions(transition, (short) next.length);
        next = Arrays.copyOf(next, next.length + 1);
        next[next.length - 1] = transition;
    }

    public void removeLoopBackNext() {
        next = Arrays.copyOf(next, next.length - 1);
        if (transitionToAnchoredFinalState == next.length) {
            transitionToAnchoredFinalState = -1;
        }
        if (transitionToUnAnchoredFinalState == next.length) {
            transitionToUnAnchoredFinalState = -1;
        }
    }

    public void setNext(NFAStateTransition[] transitions, boolean createReverseTransitions) {
        this.next = transitions;
        for (short i = 0; i < transitions.length; i++) {
            NFAStateTransition t = transitions[i];
            updateFinalStateTransitions(t, i);
            if (createReverseTransitions) {
                t.getTarget().prevLength++;
            }
        }
    }

    private void updateFinalStateTransitions(NFAStateTransition transition, short i) {
        if (transitionToAnchoredFinalState == -1 && transition.getTarget().isForwardAnchoredFinalState()) {
            transitionToAnchoredFinalState = i;
        }
        if (transitionToUnAnchoredFinalState == -1 && transition.getTarget().isForwardUnAnchoredFinalState()) {
            transitionToUnAnchoredFinalState = i;
        }
    }

    public void removeNext(NFAState state) {
        int remove = indexOfTransition(state);
        if (remove == -1) {
            return;
        }
        NFAStateTransition[] newNext = new NFAStateTransition[next.length - 1];
        System.arraycopy(next, 0, newNext, 0, remove);
        System.arraycopy(next, remove + 1, newNext, remove, newNext.length - remove);
        next = newNext;
        if (transitionToAnchoredFinalState == remove) {
            transitionToAnchoredFinalState = -1;
        } else if (transitionToAnchoredFinalState > remove) {
            transitionToAnchoredFinalState--;
        }
        if (transitionToUnAnchoredFinalState == remove) {
            transitionToUnAnchoredFinalState = -1;
        } else if (transitionToUnAnchoredFinalState > remove) {
            transitionToUnAnchoredFinalState--;
        }
    }

    private int indexOfTransition(NFAState target) {
        for (int i = 0; i < next.length; i++) {
            if (next[i].getTarget() == target) {
                return i;
            }
        }
        return -1;
    }

    public void linkPrev() {
        for (NFAStateTransition t : next) {
            if (t.getTarget().prev == EMPTY_TRANSITIONS) {
                t.getTarget().prev = new NFAStateTransition[t.getTarget().prevLength];
            }
            t.getTarget().prevLength--;
            if (isReverseAnchoredFinalState()) {
                t.getTarget().revTransitionToAnchoredFinalState = t.getTarget().prevLength;
            }
            if (isReverseUnAnchoredFinalState()) {
                t.getTarget().revTransitionToUnAnchoredFinalState = t.getTarget().prevLength;
            }
            t.getTarget().prev[t.getTarget().prevLength] = t;
        }
    }

    public void setPrev(NFAStateTransition[] transitions) {
        this.prev = transitions;
    }

    /**
     * List of possible previous states, unsorted.
     */
    public NFAStateTransition[] getPrev() {
        return prev;
    }

    public NFAStateTransition[] getPrev(boolean forward) {
        return forward ? prev : next;
    }

    @Override
    public short getId() {
        return id;
    }

    /**
     * Set of possible pre-calculated result indices as generated by the
     * {@link NFATraceFinderGenerator}. This set must be sorted, since the index values indicate the
     * priority of their respective pre-calculated results. Example: /(a)|([ab])/ will yield two
     * possible results, where the one corresponding to capture group 1 will have the higher
     * priority, so when a single 'a' is encountered when searching for a match, the pre-calculated
     * result corresponding to capture group 1 must be preferred.
     */
    public List<Integer> getPossibleResults() {
        if (possibleResults == null) {
            return Collections.emptyList();
        }
        return possibleResults;
    }

    public boolean hasPossibleResults() {
        return !(possibleResults == null || possibleResults.isEmpty());
    }

    public void addPossibleResult(int index) {
        if (possibleResults == null) {
            possibleResults = new ArrayList<>();
        }
        int searchResult = Collections.binarySearch(possibleResults, index);
        if (searchResult < 0) {
            possibleResults.add((searchResult + 1) * -1, index);
        }
    }

    public boolean isDead(boolean forward) {
        return !isFinalState(forward) && (getNext(forward).length == 0 || getNext(forward).length == 1 && getNext(forward)[0].getTarget(forward) == this);
    }

    @TruffleBoundary
    public String idToString() {
        return getStateSet().stream().map(x -> String.valueOf(x.getId())).collect(Collectors.joining(",", "(", ")")) + "[" + id + "]";
    }

    @TruffleBoundary
    @Override
    public String toString() {
        return idToString();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof NFAState && id == ((NFAState) o).id;
    }

    @Override
    public int hashCode() {
        return id;
    }

    @TruffleBoundary
    private JsonArray sourceSectionsToJson() {
        return Json.array(getStateSet().stream().map(x -> getStateSet().getAst().getSourceSections(x)).filter(Objects::nonNull).flatMap(Collection::stream).map(x -> Json.obj(
                        Json.prop("start", x.getCharIndex()),
                        Json.prop("end", x.getCharEndIndex()))));
    }

    @TruffleBoundary
    @Override
    public JsonObject toJson() {
        return Json.obj(Json.prop("id", id),
                        Json.prop("stateSet", getStateSet().stream().map(x -> Json.val(x.getId()))),
                        Json.prop("sourceSections", sourceSectionsToJson()),
                        Json.prop("matcherBuilder", matcherBuilder.toString()),
                        Json.prop("forwardAnchoredFinalState", isForwardAnchoredFinalState()),
                        Json.prop("forwardUnAnchoredFinalState", isForwardUnAnchoredFinalState()),
                        Json.prop("reverseAnchoredFinalState", isReverseAnchoredFinalState()),
                        Json.prop("reverseUnAnchoredFinalState", isReverseUnAnchoredFinalState()),
                        Json.prop("next", Arrays.stream(next).map(x -> Json.val(x.getId()))),
                        Json.prop("prev", Arrays.stream(prev).map(x -> Json.val(x.getId()))));
    }

    @TruffleBoundary
    public JsonObject toJson(boolean forward) {
        return Json.obj(Json.prop("id", id),
                        Json.prop("stateSet", getStateSet().stream().map(x -> Json.val(x.getId()))),
                        Json.prop("sourceSections", sourceSectionsToJson()),
                        Json.prop("matcherBuilder", matcherBuilder.toString()),
                        Json.prop("anchoredFinalState", isAnchoredFinalState(forward)),
                        Json.prop("unAnchoredFinalState", isUnAnchoredFinalState(forward)),
                        Json.prop("transitions", Arrays.stream(getNext(forward)).map(x -> Json.val(x.getId()))));
    }
}
