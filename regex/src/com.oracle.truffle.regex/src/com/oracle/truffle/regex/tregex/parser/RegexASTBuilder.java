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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.RegexFlags;
import com.oracle.truffle.regex.RegexLanguage;
import com.oracle.truffle.regex.RegexOptions;
import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.charset.CodePointSet;
import com.oracle.truffle.regex.charset.CodePointSetAccumulator;
import com.oracle.truffle.regex.charset.Constants;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.buffer.IntRangesBuffer;
import com.oracle.truffle.regex.tregex.parser.ast.BackReference;
import com.oracle.truffle.regex.tregex.parser.ast.CharacterClass;
import com.oracle.truffle.regex.tregex.parser.ast.Group;
import com.oracle.truffle.regex.tregex.parser.ast.LookAheadAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.LookBehindAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.PositionAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.QuantifiableTerm;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTNode;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTRootNode;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTSubtreeRootNode;
import com.oracle.truffle.regex.tregex.parser.ast.Sequence;
import com.oracle.truffle.regex.tregex.parser.ast.Term;
import com.oracle.truffle.regex.tregex.parser.ast.visitors.DepthFirstTraversalRegexASTVisitor;
import com.oracle.truffle.regex.tregex.parser.ast.visitors.NodeCountVisitor;
import com.oracle.truffle.regex.tregex.parser.ast.visitors.SetSourceSectionVisitor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

public final class RegexASTBuilder {

    private final RegexParserGlobals globals;
    private final RegexFlags flags;
    private final RegexOptions options;
    private final RegexAST ast;
    private final RegexProperties properties;
    private final Counter.ThresholdCounter groupCount;
    private final NodeCountVisitor countVisitor;
    private final SetSourceSectionVisitor setSourceSectionVisitor;
    private final CompilationBuffer compilationBuffer;

    private Group curGroup;
    private Sequence curSequence;
    private Term curTerm;

    @TruffleBoundary
    public RegexASTBuilder(RegexLanguage language, RegexSource source, RegexFlags flags, CompilationBuffer compilationBuffer) {
        this.globals = language.parserGlobals;
        this.flags = flags;
        this.options = source.getOptions();
        this.ast = new RegexAST(language, source, flags);
        this.properties = ast.getProperties();
        this.groupCount = ast.getGroupCount();
        this.countVisitor = new NodeCountVisitor();
        this.setSourceSectionVisitor = options.isDumpAutomataWithSourceSections() ? new SetSourceSectionVisitor(ast) : null;
        this.compilationBuffer = compilationBuffer;
    }

    public Group getCurGroup() {
        return curGroup;
    }

    public Sequence getCurSequence() {
        return curSequence;
    }

    public Term getCurTerm() {
        return curTerm;
    }

    public boolean curGroupIsRoot() {
        return curGroup == ast.getRoot();
    }

    public void pushRootGroup() {
        pushRootGroup(true);
    }

    public void pushRootGroup(boolean rootCapture) {
        RegexASTRootNode rootParent = ast.createRootNode();
        ast.setRoot(pushGroup(null, false, rootCapture, rootParent));
        if (options.isDumpAutomataWithSourceSections()) {
            // set leading and trailing '/' as source sections of root
            ast.addSourceSections(ast.getRoot(),
                            Arrays.asList(ast.getSource().getSource().createSection(0, 1), ast.getSource().getSource().createSection(ast.getSource().getPattern().length() + 1, 1)));
        }
    }

    /**
     * This is the build method of this Builder. As such, it should be the last method you call on
     * an {@link RegexASTBuilder} instance.
     * 
     * @return the generated AST
     */
    public RegexAST popRootGroup() {
        optimizeGroup();
        ast.getRoot().setEnclosedCaptureGroupsHigh(groupCount.getCount());
        return ast;
    }

    public void pushGroup(Token token) {
        pushGroup(token, true, false, null);
    }

    public void pushCaptureGroup(Token token) {
        pushGroup(token, true, true, null);
    }

    public void pushLookBehindAssertion(Token token, boolean negate) {
        LookBehindAssertion lookBehind = ast.createLookBehindAssertion(negate);
        ast.addSourceSection(lookBehind, token);
        addTerm(lookBehind);
        pushGroup(token, false, false, lookBehind);
    }

    public void pushLookAheadAssertion(Token token, boolean negate) {
        LookAheadAssertion lookAhead = ast.createLookAheadAssertion(negate);
        ast.addSourceSection(lookAhead, token);
        addTerm(lookAhead);
        pushGroup(token, false, false, lookAhead);
    }

    private Group pushGroup(Token token, boolean addToSeq, boolean capture, RegexASTSubtreeRootNode parent) {
        Group group = capture ? ast.createCaptureGroup(groupCount.inc()) : ast.createGroup();
        if (parent != null) {
            parent.setGroup(group);
        }
        if (addToSeq) {
            addTerm(group);
        }
        ast.addSourceSection(group, token);
        curGroup = group;
        curGroup.setEnclosedCaptureGroupsLow(groupCount.getCount());
        addSequence();
        return group;
    }

    public void popGroup(Token token) {
        if (tryMergeSingleCharClassAlternations()) {
            curGroup.removeLastSequence();
            ast.getNodeCount().dec();
        }
        optimizeGroup();
        curGroup.setEnclosedCaptureGroupsHigh(groupCount.getCount());
        ast.addSourceSection(curGroup, token);
        if (curGroup.getParent().isLookAroundAssertion()) {
            ast.addSourceSection(curGroup.getParent(), token);
        }
        RegexASTNode parent = curGroup.getParent();
        if (parent.isLookAroundAssertion()) {
            curTerm = (Term) parent;
            curSequence = parent.getParent().asSequence();
        } else {
            curTerm = curGroup;
            curSequence = (Sequence) parent;
        }
        curGroup = curSequence.getParent();
    }

    /**
     * Adds a new {@link Sequence} to the current {@link Group}.
     */
    public void addSequence() {
        if (!tryMergeSingleCharClassAlternations()) {
            curSequence = curGroup.addSequence(ast);
            curTerm = null;
        }
    }

    public void addTerm(Term term) {
        curSequence.add(term);
        curTerm = term;
    }

    public void addCharClass(Token.CharacterClass token) {
        CodePointSet codePointSet = token.getCodePointSet();
        if (flags.isUnicode()) {
            if (codePointSet.matchesNothing()) {
                // We need this branch because a Group with no alternatives is invalid
                addTerm(createCharClass(CodePointSet.getEmpty(), token));
            } else {
                addTerm(translateUnicodeCharClass(token));
            }
        } else {
            addTerm(createCharClass(codePointSet, token, token.wasSingleChar()));
        }
    }

    private CharacterClass createCharClass(CodePointSet charSet, Token token) {
        return createCharClass(charSet, token, false);
    }

    private CharacterClass createCharClass(CodePointSet charSet, Token token, boolean wasSingleChar) {
        CharacterClass characterClass = ast.createCharacterClass(charSet);
        ast.addSourceSection(characterClass, token);
        if (wasSingleChar) {
            characterClass.setWasSingleChar();
        }
        return characterClass;
    }

    private Term translateUnicodeCharClass(Token.CharacterClass token) {
        CodePointSet codePointSet = token.getCodePointSet();
        if (!options.isUTF16ExplodeAstralSymbols() || Constants.BMP_WITHOUT_SURROGATES.contains(token.getCodePointSet())) {
            return createCharClass(codePointSet, token, token.wasSingleChar());
        }
        Group group = ast.createGroup();
        group.setEnclosedCaptureGroupsLow(groupCount.getCount());
        group.setEnclosedCaptureGroupsHigh(groupCount.getCount());
        IntRangesBuffer tmp = compilationBuffer.getIntRangesBuffer1();
        CodePointSet bmpRanges = codePointSet.createIntersection(Constants.BMP_WITHOUT_SURROGATES, tmp);
        CodePointSet astralRanges = codePointSet.createIntersection(Constants.ASTRAL_SYMBOLS, tmp);
        CodePointSet loneLeadSurrogateRanges = codePointSet.createIntersection(Constants.LEAD_SURROGATES, tmp);
        CodePointSet loneTrailSurrogateRanges = codePointSet.createIntersection(Constants.TRAIL_SURROGATES, tmp);

        assert astralRanges.matchesSomething() || loneLeadSurrogateRanges.matchesSomething() || loneTrailSurrogateRanges.matchesSomething();

        if (bmpRanges.matchesSomething()) {
            Sequence bmpAlternative = group.addSequence(ast);
            bmpAlternative.add(createCharClass(bmpRanges, token));
        }

        if (loneLeadSurrogateRanges.matchesSomething()) {
            Sequence loneLeadSurrogateAlternative = group.addSequence(ast);
            loneLeadSurrogateAlternative.add(createCharClass(loneLeadSurrogateRanges, token));
            loneLeadSurrogateAlternative.add(globals.noTrailSurrogateAhead.copyRecursive(ast, compilationBuffer));
        }

        if (loneTrailSurrogateRanges.matchesSomething()) {
            Sequence loneTrailSurrogateAlternative = group.addSequence(ast);
            loneTrailSurrogateAlternative.add(globals.noLeadSurrogateBehind.copyRecursive(ast, compilationBuffer));
            loneTrailSurrogateAlternative.add(createCharClass(loneTrailSurrogateRanges, token));
        }

        if (astralRanges.matchesSomething()) {
            // completeRanges matches surrogate pairs where leading surrogates can be followed by
            // any trailing surrogates
            CodePointSetAccumulator completeRanges = compilationBuffer.getCodePointSetAccumulator1();
            completeRanges.clear();

            char curLead = Character.highSurrogate(astralRanges.getLo(0));
            CodePointSetAccumulator curTrails = compilationBuffer.getCodePointSetAccumulator2();
            curTrails.clear();
            for (int i = 0; i < astralRanges.size(); i++) {
                char startLead = Character.highSurrogate(astralRanges.getLo(i));
                final char startTrail = Character.lowSurrogate(astralRanges.getLo(i));
                char endLead = Character.highSurrogate(astralRanges.getHi(i));
                final char endTrail = Character.lowSurrogate(astralRanges.getHi(i));

                if (startLead > curLead) {
                    if (!curTrails.isEmpty()) {
                        Sequence finishedAlternative = group.addSequence(ast);
                        finishedAlternative.add(createCharClass(CodePointSet.create(curLead), token));
                        finishedAlternative.add(createCharClass(curTrails.toCodePointSet(), token));
                    }
                    curLead = startLead;
                    curTrails.clear();
                }
                if (startLead == endLead) {
                    curTrails.addRange(startTrail, endTrail);
                } else {
                    if (startTrail != Constants.TRAIL_SURROGATES.getLo(0)) {
                        curTrails.addRange(startTrail, Constants.TRAIL_SURROGATES.getHi(0));
                        assert startLead < Character.MAX_VALUE;
                        startLead = (char) (startLead + 1);
                    }

                    if (!curTrails.isEmpty()) {
                        Sequence finishedAlternative = group.addSequence(ast);
                        finishedAlternative.add(createCharClass(CodePointSet.create(curLead), token));
                        finishedAlternative.add(createCharClass(curTrails.toCodePointSet(), token));
                    }
                    curLead = endLead;
                    curTrails.clear();

                    if (endTrail != Constants.TRAIL_SURROGATES.getHi(0)) {
                        curTrails.addRange(Constants.TRAIL_SURROGATES.getLo(0), endTrail);
                        assert endLead > Character.MIN_VALUE;
                        endLead = (char) (endLead - 1);
                    }

                    if (startLead <= endLead) {
                        completeRanges.addRange(startLead, endLead);
                    }

                }
            }
            if (!curTrails.isEmpty()) {
                Sequence lastAlternative = group.addSequence(ast);
                lastAlternative.add(createCharClass(CodePointSet.create(curLead), token));
                lastAlternative.add(createCharClass(curTrails.toCodePointSet(), token));
            }

            if (!completeRanges.isEmpty()) {
                // Complete ranges match more often and so we want them as an early alternative
                Sequence completeRangesAlt = ast.createSequence();
                group.insertFirst(completeRangesAlt);
                completeRangesAlt.add(createCharClass(completeRanges.toCodePointSet(), token));
                completeRangesAlt.add(createCharClass(Constants.TRAIL_SURROGATES, token));
            }
        }
        assert !(group.size() == 1 && group.getFirstAlternative().getTerms().size() == 1);
        return group;
    }

    public void addBackReference(Token.BackReference token) {
        BackReference backReference = ast.createBackReference(token.getGroupNr());
        ast.addSourceSection(backReference, token);
        addTerm(backReference);
        if (backReference.getGroupNr() >= groupCount.getCount()) {
            backReference.setForwardReference();
        } else if (isNestedBackReference(backReference)) {
            backReference.setNestedBackReference();
        }
    }

    private static boolean isNestedBackReference(BackReference backReference) {
        RegexASTNode parent = backReference.getParent().getParent();
        while (true) {
            if (parent.asGroup().getGroupNumber() == backReference.getGroupNr()) {
                return true;
            }
            parent = parent.getParent();
            if (parent.isRoot()) {
                return false;
            }
            if (parent.isLookAroundAssertion()) {
                parent = parent.getParent();
            }
            parent = parent.getParent();
        }
    }

    public void addPositionAssertion(Token token) {
        PositionAssertion.Type type;
        if (token.kind == Token.Kind.caret) {
            type = PositionAssertion.Type.CARET;
        } else if (token.kind == Token.Kind.dollar) {
            type = PositionAssertion.Type.DOLLAR;
        } else {
            throw new IllegalArgumentException("unexpected token kind: " + token.kind);
        }
        PositionAssertion positionAssertion = ast.createPositionAssertion(type);
        ast.addSourceSection(positionAssertion, token);
        addTerm(positionAssertion);
    }

    public void addQuantifier(Token.Quantifier quantifier) {
        assert curTerm == curSequence.getLastTerm();
        if (quantifier.getMin() == -1) {
            replaceCurTermWithDeadNode();
            return;
        }
        if (quantifier.getMax() == 0) {
            removeCurTerm();
            return;
        }
        boolean curTermIsZeroWidthGroup = curTerm.isGroup() && curTerm.asGroup().isAlwaysZeroWidth();
        if (options.getFlavor().canHaveEmptyLoopIterations()) {
            // In flavors like Python or Ruby, we cannot remove optional zero-width groups or
            // lookaround assertions as those should be executed even though they will only match
            // the empty string. These expressions could change the state of capture groups and
            // the empty checks in these dialects either a) check the state of the capture groups
            // and/or b) do not backtrack when the empty check fails.
            if (quantifier.getMin() == 0 && curTerm.isCharacterClass() && curTerm.asCharacterClass().getCharSet().matchesNothing()) {
                removeCurTerm();
                return;
            }
        } else {
            if (quantifier.getMin() == 0 && (curTerm.isLookAroundAssertion() || curTermIsZeroWidthGroup ||
                            curTerm.isCharacterClass() && curTerm.asCharacterClass().getCharSet().matchesNothing())) {
                removeCurTerm();
                return;
            }
        }
        ast.addSourceSection(curTerm, quantifier);
        if (quantifier.getMin() > 0 && (curTerm.isLookAroundAssertion() || curTermIsZeroWidthGroup)) {
            // quantifying LookAroundAssertions doesn't do anything if quantifier.getMin() > 0, so
            // ignore.
            return;
        }
        if (quantifier.getMin() == 1 && quantifier.getMax() == 1) {
            // x{1,1} -> x
            return;
        }
        setQuantifier((QuantifiableTerm) curTerm, quantifier);
        // merge equal successive quantified terms
        if (curSequence.size() > 1) {
            Term prevTerm = curSequence.getTerms().get(curSequence.size() - 2);
            if (prevTerm.isQuantifiableTerm()) {
                QuantifiableTerm prev = prevTerm.asQuantifiableTerm();
                if (prev.hasQuantifier() && ((QuantifiableTerm) curTerm).equalsSemantic(prev, true)) {
                    removeCurTerm();
                    long min = (long) prev.getQuantifier().getMin() + quantifier.getMin();
                    long max = prev.getQuantifier().isInfiniteLoop() || quantifier.isInfiniteLoop() ? -1 : (long) prev.getQuantifier().getMax() + quantifier.getMax();
                    if (min > Integer.MAX_VALUE) {
                        replaceCurTermWithDeadNode();
                        return;
                    }
                    if (max > Integer.MAX_VALUE) {
                        max = -1;
                    }
                    setQuantifier(prev, Token.createQuantifier((int) min, (int) max, prev.getQuantifier().isGreedy() || quantifier.isGreedy()));
                }
            }
        }
    }

    private void setQuantifier(QuantifiableTerm term, Token.Quantifier quantifier) {
        term.setQuantifier(quantifier);
        if (!term.isUnrollingCandidate()) {
            properties.setLargeCountedRepetitions();
        }
        properties.setQuantifiers();
    }

    public void addCopy(Token token, Group sourceGroup) {
        Group copy = sourceGroup.copyRecursive(ast, compilationBuffer);
        if (options.isDumpAutomataWithSourceSections()) {
            setSourceSectionVisitor.run(copy, token);
        }
        addTerm(copy);
    }

    public void removeCurTerm() {
        ast.getNodeCount().dec(countVisitor.count(curSequence.getLastTerm()));
        curSequence.removeLastTerm();
        curTerm = curSequence.isEmpty() ? null : curSequence.getLastTerm();
    }

    public void replaceCurTermWithDeadNode() {
        removeCurTerm();
        addTerm(createCharClass(CodePointSet.getEmpty(), null));
    }

    /* optimizations */

    private void optimizeGroup() {
        sortAlternatives(curGroup);
        mergeCommonPrefixes(curGroup);
    }

    /**
     * This method should be called when {@code curSequence} is about to be closed. If the current
     * {@link Sequence} <em>and</em> the last {@link Sequence} consist of a single
     * {@link CharacterClass} each, the {@link CharacterClass} contained in the current
     * {@link Sequence} will be removed and merged into the last {@link Sequence}'s
     * {@link CharacterClass}, resulting in a smaller NFA.
     *
     * @return {@code true} if the {@link CharacterClass} in the current sequence was merged with
     *         the {@link CharacterClass} in the last Sequence.
     */
    private boolean tryMergeSingleCharClassAlternations() {
        if (curGroup.size() > 1 && curSequence.isSingleCharClass()) {
            assert curSequence == curGroup.getAlternatives().get(curGroup.size() - 1);
            Sequence prevSequence = curGroup.getAlternatives().get(curGroup.size() - 2);
            if (prevSequence.isSingleCharClass()) {
                mergeCharClasses((CharacterClass) prevSequence.getFirstTerm(), (CharacterClass) curSequence.getFirstTerm());
                curSequence.removeLastTerm();
                ast.getNodeCount().dec();
                return true;
            }
        }
        return false;
    }

    /**
     * Merge {@code src} into {@code dst}.
     */
    private void mergeCharClasses(CharacterClass dst, CharacterClass src) {
        dst.setCharSet(dst.getCharSet().union(src.getCharSet()));
        dst.setWasSingleChar(false);
        ast.addSourceSections(dst, ast.getSourceSections(src));
    }

    /**
     * Stable-sort consecutive alternations that start with single characters, to enable more
     * simplifications with {@link #mergeCommonPrefixes(Group)}. This also works in ignore-case
     * mode, since we track character classes that were generated from single characters via
     * {@link CharacterClass#wasSingleChar()}.
     */
    private static void sortAlternatives(Group group) {
        if (group.size() < 2) {
            return;
        }
        int begin = 0;
        while (begin + 1 < group.size()) {
            int end = findSingleCharAlternatives(group, begin);
            if (end > begin + 1) {
                group.getAlternatives().subList(begin, end).sort(Comparator.comparingInt((Sequence a) -> a.getFirstTerm().asCharacterClass().getCharSet().getMin()));
                begin = end;
            } else {
                begin++;
            }
        }
    }

    /**
     * Simplify redundant alternation prefixes, e.g. {@code /ab|ac/ -> /a(?:b|c)/}. This method
     * should be called when {@code curGroup} is about to be closed.
     */
    private void mergeCommonPrefixes(Group group) {
        if (group.size() < 2) {
            return;
        }
        ArrayList<Sequence> newAlternatives = null;
        // This optimization could change the order in which different paths are explored during
        // backtracking and therefore change the semantics of the expression. After the
        // optimization, the resulting regex will first try to match the prefix and then try to
        // continue with any of the possible suffixes before backtracking and trying different
        // branches in the prefix. However, the original regex will first completely exhaust all
        // options in the prefix while retaining the first suffix.
        // See the example: /.+(?=bar)|.+/.exec("foobar"), in which it is important to try to
        // backtrack the .+ prefix to just "foo" and then satisfy the lookahead.
        // In order to handle this, we limit this optimization so that only deterministic prefixes
        // are considered.
        IsDeterministicVisitor isDeterministicVisitor = new IsDeterministicVisitor();
        int lastEnd = 0;
        int begin = 0;
        while (begin + 1 < group.size()) {
            int end = findMatchingAlternatives(group, begin);
            if (end < 0 || !isDeterministicVisitor.isDeterministic(group.getAlternatives().get(begin).getFirstTerm())) {
                begin++;
            } else {
                if (newAlternatives == null) {
                    newAlternatives = new ArrayList<>();
                }
                for (int i = lastEnd; i < begin; i++) {
                    newAlternatives.add(group.getAlternatives().get(i));
                }
                lastEnd = end;
                int prefixSize = 1;
                while (alternativesAreEqualAt(group, begin, end, prefixSize) && isDeterministicVisitor.isDeterministic(group.getAlternatives().get(begin).get(prefixSize))) {
                    prefixSize++;
                }
                Sequence prefixSeq = ast.createSequence();
                Group innerGroup = ast.createGroup();
                int enclosedCGLo = Integer.MAX_VALUE;
                int enclosedCGHi = Integer.MIN_VALUE;
                boolean emptyAlt = false;
                for (int i = begin; i < end; i++) {
                    Sequence s = group.getAlternatives().get(i);
                    assert s.size() >= prefixSize;
                    for (int j = 0; j < prefixSize; j++) {
                        Term t = s.getTerms().get(j);
                        if (i == begin) {
                            prefixSeq.add(t);
                        } else {
                            ast.addSourceSections(prefixSeq.getTerms().get(j), ast.getSourceSections(t));
                            ast.getNodeCount().dec(countVisitor.count(t));
                        }
                    }
                    // merge successive single-character-class alternatives
                    if (i > begin && s.size() - prefixSize == 1 && s.getLastTerm().isCharacterClass() && !s.getLastTerm().asCharacterClass().hasQuantifier() &&
                                    innerGroup.getLastAlternative().isSingleCharClass()) {
                        mergeCharClasses(innerGroup.getLastAlternative().getFirstTerm().asCharacterClass(), s.getLastTerm().asCharacterClass());
                    } else {
                        // avoid creation of multiple empty alternatives in one group
                        if (prefixSize == s.size()) {
                            if (!emptyAlt) {
                                innerGroup.addSequence(ast);
                            }
                            emptyAlt = true;
                        } else {
                            Sequence copy = innerGroup.addSequence(ast);
                            for (int j = prefixSize; j < s.size(); j++) {
                                Term t = s.getTerms().get(j);
                                copy.add(t);
                                if (t.isGroup()) {
                                    Group g = t.asGroup();
                                    if (g.getEnclosedCaptureGroupsLow() != g.getEnclosedCaptureGroupsHigh()) {
                                        enclosedCGLo = Math.min(enclosedCGLo, g.getEnclosedCaptureGroupsLow());
                                        enclosedCGHi = Math.max(enclosedCGHi, g.getEnclosedCaptureGroupsHigh());
                                    }
                                    if (g.isCapturing()) {
                                        enclosedCGLo = Math.min(enclosedCGLo, g.getGroupNumber());
                                        enclosedCGHi = Math.max(enclosedCGHi, g.getGroupNumber() + 1);
                                    }
                                }
                            }
                        }
                    }
                }
                if (enclosedCGLo != Integer.MAX_VALUE) {
                    innerGroup.setEnclosedCaptureGroupsLow(enclosedCGLo);
                    innerGroup.setEnclosedCaptureGroupsHigh(enclosedCGHi);
                }
                if (!innerGroup.isEmpty() && !(innerGroup.size() == 1 && innerGroup.getFirstAlternative().isEmpty())) {
                    mergeCommonPrefixes(innerGroup);
                    prefixSeq.add(innerGroup);
                }
                newAlternatives.add(prefixSeq);
                begin = end;
            }
        }
        if (newAlternatives != null) {
            for (int i = lastEnd; i < group.size(); i++) {
                newAlternatives.add(group.getAlternatives().get(i));
            }
            group.setAlternatives(newAlternatives);
        }
    }

    private static final class IsDeterministicVisitor extends DepthFirstTraversalRegexASTVisitor {

        private boolean result;

        public boolean isDeterministic(RegexASTNode node) {
            result = true;
            run(node);
            return result;
        }

        private static boolean hasNonDeterministicQuantifier(QuantifiableTerm term) {
            if (term.hasQuantifier()) {
                Token.Quantifier quantifier = term.getQuantifier();
                if (quantifier.getMin() != quantifier.getMax()) {
                    return true;
                }
            }
            return false;
        }

        @Override
        protected void visit(BackReference backReference) {
            if (hasNonDeterministicQuantifier(backReference)) {
                result = false;
            }
        }

        @Override
        protected void visit(Group group) {
            if (hasNonDeterministicQuantifier(group)) {
                result = false;
                return;
            }
            if (group.getAlternatives().size() > 1) {
                result = false;
            }
        }

        @Override
        protected void visit(CharacterClass characterClass) {
            if (hasNonDeterministicQuantifier(characterClass)) {
                result = false;
            }
        }
    }

    /**
     * Returns {@code true} iff the term at index {@code iTerm} of all alternatives in {@code group}
     * from index {@code altBegin} (inclusive) to {@code altEnd} (exclusive) are semantically equal.
     */
    private static boolean alternativesAreEqualAt(Group group, int altBegin, int altEnd, int iTerm) {
        if (group.getAlternatives().get(altBegin).size() <= iTerm) {
            return false;
        }
        Term cmp = group.getAlternatives().get(altBegin).getTerms().get(iTerm);
        for (int i = altBegin + 1; i < altEnd; i++) {
            Sequence s = group.getAlternatives().get(i);
            if (s.size() <= iTerm) {
                return false;
            }
            if (!s.getTerms().get(iTerm).equalsSemantic(cmp)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns an index {@code end} where the following condition holds: The first term of all
     * alternatives in {@code group} from alternative index {@code begin} (inclusive) to {@code end}
     * (exclusive) is semantically equivalent. If no such index exists, returns {@code -1}.
     */
    private static int findMatchingAlternatives(Group group, int begin) {
        if (group.getAlternatives().get(begin).isEmpty()) {
            return -1;
        }
        Term cmp = group.getAlternatives().get(begin).getFirstTerm();
        int ret = -1;
        for (int i = begin + 1; i < group.size(); i++) {
            Sequence s = group.getAlternatives().get(i);
            if (!s.isEmpty() && cmp.equalsSemantic(s.getFirstTerm())) {
                ret = i + 1;
            } else {
                return ret;
            }
        }
        return ret;
    }

    /**
     * Returns an index {@code end} where the following condition holds: The first term of all
     * alternatives in {@code group} from alternative index {@code begin} (inclusive) to {@code end}
     * (exclusive) is a character class generated from a single character.
     */
    private static int findSingleCharAlternatives(Group group, int begin) {
        int ret = -1;
        for (int i = begin; i < group.size(); i++) {
            Sequence s = group.getAlternatives().get(i);
            if (s.isEmpty() || !s.getFirstTerm().isCharacterClass()) {
                return ret;
            }
            CharacterClass firstCC = s.getFirstTerm().asCharacterClass();
            if (!firstCC.wasSingleChar() || firstCC.hasQuantifier() && firstCC.getQuantifier().getMin() == 0) {
                return ret;
            }
            ret = i + 1;
        }
        return ret;
    }
}
