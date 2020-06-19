/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.regex.charset.CodePointSet;
import com.oracle.truffle.regex.tregex.TRegexOptions;
import com.oracle.truffle.regex.tregex.automaton.StateSet;
import com.oracle.truffle.regex.tregex.automaton.TransitionBuilder;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
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
    private final Map<StateSet<RegexAST, ? extends RegexASTNode>, NFAState> nfaStates = new HashMap<>();
    private final List<NFAState> hardPrefixStates = new ArrayList<>();
    private final ASTStepVisitor astStepVisitor;
    private final ASTTransitionCanonicalizer astTransitionCanonicalizer;
    private final CompilationFinalBitSet transitionGBUpdateIndices;
    private final CompilationFinalBitSet transitionGBClearIndices;
    private final ArrayList<NFAStateTransition> transitionsBuffer = new ArrayList<>();
    private final CompilationBuffer compilationBuffer;

    private NFAGenerator(RegexAST ast, CompilationBuffer compilationBuffer) {
        this.ast = ast;
        this.astStepVisitor = new ASTStepVisitor(ast);
        this.transitionGBUpdateIndices = new CompilationFinalBitSet(ast.getNumberOfCaptureGroups() * 2);
        this.transitionGBClearIndices = new CompilationFinalBitSet(ast.getNumberOfCaptureGroups() * 2);
        this.astTransitionCanonicalizer = new ASTTransitionCanonicalizer(ast, true, false);
        this.compilationBuffer = compilationBuffer;
        dummyInitialState = new NFAState((short) stateID.inc(), StateSet.create(ast, ast.getWrappedRoot()), CodePointSet.getEmpty(), Collections.emptySet(), false);
        nfaStates.put(dummyInitialState.getStateSet(), dummyInitialState);
        anchoredFinalState = createFinalState(StateSet.create(ast, ast.getReachableDollars()));
        anchoredFinalState.setAnchoredFinalState();
        finalState = createFinalState(StateSet.create(ast, ast.getRoot().getSubTreeParent().getMatchFound()));
        finalState.setUnAnchoredFinalState();
        assert transitionGBUpdateIndices.isEmpty() && transitionGBClearIndices.isEmpty();
        anchoredReverseEntry = createTransition(anchoredFinalState, dummyInitialState, ast.getEncoding().getFullSet());
        unAnchoredReverseEntry = createTransition(finalState, dummyInitialState, ast.getEncoding().getFullSet());
        int nEntries = ast.getWrappedPrefixLength() + 1;
        initialStates = new NFAState[nEntries];
        unAnchoredEntries = new NFAStateTransition[nEntries];
        for (int i = 0; i <= ast.getWrappedPrefixLength(); i++) {
            NFAState initialState = createFinalState(StateSet.create(ast, ast.getNFAUnAnchoredInitialState(i)));
            initialState.setUnAnchoredInitialState(true);
            initialStates[i] = initialState;
            unAnchoredEntries[i] = createTransition(dummyInitialState, initialState, ast.getEncoding().getFullSet());
        }
        if (ast.getReachableCarets().isEmpty()) {
            anchoredInitialStates = initialStates;
            anchoredEntries = unAnchoredEntries;
        } else {
            anchoredInitialStates = new NFAState[nEntries];
            anchoredEntries = new NFAStateTransition[nEntries];
            for (int i = 0; i <= ast.getWrappedPrefixLength(); i++) {
                NFAState anchoredInitialState = createFinalState(StateSet.create(ast, ast.getNFAAnchoredInitialState(i)));
                anchoredInitialState.setAnchoredInitialState();
                anchoredInitialStates[i] = anchoredInitialState;
                anchoredEntries[i] = createTransition(dummyInitialState, anchoredInitialState, ast.getEncoding().getFullSet());
            }
        }
        NFAStateTransition[] dummyInitNext = Arrays.copyOf(anchoredEntries, nEntries * 2);
        System.arraycopy(unAnchoredEntries, 0, dummyInitNext, nEntries, nEntries);
        NFAStateTransition[] dummyInitPrev = new NFAStateTransition[]{anchoredReverseEntry, unAnchoredReverseEntry};
        dummyInitialState.setSuccessors(dummyInitNext, false);
        dummyInitialState.setPredecessors(dummyInitPrev);
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
        for (NFAState s : nfaStates.values()) {
            if (s != dummyInitialState && ast.getHardPrefixNodes().isDisjoint(s.getStateSet())) {
                s.linkPredecessors();
            }
        }
        ArrayList<NFAState> deadStates = new ArrayList<>();
        findDeadStates(deadStates);
        while (!deadStates.isEmpty()) {
            for (NFAState state : deadStates) {
                for (NFAStateTransition pre : state.getPredecessors()) {
                    pre.getSource().removeSuccessor(state);
                }
                // hardPrefixStates are not reachable by prev-transitions
                for (NFAState prefixState : hardPrefixStates) {
                    prefixState.removeSuccessor(state);
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
                initialStates[i].addLoopBackNext(createTransition(initialStates[i], initialStates[i - 1], ast.getEncoding().getFullSet()));
            }
        }
        return new NFA(ast, dummyInitialState, anchoredEntries, unAnchoredEntries, anchoredReverseEntry, unAnchoredReverseEntry, nfaStates.values(), stateID, transitionID, null);
    }

    private void findDeadStates(ArrayList<NFAState> deadStates) {
        for (NFAState state : nfaStates.values()) {
            if (state.isDead(true)) {
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
        curState.setSuccessors(createNFATransitions(curState, nextStep), !isHardPrefixState);
    }

    private NFAStateTransition[] createNFATransitions(NFAState sourceState, ASTStep nextStep) {
        transitionsBuffer.clear();
        StateSet<RegexAST, CharacterClass> stateSetCC;
        StateSet<RegexAST, LookBehindAssertion> finishedLookBehinds;
        for (ASTSuccessor successor : nextStep.getSuccessors()) {
            for (TransitionBuilder<RegexAST, Term, ASTTransition> mergeBuilder : successor.getMergedStates(astTransitionCanonicalizer, compilationBuffer)) {
                stateSetCC = null;
                finishedLookBehinds = null;
                boolean containsPositionAssertion = false;
                boolean containsMatchFound = false;
                boolean containsPrefixStates = false;
                for (ASTTransition astTransition : mergeBuilder.getTransitionSet().getTransitions()) {
                    Term target = astTransition.getTarget();
                    if (target instanceof CharacterClass) {
                        if (stateSetCC == null) {
                            stateSetCC = StateSet.create(ast);
                            finishedLookBehinds = StateSet.create(ast);
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
                        transitionsBuffer.add(createTransition(sourceState, anchoredFinalState, ast.getEncoding().getFullSet()));
                    } else if (containsMatchFound) {
                        transitionsBuffer.add(createTransition(sourceState, finalState, ast.getEncoding().getFullSet()));
                    }
                } else if (!containsPositionAssertion) {
                    assert mergeBuilder.getCodePointSet().matchesSomething();
                    transitionsBuffer.add(createTransition(sourceState,
                                    registerMatcherState(stateSetCC, mergeBuilder.getCodePointSet(), finishedLookBehinds, containsPrefixStates), mergeBuilder.getCodePointSet()));
                }
                transitionGBUpdateIndices.clear();
                transitionGBClearIndices.clear();
            }
        }
        return transitionsBuffer.toArray(new NFAStateTransition[transitionsBuffer.size()]);
    }

    private NFAState createFinalState(StateSet<RegexAST, ? extends RegexASTNode> stateSet) {
        NFAState state = new NFAState((short) stateID.inc(), stateSet, ast.getEncoding().getFullSet(), Collections.emptySet(), false);
        assert !nfaStates.containsKey(state.getStateSet());
        nfaStates.put(state.getStateSet(), state);
        return state;
    }

    private NFAStateTransition createTransition(NFAState source, NFAState target, CodePointSet codePointSet) {
        return new NFAStateTransition((short) transitionID.inc(), source, target, codePointSet, ast.createGroupBoundaries(transitionGBUpdateIndices, transitionGBClearIndices));
    }

    private NFAState registerMatcherState(StateSet<RegexAST, CharacterClass> stateSetCC,
                    CodePointSet matcherBuilder,
                    StateSet<RegexAST, LookBehindAssertion> finishedLookBehinds,
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
