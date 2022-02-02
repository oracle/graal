/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Objects;

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
import com.oracle.truffle.regex.util.TBitSet;

public final class NFAGenerator {

    private final RegexAST ast;
    private final Counter.ThresholdCounter stateID = new Counter.ThresholdCounter(TRegexOptions.TRegexMaxNFASize, "NFA explosion");
    private final Counter.ThresholdCounter transitionID = new Counter.ThresholdCounter(Short.MAX_VALUE, "NFA transition explosion");
    private final NFAState dummyInitialState;
    private final NFAState[] anchoredInitialStates;
    private final NFAState[] initialStates;
    /**
     * These are like {@link #initialStates}, but with {@code mustAdvance} set to {@code false},
     * i.e. we have already advanced when we are in these states. In a regular expression with
     * {@code MustAdvance=true}, all loopback transitions end in {@link #advancedInitialStates}
     * instead of {@link #initialStates}.
     */
    private final NFAState[] advancedInitialStates;
    private final NFAState anchoredFinalState;
    private final NFAState finalState;
    private final NFAStateTransition[] anchoredEntries;
    private final NFAStateTransition[] unAnchoredEntries;
    private final NFAStateTransition anchoredReverseEntry;
    private final NFAStateTransition unAnchoredReverseEntry;
    private final Deque<NFAState> expansionQueue = new ArrayDeque<>();
    private final Map<NFAStateID, NFAState> nfaStates = new HashMap<>();
    private final List<NFAState> hardPrefixStates = new ArrayList<>();
    private final ASTStepVisitor astStepVisitor;
    private final ASTTransitionCanonicalizer astTransitionCanonicalizer;
    private final TBitSet transitionGBUpdateIndices;
    private final TBitSet transitionGBClearIndices;
    private final ArrayList<NFAStateTransition> transitionsBuffer = new ArrayList<>();
    private final CompilationBuffer compilationBuffer;

    private NFAGenerator(RegexAST ast, CompilationBuffer compilationBuffer) {
        this.ast = ast;
        this.astStepVisitor = new ASTStepVisitor(ast);
        this.transitionGBUpdateIndices = new TBitSet(ast.getNumberOfCaptureGroups() * 2);
        this.transitionGBClearIndices = new TBitSet(ast.getNumberOfCaptureGroups() * 2);
        this.astTransitionCanonicalizer = new ASTTransitionCanonicalizer(ast, true, false);
        this.compilationBuffer = compilationBuffer;
        dummyInitialState = new NFAState((short) stateID.inc(), StateSet.create(ast, ast.getWrappedRoot()), CodePointSet.getEmpty(), Collections.emptySet(), false, ast.getOptions().isMustAdvance());
        nfaStates.put(NFAStateID.create(dummyInitialState), dummyInitialState);
        anchoredFinalState = createFinalState(StateSet.create(ast, ast.getReachableDollars()), false);
        anchoredFinalState.setAnchoredFinalState();
        finalState = createFinalState(StateSet.create(ast, ast.getRoot().getSubTreeParent().getMatchFound()), false);
        finalState.setUnAnchoredFinalState();
        assert transitionGBUpdateIndices.isEmpty() && transitionGBClearIndices.isEmpty();
        anchoredReverseEntry = createTransition(anchoredFinalState, dummyInitialState, ast.getEncoding().getFullSet(), -1);
        unAnchoredReverseEntry = createTransition(finalState, dummyInitialState, ast.getEncoding().getFullSet(), -1);
        int nEntries = ast.getWrappedPrefixLength() + 1;
        initialStates = new NFAState[nEntries];
        advancedInitialStates = ast.getOptions().isMustAdvance() ? new NFAState[Math.max(1, nEntries - 1)] : null;
        unAnchoredEntries = new NFAStateTransition[nEntries];
        for (int i = 0; i < initialStates.length; i++) {
            initialStates[i] = createFinalState(StateSet.create(ast, ast.getNFAUnAnchoredInitialState(i)), ast.getOptions().isMustAdvance());
            initialStates[i].setUnAnchoredInitialState(true);
            unAnchoredEntries[i] = createTransition(dummyInitialState, initialStates[i], ast.getEncoding().getFullSet(), -1);
        }
        if (ast.getOptions().isMustAdvance()) {
            for (int i = 0; i < advancedInitialStates.length; i++) {
                advancedInitialStates[i] = createFinalState(StateSet.create(ast, ast.getNFAUnAnchoredInitialState(i)), false);
            }
        }
        if (ast.getReachableCarets().isEmpty()) {
            anchoredInitialStates = initialStates;
            anchoredEntries = unAnchoredEntries;
        } else {
            anchoredInitialStates = new NFAState[nEntries];
            anchoredEntries = new NFAStateTransition[nEntries];
            for (int i = 0; i < anchoredInitialStates.length; i++) {
                anchoredInitialStates[i] = createFinalState(StateSet.create(ast, ast.getNFAAnchoredInitialState(i)), ast.getOptions().isMustAdvance());
                anchoredInitialStates[i].setAnchoredInitialState();
                anchoredEntries[i] = createTransition(dummyInitialState, anchoredInitialStates[i], ast.getEncoding().getFullSet(), -1);
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
        if (ast.getOptions().isMustAdvance()) {
            Collections.addAll(expansionQueue, advancedInitialStates);
        }
        if (!ast.getReachableCarets().isEmpty()) {
            Collections.addAll(expansionQueue, anchoredInitialStates);
        }

        while (!expansionQueue.isEmpty()) {
            expandNFAState(expansionQueue.pop());
        }

        NFAStateTransition initialLoopBack;
        assert transitionGBUpdateIndices.isEmpty() && transitionGBClearIndices.isEmpty();
        if (ast.getOptions().isMustAdvance()) {
            for (int i = 1; i < initialStates.length; i++) {
                addNewLoopBackTransition(initialStates[i], advancedInitialStates[i - 1]);
            }
            for (int i = 1; i < advancedInitialStates.length; i++) {
                addNewLoopBackTransition(advancedInitialStates[i], advancedInitialStates[i - 1]);
            }
            addNewLoopBackTransition(initialStates[0], advancedInitialStates[0]);
            initialLoopBack = createTransition(advancedInitialStates[0], advancedInitialStates[0], ast.getEncoding().getFullSet(), -1);
        } else {
            for (int i = 1; i < initialStates.length; i++) {
                addNewLoopBackTransition(initialStates[i], initialStates[i - 1]);
            }
            initialLoopBack = createTransition(initialStates[0], initialStates[0], ast.getEncoding().getFullSet(), -1);
        }

        for (NFAState s : nfaStates.values()) {
            if (s != dummyInitialState && (ast.getHardPrefixNodes().isDisjoint(s.getStateSet()) || ast.getFlags().isSticky())) {
                s.linkPredecessors();
            }
        }

        pruneDeadStates();

        return new NFA(ast, dummyInitialState, anchoredEntries, unAnchoredEntries, anchoredReverseEntry, unAnchoredReverseEntry, nfaStates.values(), stateID, transitionID, initialLoopBack, null);
    }

    private void pruneDeadStates() {
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
                for (NFAState initialState : initialStates) {
                    initialState.removeSuccessor(state);
                }
                for (NFAState initialState : anchoredInitialStates) {
                    initialState.removeSuccessor(state);
                }
                if (ast.getOptions().isMustAdvance()) {
                    for (NFAState initialState : advancedInitialStates) {
                        initialState.removeSuccessor(state);
                    }
                }
                dummyInitialState.removeSuccessor(state);
                nfaStates.remove(NFAStateID.create(state));
            }
            deadStates.clear();
            findDeadStates(deadStates);
        }
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
        curState.setSuccessors(createNFATransitions(curState, nextStep), !isHardPrefixState || ast.getFlags().isSticky());
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
                int lastGroup = -1;
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
                    if (!target.isInLookAheadAssertion() && !target.isInLookBehindAssertion()) {
                        lastGroup = astTransition.getGroupBoundaries().getLastGroup();
                    }
                }
                if (!(sourceState.isMustAdvance() && transitionGBUpdateIndices.get(0) && transitionGBUpdateIndices.get(1))) {
                    if (stateSetCC == null) {
                        if (containsPositionAssertion) {
                            transitionsBuffer.add(createTransition(sourceState, anchoredFinalState, ast.getEncoding().getFullSet(), lastGroup));
                        } else if (containsMatchFound) {
                            transitionsBuffer.add(createTransition(sourceState, finalState, ast.getEncoding().getFullSet(), lastGroup));
                            // Transitions dominated by a transition to a final state will never end
                            // up being used and so we can skip generating them and return the
                            // current list of transitions.
                            transitionGBUpdateIndices.clear();
                            transitionGBClearIndices.clear();
                            return transitionsBuffer.toArray(new NFAStateTransition[transitionsBuffer.size()]);
                        }
                    } else if (!containsPositionAssertion) {
                        assert mergeBuilder.getCodePointSet().matchesSomething();
                        NFAState targetState = registerMatcherState(stateSetCC, mergeBuilder.getCodePointSet(), finishedLookBehinds, containsPrefixStates,
                                        sourceState.isMustAdvance() && !ast.getHardPrefixNodes().isDisjoint(stateSetCC));
                        transitionsBuffer.add(createTransition(sourceState, targetState, mergeBuilder.getCodePointSet(), lastGroup));
                    }
                }
                transitionGBUpdateIndices.clear();
                transitionGBClearIndices.clear();
            }
        }
        return transitionsBuffer.toArray(new NFAStateTransition[transitionsBuffer.size()]);
    }

    private NFAState createFinalState(StateSet<RegexAST, ? extends RegexASTNode> stateSet, boolean mustAdvance) {
        NFAState state = new NFAState((short) stateID.inc(), stateSet, ast.getEncoding().getFullSet(), Collections.emptySet(), false, mustAdvance);
        assert !nfaStates.containsKey(NFAStateID.create(state));
        nfaStates.put(NFAStateID.create(state), state);
        return state;
    }

    private NFAStateTransition createTransition(NFAState source, NFAState target, CodePointSet codePointSet, int lastGroup) {
        return new NFAStateTransition((short) transitionID.inc(), source, target, codePointSet, ast.createGroupBoundaries(transitionGBUpdateIndices, transitionGBClearIndices, lastGroup));
    }

    private NFAState registerMatcherState(StateSet<RegexAST, CharacterClass> stateSetCC,
                    CodePointSet matcherBuilder,
                    StateSet<RegexAST, LookBehindAssertion> finishedLookBehinds,
                    boolean containsPrefixStates,
                    boolean mustAdvance) {
        NFAStateID nfaStateID = new NFAStateID(stateSetCC, mustAdvance);
        if (nfaStates.containsKey(nfaStateID)) {
            return nfaStates.get(nfaStateID);
        } else {
            NFAState state = new NFAState((short) stateID.inc(), stateSetCC, matcherBuilder, finishedLookBehinds, containsPrefixStates, mustAdvance);
            expansionQueue.push(state);
            nfaStates.put(nfaStateID, state);
            return state;
        }
    }

    private void addNewLoopBackTransition(NFAState source, NFAState target) {
        source.addLoopBackNext(createTransition(source, target, ast.getEncoding().getFullSet(), -1));
        if (ast.getHardPrefixNodes().isDisjoint(source.getStateSet()) || ast.getFlags().isSticky()) {
            target.incPredecessors();
        }
    }

    private static final class NFAStateID {

        private final StateSet<RegexAST, ? extends RegexASTNode> stateSet;
        private final boolean mustAdvance;

        NFAStateID(StateSet<RegexAST, ? extends RegexASTNode> stateSet, boolean mustAdvance) {
            this.stateSet = stateSet;
            this.mustAdvance = mustAdvance;
        }

        public static NFAStateID create(NFAState state) {
            return new NFAStateID(state.getStateSet(), state.isMustAdvance());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof NFAStateID)) {
                return false;
            }
            NFAStateID that = (NFAStateID) o;
            return mustAdvance == that.mustAdvance && stateSet.equals(that.stateSet);
        }

        @Override
        public int hashCode() {
            return Objects.hash(stateSet, mustAdvance);
        }
    }
}
