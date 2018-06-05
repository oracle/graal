/*
 * Copyright (c) 2016, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.nfa;

import com.oracle.truffle.regex.tregex.automaton.IndexedState;
import com.oracle.truffle.regex.tregex.matchers.MatcherBuilder;
import com.oracle.truffle.regex.tregex.parser.ast.LookBehindAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTNode;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonArray;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

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

    private final short id;
    private final ASTNodeSet<? extends RegexASTNode> stateSet;
    private byte flags;
    private short transitionToAnchoredFinalState = -1;
    private short transitionToUnAnchoredFinalState = -1;
    private short revTransitionToAnchoredFinalState = -1;
    private short revTransitionToUnAnchoredFinalState = -1;
    private List<NFAStateTransition> next;
    private List<NFAStateTransition> prev;
    private List<Integer> possibleResults;
    private final MatcherBuilder matcherBuilder;
    private final Set<LookBehindAssertion> finishedLookBehinds;

    public NFAState(short id,
                    ASTNodeSet<? extends RegexASTNode> stateSet,
                    MatcherBuilder matcherBuilder,
                    Set<LookBehindAssertion> finishedLookBehinds,
                    boolean hasPrefixStates) {
        this(id, stateSet, hasPrefixStates ? FLAG_HAS_PREFIX_STATES : FLAGS_NONE,
                        new ArrayList<>(), new ArrayList<>(), null, matcherBuilder, finishedLookBehinds);
    }

    private NFAState(short id,
                    ASTNodeSet<? extends RegexASTNode> stateSet,
                    byte flags,
                    MatcherBuilder matcherBuilder,
                    Set<LookBehindAssertion> finishedLookBehinds) {
        this(id, stateSet, flags, new ArrayList<>(), new ArrayList<>(), null, matcherBuilder, finishedLookBehinds);
    }

    private NFAState(short id,
                    ASTNodeSet<? extends RegexASTNode> stateSet,
                    byte flags,
                    List<NFAStateTransition> next,
                    List<NFAStateTransition> prev,
                    List<Integer> possibleResults,
                    MatcherBuilder matcherBuilder,
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

    public MatcherBuilder getMatcherBuilder() {
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

    public NFAStateTransition getTransitionToAnchoredFinalState(boolean forward) {
        assert hasTransitionToAnchoredFinalState(forward);
        return forward ? next.get(transitionToAnchoredFinalState) : prev.get(revTransitionToAnchoredFinalState);
    }

    public boolean hasTransitionToUnAnchoredFinalState(boolean forward) {
        return (forward ? transitionToUnAnchoredFinalState : revTransitionToUnAnchoredFinalState) >= 0;
    }

    public NFAStateTransition getTransitionToUnAnchoredFinalState(boolean forward) {
        assert hasTransitionToUnAnchoredFinalState(forward);
        return forward ? next.get(transitionToUnAnchoredFinalState) : prev.get(revTransitionToUnAnchoredFinalState);
    }

    /**
     * List of possible next states, sorted by priority.
     */
    public List<NFAStateTransition> getNext() {
        return next;
    }

    public List<NFAStateTransition> getNext(boolean forward) {
        return forward ? next : prev;
    }

    public void addLoopBackNext(NFAStateTransition transition) {
        // loopBack transitions always have minimal priority, so no sorting is necessary
        updateFinalStateTransitions(transition, (short) next.size());
        next.add(transition);
    }

    public void removeLoopBackNext() {
        next.remove(next.size() - 1);
        if (transitionToAnchoredFinalState == next.size()) {
            transitionToAnchoredFinalState = -1;
        }
        if (transitionToUnAnchoredFinalState == next.size()) {
            transitionToUnAnchoredFinalState = -1;
        }
    }

    public void setNext(ArrayList<NFAStateTransition> transitions, boolean createReverseTransitions) {
        this.next = transitions;
        for (short i = 0; i < transitions.size(); i++) {
            NFAStateTransition t = transitions.get(i);
            updateFinalStateTransitions(t, i);
            if (createReverseTransitions) {
                if (isReverseAnchoredFinalState()) {
                    t.getTarget().revTransitionToAnchoredFinalState = (short) t.getTarget().prev.size();
                }
                if (isReverseUnAnchoredFinalState()) {
                    t.getTarget().revTransitionToUnAnchoredFinalState = (short) t.getTarget().prev.size();
                }
                t.getTarget().prev.add(t);
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
        next.removeIf(x -> x.getTarget() == state);
    }

    public void setPrev(ArrayList<NFAStateTransition> transitions) {
        this.prev = transitions;
    }

    /**
     * List of possible previous states, unsorted.
     */
    public List<NFAStateTransition> getPrev() {
        return prev;
    }

    public List<NFAStateTransition> getPrev(boolean forward) {
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

    @TruffleBoundary
    public String idToString() {
        return getStateSet().stream().map(x -> String.valueOf(x.getId())).collect(Collectors.joining(",", "(", ")")) + "[" + id + "]";
    }

    @TruffleBoundary
    @Override
    public String toString() {
        return idToString();
    }

    @TruffleBoundary
    private JsonArray sourceSectionsToJson() {
        return Json.array(getStateSet().stream().map(RegexASTNode::getSourceSection).filter(Objects::nonNull).map(x -> Json.obj(
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
                        Json.prop("next", next.stream().map(x -> Json.val(x.getId()))),
                        Json.prop("prev", next.stream().map(x -> Json.val(x.getId()))));
    }

    @TruffleBoundary
    public JsonObject toJson(boolean forward) {
        return Json.obj(Json.prop("id", id),
                        Json.prop("stateSet", getStateSet().stream().map(x -> Json.val(x.getId()))),
                        Json.prop("sourceSections", sourceSectionsToJson()),
                        Json.prop("matcherBuilder", matcherBuilder.toString()),
                        Json.prop("anchoredFinalState", isAnchoredFinalState(forward)),
                        Json.prop("unAnchoredFinalState", isUnAnchoredFinalState(forward)),
                        Json.prop("transitions", getNext(forward).stream().map(x -> Json.val(x.getId()))));
    }
}
