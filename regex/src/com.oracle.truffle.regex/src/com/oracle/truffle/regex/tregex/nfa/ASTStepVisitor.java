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
import java.util.Deque;
import java.util.List;
import java.util.Set;

import org.graalvm.collections.EconomicMap;

import com.oracle.truffle.regex.tregex.parser.ast.CharacterClass;
import com.oracle.truffle.regex.tregex.parser.ast.GroupBoundaries;
import com.oracle.truffle.regex.tregex.parser.ast.LookAheadAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.LookBehindAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.MatchFound;
import com.oracle.truffle.regex.tregex.parser.ast.PositionAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTNode;
import com.oracle.truffle.regex.tregex.parser.ast.Term;
import com.oracle.truffle.regex.tregex.parser.ast.visitors.NFATraversalRegexASTVisitor;

/**
 * Regex AST visitor that will find convert all NFA successors of a given {@link Term} to
 * {@link ASTTransition}s (by calculating their respective {@link GroupBoundaries}) and annotate for
 * every successor which {@link LookAheadAssertion}s and/or {@link LookBehindAssertion}s it should
 * be merged with. For example, when starting from Term "a" in the expression
 * {@code /a(b|(?=c)d)(?<=e)/}, it will find the successors "b" and "d", where "d" must be merged
 * with the successors of the look-ahead assertion ("c"), and both successors may be merged with the
 * successors of the look-behind assertion ("e").
 *
 * @see NFATraversalRegexASTVisitor
 */
public final class ASTStepVisitor extends NFATraversalRegexASTVisitor {

    private ASTStep stepCur;
    private final EconomicMap<ASTStepCacheKey, ASTStep> lookAheadMap = EconomicMap.create();
    private final List<ASTStep> curLookAheads = new ArrayList<>();
    private final List<ASTStep> curLookBehinds = new ArrayList<>();
    private final Deque<ASTStep> lookAroundExpansionQueue = new ArrayDeque<>();

    public ASTStepVisitor(RegexAST ast) {
        super(ast);
    }

    public ASTStep step(NFAState expandState) {
        ASTStep stepRoot = null;
        assert curLookAheads.isEmpty();
        assert curLookBehinds.isEmpty();
        assert lookAroundExpansionQueue.isEmpty();
        for (RegexASTNode t : expandState.getStateSet()) {
            if (t.isInLookAheadAssertion()) {
                ASTStep laStep = new ASTStep(t);
                curLookAheads.add(laStep);
                lookAroundExpansionQueue.push(laStep);
            } else if (t.isInLookBehindAssertion()) {
                ASTStep lbStep = new ASTStep(t);
                curLookBehinds.add(lbStep);
                lookAroundExpansionQueue.push(lbStep);
            } else {
                assert stepRoot == null;
                stepRoot = new ASTStep(t);
            }
        }
        if (stepRoot == null) {
            if (curLookAheads.isEmpty()) {
                assert !curLookBehinds.isEmpty();
                // The state we want to expand contains look-behind assertions only. This can happen
                // when compiling expressions like /(?<=aa)/:
                // The parser will expand the expression with a prefix, resulting in the following:
                // (?:[_any_][_any_](?:|[_any_](?:|[_any_])))(?<=aa)
                // When we compile the NFA, some of the paths we explore in this expression will
                // reach the second character of the look-behind assertion, while simultaneously
                // reaching the end of the prefix. These states must not match and therefore have
                // no valid successors.
                // For this reason, we can simply return one of the look-behind ASTStep objects,
                // whose successors have not been calculated at this point.
                ASTStep noSuccessors = curLookBehinds.get(0);
                curLookBehinds.clear();
                lookAroundExpansionQueue.clear();
                return noSuccessors;
            } else {
                stepRoot = curLookAheads.get(curLookAheads.size() - 1);
                curLookAheads.remove(curLookAheads.size() - 1);
                lookAroundExpansionQueue.remove(stepRoot);
            }
        }
        stepCur = stepRoot;
        Term root = (Term) stepRoot.getRoot();
        setTraversableLookBehindAssertions(expandState.getFinishedLookBehinds());
        setCanTraverseCaret(root instanceof PositionAssertion && ast.getNfaAnchoredInitialStates().contains(root));
        run(root);
        curLookAheads.clear();
        curLookBehinds.clear();
        while (!lookAroundExpansionQueue.isEmpty()) {
            stepCur = lookAroundExpansionQueue.pop();
            root = (Term) stepCur.getRoot();
            run(root);
        }
        return stepRoot;
    }

    @Override
    protected void visit(RegexASTNode target) {
        ASTSuccessor successor = new ASTSuccessor();
        ASTTransition transition = new ASTTransition();
        transition.setGroupBoundaries(getGroupBoundaries());
        if (dollarsOnPath()) {
            assert target instanceof MatchFound;
            transition.setTarget(getLastDollarOnPath());
        } else {
            if (target instanceof CharacterClass) {
                final CharacterClass charClass = (CharacterClass) target;
                if (!charClass.getLookBehindEntries().isEmpty()) {
                    ArrayList<ASTStep> newLookBehinds = new ArrayList<>(charClass.getLookBehindEntries().size());
                    for (LookBehindAssertion lb : charClass.getLookBehindEntries()) {
                        final ASTStep lbAstStep = new ASTStep(lb.getGroup());
                        assert lb.getGroup().isLiteral();
                        lbAstStep.addSuccessor(new ASTSuccessor(new ASTTransition(lb.getGroup().getFirstAlternative().getFirstTerm())));
                        newLookBehinds.add(lbAstStep);
                    }
                    successor.setLookBehinds(newLookBehinds);
                }
                transition.setTarget(charClass);
            } else {
                assert target instanceof MatchFound;
                transition.setTarget((MatchFound) target);
            }
        }
        successor.setInitialTransition(transition);
        if (!curLookAheads.isEmpty()) {
            successor.setLookAheads(new ArrayList<>(curLookAheads));
        }
        if (!curLookBehinds.isEmpty()) {
            successor.addLookBehinds(curLookBehinds);
        }
        stepCur.addSuccessor(successor);
    }

    @Override
    protected void enterLookAhead(LookAheadAssertion assertion) {
        ASTStepCacheKey key = new ASTStepCacheKey(assertion, canTraverseCaret(), getTraversableLookBehindAssertions());
        ASTStep laStep = lookAheadMap.get(key);
        if (laStep == null) {
            laStep = new ASTStep(assertion.getGroup());
            lookAroundExpansionQueue.push(laStep);
            lookAheadMap.put(key, laStep);
        }
        curLookAheads.add(laStep);
    }

    @Override
    protected void leaveLookAhead(LookAheadAssertion assertion) {
        assert curLookAheads.get(curLookAheads.size() - 1).getRoot().getParent() == assertion;
        curLookAheads.remove(curLookAheads.size() - 1);
    }

    private static class ASTStepCacheKey {
        private final RegexASTNode root;
        private final boolean canTraverseCaret;
        private final Set<LookBehindAssertion> traversableLookBehindAssertions;

        ASTStepCacheKey(RegexASTNode root, boolean canTraverseCaret, Set<LookBehindAssertion> traversableLookBehindAssertions) {
            this.root = root;
            this.canTraverseCaret = canTraverseCaret;
            this.traversableLookBehindAssertions = traversableLookBehindAssertions;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof ASTStepCacheKey)) {
                return false;
            }
            ASTStepCacheKey that = (ASTStepCacheKey) obj;
            return this.root.equals(that.root) && this.canTraverseCaret == that.canTraverseCaret && this.traversableLookBehindAssertions.equals(that.traversableLookBehindAssertions);
        }

        @Override
        public int hashCode() {
            return root.hashCode() + 31 * (Boolean.hashCode(canTraverseCaret) + 31 * traversableLookBehindAssertions.hashCode());
        }
    }

    @Override
    protected boolean canTraverseLookArounds() {
        return true;
    }
}
