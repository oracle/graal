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

import com.oracle.truffle.regex.tregex.TRegexOptions;
import com.oracle.truffle.regex.tregex.automaton.TransitionBuilder;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.matchers.MatcherBuilder;
import com.oracle.truffle.regex.tregex.parser.Counter;
import com.oracle.truffle.regex.tregex.parser.ast.CharacterClass;
import com.oracle.truffle.regex.tregex.parser.ast.LookBehindAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.MatchFound;
import com.oracle.truffle.regex.tregex.parser.ast.PositionAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTNode;
import com.oracle.truffle.regex.tregex.parser.ast.Sequence;
import com.oracle.truffle.regex.tregex.parser.ast.Term;
import com.oracle.truffle.regex.util.CompilationFinalBitSet;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class NFAGenerator {

    private final RegexAST ast;
    private final Counter.ThresholdCounter stateID = new Counter.ThresholdCounter(TRegexOptions.TRegexMaxNFASize, "NFA explosion");
    private final Counter.ThresholdCounter transitionID = new Counter.ThresholdCounter(Short.MAX_VALUE, "NFA transition explosion");
    private final NFAState dummyInitialState;
    private final NFAState[] anchoredInitialStates;
    private final NFAState[] initialStates;
    private final NFAState anchoredFinalState;
    private final NFAState finalState;
    private final NFAStateTransition[] anchoredEntries;
    private final NFAStateTransition[] unAnchoredEntries;
    private final NFAStateTransition anchoredReverseEntry;
    private final NFAStateTransition unAnchoredReverseEntry;
    private final Deque<NFAState> expansionQueue = new ArrayDeque<>();
    private final Map<ASTNodeSet<? extends RegexASTNode>, NFAState> nfaStates = new HashMap<>();
    private final List<NFAState> hardPrefixStates = new ArrayList<>();
    private final ASTStepVisitor astStepVisitor;
    private final ASTTransitionCanonicalizer astTransitionCanonicalizer = new ASTTransitionCanonicalizer();
    private final CompilationFinalBitSet transitionGBUpdateIndices;
    private final CompilationFinalBitSet transitionGBClearIndices;

    private NFAGenerator(RegexAST ast, CompilationBuffer compilationBuffer) {
        this.ast = ast;
        this.astStepVisitor = new ASTStepVisitor(ast, compilationBuffer);
        this.transitionGBUpdateIndices = new CompilationFinalBitSet(ast.getNumberOfCaptureGroups() * 2);
        this.transitionGBClearIndices = new CompilationFinalBitSet(ast.getNumberOfCaptureGroups() * 2);
        dummyInitialState = new NFAState((short) stateID.inc(), new ASTNodeSet<>(ast, ast.getWrappedRoot()), MatcherBuilder.createEmpty(), Collections.emptySet(), false);
        nfaStates.put(dummyInitialState.getStateSet(), dummyInitialState);
        anchoredFinalState = createFinalState(new ASTNodeSet<>(ast, ast.getReachableDollars()));
        anchoredFinalState.setForwardAnchoredFinalState(true);
        finalState = createFinalState(new ASTNodeSet<>(ast, ast.getRoot().getSubTreeParent().getMatchFound()));
        finalState.setForwardUnAnchoredFinalState(true);
        assert transitionGBUpdateIndices.isEmpty() && transitionGBClearIndices.isEmpty();
        anchoredReverseEntry = createTransition(anchoredFinalState, dummyInitialState);
        unAnchoredReverseEntry = createTransition(finalState, dummyInitialState);
        int nEntries = ast.getWrappedPrefixLength() + 1;
        initialStates = new NFAState[nEntries];
        unAnchoredEntries = new NFAStateTransition[nEntries];
        for (int i = 0; i <= ast.getWrappedPrefixLength(); i++) {
            NFAState initialState = createFinalState(new ASTNodeSet<>(ast, ast.getNFAUnAnchoredInitialState(i)));
            initialState.setReverseUnAnchoredFinalState(true);
            initialStates[i] = initialState;
            unAnchoredEntries[i] = createTransition(dummyInitialState, initialState);
        }
        if (ast.getReachableCarets().isEmpty()) {
            anchoredInitialStates = initialStates;
            anchoredEntries = unAnchoredEntries;
        } else {
            anchoredInitialStates = new NFAState[nEntries];
            anchoredEntries = new NFAStateTransition[nEntries];
            for (int i = 0; i <= ast.getWrappedPrefixLength(); i++) {
                NFAState anchoredInitialState = createFinalState(new ASTNodeSet<>(ast, ast.getNFAAnchoredInitialState(i)));
                anchoredInitialState.setReverseAnchoredFinalState(true);
                anchoredInitialStates[i] = anchoredInitialState;
                anchoredEntries[i] = createTransition(dummyInitialState, anchoredInitialState);
            }
        }
        ArrayList<NFAStateTransition> dummyInitNext = new ArrayList<>(nEntries * 2);
        Collections.addAll(dummyInitNext, anchoredEntries);
        Collections.addAll(dummyInitNext, unAnchoredEntries);
        ArrayList<NFAStateTransition> dummyInitPrev = new ArrayList<>(2);
        dummyInitPrev.add(anchoredReverseEntry);
        dummyInitPrev.add(unAnchoredReverseEntry);
        dummyInitialState.setNext(dummyInitNext, false);
        dummyInitialState.setPrev(dummyInitPrev);
    }

    public static NFA createNFA(RegexAST ast, CompilationBuffer compilationBuffer) {
        return new NFAGenerator(ast, compilationBuffer).doCreateNFA();
    }

    private NFA doCreateNFA() {
        Collections.addAll(expansionQueue, initialStates);
        if (!ast.getReachableCarets().isEmpty()) {
            Collections.addAll(expansionQueue, anchoredInitialStates);
        }
        while (!expansionQueue.isEmpty()) {
            expandNFAState(expansionQueue.pop());
        }
        ArrayList<NFAState> deadStates = new ArrayList<>();
        findDeadStates(deadStates);
        while (!deadStates.isEmpty()) {
            for (NFAState state : deadStates) {
                for (NFAStateTransition pre : state.getPrev()) {
                    pre.getSource().removeNext(state);
                }
                // hardPrefixStates are not reachable by prev-transitions
                for (NFAState prefixState : hardPrefixStates) {
                    prefixState.removeNext(state);
                }
                nfaStates.remove(state.getStateSet());
            }
            deadStates.clear();
            findDeadStates(deadStates);
        }
        assert transitionGBUpdateIndices.isEmpty() && transitionGBClearIndices.isEmpty();
        for (int i = 1; i < initialStates.length; i++) {
            // check if state was eliminated by findDeadStates
            if (nfaStates.containsKey(initialStates[i].getStateSet())) {
                initialStates[i].addLoopBackNext(createTransition(initialStates[i], initialStates[i - 1]));
            }
        }
        return new NFA(ast, dummyInitialState, anchoredEntries, unAnchoredEntries, anchoredReverseEntry, unAnchoredReverseEntry, nfaStates.values(), stateID, transitionID, null);
    }

    private void findDeadStates(ArrayList<NFAState> deadStates) {
        for (NFAState state : nfaStates.values()) {
            if (!state.isForwardFinalState() && (state.getNext().isEmpty() || state.getNext().size() == 1 && state.getNext().get(0).getTarget() == state)) {
                deadStates.add(state);
            }
        }
    }

    private void expandNFAState(NFAState curState) {
        ASTStep nextStep = astStepVisitor.step(curState);
        // hard prefix states are non-optional, they are used only in forward search mode when
        // fromIndex > 0.
        boolean isHardPrefixState = !ast.getHardPrefixNodes().isDisjoint(curState.getStateSet());
        if (isHardPrefixState) {
            hardPrefixStates.add(curState);
        }
        curState.setNext(createNFATransitions(curState, nextStep), !isHardPrefixState);
    }

    private ArrayList<NFAStateTransition> createNFATransitions(NFAState sourceState, ASTStep nextStep) {
        ArrayList<NFAStateTransition> transitions = new ArrayList<>();
        ASTNodeSet<CharacterClass> stateSetCC;
        ASTNodeSet<LookBehindAssertion> finishedLookBehinds;
        for (ASTSuccessor successor : nextStep.getSuccessors()) {
            for (TransitionBuilder<ASTTransitionSet> mergeBuilder : successor.getMergedStates(astTransitionCanonicalizer)) {
                stateSetCC = null;
                finishedLookBehinds = null;
                boolean containsPositionAssertion = false;
                boolean containsMatchFound = false;
                boolean containsPrefixStates = false;
                for (ASTTransition astTransition : mergeBuilder.getTransitionSet()) {
                    Term target = astTransition.getTarget();
                    if (target instanceof CharacterClass) {
                        if (stateSetCC == null) {
                            stateSetCC = new ASTNodeSet<>(ast);
                            finishedLookBehinds = new ASTNodeSet<>(ast);
                        }
                        stateSetCC.add((CharacterClass) target);
                        if (target.isInLookBehindAssertion() && target == ((Sequence) target.getParent()).getLastTerm()) {
                            finishedLookBehinds.add((LookBehindAssertion) target.getSubTreeParent());
                        }
                    } else if (target instanceof PositionAssertion) {
                        containsPositionAssertion = true;
                    } else {
                        assert target instanceof MatchFound;
                        containsMatchFound = true;
                    }
                    containsPrefixStates |= target.isPrefix();
                    astTransition.getGroupBoundaries().updateBitSets(transitionGBUpdateIndices, transitionGBClearIndices);
                }
                if (stateSetCC == null) {
                    if (containsPositionAssertion) {
                        transitions.add(createTransition(sourceState, anchoredFinalState));
                    } else if (containsMatchFound) {
                        transitions.add(createTransition(sourceState, finalState));
                    }
                } else if (!containsPositionAssertion) {
                    assert mergeBuilder.getMatcherBuilder().matchesSomething();
                    transitions.add(createTransition(sourceState,
                                    registerMatcherState(stateSetCC, mergeBuilder.getMatcherBuilder(), finishedLookBehinds, containsPrefixStates)));
                }
                transitionGBUpdateIndices.clear();
                transitionGBClearIndices.clear();
            }
        }
        return transitions;
    }

    private NFAState createFinalState(ASTNodeSet<? extends RegexASTNode> stateSet) {
        NFAState state = new NFAState((short) stateID.inc(), stateSet, MatcherBuilder.createFull(), Collections.emptySet(), false);
        assert !nfaStates.containsKey(state.getStateSet());
        nfaStates.put(state.getStateSet(), state);
        return state;
    }

    private NFAStateTransition createTransition(NFAState source, NFAState target) {
        return new NFAStateTransition((short) transitionID.inc(), source, target, ast.createGroupBoundaries(transitionGBUpdateIndices, transitionGBClearIndices));
    }

    private NFAState registerMatcherState(ASTNodeSet<CharacterClass> stateSetCC,
                    MatcherBuilder matcherBuilder,
                    ASTNodeSet<LookBehindAssertion> finishedLookBehinds,
                    boolean containsPrefixStates) {
        if (nfaStates.containsKey(stateSetCC)) {
            return nfaStates.get(stateSetCC);
        } else {
            NFAState state = new NFAState((short) stateID.inc(), stateSetCC, matcherBuilder, finishedLookBehinds, containsPrefixStates);
            expansionQueue.push(state);
            nfaStates.put(state.getStateSet(), state);
            return state;
        }
    }
}
