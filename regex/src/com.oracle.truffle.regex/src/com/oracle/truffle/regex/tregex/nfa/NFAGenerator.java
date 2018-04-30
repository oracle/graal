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
import com.oracle.truffle.regex.tregex.util.DebugUtil;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class NFAGenerator {

    private final RegexAST ast;
    private final Counter.ThresholdCounter stateID = new Counter.ThresholdCounter(TRegexOptions.TRegexMaxNFASize, "NFA explosion");
    private final Counter.ThresholdCounter transitionID = new Counter.ThresholdCounter(Short.MAX_VALUE, "NFA transition explosion");
    private final List<NFAAnchoredFinalState> anchoredInitialStates;
    private final List<NFAFinalState> initialStates;
    private final NFAAnchoredFinalState anchoredFinalState;
    private final NFAFinalState finalState;
    private final Deque<NFAState> expansionQueue = new ArrayDeque<>();
    private final Map<ASTNodeSet<? extends RegexASTNode>, NFAState> nfaStates = new HashMap<>();
    private final List<NFAState> hardPrefixStates = new ArrayList<>();
    private final ASTStepVisitor astStepVisitor;
    private final ASTTransitionCanonicalizer astTransitionCanonicalizer = new ASTTransitionCanonicalizer();

    private NFAGenerator(RegexAST ast, CompilationBuffer compilationBuffer) {
        this.ast = ast;
        this.astStepVisitor = new ASTStepVisitor(ast, compilationBuffer);
        stateID.inc();
        anchoredFinalState = new NFAAnchoredFinalState((short) stateID.inc(), new ASTNodeSet<>(ast, ast.getReachableDollars()));
        finalState = new NFAFinalState((short) stateID.inc(), new ASTNodeSet<>(ast, ast.getRoot().getSubTreeParent().getMatchFound()));
        nfaStates.put(anchoredFinalState.getStateSet(), anchoredFinalState);
        nfaStates.put(finalState.getStateSet(), finalState);
        anchoredInitialStates = new ArrayList<>(ast.getWrappedPrefixLength() + 1);
        initialStates = new ArrayList<>(ast.getWrappedPrefixLength() + 1);
        for (int i = 0; i <= ast.getWrappedPrefixLength(); i++) {
            final NFAFinalState initialState = new NFAFinalState((short) stateID.inc(), new ASTNodeSet<>(ast, ast.getNFAUnAnchoredInitialState(i)));
            final NFAAnchoredFinalState anchoredInitialState = new NFAAnchoredFinalState((short) stateID.inc(), new ASTNodeSet<>(ast, ast.getNFAAnchoredInitialState(i)));
            initialStates.add(initialState);
            anchoredInitialStates.add(anchoredInitialState);
            nfaStates.put(initialState.getStateSet(), initialState);
            nfaStates.put(anchoredInitialState.getStateSet(), anchoredInitialState);
        }
    }

    public static NFA createNFA(RegexAST ast, CompilationBuffer compilationBuffer) {
        return new NFAGenerator(ast, compilationBuffer).doCreateNFA();
    }

    private NFA doCreateNFA() {
        expansionQueue.addAll(initialStates);
        expansionQueue.addAll(anchoredInitialStates);
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
        if (DebugUtil.DEBUG) {
            dumpNFA();
        }
        return new NFA(ast, anchoredInitialStates, initialStates, anchoredFinalState, finalState, nfaStates.values(), stateID, transitionID, null);
    }

    private void findDeadStates(ArrayList<NFAState> deadStates) {
        for (NFAState state : nfaStates.values()) {
            if (state instanceof NFAMatcherState && (state.getNext().isEmpty() || state.getNext().size() == 1 && state.getNext().get(0).getTarget() == state)) {
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
        GroupBoundaries groupBoundaries;
        for (ASTSuccessor successor : nextStep.getSuccessors()) {
            for (ASTTransitionSetBuilder mergeBuilder : successor.getMergedStates(astTransitionCanonicalizer)) {
                stateSetCC = null;
                finishedLookBehinds = null;
                groupBoundaries = new GroupBoundaries();
                boolean containsPositionAssertion = false;
                boolean containsMatchFound = false;
                boolean containsPrefixStates = false;
                for (ASTTransition astTransition : mergeBuilder.getTargetState()) {
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
                    groupBoundaries.addAll(astTransition.getGroupBoundaries());
                }
                if (stateSetCC == null) {
                    if (containsPositionAssertion) {
                        transitions.add(createTransition(sourceState, anchoredFinalState, groupBoundaries));
                    } else if (containsMatchFound) {
                        transitions.add(createTransition(sourceState, finalState, groupBoundaries));
                    }
                } else if (!containsPositionAssertion) {
                    assert mergeBuilder.getMatcherBuilder().matchesSomething();
                    transitions.add(createTransition(sourceState,
                                    registerMatcherState(stateSetCC, mergeBuilder.getMatcherBuilder(), finishedLookBehinds, containsPrefixStates),
                                    groupBoundaries));
                }
            }
        }
        return transitions;
    }

    private NFAStateTransition createTransition(NFAState source, NFAState target, GroupBoundaries groupBoundaries) {
        return new NFAStateTransition((short) transitionID.inc(), source, target, groupBoundaries);
    }

    private NFAState registerMatcherState(ASTNodeSet<CharacterClass> stateSetCC,
                    MatcherBuilder matcherBuilder,
                    ASTNodeSet<LookBehindAssertion> finishedLookBehinds,
                    boolean containsPrefixStates) {
        if (nfaStates.containsKey(stateSetCC)) {
            return nfaStates.get(stateSetCC);
        } else {
            NFAState state = new NFAMatcherState((short) stateID.inc(), stateSetCC, matcherBuilder, finishedLookBehinds, containsPrefixStates);
            expansionQueue.push(state);
            nfaStates.put(state.getStateSet(), state);
            return state;
        }
    }

    private void dumpNFA() {
        System.out.println("NFA:");
        for (NFAState state : nfaStates.values()) {
            System.out.println(state.toTable());
        }
        System.out.println();
    }
}
