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

import java.util.Arrays;

import com.oracle.truffle.regex.tregex.buffer.ObjectArrayBuffer;
import com.oracle.truffle.regex.tregex.parser.ast.LookAheadAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.MatchFound;
import com.oracle.truffle.regex.tregex.parser.ast.PositionAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTNode;
import com.oracle.truffle.regex.tregex.parser.ast.Term;
import com.oracle.truffle.regex.tregex.parser.ast.visitors.NFATraversalRegexASTVisitor;

public final class PureNFATransitionGenerator extends NFATraversalRegexASTVisitor {

    private final PureNFAGenerator nfaGen;
    private final ObjectArrayBuffer transitionBuffer = new ObjectArrayBuffer(8);
    private PureNFAState curState;

    public PureNFATransitionGenerator(RegexAST ast, PureNFAGenerator nfaGen) {
        super(ast);
        this.nfaGen = nfaGen;
    }

    public void generateTransitions(PureNFAState state) {
        this.curState = state;
        Term root = (Term) ast.getState(state.getAstNodeId());
        setCanTraverseCaret(root instanceof PositionAssertion && ast.getNfaAnchoredInitialStates().contains(root));
        transitionBuffer.clear();
        run(root);
        curState.setSuccessors(transitionBuffer.toArray(new PureNFATransition[transitionBuffer.length()]));
    }

    @Override
    protected void visit(RegexASTNode target) {
        PureNFAState targetState;
        if (target instanceof MatchFound) {
            targetState = dollarsOnPath() ? nfaGen.getAnchoredFinalState() : nfaGen.getUnAnchoredFinalState();
        } else {
            targetState = nfaGen.getOrCreateState((Term) target);
        }
        targetState.incPredecessors();
        QuantifierGuard[] quantifiers = getQuantifierGuardsOnPath();
        transitionBuffer.add(new PureNFATransition((short) nfaGen.getTransitionIdCounter().inc(),
                        curState,
                        targetState,
                        getGroupBoundaries(),
                        getLookAheadsOnPath(),
                        getLookBehindsOnPath(),
                        quantifiers.length == 0 ? QuantifierGuard.NO_GUARDS : Arrays.copyOf(quantifiers, quantifiers.length)));
    }

    @Override
    protected void enterLookAhead(LookAheadAssertion assertion) {
    }

    @Override
    protected void leaveLookAhead(LookAheadAssertion assertion) {
    }
}
