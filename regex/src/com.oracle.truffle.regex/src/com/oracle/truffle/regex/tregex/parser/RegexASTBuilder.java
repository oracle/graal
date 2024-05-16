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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.regex.RegexFlags;
import com.oracle.truffle.regex.RegexLanguage;
import com.oracle.truffle.regex.RegexOptions;
import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.charset.ClassSetContents;
import com.oracle.truffle.regex.charset.CodePointSet;
import com.oracle.truffle.regex.charset.CodePointSetAccumulator;
import com.oracle.truffle.regex.charset.Constants;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.buffer.IntRangesBuffer;
import com.oracle.truffle.regex.tregex.parser.ast.AtomicGroup;
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
import com.oracle.truffle.regex.tregex.parser.ast.SubexpressionCall;
import com.oracle.truffle.regex.tregex.parser.ast.Term;
import com.oracle.truffle.regex.tregex.parser.ast.visitors.DepthFirstTraversalRegexASTVisitor;
import com.oracle.truffle.regex.tregex.parser.ast.visitors.NodeCountVisitor;
import com.oracle.truffle.regex.tregex.parser.ast.visitors.SetSourceSectionVisitor;
import com.oracle.truffle.regex.tregex.string.Encodings.Encoding;

/**
 * This class is used to generate regex ASTs. The provided methods append nodes to the AST.
 */
public final class RegexASTBuilder {

    private final RegexParserGlobals globals;
    private final RegexOptions options;
    private final Encoding encoding;
    private final RegexAST ast;
    private final RegexProperties properties;
    private final Counter.ThresholdCounter groupCount;
    private final NodeCountVisitor countVisitor;
    private final SetSourceSectionVisitor setSourceSectionVisitor;
    private final boolean canExplodeUTF16;
    private final CompilationBuffer compilationBuffer;

    private Group curGroup;
    private Sequence curSequence;
    private Term curTerm;
    private SourceSection overrideSourceSection;

    private final EconomicMap<Group, Integer> groupStartPositions;

    @TruffleBoundary
    public RegexASTBuilder(RegexLanguage language, RegexSource source, RegexFlags flags, boolean canExplodeUTF16, CompilationBuffer compilationBuffer) {
        this.globals = language.parserGlobals;
        this.options = source.getOptions();
        this.encoding = source.getEncoding();
        this.canExplodeUTF16 = canExplodeUTF16;
        this.ast = new RegexAST(language, source, flags);
        this.properties = ast.getProperties();
        this.groupCount = ast.getGroupCount();
        this.countVisitor = new NodeCountVisitor();
        this.setSourceSectionVisitor = options.isDumpAutomataWithSourceSections() ? new SetSourceSectionVisitor(ast) : null;
        this.compilationBuffer = compilationBuffer;
        this.groupStartPositions = source.getOptions().getFlavor().needsGroupStartPositions() ? EconomicMap.create(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE) : null;
    }

    public CompilationBuffer getCompilationBuffer() {
        return compilationBuffer;
    }

    /**
     * Returns the current {@link Group}. Any new {@link Term}s will be added to its last
     * {@link Sequence} (the one returned by {@link #curSequence}).
     */
    public Group getCurGroup() {
        return curGroup;
    }

    /**
     * Returns the current {@link Sequence} into which new {@link Term}s will be added.
     */
    public Sequence getCurSequence() {
        return curSequence;
    }

    /**
     * Returns the last {@link Term} inserted into the current {@link Sequence}. This will be
     * {@code null} if a new {@link Sequence} or {@link Group} was just started.
     */
    public Term getCurTerm() {
        return curTerm;
    }

    /**
     * Returns the code position of the beginning (opening parenthesis) of the current {@link Group}
     * ({@link #getCurGroup()}).
     */
    public int getCurGroupStartPosition() {
        return groupStartPositions.get(curGroup, 0);
    }

    private void setGroupStartPosition(Group group, Token token) {
        if (token != null) {
            setGroupStartPosition(group, token.getPosition());
        }
    }

    private void setGroupStartPosition(Group group, int startPosition) {
        if (options.getFlavor().needsGroupStartPositions()) {
            groupStartPositions.putIfAbsent(group, startPosition);
        }
    }

    public void setOverrideSourceSection(SourceSection sourceSection) {
        overrideSourceSection = sourceSection;
    }

    public void clearOverrideSourceSection() {
        overrideSourceSection = null;
    }

    private void addSourceSection(RegexASTNode node, Token token) {
        ast.addSourceSection(node, overrideSourceSection == null && token != null ? token.getSourceSection() : overrideSourceSection);
    }

    /**
     * Indicates whether the builder is currently in the root group or in some nested group.
     * 
     * @return {@code true} if the builder is in the root group
     */
    public boolean curGroupIsRoot() {
        return curGroup == ast.getRoot();
    }

    /**
     * This should be called first after creating a new {@link RegexASTBuilder}. This will create
     * and enter the root capture group (group number 0).
     */
    public void pushRootGroup() {
        pushRootGroup(true);
    }

    /**
     * Like {@link #pushRootGroup()}, but allows creating a non-capturing root group. This is useful
     * for building intermediate ASTs that are then pasted into other ASTs.
     */
    public void pushRootGroup(boolean rootCapture) {
        RegexASTRootNode rootParent = ast.createRootNode();
        ast.setRoot(pushGroup(null, rootCapture ? ast.createCaptureGroup(groupCount.inc()) : ast.createGroup(), rootParent, true));
        setGroupStartPosition(ast.getRoot(), 0);
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
        optimizeGroup(curGroup);
        ast.getRoot().setEnclosedCaptureGroupsHigh(groupCount.getCount());
        return ast;
    }

    /**
     * Creates and enters a new non-capturing group. This call should be paired with a call to
     * {@link #popGroup}.
     * 
     * @param token a {@link Token} whose source section should be included in the group's source
     *            sections, or {@code null} if none
     */
    public void pushGroup(Token token) {
        pushGroup(token, ast.createGroup(), null, true);
    }

    public void pushGroup() {
        pushGroup(null);
    }

    /**
     * Creates and enters a new capture group. This call should be paired with a call to
     * {@link #popGroup}.
     * 
     * @param token a {@link Token} whose source section should be included in the group's source
     *            sections, or {@code null} if none
     */
    public void pushCaptureGroup(Token token) {
        pushGroup(token, ast.createCaptureGroup(groupCount.inc()), null, true);
    }

    public void pushCaptureGroup() {
        pushCaptureGroup(null);
    }

    /**
     * Creates and enters a new look-ahead assertion. This call should be paired with a call to
     * {@link #popGroup}.
     * 
     * @param token a {@link Token} whose source section should be included in the assertion's
     *            source sections, or {@code null} if none
     * @param negate {@code true} if the look-ahead assertion is to be negative
     */
    public void pushLookAheadAssertion(Token token, boolean negate) {
        LookAheadAssertion lookAhead = ast.createLookAheadAssertion(negate);
        addSourceSection(lookAhead, token);
        addTerm(lookAhead);
        pushGroup(token, ast.createGroup(), lookAhead, true);
    }

    public void pushLookAheadAssertion(boolean negate) {
        pushLookAheadAssertion(null, negate);
    }

    /**
     * Creates and enters a new look-behind assertion. This call should be paired with a call to
     * {@link #popGroup}.
     * 
     * @param token a {@link Token} whose source section should be included in the assertion's
     *            source sections, or {@code null} if none
     * @param negate {@code true} if the look-behind assertion is to be negative
     */
    public void pushLookBehindAssertion(Token token, boolean negate) {
        LookBehindAssertion lookBehind = ast.createLookBehindAssertion(negate);
        addSourceSection(lookBehind, token);
        addTerm(lookBehind);
        pushGroup(token, ast.createGroup(), lookBehind, true);
    }

    public void pushLookBehindAssertion(boolean negate) {
        pushLookBehindAssertion(null, negate);
    }

    /**
     * Creates and enters a new atomic group. This call should be paired with a call to
     * {@link #popGroup}.
     *
     * @param token a {@link Token} whose source section should be included in the group's source
     *            sections, or {@code null} if none
     */
    public void pushAtomicGroup(Token token) {
        AtomicGroup atomicGroup = ast.createAtomicGroup();
        addSourceSection(atomicGroup, token);
        addTerm(atomicGroup);
        pushGroup(token, ast.createGroup(), atomicGroup, true);
    }

    public void pushAtomicGroup() {
        pushAtomicGroup(null);
    }

    /**
     * Creates and enters a new conditional back-reference group. This call should be paired with a
     * call to {@link #popGroup}.
     *
     * @param token a {@link Token} whose source section should be included in the group's source
     *            sections, or {@code null} if none
     */
    public void pushConditionalBackReferenceGroup(Token.BackReference token) {
        assert token.kind == Token.Kind.conditionalBackreference;
        assert token.getGroupNumbers().length == 1;
        pushGroup(token, ast.createConditionalBackReferenceGroup(token.getGroupNumbers()[0]), null, true);
    }

    public void pushConditionalBackReferenceGroup(int referencedGroupNumber, boolean namedReference) {
        pushConditionalBackReferenceGroup(Token.createConditionalBackReference(referencedGroupNumber, namedReference));
    }

    private Group pushGroup(Token token, Group group, RegexASTSubtreeRootNode parent, boolean openFirstSequence) {
        if (parent != null) {
            parent.setGroup(group);
        } else {
            addTerm(group);
        }
        addSourceSection(group, token);
        setGroupStartPosition(group, token);
        curGroup = group;
        curGroup.setEnclosedCaptureGroupsLow(groupCount.getCount());
        if (openFirstSequence) {
            nextSequence();
        } else {
            curSequence = null;
            curTerm = null;
        }
        return group;
    }

    private void pushGroup(boolean openNextSequence) {
        pushGroup(null, ast.createGroup(), null, openNextSequence);
    }

    /**
     * Close and leave the current group. This should be paired either with
     * {@link #pushGroup(Token)}, {@link #pushCaptureGroup(Token)},
     * {@link #pushLookAheadAssertion(Token, boolean)} or
     * {@link #pushLookBehindAssertion(Token, boolean)}.
     * 
     * @param token a {@link Token} whose source section should be included in the group's or
     *            assertion's source sections, or {@code null} if none
     */
    public void popGroup(Token token) {
        if (tryMergeSingleCharClassAlternations()) {
            curGroup.removeLastSequence();
            ast.getNodeCount().dec();
        }
        optimizeGroup(curGroup);
        curGroup.setEnclosedCaptureGroupsHigh(groupCount.getCount());
        addSourceSection(curGroup, token);
        if (curGroup.getParent().isSubtreeRoot()) {
            addSourceSection(curGroup.getParent(), token);
        }
        RegexASTNode parent = curGroup.getParent();
        if (parent.isSubtreeRoot()) {
            curTerm = (Term) parent;
            curSequence = parent.getParent().asSequence();
        } else {
            curTerm = curGroup;
            curSequence = (Sequence) parent;
        }
        curGroup = curSequence.getParent();
    }

    public void popGroup() {
        popGroup(null);
    }

    /**
     * Adds a new {@link Sequence} to the current {@link Group}. In a parser, you would call this
     * method after encountering the vertical bar operator.
     */
    public void nextSequence() {
        if (!tryMergeSingleCharClassAlternations()) {
            curSequence = curGroup.addSequence(ast);
        }
        curTerm = null;
    }

    private void addTerm(Term term) {
        curSequence.add(term);
        curTerm = term;
    }

    private boolean shouldExplodeUTF16() {
        return canExplodeUTF16 && options.isUTF16ExplodeAstralSymbols();
    }

    /**
     * Adds a new {@link CharacterClass} to the current {@link Sequence}.
     * 
     * @param token aside from the source sections, the token most importantly contains the set of
     *            code points to be included in the character class and a flag indicating whether it
     *            corresponds to a single character in the regex (i.e. a literal or an escaped
     *            character)
     */
    public void addCharClass(Token.CharacterClass token) {
        CodePointSet codePointSet = pruneCharClass(token.getCodePointSet());
        if (shouldExplodeUTF16()) {
            addTerm(translateUnicodeCharClass(codePointSet, token, token.wasSingleChar()));
        } else {
            addTerm(createCharClass(codePointSet, token, token.wasSingleChar()));
        }
    }

    public void addCharClass(CodePointSet charSet, boolean wasSingleChar) {
        addCharClass(Token.createCharClass(charSet, wasSingleChar));
    }

    public void addCharClass(CodePointSet charSet) {
        addCharClass(charSet, charSet.matchesSingleChar());
    }

    private CodePointSet pruneCharClass(CodePointSet cps) {
        return encoding.getFullSet().createIntersection(cps, compilationBuffer);
    }

    private CharacterClass createCharClass(CodePointSet charSet, Token token) {
        return createCharClass(charSet, token, false);
    }

    private CharacterClass createCharClass(CodePointSet charSet, Token token, boolean wasSingleChar) {
        CharacterClass characterClass = ast.createCharacterClass(charSet);
        addSourceSection(characterClass, token);
        if (wasSingleChar) {
            characterClass.setWasSingleChar();
        }
        return characterClass;
    }

    private Term translateUnicodeCharClass(CodePointSet codePointSet, Token token, boolean wasSingleChar) {
        if (Constants.BMP_WITHOUT_SURROGATES.contains(codePointSet)) {
            return createCharClass(codePointSet, token, wasSingleChar);
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
            loneLeadSurrogateAlternative.add(globals.getJsNoTrailSurrogateAhead().copyRecursive(ast, compilationBuffer));
        }

        if (loneTrailSurrogateRanges.matchesSomething()) {
            Sequence loneTrailSurrogateAlternative = group.addSequence(ast);
            loneTrailSurrogateAlternative.add(globals.getJsNoLeadSurrogateBehind().copyRecursive(ast, compilationBuffer));
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

    /**
     * Adds a new {@link Group} representing a class set expression to the current {@link Sequence}.
     *
     * @param token aside from the source sections, the token most importantly contains the set of
     *            code points and strings to be included in the class set
     */
    public void addClassSet(Token.ClassSet token, CaseFoldData.CaseFoldUnfoldAlgorithm caseUnfoldAlgo) {
        CodePointSetAccumulator buf = compilationBuffer.getCodePointSetAccumulator1();

        ClassSetContents contents = token.getContents();
        pushGroup(false);

        String[] sortedStrings = new String[contents.getStrings().size()];
        contents.getStrings().toArray(sortedStrings);
        Arrays.sort(sortedStrings, Comparator.comparingInt(String::length).reversed());
        for (String string : sortedStrings) {
            if (string.isEmpty()) {
                continue;
            }
            nextSequence();
            string.codePoints().forEachOrdered(cp -> {
                if (caseUnfoldAlgo != null) {
                    buf.clear();
                    buf.addCodePoint(cp);
                    CaseFoldData.applyCaseFoldUnfold(buf, compilationBuffer.getCodePointSetAccumulator2(), caseUnfoldAlgo);
                    addCharClass(buf.toCodePointSet());
                } else {
                    addCharClass(CodePointSet.create(cp));
                }
            });
        }

        if (!contents.getCodePointSet().isEmpty()) {
            nextSequence();
            if (caseUnfoldAlgo != null) {
                buf.clear();
                buf.addSet(contents.getCodePointSet());
                CaseFoldData.applyCaseFoldUnfold(buf, compilationBuffer.getCodePointSetAccumulator2(), caseUnfoldAlgo);
                addCharClass(buf.toCodePointSet());
            } else {
                addCharClass(contents.getCodePointSet());
            }
        }

        if (contents.getStrings().contains("")) {
            nextSequence();
        }

        popGroup();
    }

    public void addBackReference(Token.BackReference token) {
        addBackReference(token, false);
    }

    public void addBackReference(Token.BackReference token, boolean ignoreCase) {
        addBackReference(token, ignoreCase, false);
    }

    /**
     * Adds a new {@link BackReference} to the current {@link Sequence}.
     * 
     * @param token aside from the source sections, this contains the number of the group being
     *            referenced
     */
    public void addBackReference(Token.BackReference token, boolean ignoreCase, boolean ignoreCaseAltMode) {
        assert token.kind == Token.Kind.backReference;
        BackReference backReference = ast.createBackReference(token.getGroupNumbers());
        addSourceSection(backReference, token);
        addTerm(backReference);

        boolean allNestedReferences = true;
        boolean allForwardReferences = true;
        boolean allNestedOrForwardReferences = true;

        for (int groupNumber : backReference.getGroupNumbers()) {
            boolean forwardReference = groupNumber >= groupCount.getCount();
            boolean nestedReference = !forwardReference && isNestedBackReference(backReference, groupNumber);
            if (nestedReference) {
                ast.setGroupRecursivelyReferenced(groupNumber);
            }
            allNestedReferences = allNestedReferences && nestedReference;
            allForwardReferences = allForwardReferences && forwardReference;
            allNestedOrForwardReferences = allNestedOrForwardReferences && (forwardReference || nestedReference);
        }

        if (allForwardReferences) {
            backReference.setForwardReference();
        }
        if (allNestedReferences) {
            backReference.setNestedBackReference();
        }
        if (allNestedOrForwardReferences) {
            backReference.setNestedOrForwardReference();
        }
        if (ignoreCase) {
            backReference.setIgnoreCaseReference();
        }
        if (ignoreCaseAltMode) {
            backReference.setIgnoreCaseReferenceAltMode();
        }
    }

    public void addBackReference(int groupNumber, boolean namedReference, boolean ignoreCase) {
        addBackReference(Token.createBackReference(groupNumber, namedReference), ignoreCase);
    }

    private static boolean isNestedBackReference(BackReference backReference, int groupNumber) {
        RegexASTNode parent = backReference.getParent().getParent();
        while (true) {
            if (parent.asGroup().getGroupNumber() == groupNumber) {
                return true;
            }
            parent = parent.getParent();
            if (parent.isRoot()) {
                return false;
            }
            if (parent.isSubtreeRoot()) {
                parent = parent.getParent();
            }
            parent = parent.getParent();
        }
    }

    public void addSubexpressionCall(int groupNumber) {
        SubexpressionCall subexpressionCall = ast.createSubexpressionCall(groupNumber);
        addTerm(subexpressionCall);
    }

    /**
     * Adds a new {@link PositionAssertion} to the current {@link Sequence}.
     * 
     * @param token aside from the source sections, the kind of this token indicates whether this is
     *            the {@code ^} assertion or the {@code $} assertion
     */
    public void addPositionAssertion(Token token) {
        PositionAssertion.Type type;
        switch (token.kind) {
            case A:
            case caret:
                type = PositionAssertion.Type.CARET;
                break;
            case Z:
            case z:
            case dollar:
                type = PositionAssertion.Type.DOLLAR;
                break;
            default:
                throw new IllegalArgumentException("unexpected token kind: " + token.kind);
        }
        PositionAssertion positionAssertion = ast.createPositionAssertion(type);
        addSourceSection(positionAssertion, token);
        addTerm(positionAssertion);
    }

    public void addCaret() {
        addPositionAssertion(Token.createCaret());
    }

    public void addDollar() {
        addPositionAssertion(Token.createDollar());
    }

    /**
     * Adds a quantifier to the current {@link Term}.
     * 
     * @param quantifier this token contains a specification of the quantifier's semantics, along
     *            with the source section data
     */
    public void addQuantifier(Token.Quantifier quantifier) {
        assert curTerm == curSequence.getLastTerm();
        if (quantifier.isDead()) {
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
                // NB: If JavaScript ever gets possessive quantifiers, we might have to adjust this.
                removeCurTerm();
                return;
            }
        }
        if (quantifier.getMin() > 0 && (curTerm.isLookAroundAssertion() || curTermIsZeroWidthGroup)) {
            // Quantifying LookAroundAssertions doesn't do anything if quantifier.getMin() > 0, so
            // ignore. A possessive quantifier would still result in atomicity.
            if (quantifier.isPossessive()) {
                wrapCurTermInAtomicGroup();
            }
            return;
        }
        if (quantifier.getMin() == 1 && quantifier.getMax() == 1) {
            // x{1,1} -> x
            if (quantifier.isPossessive()) {
                wrapCurTermInAtomicGroup();
            }
            return;
        }
        curTerm = addQuantifier(curTerm, quantifier);
        if (quantifier.isPossessive()) {
            wrapCurTermInAtomicGroup();
            // do not attempt to merge quantifiers when possessive quantifiers are present
            return;
        }
        // merge equal successive quantified terms
        if (curSequence.size() > 1) {
            Term prevTerm = curSequence.getTerms().get(curSequence.size() - 2);
            if (prevTerm.isQuantifiableTerm()) {
                QuantifiableTerm prev = prevTerm.asQuantifiableTerm();
                if (prev.hasQuantifier() && prev.getQuantifier().isGreedy() == quantifier.isGreedy() && curTerm.asQuantifiableTerm().equalsSemantic(prev, true)) {
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
                    setQuantifier(prev, Token.createQuantifier((int) min, (int) max, quantifier.isGreedy(), false, quantifier.isSingleChar()));
                }
            }
        }
    }

    private QuantifiableTerm addQuantifier(Term term, Token.Quantifier quantifier) {
        QuantifiableTerm quantifiableTerm = term.isQuantifiableTerm() ? term.asQuantifiableTerm() : wrapTermInGroup(term);
        if (quantifiableTerm.hasQuantifier()) {
            quantifiableTerm = wrapTermInGroup(term);
        }
        addSourceSection(quantifiableTerm, quantifier);
        setQuantifier(quantifiableTerm, quantifier);
        return quantifiableTerm;
    }

    private void setQuantifier(QuantifiableTerm term, Token.Quantifier quantifier) {
        term.setQuantifier(quantifier);
        if (!term.isUnrollingCandidate()) {
            properties.setLargeCountedRepetitions();
        }
        properties.setQuantifiers();
    }

    private Group wrapTermInGroup(Term term) {
        Group wrapperGroup = ast.createGroup();
        if (term.isGroup()) {
            wrapperGroup.setEnclosedCaptureGroupsLow(term.asGroup().getCaptureGroupsLow());
            wrapperGroup.setEnclosedCaptureGroupsHigh(term.asGroup().getCaptureGroupsHigh());
        } else if (term.isAtomicGroup()) {
            wrapperGroup.setEnclosedCaptureGroupsLow(term.asAtomicGroup().getEnclosedCaptureGroupsLow());
            wrapperGroup.setEnclosedCaptureGroupsHigh(term.asAtomicGroup().getEnclosedCaptureGroupsHigh());
        }
        Sequence wrapperSequence = wrapperGroup.addSequence(ast);
        term.getParent().asSequence().replace(term.getSeqIndex(), wrapperGroup);
        wrapperSequence.add(term);
        return wrapperGroup;
    }

    /**
     * Adds a copy of {@code sourceGroup} to the current {@link Sequence}.
     * 
     * @param token a token indicating which source sections should be attributed to the copied
     *            group
     * @param sourceGroup the {@link Group} to be copied
     */
    public void addCopy(Token token, Group sourceGroup) {
        Group copy = sourceGroup.copyRecursive(ast, compilationBuffer);
        if (options.isDumpAutomataWithSourceSections()) {
            setSourceSectionVisitor.run(copy, overrideSourceSection != null ? overrideSourceSection : token.getSourceSection());
        }
        addTerm(copy);
    }

    /**
     * Removes the current {@link Term} from the current {@link Sequence}.
     */
    public void removeCurTerm() {
        ast.getNodeCount().dec(countVisitor.count(curSequence.getLastTerm()));
        curSequence.removeLastTerm();
        curTerm = curSequence.isEmpty() ? null : curSequence.getLastTerm();
    }

    /**
     * Adds a dead node (an empty character class) to the current {@link Sequence}.
     */
    public void addDeadNode() {
        addTerm(createCharClass(CodePointSet.getEmpty(), null));
    }

    /**
     * Replaces the current {@link Term} with a dead node.
     */
    public void replaceCurTermWithDeadNode() {
        removeCurTerm();
        addDeadNode();
    }

    /**
     * Wraps the current {@link Term} in a non-capturing group.
     */
    public void wrapCurTermInGroup() {
        curTerm = wrapTermInGroup(curTerm);
    }

    /**
     * Wraps the current {@link Term} in an atomic group. This can be useful when implementing
     * possessive quantifiers.
     */
    public void wrapCurTermInAtomicGroup() {
        Group atomicGroupContents = wrapTermInGroup(curTerm);
        AtomicGroup atomicGroup = ast.createAtomicGroup();
        curSequence.replace(atomicGroupContents.getSeqIndex(), atomicGroup);
        atomicGroup.setGroup(atomicGroupContents);
        curTerm = atomicGroup;
    }

    public void addWordBoundaryAssertion(CodePointSet wordChars, CodePointSet nonWordChars) {
        // (?:(?:^|(?<=\W))(?=\w)|(?<=\w)(?:(?=\W)|$))
        pushGroup(); // (?:
        pushGroup(); // (?:
        addCaret(); // ^
        nextSequence(); // |
        pushLookBehindAssertion(false); // (?<=
        addCharClass(nonWordChars); // \W
        popGroup(); // )
        popGroup(); // )
        pushLookAheadAssertion(false); // (?=
        addCharClass(wordChars); // \w
        popGroup(); // )
        nextSequence(); // |
        pushLookBehindAssertion(false); // (?<=
        addCharClass(wordChars); // \w
        popGroup(); // )
        pushGroup(); // (?:
        pushLookAheadAssertion(false); // (?=
        addCharClass(nonWordChars); // \W
        popGroup(); // )
        nextSequence(); // |
        addDollar(); // $
        popGroup(); // )
        popGroup(); // )
    }

    public void addWordNonBoundaryAssertion(CodePointSet wordChars, CodePointSet nonWordChars) {
        // (?:(?:^|(?<=\W))(?:(?=\W)|$)|(?<=\w)(?=\w))
        pushGroup(); // (?:
        pushGroup(); // (?:
        addCaret(); // ^
        nextSequence(); // |
        pushLookBehindAssertion(false); // (?<=
        addCharClass(nonWordChars); // \W
        popGroup(); // )
        popGroup(); // )
        pushGroup(); // (?:
        pushLookAheadAssertion(false); // (?=
        addCharClass(nonWordChars); // \W
        popGroup(); // )
        nextSequence(); // |
        addDollar(); // $
        popGroup(); // )
        nextSequence(); // |
        pushLookBehindAssertion(false); // (?<=
        addCharClass(wordChars); // \w
        popGroup(); // )
        pushLookAheadAssertion(false); // (?=
        addCharClass(wordChars); // \w
        popGroup(); // )
        popGroup(); // )
    }

    public void addWordNonBoundaryAssertionPython(CodePointSet wordChars, CodePointSet nonWordChars) {
        // (?:^(?=\W)|(?<=\W)$|(?<=\W)(?=\W)|(?<=\w)(?=\w))
        pushGroup(); // (?:
        addCaret(); // ^
        pushLookAheadAssertion(false); // (?=
        addCharClass(nonWordChars); // \W
        popGroup(); // )
        nextSequence(); // |
        pushLookBehindAssertion(false); // (?<=
        addCharClass(nonWordChars); // \W
        popGroup(); // )
        addDollar(); // $
        nextSequence(); // |
        pushLookBehindAssertion(false); // (?<=
        addCharClass(nonWordChars); // \W
        popGroup(); // )
        pushLookAheadAssertion(false); // (?=
        addCharClass(nonWordChars); // \W
        popGroup(); // )
        nextSequence(); // |
        pushLookBehindAssertion(false); // (?<=
        addCharClass(wordChars); // \W
        popGroup(); // )
        pushLookAheadAssertion(false); // (?=
        addCharClass(wordChars); // \W
        popGroup(); // )
        popGroup(); // )
    }

    /* optimizations */

    private void optimizeGroup(Group group) {
        if (group.isConditionalBackReferenceGroup()) {
            return;
        }
        sortAlternatives(group);
        mergeCommonPrefixes(group);
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
        if (curGroup.size() > 1 && curSequence.isSingleCharClass() && !curGroup.isConditionalBackReferenceGroup()) {
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
                    optimizeGroup(innerGroup);
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
