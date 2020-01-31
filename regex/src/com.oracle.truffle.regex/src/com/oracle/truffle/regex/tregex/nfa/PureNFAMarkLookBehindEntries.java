/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.regex.UnsupportedRegexException;
import com.oracle.truffle.regex.tregex.automaton.StateSet;
import com.oracle.truffle.regex.tregex.parser.ast.LookAroundAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.LookBehindAssertion;

public class PureNFAMarkLookBehindEntries {

    private final PureNFAMap nfa;
    private StateSet<LookBehindAssertion> allReferencedInState;

    private StateSet<PureNFAState> markLiteralStatesCur;
    private StateSet<PureNFAState> markLiteralStatesNext;

    public PureNFAMarkLookBehindEntries(PureNFAMap nfa) {
        this.nfa = nfa;
        markLiteralStatesCur = StateSet.create(nfa.getRoot());
        markLiteralStatesNext = StateSet.create(nfa.getRoot());
        allReferencedInState = StateSet.create(nfa.getAst().getLookArounds());
    }

    public void markEntries() {
        if (!nfa.getAst().getProperties().hasLookBehindAssertions()) {
            return;
        }
        for (PureNFA subTree : nfa.getLookArounds()) {
            markEntriesInSubtree(subTree, false);
        }
        markEntriesInSubtree(nfa.getRoot(), true);
    }

    private void markEntriesInSubtree(PureNFA subtree, boolean subtreeIsRoot) {
        for (PureNFAState s : subtree.getStates()) {
            allReferencedInState.clear();
            for (PureNFATransition t : s.getSuccessors()) {
                for (int id : t.getTraversedLookArounds()) {
                    LookAroundAssertion la = nfa.getAst().getLookArounds().get(id);
                    if (la instanceof LookBehindAssertion) {
                        allReferencedInState.add((LookBehindAssertion) la);
                    }
                }
            }
            for (LookBehindAssertion lb : allReferencedInState) {
                PureNFA lookBehindNFA = nfa.getLookArounds().get(lb.getSubTreeId());
                if (subtreeIsRoot && lb.getGroup().isLiteral()) {
                    markLiteral(s, lookBehindNFA);
                } else {
                    markGeneric(s, lookBehindNFA);
                }
            }
        }
    }

    /**
     * Fast path for non-nested look-behind expressions containing nothing but character class
     * nodes.
     */
    private void markLiteral(PureNFAState parentState, PureNFA lb) {
        PureNFAState lbChar = lb.getReverseUnAnchoredEntry().getSource();
        markLiteralStatesCur.clear();
        markLiteralStatesCur.add(parentState);
        while (!markLiteralStatesCur.isEmpty()) {
            markLiteralStatesNext.clear();
            assert lbChar.getPredecessors().length == 1;
            // iterate over the literal lookbehind's characters
            lbChar = lbChar.getPredecessors()[0].getSource();
            assert !lbChar.isInitialState() && !lbChar.isFinalState();
            for (PureNFAState cur : markLiteralStatesCur) {
                if (cur.isAnchoredInitialState()) {
                    // lookbehind can't go past a caret, prune
                    continue;
                } else if (cur.isUnAnchoredInitialState()) {
                    // lookbehind starts before the root expression
                    int offset = 0;
                    PureNFAState prevLBState = lbChar;
                    do {
                        assert prevLBState.getPredecessors().length == 1;
                        prevLBState = prevLBState.getPredecessors()[0].getSource();
                        offset++;
                    } while (!prevLBState.isUnAnchoredInitialState());
                    nfa.addPrefixLookBehindEntry(lb, offset);
                } else if (cur.getCharSet().intersects(lbChar.getCharSet())) {
                    assert lbChar.getPredecessors().length == 1;
                    PureNFAState prevLBState = lbChar.getPredecessors()[0].getSource();
                    if (prevLBState.isAnchoredInitialState()) {
                        for (PureNFATransition curTransition : cur.getPredecessors()) {
                            if (curTransition.getSource().isInitialState()) {
                                cur.addLookBehindEntry(nfa.getLookArounds(), lb);
                                break;
                            }
                        }
                    } else if (prevLBState.isUnAnchoredInitialState()) {
                        cur.addLookBehindEntry(nfa.getLookArounds(), lb);
                    } else {
                        for (PureNFATransition curTransition : cur.getPredecessors()) {
                            markLiteralStatesNext.add(curTransition.getSource());
                        }
                    }
                }
            }
            StateSet<PureNFAState> tmp = markLiteralStatesCur;
            markLiteralStatesCur = markLiteralStatesNext;
            markLiteralStatesNext = tmp;
        }
    }

    @SuppressWarnings("unused")
    private void markGeneric(PureNFAState s, PureNFA lookBehindNFA) {
        throw new UnsupportedRegexException("not implemented", nfa.getAst().getSource());
    }
}
