/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.parser;

import com.oracle.truffle.regex.RegexFlags;
import com.oracle.truffle.regex.charset.Constants;
import com.oracle.truffle.regex.tregex.TRegexOptions;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.buffer.ObjectArrayBuffer;
import com.oracle.truffle.regex.tregex.parser.ast.BackReference;
import com.oracle.truffle.regex.tregex.parser.ast.CalcASTPropsVisitor;
import com.oracle.truffle.regex.tregex.parser.ast.CharacterClass;
import com.oracle.truffle.regex.tregex.parser.ast.Group;
import com.oracle.truffle.regex.tregex.parser.ast.QuantifiableTerm;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.parser.ast.Sequence;
import com.oracle.truffle.regex.tregex.parser.ast.Term;
import com.oracle.truffle.regex.tregex.parser.ast.visitors.CopyVisitor;
import com.oracle.truffle.regex.tregex.parser.ast.visitors.DepthFirstTraversalRegexASTVisitor;
import com.oracle.truffle.regex.tregex.parser.ast.visitors.InitIDVisitor;
import com.oracle.truffle.regex.tregex.parser.ast.visitors.MarkLookBehindEntriesVisitor;
import com.oracle.truffle.regex.tregex.string.Encodings;

import java.util.ArrayList;

public class RegexASTPostProcessor {

    private final RegexAST ast;
    private final RegexProperties properties;
    private final RegexFlags flags;
    private final CompilationBuffer compilationBuffer;

    public RegexASTPostProcessor(RegexAST ast, CompilationBuffer compilationBuffer) {
        this.ast = ast;
        this.properties = ast.getProperties();
        this.flags = ast.getFlags();
        this.compilationBuffer = compilationBuffer;
    }

    public void prepareForDFA() {
        if (properties.hasQuantifiers()) {
            UnrollQuantifiersVisitor.unrollQuantifiers(ast, compilationBuffer);
        }
        CalcASTPropsVisitor.run(ast);
        ast.createPrefix();
        InitIDVisitor.init(ast);
        if (!properties.hasNonLiteralLookBehindAssertions() && !ast.getRoot().hasBackReferences() && !properties.hasLargeCountedRepetitions()) {
            new MarkLookBehindEntriesVisitor(ast).run();
        }
        checkInnerLiteral();
    }

    private void checkInnerLiteral() {
        if (ast.isLiteralString() || ast.getRoot().startsWithCaret() || ast.getRoot().endsWithDollar() || ast.getRoot().size() != 1 || flags.isSticky()) {
            return;
        }
        ArrayList<Term> terms = ast.getRoot().getFirstAlternative().getTerms();
        int literalStart = -1;
        int literalEnd = -1;
        for (int i = 0; i < terms.size(); i++) {
            Term t = terms.get(i);
            if (t.isCharacterClass() &&
                            (t.asCharacterClass().getCharSet().matchesSingleChar() || t.asCharacterClass().getCharSet().matches2CharsWith1BitDifference()) &&
                            ast.getEncoding().isFixedCodePointWidth(t.asCharacterClass().getCharSet()) &&
                            (ast.getEncoding() == Encodings.UTF_16_RAW || !t.asCharacterClass().getCharSet().intersects(Constants.SURROGATES))) {
                if (literalStart < 0) {
                    literalStart = i;
                }
                literalEnd = i + 1;
            } else if (literalStart >= 0 || t.hasLoops() || t.hasBackReferences()) {
                break;
            } else {
                if (t.getMaxPath() > 4) {
                    return;
                }
            }
        }
        if (literalStart >= 0 && (literalStart > 0 || literalEnd - literalStart > 1)) {
            properties.setInnerLiteral(literalStart, literalEnd);
        }
    }

    private static final class UnrollQuantifiersVisitor extends DepthFirstTraversalRegexASTVisitor {

        private final RegexAST ast;
        private final ShouldUnrollQuantifierVisitor shouldUnrollVisitor = new ShouldUnrollQuantifierVisitor();
        private final QuantifierExpander quantifierExpander;

        private UnrollQuantifiersVisitor(RegexAST ast, CompilationBuffer compilationBuffer) {
            this.ast = ast;
            this.quantifierExpander = new QuantifierExpander(ast, compilationBuffer);
        }

        public static void unrollQuantifiers(RegexAST ast, CompilationBuffer compilationBuffer) {
            new UnrollQuantifiersVisitor(ast, compilationBuffer).run(ast.getRoot());
        }

        @Override
        protected void visit(BackReference backReference) {
            if (backReference.hasNotUnrolledQuantifier()) {
                quantifierExpander.expandQuantifier(backReference, shouldUnroll(backReference));
            }
        }

        @Override
        protected void visit(CharacterClass characterClass) {
            if (characterClass.hasNotUnrolledQuantifier()) {
                quantifierExpander.expandQuantifier(characterClass, shouldUnroll(characterClass));
            }
        }

        @Override
        protected void leave(Group group) {
            if (group.hasNotUnrolledQuantifier() && !group.getFirstAlternative().isExpandedQuantifier() && !group.getLastAlternative().isExpandedQuantifier()) {
                quantifierExpander.expandQuantifier(group, shouldUnroll(group) && shouldUnrollVisitor.shouldUnroll(group));
            }
        }

        private boolean shouldUnroll(QuantifiableTerm term) {
            return term.getQuantifier().isUnrollTrivial() || (ast.getNumberOfNodes() <= TRegexOptions.TRegexMaxParseTreeSizeForDFA && term.isUnrollingCandidate());
        }

        private static final class ShouldUnrollQuantifierVisitor extends DepthFirstTraversalRegexASTVisitor {

            private Group root;
            private boolean result;

            boolean shouldUnroll(Group group) {
                assert group.hasQuantifier();
                result = true;
                root = group;
                run(group);
                return result;
            }

            @Override
            protected void visit(BackReference backReference) {
                result = false;
            }

            @Override
            protected void visit(Group group) {
                if (group != root && group.hasNotUnrolledQuantifier()) {
                    result = false;
                }
            }
        }

        private static final class QuantifierExpander {

            private final RegexAST ast;
            private final CompilationBuffer compilationBuffer;
            private final CopyVisitor copyVisitor;

            private Group curGroup;
            private Sequence curSequence;
            private Term curTerm;

            QuantifierExpander(RegexAST ast, CompilationBuffer compilationBuffer) {
                this.ast = ast;
                this.compilationBuffer = compilationBuffer;
                this.copyVisitor = new CopyVisitor(ast);
            }

            private void pushGroup() {
                Group group = ast.createGroup();
                curSequence.add(group);
                curGroup = group;
                addSequence();
            }

            private void popGroup() {
                curTerm = curGroup;
                curSequence = curGroup.getParent().asSequence();
                curGroup = curSequence.getParent();
            }

            private void addSequence() {
                curSequence = curGroup.addSequence(ast);
                curTerm = null;
            }

            private void addTerm(Term term) {
                curSequence.add(term);
                curTerm = term;
            }

            private void createOptionalBranch(QuantifiableTerm term, Token.Quantifier quantifier, boolean copy, boolean unroll, int recurse, boolean emptyGuard) {
                addTerm(copy ? copyVisitor.copy(term) : term);
                curTerm.setExpandedQuantifier(false);
                ((QuantifiableTerm) curTerm).setQuantifier(null);
                curTerm.setEmptyGuard(emptyGuard);
                createOptional(term, quantifier, true, unroll, recurse - 1, emptyGuard);
            }

            private void createOptional(QuantifiableTerm term, Token.Quantifier quantifier, boolean copy, boolean unroll, int recurse, boolean emptyGuard) {
                if (recurse < 0) {
                    return;
                }
                if (copy) {
                    // the outermost group is already generated by expandQuantifier if copy == false
                    pushGroup();
                }
                curGroup.setExpandedQuantifier(unroll);
                curGroup.setQuantifier(quantifier);
                if (term.isGroup()) {
                    curGroup.setEnclosedCaptureGroupsLow(term.asGroup().getEnclosedCaptureGroupsLow());
                    curGroup.setEnclosedCaptureGroupsHigh(term.asGroup().getEnclosedCaptureGroupsHigh());
                }
                if (quantifier.isGreedy()) {
                    createOptionalBranch(term, quantifier, copy, unroll, recurse, emptyGuard);
                    addSequence();
                    curSequence.setExpandedQuantifier(true);
                } else {
                    curSequence.setExpandedQuantifier(true);
                    addSequence();
                    createOptionalBranch(term, quantifier, copy, unroll, recurse, emptyGuard);
                }
                popGroup();
            }

            private void expandQuantifier(QuantifiableTerm toExpand, boolean unroll) {
                assert toExpand.hasNotUnrolledQuantifier();
                Token.Quantifier quantifier = toExpand.getQuantifier();
                assert !unroll || toExpand.isUnrollingCandidate();
                curTerm = toExpand;
                curSequence = (Sequence) curTerm.getParent();
                curGroup = curSequence.getParent();

                ObjectArrayBuffer<Term> buf = compilationBuffer.getObjectBuffer1();
                if (unroll && quantifier.getMin() > 0) {
                    // stash successors of toExpand to buffer
                    int size = curSequence.size();
                    for (int i = curTerm.getSeqIndex() + 1; i < size; i++) {
                        buf.add(curSequence.getLastTerm());
                        curSequence.removeLastTerm();
                    }
                    // unroll non-optional part ( x{3} -> xxx )
                    curTerm.setExpandedQuantifier(true);
                    for (int i = 0; i < quantifier.getMin() - 1; i++) {
                        Term term = copyVisitor.copy(curTerm);
                        term.setExpandedQuantifier(true);
                        curSequence.add(term);
                        curTerm = term;
                    }
                } else {
                    assert !unroll || quantifier.getMin() == 0;
                    // replace the term to expand with a new wrapper group
                    curGroup = ast.createGroup();
                    curGroup.addSequence(ast);
                    curSequence.replace(toExpand.getSeqIndex(), curGroup);
                    curSequence = curGroup.getFirstAlternative();
                    curTerm = null;
                }
                // unroll optional part ( x{0,3} -> (x(x(x|)|)|) )
                // In flavors like Python or Ruby, loops can be repeated past the point where the
                // position in the string keeps advancing (i.e. we are matching at least one
                // character per iteration). In Ruby, this can happen for as long as the state of
                // capture groups is being changed by each iteration. In Python, an extra empty
                // iteration is run because there is no backtracking after failing the empty check.
                // We can emulate this behavior by dropping empty guards in small bounded loops,
                // such as is the case for unrolled loops.
                createOptional(toExpand, quantifier, unroll && quantifier.getMin() > 0, unroll, !unroll || quantifier.isInfiniteLoop() ? 0 : (quantifier.getMax() - quantifier.getMin()) - 1,
                                !ast.getOptions().getFlavor().canHaveEmptyLoopIterations());
                if (!unroll || quantifier.isInfiniteLoop()) {
                    ((Group) curTerm).setLoop(true);
                }
                if (unroll && quantifier.getMin() > 0) {
                    // restore the stashed successors
                    for (int i = buf.length() - 1; i >= 0; i--) {
                        curSequence.add(buf.get(i));
                    }
                }
            }
        }
    }
}
