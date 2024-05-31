/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Arrays;
import java.util.Deque;

import com.oracle.truffle.regex.tregex.TRegexOptions;
import com.oracle.truffle.regex.tregex.parser.Counter;
import com.oracle.truffle.regex.tregex.parser.ast.GroupBoundaries;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTSubtreeRootNode;
import com.oracle.truffle.regex.tregex.parser.ast.Term;

public final class PureNFAGenerator {

    private final RegexAST ast;
    private final Counter.ThresholdCounter stateID = new Counter.ThresholdCounter(TRegexOptions.TRegexMaxPureNFASize, "PureNFA explosion");
    private final Counter.ThresholdCounter transitionID = new Counter.ThresholdCounter(TRegexOptions.TRegexMaxPureNFATransitions, "PureNFA transition explosion");
    private PureNFAState anchoredInitialState;
    private PureNFAState unAnchoredInitialState;
    private PureNFAState anchoredFinalState;
    private PureNFAState unAnchoredFinalState;
    private final Deque<PureNFAState> expansionQueue = new ArrayDeque<>();
    private final PureNFAState[] nfaStates;
    private final PureNFATransitionGenerator transitionGen;

    private PureNFAGenerator(RegexAST ast) {
        this.ast = ast;
        this.nfaStates = new PureNFAState[ast.getNumberOfStates()];
        transitionGen = new PureNFATransitionGenerator(ast, this);
        transitionGen.setCanTraverseCaret(true);
    }

    public static PureNFA mapToNFA(RegexAST ast) {
        ast.hidePrefix();
        PureNFAGenerator gen = new PureNFAGenerator(ast);
        PureNFA rootNFA = gen.createNFA(ast.getRoot().getSubTreeParent());
        Deque<PureNFA> subtreeExpansionQueue = new ArrayDeque<>();
        subtreeExpansionQueue.push(rootNFA);
        while (!subtreeExpansionQueue.isEmpty()) {
            PureNFA parentNFA = subtreeExpansionQueue.pop();
            RegexASTSubtreeRootNode parentRoot = parentNFA.getASTSubtree(ast);
            for (int i = 0; i < parentNFA.getSubtrees().length; i++) {
                PureNFA childNFA = gen.createNFA(parentRoot.getSubtrees().get(i));
                assert !childNFA.isRoot();
                subtreeExpansionQueue.push(childNFA);
                parentNFA.getSubtrees()[i] = childNFA;
            }
        }
        ast.unhidePrefix();
        assert rootNFA.getGlobalSubTreeId() == -1;
        assert rootNFA.getSubTreeId() == -1;
        assert rootNFA.isRoot();
        return rootNFA;
    }

    public Counter.ThresholdCounter getTransitionIdCounter() {
        return transitionID;
    }

    public PureNFAState getAnchoredInitialState() {
        return anchoredInitialState;
    }

    public PureNFAState getUnAnchoredInitialState() {
        return unAnchoredInitialState;
    }

    public PureNFAState getAnchoredFinalState() {
        return anchoredFinalState;
    }

    public PureNFAState getUnAnchoredFinalState() {
        return unAnchoredFinalState;
    }

    public PureNFAState getOrCreateState(Term t) {
        PureNFAState lookup = nfaStates[t.getId()];
        if (lookup != null) {
            return lookup;
        } else {
            PureNFAState state = new PureNFAState(stateID.inc(), t);
            expansionQueue.push(state);
            nfaStates[t.getId()] = state;
            return state;
        }
    }

    private PureNFA createNFA(RegexASTSubtreeRootNode root) {
        assert expansionQueue.isEmpty();
        Arrays.fill(nfaStates, null);
        stateID.reset();
        transitionID.reset();
        transitionGen.setReverse(root.isLookBehindAssertion());

        PureNFAState dummyInitialState = new PureNFAState(stateID.inc(), ast.getWrappedRoot());
        nfaStates[ast.getWrappedRoot().getId()] = dummyInitialState;
        assert dummyInitialState.getId() == 0;

        if (root.isLookBehindAssertion()) {
            if (root.hasCaret()) {
                anchoredFinalState = createFinalState(root.getAnchoredInitialState(), false);
                anchoredFinalState.setAnchoredFinalState();
            } else {
                anchoredFinalState = null;
            }
            unAnchoredFinalState = createFinalState(root.getUnAnchoredInitialState(), false);
            unAnchoredFinalState.setUnAnchoredFinalState();
            unAnchoredInitialState = createUnAnchoredInitialState(root.getMatchFound());
            if (root.hasDollar()) {
                anchoredInitialState = createAnchoredInitialState(root.getAnchoredFinalState());
            } else {
                anchoredInitialState = null;
            }
        } else {
            if (root.hasDollar()) {
                anchoredFinalState = createFinalState(root.getAnchoredFinalState(), false);
                anchoredFinalState.setAnchoredFinalState();
            } else {
                anchoredFinalState = null;
            }
            unAnchoredFinalState = createFinalState(root.getMatchFound(), false);
            unAnchoredFinalState.setUnAnchoredFinalState();
            unAnchoredInitialState = createUnAnchoredInitialState(root.getUnAnchoredInitialState());
            if (root.hasCaret()) {
                anchoredInitialState = createAnchoredInitialState(root.getAnchoredInitialState());
            } else {
                anchoredInitialState = null;
            }
        }

        PureNFATransition initialStateTransition = createEmptyTransition(dummyInitialState, unAnchoredInitialState);
        if (anchoredInitialState != null) {
            dummyInitialState.setSuccessors(new PureNFATransition[]{createEmptyTransition(dummyInitialState, anchoredInitialState), initialStateTransition});
        } else {
            dummyInitialState.setSuccessors(new PureNFATransition[]{initialStateTransition, initialStateTransition});
        }

        expandAllStates();
        return new PureNFA(root, nfaStates, stateID, transitionID);
    }

    private void expandAllStates() {
        while (!expansionQueue.isEmpty()) {
            expandNFAState(expansionQueue.pop());
        }
    }

    private void expandNFAState(PureNFAState curState) {
        transitionGen.generateTransitions(curState);
    }

    private PureNFAState createAnchoredInitialState(Term astNode) {
        PureNFAState state = createInitialState(astNode);
        state.setAnchoredInitialState();
        return state;
    }

    private PureNFAState createUnAnchoredInitialState(Term astNode) {
        PureNFAState state = createInitialState(astNode);
        state.setUnAnchoredInitialState();
        return state;
    }

    private PureNFAState createInitialState(Term astNode) {
        return createFinalState(astNode, true);
    }

    private PureNFAState createFinalState(Term astNode, boolean enqueue) {
        PureNFAState state = new PureNFAState(stateID.inc(), astNode);
        assert nfaStates[astNode.getId()] == null;
        nfaStates[astNode.getId()] = state;
        if (enqueue) {
            expansionQueue.add(state);
        }
        return state;
    }

    private PureNFATransition createEmptyTransition(PureNFAState src, PureNFAState tgt) {
        return new PureNFATransition(transitionID.inc(), src, tgt, GroupBoundaries.getEmptyInstance(ast.getLanguage()), false, false, TransitionGuard.NO_GUARDS);
    }
}
