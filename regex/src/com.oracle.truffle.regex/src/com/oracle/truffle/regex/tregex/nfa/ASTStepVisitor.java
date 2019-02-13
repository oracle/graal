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

import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.parser.ast.CharacterClass;
import com.oracle.truffle.regex.tregex.parser.ast.Group;
import com.oracle.truffle.regex.tregex.parser.ast.GroupBoundaries;
import com.oracle.truffle.regex.tregex.parser.ast.LookAheadAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.LookBehindAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.MatchFound;
import com.oracle.truffle.regex.tregex.parser.ast.PositionAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTNode;
import com.oracle.truffle.regex.tregex.parser.ast.Term;
import com.oracle.truffle.regex.tregex.parser.ast.visitors.NFATraversalRegexASTVisitor;
import com.oracle.truffle.regex.util.CompilationFinalBitSet;
import org.graalvm.collections.EconomicMap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

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
    private final EconomicMap<LookAheadAssertion, ASTStep> lookAheadMap = EconomicMap.create();
    private final EconomicMap<LookAheadAssertion, ASTStep> lookAheadMapWithCaret = EconomicMap.create();
    private final List<ASTStep> curLookAheads = new ArrayList<>();
    private final List<ASTStep> curLookBehinds = new ArrayList<>();
    private final Deque<ASTStep> lookAroundExpansionQueue = new ArrayDeque<>();
    private final CompilationFinalBitSet captureGroupUpdates;
    private final CompilationFinalBitSet captureGroupClears;

    private final CompilationBuffer compilationBuffer;

    public ASTStepVisitor(RegexAST ast, CompilationBuffer compilationBuffer) {
        super(ast);
        this.compilationBuffer = compilationBuffer;
        captureGroupUpdates = new CompilationFinalBitSet(ast.getNumberOfCaptureGroups() * 2);
        captureGroupClears = new CompilationFinalBitSet(ast.getNumberOfCaptureGroups() * 2);
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
    protected void visit(ArrayList<PathElement> path) {
        ASTSuccessor successor = new ASTSuccessor(compilationBuffer);
        ASTTransition transition = new ASTTransition();
        PositionAssertion dollar = null;
        captureGroupUpdates.clear();
        captureGroupClears.clear();
        Group outerPassThrough = null;
        for (PathElement element : path) {
            final RegexASTNode node = element.getNode();
            if (node instanceof Group) {
                Group group = (Group) node;
                if (element.isGroupEnter()) {
                    if (outerPassThrough == null) {
                        if (element.isGroupPassThrough() && group.isExpandedQuantifier()) {
                            outerPassThrough = group;
                        }
                        if (group.isCapturing() && !(element.isGroupPassThrough() && group.isExpandedQuantifier())) {
                            captureGroupUpdates.set(group.getBoundaryIndexStart());
                            captureGroupClears.clear(group.getBoundaryIndexStart());
                        }
                        if (!element.isGroupPassThrough() && (group.isLoop() || group.isExpandedQuantifier())) {
                            for (int i = group.getEnclosedCaptureGroupsLow(); i < group.getEnclosedCaptureGroupsHigh(); i++) {
                                if (!captureGroupUpdates.get(Group.groupNumberToBoundaryIndexStart(i))) {
                                    captureGroupClears.set(Group.groupNumberToBoundaryIndexStart(i));
                                }
                                if (!captureGroupUpdates.get(Group.groupNumberToBoundaryIndexEnd(i))) {
                                    captureGroupClears.set(Group.groupNumberToBoundaryIndexEnd(i));
                                }
                            }
                        }
                    }
                } else {
                    assert element.isGroupExit();
                    if (outerPassThrough == null) {
                        if (group.isCapturing() && !(element.isGroupPassThrough() && group.isExpandedQuantifier())) {
                            captureGroupUpdates.set(group.getBoundaryIndexEnd());
                            captureGroupClears.clear(group.getBoundaryIndexEnd());
                        }
                    } else if (outerPassThrough == group) {
                        outerPassThrough = null;
                    }
                }
            } else if (node instanceof PositionAssertion && ((PositionAssertion) node).type == PositionAssertion.Type.DOLLAR) {
                dollar = (PositionAssertion) node;
            }
        }
        transition.setGroupBoundaries(ast.createGroupBoundaries(captureGroupUpdates, captureGroupClears));
        final RegexASTNode lastNode = path.get(path.size() - 1).getNode();
        if (dollar == null) {
            if (lastNode instanceof CharacterClass) {
                final CharacterClass charClass = (CharacterClass) lastNode;
                ArrayList<ASTStep> newLookBehinds = new ArrayList<>();
                for (Group g : charClass.getLookBehindEntries()) {
                    final ASTStep lbAstStep = new ASTStep(g);
                    assert g.isLiteral();
                    lbAstStep.addSuccessor(new ASTSuccessor(compilationBuffer, new ASTTransition(g.getAlternatives().get(0).getFirstTerm())));
                    newLookBehinds.add(lbAstStep);
                }
                transition.setTarget(charClass);
                successor.setLookBehinds(newLookBehinds);
            } else {
                assert lastNode instanceof MatchFound;
                transition.setTarget((MatchFound) lastNode);
            }
        } else {
            assert lastNode instanceof MatchFound;
            transition.setTarget(dollar);
        }
        successor.addInitialTransition(transition);
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
        EconomicMap<LookAheadAssertion, ASTStep> laMap = canTraverseCaret() ? lookAheadMapWithCaret : lookAheadMap;
        ASTStep laStep = laMap.get(assertion);
        if (laStep == null) {
            laStep = new ASTStep(assertion.getGroup());
            lookAroundExpansionQueue.push(laStep);
            laMap.put(assertion, laStep);
        }
        curLookAheads.add(laStep);
    }

    @Override
    protected void leaveLookAhead(LookAheadAssertion assertion) {
        assert curLookAheads.get(curLookAheads.size() - 1).getRoot().getParent() == assertion;
        curLookAheads.remove(curLookAheads.size() - 1);
    }
}
