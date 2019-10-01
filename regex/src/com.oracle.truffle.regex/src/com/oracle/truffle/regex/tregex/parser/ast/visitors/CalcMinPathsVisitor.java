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
package com.oracle.truffle.regex.tregex.parser.ast.visitors;

import com.oracle.truffle.regex.tregex.parser.ast.BackReference;
import com.oracle.truffle.regex.tregex.parser.ast.CharacterClass;
import com.oracle.truffle.regex.tregex.parser.ast.Group;
import com.oracle.truffle.regex.tregex.parser.ast.LookAheadAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.LookAroundAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.LookBehindAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.MatchFound;
import com.oracle.truffle.regex.tregex.parser.ast.PositionAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTNode;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTSubtreeRootNode;
import com.oracle.truffle.regex.tregex.parser.ast.Sequence;

/**
 * This visitor will set the {@link RegexASTNode#getMinPath()} - property of children of an AST in
 * the following manner:
 * <ul>
 * <li>The minPath of {@link LookBehindAssertion} and {@link LookAheadAssertion} nodes is the
 * minimum number of CharacterClass nodes that need to be traversed in order to reach the node.</li>
 * <li>The minPath of {@link BackReference}, {@link PositionAssertion} and {@link MatchFound} nodes
 * is undefined (or is always 0). Their minPath is never set by {@link CalcMinPathsVisitor}.</li>
 * <li>The minPath of {@link Sequence} and {@link Group} nodes is the minimum number of
 * {@link CharacterClass} nodes that need to be traversed in order to reach the end of the node. The
 * minPath field of {@link Sequence} nodes is used as a mutable iteration variable when traversing
 * their children (see {@link #visit(CharacterClass)}). The resulting value after the traversal
 * holds the minimum number of {@link CharacterClass} nodes that need to be traversed to reach the
 * end of the Sequence. The same holds for {@link Group} nodes.</li>
 * </ul>
 * {@link CalcMinPathsVisitor} will also set {@link RegexASTNode#getMaxPath()}, analogous to
 * {@link RegexASTNode#getMinPath()}, but without taking loops ({@link Group#isLoop()}) into
 * account. {@link CalcMinPathsVisitor} will simultaneously mark {@link PositionAssertion}s (type
 * {@link com.oracle.truffle.regex.tregex.parser.ast.PositionAssertion.Type#CARET} in forward mode
 * and {@link com.oracle.truffle.regex.tregex.parser.ast.PositionAssertion.Type#DOLLAR} in backward
 * mode) whose minimum path is greater than zero as dead. Note that this algorithm will e.g. not
 * mark the dollar assertion in {@code /(?=a$)bc/} as dead, since it has a (reverse) minimum path of
 * 0 inside the look-ahead assertion. The visitor will also mark all {@link Sequence} nodes that
 * start with a caret {@link PositionAssertion} or end with a dollar {@link PositionAssertion} with
 * the {@link RegexASTNode#startsWithCaret()}/{@link RegexASTNode#endsWithDollar()} flags, and
 * likewise for all {@link Group}s where all child {@link Sequence}s start with a caret or end with
 * a dollar. The visitor is intended to be run on an AST once in reverse, and then once in forward
 * direction. The results of the forward run will be used by {@link RegexAST#createPrefix()}.
 *
 * @see RegexASTNode#startsWithCaret()
 * @see RegexASTNode#endsWithDollar()
 * @see RegexASTNode#getMinPath()
 * @see RegexASTNode#getMaxPath()
 * @see RegexASTNode#isDead()
 * @see RegexAST#createPrefix()
 */
public class CalcMinPathsVisitor extends DepthFirstTraversalRegexASTVisitor {

    @Override
    protected void init(RegexASTNode runRoot) {
        runRoot.setMinPath(0);
        runRoot.setMaxPath(0);
    }

    @Override
    protected void visit(BackReference backReference) {
    }

    @Override
    protected void visit(Group group) {
        if (group.getParent() instanceof Sequence) {
            group.setMinPath(group.getParent().getMinPath());
            group.setMaxPath(group.getParent().getMaxPath());
        } else {
            assert group.getParent() instanceof RegexASTSubtreeRootNode;
            group.setMinPath(0);
            group.setMaxPath(0);
        }
    }

    @Override
    protected void leave(Group group) {
        if (group.isDead()) {
            return;
        }
        int minPath = Short.MAX_VALUE;
        int maxPath = 0;
        boolean caret = true;
        boolean dollar = true;
        boolean hasLoops = group.isLoop();
        boolean isDead = true;
        for (Sequence s : group.getAlternatives()) {
            isDead &= s.isDead();
            if (s.isDead()) {
                continue;
            }
            caret &= s.startsWithCaret();
            dollar &= s.endsWithDollar();
            hasLoops |= s.hasLoops();
            minPath = Math.min(minPath, s.getMinPath());
            maxPath = Math.max(maxPath, s.getMaxPath());
        }
        if (group.hasQuantifier()) {
            if (group.getQuantifier().getMin() == 0) {
                caret = false;
                dollar = false;
            }
            // group.minPath and group.maxPath are summed up from the beginning of the regex to the
            // beginning of the group.
            // the min and max path of the sequences are further summed up with min and max path of
            // the group, so sequence.minPath - group.minPath == the sequence's "own" minPath
            minPath = group.getMinPath() + ((minPath - group.getMinPath()) * group.getQuantifier().getMin());
            if (group.getQuantifier().isInfiniteLoop()) {
                hasLoops = true;
            } else {
                maxPath = group.getMaxPath() + ((maxPath - group.getMaxPath()) * group.getQuantifier().getMax());
            }
        }
        group.setStartsWithCaret(caret);
        group.setEndsWithDollar(dollar);
        group.setHasLoops(hasLoops);
        if (isDead) {
            group.markAsDead();
        }
        group.setMinPath(minPath);
        group.setMaxPath(maxPath);
        if (group.getParent() instanceof Sequence) {
            group.getParent().setMinPath(minPath);
            group.getParent().setMaxPath(maxPath);
        }
        if (group.getParent() != null) {
            if (caret) {
                group.getParent().setStartsWithCaret();
            }
            if (dollar) {
                group.getParent().setEndsWithDollar();
            }
            if (hasLoops) {
                group.getParent().setHasLoops();
            }
            if (isDead) {
                group.getParent().markAsDead();
            }
        }
    }

    @Override
    protected void visit(Sequence sequence) {
        sequence.setMinPath(sequence.getParent().getMinPath());
        sequence.setMaxPath(sequence.getParent().getMaxPath());
    }

    @Override
    protected void visit(PositionAssertion assertion) {
        switch (assertion.type) {
            case CARET:
                if (!isReverse()) {
                    if (assertion.getParent().getMinPath() > 0) {
                        assertion.markAsDead();
                        assertion.getParent().markAsDead();
                    } else {
                        assertion.getParent().setStartsWithCaret();
                    }
                }
                break;
            case DOLLAR:
                if (isReverse()) {
                    if (assertion.getParent().getMinPath() > 0) {
                        assertion.markAsDead();
                        assertion.getParent().markAsDead();
                    } else {
                        assertion.getParent().setEndsWithDollar();
                    }
                }
                break;
        }
    }

    @Override
    protected void visit(LookBehindAssertion assertion) {
        assertion.setMinPath(assertion.getParent().getMinPath());
        assertion.setMaxPath(assertion.getParent().getMaxPath());
    }

    @Override
    protected void leave(LookBehindAssertion assertion) {
        leaveLookAroundAssertion(assertion);
    }

    @Override
    protected void visit(LookAheadAssertion assertion) {
        assertion.setMinPath(assertion.getParent().getMinPath());
        assertion.setMaxPath(assertion.getParent().getMaxPath());
    }

    @Override
    protected void leave(LookAheadAssertion assertion) {
        leaveLookAroundAssertion(assertion);
    }

    public void leaveLookAroundAssertion(LookAroundAssertion assertion) {
        if (assertion.startsWithCaret()) {
            assertion.getParent().setStartsWithCaret();
        }
        if (assertion.endsWithDollar()) {
            assertion.getParent().setEndsWithDollar();
        }
        if (assertion.hasLoops()) {
            assertion.getParent().setHasLoops();
        }
        if (assertion.isDead()) {
            assertion.getParent().markAsDead();
        }
    }

    @Override
    protected void visit(CharacterClass characterClass) {
        if (characterClass.hasQuantifier()) {
            characterClass.getParent().incMinPath(characterClass.getQuantifier().getMin());
            if (characterClass.getQuantifier().isInfiniteLoop()) {
                characterClass.setHasLoops();
                characterClass.getParent().setHasLoops();
            } else {
                characterClass.getParent().incMaxPath(characterClass.getQuantifier().getMax());
            }
        } else {
            characterClass.getParent().incMinPath();
            characterClass.getParent().incMaxPath();
        }
        characterClass.setMinPath(characterClass.getParent().getMinPath());
        characterClass.setMaxPath(characterClass.getParent().getMaxPath());
        if (characterClass.getCharSet().matchesNothing()) {
            characterClass.markAsDead();
            characterClass.getParent().markAsDead();
        }
    }

    @Override
    protected void visit(MatchFound matchFound) {
        throw new IllegalStateException();
    }
}
