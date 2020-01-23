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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.regex.charset.CharSet;
import com.oracle.truffle.regex.tregex.TRegexOptions;
import com.oracle.truffle.regex.tregex.parser.Counter;
import com.oracle.truffle.regex.tregex.parser.ast.CharacterClass;
import com.oracle.truffle.regex.tregex.parser.ast.GroupBoundaries;
import com.oracle.truffle.regex.tregex.parser.ast.LookAroundAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.LookAroundIndex;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTNode;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTSubtreeRootNode;
import com.oracle.truffle.regex.tregex.parser.ast.Term;

public final class PureNFAGenerator {

    private final RegexAST ast;
    private final Counter.ThresholdCounter stateID = new Counter.ThresholdCounter(TRegexOptions.TRegexMaxNFASize, "NFA explosion");
    private final Counter.ThresholdCounter transitionID = new Counter.ThresholdCounter(Short.MAX_VALUE, "NFA transition explosion");
    private PureNFAState anchoredFinalState;
    private PureNFAState unAnchoredFinalState;
    private final Deque<PureNFAState> expansionQueue = new ArrayDeque<>();
    private final Map<RegexASTNode, PureNFAState> nfaStates = new HashMap<>();
    private final PureNFATransitionGenerator transitionGen;

    private PureNFAGenerator(RegexAST ast) {
        this.ast = ast;
        transitionGen = new PureNFATransitionGenerator(ast, this);
    }

    public static PureNFAMap mapToNFA(RegexAST ast) {
        PureNFAGenerator gen = new PureNFAGenerator(ast);
        PureNFA root = gen.createNFA(ast.getWrappedRoot().getSubTreeParent());
        PureNFAIndex lookAheads = mapLookArounds(gen, ast.getLookAheads());
        PureNFAIndex lookBehinds = mapLookArounds(gen, ast.getLookBehinds());
        return new PureNFAMap(ast, root, lookAheads, lookBehinds);
    }

    private static PureNFAIndex mapLookArounds(PureNFAGenerator gen, LookAroundIndex<? extends LookAroundAssertion> lookArounds) {
        if (lookArounds.isEmpty()) {
            return PureNFAIndex.getEmptyInstance();
        }
        PureNFAIndex map = new PureNFAIndex(lookArounds.getNumberOfStates());
        for (LookAroundAssertion la : lookArounds) {
            map.add(gen.createNFA(la));
        }
        return map;
    }

    public Counter.ThresholdCounter getTransitionIdCounter() {
        return transitionID;
    }

    public PureNFAState getAnchoredFinalState() {
        return anchoredFinalState;
    }

    public PureNFAState getUnAnchoredFinalState() {
        return unAnchoredFinalState;
    }

    public PureNFAState getOrCreateState(Term t) {
        PureNFAState lookup = nfaStates.get(t);
        if (lookup != null) {
            return lookup;
        } else {
            PureNFAState state = new PureNFAState((short) stateID.inc(), t.getId(), t instanceof CharacterClass ? ((CharacterClass) t).getCharSet() : CharSet.getEmpty());
            expansionQueue.push(state);
            nfaStates.put(t, state);
            return state;
        }
    }

    private PureNFA createNFA(RegexASTSubtreeRootNode root) {
        assert expansionQueue.isEmpty();
        nfaStates.clear();
        stateID.reset();
        transitionID.reset();
        PureNFAState dummyInitialState = new PureNFAState((short) stateID.inc(), ast.getWrappedRoot().getId(), CharSet.getEmpty());
        nfaStates.put(ast.getWrappedRoot(), dummyInitialState);
        if (!root.hasDollar()) {
            anchoredFinalState = null;
        } else {
            anchoredFinalState = createMatchAllState(root.getAnchoredFinalState(), false);
            anchoredFinalState.setAnchoredFinalState();
        }
        unAnchoredFinalState = createMatchAllState(root.getMatchFound(), false);
        unAnchoredFinalState.setUnAnchoredFinalState();
        if (root == ast.getWrappedRoot().getSubTreeParent()) {
            int nEntries = ast.getWrappedPrefixLength() + 1;
            PureNFATransition[] entries = new PureNFATransition[nEntries * 2];
            PureNFAState[] initialStates = new PureNFAState[nEntries];
            for (int i = 0; i <= ast.getWrappedPrefixLength(); i++) {
                PureNFAState initialState = createUnAnchoredInitialState(ast.getNFAUnAnchoredInitialState(i));
                initialStates[i] = initialState;
                entries[nEntries + i] = createEmptyTransition(dummyInitialState, initialState);
            }
            PureNFAState[] anchoredInitialStates;
            if (ast.getReachableCarets().isEmpty()) {
                System.arraycopy(entries, nEntries, entries, 0, nEntries);
            } else {
                anchoredInitialStates = new PureNFAState[nEntries];
                for (int i = 0; i <= ast.getWrappedPrefixLength(); i++) {
                    PureNFAState anchoredInitialState = createAnchoredInitialState(ast.getNFAAnchoredInitialState(i));
                    anchoredInitialStates[i] = anchoredInitialState;
                    entries[i] = createEmptyTransition(dummyInitialState, anchoredInitialState);
                }
            }
            dummyInitialState.setSuccessors(entries);
        } else {
            PureNFATransition initialStateTransition = createEmptyTransition(dummyInitialState, createUnAnchoredInitialState(root.getUnAnchoredInitialState()));
            if (root.hasCaret()) {
                dummyInitialState.setSuccessors(new PureNFATransition[]{createEmptyTransition(dummyInitialState, createAnchoredInitialState(root.getAnchoredInitialState())), initialStateTransition});
            } else {
                dummyInitialState.setSuccessors(new PureNFATransition[]{initialStateTransition, initialStateTransition});
            }
        }
        assert dummyInitialState.getId() == 0;
        dummyInitialState.setPredecessors(new PureNFATransition[]{createEmptyTransition(anchoredFinalState, dummyInitialState), createEmptyTransition(unAnchoredFinalState, dummyInitialState)});
        expandAllStates();
        return new PureNFA(root.getSubTreeId(), nfaStates.values(), stateID, transitionID);
    }

    private void expandAllStates() {
        while (!expansionQueue.isEmpty()) {
            expandNFAState(expansionQueue.pop());
        }
    }

    private void expandNFAState(PureNFAState curState) {
        transitionGen.generateTransitions(curState);
    }

    private PureNFAState createAnchoredInitialState(RegexASTNode astNode) {
        PureNFAState state = createMatchAllState(astNode);
        state.setAnchoredInitialState();
        return state;
    }

    private PureNFAState createUnAnchoredInitialState(RegexASTNode astNode) {
        PureNFAState state = createMatchAllState(astNode);
        state.setUnAnchoredInitialState();
        return state;
    }

    private PureNFAState createMatchAllState(RegexASTNode astNode) {
        return createMatchAllState(astNode, true);
    }

    private PureNFAState createMatchAllState(RegexASTNode astNode, boolean enqueue) {
        PureNFAState state = new PureNFAState((short) stateID.inc(), astNode.getId(), CharSet.getFull());
        assert !nfaStates.containsKey(astNode);
        nfaStates.put(astNode, state);
        if (enqueue) {
            expansionQueue.add(state);
        }
        return state;
    }

    private PureNFATransition createEmptyTransition(PureNFAState src, PureNFAState tgt) {
        return new PureNFATransition((short) transitionID.inc(), src, tgt, GroupBoundaries.getEmptyInstance(),
                        ast.getLookAheads().getEmptySet(),
                        ast.getLookBehinds().getEmptySet(),
                        QuantifierGuard.NO_GUARDS);
    }
}
