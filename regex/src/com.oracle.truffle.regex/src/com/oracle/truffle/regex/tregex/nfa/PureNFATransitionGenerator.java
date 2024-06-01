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

import com.oracle.truffle.regex.tregex.buffer.ObjectArrayBuffer;
import com.oracle.truffle.regex.tregex.parser.ast.LookAheadAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.LookBehindAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.MatchFound;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTNode;
import com.oracle.truffle.regex.tregex.parser.ast.Term;
import com.oracle.truffle.regex.tregex.parser.ast.visitors.NFATraversalRegexASTVisitor;

/**
 * Calculates the successor transitions of a given {@link PureNFAState}.
 */
public final class PureNFATransitionGenerator extends NFATraversalRegexASTVisitor {

    private final PureNFAGenerator nfaGen;
    private final ObjectArrayBuffer<PureNFATransition> transitionBuffer = new ObjectArrayBuffer<>(8);
    private PureNFAState curState;

    public PureNFATransitionGenerator(RegexAST ast, PureNFAGenerator nfaGen) {
        super(ast);
        this.nfaGen = nfaGen;
    }

    public void generateTransitions(PureNFAState state) {
        this.curState = state;
        Term root = state.getAstNode(ast);
        transitionBuffer.clear();
        run(root);
        curState.setSuccessors(transitionBuffer.toArray(new PureNFATransition[transitionBuffer.length()]));
    }

    @Override
    protected void visit(RegexASTNode target) {
        // Optimization: eagerly remove transitions that cannot match with their respective
        // look-around assertions
        if (pruneLookarounds(target)) {
            return;
        }

        PureNFAState targetState;
        if (target instanceof MatchFound) {
            targetState = (isReverse() && caretsOnPath() || !isReverse() && dollarsOnPath()) ? nfaGen.getAnchoredFinalState() : nfaGen.getUnAnchoredFinalState();
        } else {
            targetState = nfaGen.getOrCreateState((Term) target);
        }
        transitionBuffer.add(new PureNFATransition(nfaGen.getTransitionIdCounter().inc(), curState, targetState, getGroupBoundaries(), caretsOnPath(), dollarsOnPath(), getTransitionGuardsOnPath()));
    }

    @Override
    protected void enterLookAhead(LookAheadAssertion assertion) {
    }

    @Override
    protected void leaveLookAhead(LookAheadAssertion assertion) {
    }

    private boolean pruneLookarounds(RegexASTNode target) {
        if (isReverse()) {
            if (curState.isLookBehind(ast) && target.isCharacterClass()) {
                LookBehindAssertion lb = curState.getAstNode(ast).asLookBehindAssertion();
                if (!lb.isNegated() && lb.endsWithCharClass()) {
                    return !lb.getGroup().getFirstAlternative().getLastTerm().asCharacterClass().getCharSet().intersects(target.asCharacterClass().getCharSet());
                }
            } else if (curState.isCharacterClass() && target instanceof LookAheadAssertion) {
                LookAheadAssertion la = (LookAheadAssertion) target;
                if (!la.isNegated() && la.startsWithCharClass()) {
                    return !la.getGroup().getFirstAlternative().getFirstTerm().asCharacterClass().getCharSet().intersects(curState.getCharSet());
                }
            }
        } else {
            if (curState.isLookAhead(ast) && target.isCharacterClass()) {
                LookAheadAssertion la = curState.getAstNode(ast).asLookAheadAssertion();
                if (!la.isNegated() && la.startsWithCharClass()) {
                    return !la.getGroup().getFirstAlternative().getFirstTerm().asCharacterClass().getCharSet().intersects(target.asCharacterClass().getCharSet());
                }
            } else if (curState.isCharacterClass() && target instanceof LookBehindAssertion) {
                LookBehindAssertion lb = (LookBehindAssertion) target;
                if (!lb.isNegated() && lb.endsWithCharClass()) {
                    return !lb.getGroup().getFirstAlternative().getLastTerm().asCharacterClass().getCharSet().intersects(curState.getCharSet());
                }
            }
        }
        return false;
    }

    @Override
    protected boolean isBuildingDFA() {
        return false;
    }

    @Override
    protected boolean canPruneAfterUnconditionalFinalState() {
        return true;
    }
}
